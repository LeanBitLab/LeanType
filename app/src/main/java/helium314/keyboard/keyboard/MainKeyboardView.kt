package helium314.keyboard.keyboard

import android.animation.AnimatorInflater
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import helium314.keyboard.accessibility.AccessibilityUtils
import helium314.keyboard.accessibility.MainKeyboardAccessibilityDelegate
import helium314.keyboard.compat.locale
import helium314.keyboard.keyboard.internal.*
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.FloatingKeyboardManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.CoordinateUtils
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.handwriting.HandwritingLoader
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.*
import java.util.Locale
import java.util.WeakHashMap

class MainKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = R.attr.mainKeyboardViewStyle
) : KeyboardView(context, attrs, defStyle), DrawingProxy, PopupKeysPanel.Controller {

    private var mKeyboardActionListener: KeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER
    private var mSpaceKey: Key? = null
    private val mLanguageOnSpacebarFinalAlpha: Int
    private val mLanguageOnSpacebarFadeoutAnimator: ObjectAnimator?
    private var mLanguageOnSpacebarFormatType = 0
    private var mHasMultipleEnabledIMEsOrSubtypes = false
    private var mLanguageOnSpacebarAnimAlpha = Constants.Color.ALPHA_OPAQUE
    private val mLanguageOnSpacebarTextRatio: Float
    private var mLanguageOnSpacebarTextSize = 0f
    private val mLanguageOnSpacebarTextColor: Int
    private val mLanguageOnSpacebarTextShadowRadius: Float
    private val mLanguageOnSpacebarTextShadowColor: Int
    private val mIncognitoIcon: Drawable?

    private val mAltCodeKeyWhileTypingFadeoutAnimator: ObjectAnimator?
    private val mAltCodeKeyWhileTypingFadeinAnimator: ObjectAnimator?

    private val mDrawingPreviewPlacerView: DrawingPreviewPlacerView
    private val mOriginCoords = CoordinateUtils.newInstance()
    private val mGestureFloatingTextDrawingPreview: GestureFloatingTextDrawingPreview
    private val mGestureTrailsDrawingPreview: GestureTrailsDrawingPreview
    private val mSlidingKeyInputDrawingPreview: SlidingKeyInputDrawingPreview

    private val mKeyPreviewDrawParams: KeyPreviewDrawParams
    private val mKeyPreviewChoreographer: KeyPreviewChoreographer

    private val mPopupKeysKeyboardContainer: View
    private val mPopupKeysKeyboardForActionContainer: View
    private val mPopupKeysKeyboardCache = WeakHashMap<Key, Keyboard>()
    private val mConfigShowPopupKeysKeyboardAtTouchedPoint: Boolean
    private var mPopupKeysPanel: PopupKeysPanel? = null

    private val mGestureFloatingPreviewTextLingerTimeout: Int
    private val mKeyDetector: KeyDetector
    private val mNonDistinctMultitouchHelper: NonDistinctMultitouchHelper?
    private val mTimerHandler: TimerHandler
    private val mLanguageOnSpacebarHorizontalMargin: Int
    private var mAccessibilityDelegate: MainKeyboardAccessibilityDelegate? = null

    init {
        val drawingPreviewPlacerView = DrawingPreviewPlacerView(ContextThemeWrapper(context, R.style.platformActivityTheme), attrs)
        val mainKeyboardViewAttr = context.obtainStyledAttributes(attrs, R.styleable.MainKeyboardView, defStyle, R.style.MainKeyboardView)
        
        mTimerHandler = TimerHandler(this, mainKeyboardViewAttr.getInt(R.styleable.MainKeyboardView_ignoreAltCodeKeyTimeout, 0), mainKeyboardViewAttr.getInt(R.styleable.MainKeyboardView_gestureRecognitionUpdateTime, 0))
        mKeyDetector = KeyDetector(mainKeyboardViewAttr.getDimension(R.styleable.MainKeyboardView_keyHysteresisDistance, 0.0f), mainKeyboardViewAttr.getDimension(R.styleable.MainKeyboardView_keyHysteresisDistanceForSlidingModifier, 0.0f))
        
        PointerTracker.init(mainKeyboardViewAttr, mTimerHandler, this)
        
        val prefs = context.prefs()
        val forceNonDistinctMultitouch = prefs.getBoolean(DebugSettings.PREF_FORCE_NON_DISTINCT_MULTITOUCH, Defaults.PREF_FORCE_NON_DISTINCT_MULTITOUCH)
        val hasDistinctMultitouch = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT) && !forceNonDistinctMultitouch
        mNonDistinctMultitouchHelper = if (hasDistinctMultitouch) null else NonDistinctMultitouchHelper()

        mLanguageOnSpacebarTextRatio = mainKeyboardViewAttr.getFraction(R.styleable.MainKeyboardView_languageOnSpacebarTextRatio, 1, 1, 1.0f) * Settings.getValues().mFontSizeMultiplier
        val colors = Settings.getValues().mColors
        mLanguageOnSpacebarTextColor = colors.get(ColorType.SPACE_BAR_TEXT)
        mLanguageOnSpacebarTextShadowRadius = mainKeyboardViewAttr.getFloat(R.styleable.MainKeyboardView_languageOnSpacebarTextShadowRadius, -1.0f)
        mLanguageOnSpacebarTextShadowColor = mainKeyboardViewAttr.getColor(R.styleable.MainKeyboardView_languageOnSpacebarTextShadowColor, 0)
        mLanguageOnSpacebarFinalAlpha = Color.alpha(mLanguageOnSpacebarTextColor)
        
        mKeyPreviewDrawParams = KeyPreviewDrawParams(mainKeyboardViewAttr)
        mKeyPreviewChoreographer = KeyPreviewChoreographer(mKeyPreviewDrawParams)

        val popupKeysKeyboardLayoutId = mainKeyboardViewAttr.getResourceId(R.styleable.MainKeyboardView_popupKeysKeyboardLayout, 0)
        val popupKeysKeyboardForActionLayoutId = mainKeyboardViewAttr.getResourceId(R.styleable.MainKeyboardView_popupKeysKeyboardForActionLayout, popupKeysKeyboardLayoutId)
        mConfigShowPopupKeysKeyboardAtTouchedPoint = mainKeyboardViewAttr.getBoolean(R.styleable.MainKeyboardView_showPopupKeysKeyboardAtTouchedPoint, false)

        mGestureFloatingPreviewTextLingerTimeout = Settings.getValues().mGestureTrailFadeoutDuration / 4

        mGestureFloatingTextDrawingPreview = GestureFloatingTextDrawingPreview(mainKeyboardViewAttr).apply { setDrawingView(drawingPreviewPlacerView) }
        mGestureTrailsDrawingPreview = GestureTrailsDrawingPreview(mainKeyboardViewAttr).apply { setDrawingView(drawingPreviewPlacerView) }
        mSlidingKeyInputDrawingPreview = SlidingKeyInputDrawingPreview(mainKeyboardViewAttr).apply { setDrawingView(drawingPreviewPlacerView) }

        val languageOnSpacebarFadeoutAnimatorResId = mainKeyboardViewAttr.getResourceId(R.styleable.MainKeyboardView_languageOnSpacebarFadeoutAnimator, 0)
        val altCodeKeyWhileTypingFadeoutAnimatorResId = mainKeyboardViewAttr.getResourceId(R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeoutAnimator, 0)
        val altCodeKeyWhileTypingFadeinAnimatorResId = mainKeyboardViewAttr.getResourceId(R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeinAnimator, 0)
        mainKeyboardViewAttr.recycle()

        mDrawingPreviewPlacerView = drawingPreviewPlacerView
        val inflater = LayoutInflater.from(context)
        mPopupKeysKeyboardContainer = inflater.inflate(popupKeysKeyboardLayoutId, null)
        mPopupKeysKeyboardForActionContainer = inflater.inflate(popupKeysKeyboardForActionLayoutId, null)
        
        mLanguageOnSpacebarFadeoutAnimator = loadObjectAnimator(languageOnSpacebarFadeoutAnimatorResId, this)?.apply { setIntValues(255, mLanguageOnSpacebarFinalAlpha) }
        mAltCodeKeyWhileTypingFadeoutAnimator = loadObjectAnimator(altCodeKeyWhileTypingFadeoutAnimatorResId, this)
        mAltCodeKeyWhileTypingFadeinAnimator = loadObjectAnimator(altCodeKeyWhileTypingFadeinAnimatorResId, this)

        mLanguageOnSpacebarHorizontalMargin = resources.getDimension(R.dimen.config_language_on_spacebar_horizontal_margin).toInt()
        mIncognitoIcon = KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.INCOGNITO.name, context)?.apply { colors.setColor(this, ColorType.SPACE_BAR_TEXT) }
    }

    override fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        super.setHardwareAcceleratedDrawingEnabled(enabled)
        mDrawingPreviewPlacerView.setHardwareAcceleratedDrawingEnabled(enabled)
    }

    private fun loadObjectAnimator(resId: Int, target: Any): ObjectAnimator? {
        if (resId == 0) return null
        val animator = AnimatorInflater.loadAnimator(context, resId) as? ObjectAnimator
        animator?.setTarget(target)
        return animator
    }

    override fun startWhileTypingAnimation(fadeInOrOut: Int) {
        when (fadeInOrOut) {
            DrawingProxy.FADE_IN -> cancelAndStartAnimators(mAltCodeKeyWhileTypingFadeoutAnimator, mAltCodeKeyWhileTypingFadeinAnimator)
            DrawingProxy.FADE_OUT -> cancelAndStartAnimators(mAltCodeKeyWhileTypingFadeinAnimator, mAltCodeKeyWhileTypingFadeoutAnimator)
        }
    }

    fun setLanguageOnSpacebarAnimAlpha(alpha: Int) { mLanguageOnSpacebarAnimAlpha = alpha; invalidateKey(mSpaceKey) }
    fun setKeyboardActionListener(listener: KeyboardActionListener?) {
        val nonNullListener = listener ?: KeyboardActionListener.EMPTY_LISTENER
        mKeyboardActionListener = nonNullListener
        PointerTracker.setKeyboardActionListener(nonNullListener)
    }
    fun getKeyX(x: Int): Int = if (Constants.isValidCoordinate(x)) mKeyDetector.getTouchX(x) else x
    fun getKeyY(y: Int): Int = if (Constants.isValidCoordinate(y)) mKeyDetector.getTouchY(y) else y

    override fun setKeyboard(keyboard: Keyboard) {
        mTimerHandler.cancelLongPressTimers()
        super.setKeyboard(keyboard)
        mKeyDetector.setKeyboard(keyboard, -paddingLeft.toFloat(), -paddingTop.toFloat() + verticalCorrection)
        PointerTracker.setKeyDetector(mKeyDetector)
        mPopupKeysKeyboardCache.clear()
        mSpaceKey = keyboard.getKey(Constants.CODE_SPACE)
        mLanguageOnSpacebarTextSize = (keyboard.mMostCommonKeyHeight - keyboard.mVerticalGap) * mLanguageOnSpacebarTextRatio

        if (AccessibilityUtils.instance.isAccessibilityEnabled) {
            if (mAccessibilityDelegate == null) mAccessibilityDelegate = MainKeyboardAccessibilityDelegate(this, mKeyDetector)
            mAccessibilityDelegate?.keyboard = keyboard
        } else {
            mAccessibilityDelegate = null
        }
    }

    fun setKeyPreviewPopupEnabled(previewEnabled: Boolean) { mKeyPreviewDrawParams.setPopupEnabled(previewEnabled) }
    
    private fun locatePreviewPlacerView() {
        getLocationInWindow(mOriginCoords)
        mDrawingPreviewPlacerView.setKeyboardViewGeometry(mOriginCoords, width, height)
    }

    private fun installPreviewPlacerView() {
        val floatingManager = KeyboardSwitcher.getInstance().floatingKeyboardManager
        if (floatingManager != null && floatingManager.isFloating) {
            val overlayRoot = floatingManager.overlayRoot
            if (overlayRoot != null) {
                (mDrawingPreviewPlacerView.parent as? ViewGroup)?.removeView(mDrawingPreviewPlacerView)
                overlayRoot.addView(mDrawingPreviewPlacerView)
                return
            }
        }
        val rootView = rootView ?: return
        val windowContentView = rootView.findViewById<ViewGroup>(android.R.id.content) ?: return
        windowContentView.addView(mDrawingPreviewPlacerView)
    }

    override fun onKeyPressed(key: Key, withPreview: Boolean) {
        key.onPressed()
        invalidateKey(key)
        val kb = keyboard ?: return
        mKeyPreviewDrawParams.setVisibleOffset(-kb.mVerticalGap)
        if (withPreview && key.hasPreview() && mKeyPreviewDrawParams.isPopupEnabled()) showKeyPreview(key)
    }

    private fun showKeyPreview(key: Key) {
        val kbd = keyboard ?: return
        locatePreviewPlacerView()
        getLocationInWindow(mOriginCoords)
        val fullWidth = KeyboardSwitcher.getInstance().wrapperView?.width ?: width
        mKeyPreviewChoreographer.placeAndShowKeyPreview(key, kbd.mIconsSet, keyDrawParams, fullWidth, mOriginCoords, mDrawingPreviewPlacerView)
    }

    private fun dismissKeyPreviewWithoutDelay(key: Key) { mKeyPreviewChoreographer.dismissKeyPreview(key); invalidateKey(key) }

    override fun onKeyReleased(key: Key, withAnimation: Boolean) {
        key.onReleased()
        invalidateKey(key)
        if (key.hasPreview()) {
            if (withAnimation) dismissKeyPreview(key) else dismissKeyPreviewWithoutDelay(key)
        }
    }

    private fun dismissKeyPreview(key: Key) {
        if (isHardwareAccelerated) mKeyPreviewChoreographer.dismissKeyPreview(key) else dismissKeyPreviewWithoutDelay(key)
    }

    fun setSlidingKeyInputPreviewEnabled(enabled: Boolean) { mSlidingKeyInputDrawingPreview.setPreviewEnabled(enabled) }

    override fun showSlidingKeyInputPreview(tracker: PointerTracker?) {
        locatePreviewPlacerView()
        if (tracker != null) mSlidingKeyInputDrawingPreview.setPreviewPosition(tracker) else mSlidingKeyInputDrawingPreview.dismissSlidingKeyInputPreview()
    }

    private fun setGesturePreviewMode(isGestureTrailEnabled: Boolean, isGestureFloatingPreviewTextEnabled: Boolean) {
        mGestureFloatingTextDrawingPreview.setPreviewEnabled(isGestureFloatingPreviewTextEnabled)
        mGestureTrailsDrawingPreview.setPreviewEnabled(isGestureTrailEnabled)
    }

    fun showGestureFloatingPreviewText(suggestedWords: SuggestedWords, dismissDelayed: Boolean) {
        locatePreviewPlacerView()
        mGestureFloatingTextDrawingPreview.setSuggestedWords(suggestedWords)
        if (dismissDelayed) mTimerHandler.postDismissGestureFloatingPreviewText(mGestureFloatingPreviewTextLingerTimeout.toLong())
    }

    override fun dismissGestureFloatingPreviewTextWithoutDelay() { mGestureFloatingTextDrawingPreview.dismissGestureFloatingPreviewText() }

    override fun showGestureTrail(tracker: PointerTracker, showsFloatingPreviewText: Boolean) {
        locatePreviewPlacerView()
        if (showsFloatingPreviewText) mGestureFloatingTextDrawingPreview.setPreviewPosition(tracker)
        mGestureTrailsDrawingPreview.setPreviewPosition(tracker)
    }

    fun setMainDictionaryAvailability(mainDictionaryAvailable: Boolean) { PointerTracker.setMainDictionaryAvailability(mainDictionaryAvailable) }

    fun setGestureHandlingEnabledByUser(isGestureHandlingEnabledByUser: Boolean, isGestureTrailEnabled: Boolean, isGestureFloatingPreviewTextEnabled: Boolean) {
        PointerTracker.setGestureHandlingEnabledByUser(isGestureHandlingEnabledByUser)
        setGesturePreviewMode(isGestureHandlingEnabledByUser && isGestureTrailEnabled, isGestureHandlingEnabledByUser && isGestureFloatingPreviewTextEnabled)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); installPreviewPlacerView() }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); mDrawingPreviewPlacerView.removeAllViews() }

    override fun showPopupKeysKeyboard(key: Key, tracker: PointerTracker): PopupKeysPanel? {
        val popupKeys = key.popupKeys ?: return null
        val kbd = keyboard ?: return null
        var popupKeysKeyboard = mPopupKeysKeyboardCache[key]
        if (popupKeysKeyboard == null) {
            val isSinglePopupKeyWithPreview = mKeyPreviewDrawParams.isPopupEnabled() && key.hasPreview() && popupKeys.size == 1 && mKeyPreviewDrawParams.getVisibleWidth() > 0
            val builder = PopupKeysKeyboard.Builder(context, key, kbd, isSinglePopupKeyWithPreview, mKeyPreviewDrawParams.getVisibleWidth(), mKeyPreviewDrawParams.getVisibleHeight(), newLabelPaint(key))
            popupKeysKeyboard = builder.build()
            mPopupKeysKeyboardCache[key] = popupKeysKeyboard
        }

        val container = if (key.hasActionKeyPopups()) mPopupKeysKeyboardForActionContainer else mPopupKeysKeyboardContainer
        val popupKeysKeyboardView = container.findViewById<PopupKeysKeyboardView>(R.id.popup_keys_keyboard_view)
        popupKeysKeyboardView.setKeyboard(popupKeysKeyboard)
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val lastCoords = CoordinateUtils.newInstance()
        tracker.getLastCoordinates(lastCoords)
        val keyPreviewEnabled = mKeyPreviewDrawParams.isPopupEnabled() && key.hasPreview()
        val pointX = if (mConfigShowPopupKeysKeyboardAtTouchedPoint && !keyPreviewEnabled) CoordinateUtils.x(lastCoords) else key.x + key.width / 2
        val pointY = key.y + mKeyPreviewDrawParams.getVisibleOffset()
        popupKeysKeyboardView.showPopupKeysPanel(this, this, pointX, pointY, mKeyboardActionListener)
        return popupKeysKeyboardView
    }

    fun isInDraggingFinger(): Boolean = isShowingPopupKeysPanel() || PointerTracker.isAnyInDraggingFinger()

    override fun onShowPopupKeysPanel(panel: PopupKeysPanel) {
        locatePreviewPlacerView()
        onDismissPopupKeysPanel()
        PointerTracker.setReleasedKeyGraphicsToAllKeys()
        mSlidingKeyInputDrawingPreview.dismissSlidingKeyInputPreview()
        panel.showInParent(mDrawingPreviewPlacerView)
        mPopupKeysPanel = panel
    }

    fun isShowingPopupKeysPanel(): Boolean = mPopupKeysPanel?.isShowingInParent == true
    override fun onCancelPopupKeysPanel() { PointerTracker.dismissAllPopupKeysPanels() }
    override fun onDismissPopupKeysPanel() { if (isShowingPopupKeysPanel()) { mPopupKeysPanel?.removeFromParent(); mPopupKeysPanel = null } }

    fun startDoubleTapShiftKeyTimer() { mTimerHandler.startDoubleTapShiftKeyTimer() }
    fun cancelDoubleTapShiftKeyTimer() { mTimerHandler.cancelDoubleTapShiftKeyTimer() }
    fun isInDoubleTapShiftKeyTimeout(): Boolean = mTimerHandler.isInDoubleTapShiftKeyTimeout()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (keyboard == null) return false
        if (mNonDistinctMultitouchHelper != null) {
            if (event.pointerCount > 1 && mTimerHandler.isInKeyRepeat()) mTimerHandler.cancelKeyRepeatTimers()
            mNonDistinctMultitouchHelper.processMotionEvent(event, mKeyDetector)
            return true
        }
        return processMotionEvent(event)
    }

    fun processMotionEvent(event: MotionEvent): Boolean {
        val index = event.actionIndex
        val id = event.getPointerId(index)
        val tracker = PointerTracker.getPointerTracker(id)
        if (isShowingPopupKeysPanel() && !tracker.isShowingPopupKeysPanel() && PointerTracker.getActivePointerTrackerCount() == 1) return true
        tracker.processMotionEvent(event, mKeyDetector)
        return true
    }

    fun dismissAllKeyPreviews() { mKeyPreviewChoreographer.clear(); mDrawingPreviewPlacerView.removeAllViews() }
    fun cancelAllOngoingEvents() {
        mTimerHandler.cancelAllMessages()
        PointerTracker.setReleasedKeyGraphicsToAllKeys()
        mGestureFloatingTextDrawingPreview.dismissGestureFloatingPreviewText()
        mSlidingKeyInputDrawingPreview.dismissSlidingKeyInputPreview()
        PointerTracker.dismissAllPopupKeysPanels()
        dismissAllKeyPreviews()
        PointerTracker.cancelAllPointerTrackers()
    }

    fun closing() { cancelAllOngoingEvents(); mPopupKeysKeyboardCache.clear() }

    fun onHideWindow() {
        onDismissPopupKeysPanel()
        mAccessibilityDelegate?.let { if (AccessibilityUtils.instance.isAccessibilityEnabled) it.onHideWindow() }
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        mAccessibilityDelegate?.let { if (AccessibilityUtils.instance.isTouchExplorationEnabled) return it.onHoverEvent(event) }
        return super.onHoverEvent(event)
    }

    fun updateShortcutKey(available: Boolean) {
        val kb = keyboard ?: return
        kb.getKey(KeyCode.VOICE_INPUT)?.let { it.isEnabled = available; invalidateKey(it) }
    }

    fun updateLockState(keyCode: Int, locked: Boolean) {
        val kb = keyboard ?: return
        kb.getKey(keyCode)?.let { it.isLocked = locked; invalidateKey(it) }
    }

    fun startDisplayLanguageOnSpacebar(subtypeChanged: Boolean, languageOnSpacebarFormatType: Int, hasMultipleEnabledIMEsOrSubtypes: Boolean) {
        if (subtypeChanged) KeyPreviewView.clearTextCache()
        mLanguageOnSpacebarFormatType = languageOnSpacebarFormatType
        mHasMultipleEnabledIMEsOrSubtypes = hasMultipleEnabledIMEsOrSubtypes
        val animator = mLanguageOnSpacebarFadeoutAnimator
        if (animator == null) {
            mLanguageOnSpacebarFormatType = LanguageOnSpacebarUtils.FORMAT_TYPE_NONE
        } else {
            if (subtypeChanged && languageOnSpacebarFormatType != LanguageOnSpacebarUtils.FORMAT_TYPE_NONE) {
                setLanguageOnSpacebarAnimAlpha(Constants.Color.ALPHA_OPAQUE)
                if (animator.isStarted) animator.cancel()
                animator.start()
            } else {
                if (!animator.isStarted) mLanguageOnSpacebarAnimAlpha = mLanguageOnSpacebarFinalAlpha
            }
        }
        invalidateKey(mSpaceKey)
    }

    override fun onDrawKeyTopVisuals(key: Key, canvas: Canvas, paint: Paint, params: KeyDrawParams) {
        if (key.altCodeWhileTyping() && key.isEnabled) params.mAnimAlpha = Constants.Color.ALPHA_OPAQUE
        super.onDrawKeyTopVisuals(key, canvas, paint, params)
        if (key.code == Constants.CODE_SPACE) {
            if (Settings.getValues().mIncognitoModeEnabled && mIncognitoIcon != null) drawIncognitoOnSpacebar(key, canvas)
            if (mLanguageOnSpacebarFormatType != LanguageOnSpacebarUtils.FORMAT_TYPE_NONE) drawLanguageOnSpacebar(key, canvas, paint)
            if (key.isLongPressEnabled && mHasMultipleEnabledIMEsOrSubtypes) drawKeyPopupHint(key, canvas, paint, params)
        } else if (key.code == KeyCode.LANGUAGE_SWITCH) {
            drawKeyPopupHint(key, canvas, paint, params)
        }
    }

    private fun fitsTextIntoWidth(width: Int, text: String, paint: Paint): Boolean {
        val maxTextWidth = width - mLanguageOnSpacebarHorizontalMargin * 2
        paint.textScaleX = 1.0f
        val textWidth = TypefaceUtils.getStringWidth(text, paint)
        if (textWidth < width) return true
        val scaleX = maxTextWidth / textWidth
        if (scaleX < 0.8f) return false
        paint.textScaleX = scaleX
        return TypefaceUtils.getStringWidth(text, paint) < maxTextWidth
    }

    private fun layoutLanguageOnSpacebar(paint: Paint, subtype: RichInputMethodSubtype, width: Int): String {
        if (KeyboardSwitcher.getInstance().isHandwritingShowing) {
            val hwName = HandwritingLoader.getEffectiveDisplayName(context, subtype.locale.toLanguageTag())
            if (fitsTextIntoWidth(width, hwName, paint)) return hwName
        }
        val secondaryLocales = Settings.getValues().mSecondaryLocales
        val secondaryLocalesToUse = withoutDuplicateLanguages(secondaryLocales, subtype.locale.language)
        if (secondaryLocalesToUse.isNotEmpty()) {
            val sb = StringBuilder(subtype.middleDisplayName)
            val displayLocale = resources.configuration.locale()
            for (locale in secondaryLocales) { sb.append(" - "); sb.append(locale.getDisplayLanguage(displayLocale)) }
            val full = sb.toString()
            if (fitsTextIntoWidth(width, full, paint)) return full
            sb.setLength(0)
            sb.append(subtype.locale.language.uppercase(displayLocale))
            for (locale in secondaryLocales) { sb.append(" - "); sb.append(locale.language.uppercase(displayLocale)) }
            val middle = sb.toString()
            if (fitsTextIntoWidth(width, middle, paint)) return middle
        }
        if (mLanguageOnSpacebarFormatType == LanguageOnSpacebarUtils.FORMAT_TYPE_FULL_LOCALE) {
            val fullText = subtype.fullDisplayName
            if (fitsTextIntoWidth(width, fullText, paint)) return fullText
        }
        val middleText = subtype.middleDisplayName
        if (fitsTextIntoWidth(width, middleText, paint)) return middleText
        return ""
    }

    private fun withoutDuplicateLanguages(locales: List<Locale>, mainLanguage: String): List<Locale> {
        val languages = ArrayList<String>().apply { add(mainLanguage) }
        val newLocales = ArrayList<Locale>()
        for (locale in locales) {
            var keep = true
            for (language in languages) if (locale.language == language) keep = false
            if (!keep) continue
            languages.add(locale.language)
            newLocales.add(locale)
        }
        return newLocales
    }

    private fun drawLanguageOnSpacebar(key: Key, canvas: Canvas, paint: Paint) {
        val kb = keyboard ?: return
        val width = key.width; val height = key.height
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = mTypeface ?: Typeface.DEFAULT
        paint.textSize = mLanguageOnSpacebarTextSize
        val customText = Settings.getValues().mSpaceBarText
        val spaceText = if (customText.isNotEmpty()) customText else if (DebugFlags.DEBUG_ENABLED) (KeyboardSwitcher.getInstance().localeAndConfidenceInfo ?: layoutLanguageOnSpacebar(paint, kb.mId.mSubtype, width)) else layoutLanguageOnSpacebar(paint, kb.mId.mSubtype, width)
        
        val descent = paint.descent()
        val textHeight = -paint.ascent() + descent
        val baseline = height / 2f + textHeight / 2
        if (mLanguageOnSpacebarTextShadowRadius > 0.0f) paint.setShadowLayer(mLanguageOnSpacebarTextShadowRadius, 0f, 0f, mLanguageOnSpacebarTextShadowColor) else paint.clearShadowLayer()
        paint.color = mLanguageOnSpacebarTextColor
        paint.alpha = mLanguageOnSpacebarAnimAlpha
        if (!fitsTextIntoWidth(width, spaceText, paint)) {
            val textWidth = TypefaceUtils.getStringWidth(spaceText, paint)
            paint.textScaleX = (width - mLanguageOnSpacebarHorizontalMargin * 2) / textWidth
        }
        canvas.drawText(spaceText, width / 2f, baseline - descent, paint)
        paint.clearShadowLayer()
        paint.textScaleX = 1.0f
    }

    private fun drawIncognitoOnSpacebar(key: Key, canvas: Canvas) {
        val width = key.width; val height = key.height
        val iconSize = (height * 0.8f).toInt()
        val iconY = (height - iconSize) / 2
        var iconX = (width - iconSize) / 2
        if (mLanguageOnSpacebarFormatType != LanguageOnSpacebarUtils.FORMAT_TYPE_NONE) iconX = width - iconSize - (width * 0.05f).toInt()
        mIncognitoIcon?.alpha = 38
        mIncognitoIcon?.setBounds(iconX, iconY, iconX + iconSize, iconY + iconSize)
        mIncognitoIcon?.draw(canvas)
        mIncognitoIcon?.alpha = 255
    }

    override fun deallocateMemory() { super.deallocateMemory(); mDrawingPreviewPlacerView.deallocateMemory() }

    companion object {
        private fun cancelAndStartAnimators(animatorToCancel: ObjectAnimator?, animatorToStart: ObjectAnimator?) {
            if (animatorToCancel == null || animatorToStart == null) return
            var startFraction = 0.0f
            if (animatorToCancel.isStarted) {
                animatorToCancel.cancel()
                startFraction = 1.0f - animatorToCancel.animatedFraction
            }
            val startTime = (animatorToStart.duration * startFraction).toLong()
            animatorToStart.start()
            animatorToStart.currentPlayTime = startTime
        }
    }
}
