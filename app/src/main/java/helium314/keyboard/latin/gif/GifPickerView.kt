package helium314.keyboard.latin.gif

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Precision
import com.leanbitlab.leantype.gif.Contract
import com.leanbitlab.leantype.gif.GifItem
import com.leanbitlab.leantype.gif.IGifCallback
import com.leanbitlab.leantype.gif.IGifEngine
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants
import kotlinx.coroutines.*
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class GifPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val manager = GifPluginManager.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val query = StringBuilder()

    private var queryJob: Job? = null
    private var stateJob: Job? = null
    private var currentRequestId: Int? = null
    private var loadSeq = 0
    private var opened = false

    private lateinit var searchText: TextView
    private lateinit var closeButton: ImageButton
    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var emptyView: TextView

    private val adapter = GifAdapter(context) { onGifClicked(it) }
    private val isInserting = AtomicBoolean(false)

    var targetProvider: (() -> Pair<InputConnection?, EditorInfo?>)? = null
    var sessionProvider: (() -> Long)? = null
    var onCloseRequested: (() -> Unit)? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        searchText = findViewById(R.id.searchText)
        closeButton = findViewById(R.id.closeButton)
        recycler = findViewById(R.id.recycler)
        progress = findViewById(R.id.progress)
        emptyView = findViewById(R.id.emptyView)

        recycler.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter
        closeButton.setOnClickListener { onCloseRequested?.invoke() }
    }

    fun open() {
        if (opened) return
        opened = true
        loadSeq++
        query.clear()
        renderQuery()
        adapter.submit(emptyList())
        manager.acquire("picker")

        stateJob?.cancel()
        stateJob = scope.launch {
            manager.connectionGeneration.collect { gen ->
                if (!opened) return@collect
                loadSeq++
                adapter.submit(emptyList())
                if (manager.state.value == GifPluginManager.State.CONNECTED) {
                    reload(debounce = false)
                }
            }
        }
        reload(debounce = false)
    }

    fun close() {
        if (!opened) return
        opened = false
        loadSeq++
        queryJob?.cancel()
        stateJob?.cancel()
        cancelCurrent()
        manager.release("picker")
        adapter.submit(emptyList())
    }

    override fun onDetachedFromWindow() {
        close()
        scope.coroutineContext.cancelChildren()
        super.onDetachedFromWindow()
    }

    /**
     * Called by host input pipeline for keystrokes while picker is open.
     * Returns true if consumed.
     */
    fun onHostKey(code: Int): Boolean {
        when {
            code == KeyCode.DELETE || code == -5 -> {
                if (query.isNotEmpty()) {
                    query.setLength(query.offsetByCodePoints(query.length, -1))
                    renderQuery()
                    reload(debounce = true)
                }
                return true // Consumed: empty buffer does not trigger refetch
            }
            code == Constants.CODE_ENTER || code == '\n'.code -> {
                reload(debounce = false)
                return true
            }
            code >= 32 -> {
                if (query.codePointCount(0, query.length) < MAX_QUERY_CODE_POINTS) {
                    query.appendCodePoint(code)
                    renderQuery()
                    reload(debounce = true)
                }
                return true
            }
            else -> return false // Shift, layout switches, etc.
        }
    }

    fun onHostText(text: String): Boolean {
        var added = false
        for (cp in text.codePoints()) {
            if (query.codePointCount(0, query.length) < MAX_QUERY_CODE_POINTS) {
                query.appendCodePoint(cp)
                added = true
            }
        }
        if (added) {
            renderQuery()
            reload(debounce = true)
        }
        return true
    }

    private fun renderQuery() {
        searchText.text = query.toString()
    }

    private fun reload(debounce: Boolean) {
        queryJob?.cancel()
        queryJob = scope.launch {
            if (debounce) delay(300)
            val q = query.toString().trim()
            runRequest { engine, id, cb ->
                if (q.isEmpty()) {
                    engine.trending(id, DEFAULT_PROVIDER, "", PAGE_SIZE, cb)
                } else {
                    engine.search(id, DEFAULT_PROVIDER, q, "", PAGE_SIZE, cb)
                }
            }
        }
    }

    private suspend fun runRequest(call: (IGifEngine, Int, IGifCallback) -> Unit) {
        cancelCurrent()
        val seq = ++loadSeq
        val gen = manager.connectionGeneration.value
        val id = manager.nextRequestId().also { currentRequestId = it }

        progress.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        val deferred = CompletableDeferred<List<GifItem>>()
        val cb = object : IGifCallback.Stub() {
            override fun onItems(requestId: Int, items: MutableList<GifItem>, nextPos: String?) {
                if (requestId == id) deferred.complete(items)
            }
            override fun onFetched(requestId: Int, pfd: ParcelFileDescriptor?, mimeType: String?, width: Int, height: Int) {
                runCatching { pfd?.close() }
            }
            override fun onError(requestId: Int, code: Int, message: String?) {
                if (requestId == id) deferred.completeExceptionally(PluginException(code, message ?: ""))
            }
        }

        val result = manager.call(12_000) { engine ->
            call(engine, id, cb)
        }

        if (result.isFailure) {
            if (currentRequestId == id) currentRequestId = null
            if (opened && seq == loadSeq) {
                progress.visibility = View.GONE
                showMessage(failureMessage(result.exceptionOrNull() ?: Exception()))
            }
            return
        }

        val itemsResult = try {
            withTimeout(12_000) { deferred.await() }
        } catch (e: Exception) {
            runCatching { manager.call { it.cancel(id) } }
            if (currentRequestId == id) currentRequestId = null
            if (opened && seq == loadSeq) {
                progress.visibility = View.GONE
                showMessage(failureMessage(e))
            }
            return
        }

        if (currentRequestId == id) currentRequestId = null
        if (!opened || seq != loadSeq || gen != manager.connectionGeneration.value) return

        progress.visibility = View.GONE
        if (recycler.isComputingLayout) {
            post {
                adapter.submit(itemsResult)
                if (itemsResult.isEmpty()) showMessage(context.getString(R.string.gif_no_results))
            }
        } else {
            adapter.submit(itemsResult)
            if (itemsResult.isEmpty()) showMessage(context.getString(R.string.gif_no_results))
        }
    }

    private fun cancelCurrent() {
        currentRequestId?.let { id ->
            scope.launch {
                runCatching { manager.call { it.cancel(id) } }
            }
        }
        currentRequestId = null
    }

    private fun showMessage(msg: String) {
        emptyView.text = msg
        emptyView.visibility = View.VISIBLE
    }

    private fun failureMessage(t: Throwable): String = when (manager.state.value) {
        GifPluginManager.State.NOT_INSTALLED -> context.getString(R.string.gif_error_plugin_not_installed)
        GifPluginManager.State.UNTRUSTED_PLUGIN -> context.getString(R.string.gif_error_untrusted)
        GifPluginManager.State.HOST_TOO_OLD -> context.getString(R.string.gif_error_host_too_old)
        GifPluginManager.State.PLUGIN_TOO_OLD -> context.getString(R.string.gif_error_plugin_too_old)
        GifPluginManager.State.UNAVAILABLE -> context.getString(R.string.gif_error_unavailable)
        else -> when {
            t is PluginBusy -> context.getString(R.string.gif_error_busy)
            (t as? PluginException)?.code == Contract.ERR_NOT_CONFIGURED -> context.getString(R.string.gif_error_not_configured)
            (t as? PluginException)?.code == Contract.ERR_RATE_LIMITED -> context.getString(R.string.gif_error_rate_limited)
            (t as? PluginException)?.code == Contract.ERR_TIMEOUT || t is TimeoutException || t is TimeoutCancellationException -> context.getString(R.string.gif_error_timeout)
            else -> context.getString(R.string.gif_error_generic)
        }
    }

    private fun onGifClicked(item: GifItem) {
        if (!isInserting.compareAndSet(false, true)) return
        val (ic, ei) = targetProvider?.invoke() ?: run {
            isInserting.set(false)
            return
        }
        if (ic == null || ei == null) {
            isInserting.set(false)
            return
        }
        val capturedSession = sessionProvider?.invoke() ?: 0L

        scope.launch {
            manager.acquire("insert")
            try {
                val id = manager.nextRequestId()
                val d = CompletableDeferred<Pair<ParcelFileDescriptor, String>>()

                val result = manager.call(12_000) { engine ->
                    engine.fetch(id, item, object : IGifCallback.Stub() {
                        override fun onFetched(requestId: Int, pfd: ParcelFileDescriptor?, mimeType: String?, width: Int, height: Int) {
                            if (requestId != id || pfd == null) {
                                runCatching { pfd?.close() }
                                return
                            }
                            if (!d.complete(pfd to (mimeType ?: "image/gif"))) {
                                runCatching { pfd.close() }
                            }
                        }
                        override fun onItems(requestId: Int, items: MutableList<GifItem>, nextPos: String?) {}
                        override fun onError(requestId: Int, code: Int, message: String?) {
                            if (requestId == id) d.completeExceptionally(PluginException(code, message ?: ""))
                        }
                    })
                }

                if (result.isFailure) {
                    Toast.makeText(context.applicationContext, failureMessage(result.exceptionOrNull() ?: Exception()), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val pfdPair = try {
                    withTimeout(12_000) { d.await() }
                } catch (e: Exception) {
                    runCatching { manager.call { it.cancel(id) } }
                    Toast.makeText(context.applicationContext, context.getString(R.string.gif_error_timeout), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val (pfd, mime) = pfdPair
                val currentSession = sessionProvider?.invoke() ?: 0L
                if (!opened || currentSession != capturedSession) {
                    runCatching { pfd.close() }
                    return@launch
                }

                GifInserter.commit(context.applicationContext, pfd, mime, ic, ei)
            } finally {
                manager.release("insert")
                isInserting.set(false)
            }
        }
    }

    private class GifAdapter(
        private val context: Context,
        private val onClick: (GifItem) -> Unit
    ) : RecyclerView.Adapter<GifAdapter.VH>() {
        private var items = listOf<GifItem>()

        @SuppressLint("NotifyDataSetChanged")
        fun submit(new: List<GifItem>) {
            items = new
            notifyDataSetChanged()
        }

        class VH(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gif_thumb, parent, false) as ImageView
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val uri = Uri.parse(item.previewUri)
            if (uri.scheme != "content" || uri.authority != THUMB_AUTHORITY) {
                return
            }

            val lp = holder.imageView.layoutParams
            val h = context.resources.getDimensionPixelSize(R.dimen.gif_thumb_height)
            lp.height = h
            val computedWidth = (h * item.width.toFloat() / item.height.coerceAtLeast(1)).toInt()
            lp.width = computedWidth.coerceIn((h * 0.5f).toInt(), (h * 2.0f).toInt())
            holder.imageView.layoutParams = lp

            holder.imageView.load(uri, GifImageLoader.get(context)) {
                placeholder(R.drawable.ic_gif_placeholder)
                precision(Precision.INEXACT)
            }
            holder.imageView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        private const val DEFAULT_PROVIDER = "klipy"
        private const val PAGE_SIZE = 24
        private const val MAX_QUERY_CODE_POINTS = 80
        private const val THUMB_AUTHORITY = "com.leanbitlab.leantype.gif.thumbs"
    }
}
