/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2026 LeanBitLab
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.emoji

import kotlin.math.abs
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.InlineCursorTextView
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.PointerTracker
import helium314.keyboard.keyboard.internal.KeyDrawParams
import helium314.keyboard.keyboard.internal.KeyVisualAttributes
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.SingleDictionaryFacilitator
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.DictionaryFactory
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.getPlatformDialogThemeContext
import helium314.keyboard.settings.SettingsActivity
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.HashSet

class EmojiPalettesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.emojiPalettesViewStyle
) : LinearLayout(context, attrs, defStyleAttr), View.OnClickListener, EmojiViewCallback {

    private class PagerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var mCategoryId: Long = 0
    }

    private inner class PagerAdapter(private val pager: ViewPager2) : RecyclerView.Adapter<PagerViewHolder>() {
        private var mInitialized = false
        private val mViews = HashMap<Int, RecyclerView>(mEmojiCategory.getShownCategories().size)

        fun getRecyclerViewForPosition(pos: Int): RecyclerView? = mViews[pos]

        init {
            setHasStableIds(true)
            pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val categoryId = getItemId(position).toInt()
                    setCurrentCategoryId(categoryId, false)
                    val recyclerView = mViews[position]
                    if (recyclerView != null) {
                        updateState(recyclerView, categoryId.toLong())
                    }
                }
            })
        }

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            recyclerView.setItemViewCacheSize(mEmojiCategory.getShownCategories().size)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagerViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.emoji_category_view, parent, false)
            val viewHolder = PagerViewHolder(view)
            val emojiRecyclerView = getRecyclerView(view)

            emojiRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateState(recyclerView, viewHolder.mCategoryId)
                }
            })

            emojiRecyclerView.setPersistentDrawingCache(PERSISTENT_NO_CACHE)
            return viewHolder
        }

        override fun onBindViewHolder(holder: PagerViewHolder, position: Int) {
            holder.mCategoryId = getItemId(position)
            val recyclerView = getRecyclerView(holder.itemView)
            mViews[position] = recyclerView
            recyclerView.adapter = EmojiPalettesAdapter(mEmojiCategory, holder.mCategoryId.toInt(), this@EmojiPalettesView)
            setupEmoticonGroups(holder.itemView, recyclerView, holder.mCategoryId.toInt() == EmojiCategory.ID_EMOTICONS)

            if (!mInitialized) {
                recyclerView.scrollToPosition(mEmojiCategory.getCurrentCategoryPageId())
                mInitialized = true
            }
        }

        override fun getItemCount(): Int {
            return mEmojiCategory.getShownCategories().size
        }

        override fun onViewDetachedFromWindow(holder: PagerViewHolder) {
            if (holder.mCategoryId == EmojiCategory.ID_RECENTS.toLong()) {
                getRecentsKeyboard().flushPendingRecentKeys()
                getRecyclerView(holder.itemView).adapter?.notifyDataSetChanged()
            }
        }

        override fun getItemId(position: Int): Long {
            return mEmojiCategory.getShownCategories()[position].mCategoryId.toLong()
        }

        private fun updateState(recyclerView: RecyclerView, categoryId: Long) {
            if (categoryId.toInt() != mEmojiCategory.getCurrentCategoryId()) {
                return
            }

            val offset = recyclerView.computeVerticalScrollOffset()
            val extent = recyclerView.computeVerticalScrollExtent()
            val range = recyclerView.computeVerticalScrollRange()
            val percentage = offset / (range - extent).toFloat()

            val currentCategorySize = mEmojiCategory.getCurrentCategoryPageCount()
            val a = (percentage * currentCategorySize).toInt()
            val b = percentage * currentCategorySize - a
            mEmojiCategoryPageIndicatorView?.setCategoryPageId(currentCategorySize, a, b)

            val layoutManager = recyclerView.layoutManager as LinearLayoutManager?
            val firstCompleteVisibleBoard = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: 0
            val firstVisibleBoard = layoutManager?.findFirstVisibleItemPosition() ?: 0
            mEmojiCategory.setCurrentCategoryPageId(
                if (firstCompleteVisibleBoard > 0) firstCompleteVisibleBoard else firstVisibleBoard
            )
        }
    }

    private var initialized = false
    private var mColors: Colors
    private val mEmojiLayoutParams: EmojiLayoutParams
    private var mTabStrip: LinearLayout? = null
    private var mEmojiCategoryPageIndicatorView: EmojiCategoryPageIndicatorView? = null
    private var mKeyboardActionListener: KeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER
    private val mEmojiCategory: EmojiCategory
    private var mPager: ViewPager2? = null
    private var mPagerAdapter: PagerAdapter? = null

    private var mSearchContainer: LinearLayout? = null
    private var mSearchResultsList: RecyclerView? = null
    private var mSearchEmptyView: TextView? = null
    private var mSearchAdapter: EmojiSearchAdapter? = null
    private var mSearchBar: InlineCursorTextView? = null
    private var mInSearchMode = false
    private var mIsDownloadingEmojiDict = false
    private var mDownloadBtn: Button? = null
    private var mOriginalActionListener: KeyboardActionListener? = null
    private var mSearchKeyboardLayoutSet: KeyboardLayoutSet? = null
    private var mSearchAlphabetKeyboardId = KeyboardId.ELEMENT_ALPHABET
    private var isCursorBlinkVisible = true
    private var mIgnoreNextShiftRelease = false
    private var mDeleteSwipeStartSel = -1
    private var mCurrentDeleteSwipeStart = -1

    private val cursorBlinkRunnable = object : Runnable {
        override fun run() {
            if (!mInSearchMode) return
            isCursorBlinkVisible = !isCursorBlinkVisible
            mSearchBar?.showInlineCursor = isCursorBlinkVisible
            postDelayed(this, 500)
        }
    }

    private fun resetCursorBlink() {
        isCursorBlinkVisible = true
        mSearchBar?.showInlineCursor = true
        removeCallbacks(cursorBlinkRunnable)
        postDelayed(cursorBlinkRunnable, 500)
    }

    val isInSearchMode: Boolean get() = mInSearchMode

    fun onSearchTextInput(text: String?) {
        if (!mInSearchMode || text == null) return
        val searchBar = mSearchBar ?: return
        val content = searchBar.text ?: return
        var sel = searchBar.selectionStart
        if (sel < 0) sel = content.length
        content.insert(sel, text)
        resetCursorBlink()
        val bottomRow = findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)
        if (bottomRow.keyboard?.mId?.mElementId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED) {
            mSearchKeyboardLayoutSet?.let { layoutSet ->
                PointerTracker.switchTo(bottomRow)
                bottomRow.setKeyboard(layoutSet.getKeyboard(mSearchAlphabetKeyboardId))
                bottomRow.setKeyPreviewPopupEnabled(Settings.getValues().mKeyPreviewPopupOn)
            }
        }
    }

    private var mEditorInfo: EditorInfo? = null

    init {
        mColors = Settings.getValues().mColors
        val builder = KeyboardLayoutSet.Builder(context, null)
        mEmojiLayoutParams = EmojiLayoutParams(context)
        builder.setSubtype(RichInputMethodSubtype.emojiSubtype)
        builder.setKeyboardGeometry(
            ResourceUtils.getKeyboardWidth(context, Settings.getValues()),
            mEmojiLayoutParams.emojiKeyboardHeight
        )
        val layoutSet = builder.build()
        val emojiPalettesViewAttr = context.obtainStyledAttributes(
            attrs, R.styleable.EmojiPalettesView, defStyleAttr, R.style.EmojiPalettesView
        )
        mEmojiCategory = EmojiCategory(context, layoutSet, emojiPalettesViewAttr)
        emojiPalettesViewAttr.recycle()
        fitsSystemWindows = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!mInSearchMode && mPager != null) {
            mEmojiLayoutParams.updateDimensions(context)
            val lp = mPager?.layoutParams as? LinearLayout.LayoutParams
            if (lp != null && lp.height != mEmojiLayoutParams.emojiKeyboardHeight) {
                lp.height = mEmojiLayoutParams.emojiKeyboardHeight
                mPager?.layoutParams = lp
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val res = context.resources
        val isFloating = helium314.keyboard.latin.FloatingKeyboardManager.isFloatingMode(context)
        val padH = if (isFloating) 0 else (paddingLeft + paddingRight)
        val padV = if (isFloating) 0 else (paddingTop + paddingBottom)
        val width = ResourceUtils.getKeyboardWidth(context, Settings.getValues()) + padH
        if (!mInSearchMode) {
            val height = ResourceUtils.getSecondaryKeyboardHeight(res, Settings.getValues()) + padV
            setMeasuredDimension(width, height)
        } else {
            setMeasuredDimension(width, measuredHeight)
        }
        mEmojiCategoryPageIndicatorView?.mWidth = width
    }

    fun initialize() {
        if (initialized) return
        mEmojiCategory.initialize()
        val tabStrip = KeyboardSwitcher.getInstance().emojiTabStrip as LinearLayout?
        mTabStrip = tabStrip

        setupCategoryTabs()

        val pager: ViewPager2 = findViewById(R.id.emoji_pager)
        mPager = pager
        mPagerAdapter = PagerAdapter(pager).also { pager.adapter = it }
        mEmojiLayoutParams.setEmojiListProperties(pager)
        val indicatorView: EmojiCategoryPageIndicatorView = findViewById(R.id.emoji_category_page_id_view)
        mEmojiCategoryPageIndicatorView = indicatorView
        mEmojiLayoutParams.setCategoryPageIdViewProperties(indicatorView)

        mSearchContainer = findViewById(R.id.emoji_search_container)
        val resultsList: RecyclerView = findViewById(R.id.emoji_search_results)
        mSearchResultsList = resultsList
        mSearchEmptyView = findViewById(R.id.emoji_search_empty_view)

        resultsList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        val searchAdapter = EmojiSearchAdapter { emoji ->
            mKeyboardActionListener.onTextInput(emoji)
            addRecentKey(emoji)
            stopSearchMode()
        }
        mSearchAdapter = searchAdapter
        resultsList.adapter = searchAdapter

        findViewById<View>(R.id.emoji_search_close_btn).setOnClickListener { stopSearchMode() }

        val searchBar: InlineCursorTextView = findViewById(R.id.emoji_search_bar)
        mSearchBar = searchBar
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performSearch(s.toString())
            }
        })

        setCurrentCategoryId(mEmojiCategory.getCurrentCategoryId(), true)
        indicatorView.setColors(
            mColors.get(ColorType.EMOJI_CATEGORY_SELECTED),
            mColors.get(ColorType.STRIP_BACKGROUND)
        )
        initialized = true
    }

    private fun addTab(host: LinearLayout, categoryId: Int) {
        val iconView = ImageView(context)
        mColors.setBackground(iconView, ColorType.STRIP_BACKGROUND)
        mColors.setColor(iconView, ColorType.EMOJI_CATEGORY)
        iconView.scaleType = ImageView.ScaleType.CENTER

        if (categoryId == ID_SEARCH_TAB) {
            iconView.setImageResource(R.drawable.sym_keyboard_search_lxx)
            iconView.contentDescription = "Search Emojis"
        } else {
            iconView.setImageResource(mEmojiCategory.getCategoryTabIcon(categoryId))
            iconView.contentDescription = mEmojiCategory.getAccessibilityDescription(categoryId)
        }

        iconView.tag = categoryId.toLong()
        host.addView(iconView)
        iconView.layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        iconView.setOnClickListener(this)

        if (categoryId == mEmojiCategory.getCurrentCategoryId()) {
            iconView.setBackgroundResource(R.drawable.toolbar_key_background)
            Settings.getValues().mColors.setColor(iconView.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
        }

        if (categoryId == EmojiCategory.ID_RECENTS) {
            iconView.setOnLongClickListener {
                val dialog = AlertDialog.Builder(getPlatformDialogThemeContext(context))
                    .setTitle(R.string.clear_emoji_history_title)
                    .setMessage(R.string.clear_emoji_history_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        getRecentsKeyboard().clearRecentKeys()
                        mPager?.adapter?.notifyDataSetChanged()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
                val window = dialog.window
                if (window != null) {
                    val lp = window.attributes
                    lp.token = iconView.windowToken
                    lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
                    window.attributes = lp
                    window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                }
                dialog.show()
                true
            }
        }
    }

    private fun toPx(dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
    }

    private fun setupCategoryTabs() {
        val tabStrip = mTabStrip ?: return
        tabStrip.removeAllViews()
        if (Settings.getValues().mSecondaryStripVisible) {
            addTab(tabStrip, ID_SEARCH_TAB)
            val splitToolbar = Settings.getValues().mSplitToolbar
            for (properties in mEmojiCategory.getShownCategories()) {
                if (splitToolbar && properties.mCategoryId == EmojiCategory.ID_RECENTS) continue
                addTab(tabStrip, properties.mCategoryId)
            }
        }
    }

    private fun startSearchMode() {
        Log.d("EmojiSearch", "startSearchMode() called")
        if (mInSearchMode) return
        mInSearchMode = true

        mTabStrip?.removeAllViews()
        val ctx = context

        val stripContainer = LinearLayout(ctx)
        stripContainer.orientation = HORIZONTAL
        stripContainer.layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        mTabStrip?.addView(stripContainer)

        val inputContainer = LinearLayout(ctx)
        inputContainer.orientation = HORIZONTAL
        inputContainer.gravity = android.view.Gravity.CENTER_VERTICAL
        val inputWeight = if (Settings.getValues().mSplitToolbar) 1.0f else 0.4f
        inputContainer.layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, inputWeight)
        inputContainer.setPadding(toPx(4f), 0, toPx(4f), 0)

        val searchIcon = ImageView(ctx)
        searchIcon.setImageResource(R.drawable.sym_keyboard_search_rounded)
        mColors.setColor(searchIcon, ColorType.KEY_TEXT)
        searchIcon.alpha = 0.7f
        val iconSize = toPx(20f)
        searchIcon.layoutParams = LayoutParams(iconSize, iconSize)
        inputContainer.addView(searchIcon)

        val searchBar = InlineCursorTextView(ctx).apply {
            background = null
            hint = "Search"
            val textColor = mColors.get(ColorType.KEY_TEXT)
            setTextColor(textColor)
            cursorColor = textColor
            setHintTextColor(textColor and 0x00FFFFFF or -0x80000000)
            textSize = 14f
            maxLines = 1
            setHorizontallyScrolling(true)
            isHorizontalScrollBarEnabled = false
            setPadding(toPx(4f), 0, toPx(4f), 0)
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            isFocusable = false
            isFocusableInTouchMode = false
            var touchDownX = 0f
            var touchDownY = 0f
            val slop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchDownX = event.x
                        touchDownY = event.y
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP -> {
                        if (abs(event.x - touchDownX) < slop && abs(event.y - touchDownY) < slop) {
                            val l = layout
                            if (l != null) {
                                val x = event.x - totalPaddingLeft + scrollX
                                val y = event.y - totalPaddingTop + scrollY
                                val line = l.getLineForVertical(y.toInt().coerceIn(0, (l.height - 1).coerceAtLeast(0)))
                                val offset = l.getOffsetForHorizontal(line, x)
                                setSelection(offset.coerceIn(0, text.length))
                                resetCursorBlink()
                            }
                        }
                    }
                }
                false
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    performSearch(s.toString())
                }
            })
        }
        mSearchBar = searchBar
        inputContainer.addView(searchBar)

        val closeBtn = ImageButton(ctx)
        closeBtn.setImageResource(R.drawable.ic_close_rounded)
        closeBtn.background = null
        mColors.setColor(closeBtn, ColorType.KEY_TEXT)
        closeBtn.scaleType = ImageView.ScaleType.CENTER_INSIDE
        closeBtn.setOnClickListener { stopSearchMode() }
        val btnSize = toPx(48f)
        closeBtn.layoutParams = LayoutParams(btnSize, btnSize)
        inputContainer.addView(closeBtn)

        stripContainer.addView(inputContainer)

        if (!Settings.getValues().mSplitToolbar) {
            val divider = View(ctx)
            divider.setBackgroundColor(0x55888888)
            val divParams = LayoutParams(toPx(1f), ViewGroup.LayoutParams.MATCH_PARENT)
            divParams.setMargins(0, toPx(4f), 0, toPx(4f))
            divider.layoutParams = divParams
            stripContainer.addView(divider)
        }

        if (Settings.getValues().mSplitToolbar) {
            mSearchAdapter = null
            updateSplitToolbarEmojiSuggestions()
        } else if (sDictionaryFacilitator == null) {
            val downloadBtn = Button(ctx)
            downloadBtn.text = ctx.getString(R.string.button_download)
            downloadBtn.textSize = 12f
            downloadBtn.isAllCaps = false
            downloadBtn.setOnClickListener {
                if ("standard" == BuildConfig.FLAVOR || "standardfull" == BuildConfig.FLAVOR) {
                    downloadEmojiDictionary()
                    downloadBtn.text = ctx.getString(R.string.downloading)
                    downloadBtn.isEnabled = false
                } else {
                    val intent = Intent(ctx, SettingsActivity::class.java)
                    intent.putExtra("screen", "dictionaries")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                }
            }
            downloadBtn.layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.6f)
            stripContainer.addView(downloadBtn)
            mDownloadBtn = downloadBtn
        } else {
            val resultsList = RecyclerView(ctx)
            resultsList.layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.6f)
            resultsList.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)

            mSearchAdapter = EmojiSearchAdapter { emoji ->
                mKeyboardActionListener.onTextInput(emoji)
                addRecentKey(emoji)
            }
            resultsList.adapter = mSearchAdapter
            stripContainer.addView(resultsList)
        }

        mEmojiCategoryPageIndicatorView?.visibility = View.GONE
        mPager?.visibility = View.GONE
        mSearchContainer?.visibility = View.GONE

        mIgnoreNextShiftRelease = false
        mDeleteSwipeStartSel = -1
        mCurrentDeleteSwipeStart = -1
        resetCursorBlink()

        val bottomRow = findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)
        mOriginalActionListener = mKeyboardActionListener

        bottomRow.setKeyboardActionListener(object : KeyboardActionListener {
            private var mLastShiftPressTime = 0L

            override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) {
                val searchBar = mSearchBar ?: return
                val content = searchBar.text ?: return
                var selStart = searchBar.selectionStart
                var selEnd = searchBar.selectionEnd
                if (selStart < 0) selStart = content.length
                if (selEnd < 0) selEnd = content.length
                val minSel = minOf(selStart, selEnd)
                val maxSel = maxOf(selStart, selEnd)

                if (primaryCode != KeyCode.SHIFT) {
                    mLastShiftPressTime = 0L
                }
                if (primaryCode == KeyCode.DELETE) {
                    if (minSel != maxSel) {
                        content.delete(minSel, maxSel)
                    } else if (minSel > 0) {
                        content.delete(minSel - 1, minSel)
                    }
                    resetCursorBlink()
                } else if (primaryCode == Constants.CODE_SPACE) {
                    content.replace(minSel, maxSel, " ")
                    resetCursorBlink()
                } else if (primaryCode == KeyCode.ARROW_LEFT) {
                    if (minSel > 0) {
                        searchBar.setSelection(minSel - 1)
                        resetCursorBlink()
                    }
                } else if (primaryCode == KeyCode.ARROW_RIGHT) {
                    if (maxSel < content.length) {
                        searchBar.setSelection(maxSel + 1)
                        resetCursorBlink()
                    }
                } else if (primaryCode > 0) {
                    content.replace(minSel, maxSel, primaryCode.toChar().toString())
                    resetCursorBlink()
                    val bottomRow = findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)
                    if (bottomRow.keyboard?.mId?.mElementId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED) {
                        mSearchKeyboardLayoutSet?.let { layoutSet ->
                            PointerTracker.switchTo(bottomRow)
                            bottomRow.setKeyboard(layoutSet.getKeyboard(mSearchAlphabetKeyboardId))
                            bottomRow.setKeyPreviewPopupEnabled(Settings.getValues().mKeyPreviewPopupOn)
                        }
                    }
                } else if (primaryCode == Constants.CODE_ENTER) {
                    stopSearchMode()
                } else if (primaryCode == KeyCode.SYMBOL || primaryCode == KeyCode.SYMBOL_ALPHA || primaryCode == KeyCode.ALPHA) {
                    mSearchKeyboardLayoutSet?.let { layoutSet ->
                        val bottomRow = findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)
                        val currentElementId = bottomRow.keyboard?.mId?.mElementId ?: KeyboardId.ELEMENT_ALPHABET
                        val isOnSymbols = currentElementId == KeyboardId.ELEMENT_SYMBOLS || currentElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED
                        val targetId = if (isOnSymbols) mSearchAlphabetKeyboardId else KeyboardId.ELEMENT_SYMBOLS
                        PointerTracker.switchTo(bottomRow)
                        bottomRow.setKeyboard(layoutSet.getKeyboard(targetId))
                        bottomRow.setKeyPreviewPopupEnabled(Settings.getValues().mKeyPreviewPopupOn)
                    }
                } else if (primaryCode == KeyCode.SHIFT || primaryCode == KeyCode.CAPS_LOCK) {
                    if (mIgnoreNextShiftRelease) {
                        mIgnoreNextShiftRelease = false
                        return
                    }
                    mSearchKeyboardLayoutSet?.let { layoutSet ->
                        val bottomRow = findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)
                        val currentElementId = bottomRow.keyboard?.mId?.mElementId ?: KeyboardId.ELEMENT_ALPHABET
                        val now = android.os.SystemClock.uptimeMillis()
                        val isDoubleTap = primaryCode == KeyCode.SHIFT && mLastShiftPressTime > 0L && (now - mLastShiftPressTime < android.view.ViewConfiguration.getDoubleTapTimeout())
                        mLastShiftPressTime = if (primaryCode == KeyCode.SHIFT && !isDoubleTap) now else 0L

                        val targetId = when (currentElementId) {
                            KeyboardId.ELEMENT_SYMBOLS -> KeyboardId.ELEMENT_SYMBOLS_SHIFTED
                            KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> KeyboardId.ELEMENT_SYMBOLS
                            KeyboardId.ELEMENT_ALPHABET -> {
                                if (primaryCode == KeyCode.CAPS_LOCK) KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED
                                else KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED
                            }
                            KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED -> {
                                if (primaryCode == KeyCode.CAPS_LOCK || isDoubleTap) KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED
                                else mSearchAlphabetKeyboardId
                            }
                            KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED,
                            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED -> mSearchAlphabetKeyboardId
                            else -> mSearchAlphabetKeyboardId
                        }
                        PointerTracker.switchTo(bottomRow)
                        bottomRow.setKeyboard(layoutSet.getKeyboard(targetId))
                        bottomRow.setKeyPreviewPopupEnabled(Settings.getValues().mKeyPreviewPopupOn)
                    }
                } else if (primaryCode == KeyCode.EMOJI || primaryCode == KeyCode.CLIPBOARD) {
                    stopSearchMode()
                    if (primaryCode == KeyCode.CLIPBOARD) {
                        mOriginalActionListener?.onCodeInput(primaryCode, x, y, isKeyRepeat)
                    }
                } else if (primaryCode == KeyCode.LANGUAGE_SWITCH ||
                    primaryCode == KeyCode.CUSTOM1 || primaryCode == KeyCode.CUSTOM2 ||
                    primaryCode == KeyCode.CUSTOM3 || primaryCode == KeyCode.CUSTOM4 ||
                    primaryCode == KeyCode.CUSTOM5
                ) {
                    // Ignore
                } else {
                    mOriginalActionListener?.onCodeInput(primaryCode, x, y, isKeyRepeat)
                }
            }

            override fun onPressKey(p: Int, r: Int, s: Boolean, h: HapticEvent) {
                if (p != KeyCode.SYMBOL && p != KeyCode.ALPHA && p != KeyCode.NUMPAD &&
                    p != KeyCode.SYMBOL_ALPHA && p != KeyCode.SHIFT && p != KeyCode.EMOJI &&
                    p != KeyCode.CLIPBOARD && p != KeyCode.LANGUAGE_SWITCH &&
                    p != KeyCode.CUSTOM1 && p != KeyCode.CUSTOM2 && p != KeyCode.CUSTOM3 &&
                    p != KeyCode.CUSTOM4 && p != KeyCode.CUSTOM5
                ) {
                    mOriginalActionListener?.onPressKey(p, r, s, h)
                }
            }

            override fun onReleaseKey(p: Int, w: Boolean) {
                if (p != KeyCode.SYMBOL && p != KeyCode.ALPHA && p != KeyCode.NUMPAD &&
                    p != KeyCode.SYMBOL_ALPHA && p != KeyCode.SHIFT && p != KeyCode.EMOJI &&
                    p != KeyCode.CLIPBOARD && p != KeyCode.LANGUAGE_SWITCH &&
                    p != KeyCode.CUSTOM1 && p != KeyCode.CUSTOM2 && p != KeyCode.CUSTOM3 &&
                    p != KeyCode.CUSTOM4 && p != KeyCode.CUSTOM5
                ) {
                    mOriginalActionListener?.onReleaseKey(p, w)
                }
            }

            override fun onTextInput(t: String?) {
                if (t != null) {
                    val searchBar = mSearchBar ?: return
                    val content = searchBar.text ?: return
                    var selStart = searchBar.selectionStart
                    var selEnd = searchBar.selectionEnd
                    if (selStart < 0) selStart = content.length
                    if (selEnd < 0) selEnd = content.length
                    val minSel = minOf(selStart, selEnd)
                    val maxSel = maxOf(selStart, selEnd)
                    content.replace(minSel, maxSel, t)
                    resetCursorBlink()
                    val bottomRow = findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)
                    if (bottomRow.keyboard?.mId?.mElementId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED) {
                        mSearchKeyboardLayoutSet?.let { layoutSet ->
                            PointerTracker.switchTo(bottomRow)
                            bottomRow.setKeyboard(layoutSet.getKeyboard(mSearchAlphabetKeyboardId))
                            bottomRow.setKeyPreviewPopupEnabled(Settings.getValues().mKeyPreviewPopupOn)
                        }
                    }
                }
            }

            override fun onImageSelected(imageUri: String) {}
            override fun onLongPressKey(p: Int) {
                if (p == KeyCode.SHIFT) {
                    mIgnoreNextShiftRelease = true
                    mSearchKeyboardLayoutSet?.let { layoutSet ->
                        val bottomRow = findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)
                        bottomRow.setKeyboard(layoutSet.getKeyboard(KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED))
                        bottomRow.setKeyPreviewPopupEnabled(Settings.getValues().mKeyPreviewPopupOn)
                    }
                    return
                }
                mOriginalActionListener?.onLongPressKey(p)
            }
            override fun onKeyDown(k: Int, e: android.view.KeyEvent): Boolean = false
            override fun onKeyUp(k: Int, e: android.view.KeyEvent): Boolean = false
            override fun onStartBatchInput() { mOriginalActionListener?.onStartBatchInput() }
            override fun onUpdateBatchInput(p: helium314.keyboard.latin.common.InputPointers) { mOriginalActionListener?.onUpdateBatchInput(p) }
            override fun onEndBatchInput(p: helium314.keyboard.latin.common.InputPointers) { mOriginalActionListener?.onEndBatchInput(p) }
            override fun onCancelBatchInput() { mOriginalActionListener?.onCancelBatchInput() }
            
            override fun onCancelInput() {
                val searchBar = mSearchBar
                if (mDeleteSwipeStartSel >= 0 && searchBar != null) {
                    searchBar.setSelection(mDeleteSwipeStartSel.coerceIn(0, searchBar.text?.length ?: 0))
                }
                mDeleteSwipeStartSel = -1
                mCurrentDeleteSwipeStart = -1
                resetCursorBlink()
            }

            override fun onFinishSlidingInput() {}
            override fun onCustomRequest(r: Int): Boolean = false
            
            override fun onHorizontalSpaceSwipe(s: Int): Boolean {
                val searchBar = mSearchBar ?: return false
                val text = searchBar.text ?: return false
                if (text.isEmpty()) return false
                val rtl = RichInputMethodManager.getInstance().currentSubtype.isRtlSubtype
                val steps = if (rtl) -s else s

                val cur = searchBar.selectionStart.takeIf { it >= 0 } ?: text.length
                val newPos = (cur + steps).coerceIn(0, text.length)
                if (newPos != cur) {
                    searchBar.setSelection(newPos)
                    resetCursorBlink()
                }
                return true
            }

            override fun onVerticalSpaceSwipe(s: Int, action: Int): Boolean = false
            override fun onEndSpaceSwipe() {}
            override fun toggleNumpad(w: Boolean, f: Boolean): Boolean = false

            override fun onMoveDeletePointer(s: Int) {
                val searchBar = mSearchBar ?: return
                val text = searchBar.text ?: return
                if (text.isEmpty()) return

                if (mDeleteSwipeStartSel == -1) {
                    val cur = searchBar.selectionStart.takeIf { it >= 0 } ?: text.length
                    mDeleteSwipeStartSel = cur
                    mCurrentDeleteSwipeStart = cur
                }

                val rtl = RichInputMethodManager.getInstance().currentSubtype.isRtlSubtype
                val steps = if (rtl) -s else s

                mCurrentDeleteSwipeStart += steps
                if (mCurrentDeleteSwipeStart < 0) mCurrentDeleteSwipeStart = 0
                if (mCurrentDeleteSwipeStart > mDeleteSwipeStartSel) mCurrentDeleteSwipeStart = mDeleteSwipeStartSel

                searchBar.setSelection(mCurrentDeleteSwipeStart, mDeleteSwipeStartSel)
            }

            override fun onUpWithDeletePointerActive() {
                val searchBar = mSearchBar ?: return
                val text = searchBar.text ?: return
                if (mDeleteSwipeStartSel != -1 && mCurrentDeleteSwipeStart != -1) {
                    val start = minOf(mCurrentDeleteSwipeStart, mDeleteSwipeStartSel)
                    val end = maxOf(mCurrentDeleteSwipeStart, mDeleteSwipeStartSel)
                    if (end > start && start >= 0 && end <= text.length) {
                        text.delete(start, end)
                        searchBar.setSelection(start)
                    }
                }
                mDeleteSwipeStartSel = -1
                mCurrentDeleteSwipeStart = -1
                resetCursorBlink()
            }

            override fun resetMetaState() {}
        })

        val builder = KeyboardLayoutSet.Builder(ctx, null)
        builder.setSubtype(RichInputMethodManager.getInstance().currentSubtype)
        builder.setKeyboardOptions(Settings.getValues())
        builder.setKeyboardGeometry(
            ResourceUtils.getKeyboardWidth(ctx, Settings.getValues()),
            ResourceUtils.getKeyboardHeight(resources, Settings.getValues())
        )

        val searchKeyboardLayoutSet = builder.build()
        mSearchKeyboardLayoutSet = searchKeyboardLayoutSet
        mSearchAlphabetKeyboardId = KeyboardSwitcher.getInstance().activeAlphabetKeyboardId
        PointerTracker.switchTo(bottomRow)
        bottomRow.setKeyboard(searchKeyboardLayoutSet.getKeyboard(mSearchAlphabetKeyboardId))
        bottomRow.setKeyPreviewPopupEnabled(Settings.getValues().mKeyPreviewPopupOn)

        if (isInLayout) {
            post { requestLayout() }
        } else {
            requestLayout()
        }
    }

    private fun stopSearchMode(returnToKeyboard: Boolean = true) {
        Log.d("EmojiSearch", "stopSearchMode")
        if (!mInSearchMode) return
        mInSearchMode = false

        if (returnToKeyboard) {
            setupBottomRowKeyboard(null, mOriginalActionListener)
        } else {
            // Do not take PointerTracker back from the panel that is replacing us.
            findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)?.setKeyboardActionListener(mOriginalActionListener)
        }
        setupCategoryTabs()

        mEmojiCategoryPageIndicatorView?.visibility = View.GONE
        mPager?.visibility = View.VISIBLE
        mSearchContainer?.visibility = View.GONE

        removeCallbacks(cursorBlinkRunnable)
        mIgnoreNextShiftRelease = false
        mDeleteSwipeStartSel = -1
        mCurrentDeleteSwipeStart = -1
        mSearchBar?.clearFocus()
        mSearchBar?.setText("")
        mSearchBar = null
        mSearchKeyboardLayoutSet = null

        mOriginalActionListener?.let {
            PointerTracker.setKeyboardActionListener(it)
        }

        if (returnToKeyboard && isAttachedToWindow) {
            KeyboardSwitcher.getInstance().returnToAlphabetKeyboard()
        }

        if (isInLayout) {
            post { requestLayout() }
        } else {
            requestLayout()
        }
    }

    private fun performSearch(query: String?) {
        Log.d("EmojiSearch", "performSearch: $query")
        val dict = sDictionaryFacilitator
        if (dict == null || query.isNullOrEmpty()) {
            mSearchAdapter?.submitList(Collections.emptyList())
            if (Settings.getValues().mSplitToolbar) {
                if (dict == null) {
                    updateSplitToolbarEmojiSuggestions()
                } else {
                    populateSuggestionBarWithRecents()
                }
            }
            return
        }

        val suggestions = dict.getSuggestions(query.splitOnWhitespace())
        val results = ArrayList<String>()
        for (info in suggestions) {
            if (info.isEmoji) {
                results.add(info.mWord)
            }
        }

        Log.d("EmojiSearch", "Found ${results.size} results for: $query")
        mSearchAdapter?.submitList(results)

        if (Settings.getValues().mSplitToolbar && results.isNotEmpty()) {
            pushEmojisToSuggestionBar(results)
        }
    }

    fun startEmojiPalettes(
        keyVisualAttr: KeyVisualAttributes?,
        editorInfo: EditorInfo?,
        keyboardActionListener: KeyboardActionListener?
    ) {
        stopSearchMode(returnToKeyboard = false)
        mEditorInfo = editorInfo
        mKeyboardActionListener = keyboardActionListener ?: KeyboardActionListener.EMPTY_LISTENER
        initialize()
        mEmojiLayoutParams.updateDimensions(context)
        mPager?.let { mEmojiLayoutParams.setEmojiListProperties(it) }
        val splitToolbar = Settings.getValues().mSplitToolbar
        val isRecentsShown = mEmojiCategory.getShownCategories().any { it.mCategoryId == EmojiCategory.ID_RECENTS }
        if (splitToolbar == isRecentsShown) {
            mEmojiCategory.initialize()
            setupCategoryTabs()
            mPager?.adapter?.notifyDataSetChanged()
        }
        if (splitToolbar && mEmojiCategory.getCurrentCategoryId() == EmojiCategory.ID_RECENTS) {
            setCurrentCategoryId(EmojiCategory.ID_SMILEYS_EMOTION, true)
        }
        updateColors()
        setupBottomRowKeyboard(editorInfo, mKeyboardActionListener)
        val params = KeyDrawParams()
        params.updateParams(mEmojiLayoutParams.bottomRowKeyHeight, keyVisualAttr)
        setupSidePadding()
        initDictionaryFacilitator()

        if (splitToolbar) {
            populateSuggestionBarWithRecents()
        }
    }

    override fun onClick(v: View) {
        val tag = v.tag
        if (tag is Long) {
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this, HapticEvent.KEY_PRESS)
            val categoryId = tag.toInt()

            if (categoryId == ID_SEARCH_TAB) {
                startSearchMode()
                return
            }

            if (categoryId != mEmojiCategory.getCurrentCategoryId()) {
                setCurrentCategoryId(categoryId, false)
                updateEmojiCategoryPageIdView()
            }
        }
    }

    private fun setupBottomRowKeyboard(editorInfo: EditorInfo?, keyboardActionListener: KeyboardActionListener?) {
        val keyboardView = findViewById<MainKeyboardView>(R.id.bottom_row_keyboard) ?: return
        keyboardView.setKeyboardActionListener(keyboardActionListener)
        if (!isAttachedToWindow) return
        
        keyboardView.setKeyPreviewPopupEnabled(Settings.getValues().mKeyPreviewPopupOn)
        val ei = editorInfo ?: mEditorInfo

        try {
            PointerTracker.switchTo(keyboardView)
            val kls = KeyboardLayoutSet.Builder.buildEmojiClipBottomRow(context, ei)
            val keyboard = kls.getKeyboard(KeyboardId.ELEMENT_EMOJI_BOTTOM_ROW)
            keyboardView.setKeyboard(keyboard)
        } catch (e: NullPointerException) {
            Log.e("EmojiPalettesView", "Failed to switch PointerTracker during teardown", e)
        }
    }

    override fun onPressKey(key: Key) {
        val code = key.code
        mKeyboardActionListener.onPressKey(code, 0, true, HapticEvent.KEY_PRESS)
    }

    override fun onReleaseKey(key: Key) {
        addRecentKey(key)
        val code = key.code
        if (code == KeyCode.MULTIPLE_CODE_POINTS) {
            mKeyboardActionListener.onTextInput(key.outputText)
        } else {
            mKeyboardActionListener.onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        }
        mKeyboardActionListener.onReleaseKey(code, false)
        if (Settings.getValues().mAlphaAfterEmojiInEmojiView) {
            mKeyboardActionListener.onCodeInput(KeyCode.ALPHA, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        }
    }

    override fun getDescription(emoji: String): String? {
        val wordProperty = sDictionaryFacilitator?.getWordProperty(emoji)
        if (wordProperty?.mHasShortcuts == true) {
            val desc = wordProperty.mShortcutTargets?.firstOrNull()?.mWord
            if (!desc.isNullOrEmpty()) return desc
        }

        val cleanEmoji = stripEmojiModifiers(emoji)
        if (cleanEmoji != emoji) {
            val baseProperty = sDictionaryFacilitator?.getWordProperty(cleanEmoji)
            if (baseProperty?.mHasShortcuts == true) {
                val desc = baseProperty.mShortcutTargets?.firstOrNull()?.mWord
                if (!desc.isNullOrEmpty()) return desc
            }
        }

        return EMOJI_FALLBACK_DESCRIPTIONS[emoji]
            ?: EMOJI_FALLBACK_DESCRIPTIONS[cleanEmoji]
    }

    fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        if (!enabled) return
        setLayerType(LAYER_TYPE_HARDWARE, null)
        findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)?.setHardwareAcceleratedDrawingEnabled(enabled)
    }

    private fun addRecentKey(key: Key) {
        if (Settings.getValues().mIncognitoModeEnabled) return
        val split = Settings.getValues().mSplitToolbar
        if (mEmojiCategory.isInRecentTab() && !split) {
            getRecentsKeyboard().addPendingKey(key)
            return
        }
        getRecentsKeyboard().addKeyFirst(key)
        if (!split) {
            mPager?.adapter?.notifyItemChanged(mEmojiCategory.getRecentTabId())
        }
        if (split && isShown) {
            populateSuggestionBarWithRecents()
        }
    }

    fun addRecentKey(emoji: String?) {
        if (Settings.getValues().mIncognitoModeEnabled || emoji.isNullOrEmpty()) {
            return
        }
        val split = Settings.getValues().mSplitToolbar
        if (mEmojiCategory.isInRecentTab() && !split) {
            getRecentsKeyboard().addPendingStringKey(emoji)
            return
        }
        getRecentsKeyboard().addStringKeyFirst(emoji)
        if (!split) {
            mPager?.adapter?.notifyItemChanged(mEmojiCategory.getRecentTabId())
        }
        if (split && isShown) {
            populateSuggestionBarWithRecents()
        }
    }

    private fun setupSidePadding() {
        val sv = Settings.getValues()
        val keyboardWidth = ResourceUtils.getKeyboardWidth(context, sv)
        val keyboardAttr = context.obtainStyledAttributes(null, R.styleable.Keyboard, R.attr.keyboardStyle, R.style.Keyboard)
        val leftPadding = keyboardAttr.getFraction(R.styleable.Keyboard_keyboardLeftPadding, keyboardWidth, keyboardWidth, 0f) * sv.mSidePaddingScale
        val rightPadding = keyboardAttr.getFraction(R.styleable.Keyboard_keyboardRightPadding, keyboardWidth, keyboardWidth, 0f) * sv.mSidePaddingScale
        keyboardAttr.recycle()
        mPager?.let {
            it.setPadding(leftPadding.toInt(), it.paddingTop, rightPadding.toInt(), it.paddingBottom)
        }
        mEmojiCategoryPageIndicatorView?.let {
            it.setPadding(leftPadding.toInt(), it.paddingTop, rightPadding.toInt(), it.paddingBottom)
        }
    }

    fun stopEmojiPalettes() {
        if (!initialized) return

        if (mInSearchMode) {
            stopSearchMode(returnToKeyboard = false)
        }

        getRecentsKeyboard().flushPendingRecentKeys()

        val stripView = KeyboardSwitcher.getInstance().suggestionStripView
        stripView?.clearEmojiSuggestions()
    }

    private fun getRecentsKeyboard(): DynamicGridKeyboard {
        return requireNotNull(mEmojiCategory.getKeyboard(EmojiCategory.ID_RECENTS, 0)) {
            "Recent emoji keyboard must be available"
        }
    }

    private fun populateSuggestionBarWithRecents() {
        val stripView = KeyboardSwitcher.getInstance().suggestionStripView ?: return

        val recentEmojis = ArrayList<String>()
        val seen = HashSet<String>()
        for (key in getRecentsKeyboard().sortedKeys) {
            val output = key.outputText
            var emojiStr: String? = null
            if (output != null) {
                emojiStr = output
            } else if (key.code > 0) {
                emojiStr = String(Character.toChars(key.code))
            }
            if (emojiStr != null && seen.add(emojiStr)) {
                recentEmojis.add(emojiStr)
            }
        }
        pushEmojisToSuggestionBar(recentEmojis)
    }

    private fun pushEmojisToSuggestionBar(emojis: List<String>) {
        val stripView = KeyboardSwitcher.getInstance().suggestionStripView ?: return
        stripView.setEmojiSuggestions(emojis) { emoji ->
            mKeyboardActionListener.onTextInput(emoji)
            addRecentKey(emoji)
        }
    }

    private fun updateSplitToolbarEmojiSuggestions() {
        val stripView = KeyboardSwitcher.getInstance().suggestionStripView ?: return

        if (sDictionaryFacilitator == null) {
            stripView.setEmojiDownloadButton({
                if ("standard" == BuildConfig.FLAVOR || "standardfull" == BuildConfig.FLAVOR) {
                    downloadEmojiDictionary()
                    mIsDownloadingEmojiDict = true
                    updateSplitToolbarEmojiSuggestions()
                } else {
                    val ctx = context
                    val intent = Intent(ctx, SettingsActivity::class.java)
                    intent.putExtra("screen", "dictionaries")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                }
            }, mIsDownloadingEmojiDict)
        } else {
            val queryText = mSearchBar?.text
            if (!queryText.isNullOrEmpty()) {
                performSearch(queryText.toString())
            } else {
                populateSuggestionBarWithRecents()
            }
        }
    }

    fun setKeyboardActionListener(listener: KeyboardActionListener?) {
        mKeyboardActionListener = listener ?: KeyboardActionListener.EMPTY_LISTENER
    }

    private fun updateEmojiCategoryPageIdView() {
        mEmojiCategoryPageIndicatorView?.setCategoryPageId(
            mEmojiCategory.getCurrentCategoryPageCount(),
            mEmojiCategory.getCurrentCategoryPageId(), 0.0f
        )
    }

    private fun setCurrentCategoryId(categoryId: Int, initial: Boolean) {
        val oldCategoryId = mEmojiCategory.getCurrentCategoryId()
        if (initial || oldCategoryId != categoryId) {
            mEmojiCategory.setCurrentCategoryId(categoryId)

            val pager = mPager
            if (pager != null && pager.scrollState != ViewPager2.SCROLL_STATE_DRAGGING) {
                pager.setCurrentItem(
                    mEmojiCategory.getTabIdFromCategoryId(mEmojiCategory.getCurrentCategoryId()),
                    false
                )
            }

            if (Settings.getValues().mSecondaryStripVisible) {
                val old = mTabStrip?.findViewWithTag<View>(oldCategoryId.toLong())
                val current = mTabStrip?.findViewWithTag<View>(categoryId.toLong())

                if (old is ImageView) {
                    Settings.getValues().mColors.setColor(old, ColorType.EMOJI_CATEGORY)
                    old.background = null
                    Settings.getValues().mColors.setBackground(old, ColorType.STRIP_BACKGROUND)
                }
                if (current is ImageView) {
                    current.setBackgroundResource(R.drawable.toolbar_key_background)
                    Settings.getValues().mColors.setColor(current.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
                }
            }
        }
    }

    private var mEmoticonGroupStrip: LinearLayout? = null

    /**
     * Shows the horizontally scrolling emoticon groups (Classic, Smiling, ...) above the emoticon list, like Gboard.
     * The strip lives in the category page layout and is hidden for every other category
     * (pages are reused, so this also has to reset it). Without groups in EMOTICONS.txt nothing is shown.
     */
    private fun setupEmoticonGroups(page: View, recyclerView: RecyclerView, isEmoticons: Boolean) {
        val scroll = page.findViewById<HorizontalScrollView>(R.id.emoticon_group_scroll) ?: return
        val strip = page.findViewById<LinearLayout>(R.id.emoticon_group_strip) ?: return
        val groups = if (isEmoticons) mEmojiCategory.getEmoticonGroups() else emptyList()
        val lp = recyclerView.layoutParams as? FrameLayout.LayoutParams
        val density = resources.displayMetrics.density
        val topMargin = if (groups.isEmpty()) 0 else (40 * density).toInt()
        if (lp != null && lp.topMargin != topMargin) {
            lp.topMargin = topMargin
            recyclerView.layoutParams = lp
        }
        if (groups.isEmpty()) {
            scroll.visibility = View.GONE
            if (mEmoticonGroupStrip == strip) {
                mEmoticonGroupStrip = null
            }
            return
        }
        mEmoticonGroupStrip = strip
        scroll.visibility = View.VISIBLE
        mColors.setBackground(scroll, ColorType.STRIP_BACKGROUND)

        // Disallow ViewPager2 horizontal intercept when touching or scrolling emoticon sub-categories
        scroll.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        val chipHeight = (30 * density).toInt()
        val marginH = (4 * density).toInt()
        val padH = (15 * density).toInt()
        val currentGroup = mEmojiCategory.getCurrentEmoticonGroup()

        // Reuse existing child views if the strip is already populated with matching count
        if (strip.childCount != groups.size) {
            strip.removeAllViews()
            groups.forEachIndexed { index, group ->
                val tab = TextView(context).apply {
                    text = EmojiCategory.getEmoticonGroupDisplayName(context, group.name)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                    maxLines = 1
                    gravity = android.view.Gravity.CENTER
                    includeFontPadding = false
                    setPadding(padH, 0, padH, 0)
                    setOnClickListener { v ->
                        mEmojiCategory.setCurrentEmoticonGroup(index)
                        styleEmoticonGroupTabs(strip)
                        recyclerView.adapter?.notifyDataSetChanged()
                        recyclerView.scrollToPosition(0)
                        mEmojiCategoryPageIndicatorView?.setCategoryPageId(mEmojiCategory.getCurrentCategoryPageCount(), 0, 0f)
                        val targetScrollX = (v.left - marginH - (6 * density).toInt()).coerceAtLeast(0)
                        scroll.smoothScrollTo(targetScrollX, 0)
                    }
                }
                val chipLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, chipHeight).apply {
                    marginStart = marginH
                    marginEnd = marginH
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                strip.addView(tab, chipLp)
            }
        } else {
            groups.forEachIndexed { index, group ->
                (strip.getChildAt(index) as? TextView)?.apply {
                    text = EmojiCategory.getEmoticonGroupDisplayName(context, group.name)
                    setPadding(padH, 0, padH, 0)
                    setOnClickListener { v ->
                        mEmojiCategory.setCurrentEmoticonGroup(index)
                        styleEmoticonGroupTabs(strip)
                        recyclerView.adapter?.notifyDataSetChanged()
                        recyclerView.scrollToPosition(0)
                        mEmojiCategoryPageIndicatorView?.setCategoryPageId(mEmojiCategory.getCurrentCategoryPageCount(), 0, 0f)
                        val targetScrollX = (v.left - marginH - (6 * density).toInt()).coerceAtLeast(0)
                        scroll.smoothScrollTo(targetScrollX, 0)
                    }
                }
            }
        }
        styleEmoticonGroupTabs(strip)

        // Ensure the selected tab is scrolled into view
        scroll.post {
            val selectedTab = strip.getChildAt(currentGroup)
            if (selectedTab != null) {
                val targetScrollX = (selectedTab.left - marginH - (6 * density).toInt()).coerceAtLeast(0)
                scroll.scrollTo(targetScrollX, 0)
            }
        }
    }

    private fun styleEmoticonGroupTabs(strip: LinearLayout) {
        val current = mEmojiCategory.getCurrentEmoticonGroup()
        for (i in 0 until strip.childCount) {
            val tab = strip.getChildAt(i) as? TextView ?: continue
            if (i == current) {
                tab.setTextColor(mColors.get(ColorType.EMOJI_CATEGORY_SELECTED))
                tab.setTypeface(null, Typeface.BOLD)
                tab.setBackgroundResource(R.drawable.emoticon_group_tab_background)
                mColors.setColor(tab.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
            } else {
                tab.setTextColor(mColors.get(ColorType.EMOJI_CATEGORY))
                tab.setTypeface(null, Typeface.NORMAL)
                tab.setBackgroundResource(R.drawable.emoticon_group_tab_unselected)
            }
        }
    }

    fun updateColors() {
        mColors = Settings.getValues().mColors
        val tabStrip = mTabStrip
        if (tabStrip != null) {
            mColors.setBackground(tabStrip, ColorType.STRIP_BACKGROUND)
            for (i in 0 until tabStrip.childCount) {
                val child = tabStrip.getChildAt(i)
                if (child is ImageView) {
                    val tag = child.tag
                    val categoryId = if (tag is Long) tag.toInt() else -1
                    if (categoryId == mEmojiCategory.getCurrentCategoryId()) {
                        child.setBackgroundResource(R.drawable.toolbar_key_background)
                        mColors.setColor(child.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
                    } else {
                        child.background = null
                        mColors.setBackground(child, ColorType.STRIP_BACKGROUND)
                    }
                    mColors.setColor(child, ColorType.EMOJI_CATEGORY)
                }
            }
        }
        mEmoticonGroupStrip?.let { strip ->
            (strip.parent as? View)?.let { mColors.setBackground(it, ColorType.STRIP_BACKGROUND) }
            styleEmoticonGroupTabs(strip)
        }
        mEmojiCategoryPageIndicatorView?.setColors(
            mColors.get(ColorType.EMOJI_CATEGORY_SELECTED),
            mColors.get(ColorType.STRIP_BACKGROUND)
        )
    }

    private fun isAnimationsDisabled(): Boolean {
        return android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f
        ) == 0.0f
    }

    fun clearKeyboardCache() {
        if (!initialized) return

        mEmojiCategory.clearKeyboardCache()
        setupCategoryTabs()
        updateColors()
        mDownloadBtn?.let { btn ->
            if (!mIsDownloadingEmojiDict) {
                btn.text = context.getString(R.string.button_download)
            }
        }
        if (Settings.getValues().mSplitToolbar) {
            updateSplitToolbarEmojiSuggestions()
        }
        mPager?.adapter?.notifyDataSetChanged()
        closeDictionaryFacilitator()
    }

    private fun initDictionaryFacilitator() {
        val locale = RichInputMethodManager.getInstance().currentSubtype.locale
        val facilitator = sDictionaryFacilitator
        if (facilitator == null || !facilitator.isForLocale(locale)) {
            closeDictionaryFacilitator()
            val dictFile = DictionaryInfoUtils.getCachedDictForLocaleAndType(locale, Dictionary.TYPE_EMOJI, context)
            val dictionary = if (dictFile != null) DictionaryFactory.getDictionary(dictFile, locale) else null
            sDictionaryFacilitator = if (dictionary != null) SingleDictionaryFacilitator(dictionary) else null
        }
    }

    override fun setVisibility(visibility: Int) {
        if (visibility != View.VISIBLE && mInSearchMode) {
            stopSearchMode(returnToKeyboard = false)
        }
        super.setVisibility(visibility)
    }

    override fun onDetachedFromWindow() {
        if (mInSearchMode) {
            stopSearchMode(returnToKeyboard = false)
        }
        super.onDetachedFromWindow()
    }

    private fun downloadEmojiDictionary() {
        val locale = RichInputMethodManager.getInstance().currentSubtype.locale
        val lang = locale.language
        val urlStr = Links.DICTIONARY_URL + Links.DICTIONARY_DOWNLOAD_SUFFIX + Links.DICTIONARY_EMOJI_CLDR_SUFFIX + "emoji_$lang.dict"

        Toast.makeText(context, context.getString(R.string.downloading), Toast.LENGTH_SHORT).show()

        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "HeliboardL/3.8.9 (Android)")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = true
                conn.connect()

                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    throw java.io.IOException("Server returned HTTP " + conn.responseCode)
                }

                val cachePath = DictionaryInfoUtils.getCacheDirectoryForLocale(locale, context)
                if (cachePath != null) {
                    val targetFile = File(cachePath, "emoji_$lang.dict")
                    conn.inputStream.use { `is` ->
                        FileOutputStream(targetFile).use { fos ->
                            val buffer = ByteArray(4096)
                            var length: Int
                            while (`is`.read(buffer).also { length = it } > 0) {
                                fos.write(buffer, 0, length)
                            }
                        }
                    }

                    val prefs = DeviceProtectedUtils.getSharedPreferences(context)
                    prefs.edit()
                        .putString("pref_dict_download_link_emoji_" + locale.toString(), urlStr)
                        .putString("pref_dict_download_link_emoji_" + locale.toLanguageTag(), urlStr)
                        .apply()

                    this@EmojiPalettesView.post {
                        Toast.makeText(context, context.getString(R.string.emoji_dict_loaded_success), Toast.LENGTH_SHORT).show()
                        closeDictionaryFacilitator()
                        initDictionaryFacilitator()
                        mIsDownloadingEmojiDict = false
                        updateSplitToolbarEmojiSuggestions()
                        if (mInSearchMode) {
                            val query = mSearchBar?.text?.toString() ?: ""
                            if (!Settings.getValues().mSplitToolbar) {
                                stopSearchMode(returnToKeyboard = false)
                                startSearchMode()
                                mSearchBar?.setText(query)
                                mSearchBar?.setSelection(query.length)
                            } else {
                                performSearch(query)
                            }
                        }
                    }
                } else {
                    throw java.io.IOException("Cache path is null")
                }
            } catch (e: Exception) {
                Log.e("EmojiSearch", "Failed to download dictionary", e)
                this@EmojiPalettesView.post {
                    Toast.makeText(context, context.getString(R.string.emoji_dict_download_failed), Toast.LENGTH_SHORT).show()
                    mIsDownloadingEmojiDict = false
                    if (mInSearchMode) {
                        stopSearchMode()
                        startSearchMode()
                    }
                }
            }
        }
    }

    fun getCurrentPageKeyboardView(): EmojiPageKeyboardView? {
        val pager = mPager ?: return null
        val currentCatPos = pager.currentItem
        val recyclerView = mPagerAdapter?.getRecyclerViewForPosition(currentCatPos) ?: return null
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return null
        val firstPos = layoutManager.findFirstCompletelyVisibleItemPosition().takeIf { it >= 0 }
            ?: layoutManager.findFirstVisibleItemPosition().takeIf { it >= 0 }
            ?: 0
        val holder = recyclerView.findViewHolderForAdapterPosition(firstPos) as? EmojiPalettesAdapter.ViewHolder
        if (holder != null) return holder.getKeyboardView()
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            if (child is EmojiPageKeyboardView) return child
        }
        return null
    }

    fun selectNextCategory() {
        val categories = mEmojiCategory.getShownCategories()
        val curId = mEmojiCategory.getCurrentCategoryId()
        val idx = categories.indexOfFirst { it.mCategoryId == curId }
        val nextIdx = if (idx >= 0) (idx + 1) % categories.size else 0
        setCurrentCategoryId(categories[nextIdx].mCategoryId, false)
        updateEmojiCategoryPageIdView()
    }

    fun selectPreviousCategory() {
        val categories = mEmojiCategory.getShownCategories()
        val curId = mEmojiCategory.getCurrentCategoryId()
        val idx = categories.indexOfFirst { it.mCategoryId == curId }
        val prevIdx = if (idx > 0) idx - 1 else categories.size - 1
        setCurrentCategoryId(categories[prevIdx].mCategoryId, false)
        updateEmojiCategoryPageIdView()
    }

    fun onHardwareKeyEvent(keyCode: Int, keyEvent: KeyEvent): Boolean {
        if (mInSearchMode) {
            when (keyCode) {
                KeyEvent.KEYCODE_ESCAPE -> {
                    stopSearchMode(false)
                    return true
                }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    val first = mSearchAdapter?.getFirstEmoji()
                    if (first != null) {
                        mKeyboardActionListener.onTextInput(first)
                        addRecentKey(first)
                        stopSearchMode(returnToKeyboard = Settings.getValues().mAlphaAfterEmojiInEmojiView)
                        return true
                    }
                    stopSearchMode(false)
                    return true
                }
                KeyEvent.KEYCODE_DEL -> {
                    val text = mSearchBar?.text
                    if (text != null && text.isNotEmpty()) {
                        text.delete(text.length - 1, text.length)
                        resetCursorBlink()
                        return true
                    } else {
                        stopSearchMode(false)
                        return true
                    }
                }
                else -> {
                    if (keyEvent.isPrintingKey && keyEvent.unicodeChar != 0) {
                        mSearchBar?.text?.append(keyEvent.unicodeChar.toChar())
                        resetCursorBlink()
                        return true
                    }
                }
            }
            return false
        }

        when (keyCode) {
            KeyEvent.KEYCODE_ESCAPE -> {
                KeyboardSwitcher.getInstance().onToggleKeyboard(KeyboardSwitcher.KeyboardSwitchState.EMOJI)
                return true
            }
            KeyEvent.KEYCODE_SLASH -> {
                startSearchMode()
                return true
            }
            KeyEvent.KEYCODE_TAB -> {
                if (keyEvent.isShiftPressed) selectPreviousCategory() else selectNextCategory()
                return true
            }
            KeyEvent.KEYCODE_PAGE_UP -> {
                selectPreviousCategory()
                return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                selectNextCategory()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (keyEvent.isCtrlPressed) {
                    selectPreviousCategory()
                    return true
                }
                return getCurrentPageKeyboardView()?.moveFocus(-1, 0) ?: false
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (keyEvent.isCtrlPressed) {
                    selectNextCategory()
                    return true
                }
                return getCurrentPageKeyboardView()?.moveFocus(1, 0) ?: false
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                return getCurrentPageKeyboardView()?.moveFocus(0, -1) ?: false
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                return getCurrentPageKeyboardView()?.moveFocus(0, 1) ?: false
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE -> {
                val committed = getCurrentPageKeyboardView()?.commitFocusedKey() ?: false
                if (committed) {
                    if (Settings.getValues().mAlphaAfterEmojiInEmojiView) {
                        KeyboardSwitcher.getInstance().onToggleKeyboard(KeyboardSwitcher.KeyboardSwitchState.EMOJI)
                    }
                    return true
                }
                return false
            }
            else -> {
                if (keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9) {
                    val categoryIdx = keyCode - KeyEvent.KEYCODE_1
                    val categories = mEmojiCategory.getShownCategories()
                    if (categoryIdx in categories.indices) {
                        setCurrentCategoryId(categories[categoryIdx].mCategoryId, false)
                        updateEmojiCategoryPageIdView()
                        return true
                    }
                }
                if (keyEvent.isPrintingKey && (keyEvent.unicodeChar in 'a'.code..'z'.code || keyEvent.unicodeChar in 'A'.code..'Z'.code)) {
                    startSearchMode()
                    mSearchBar?.text?.append(keyEvent.unicodeChar.toChar())
                    resetCursorBlink()
                    return true
                }
            }
        }
        return false
    }

    companion object {
        private const val ID_SEARCH_TAB = -2
        private var sDictionaryFacilitator: SingleDictionaryFacilitator? = null

        fun closeDictionaryFacilitator() {
            sDictionaryFacilitator?.closeDictionaries()
            sDictionaryFacilitator = null
        }

        private fun getRecyclerView(view: View): RecyclerView {
            return view.findViewById(R.id.emoji_keyboard_list)
        }

        private val REGEX_EMOJI_MODIFIERS = Regex("[\\uFE0E\\uFE0F\\uD83C\\uDFFB-\\uD83C\\uDFFF]")

        private fun stripEmojiModifiers(emoji: String): String {
            return emoji.replace(REGEX_EMOJI_MODIFIERS, "")
        }

        private val EMOJI_FALLBACK_DESCRIPTIONS: Map<String, String> = mapOf(
            // Unicode 14.0
            "🛝" to "playground slide",
            "🛞" to "wheel",
            "🛟" to "ring buoy",
            "🟰" to "heavy equals sign",
            "🥹" to "face holding back tears",
            "🧌" to "troll",
            "🩻" to "x-ray",
            "🩼" to "crutch",
            "🪩" to "mirror ball",
            "🪪" to "identification card",
            "🪫" to "low battery",
            "🪬" to "hamsa",
            "🪷" to "lotus",
            "🪸" to "coral",
            "🪹" to "empty nest",
            "🪺" to "nest with eggs",
            "🫃" to "pregnant man",
            "🫄" to "pregnant person",
            "🫅" to "person with crown",
            "🫗" to "pouring liquid",
            "🫘" to "beans",
            "🫙" to "jar",
            "🫠" to "melting face",
            "🫡" to "saluting face",
            "🫢" to "face with open eyes and hand over mouth",
            "🫣" to "face with peeking eye",
            "🫤" to "face with diagonal mouth",
            "🫥" to "dotted line face",
            "🫦" to "biting lip",
            "🫧" to "bubbles",
            "🫰" to "hand with index finger and thumb crossed",
            "🫱" to "rightwards hand",
            "🫲" to "leftwards hand",
            "🫳" to "palm down hand",
            "🫴" to "palm up hand",
            "🫵" to "index pointing at the viewer",
            "🫶" to "heart hands",

            // Unicode 15.0
            "🫨" to "shaking face",
            "🩷" to "pink heart",
            "🩵" to "light blue heart",
            "🩶" to "grey heart",
            "🫷" to "leftwards pushing hand",
            "🫸" to "rightwards pushing hand",
            "🫎" to "moose",
            "🫏" to "donkey",
            "🪽" to "wing",
            "🐦\u200D⬛" to "black bird",
            "🪿" to "goose",
            "🪼" to "jellyfish",
            "🪻" to "hyacinth",
            "🫚" to "ginger root",
            "🫛" to "pea pod",
            "🪭" to "folding hand fan",
            "🪮" to "hair pick",
            "🪇" to "maracas",
            "🪈" to "flute",
            "🪯" to "khanda",
            "🛜" to "wireless",

            // Unicode 15.1
            "🙂\u200D↔️" to "head shaking horizontally",
            "🙂\u200D↔" to "head shaking horizontally",
            "🙂\u200D↕️" to "head shaking vertically",
            "🙂\u200D↕" to "head shaking vertically",
            "🐦\u200D🔥" to "phoenix",
            "🍋\u200D🟩" to "lime",
            "🍄\u200D🟫" to "brown mushroom",
            "⛓️\u200D💥" to "broken chain",
            "⛓\u200D💥" to "broken chain",
            "🧑\u200D🧑\u200D🧒" to "family: adult, adult, child",
            "🧑\u200D🧑\u200D🧒\u200D🧒" to "family: adult, adult, child, child",
            "🧑\u200D🧒" to "family: adult, child",
            "🧑\u200D🧒\u200D🧒" to "family: adult, child, child",
            "🚶\u200D➡" to "person walking facing right",
            "🚶\u200D➡️" to "person walking facing right",
            "🚶‍♀\u200D➡" to "woman walking facing right",
            "🚶‍♀\u200D➡️" to "woman walking facing right",
            "🚶‍♂\u200D➡" to "man walking facing right",
            "🚶‍♂\u200D➡️" to "man walking facing right",
            "🏃\u200D➡" to "person running facing right",
            "🏃\u200D➡️" to "person running facing right",
            "🏃‍♀\u200D➡" to "woman running facing right",
            "🏃‍♀\u200D➡️" to "woman running facing right",
            "🏃‍♂\u200D➡" to "man running facing right",
            "🏃‍♂\u200D➡️" to "man running facing right",
            "🧎\u200D➡" to "person kneeling facing right",
            "🧎\u200D➡️" to "person kneeling facing right",
            "🧎‍♀\u200D➡" to "woman kneeling facing right",
            "🧎‍♀\u200D➡️" to "woman kneeling facing right",
            "🧎‍♂\u200D➡" to "man kneeling facing right",
            "🧎‍♂\u200D➡️" to "man kneeling facing right",
            "🧑\u200D🦯\u200D➡" to "person with white cane facing right",
            "🧑\u200D🦯\u200D➡️" to "person with white cane facing right",
            "👨\u200D🦯\u200D➡" to "man with white cane facing right",
            "👨\u200D🦯\u200D➡️" to "man with white cane facing right",
            "👩\u200D🦯\u200D➡" to "woman with white cane facing right",
            "👩\u200D🦯\u200D➡️" to "woman with white cane facing right",
            "🧑\u200D🦼\u200D➡" to "person in motorized wheelchair facing right",
            "🧑\u200D🦼\u200D➡️" to "person in motorized wheelchair facing right",
            "👨\u200D🦼\u200D➡" to "man in motorized wheelchair facing right",
            "👨\u200D🦼\u200D➡️" to "man in motorized wheelchair facing right",
            "👩\u200D🦼\u200D➡" to "woman in motorized wheelchair facing right",
            "👩\u200D🦼\u200D➡️" to "woman in motorized wheelchair facing right",
            "🧑\u200D🦽\u200D➡" to "person in manual wheelchair facing right",
            "🧑\u200D🦽\u200D➡️" to "person in manual wheelchair facing right",
            "👨\u200D🦽\u200D➡" to "man in manual wheelchair facing right",
            "👨\u200D🦽\u200D➡️" to "man in manual wheelchair facing right",
            "👩\u200D🦽\u200D➡" to "woman in manual wheelchair facing right",
            "👩\u200D🦽\u200D➡️" to "woman in manual wheelchair facing right",

            // Unicode 16.0
            "🫩" to "face with bags under eyes",
            "🫆" to "fingerprint",
            "🪾" to "leafless tree",
            "🫜" to "root vegetable",
            "🪉" to "harp",
            "🪏" to "shovel",
            "🫟" to "splatter",
            "🇨🇶" to "flag: Sark",

            // Unicode 17.0
            "🫪" to "distorted face",
            "🫯" to "fight cloud",
            "🫈" to "hairy creature",
            "🧑\u200D🩰" to "ballet dancer",
            "👯" to "people with bunny ears",
            "👯‍♀" to "women with bunny ears",
            "👯‍♂" to "men with bunny ears",
            "🤼" to "people wrestling",
            "🤼‍♀" to "women wrestling",
            "🤼‍♂" to "men wrestling",
            "🫍" to "orca",
            "🛘" to "landslide",
            "🪊" to "trombone",
            "🪎" to "treasure chest"
        )
    }
}
