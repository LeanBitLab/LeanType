/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin.suggestions

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import java.util.Locale
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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.leanbitlab.leantype.voice.VoiceConstants
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
import helium314.keyboard.latin.utils.setToolbarButtonActivatedState
import helium314.keyboard.latin.utils.isRepeatableToolbarKey
import helium314.keyboard.latin.utils.RepeatableKeyTouchListener
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.dpToPx
import helium314.keyboard.latin.utils.getCodeForToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKeyLongClick
import helium314.keyboard.latin.utils.getEnabledToolbarKeys
import helium314.keyboard.latin.utils.getPinnedToolbarKeys
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.removeFirst
import helium314.keyboard.latin.utils.removePinnedKey
import helium314.keyboard.latin.utils.setToolbarButtonsActivatedStateOnPrefChange
import helium314.keyboard.latin.utils.isMainDictionaryMissing
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.settings.SettingsWithoutKey

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
    private val deleteModeRunnables = mutableMapOf<TextView, Runnable>()

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
    private var toolbarRow: LinearLayout? = null
    private var dictDownloadButton: ImageButton? = null
    private val toolbarArrowIcon = KeyboardIconsSet.instance.getNewDrawable(KeyboardIconsSet.NAME_TOOLBAR_KEY, context)
    private val defaultToolbarBackground: Drawable = toolbarExpandKey.background
    private val enabledToolKeyBackground = GradientDrawable()
    private var direction = 1 // 1 if LTR, -1 if RTL

    // Track whether the user manually toggles the toolbar open/close
    var isToolbarManuallyOpen: Boolean = false

    // Translate language selector
    private var isTranslateLanguageSelectorVisible = false
    private val translateLanguageContainer: View = findViewById(R.id.translate_language_container)
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

    private val keyDimension: Int
        get() {
            val stripHeight = ResourceUtils.getSuggestionsStripHeight(resources)
            val defaultEdgeWidth = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width)
            val defaultStripHeight = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
            val ratio = if (defaultStripHeight > 0) defaultEdgeWidth.toFloat() / defaultStripHeight else 0.9f
            return (stripHeight * ratio).toInt()
        }

    private val toolbarKeyLayoutParams: LinearLayout.LayoutParams
        get() = LinearLayout.LayoutParams(keyDimension, keyDimension).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

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
        val toolbarHeight = keyDimension
        toolbarExpandKey.layoutParams.height = toolbarHeight
        toolbarExpandKey.layoutParams.width = toolbarHeight // we want it square
        toolbarExpandKey.setBackgroundResource(R.drawable.toolbar_key_background)
        val defaultStripHeight = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height).toFloat()
        val stripHeight = ResourceUtils.getSuggestionsStripHeight(resources).toFloat()
        val effectiveScale = if (defaultStripHeight > 0f) stripHeight / defaultStripHeight else 1.0f
        val expandPadding = (9 * effectiveScale).toInt().dpToPx(resources).coerceAtLeast(2)
        toolbarExpandKey.setPadding(expandPadding, expandPadding, expandPadding, expandPadding)
        colors.setColor(toolbarExpandKey, ColorType.TOOL_BAR_EXPAND_KEY)
        colors.setColor(toolbarExpandKey.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)

        // background indicator for pinned keys
        val color = colors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND) or -0x1000000 // ignore alpha (in Java this is more readable 0xFF000000)
        enabledToolKeyBackground.colors = intArrayOf(color, Color.TRANSPARENT)
        enabledToolKeyBackground.gradientType = GradientDrawable.RADIAL_GRADIENT
        enabledToolKeyBackground.gradientRadius = ResourceUtils.getSuggestionsStripHeight(resources) / 2.1f

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
            val stripHeight = ResourceUtils.getSuggestionsStripHeight(resources)
            
            val wrapper = findViewById<LinearLayout>(R.id.suggestions_strip_wrapper)
            
            // Set wrapper to vertical
            wrapper.orientation = LinearLayout.VERTICAL
            
            // Create toolbar row for Expand Key, Toolbar, Pinned Keys
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    stripHeight
                )
            }
            toolbarRow = row
            
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
            row.addView(toolbarExpandKey)
            row.addView(toolbarContainer)
            row.addView(pinnedKeys)
            
            // Add toolbar row to wrapper at the START (Top) - Toolbar at top, Suggestions at bottom
            wrapper.addView(row, 0)
            
            // Set suggestions strip params - use weight to fill remaining space
            val suggestionsParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            suggestionsStrip.layoutParams = suggestionsParams
            translateLanguageContainer.layoutParams = suggestionsParams
        }

        if (Settings.getValues().mSplitToolbar) {
             // Ensure Expand Key is visible (actually handled in updateKeys now)
        }

        layoutHelper = SuggestionStripLayoutHelper(context, attrs, defStyle, wordViews, dividerViews, debugInfoViews)
        updateKeys()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val stripHeight = ResourceUtils.getSuggestionsStripHeight(resources)
        val split = Settings.getValues().mSplitToolbar
        val isEmojiView = split && (isShowingEmojiSuggestions || helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().isShowingEmojiPalettes)

        val newHeightSpec = if (split && !isEmojiView) {
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

    private var swipeDownStartY = 0f
    private var swipeDownStartX = 0f
    private var isSwipeDownTriggered = false
    private var swipeVelocityTracker: VelocityTracker? = null

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
            val isEmojiView = isShowingEmojiSuggestions || helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().isShowingEmojiPalettes
            toolbarRow?.isVisible = !isEmojiView
            toolbarContainer.isVisible = !locked && !isEmojiView
            toolbar.visibility = if (isEmojiView) GONE else VISIBLE
            pinnedKeys.isVisible = false // Hide pinned keys
            toolbarExpandKey.isVisible = false // Hide expand key
            updateSplitToolbarState()
        } else {
            val mode = Settings.getValues().mToolbarMode
            val forceToolbar = mode == ToolbarMode.TOOLBAR_KEYS
            val effectiveToolbarVisible = forceToolbar || toolbarVisible
            val showPinned = !locked && !effectiveToolbarVisible && mode != ToolbarMode.SUGGESTION_STRIP
            pinnedKeys.isVisible = showPinned
            suggestionsStrip.isVisible = locked || !effectiveToolbarVisible
            toolbarContainer.isVisible = !locked && effectiveToolbarVisible
        }

        if (DEBUG_SUGGESTIONS) {
            for (view in debugInfoViews) {
                view.visibility = suggestionsStrip.visibility
            }
        }

        toolbarExpandKey.scaleX = (if (toolbarVisible && !locked) -1f else 1f) * direction

        applyToolbarKeyLayoutParams(toolbarVisible && !locked)
        toolbarContainer.post { applyToolbarKeyLayoutParams(toolbarContainer.isVisible) }

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
        if (isVoiceActive) return

        if (isShowingEmojiSuggestions && !helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().isShowingEmojiPalettes) {
            isShowingEmojiSuggestions = false
        }
        if (isShowingEmojiSuggestions) return
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

    fun pickSuggestionByVisualPosition(positionInStrip: Int): Boolean {
        disarmDeleteMode()
        if (suggestedWords.isEmpty || suggestedWords.isPunctuationSuggestions) return false

        val wordView = wordViews.getOrNull(positionInStrip) ?: return false
        if (!wordView.isEnabled) return false

        val tag = wordView.tag as? Int ?: return false
        if (tag >= suggestedWords.size()) return false

        listener.pickSuggestionManually(suggestedWords.getInfo(tag))
        return true
    }

    fun setExternalSuggestionView(view: View?, addCloseButton: Boolean) {
        if (isVoiceActive) return
        if (isShowingEmojiSuggestions && !helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().isShowingEmojiPalettes) {
            isShowingEmojiSuggestions = false
        }
        if (isShowingEmojiSuggestions) return
        clear()
        if (view == null) {
            isExternalSuggestionVisible = false
            updateSplitToolbarState()
            return
        }
        isExternalSuggestionVisible = true

        if (addCloseButton) {
            val wrapper = LinearLayout(context)
            wrapper.layoutParams = LinearLayout.LayoutParams(suggestionsStrip.width - 30.dpToPx(resources), LayoutParams.MATCH_PARENT)
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

    private var isVoiceActive = false
    private var voiceVisualizerView: VoiceVisualizerView? = null

    @JvmOverloads
    fun showVoiceStatus(
        statusText: String,
        isProcessing: Boolean,
        onStop: Runnable? = null,
        onCancel: Runnable? = null,
        mode: VoiceVisualizerView.Mode = if (isProcessing) VoiceVisualizerView.Mode.PROCESSING else VoiceVisualizerView.Mode.RECORDING
    ) {
        clear()
        isExternalSuggestionVisible = true
        isVoiceActive = true

        val colors = Settings.getValues().mColors
        val accentColor = colors.get(ColorType.GESTURE_TRAIL)
        val textColor = colors.get(ColorType.KEY_TEXT)
        val actionColor = if (accentColor != 0) accentColor else textColor

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setPadding(8.dpToPx(resources), 0, 4.dpToPx(resources), 0)
        }

        // Microphone Icon
        val micIconView = android.widget.ImageView(context).apply {
            val micDrawable = KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.VOICE.name.lowercase(Locale.US), context)
            setImageDrawable(micDrawable)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            val pad = 6.dpToPx(resources)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(34.dpToPx(resources), 34.dpToPx(resources))
            colors.setColor(this, ColorType.TOOL_BAR_KEY)
        }
        container.addView(micIconView)

        // Animated Audio Waveform Visualizer
        val visualizer = VoiceVisualizerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(28.dpToPx(resources), LayoutParams.MATCH_PARENT).apply {
                marginStart = 2.dpToPx(resources)
                marginEnd = 6.dpToPx(resources)
            }
            setColor(actionColor)
            setMode(mode)
        }
        voiceVisualizerView = visualizer
        container.addView(visualizer)

        // Status Text
        val textView = TextView(context).apply {
            text = statusText
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = 4.dpToPx(resources)
                marginEnd = 4.dpToPx(resources)
            }
        }
        container.addView(textView)

        // Done / Stop Button (Checkmark)
        if (!isProcessing && onStop != null) {
            val doneButton = ImageButton(context, null, R.attr.suggestionWordStyle).apply {
                val doneIcon = KeyboardIconsSet.instance.getNewDrawable(KeyboardIconsSet.NAME_DONE_KEY, context)
                setImageDrawable(doneIcon)
                setBackgroundResource(R.drawable.toolbar_key_background)
                colors.setColor(background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
                colors.setColor(this, ColorType.TOOL_BAR_KEY)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                val pad = 9.dpToPx(resources)
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams(40.dpToPx(resources), LayoutParams.MATCH_PARENT)
                contentDescription = context.getString(R.string.voice_action_done)
                setOnClickListener {
                    AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this@SuggestionStripView, HapticEvent.KEY_PRESS)
                    onStop.run()
                }
            }
            container.addView(doneButton)
        }

        // Close / Cancel Button (X)
        if (onCancel != null) {
            val closeButton = ImageButton(context, null, R.attr.suggestionWordStyle).apply {
                val closeIcon = KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase(Locale.US), context)
                setImageDrawable(closeIcon)
                setBackgroundResource(R.drawable.toolbar_key_background)
                colors.setColor(background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
                colors.setColor(this, ColorType.TOOL_BAR_KEY)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                val pad = 9.dpToPx(resources)
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams(40.dpToPx(resources), LayoutParams.MATCH_PARENT)
                contentDescription = context.getString(R.string.voice_action_cancel)
                setOnClickListener {
                    AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this@SuggestionStripView, HapticEvent.KEY_PRESS)
                    onCancel.run()
                }
            }
            container.addView(closeButton)
        }

        suggestionsStrip.addView(container)
        pinnedKeys.isVisible = false
        suggestionsStrip.isVisible = true
        toolbarContainer.isVisible = false
        updateSplitToolbarState()
    }

    fun hideVoiceStatus() {
        if (!isVoiceActive) return
        isVoiceActive = false
        isExternalSuggestionVisible = false
        voiceVisualizerView?.setMode(VoiceVisualizerView.Mode.IDLE)
        voiceVisualizerView = null
        clear()
        setToolbarVisibility(isToolbarManuallyOpen, saveState = false)
        updateSplitToolbarState()
    }

    // overrides: necessarily public, but not used from outside

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        setToolbarButtonsActivatedStateOnPrefChange(pinnedKeys, key)
        setToolbarButtonsActivatedStateOnPrefChange(toolbar, key)
        if (key == VoiceConstants.PREF_VOICE_OFFLINE_ENABLED) {
            updateVoiceKey()
        }
        if (key == Settings.PREF_PINNED_TOOLBAR_KEYS 
            || key == Settings.PREF_TOOLBAR_KEYS 
            || key == Settings.PREF_QUICK_PIN_TOOLBAR_KEYS 
            || key == Settings.PREF_AUTO_HIDE_PINNED_KEYS 
            || key == Settings.PREF_AUTO_SPAN_TOOLBAR_KEYS
            || key == Settings.PREF_TOOLBAR_KEYS_ALIGNMENT
            || key == Settings.PREF_CLIPBOARD_KEYS_ALIGNMENT
            || key == Settings.PREF_SPLIT_TOOLBAR
            || key == Settings.PREF_SHOW_DOWNLOAD_BUTTON_IN_TOOLBAR
            || key == Settings.PREF_CUSTOM_ICON_NAMES
            || key == Settings.PREF_ICON_STYLE
            || key == Settings.PREF_CLEAR_CLIPBOARD_ICON
            || key == "pref_custom_ai_show_tags_on_toolbar"
            || key?.startsWith("pref_custom_ai_tag_") == true
            || key?.startsWith("pref_dict_download_link_") == true) {
            KeyboardIconsSet.instance.loadIcons(context)
            rebuildToolbarKeys()
            // Update visibility with auto-hide logic
            setToolbarVisibility(isToolbarManuallyOpen, false)
            updateKeys()
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

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (Settings.getValues().mToolbarSwipeDownDismiss) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeDownStartY = ev.rawY
                    swipeDownStartX = ev.rawX
                    isSwipeDownTriggered = false
                    swipeVelocityTracker?.recycle()
                    swipeVelocityTracker = VelocityTracker.obtain().apply {
                        addMovement(ev)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    swipeVelocityTracker?.addMovement(ev)
                    if (!isSwipeDownTriggered) {
                        val dy = ev.rawY - swipeDownStartY
                        val dx = Math.abs(ev.rawX - swipeDownStartX)
                        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
                        val minDistance = Math.max(touchSlop * 2, 20.dpToPx(resources))

                        swipeVelocityTracker?.computeCurrentVelocity(1000)
                        val vy = swipeVelocityTracker?.yVelocity ?: 0f
                        val vx = Math.abs(swipeVelocityTracker?.xVelocity ?: 0f)
                        val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity

                        val isFlingDown = vy > minFlingVelocity && vy > vx * 1.2f && dy > touchSlop
                        val isDragDown = dy > minDistance && dy > dx * 1.2f

                        if (isFlingDown || isDragDown) {
                            isSwipeDownTriggered = true
                            listener.onCodeInput(KeyCode.IME_HIDE_UI, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false)
                            val cancelEvent = MotionEvent.obtain(ev).apply {
                                action = MotionEvent.ACTION_CANCEL
                            }
                            super.dispatchTouchEvent(cancelEvent)
                            cancelEvent.recycle()
                            return true
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    swipeVelocityTracker?.recycle()
                    swipeVelocityTracker = null
                    if (isSwipeDownTriggered) {
                        isSwipeDownTriggered = false
                        return true
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(motionEvent: MotionEvent): Boolean {
        // Disable More Suggestions if external suggestions are visible
        if (isExternalSuggestionVisible) {
            return false
        }

        // In split mode, don't intercept touches on the top row (toolbar row)
        // to prevent accidentally cancelling long presses on toolbar buttons.
        if (Settings.getValues().mSplitToolbar) {
            val stripHeight = ResourceUtils.getSuggestionsStripHeight(resources)
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
        disarmDeleteMode()
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

        val longClickCode = getCodeForToolbarKeyLongClick(tag)
        if (longClickCode != KeyCode.UNSPECIFIED) {
            // Always perform long-press shortcut if one exists
            listener.onCodeInput(longClickCode, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false)
        } else if (Settings.getValues().mQuickPinToolbarKeys && !Settings.getValues().mSplitToolbar) {
            // If no shortcut exists, and quick pin is enabled, perform pinning/unpinning
            if (view.parent === toolbar) {
                addPinnedKey(context.prefs(), tag)
            } else if (view.parent === pinnedKeys) {
                removePinnedKey(context.prefs(), tag)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility") // no need for View#performClick, we only return false mostly anyway
    private fun onLongClickSuggestion(wordView: TextView): Boolean {
        // Cancel any pending restore for this recycled view
        deleteModeRunnables.remove(wordView)?.let(wordView::removeCallbacks)

        var showIcon = true
        if (wordView.tag is Int) {
            val index = wordView.tag as Int
            if (index < suggestedWords.size() && suggestedWords.getInfo(index).mSourceDict == Dictionary.DICTIONARY_USER_TYPED)
                showIcon = false
        }

        if (showIcon) {
            val icon = KeyboardIconsSet.instance.getNewDrawable(KeyboardIconsSet.NAME_BIN, context)
            if (icon == null) return true

            Settings.getValues().mColors.setColor(icon, ColorType.REMOVE_SUGGESTION_ICON)
            wordView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
            wordView.ellipsize = TextUtils.TruncateAt.END

            val savedTag = wordView.tag
            val restoreRunnable = Runnable {
                wordView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
                wordView.setOnTouchListener(null)
                wordView.tag = savedTag
                wordView.setOnClickListener(this)
                deleteModeRunnables.remove(wordView)
            }

            deleteModeRunnables[wordView] = restoreRunnable
            wordView.setOnClickListener { removeSuggestion(wordView) }
            wordView.postDelayed(restoreRunnable, 3000)
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

    fun disarmDeleteMode() {
        for (word in wordViews) {
            deleteModeRunnables.remove(word)?.let(word::removeCallbacks)
            word.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            word.setOnTouchListener(null)
            word.setOnClickListener(this)
        }
        deleteModeRunnables.clear()
    }

    private fun clear() {
        if (isTranslateLanguageSelectorVisible) hideTranslateLanguageSelector()
        suggestionsStrip.removeAllViews()
        if (DEBUG_SUGGESTIONS) removeAllDebugInfoViews()
        if (!toolbarContainer.isVisible)
            suggestionsStrip.isVisible = true
        dismissMoreSuggestionsPanel()

        disarmDeleteMode()

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

    private fun getLanguageHistory(prefs: SharedPreferences) = helium314.keyboard.latin.utils.TranslationUtils.getLanguageHistory(prefs)

    private fun saveLanguageHistory(prefs: SharedPreferences, name: String, code: String) = helium314.keyboard.latin.utils.TranslationUtils.saveLanguageHistory(prefs, name, code)

    private fun isSameLanguage(p1: Pair<String, String>, p2: Pair<String, String>) = helium314.keyboard.latin.utils.TranslationUtils.isSameLanguage(p1, p2)

    private fun showDialogForIme(builder: android.app.AlertDialog.Builder) {
        val dialog = builder.create()
        val window = dialog.window
        if (window != null) {
            val lp = window.attributes
            lp.token = windowToken
            lp.type = android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            window.attributes = lp
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
        dialog.show()
    }

    fun showTranslateLanguageSelector() {
        // Hide other views
        suggestionsStrip.isVisible = false
        if (!Settings.getValues().mSplitToolbar) {
            toolbarContainer.isVisible = false
            pinnedKeys.isVisible = false
            toolbarExpandKey.isVisible = false
        }

        // Populate language buttons
        val languageList = findViewById<LinearLayout>(R.id.translate_language_list)
        languageList.removeAllViews()

        val languageNames = resources.getStringArray(R.array.translate_language_names)
        val languageCodes = resources.getStringArray(R.array.translate_language_codes)
        val prefs = context.prefs()

        val defaultList = languageNames.zip(languageCodes).toMutableList()
        val rawCode = prefs.getString(SettingsWithoutKey.GEMINI_TARGET_LANGUAGE, "en") ?: "en"
        val currentLanguageCode = if (rawCode.equals("English", ignoreCase = true)) "en" else rawCode
        val currentLanguageName = prefs.getString(Settings.PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE, currentLanguageCode) ?: currentLanguageCode
        
        val history = getLanguageHistory(prefs).toMutableList()
        if (currentLanguageCode.isNotEmpty() && currentLanguageCode != "custom") {
            val currentPair = currentLanguageName to currentLanguageCode
            if (history.none { isSameLanguage(it, currentPair) }) {
                history.add(0, currentPair)
            }
        }

        val list = mutableListOf<Pair<String, String>>()
        for (item in history) {
            if (list.none { isSameLanguage(it, item) }) {
                list.add(item)
            }
        }
        for (item in defaultList) {
            if (list.none { isSameLanguage(it, item) }) {
                list.add(item)
            }
        }

        val removed = helium314.keyboard.latin.utils.TranslationUtils.getRemovedLanguages(prefs)
        val filteredList = list.filter { 
            it.first.lowercase() !in removed && it.second.lowercase() !in removed 
        }

        // Create a button for each language
        for ((languageName, languageCode) in filteredList) {
            val button = android.widget.TextView(context, null, R.attr.suggestionWordStyle).apply {
                text = languageName
                gravity = android.view.Gravity.CENTER
                setPadding(8.dpToPx(resources), 0, 8.dpToPx(resources), 0)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setSingleLine()
                ellipsize = android.text.TextUtils.TruncateAt.END
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
                    putString(SettingsWithoutKey.GEMINI_TARGET_LANGUAGE, languageCode)
                }.apply()
                saveLanguageHistory(context.prefs(), languageName, languageCode)
                helium314.keyboard.latin.utils.ProofreadService(context).setTargetLanguage(languageCode)
                hideTranslateLanguageSelector()
                listener.onCodeInput(KeyCode.TRANSLATE, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false)
            }

            button.setBackgroundResource(R.drawable.toolbar_key_background)
            val colors = Settings.getValues().mColors
            colors.setColor(button.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
            button.setTextColor(colors.get(ColorType.KEY_TEXT))
            languageList.addView(button)
        }

        // Setup close button
        val colors = Settings.getValues().mColors
        translateLanguageCloseButton.setBackgroundResource(R.drawable.toolbar_key_background)
        val closePadding = 9.dpToPx(resources)
        translateLanguageCloseButton.setPadding(closePadding, closePadding, closePadding, closePadding)
        translateLanguageCloseButton.setImageDrawable(KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.CLOSE_HISTORY.name, context))
        colors.setColor(translateLanguageCloseButton, ColorType.TOOL_BAR_EXPAND_KEY)
        colors.setColor(translateLanguageCloseButton.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
        translateLanguageCloseButton.setOnClickListener {
            hideTranslateLanguageSelector()
        }

        // Show the selector
        translateLanguageContainer.isVisible = true
        isTranslateLanguageSelectorVisible = true
    }

    fun hideTranslateLanguageSelector() {
        translateLanguageContainer.isVisible = false

        // Restore normal view
        val settingsValues = Settings.getValues()
        if (!settingsValues.mSplitToolbar) {
            toolbarExpandKey.isVisible = settingsValues.mToolbarMode == ToolbarMode.EXPANDABLE
            setToolbarVisibility(isToolbarManuallyOpen, saveState = false)
        } else {
            val isEmojiView = isShowingEmojiSuggestions || helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().isShowingEmojiPalettes
            toolbarRow?.isVisible = !isEmojiView
            toolbarContainer.isVisible = !isDeviceLocked(context) && !isEmojiView
            toolbar.visibility = if (isEmojiView) GONE else VISIBLE
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

            val isEmojiView = isShowingEmojiSuggestions || helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().isShowingEmojiPalettes
            toolbarRow?.isVisible = !isEmojiView
            toolbarContainer.isVisible = !hideToolbarKeys && !isEmojiView
            toolbar.visibility = if (isEmojiView) GONE else VISIBLE

            updateVoiceKey() // Re-apply voice logic to pinned keys
            layoutHelper.setSuggestionsCountInStrip(5)
            applyToolbarKeyLayoutParams(true)
            toolbarContainer.post { applyToolbarKeyLayoutParams(true) }
        } else {
            toolbarExpandKey.isVisible = toolbarIsExpandable
            // Don't manage visibility here - let setToolbarVisibility handle it
            // This prevents conflicts with auto-hide pinned keys logic
            layoutHelper.setSuggestionsCountInStrip(3)
        }
        
        // ponytail: show/hide dictionary download button if dictionary is missing
        val currentLocale = SubtypeSettings.getSelectedSubtype(context.prefs()).locale()
        val showDownloadButton = Settings.getValues().mShowDownloadButtonInToolbar
        if (showDownloadButton && isMainDictionaryMissing(context, currentLocale) && !hideToolbarKeys) {
            if (dictDownloadButton == null) {
                dictDownloadButton = ImageButton(context, null, R.attr.suggestionWordStyle).apply {
                    scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    val padding = 6.dpToPx(resources)
                    setPadding(padding, padding, padding, padding)
                    setImageResource(R.drawable.ic_dictionary)
                    contentDescription = context.getString(R.string.download)
                    setOnClickListener {
                        val intent = android.content.Intent().apply {
                            setClass(context, helium314.keyboard.settings.SettingsActivity2::class.java)
                            putExtra("screen", "dictionaries")
                            putExtra("from_ime", true)
                            setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                    or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                    or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(intent)
                    }
                }
                val configHeight = resources.getDimension(R.dimen.config_suggestions_strip_height).toInt()
                val rawHeight = toolbarExpandKey.layoutParams.height
                val toolbarHeight = if (rawHeight > 0) min(rawHeight, configHeight) else configHeight
                dictDownloadButton?.layoutParams = LinearLayout.LayoutParams(toolbarHeight, toolbarHeight).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                
                val wrapper = findViewById<LinearLayout>(R.id.suggestions_strip_wrapper)
                val expandIndex = wrapper.indexOfChild(toolbarExpandKey)
                wrapper.addView(dictDownloadButton, expandIndex + 1)
            }
            val colors = Settings.getValues().mColors
            dictDownloadButton?.let { btn ->
                colors.setColor(btn, ColorType.TOOL_BAR_KEY)
                btn.setBackgroundResource(R.drawable.toolbar_key_background)
                btn.background?.let { bg -> colors.setColor(bg, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND) }
                btn.isVisible = true
            }
        } else {
            dictDownloadButton?.isVisible = false
        }

        isExternalSuggestionVisible = false
    }

    private fun setupKey(view: ImageButton, colors: Colors) {
        val tag = view.tag
        if (tag is ToolbarKey && isRepeatableToolbarKey(tag)) {
            view.setOnTouchListener(RepeatableKeyTouchListener { repeatCount ->
                if (repeatCount == 0 || repeatCount % 4 == 0) {
                    AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, view, HapticEvent.KEY_PRESS)
                }
                val code = getCodeForToolbarKey(tag)
                if (code != KeyCode.UNSPECIFIED) {
                    listener.onCodeInput(code, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, repeatCount > 0)
                }
            })
        } else {
            view.setOnClickListener(this)
            view.setOnLongClickListener(this)
        }
        setToolbarButtonActivatedState(view)
    }

    private fun rebuildToolbarKeys() {
        KeyboardIconsSet.instance.loadIcons(context)
        toolbar.removeAllViews()
        pinnedKeys.removeAllViews()

        val colors = Settings.getValues().mColors
        val pinnedKeysList = getPinnedToolbarKeys(context.prefs())
        val mToolbarMode = Settings.getValues().mToolbarMode
        val isSplitToolbar = Settings.getValues().mSplitToolbar
        
        // Toolbar keys setup
        // Always populate toolbar keys if mode allows, visibility handled in updateKeys
        if (mToolbarMode == ToolbarMode.TOOLBAR_KEYS || mToolbarMode == ToolbarMode.EXPANDABLE) {
            // In split mode, show ALL enabled keys in the toolbar, ignoring pin status
            // When autoHidePinnedKeys=false: include pinned keys in toolbar too (they show side-by-side)
            // When autoHidePinnedKeys=true: exclude pinned keys from toolbar (pinnedKeys container handles them)
            val keysToRender = if (isSplitToolbar || !Settings.getValues().mAutoHidePinnedKeys) {
                getEnabledToolbarKeys(context.prefs())
            } else {
                getEnabledToolbarKeys(context.prefs()).filterNot { it in pinnedKeysList }
            }
            for (key in keysToRender) {                val button = createToolbarKey(context, key)
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
        applyToolbarKeyLayoutParams(toolbarContainer.isVisible)
        toolbarContainer.post { applyToolbarKeyLayoutParams(toolbarContainer.isVisible) }
    }

    private fun applyToolbarKeyLayoutParams(isExpanded: Boolean) {
        val count = toolbar.childCount
        if (count == 0) return
        val singleKeyWidth = keyDimension

        val visibleCount = (0 until count).count {
            val child = toolbar.getChildAt(it)
            child != null && child.visibility != View.GONE
        }
        if (visibleCount == 0) return

        val isSplit = Settings.getValues().mSplitToolbar
        val hasExpandKey = Settings.getValues().mToolbarMode == ToolbarMode.EXPANDABLE && !isSplit
        val expandKeyWidth = if (hasExpandKey) {
            if (toolbarExpandKey.width > 0) toolbarExpandKey.width else keyDimension
        } else 0

        val pinnedCount = if (!Settings.getValues().mAutoHidePinnedKeys && !isSplit) {
            getPinnedToolbarKeys(context.prefs()).size
        } else 0
        val pinnedWidth = if (pinnedKeys.width > 0) pinnedKeys.width else (pinnedCount * keyDimension)

        val keyboardWidth = ResourceUtils.getKeyboardWidth(context, Settings.getValues())
        val currentStripWidth = (if (width > 0) width else measuredWidth).takeIf { it > 0 } ?: keyboardWidth
        val fallbackAvailableWidth = (currentStripWidth - expandKeyWidth - pinnedWidth).coerceAtLeast(0)

        val containerWidth = toolbarContainer.width.takeIf { it > 0 }
            ?: toolbarContainer.measuredWidth.takeIf { it > 0 }
            ?: fallbackAvailableWidth

        val isAutoSpan = Settings.getValues().mAutoSpanToolbarKeys
        val isToolbarVisible = toolbarContainer.isVisible && (isExpanded || isSplit)
        val minSpannedKeyWidth = (singleKeyWidth * 1.25f).toInt()
        val canSpan = containerWidth > 0 && (containerWidth / visibleCount >= minSpannedKeyWidth)
        val useEqualSpacing = isAutoSpan && isToolbarVisible && canSpan

        val alignmentGravity = when (Settings.getValues().mToolbarKeysAlignment) {
            "left" -> Gravity.START or Gravity.CENTER_VERTICAL
            "center" -> Gravity.CENTER
            else -> Gravity.END or Gravity.CENTER_VERTICAL
        }
        (toolbar as? LinearLayout)?.gravity = if (useEqualSpacing) Gravity.NO_GRAVITY else alignmentGravity

        val spannedLayoutParams = LinearLayout.LayoutParams(0, singleKeyWidth, 1f).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        for (i in 0 until count) {
            val child = toolbar.getChildAt(i) ?: continue
            if (child.visibility == View.GONE) continue
            child.layoutParams = if (useEqualSpacing) {
                spannedLayoutParams
            } else {
                toolbarKeyLayoutParams
            }
        }
    }

    fun onFloatingKeyboardScaleChanged() {
        val toolbarHeight = keyDimension
        toolbarExpandKey.layoutParams.height = toolbarHeight
        toolbarExpandKey.layoutParams.width = toolbarHeight
        val defaultStripHeight = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height).toFloat()
        val stripHeight = ResourceUtils.getSuggestionsStripHeight(resources).toFloat()
        val effectiveScale = if (defaultStripHeight > 0f) stripHeight / defaultStripHeight else 1.0f
        val expandPadding = (9 * effectiveScale).toInt().dpToPx(resources).coerceAtLeast(2)
        toolbarExpandKey.setPadding(expandPadding, expandPadding, expandPadding, expandPadding)

        rebuildToolbarKeys()
        requestLayout()
        invalidate()
    }

    fun updateSplitToolbarState() {
        if (!Settings.getValues().mSplitToolbar) return
        val isEmojiView = isShowingEmojiSuggestions || helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().isShowingEmojiPalettes
        if (isEmojiView) {
            toolbarRow?.isVisible = false
            toolbarContainer.isVisible = false
            suggestionsStrip.isVisible = true
            return
        }
        toolbarRow?.isVisible = true
        toolbarContainer.isVisible = !isDeviceLocked(context)
        // Clipboard/screenshot suggestions are LinearLayout roots, not TextViews —
        // skip placeholder logic entirely so the external view is not obscured.
        if (isExternalSuggestionVisible) {
            suggestionsStrip.isVisible = true
            return
        }
        suggestionsStrip.isVisible = true
        
        // ponytail: no fallback suggestions to keep it clean and minimal
        val PLACEHOLDER_TAG = "PLACEHOLDER_VIEW"
        val placeholder = suggestionsStrip.findViewWithTag<View>(PLACEHOLDER_TAG)
        if (placeholder != null) {
            suggestionsStrip.removeView(placeholder)
        }
    }

    private var isShowingEmojiSuggestions = false

    /**
     * Populates the suggestion strip with emoji items (used in split toolbar mode).
     * @param emojis List of emoji strings to display
     * @param onEmojiClick Callback when an emoji is tapped
     */
    fun setEmojiSuggestions(emojis: List<String>, onEmojiClick: java.util.function.Consumer<String>) {
        if (isVoiceActive) return
        if (!Settings.getValues().mSplitToolbar) return
        isShowingEmojiSuggestions = true
        suggestionsStrip.removeAllViews()

        val colors = Settings.getValues().mColors
        val customTypeface = Settings.getInstance().customEmojiTypeface
        val stripHeight = ResourceUtils.getSuggestionsStripHeight(resources)

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
        updateSplitToolbarState()
    }

    /**
     * ponytail: Shows a download button in the suggestion strip (used in split toolbar mode).
     * @param onClick Callback when the download button is tapped
     * @param isDownloading Whether the dictionary is currently downloading
     */
    fun setEmojiDownloadButton(onClick: java.lang.Runnable, isDownloading: Boolean) {
        if (isVoiceActive) return
        if (!Settings.getValues().mSplitToolbar) return
        isShowingEmojiSuggestions = true
        suggestionsStrip.removeAllViews()

        val btn = android.widget.Button(context)
        btn.text = if (isDownloading) "Downloading..." else "Download Dictionary"
        btn.textSize = 12f
        btn.isAllCaps = false
        btn.isEnabled = !isDownloading
        btn.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER
        }
        btn.setOnClickListener {
            onClick.run()
        }

        // Wrap button in a container that properly constrains its height
        val container = LinearLayout(context)
        container.orientation = LinearLayout.HORIZONTAL
        container.gravity = android.view.Gravity.CENTER
        container.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        container.addView(btn)

        suggestionsStrip.addView(container)
        suggestionsStrip.isVisible = true
        updateSplitToolbarState()
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
        var DEBUG_SUGGESTIONS = false
        private const val DEBUG_INFO_TEXT_SIZE_IN_DIP = 6.5f
        private val TAG = SuggestionStripView::class.java.simpleName
    }
}
