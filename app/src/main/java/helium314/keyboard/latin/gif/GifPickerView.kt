package helium314.keyboard.latin.gif

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import coil.size.Precision
import com.leanbitlab.leantype.gif.Contract
import com.leanbitlab.leantype.gif.GifItem
import com.leanbitlab.leantype.gif.IGifCallback
import com.leanbitlab.leantype.gif.IGifEngine
import com.leanbitlab.leantype.gif.ProviderInfo
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.dpToPx
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
    private var cursorPos = 0

    private var activeProviderId: String = ""
    private var activeProviderName: String = ""
    private var activeProviderKinds: Int = Contract.KINDS_GIF
    private var selectedKind: Int = Contract.KIND_GIF
    private var cachedProviders: List<ProviderInfo> = emptyList()
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
    private lateinit var cursorIndicator: View
    private lateinit var providerBadge: TextView
    private lateinit var clearQueryButton: ImageButton
    private lateinit var closeButton: ImageButton
    private lateinit var categoryTabsScroll: HorizontalScrollView
    private lateinit var tabRecent: TextView
    private lateinit var tabGifs: TextView
    private lateinit var tabStickers: TextView
    private lateinit var tabMemes: TextView
    private lateinit var tabEmojis: TextView
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
        cursorIndicator = findViewById(R.id.cursorIndicator)
        searchText.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateCursorPosition() }
        providerBadge = findViewById(R.id.providerBadge)
        clearQueryButton = findViewById(R.id.clearQueryButton)
        closeButton = findViewById(R.id.closeButton)
        categoryTabsScroll = findViewById(R.id.categoryTabsScroll)
        tabRecent = findViewById(R.id.tabRecent)
        tabGifs = findViewById(R.id.tabGifs)
        tabStickers = findViewById(R.id.tabStickers)
        tabMemes = findViewById(R.id.tabMemes)
        tabEmojis = findViewById(R.id.tabEmojis)
        recycler = findViewById(R.id.recycler)
        progress = findViewById(R.id.progress)
        statusContainer = findViewById(R.id.statusContainer)
        statusText = findViewById(R.id.statusText)
        statusAction = findViewById(R.id.statusAction)

        tabRecent.setOnClickListener { selectKind(KIND_RECENT) }
        tabGifs.setOnClickListener { selectKind(Contract.KIND_GIF) }
        tabStickers.setOnClickListener { selectKind(Contract.KIND_STICKER) }
        tabMemes.setOnClickListener { selectKind(Contract.KIND_MEME) }
        tabEmojis.setOnClickListener { selectKind(Contract.KIND_ANIMATED_EMOJI) }

        val lm = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        recycler.layoutManager = lm
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter

        closeButton.setOnClickListener { onCloseRequested?.invoke() }
        clearQueryButton.setOnClickListener {
            query.clear()
            cursorPos = 0
            renderQuery()
            reload(debounce = false)
        }
        @SuppressLint("ClickableViewAccessibility")
        searchText.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP && query.isNotEmpty()) {
                val layout = searchText.layout
                if (layout != null) {
                    val x = event.x - searchText.totalPaddingLeft + searchText.scrollX
                    val offset = layout.getOffsetForHorizontal(0, x)
                    cursorPos = offset.coerceIn(0, query.length)
                    renderQuery()
                }
            }
            false
        }
        providerBadge.setOnClickListener { toggleProvider() }

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
        cursorPos = 0
        nextPos = null
        isPaging = false
        hasMore = true
        selectedKind = Contract.KIND_GIF
        renderQuery()
        statusContainer.visibility = View.GONE
        providerBadge.visibility = View.GONE
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
        deleteSwipeStartPos = -1
        currentDeleteSwipePos = -1
        removeCallbacks(cursorBlinkRunnable)
        activeProviderId = ""
        activeProviderName = ""
        providerBadge.visibility = View.GONE
        loadSeq++
        queryJob?.cancel()
        pagingJob?.cancel()
        stateJob?.cancel()
        cancelCurrent()
        manager.release("picker")
        adapter.submit(emptyList())
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(cursorBlinkRunnable)
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
                deleteSwipeStartPos = -1
                currentDeleteSwipePos = -1
                if (cursorPos > 0) {
                    val prevPos = query.offsetByCodePoints(cursorPos, -1)
                    query.delete(prevPos, cursorPos)
                    cursorPos = prevPos
                    renderQuery()
                    reload(debounce = true)
                }
                return true
            }
            code == Constants.CODE_ENTER || code == '\n'.code -> {
                reload(debounce = false)
                return true
            }
            code == KeyCode.ARROW_LEFT -> {
                return onMoveCursor(-1)
            }
            code == KeyCode.ARROW_RIGHT -> {
                return onMoveCursor(1)
            }
            code >= 32 -> {
                if (query.codePointCount(0, query.length) < MAX_QUERY_CODE_POINTS) {
                    val str = String(Character.toChars(code))
                    query.insert(cursorPos, str)
                    cursorPos += str.length
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
                val str = String(Character.toChars(cp))
                query.insert(cursorPos, str)
                cursorPos += str.length
                added = true
            }
        }
        if (added) {
            renderQuery()
            reload(debounce = true)
        }
        return true
    }

    fun onMoveCursor(steps: Int): Boolean {
        if (query.isEmpty() || steps == 0) return false
        var pos = cursorPos
        if (steps > 0) {
            var c = steps
            while (c > 0 && pos < query.length) {
                pos = query.offsetByCodePoints(pos, 1)
                c--
            }
        } else {
            var c = -steps
            while (c > 0 && pos > 0) {
                pos = query.offsetByCodePoints(pos, -1)
                c--
            }
        }
        if (pos != cursorPos) {
            cursorPos = pos
            renderQuery()
            return true
        }
        return false
    }

    private var isCursorVisible = true
    private val cursorBlinkRunnable = object : Runnable {
        override fun run() {
            if (!opened || !isAttachedToWindow) return
            isCursorVisible = !isCursorVisible
            cursorIndicator.visibility = if (isCursorVisible) View.VISIBLE else View.INVISIBLE
            postDelayed(this, 500)
        }
    }

    private fun resetCursorBlink() {
        isCursorVisible = true
        cursorIndicator.visibility = View.VISIBLE
        removeCallbacks(cursorBlinkRunnable)
        if (opened && isAttachedToWindow) {
            postDelayed(cursorBlinkRunnable, 500)
        }
    }

    private fun updateCursorPosition() {
        val text = query.toString()
        if (text.isEmpty()) {
            cursorIndicator.translationX = 0f
        } else {
            val textBeforeCursor = text.substring(0, cursorPos.coerceIn(0, text.length))
            val textWidth = if (textBeforeCursor.isEmpty()) 0f else searchText.paint.measureText(textBeforeCursor)
            val delta = 4.dpToPx(resources).toFloat()
            val maxTranslation = (searchText.width - 20.dpToPx(resources)).toFloat().coerceAtLeast(0f)
            cursorIndicator.translationX = (textWidth + delta).coerceAtMost(maxTranslation)
        }
    }

    private fun renderQuery() {
        val text = query.toString()
        searchText.text = text
        clearQueryButton.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
        resetCursorBlink()
        updateCursorPosition()
    }

    private var deleteSwipeStartPos = -1
    private var currentDeleteSwipePos = -1

    fun onMoveDeletePointer(stepsX: Int, stepsY: Int) {
        if (query.isEmpty()) return
        if (deleteSwipeStartPos == -1) {
            deleteSwipeStartPos = cursorPos
            currentDeleteSwipePos = cursorPos
        }
        if (stepsY < 0) {
            currentDeleteSwipePos = 0
        } else if (stepsY > 0) {
            currentDeleteSwipePos = deleteSwipeStartPos
        } else if (stepsX != 0) {
            var pos = currentDeleteSwipePos
            if (stepsX > 0) {
                var c = stepsX
                while (c > 0 && pos < query.length) {
                    pos = query.offsetByCodePoints(pos, 1)
                    c--
                }
            } else {
                var c = -stepsX
                while (c > 0 && pos > 0) {
                    pos = query.offsetByCodePoints(pos, -1)
                    c--
                }
            }
            currentDeleteSwipePos = pos
        }
        renderQueryWithSelection(
            minOf(deleteSwipeStartPos, currentDeleteSwipePos),
            maxOf(deleteSwipeStartPos, currentDeleteSwipePos)
        )
    }

    fun onUpWithDeletePointerActive() {
        if (deleteSwipeStartPos != -1 && currentDeleteSwipePos != -1) {
            val start = minOf(deleteSwipeStartPos, currentDeleteSwipePos)
            val end = maxOf(deleteSwipeStartPos, currentDeleteSwipePos)
            if (start < end) {
                query.delete(start, end)
                cursorPos = start
                reload(debounce = true)
            }
        }
        deleteSwipeStartPos = -1
        currentDeleteSwipePos = -1
        renderQuery()
    }

    private fun renderQueryWithSelection(selStart: Int, selEnd: Int) {
        val text = query.toString()
        if (selStart >= 0 && selEnd > selStart && selEnd <= text.length) {
            val sb = android.text.SpannableStringBuilder(text)
            val textColor = Settings.getValues().mColors.get(ColorType.KEY_TEXT)
            val highlightColor = (textColor and 0x00FFFFFF) or 0x50000000
            sb.setSpan(
                android.text.style.BackgroundColorSpan(highlightColor),
                selStart,
                selEnd,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            searchText.text = sb
            cursorIndicator.visibility = View.INVISIBLE
            removeCallbacks(cursorBlinkRunnable)
        } else {
            renderQuery()
        }
    }

    private fun selectKind(kind: Int) {
        if (selectedKind == kind) return
        selectedKind = kind
        updateCategoryTabsUI()
        updateSearchHint()
        reload(debounce = false)
    }

    private fun updateCategoryTabsVisibility() {
        val hasStickers = (activeProviderKinds and Contract.KINDS_STICKER) != 0
        val hasMemes = (activeProviderKinds and Contract.KINDS_MEME) != 0
        val hasEmojis = (activeProviderKinds and Contract.KINDS_ANIMATED_EMOJI) != 0

        tabStickers.visibility = if (hasStickers) View.VISIBLE else View.GONE
        tabMemes.visibility = if (hasMemes) View.VISIBLE else View.GONE
        tabEmojis.visibility = if (hasEmojis) View.VISIBLE else View.GONE

        if (selectedKind != KIND_RECENT && (
            selectedKind == Contract.KIND_STICKER && !hasStickers ||
            selectedKind == Contract.KIND_MEME && !hasMemes ||
            selectedKind == Contract.KIND_ANIMATED_EMOJI && !hasEmojis
        )) {
            selectedKind = Contract.KIND_GIF
        }
        updateCategoryTabsUI()
    }

    private fun updateCategoryTabsUI() {
        val colors = Settings.getValues().mColors
        val activeBg = colors?.get(ColorType.ACTION_KEY_BACKGROUND) ?: 0x33888888
        val activeText = colors?.get(ColorType.KEY_TEXT) ?: 0xFFFFFFFF.toInt()
        val inactiveText = colors?.get(ColorType.KEY_HINT_TEXT) ?: 0x88FFFFFF.toInt()

        val tabs = listOf(
            tabRecent to KIND_RECENT,
            tabGifs to Contract.KIND_GIF,
            tabStickers to Contract.KIND_STICKER,
            tabMemes to Contract.KIND_MEME,
            tabEmojis to Contract.KIND_ANIMATED_EMOJI
        )
        for ((tab, kind) in tabs) {
            val isSelected = (selectedKind == kind)
            if (isSelected) {
                tab.backgroundTintList = ColorStateList.valueOf(activeBg)
                tab.setTextColor(activeText)
                tab.setTypeface(null, Typeface.BOLD)
            } else {
                tab.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                tab.setTextColor(inactiveText)
                tab.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    private fun updateSearchHint() {
        if (activeProviderId == "disabled") {
            searchText.hint = context.getString(R.string.gif_search_hint)
            return
        }
        val categoryName = when (selectedKind) {
            KIND_RECENT -> context.getString(R.string.gif_category_recent)
            Contract.KIND_STICKER -> context.getString(R.string.gif_category_stickers)
            Contract.KIND_MEME -> context.getString(R.string.gif_category_memes)
            Contract.KIND_ANIMATED_EMOJI -> context.getString(R.string.gif_category_emojis)
            else -> context.getString(R.string.gif_category_gifs)
        }
        val isKlipy = activeProviderId == "klipy"
        val isGiphy = activeProviderId == "giphy"
        val isLocal = activeProviderId == "local"
        searchText.hint = when {
            selectedKind == KIND_RECENT -> "Filter recent items"
            isKlipy -> "Search KLIPY $categoryName"
            isGiphy -> "Search GIPHY $categoryName"
            isLocal -> "Search local $categoryName"
            activeProviderName.isNotEmpty() -> "Search $categoryName ($activeProviderName)"
            else -> "Search $categoryName"
        }
    }

    private fun resolveProvider(engine: IGifEngine): String {
        val rawPref = context.prefs().getString(Settings.PREF_GIF_PROVIDER, "") ?: Settings.getValues().mGifProvider
        val list = try { engine.listProviders() } catch (_: Exception) { emptyList() }
        cachedProviders = list

        val prefs = context.prefs()
        val enabledConfigured = list.filter { it.configured && Settings.isGifProviderEnabled(prefs, it.id) }

        if (rawPref == "disabled" || enabledConfigured.isEmpty()) {
            activeProviderId = "disabled"
            activeProviderName = ""
            activeProviderKinds = 0
            post {
                providerBadge.visibility = View.GONE
                updateCategoryTabsVisibility()
                updateSearchHint()
                showStatus(context.getString(R.string.gif_disabled_message), context.getString(R.string.gif_open_settings)) {
                    openGifSettings()
                }
            }
            return ""
        }

        val target = enabledConfigured.firstOrNull { it.id == rawPref }
        if (rawPref.isNotEmpty() && target == null) {
            activeProviderId = "disabled"
            activeProviderName = ""
            activeProviderKinds = 0
            post {
                providerBadge.visibility = View.GONE
                updateCategoryTabsVisibility()
                updateSearchHint()
                showStatus(context.getString(R.string.gif_disabled_message), context.getString(R.string.gif_open_settings)) {
                    openGifSettings()
                }
            }
            return ""
        }

        val chosen = target ?: enabledConfigured.first()
        val id = chosen.id
        val name = chosen.displayName
        val kinds = chosen.kinds
        activeProviderId = id
        activeProviderName = name
        activeProviderKinds = if (kinds != 0) kinds else Contract.KINDS_GIF
        post {
            updateCategoryTabsVisibility()
            updateSearchHint()
            if (activeProviderName.isNotEmpty()) {
                providerBadge.text = activeProviderName.uppercase()
                providerBadge.visibility = View.VISIBLE
            } else {
                providerBadge.visibility = View.GONE
            }
        }
        return id
    }

    private fun toggleProvider() {
        scope.launch {
            val list = manager.call { it.listProviders() }.getOrNull()
                ?.also { cachedProviders = it }
                ?: cachedProviders
            if (list.isEmpty()) return@launch

            val prefs = context.prefs()
            val candidates = list.filter { it.configured && Settings.isGifProviderEnabled(prefs, it.id) }
            if (candidates.isEmpty()) {
                activeProviderId = "disabled"
                activeProviderName = ""
                activeProviderKinds = 0
                providerBadge.visibility = View.GONE
                updateCategoryTabsVisibility()
                updateSearchHint()
                showStatus(context.getString(R.string.gif_disabled_message), context.getString(R.string.gif_open_settings)) {
                    openGifSettings()
                }
                return@launch
            }

            val currentIndex = candidates.indexOfFirst { it.id == activeProviderId }
            val nextProvider = if (currentIndex >= 0) {
                if (candidates.size <= 1) return@launch
                candidates[(currentIndex + 1) % candidates.size]
            } else {
                candidates[0]
            }
            if (nextProvider.id == activeProviderId) return@launch

            activeProviderId = nextProvider.id
            activeProviderName = nextProvider.displayName
            val kinds = nextProvider.kinds
            activeProviderKinds = if (kinds != 0) kinds else Contract.KINDS_GIF
            prefs.edit().putString(Settings.PREF_GIF_PROVIDER, nextProvider.id).apply()

            providerBadge.text = activeProviderName.uppercase()
            providerBadge.visibility = View.VISIBLE
            updateCategoryTabsVisibility()
            updateSearchHint()

            Toast.makeText(
                context,
                context.getString(R.string.gif_switched_to, activeProviderName),
                Toast.LENGTH_SHORT
            ).show()

            reload(debounce = false)
        }
    }

    private fun applyTheme() {
        val colors = Settings.getValues().mColors ?: return
        val stripBg = colors.get(ColorType.STRIP_BACKGROUND)
        val textCol = colors.get(ColorType.KEY_TEXT)
        setBackgroundColor(stripBg)

        searchContainer.background = null
        cursorIndicator.setBackgroundColor(textCol)
        statusAction.backgroundTintList = ColorStateList.valueOf(colors.get(ColorType.ACTION_KEY_BACKGROUND))

        val iconColor = colors.get(ColorType.KEY_ICON)
        searchIcon.imageTintList = ColorStateList.valueOf(iconColor)
        clearQueryButton.imageTintList = ColorStateList.valueOf(iconColor)
        closeButton.imageTintList = ColorStateList.valueOf(iconColor)
        progress.indeterminateTintList = ColorStateList.valueOf(iconColor)

        searchText.setTextColor(colors.get(ColorType.KEY_TEXT))
        searchText.setHintTextColor(colors.get(ColorType.KEY_HINT_TEXT))
        val pillBg = colors.get(ColorType.ACTION_KEY_BACKGROUND)
        providerBadge.backgroundTintList = ColorStateList.valueOf(pillBg)
        providerBadge.setTextColor(colors.get(ColorType.KEY_TEXT))
        statusText.setTextColor(colors.get(ColorType.KEY_HINT_TEXT))
        statusAction.setTextColor(colors.get(ColorType.KEY_TEXT))

        updateCategoryTabsUI()
    }

    private fun reload(debounce: Boolean) {
        val seq = ++loadSeq
        queryJob?.cancel()
        pagingJob?.cancel()
        isPaging = false
        nextPos = null
        hasMore = true
        if (activeProviderId == "disabled") {
            showStatus(context.getString(R.string.gif_disabled_message), context.getString(R.string.gif_open_settings)) {
                openGifSettings()
            }
            return
        }
        statusContainer.visibility = View.GONE

        if (selectedKind == KIND_RECENT) {
            queryJob = scope.launch {
                if (debounce) delay(150)
                if (!isActive || seq != loadSeq) return@launch
                cancelCurrent()
                progress.visibility = View.GONE
                val recents = GifRecentManager.getRecents(context)
                val q = query.toString().trim()
                val filtered = if (q.isNotEmpty()) {
                    recents.filter {
                        (it.attribution ?: "").contains(q, ignoreCase = true) ||
                        it.id.contains(q, ignoreCase = true)
                    }
                } else {
                    recents
                }
                adapter.submit(filtered)
                if (filtered.isEmpty()) {
                    showStatus(context.getString(R.string.gif_no_recent_items))
                } else {
                    statusContainer.visibility = View.GONE
                }
            }
            return
        }

        queryJob = scope.launch {
            if (debounce) delay(300)
            if (!isActive || seq != loadSeq) return@launch
            val q = query.toString().trim()
            runRequest(isPage = false, seq = seq) { engine, id, cb ->
                val provider = if (activeProviderId.isNotEmpty()) activeProviderId else resolveProvider(engine)
                val isV2 = manager.contractVersion >= 2
                if (q.isEmpty()) {
                    if (isV2) {
                        engine.trendingKind(id, provider, selectedKind, "", PAGE_SIZE, cb)
                    } else {
                        engine.trending(id, provider, "", PAGE_SIZE, cb)
                    }
                } else {
                    if (isV2) {
                        engine.searchKind(id, provider, selectedKind, q, "", PAGE_SIZE, cb)
                    } else {
                        engine.search(id, provider, q, "", PAGE_SIZE, cb)
                    }
                }
            }
        }
    }

    private fun loadNextPage() {
        if (selectedKind == KIND_RECENT) return
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
                val isV2 = manager.contractVersion >= 2
                if (q.isEmpty()) {
                    if (isV2) {
                        engine.trendingKind(id, provider, selectedKind, pos, PAGE_SIZE, cb)
                    } else {
                        engine.trending(id, provider, pos, PAGE_SIZE, cb)
                    }
                } else {
                    if (isV2) {
                        engine.searchKind(id, provider, selectedKind, q, pos, PAGE_SIZE, cb)
                    } else {
                        engine.search(id, provider, q, pos, PAGE_SIZE, cb)
                    }
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

                GifInserter.commit(context.applicationContext, pfd, mime, ic, ei, item.kind)
                GifRecentManager.addRecent(context.applicationContext, item)
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

        fun setInsertingId(id: String?) {
            if (insertingId == id) return
            val oldId = insertingId
            insertingId = id
            val oldPos = items.indexOfFirst { it.id == oldId }
            val newPos = items.indexOfFirst { it.id == insertingId }
            if (oldPos != -1) notifyItemChanged(oldPos)
            if (newPos != -1 && newPos != oldPos) notifyItemChanged(newPos)
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
            holder.imageView.dispose()
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
                holder.imageView.dispose()
                stopAnimation(holder.imageView)
                bindLive(holder, uri, item.id)
            } else {
                holder.imageView.dispose()
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
                        holder.imageView.dispose()
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
        private const val KIND_RECENT = -1
        private const val PAGE_SIZE = 24
        private const val MAX_QUERY_CODE_POINTS = 80
        private const val THUMB_AUTHORITY = "com.leanbitlab.leantype.gif.thumbs"
    }
}
