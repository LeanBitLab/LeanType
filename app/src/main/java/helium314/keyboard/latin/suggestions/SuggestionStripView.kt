/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin.suggestions

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.isVisible
import helium314.keyboard.compat.isDeviceLocked
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.addPinnedKey
import helium314.keyboard.latin.utils.createToolbarKey
import helium314.keyboard.latin.utils.dpToPx
import helium314.keyboard.latin.utils.getCodeForToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKeyLongClick
import helium314.keyboard.latin.utils.getEnabledToolbarKeys
import helium314.keyboard.latin.utils.getPinnedToolbarKeys
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.removeFirst
import helium314.keyboard.latin.utils.removePinnedKey
import helium314.keyboard.latin.utils.setToolbarButtonsActivatedStateOnPrefChange
import helium314.keyboard.settings.SettingsWithoutKey
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

@SuppressLint("InflateParams")
class SuggestionStripView(context: Context, attrs: AttributeSet?, defStyle: Int) :
    RelativeLayout(context, attrs, defStyle), View.OnClickListener, OnLongClickListener, OnSharedPreferenceChangeListener {

    /** Construct a [SuggestionStripView] for showing suggestions to be picked by the user. */
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, R.attr.suggestionStripViewStyle)

    interface Listener {
        fun pickSuggestionManually(word: SuggestedWordInfo?)
        fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean)
        fun removeSuggestion(word: String?)
        fun removeExternalSuggestions()
    }

    private val moreSuggestionsContainer: View
    private val wordViews = ArrayList<TextView>()
    private val debugInfoViews = ArrayList<TextView>()
    private val dividerViews = ArrayList<View>()
    private lateinit var layoutHelper: SuggestionStripLayoutHelper

    init {
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.suggestions_strip, this)
        moreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, null)

        val colors = Settings.getValues().mColors
        colors.setBackground(this, ColorType.STRIP_BACKGROUND)
        val customTypeface = Settings.getInstance().customTypeface
        repeat(SuggestedWords.MAX_SUGGESTIONS) {
            val word = TextView(context, null, R.attr.suggestionWordStyle)
            word.contentDescription = resources.getString(R.string.spoken_empty_suggestion)
            word.setOnClickListener(this)
            word.setOnLongClickListener(this)
            if (customTypeface != null)
                word.typeface = customTypeface
            colors.setBackground(word, ColorType.STRIP_BACKGROUND)
            wordViews.add(word)
            val divider = inflater.inflate(R.layout.suggestion_divider, null)
            dividerViews.add(divider)
            val info = TextView(context, null, R.attr.suggestionWordStyle)
            info.setTextColor(colors.get(ColorType.KEY_TEXT))
            info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, DEBUG_INFO_TEXT_SIZE_IN_DIP)
            debugInfoViews.add(info)
        }

        DEBUG_SUGGESTIONS = context.prefs().getBoolean(DebugSettings.PREF_SHOW_SUGGESTION_INFOS, Defaults.PREF_SHOW_SUGGESTION_INFOS)
    }

    // toolbar views, drawables and setup
    private val toolbar: ViewGroup = findViewById(R.id.toolbar)
    private val toolbarContainer: View = findViewById(R.id.toolbar_container)
    private val pinnedKeys: ViewGroup = findViewById(R.id.pinned_keys)
    private val suggestionsStrip: ViewGroup = findViewById(R.id.suggestions_strip)
    private val toolbarExpandKey = findViewById<ImageButton>(R.id.suggestions_strip_toolbar_key)
    private val toolbarArrowIcon = KeyboardIconsSet.instance.getNewDrawable(KeyboardIconsSet.NAME_TOOLBAR_KEY, context)
    private val defaultToolbarBackground: Drawable = toolbarExpandKey.background
    private val enabledToolKeyBackground = GradientDrawable()
    private var direction = 1 // 1 if LTR, -1 if RTL

    // Track whether the user manually toggles the toolbar open/close
    var isToolbarManuallyOpen: Boolean = false

    // Translate language selector
    private var isTranslateLanguageSelectorVisible = false
    private val translateLanguageSelector: ViewGroup = findViewById(R.id.translate_language_selector)
    private val translateLanguageCloseButton: ImageButton by lazy {
        findViewById(R.id.translate_language_close_button)
    }

    // Loading animation for proofreading/translation
    private var loadingAnimator: ValueAnimator? = null
    private val loadingBorderDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 8f
        setStroke(4, Color.TRANSPARENT)
        setColor(Color.TRANSPARENT)
    }
    private var isLoadingAnimationActive = false
    private var transientToolbarKeys: List<ToolbarKey> = emptyList()

    private val toolbarKeyLayoutParams = LinearLayout.LayoutParams(
        resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width),
        resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width)
    ).apply { gravity = android.view.Gravity.CENTER_VERTICAL }

    init {
        val settingsValues = Settings.getValues()
        if (settingsValues.mRememberToolbarState) {
            isToolbarManuallyOpen = context.prefs().getBoolean(Settings.PREF_TOOLBAR_EXPANDED, settingsValues.mAutoShowToolbar)
        } else {
            isToolbarManuallyOpen = settingsValues.mAutoShowToolbar
        }
        
        val colors = settingsValues.mColors

        // expand key
        // weird way of setting size (default is config_suggestions_strip_edge_key_width)
        // but better not change it or people will complain
        val toolbarHeight = min(toolbarExpandKey.layoutParams.height, resources.getDimension(R.dimen.config_suggestions_strip_height).toInt())
        toolbarExpandKey.layoutParams.height = toolbarHeight
        toolbarExpandKey.layoutParams.width = toolbarHeight // we want it square
        colors.setBackground(toolbarExpandKey, ColorType.STRIP_BACKGROUND) // necessary because background is re-used for defaultToolbarBackground
        colors.setColor(toolbarExpandKey, ColorType.TOOL_BAR_EXPAND_KEY)
        colors.setColor(toolbarExpandKey.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)

        // background indicator for pinned keys
        val color = colors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND) or -0x1000000 // ignore alpha (in Java this is more readable 0xFF000000)
        enabledToolKeyBackground.colors = intArrayOf(color, Color.TRANSPARENT)
        enabledToolKeyBackground.gradientType = GradientDrawable.RADIAL_GRADIENT
        enabledToolKeyBackground.gradientRadius = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height) / 2.1f

        val mToolbarMode = Settings.getValues().mToolbarMode
        if (mToolbarMode == ToolbarMode.TOOLBAR_KEYS) {
            setToolbarVisibility(true, saveState = false)
        } else if (mToolbarMode == ToolbarMode.EXPANDABLE) {
            // Always start with toolbar collapsed (unless auto-show is enabled)
            isToolbarManuallyOpen = Settings.getValues().mAutoShowToolbar
            setToolbarVisibility(isToolbarManuallyOpen, saveState = false)
        }

        // toolbar keys setup
        rebuildToolbarKeys()

        if (Settings.getValues().mSplitToolbar) {
            val stripHeight = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
            
            val wrapper = findViewById<LinearLayout>(R.id.suggestions_strip_wrapper)
            
            // Set wrapper to vertical
            wrapper.orientation = LinearLayout.VERTICAL
            
            // Create toolbar row for Expand Key, Toolbar, Pinned Keys
            val toolbarRow = LinearLayout(context)
            toolbarRow.orientation = LinearLayout.HORIZONTAL
            toolbarRow.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                stripHeight
            )
            
            // Remove views from wrapper
            wrapper.removeView(toolbarExpandKey)
            wrapper.removeView(toolbarContainer)
            wrapper.removeView(pinnedKeys)
            
            // Set new layout params when adding to toolbarRow
            val expandKeyParams = LinearLayout.LayoutParams(
                toolbarExpandKey.layoutParams.width,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            toolbarExpandKey.layoutParams = expandKeyParams
            
            val toolbarParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f  // weight
            )
            toolbarContainer.layoutParams = toolbarParams
            
            val pinnedParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            pinnedKeys.layoutParams = pinnedParams
            
            // Add views to toolbar row
            toolbarRow.addView(toolbarExpandKey)
            toolbarRow.addView(toolbarContainer)
            toolbarRow.addView(pinnedKeys)
            
            // Add toolbar row to wrapper at the START (Top) - Toolbar at top, Suggestions at bottom
            wrapper.addView(toolbarRow, 0)
            
            // Set suggestions strip params - use weight to fill remaining space
            val suggestionsParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            suggestionsStrip.layoutParams = suggestionsParams
        }

        if (Settings.getValues().mSplitToolbar) {
             // Ensure Expand Key is visible (actually handled in updateKeys now)
        }

        layoutHelper = SuggestionStripLayoutHelper(context, attrs, defStyle, wordViews, dividerViews, debugInfoViews)
        updateKeys()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val stripHeight = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        val split = Settings.getValues().mSplitToolbar
        
        val newHeightSpec = if (split) {
            MeasureSpec.makeMeasureSpec(stripHeight * 2, MeasureSpec.EXACTLY)
        } else {
            MeasureSpec.makeMeasureSpec(stripHeight, MeasureSpec.EXACTLY)
        }
        super.onMeasure(widthMeasureSpec, newHeightSpec)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.prefs().registerOnSharedPreferenceChangeListener(this)
        if (Settings.getValues().mSplitToolbar) {
            updateSplitToolbarState()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (Settings.getValues().mSplitToolbar) {
        }
    }

    private lateinit var listener: Listener
    private var suggestedWords = SuggestedWords.getEmptyInstance()
    private var startIndexOfMoreSuggestions = 0
    private var isExternalSuggestionVisible = false // Required to disable the more suggestions if other suggestions are visible
    private val moreSuggestionsView = moreSuggestionsContainer.findViewById<MoreSuggestionsView>(R.id.more_suggestions_view).apply {
        val slidingListener = object : SimpleOnGestureListener() {
            override fun onScroll(down: MotionEvent?, me: MotionEvent, deltaX: Float, deltaY: Float): Boolean {
                if (down == null) return false
                val dy = me.y - down.y
                return if (toolbarContainer.visibility != VISIBLE && deltaY > 0 && dy < (-10).dpToPx(resources)) showMoreSuggestions()
                else false
            }
        }
        gestureDetector = GestureDetector(context, slidingListener)
    }

    // public stuff

    val isShowingMoreSuggestionPanel get() = moreSuggestionsView.isShowingInParent

    /** A connection back to the input method. */
    fun setListener(newListener: Listener, inputView: View) {
        listener = newListener
        moreSuggestionsView.listener = newListener
        moreSuggestionsView.mainKeyboardView = inputView.findViewById(R.id.keyboard_view)
    }

    fun setRtl(isRtlLanguage: Boolean) {
        val newLayoutDirection: Int
        if (!Settings.getValues().mVarToolbarDirection)
            newLayoutDirection = LAYOUT_DIRECTION_LOCALE
        else {
            newLayoutDirection = if (isRtlLanguage) LAYOUT_DIRECTION_RTL else LAYOUT_DIRECTION_LTR
            direction = if (isRtlLanguage) -1 else 1
            toolbarExpandKey.scaleX = (if (toolbarContainer.visibility != VISIBLE) 1f else -1f) * direction
        }
        layoutDirection = newLayoutDirection
        suggestionsStrip.layoutDirection = newLayoutDirection
    }

    // Overload for Java compatibility (default saveState = false)
    @JvmOverloads
    fun setToolbarVisibility(toolbarVisible: Boolean, saveState: Boolean = false) {
        // avoid showing toolbar keys when locked
        val locked = isDeviceLocked(context)
        val split = Settings.getValues().mSplitToolbar

        // In split mode, show only full toolbar, hide pinned keys
        if (split) {
            // suggestionsStrip visibility is handled dynamically in updateSplitToolbarState
            toolbarContainer.isVisible = !locked // Show full toolbar in split mode
            toolbar.visibility = VISIBLE
            pinnedKeys.isVisible = false // Hide pinned keys
            toolbarExpandKey.isVisible = false // Hide expand key
            updateSplitToolbarState()
        } else {
            // When toolbar expands: show toolbar container, hide pinnedKeys container and suggestions
            // When toolbar collapsed: show suggestions + pinnedKeys container, hide toolbar container
            pinnedKeys.isVisible = !locked && !toolbarVisible
            suggestionsStrip.isVisible = locked || !toolbarVisible
            toolbarContainer.isVisible = !locked && toolbarVisible
        }

        if (DEBUG_SUGGESTIONS) {
            for (view in debugInfoViews) {
                view.visibility = suggestionsStrip.visibility
            }
        }

        toolbarExpandKey.scaleX = (if (toolbarVisible && !locked) -1f else 1f) * direction

        if (saveState && Settings.getValues().mRememberToolbarState) {
            context.prefs().edit().putBoolean(Settings.PREF_TOOLBAR_EXPANDED, toolbarVisible).apply()
        }
    }

    /** Collapse the toolbar and show suggestions instead. Called from LatinIME.onStartInputView() or for auto-hide. */
    fun foldToolbar(saveState: Boolean = false) {
        isToolbarManuallyOpen = false
        setToolbarVisibility(false, saveState = saveState)
    }

    fun setSuggestions(suggestions: SuggestedWords, isRtlLanguage: Boolean) {
        if (isExternalSuggestionVisible && (suggestions.isEmpty || suggestions.isPunctuationSuggestions)) {
            // Keep external suggestion (clipboard/screenshot) if new suggestions are empty or just punctuation
            return
        }
        clear()
        isExternalSuggestionVisible = false
        setRtl(isRtlLanguage)
        suggestedWords = suggestions
        startIndexOfMoreSuggestions = layoutHelper.layoutAndReturnStartIndexOfMoreSuggestions(
            context, suggestedWords, suggestionsStrip, this
        )
        updateKeys()
        // Update toolbar visibility state
        val settingsValues = Settings.getValues()
        if (settingsValues.mToolbarMode == ToolbarMode.EXPANDABLE && !settingsValues.mSplitToolbar) {
            setToolbarVisibility(isToolbarManuallyOpen, saveState = false)
        }
        updateSplitToolbarState()
    }

    fun setExternalSuggestionView(view: View?, addCloseButton: Boolean) {
        setExternalSuggestionView(view, addCloseButton, false)
    }

    fun setExternalSuggestionView(view: View?, addCloseButton: Boolean, fillAvailableWidth: Boolean) {
        if (view == null) return
        clear()
        if (view == null) {
            isExternalSuggestionVisible = false
            updateSplitToolbarState()
            return
        }
        isExternalSuggestionVisible = true

        if (addCloseButton) {
            val wrapper = LinearLayout(context)
            wrapper.layoutParams = if (fillAvailableWidth) {
                LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            } else {
                LinearLayout.LayoutParams(suggestionsStrip.width - 30.dpToPx(resources), LayoutParams.MATCH_PARENT)
            }
            view.layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            wrapper.addView(view)
            suggestionsStrip.addView(wrapper)

            val closeButton = createToolbarKey(context, ToolbarKey.CLOSE_HISTORY)
            closeButton.layoutParams = toolbarKeyLayoutParams
            setupKey(closeButton, Settings.getValues().mColors)
            closeButton.setOnClickListener {
                listener.removeExternalSuggestions()
            }
            suggestionsStrip.addView(closeButton)
        } else {
            suggestionsStrip.addView(view)
        }

        if (Settings.getValues().mAutoHideToolbar) setToolbarVisibility(false, saveState = false)
        updateSplitToolbarState()
    }

    fun setTransientToolbarKeys(keys: List<ToolbarKey>) {
        val distinctKeys = keys.distinct()
        if (transientToolbarKeys == distinctKeys) return
        transientToolbarKeys = distinctKeys
        rebuildToolbarKeys()
        updateKeys()
    }

    fun setMoreSuggestionsHeight(remainingHeight: Int) {
        layoutHelper.setMoreSuggestionsHeight(remainingHeight)
    }

    fun dismissMoreSuggestionsPanel() {
        moreSuggestionsView.dismissPopupKeysPanel()
    }

    /**
     * Shows pulse border loading animation on the whole toolbar.
     * Used during proofreading/translation API calls.
     */
    fun showLoadingAnimation() {
        if (isLoadingAnimationActive) return
        isLoadingAnimationActive = true
        
        // Set loading border on the whole toolbar view
        this.foreground = loadingBorderDrawable

        // Change proofread key icon to cancel/close icon
        val closeIcon = KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase(java.util.Locale.US), context)
        if (closeIcon != null) {
            val proofreadKey = toolbar.findViewWithTag<ImageButton>(ToolbarKey.PROOFREAD)
                ?: pinnedKeys.findViewWithTag<ImageButton>(ToolbarKey.PROOFREAD)
            proofreadKey?.setImageDrawable(closeIcon)
            if (proofreadKey != null) {
                Settings.getValues().mColors.setColor(proofreadKey, ColorType.TOOL_BAR_KEY)
            }
        }
        
        // Get accent color from theme (GESTURE_TRAIL is the accent color)
        val accentColor = Settings.getValues().mColors.get(ColorType.GESTURE_TRAIL) 
        
        // Create pulse animation
        loadingAnimator = ValueAnimator.ofFloat(0.25f, 1f).apply {
            duration = 800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val alpha = (animator.animatedValue as Float * 255).toInt()
                val animatedColor = (alpha shl 24) or (accentColor and 0x00FFFFFF)
                loadingBorderDrawable.setStroke(4, animatedColor)
            }
            start()
        }
    }

    /**
     * Hides the pulse border loading animation.
     */
    fun hideLoadingAnimation() {
        if (!isLoadingAnimationActive) return
        isLoadingAnimationActive = false
        
        loadingAnimator?.cancel()
        loadingAnimator = null
        loadingBorderDrawable.setStroke(4, Color.TRANSPARENT)
        this.foreground = null

        // Restore proofread key icon
        val proofreadIcon = KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.PROOFREAD.name.lowercase(java.util.Locale.US), context)
        if (proofreadIcon != null) {
            val proofreadKey = toolbar.findViewWithTag<ImageButton>(ToolbarKey.PROOFREAD)
                ?: pinnedKeys.findViewWithTag<ImageButton>(ToolbarKey.PROOFREAD)
            proofreadKey?.setImageDrawable(proofreadIcon)
            if (proofreadKey != null) {
                Settings.getValues().mColors.setColor(proofreadKey, ColorType.TOOL_BAR_KEY)
            }
        }
    }

    // overrides: necessarily public, but not used from outside

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        setToolbarButtonsActivatedStateOnPrefChange(pinnedKeys, key)
        setToolbarButtonsActivatedStateOnPrefChange(toolbar, key)
        if (key == Settings.PREF_PINNED_TOOLBAR_KEYS || key == Settings.PREF_TOOLBAR_KEYS || key == Settings.PREF_QUICK_PIN_TOOLBAR_KEYS || key == Settings.PREF_AUTO_HIDE_PINNED_KEYS || key == Settings.PREF_SPLIT_TOOLBAR) {
            rebuildToolbarKeys()
            // Update visibility with auto-hide logic
            setToolbarVisibility(isToolbarManuallyOpen, false)
        }
    }

    override fun onVisibilityChanged(view: View, visibility: Int) {
        super.onVisibilityChanged(view, visibility)
        // workaround for a bug with inline suggestions views that just keep showing up otherwise, https://github.com/Helium314/HeliBoard/pull/386
        if (view === this) {
            if (visibility == View.VISIBLE) {
                setToolbarVisibility(isToolbarManuallyOpen, saveState = false)
            } else {
                suggestionsStrip.visibility = visibility
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.prefs().unregisterOnSharedPreferenceChangeListener(this)
        dismissMoreSuggestionsPanel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Called by the framework when the size is known. Show the important notice if applicable.
        // This may be overridden by showing suggestions later, if applicable.
    }

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
        // Don't populate accessibility event with suggested words and voice key.
        return true
    }

    override fun onInterceptTouchEvent(motionEvent: MotionEvent): Boolean {
        // Disable More Suggestions if external suggestions are visible
        if (isExternalSuggestionVisible) {
            return false
        }
        
        // In split mode, don't intercept touches on the top row (toolbar row)
        // to prevent accidentally cancelling long presses on toolbar buttons.
        if (Settings.getValues().mSplitToolbar) {
            val stripHeight = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
            if (motionEvent.y < stripHeight) {
                return false
            }
        }

        // Detecting sliding up finger to show MoreSuggestionsView.
        return moreSuggestionsView.shouldInterceptTouchEvent(motionEvent)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        moreSuggestionsView.touchEvent(motionEvent)
        return true
    }

    override fun onClick(view: View) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this, HapticEvent.KEY_PRESS)
        val tag = view.tag
        if (tag is ToolbarKey) {
            val code = getCodeForToolbarKey(tag)
            if (code != KeyCode.UNSPECIFIED) {
                Log.d(TAG, "click toolbar key $tag")
                listener.onCodeInput(code, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false)
                if (tag === ToolbarKey.INCOGNITO) updateKeys() // update expand key icon
                return
            }
        }
        if (view === toolbarExpandKey) {
            val willBeVisible = toolbarContainer.visibility != VISIBLE
            isToolbarManuallyOpen = willBeVisible
            setToolbarVisibility(willBeVisible, saveState = true)
        }

        // tag for word views is set in SuggestionStripLayoutHelper (setupWordViewsTextAndColor, layoutPunctuationSuggestions)
        if (tag is Int) {
            if (tag >= suggestedWords.size()) {
                return
            }
            val wordInfo = suggestedWords.getInfo(tag)
            listener.pickSuggestionManually(wordInfo)
        }
    }

    override fun onLongClick(view: View): Boolean {
        AudioAndHapticFeedbackManager.getInstance().performHapticFeedback(this, HapticEvent.KEY_LONG_PRESS)
        if (view.tag is ToolbarKey) {
            onLongClickToolbarKey(view)
            return true
        }
        return if (view is TextView && wordViews.contains(view)) {
            onLongClickSuggestion(view)
        } else {
            showMoreSuggestions()
        }
    }

    // actually private stuff

    private fun onLongClickToolbarKey(view: View) {
        val tag = view.tag as? ToolbarKey ?: return

        // Special handling for TRANSLATE key - always allow language selector
        if (tag === ToolbarKey.TRANSLATE) {
            val longClickCode = getCodeForToolbarKeyLongClick(tag)
            if (longClickCode != KeyCode.UNSPECIFIED) {
                listener.onCodeInput(longClickCode, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false)
            }
            return
        }

        // Disable pinning when split toolbar is enabled
        if (Settings.getValues().mSplitToolbar || !Settings.getValues().mQuickPinToolbarKeys) {
            // Quick Pin disabled or Split Toolbar enabled: Perform standard long-press action
            val longClickCode = getCodeForToolbarKeyLongClick(tag)
            if (longClickCode != KeyCode.UNSPECIFIED) {
                listener.onCodeInput(longClickCode, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false)
            }
        } else {
            if (view.parent === toolbar) {
                // Pin: Move from toolbar to pinned keys
                addPinnedKey(context.prefs(), tag)
            } else if (view.parent === pinnedKeys) {
                // Unpin: Move from pinned keys back to toolbar
                removePinnedKey(context.prefs(), tag)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility") // no need for View#performClick, we only return false mostly anyway
    private fun onLongClickSuggestion(wordView: TextView): Boolean {
        var showIcon = true
        if (wordView.tag is Int) {
            val index = wordView.tag as Int
            if (index < suggestedWords.size() && suggestedWords.getInfo(index).mSourceDict == Dictionary.DICTIONARY_USER_TYPED)
                showIcon = false
        }
        if (showIcon) {
            val icon = KeyboardIconsSet.instance.getNewDrawable(KeyboardIconsSet.NAME_BIN, context)!!
            Settings.getValues().mColors.setColor(icon, ColorType.REMOVE_SUGGESTION_ICON)
            val w = icon.intrinsicWidth
            val h = icon.intrinsicHeight
            wordView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
            wordView.ellipsize = TextUtils.TruncateAt.END
            val downOk = AtomicBoolean(false)
            wordView.setOnTouchListener { _, motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_UP && downOk.get()) {
                    val x = motionEvent.x
                    val y = motionEvent.y
                    if (0 < x && x < w && 0 < y && y < h) {
                        removeSuggestion(wordView)
                        wordView.cancelLongPress()
                        wordView.isPressed = false
                        return@setOnTouchListener true
                    }
                } else if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    val x = motionEvent.x
                    val y = motionEvent.y
                    if (0 < x && x < w && 0 < y && y < h) {
                        downOk.set(true)
                    }
                }
                false
            }
        }
        if (DebugFlags.DEBUG_ENABLED && (isShowingMoreSuggestionPanel || !showMoreSuggestions())) {
            showSourceDict(wordView)
            return true
        }
        return showMoreSuggestions()
    }

    private fun showMoreSuggestions(): Boolean {
        if (suggestedWords.size() <= startIndexOfMoreSuggestions) {
            return false
        }
        if (!moreSuggestionsView.show(
                suggestedWords, startIndexOfMoreSuggestions, moreSuggestionsContainer, layoutHelper, this
        ))
            return false
        for (i in 0..<startIndexOfMoreSuggestions) {
            wordViews[i].isPressed = false
        }
        return true
    }

    private fun showSourceDict(wordView: TextView) {
        val word = wordView.text.toString()
        val index = wordView.tag as? Int ?: return
        if (index >= suggestedWords.size()) return
        val info = suggestedWords.getInfo(index)
        if (info.word != word) return

        val text = info.mSourceDict.mDictType + ":" + info.mSourceDict.mLocale
        if (isShowingMoreSuggestionPanel) {
            moreSuggestionsView.dismissPopupKeysPanel()
        }
        KeyboardSwitcher.getInstance().showToast(text, true)
    }

    private fun removeSuggestion(wordView: TextView) {
        val word = wordView.text.toString()
        listener.removeSuggestion(word)
        moreSuggestionsView.dismissPopupKeysPanel()
        // show suggestions, but without the removed word
        val suggestedWordInfos = ArrayList<SuggestedWordInfo>()
        for (i in 0..<suggestedWords.size()) {
            val info = suggestedWords.getInfo(i)
            if (info.word != word) suggestedWordInfos.add(info)
        }
        suggestedWords.mRawSuggestions?.removeFirst { it.word == word }

        val newSuggestedWords = SuggestedWords(
            suggestedWordInfos, suggestedWords.mRawSuggestions, suggestedWords.typedWordInfo, suggestedWords.mTypedWordValid,
            suggestedWords.mWillAutoCorrect, suggestedWords.mIsObsoleteSuggestions, suggestedWords.mInputStyle, suggestedWords.mSequenceNumber
        )
        setSuggestions(newSuggestedWords, direction != 1)
        suggestionsStrip.isVisible = true

        // Show the toolbar if no suggestions are left and the "Auto show toolbar" setting is enabled
        if (this.suggestedWords.isEmpty && Settings.getValues().mAutoShowToolbar) {
            setToolbarVisibility(true, saveState = false)
        }
    }

    private fun clear() {
        suggestionsStrip.removeAllViews()
        if (DEBUG_SUGGESTIONS) removeAllDebugInfoViews()
        if (!toolbarContainer.isVisible)
            suggestionsStrip.isVisible = true
        dismissMoreSuggestionsPanel()
        for (word in wordViews) {
            word.setOnTouchListener(null)
        }
        updateSplitToolbarState()
    }

    private fun removeAllDebugInfoViews() {
        for (debugInfoView in debugInfoViews) {
            val parent = debugInfoView.parent
            if (parent is ViewGroup) {
                parent.removeView(debugInfoView)
            }
        }
    }

    fun updateVoiceKey() {
        val show = Settings.getValues().mShowsVoiceInputKey
        toolbar.findViewWithTag<View>(ToolbarKey.VOICE)?.isVisible = show
        pinnedKeys.findViewWithTag<View>(ToolbarKey.VOICE)?.isVisible = show
    }

    fun showTranslateLanguageSelector() {
        // Hide other views
        suggestionsStrip.isVisible = false
        toolbarContainer.isVisible = false
        pinnedKeys.isVisible = false
        toolbarExpandKey.isVisible = false

        // Populate language buttons
        val languageList = findViewById<LinearLayout>(R.id.translate_language_list)
        languageList.removeAllViews()

        val languageNames = resources.getStringArray(R.array.translate_language_names)
        val prefs = context.prefs()

        // Create a button for each language
        for (languageName in languageNames) {
            val button = android.widget.TextView(context, null, R.attr.suggestionWordStyle).apply {
                text = languageName
                gravity = android.view.Gravity.CENTER
                setPadding(8.dpToPx(resources), 0, 8.dpToPx(resources), 0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setSingleLine()
                ellipsize = android.text.TextUtils.TruncateAt.END
                // Set minimum width for consistent appearance
                minimumWidth = 100.dpToPx(resources)
            }
            button.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            
            button.setOnClickListener {
                // Set the selected language and start translation
                context.prefs().edit().apply {
                    putString(Settings.PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE, languageName)
                    // Also update Gemini target language
                    putString(SettingsWithoutKey.GEMINI_TARGET_LANGUAGE, languageName)
                }.apply()
                // Hide selector and trigger translation
                hideTranslateLanguageSelector()
                // Trigger translation with new language
                listener.onCodeInput(KeyCode.TRANSLATE, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false)
            }
            Settings.getValues().mColors.setColor(button.background, ColorType.TOOL_BAR_KEY)
            button.setBackgroundResource(R.drawable.toolbar_key_background)
            languageList.addView(button)
        }

        // Setup close button
        translateLanguageCloseButton.isVisible = true
        translateLanguageCloseButton.setOnClickListener {
            hideTranslateLanguageSelector()
        }

        // Show the selector
        translateLanguageSelector.isVisible = true
        isTranslateLanguageSelectorVisible = true
    }

    fun hideTranslateLanguageSelector() {
        translateLanguageSelector.isVisible = false
        translateLanguageCloseButton.isVisible = false

        // Restore normal view
        val settingsValues = Settings.getValues()
        if (!settingsValues.mSplitToolbar) {
            toolbarExpandKey.isVisible = settingsValues.mToolbarMode == ToolbarMode.EXPANDABLE
            setToolbarVisibility(isToolbarManuallyOpen, saveState = false)
        } else {
            toolbarContainer.isVisible = !isDeviceLocked(context)
            toolbar.visibility = VISIBLE
            updateSplitToolbarState()
        }

        isTranslateLanguageSelectorVisible = false
    }

    private fun updateKeys() {
        updateVoiceKey()
        val settingsValues = Settings.getValues()
        val split = settingsValues.mSplitToolbar

        val toolbarIsExpandable = settingsValues.mToolbarMode == ToolbarMode.EXPANDABLE
        toolbarExpandKey.setImageDrawable(toolbarArrowIcon)

        val hideToolbarKeys = isDeviceLocked(context)
        // Keep click listener active in split mode (though key is hidden, better to leave logic clean)
        toolbarExpandKey.setOnClickListener(if (hideToolbarKeys || !toolbarIsExpandable) null else this)
        
        if (split) {
            toolbarExpandKey.isVisible = false
            pinnedKeys.isVisible = false // Hide pinned keys completely in split mode

            toolbarContainer.isVisible = !hideToolbarKeys // Show full toolbar container
            toolbar.visibility = VISIBLE // Show toolbar

            updateVoiceKey() // Re-apply voice logic to pinned keys
            layoutHelper.setSuggestionsCountInStrip(5)
        } else {
            toolbarExpandKey.isVisible = toolbarIsExpandable
            // Don't manage visibility here - let setToolbarVisibility handle it
            // This prevents conflicts with auto-hide pinned keys logic
            layoutHelper.setSuggestionsCountInStrip(3)
        }
    }

    private fun setupKey(view: ImageButton, colors: Colors) {
        view.setOnClickListener(this)
        view.setOnLongClickListener(this)
        colors.setColor(view, ColorType.TOOL_BAR_KEY)
        // Set circular background for toolbar keys
        view.setBackgroundResource(R.drawable.toolbar_key_background)
        colors.setColor(view.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
    }

    private fun rebuildToolbarKeys() {
        toolbar.removeAllViews()
        pinnedKeys.removeAllViews()

        val colors = Settings.getValues().mColors
        val pinnedKeysList = getPinnedToolbarKeys(context.prefs())
        val mToolbarMode = Settings.getValues().mToolbarMode
        val isSplitToolbar = Settings.getValues().mSplitToolbar
        
        // Toolbar keys setup
        // Always populate toolbar keys if mode allows, visibility handled in updateKeys
        if (mToolbarMode == ToolbarMode.TOOLBAR_KEYS || mToolbarMode == ToolbarMode.EXPANDABLE) {
            // In split mode, show ALL enabled keys in the toolbar, ignoring pin status.
            // When autoHidePinnedKeys=false: include pinned keys in toolbar too (they show side-by-side).
            // When autoHidePinnedKeys=true: exclude pinned keys from toolbar (pinnedKeys container handles them).
            val enabledKeys = getEnabledToolbarKeys(context.prefs())
            val keysToRender = if (isSplitToolbar || !Settings.getValues().mAutoHidePinnedKeys) {
                transientToolbarKeys + enabledKeys
            } else {
                transientToolbarKeys + enabledKeys.filterNot { it in pinnedKeysList }
            }
            for (key in keysToRender.distinct()) {
                val button = createToolbarKey(context, key)
                button.layoutParams = toolbarKeyLayoutParams
                setupKey(button, colors)
                toolbar.addView(button)
            }
        }
        
        // Only draw pinned keys if not in split mode
        if (!isSplitToolbar && !Settings.getValues().mSuggestionStripHiddenPerUserSettings) {
            for (pinnedKey in pinnedKeysList) {
                val button = createToolbarKey(context, pinnedKey)
                button.layoutParams = toolbarKeyLayoutParams
                setupKey(button, colors)
                pinnedKeys.addView(button)
            }
        }
        updateVoiceKey()
    }

    private fun updateSplitToolbarState() {
        if (!Settings.getValues().mSplitToolbar) return
        suggestionsStrip.isVisible = true
        
        val PLACEHOLDER_TAG = "PLACEHOLDER_VIEW"
        val placeholder = suggestionsStrip.findViewWithTag<View>(PLACEHOLDER_TAG)
        
        // Check if there are any visible suggestions with actual text content
        var hasRealSuggestions = false
        for (i in 0 until suggestionsStrip.childCount) {
            val child = suggestionsStrip.getChildAt(i)
            if (child.tag != PLACEHOLDER_TAG && child is TextView && !child.text.isNullOrEmpty()) {
                hasRealSuggestions = true
                break
            }
        }

        if (hasRealSuggestions) {
            // Real suggestions exist, remove placeholder
            if (placeholder != null) suggestionsStrip.removeView(placeholder)
        } else {
            // No suggestions, show random placeholder suggestions
            if (placeholder == null) {
                 val placeholderContainer = LinearLayout(context)
                 placeholderContainer.tag = PLACEHOLDER_TAG
                 placeholderContainer.orientation = LinearLayout.HORIZONTAL
                 placeholderContainer.layoutParams = LinearLayout.LayoutParams(
                     LinearLayout.LayoutParams.MATCH_PARENT, 
                     LinearLayout.LayoutParams.MATCH_PARENT
                 )
                 
                 // Random suggestion words to display
                 val randomSuggestions = listOf(
                     "the", "and", "for", "you", "with",
                     "have", "this", "from", "will", "can",
                     "hello", "thanks", "please", "okay", "good"
                 ).shuffled().take(5)
                 
                 val colors = Settings.getValues().mColors
                 val customTypeface = Settings.getInstance().customTypeface
                 
                 randomSuggestions.forEach { word ->
                     val suggestionView = TextView(context, null, R.attr.suggestionWordStyle)
                     suggestionView.text = word
                     suggestionView.gravity = android.view.Gravity.CENTER
                     suggestionView.alpha = 0.4f // More transparent to indicate they're placeholders
                     if (customTypeface != null)
                         suggestionView.typeface = customTypeface
                     colors.setBackground(suggestionView, ColorType.STRIP_BACKGROUND)
                     suggestionView.setTextColor(colors.get(ColorType.KEY_TEXT))
                     
                     val params = LinearLayout.LayoutParams(
                         0,
                         LinearLayout.LayoutParams.MATCH_PARENT,
                         1f
                     )
                     suggestionView.layoutParams = params
                     placeholderContainer.addView(suggestionView)
                 }
                 
                 suggestionsStrip.addView(placeholderContainer)
            }
        }
    }

    private var isShowingEmojiSuggestions = false

    /**
     * Populates the suggestion strip with emoji items (used in split toolbar mode).
     * @param emojis List of emoji strings to display
     * @param onEmojiClick Callback when an emoji is tapped
     */
    fun setEmojiSuggestions(emojis: List<String>, onEmojiClick: java.util.function.Consumer<String>) {
        if (!Settings.getValues().mSplitToolbar) return
        isShowingEmojiSuggestions = true
        suggestionsStrip.removeAllViews()

        val colors = Settings.getValues().mColors
        val customTypeface = Settings.getInstance().customTypeface
        val stripHeight = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)

        // Create a horizontal scroll container for emojis
        val scrollView = android.widget.HorizontalScrollView(context)
        scrollView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        scrollView.isHorizontalScrollBarEnabled = false

        val emojiContainer = LinearLayout(context)
        emojiContainer.orientation = LinearLayout.HORIZONTAL
        emojiContainer.gravity = android.view.Gravity.CENTER_VERTICAL
        emojiContainer.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        for (emoji in emojis) {
            val emojiView = TextView(context)
            emojiView.text = emoji
            emojiView.textSize = 22f
            if (customTypeface != null) emojiView.typeface = customTypeface
            emojiView.gravity = android.view.Gravity.CENTER
            emojiView.setPadding(
                8.dpToPx(resources), 2.dpToPx(resources), 
                8.dpToPx(resources), 2.dpToPx(resources)
            )
            emojiView.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            emojiView.setOnClickListener {
                AudioAndHapticFeedbackManager.getInstance()
                    .performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this, HapticEvent.KEY_PRESS)
                onEmojiClick.accept(emoji)
            }
            emojiContainer.addView(emojiView)
        }

        scrollView.addView(emojiContainer)
        suggestionsStrip.addView(scrollView)
        suggestionsStrip.isVisible = true
    }

    /**
     * Clears emoji suggestions and restores normal suggestion strip state.
     */
    fun clearEmojiSuggestions() {
        if (!isShowingEmojiSuggestions) return
        isShowingEmojiSuggestions = false
        suggestionsStrip.removeAllViews()
        updateKeys()
        // Update toolbar visibility state
        val settingsValues = Settings.getValues()
        if (settingsValues.mToolbarMode == ToolbarMode.EXPANDABLE && !settingsValues.mSplitToolbar) {
            setToolbarVisibility(isToolbarManuallyOpen, saveState = false)
        }
        updateSplitToolbarState()
    }

    companion object {
        @JvmField
        var DEBUG_SUGGESTIONS = false
        private const val DEBUG_INFO_TEXT_SIZE_IN_DIP = 6.5f
        private val TAG = SuggestionStripView::class.java.simpleName
    }
}
