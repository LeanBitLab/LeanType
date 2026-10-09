package helium314.keyboard.latin.gif

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
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
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
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

    private var activeProviderId: String = ""
    private var activeProviderName: String = ""
    private var queryJob: Job? = null
    private var pagingJob: Job? = null
    private var stateJob: Job? = null
    private var currentRequestId: Int? = null
    private var loadSeq = 0
    private var opened = false

    private var nextPos: String? = null
    private var isPaging = false
    private var hasMore = true

    private lateinit var searchContainer: LinearLayout
    private lateinit var searchIcon: ImageView
    private lateinit var searchText: TextView
    private lateinit var providerBadge: TextView
    private lateinit var clearQueryButton: ImageButton
    private lateinit var closeButton: ImageButton
    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var statusContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var statusAction: TextView

    private val adapter = GifAdapter(context, scope) { onGifClicked(it) }
    private val isInserting = AtomicBoolean(false)

    var targetProvider: (() -> Pair<InputConnection?, EditorInfo?>)? = null
    var sessionProvider: (() -> Long)? = null
    var onCloseRequested: (() -> Unit)? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        searchContainer = findViewById(R.id.searchContainer)
        searchIcon = findViewById(R.id.searchIcon)
        searchText = findViewById(R.id.searchText)
        providerBadge = findViewById(R.id.providerBadge)
        clearQueryButton = findViewById(R.id.clearQueryButton)
        closeButton = findViewById(R.id.closeButton)
        recycler = findViewById(R.id.recycler)
        progress = findViewById(R.id.progress)
        statusContainer = findViewById(R.id.statusContainer)
        statusText = findViewById(R.id.statusText)
        statusAction = findViewById(R.id.statusAction)

        val lm = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        recycler.layoutManager = lm
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter

        closeButton.setOnClickListener { onCloseRequested?.invoke() }
        clearQueryButton.setOnClickListener {
            query.clear()
            renderQuery()
            reload(debounce = false)
        }

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dx <= 0) return
                val total = lm.itemCount
                val lastVisible = lm.findLastVisibleItemPosition()
                if (total > 0 && lastVisible >= total - 4 && !isPaging && hasMore && !nextPos.isNullOrEmpty()) {
                    loadNextPage()
                }
            }
        })

        applyTheme()
    }

    fun open() {
        if (opened) return
        opened = true
        loadSeq++
        query.clear()
        nextPos = null
        isPaging = false
        hasMore = true
        renderQuery()
        statusContainer.visibility = View.GONE
        adapter.submit(emptyList())
        applyTheme()
        manager.acquire("picker")

        stateJob?.cancel()
        stateJob = scope.launch {
            manager.state.collect { state ->
                if (!opened) return@collect
                when (state) {
                    GifPluginManager.State.CONNECTED -> {
                        loadSeq++
                        manager.call { engine -> resolveProvider(engine) }
                        reload(debounce = false)
                    }
                    GifPluginManager.State.CONNECTING -> {
                        progress.visibility = View.VISIBLE
                        statusContainer.visibility = View.GONE
                    }
                    GifPluginManager.State.NOT_INSTALLED -> {
                        progress.visibility = View.GONE
                        showStatus(context.getString(R.string.gif_plugin_not_installed_desc), context.getString(R.string.gif_open_settings)) {
                            openGifSettings()
                        }
                    }
                    GifPluginManager.State.UNTRUSTED_PLUGIN -> {
                        progress.visibility = View.GONE
                        showStatus(context.getString(R.string.gif_plugin_untrusted_desc), context.getString(R.string.gif_open_settings)) {
                            openGifSettings()
                        }
                    }
                    GifPluginManager.State.PLUGIN_TOO_OLD -> {
                        progress.visibility = View.GONE
                        showStatus(context.getString(R.string.gif_error_plugin_too_old))
                    }
                    GifPluginManager.State.HOST_TOO_OLD -> {
                        progress.visibility = View.GONE
                        showStatus(context.getString(R.string.gif_error_host_too_old))
                    }
                    GifPluginManager.State.UNAVAILABLE -> {
                        progress.visibility = View.GONE
                        showStatus(context.getString(R.string.gif_error_unavailable))
                    }
                    else -> {}
                }
            }
        }
    }

    fun close() {
        if (!opened) return
        opened = false
        activeProviderId = ""
        activeProviderName = ""
        loadSeq++
        queryJob?.cancel()
        pagingJob?.cancel()
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
                return true
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
            else -> return false
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
        val text = query.toString()
        searchText.text = text
        clearQueryButton.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun resolveProvider(engine: IGifEngine): String {
        val prefProvider = context.prefs().getString(Settings.PREF_GIF_PROVIDER, "") ?: Settings.getValues().mGifProvider
        val list = try { engine.listProviders() } catch (_: Exception) { emptyList() }
        val configured = list.firstOrNull { it.id == prefProvider && it.configured }
            ?: list.firstOrNull { it.configured }
            ?: list.firstOrNull { it.id == prefProvider }
            ?: list.firstOrNull()

        val id = configured?.id ?: "giphy"
        val name = configured?.displayName ?: "GIPHY"
        activeProviderId = id
        activeProviderName = name
        post {
            if (activeProviderName.isNotEmpty()) {
                providerBadge.text = activeProviderName.uppercase()
                providerBadge.visibility = View.VISIBLE
                searchText.hint = context.getString(R.string.gif_search_hint_provider, activeProviderName)
            } else {
                providerBadge.visibility = View.GONE
                searchText.hint = context.getString(R.string.gif_search_hint)
            }
        }
        return id
    }

    private fun applyTheme() {
        val colors = Settings.getValues().mColors ?: return
        setBackgroundColor(colors.get(ColorType.STRIP_BACKGROUND))
        searchContainer.backgroundTintList = ColorStateList.valueOf(colors.get(ColorType.KEY_BACKGROUND))
        statusAction.backgroundTintList = ColorStateList.valueOf(colors.get(ColorType.ACTION_KEY_BACKGROUND))

        val iconColor = colors.get(ColorType.KEY_ICON)
        searchIcon.imageTintList = ColorStateList.valueOf(iconColor)
        clearQueryButton.imageTintList = ColorStateList.valueOf(iconColor)
        closeButton.imageTintList = ColorStateList.valueOf(iconColor)
        progress.indeterminateTintList = ColorStateList.valueOf(iconColor)

        searchText.setTextColor(colors.get(ColorType.KEY_TEXT))
        searchText.setHintTextColor(colors.get(ColorType.KEY_HINT_TEXT))
        providerBadge.setTextColor(colors.get(ColorType.KEY_HINT_TEXT))
        statusText.setTextColor(colors.get(ColorType.KEY_HINT_TEXT))
        statusAction.setTextColor(colors.get(ColorType.KEY_TEXT))
    }

    private fun reload(debounce: Boolean) {
        val seq = ++loadSeq
        queryJob?.cancel()
        pagingJob?.cancel()
        isPaging = false
        nextPos = null
        hasMore = true
        statusContainer.visibility = View.GONE

        queryJob = scope.launch {
            if (debounce) delay(300)
            if (!isActive || seq != loadSeq) return@launch
            val q = query.toString().trim()
            runRequest(isPage = false, seq = seq) { engine, id, cb ->
                val provider = if (activeProviderId.isNotEmpty()) activeProviderId else resolveProvider(engine)
                if (q.isEmpty()) {
                    engine.trending(id, provider, "", PAGE_SIZE, cb)
                } else {
                    engine.search(id, provider, q, "", PAGE_SIZE, cb)
                }
            }
        }
    }

    private fun loadNextPage() {
        if (isPaging || !hasMore) return
        val pos = nextPos ?: return
        if (pos.isEmpty()) return
        isPaging = true

        val seq = loadSeq
        pagingJob?.cancel()
        pagingJob = scope.launch {
            val q = query.toString().trim()
            runRequest(isPage = true, seq = seq) { engine, id, cb ->
                val provider = if (activeProviderId.isNotEmpty()) activeProviderId else resolveProvider(engine)
                if (q.isEmpty()) {
                    engine.trending(id, provider, pos, PAGE_SIZE, cb)
                } else {
                    engine.search(id, provider, q, pos, PAGE_SIZE, cb)
                }
            }
        }
    }

    private suspend fun runRequest(
        isPage: Boolean,
        seq: Int,
        call: (IGifEngine, Int, IGifCallback) -> Unit
    ) {
        if (!isPage) {
            cancelCurrent()
            statusContainer.visibility = View.GONE
            progress.visibility = View.VISIBLE
        }
        val gen = manager.connectionGeneration.value
        val id = manager.nextRequestId().also { currentRequestId = it }

        val deferred = CompletableDeferred<Pair<List<GifItem>, String?>>()
        val cb = object : IGifCallback.Stub() {
            override fun onItems(requestId: Int, items: MutableList<GifItem>, newNextPos: String?) {
                if (requestId == id) deferred.complete(items to newNextPos)
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
            val err = result.exceptionOrNull()
            if (err is CancellationException) return
            if (isPage) {
                isPaging = false
            } else if (opened && seq == loadSeq && currentCoroutineContext().isActive) {
                progress.visibility = View.GONE
                showErrorStatus(err ?: Exception())
            }
            return
        }

        val response = try {
            withTimeout(12_000) { deferred.await() }
        } catch (e: CancellationException) {
            runCatching { manager.call { it.cancel(id) } }
            if (currentRequestId == id) currentRequestId = null
            if (isPage) isPaging = false
            return
        } catch (e: Exception) {
            runCatching { manager.call { it.cancel(id) } }
            if (currentRequestId == id) currentRequestId = null
            if (isPage) {
                isPaging = false
            } else if (opened && seq == loadSeq && currentCoroutineContext().isActive) {
                progress.visibility = View.GONE
                showErrorStatus(e)
            }
            return
        }

        if (currentRequestId == id) currentRequestId = null
        if (!opened || seq != loadSeq || gen != manager.connectionGeneration.value || !currentCoroutineContext().isActive) {
            if (isPage) isPaging = false
            return
        }

        val (itemsResult, returnedPos) = response
        nextPos = returnedPos
        hasMore = !returnedPos.isNullOrEmpty() && itemsResult.isNotEmpty()

        if (!isPage) {
            progress.visibility = View.GONE
            adapter.submit(itemsResult)
            if (itemsResult.isEmpty()) {
                val q = query.toString().trim()
                if (q.isNotEmpty()) {
                    showStatus(
                        context.getString(R.string.gif_no_results_query, q),
                        context.getString(R.string.gif_search_clear)
                    ) {
                        query.clear()
                        renderQuery()
                        reload(debounce = false)
                    }
                } else {
                    showStatus(context.getString(R.string.gif_no_results))
                }
            } else {
                statusContainer.visibility = View.GONE
            }
        } else {
            isPaging = false
            if (itemsResult.isNotEmpty()) {
                adapter.append(itemsResult)
            }
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

    private fun showStatus(msg: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
        statusText.text = msg
        if (actionText != null && onAction != null) {
            statusAction.text = actionText
            statusAction.setOnClickListener { onAction() }
            statusAction.visibility = View.VISIBLE
        } else {
            statusAction.visibility = View.GONE
        }
        statusContainer.visibility = View.VISIBLE
    }

    private fun showErrorStatus(t: Throwable) {
        val pluginState = manager.state.value
        val isNotConfigured = (t as? PluginException)?.code == Contract.ERR_NOT_CONFIGURED
        val isNotInstalled = pluginState == GifPluginManager.State.NOT_INSTALLED
        val isUntrusted = pluginState == GifPluginManager.State.UNTRUSTED_PLUGIN

        val msg = failureMessage(t)
        when {
            isNotConfigured || isNotInstalled || isUntrusted -> {
                showStatus(msg, context.getString(R.string.gif_open_settings)) {
                    openGifSettings()
                }
            }
            else -> {
                showStatus(msg, context.getString(R.string.gif_retry)) {
                    reload(debounce = false)
                }
            }
        }
    }

    private fun openGifSettings() {
        val intent = Intent(context, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        SettingsDestination.navigateTo(SettingsDestination.Gif)
        context.startActivity(intent)
        onCloseRequested?.invoke()
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

        adapter.setInsertingId(item.id)

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
                adapter.setInsertingId(null)
                manager.release("insert")
                isInserting.set(false)
            }
        }
    }

    private class GifAdapter(
        private val context: Context,
        private val scope: CoroutineScope,
        private val onClick: (GifItem) -> Unit
    ) : RecyclerView.Adapter<GifAdapter.VH>() {
        private var items = listOf<GifItem>()
        private var insertingId: String? = null
        private var liveItemId: String? = null
        private var liveJob: Job? = null

        @SuppressLint("NotifyDataSetChanged")
        fun submit(new: List<GifItem>) {
            items = new
            liveItemId = null
            liveJob?.cancel()
            notifyDataSetChanged()
        }

        fun append(newItems: List<GifItem>) {
            if (newItems.isEmpty()) return
            val start = items.size
            items = items + newItems
            notifyItemRangeInserted(start, newItems.size)
        }

        @SuppressLint("NotifyDataSetChanged")
        fun setInsertingId(id: String?) {
            insertingId = id
            notifyDataSetChanged()
        }

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.gifThumbImage)
            val liveBadge: TextView = itemView.findViewById(R.id.liveBadge)
            val insertOverlay: View = itemView.findViewById(R.id.insertOverlay)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gif_thumb, parent, false)
            return VH(view)
        }

        override fun onViewRecycled(holder: VH) {
            super.onViewRecycled(holder)
            stopAnimation(holder.imageView)
            holder.imageView.setImageDrawable(null)
        }

        private fun stopAnimation(imageView: ImageView) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val d = imageView.drawable
                if (d is AnimatedImageDrawable) {
                    d.stop()
                }
            }
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val isCurrentInserting = item.id == insertingId
            holder.insertOverlay.visibility = if (isCurrentInserting) View.VISIBLE else View.GONE

            val uri = Uri.parse(item.previewUri)
            if (uri.scheme != "content" || uri.authority != THUMB_AUTHORITY) {
                return
            }

            val lp = holder.itemView.layoutParams
            val h = context.resources.getDimensionPixelSize(R.dimen.gif_thumb_height)
            lp.height = h
            val computedWidth = (h * item.width.toFloat() / item.height.coerceAtLeast(1)).toInt()
            lp.width = computedWidth.coerceIn((h * 0.5f).toInt(), (h * 2.0f).toInt())
            holder.itemView.layoutParams = lp

            val isLive = item.id == liveItemId
            holder.liveBadge.visibility = if (isLive) View.VISIBLE else View.GONE

            if (isLive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                bindLive(holder, uri, item.id)
            } else {
                stopAnimation(holder.imageView)
                holder.imageView.load(uri, GifImageLoader.get(context)) {
                    placeholder(R.drawable.ic_gif_placeholder)
                    precision(Precision.INEXACT)
                }
            }

            holder.itemView.setOnClickListener { onClick(item) }
            holder.itemView.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val oldLiveId = liveItemId
                liveItemId = if (liveItemId == item.id) null else item.id
                val oldPos = items.indexOfFirst { it.id == oldLiveId }
                val newPos = items.indexOfFirst { it.id == liveItemId }
                if (oldPos != -1) notifyItemChanged(oldPos)
                if (newPos != -1 && newPos != oldPos) notifyItemChanged(newPos)
                true
            }
        }

        private fun bindLive(holder: VH, uri: Uri, itemId: String) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
            liveJob?.cancel()
            liveJob = scope.launch(Dispatchers.IO) {
                val drawable = try {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeDrawable(source)
                } catch (_: Exception) {
                    null
                }
                withContext(Dispatchers.Main) {
                    if (liveItemId == itemId && holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                        if (drawable is AnimatedImageDrawable) {
                            holder.imageView.setImageDrawable(drawable)
                            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                            drawable.start()
                        } else if (drawable != null) {
                            holder.imageView.setImageDrawable(drawable)
                        }
                    }
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        private const val PAGE_SIZE = 24
        private const val MAX_QUERY_CODE_POINTS = 80
        private const val THUMB_AUTHORITY = "com.leanbitlab.leantype.gif.thumbs"
    }
}
