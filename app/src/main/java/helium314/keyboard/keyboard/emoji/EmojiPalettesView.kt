/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2026 LeanBitLab
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.emoji

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import helium314.keyboard.event.HapticEvent
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

    private var mSearchContainer: LinearLayout? = null
    private var mSearchResultsList: RecyclerView? = null
    private var mSearchEmptyView: TextView? = null
    private var mSearchAdapter: EmojiSearchAdapter? = null
    private var mSearchBar: EditText? = null
    private var mInSearchMode = false
    private var mIsDownloadingEmojiDict = false
    private var mOriginalActionListener: KeyboardActionListener? = null
    private var mSearchKeyboardLayoutSet: KeyboardLayoutSet? = null

    private var mEditorInfo: EditorInfo? = null

    init {
        mColors = Settings.getValues().mColors
        val builder = KeyboardLayoutSet.Builder(context, null)
        val res = context.resources
        mEmojiLayoutParams = EmojiLayoutParams(res)
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
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val res = context.resources
        val width = ResourceUtils.getKeyboardWidth(context, Settings.getValues()) + paddingLeft + paddingRight
        if (!mInSearchMode) {
            val height = ResourceUtils.getSecondaryKeyboardHeight(res, Settings.getValues()) + paddingTop + paddingBottom
            setMeasuredDimension(width, height)
        } else {
            setMeasuredDimension(width, measuredHeight)
        }
        mEmojiCategoryPageIndicatorView?.mWidth = width
    }

    fun initialize() {
        if (initialized) return
        mEmojiCategory.initialize()
        mTabStrip = KeyboardSwitcher.getInstance().emojiTabStrip as LinearLayout?

        if (Settings.getValues().mSecondaryStripVisible) {
            addTab(mTabStrip!!, ID_SEARCH_TAB)
            val splitToolbar = Settings.getValues().mSplitToolbar
            for (properties in mEmojiCategory.getShownCategories()) {
                if (splitToolbar && properties.mCategoryId == EmojiCategory.ID_RECENTS) continue
                addTab(mTabStrip!!, properties.mCategoryId)
            }
        }

        mPager = findViewById(R.id.emoji_pager)
        mPager!!.adapter = PagerAdapter(mPager!!)
        mEmojiLayoutParams.setEmojiListProperties(mPager!!)
        mEmojiCategoryPageIndicatorView = findViewById(R.id.emoji_category_page_id_view)
        mEmojiLayoutParams.setCategoryPageIdViewProperties(mEmojiCategoryPageIndicatorView!!)

        mSearchContainer = findViewById(R.id.emoji_search_container)
        mSearchResultsList = findViewById(R.id.emoji_search_results)
        mSearchEmptyView = findViewById(R.id.emoji_search_empty_view)

        mSearchResultsList!!.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        mSearchAdapter = EmojiSearchAdapter { emoji ->
            mKeyboardActionListener.onTextInput(emoji)
            addRecentKey(emoji)
            stopSearchMode()
        }
        mSearchResultsList!!.adapter = mSearchAdapter

        findViewById<View>(R.id.emoji_search_close_btn).setOnClickListener { stopSearchMode() }

        mSearchBar = findViewById(R.id.emoji_search_bar)
        mSearchBar!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performSearch(s.toString())
            }
        })

        setCurrentCategoryId(mEmojiCategory.getCurrentCategoryId(), true)
        mEmojiCategoryPageIndicatorView!!.setColors(
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
                val dialog = AlertDialog.Builder(context)
                    .setTitle(R.string.clear_emoji_history_title)
                    .setMessage(R.string.clear_emoji_history_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        getRecentsKeyboard().clearRecentKeys()
                        mPager?.adapter?.notifyDataSetChanged()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
                val window = dialog.window
                val lp = window!!.attributes
                lp.token = iconView.windowToken
                lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
                window.attributes = lp
                window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                dialog.show()
                true
            }
        }
    }

    private fun toPx(dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
    }

    private fun setupCategoryTabs() {
        mTabStrip?.removeAllViews()
        if (Settings.getValues().mSecondaryStripVisible) {
            addTab(mTabStrip!!, ID_SEARCH_TAB)
            val splitToolbar = Settings.getValues().mSplitToolbar
            for (properties in mEmojiCategory.getShownCategories()) {
                if (splitToolbar && properties.mCategoryId == EmojiCategory.ID_RECENTS) continue
                addTab(mTabStrip!!, properties.mCategoryId)
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

        mSearchBar = EditText(ctx)
        mSearchBar!!.background = null
        mSearchBar!!.hint = "Search"
        mSearchBar!!.setTextColor(mColors.get(ColorType.KEY_TEXT))
        mSearchBar!!.setHintTextColor(mColors.get(ColorType.KEY_TEXT) and 0x00FFFFFF or -0x80000000)
        mSearchBar!!.textSize = 14f
        mSearchBar!!.setSingleLine(true)
        mSearchBar!!.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        mSearchBar!!.setPadding(toPx(4f), 0, toPx(4f), 0)
        mSearchBar!!.layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        mSearchBar!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performSearch(s.toString())
            }
        })
        inputContainer.addView(mSearchBar)

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
            downloadBtn.text = "Download Dictionary"
            downloadBtn.textSize = 12f
            downloadBtn.isAllCaps = false
            downloadBtn.setOnClickListener {
                if ("standard" == BuildConfig.FLAVOR || "standardfull" == BuildConfig.FLAVOR) {
                    downloadEmojiDictionary()
                    downloadBtn.text = "Downloading..."
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

        val bottomRow = findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)
        mOriginalActionListener = mKeyboardActionListener

        bottomRow.setKeyboardActionListener(object : KeyboardActionListener {
            private var mDeleteSwipeStartSel = -1
            private var mCurrentDeleteSwipeStart = -1

            override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) {
                if (primaryCode == KeyCode.DELETE) {
                    val text = mSearchBar?.text ?: return
                    if (text.isNotEmpty()) {
                        val selStart = mSearchBar!!.selectionStart
                        val selEnd = mSearchBar!!.selectionEnd
                        if (selStart >= 0 && selEnd > selStart) {
                            text.delete(selStart, selEnd)
                        } else if (selStart > 0) {
                            text.delete(selStart - 1, selStart)
                        }
                    }
                } else if (primaryCode == Constants.CODE_SPACE) {
                    val text = mSearchBar?.text ?: return
                    var sel = mSearchBar!!.selectionStart
                    if (sel < 0) sel = text.length
                    text.insert(sel, " ")
                } else if (primaryCode > 0) {
                    val text = mSearchBar?.text ?: return
                    var sel = mSearchBar!!.selectionStart
                    if (sel < 0) sel = text.length
                    text.insert(sel, primaryCode.toChar().toString())
                } else if (primaryCode == Constants.CODE_ENTER) {
                    stopSearchMode()
                } else if (primaryCode == KeyCode.SYMBOL || primaryCode == KeyCode.SYMBOL_ALPHA || primaryCode == KeyCode.ALPHA) {
                    if (mSearchKeyboardLayoutSet != null) {
                        val bottomRow = findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)
                        val currentElementId = bottomRow.keyboard?.mId?.mElementId ?: KeyboardId.ELEMENT_ALPHABET
                        val isOnSymbols = currentElementId == KeyboardId.ELEMENT_SYMBOLS || currentElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED
                        val targetId = if (isOnSymbols) KeyboardId.ELEMENT_ALPHABET else KeyboardId.ELEMENT_SYMBOLS
                        bottomRow.setKeyboard(mSearchKeyboardLayoutSet!!.getKeyboard(targetId))
                        bottomRow.setKeyPreviewPopupEnabled(Settings.getValues().mKeyPreviewPopupOn)
                    }
                } else if (primaryCode == KeyCode.SHIFT) {
                    if (mSearchKeyboardLayoutSet != null) {
                        val bottomRow = findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)
                        val currentElementId = bottomRow.keyboard?.mId?.mElementId ?: KeyboardId.ELEMENT_ALPHABET
                        val targetId = when (currentElementId) {
                            KeyboardId.ELEMENT_SYMBOLS -> KeyboardId.ELEMENT_SYMBOLS_SHIFTED
                            KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> KeyboardId.ELEMENT_SYMBOLS
                            KeyboardId.ELEMENT_ALPHABET -> KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED
                            else -> KeyboardId.ELEMENT_ALPHABET
                        }
                        bottomRow.setKeyboard(mSearchKeyboardLayoutSet!!.getKeyboard(targetId))
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
                mDeleteSwipeStartSel = -1
                mCurrentDeleteSwipeStart = -1
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
                val text = mSearchBar?.text ?: return
                if (t == null) return
                var sel = mSearchBar!!.selectionStart
                if (sel < 0) sel = text.length
                text.insert(sel, t)
            }

            override fun onImageSelected(imageUri: String) {}
            override fun onLongPressKey(p: Int) {}
            override fun onKeyDown(k: Int, e: android.view.KeyEvent): Boolean = false
            override fun onKeyUp(k: Int, e: android.view.KeyEvent): Boolean = false
            override fun onStartBatchInput() {}
            override fun onUpdateBatchInput(p: helium314.keyboard.latin.common.InputPointers) {}
            override fun onEndBatchInput(p: helium314.keyboard.latin.common.InputPointers) {}
            override fun onCancelBatchInput() {}
            
            override fun onCancelInput() {
                mDeleteSwipeStartSel = -1
                mCurrentDeleteSwipeStart = -1
            }

            override fun onFinishSlidingInput() {}
            override fun onCustomRequest(r: Int): Boolean = false
            
            override fun onHorizontalSpaceSwipe(s: Int): Boolean {
                val text = mSearchBar?.text ?: return false
                val len = text.length
                val selStart = mSearchBar!!.selectionStart
                val selEnd = mSearchBar!!.selectionEnd
                if (selStart < 0 || selEnd < 0) return false

                val rtl = RichInputMethodManager.getInstance().currentSubtype.isRtlSubtype
                val steps = if (rtl) -s else s

                var newSel = selStart + steps
                if (newSel < 0) newSel = 0
                if (newSel > len) newSel = len

                mSearchBar!!.setSelection(newSel)
                return true
            }

            override fun onVerticalSpaceSwipe(s: Int): Boolean = false
            override fun onEndSpaceSwipe() {}
            override fun toggleNumpad(w: Boolean, f: Boolean): Boolean = false

            override fun onMoveDeletePointer(s: Int) {
                val text = mSearchBar?.text ?: return
                if (mDeleteSwipeStartSel == -1) {
                    mDeleteSwipeStartSel = mSearchBar!!.selectionEnd
                    mCurrentDeleteSwipeStart = mDeleteSwipeStartSel
                }
                if (mDeleteSwipeStartSel < 0) return

                mCurrentDeleteSwipeStart += s
                if (mCurrentDeleteSwipeStart < 0) mCurrentDeleteSwipeStart = 0
                if (mCurrentDeleteSwipeStart > mDeleteSwipeStartSel) mCurrentDeleteSwipeStart = mDeleteSwipeStartSel

                mSearchBar!!.setSelection(mCurrentDeleteSwipeStart, mDeleteSwipeStartSel)
            }

            override fun onUpWithDeletePointerActive() {
                val text = mSearchBar?.text ?: return
                val selStart = mSearchBar!!.selectionStart
                val selEnd = mSearchBar!!.selectionEnd
                if (selStart >= 0 && selEnd > selStart) {
                    text.delete(selStart, selEnd)
                }
                mDeleteSwipeStartSel = -1
                mCurrentDeleteSwipeStart = -1
            }

            override fun resetMetaState() {}
        })

        val builder = KeyboardLayoutSet.Builder(ctx, null)
        builder.setSubtype(RichInputMethodManager.getInstance().currentSubtype)
        builder.setSplitLayoutEnabled(Settings.getValues().mIsSplitKeyboardEnabled)
        builder.setKeyboardGeometry(
            ResourceUtils.getKeyboardWidth(ctx, Settings.getValues()),
            ResourceUtils.getSecondaryKeyboardHeight(resources, Settings.getValues())
        )

        mSearchKeyboardLayoutSet = builder.build()
        bottomRow.setKeyboard(mSearchKeyboardLayoutSet!!.getKeyboard(KeyboardId.ELEMENT_ALPHABET))
        bottomRow.setKeyPreviewPopupEnabled(Settings.getValues().mKeyPreviewPopupOn)

        mSearchBar!!.requestFocus()
        if (isInLayout) {
            post { requestLayout() }
        } else {
            requestLayout()
        }
    }

    private fun stopSearchMode() {
        Log.d("EmojiSearch", "stopSearchMode")
        if (!mInSearchMode) return
        mInSearchMode = false

        setupBottomRowKeyboard(null, mOriginalActionListener)
        setupCategoryTabs()

        mEmojiCategoryPageIndicatorView?.visibility = View.GONE
        mPager?.visibility = View.VISIBLE
        mSearchContainer?.visibility = View.GONE

        mSearchBar?.setText("")
        mSearchBar = null
        mSearchKeyboardLayoutSet = null

        if (mOriginalActionListener != null) {
            PointerTracker.setKeyboardActionListener(mOriginalActionListener!!)
        }

        if (isAttachedToWindow) {
            KeyboardSwitcher.getInstance().setAlphabetKeyboard()
        }

        if (isInLayout) {
            post { requestLayout() }
        } else {
            requestLayout()
        }
    }

    private fun performSearch(query: String?) {
        Log.d("EmojiSearch", "performSearch: $query")
        if (sDictionaryFacilitator == null || TextUtils.isEmpty(query)) {
            mSearchAdapter?.submitList(Collections.emptyList())
            if (Settings.getValues().mSplitToolbar) {
                if (sDictionaryFacilitator == null) {
                    updateSplitToolbarEmojiSuggestions()
                } else {
                    populateSuggestionBarWithRecents()
                }
            }
            return
        }

        val suggestions = sDictionaryFacilitator!!.getSuggestions(query!!.splitOnWhitespace())
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
        stopSearchMode()
        mEditorInfo = editorInfo
        mKeyboardActionListener = keyboardActionListener ?: KeyboardActionListener.EMPTY_LISTENER
        initialize()
        updateColors()
        setupBottomRowKeyboard(editorInfo, mKeyboardActionListener)
        val params = KeyDrawParams()
        params.updateParams(mEmojiLayoutParams.bottomRowKeyboardHeight, keyVisualAttr)
        setupSidePadding()
        initDictionaryFacilitator()

        if (Settings.getValues().mSplitToolbar) {
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
        if (!isAttachedToWindow) return
        
        keyboardView.setKeyPreviewPopupEnabled(Settings.getValues().mKeyPreviewPopupOn)
        val ei = editorInfo ?: mEditorInfo
        keyboardView.setKeyboardActionListener(keyboardActionListener)

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
        if (sDictionaryFacilitator == null) return null

        val wordProperty = sDictionaryFacilitator!!.getWordProperty(emoji)
        if (wordProperty == null || !wordProperty.mHasShortcuts) return null

        return wordProperty.mShortcutTargets?.firstOrNull()?.mWord
    }

    fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        if (!enabled) return
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private fun addRecentKey(key: Key) {
        if (Settings.getValues().mIncognitoModeEnabled) return
        val split = Settings.getValues().mSplitToolbar
        if (mEmojiCategory.isInRecentTab() && !split) {
            getRecentsKeyboard().addPendingKey(key)
            return
        }
        getRecentsKeyboard().addKeyFirst(key)
        if (mPager?.adapter != null) {
            mPager!!.adapter!!.notifyItemChanged(mEmojiCategory.getRecentTabId())
        }
        if (split && isShown) {
            populateSuggestionBarWithRecents()
        }
    }

    fun addRecentKey(emoji: String?) {
        if (Settings.getValues().mIncognitoModeEnabled || TextUtils.isEmpty(emoji) || !StringUtils.mightBeEmoji(emoji!!.codePointAt(0))) {
            return
        }
        val split = Settings.getValues().mSplitToolbar
        if (mEmojiCategory.isInRecentTab() && !split) {
            getRecentsKeyboard().addPendingStringKey(emoji)
            return
        }
        getRecentsKeyboard().addStringKeyFirst(emoji)
        if (mPager?.adapter != null) {
            mPager!!.adapter!!.notifyItemChanged(mEmojiCategory.getRecentTabId())
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
        mPager?.setPadding(leftPadding.toInt(), mPager!!.paddingTop, rightPadding.toInt(), mPager!!.paddingBottom)
        mEmojiCategoryPageIndicatorView?.setPadding(
            leftPadding.toInt(),
            mEmojiCategoryPageIndicatorView!!.paddingTop,
            rightPadding.toInt(),
            mEmojiCategoryPageIndicatorView!!.paddingBottom
        )
    }

    fun stopEmojiPalettes() {
        if (!initialized) return

        if (mInSearchMode) {
            stopSearchMode()
        }

        getRecentsKeyboard().flushPendingRecentKeys()

        val stripView = KeyboardSwitcher.getInstance().suggestionStripView
        stripView?.clearEmojiSuggestions()
    }

    private fun getRecentsKeyboard(): DynamicGridKeyboard {
        return mEmojiCategory.getKeyboard(EmojiCategory.ID_RECENTS, 0)!!
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
            if (mSearchBar != null && !TextUtils.isEmpty(mSearchBar!!.text)) {
                performSearch(mSearchBar!!.text.toString())
            } else {
                populateSuggestionBarWithRecents()
            }
        }
    }

    fun setKeyboardActionListener(listener: KeyboardActionListener?) {
        mKeyboardActionListener = listener ?: KeyboardActionListener.EMPTY_LISTENER
    }

    private fun updateEmojiCategoryPageIdView() {
        if (mEmojiCategoryPageIndicatorView == null) return
        mEmojiCategoryPageIndicatorView!!.setCategoryPageId(
            mEmojiCategory.getCurrentCategoryPageCount(),
            mEmojiCategory.getCurrentCategoryPageId(), 0.0f
        )
    }

    private fun setCurrentCategoryId(categoryId: Int, initial: Boolean) {
        val oldCategoryId = mEmojiCategory.getCurrentCategoryId()
        if (initial || oldCategoryId != categoryId) {
            mEmojiCategory.setCurrentCategoryId(categoryId)

            if (mPager!!.scrollState != ViewPager2.SCROLL_STATE_DRAGGING) {
                mPager!!.setCurrentItem(
                    mEmojiCategory.getTabIdFromCategoryId(mEmojiCategory.getCurrentCategoryId()),
                    !initial && !isAnimationsDisabled()
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

    fun updateColors() {
        mColors = Settings.getValues().mColors
        if (mTabStrip != null) {
            mColors.setBackground(mTabStrip!!, ColorType.STRIP_BACKGROUND)
            for (i in 0 until mTabStrip!!.childCount) {
                val child = mTabStrip!!.getChildAt(i)
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
        if (mEmojiCategoryPageIndicatorView != null) {
            mEmojiCategoryPageIndicatorView!!.setColors(
                mColors.get(ColorType.EMOJI_CATEGORY_SELECTED),
                mColors.get(ColorType.STRIP_BACKGROUND)
            )
        }
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
        updateColors()
        mPager?.adapter?.notifyDataSetChanged()
        closeDictionaryFacilitator()
    }

    private fun initDictionaryFacilitator() {
        val locale = RichInputMethodManager.getInstance().currentSubtype.locale
        if (sDictionaryFacilitator == null || !sDictionaryFacilitator!!.isForLocale(locale)) {
            closeDictionaryFacilitator()
            val dictFile = DictionaryInfoUtils.getCachedDictForLocaleAndType(locale, Dictionary.TYPE_EMOJI, context)
            val dictionary = if (dictFile != null) DictionaryFactory.getDictionary(dictFile, locale) else null
            sDictionaryFacilitator = if (dictionary != null) SingleDictionaryFacilitator(dictionary) else null
        }
    }

    override fun setVisibility(visibility: Int) {
        if (visibility != View.VISIBLE && mInSearchMode) {
            stopSearchMode()
        }
        super.setVisibility(visibility)
    }

    override fun onDetachedFromWindow() {
        if (mInSearchMode) {
            stopSearchMode()
        }
        super.onDetachedFromWindow()
    }

    private fun downloadEmojiDictionary() {
        val locale = RichInputMethodManager.getInstance().currentSubtype.locale
        val lang = locale.language
        val urlStr = Links.DICTIONARY_URL + Links.DICTIONARY_DOWNLOAD_SUFFIX + Links.DICTIONARY_EMOJI_CLDR_SUFFIX + "emoji_$lang.dict"

        Toast.makeText(context, "Downloading Emoji Dictionary...", Toast.LENGTH_SHORT).show()

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
                        Toast.makeText(context, "Emoji dictionary installed!", Toast.LENGTH_SHORT).show()
                        closeDictionaryFacilitator()
                        initDictionaryFacilitator()
                        mIsDownloadingEmojiDict = false
                        updateSplitToolbarEmojiSuggestions()
                        if (mInSearchMode) {
                            stopSearchMode()
                        }
                    }
                } else {
                    throw java.io.IOException("Cache path is null")
                }
            } catch (e: Exception) {
                Log.e("EmojiSearch", "Failed to download dictionary", e)
                this@EmojiPalettesView.post {
                    Toast.makeText(context, "Failed to download dictionary", Toast.LENGTH_SHORT).show()
                    mIsDownloadingEmojiDict = false
                    if (mInSearchMode) {
                        stopSearchMode()
                        startSearchMode()
                    }
                }
            }
        }
    }

    companion object {
        private const val ID_SEARCH_TAB = -2
        private var sDictionaryFacilitator: SingleDictionaryFacilitator? = null

        @JvmStatic
        fun closeDictionaryFacilitator() {
            sDictionaryFacilitator?.closeDictionaries()
            sDictionaryFacilitator = null
        }

        private fun getRecyclerView(view: View): RecyclerView {
            return view.findViewById(R.id.emoji_keyboard_list)
        }
    }
}
