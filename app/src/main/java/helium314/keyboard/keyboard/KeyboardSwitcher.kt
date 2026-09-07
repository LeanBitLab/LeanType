/*
 * Copyright (C) 2008 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.KeyboardLayoutSet.KeyboardLayoutSetException
import helium314.keyboard.keyboard.clipboard.ClipboardHistoryView
import helium314.keyboard.keyboard.emoji.EmojiPalettesView
import helium314.keyboard.keyboard.internal.KeyboardState
import helium314.keyboard.latin.FloatingKeyboardManager
import helium314.keyboard.latin.InputView
import helium314.keyboard.latin.KeyboardWrapperView
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.WordComposer
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.handwriting.HandwritingLoader
import helium314.keyboard.latin.handwriting.HandwritingView
import helium314.keyboard.latin.ocr.OcrCameraView
import helium314.keyboard.latin.ocr.OcrResultView
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.latin.utils.CapsModeUtils
import helium314.keyboard.latin.utils.LanguageOnSpacebarUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.RecapitalizeMode
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.ScreenProfileProvider
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.prefs

class KeyboardSwitcher private constructor() : KeyboardState.SwitchActions {

    private var mCurrentInputView: InputView? = null
    private var mKeyboardViewWrapper: KeyboardWrapperView? = null
    private var mMainKeyboardFrame: View? = null
    private var mKeyboardView: MainKeyboardView? = null
    private var mEmojiPalettesView: EmojiPalettesView? = null
    private var mEmojiTabStripView: View? = null
    private var mClipboardStripView: LinearLayout? = null
    private var mClipboardStripScrollView: HorizontalScrollView? = null
    private var mOcrStripView: LinearLayout? = null
    private var mOcrStripScrollView: HorizontalScrollView? = null
    private var mSuggestionStripView: SuggestionStripView? = null
    private var mStripContainer: LinearLayout? = null
    private var mClipboardHistoryView: ClipboardHistoryView? = null
    private var mHandwritingView: HandwritingView? = null
    private var mOcrCameraView: OcrCameraView? = null
    private var mOcrResultView: OcrResultView? = null
    private var mTouchpadView: TouchpadView? = null
    private var mFakeToastView: TextView? = null
    private var mLatinIME: LatinIME? = null
    private var mRichImm: RichInputMethodManager? = null
    private var mIsHardwareAcceleratedDrawingEnabled: Boolean = false

    private var mState: KeyboardState? = null
    private var mKeyboardLayoutSet: KeyboardLayoutSet? = null

    private var mKeyboardTheme: KeyboardTheme? = null
    private var mThemeContext: Context? = null
    private var mCurrentUiMode: Int = 0
    private var mCurrentOrientation: Int = 0
    private var mCurrentDpi: Int = 0
    private var mThemeNeedsReload: Boolean = false

    val latinIME: LatinIME? get() = mLatinIME

    private fun initInternal(latinIme: LatinIME) {
        mLatinIME = latinIme
        mRichImm = RichInputMethodManager.getInstance()
        mState = KeyboardState(this)
        mIsHardwareAcceleratedDrawingEnabled = latinIme.enableHardwareAcceleration()
    }

    fun updateKeyboardTheme(displayContext: Context) {
        val themeUpdated = updateKeyboardThemeAndContextThemeWrapper(
            displayContext, KeyboardTheme.getKeyboardTheme(displayContext)
        )
        if (themeUpdated) {
            val settings = Settings.getInstance()
            settings.loadSettings(
                displayContext, settings.current.mLocale,
                settings.current.mInputAttributes, settings.current.mCurrentKeyboardScript
            )
            if (mKeyboardView != null) {
                mLatinIME?.setInputView(onCreateInputView(displayContext, mIsHardwareAcceleratedDrawingEnabled))
            }
        }
    }

    private fun updateKeyboardThemeAndContextThemeWrapper(
        context: Context,
        keyboardTheme: KeyboardTheme
    ): Boolean {
        val res = context.resources
        if (mThemeNeedsReload
            || mThemeContext == null
            || keyboardTheme != mKeyboardTheme
            || mCurrentDpi != res.displayMetrics.densityDpi
            || mCurrentOrientation != res.configuration.orientation
            || (mCurrentUiMode and Configuration.UI_MODE_NIGHT_MASK) != (res.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
            || mThemeContext?.resources != res
            || Settings.getValues().mColors.haveColorsChanged(context)
        ) {
            mThemeNeedsReload = false
            mKeyboardTheme = keyboardTheme
            mThemeContext = ContextThemeWrapper(context, keyboardTheme.mStyleId)
            mCurrentUiMode = res.configuration.uiMode
            mCurrentOrientation = res.configuration.orientation
            mCurrentDpi = res.displayMetrics.densityDpi
            KeyboardLayoutSet.onKeyboardThemeChanged()
            return true
        }
        return false
    }

    fun loadKeyboard(
        editorInfo: EditorInfo,
        settingsValues: SettingsValues,
        currentAutoCapsState: Int,
        currentRecapitalizeState: RecapitalizeMode?,
        internalAction: KeyboardLayoutSet.InternalAction?
    ) {
        val themeContext = mThemeContext!!
        val builder = KeyboardLayoutSet.Builder(themeContext, editorInfo)
        val keyboardWidth = ResourceUtils.getKeyboardWidth(themeContext, settingsValues)
        val keyboardHeight = ResourceUtils.getKeyboardHeight(themeContext.resources, settingsValues)
        val oneHandedModeEnabled = settingsValues.mOneHandedModeEnabled
        mKeyboardLayoutSet = builder.setKeyboardGeometry(keyboardWidth, keyboardHeight)
            .setSubtype(mRichImm!!.currentSubtype)
            .setVoiceInputKeyEnabled(settingsValues.mShowsVoiceInputKey)
            .setNumberRowEnabled(settingsValues.mShowsNumberRow)
            .setNumberRowInSymbolsEnabled(settingsValues.mShowsNumberRowInSymbols)
            .setCompactNumberRowInSymbolsEnabled(settingsValues.mCompactNumberRowInSymbols)
            .setLanguageSwitchKeyEnabled(settingsValues.isLanguageSwitchKeyEnabled())
            .setEmojiKeyEnabled(settingsValues.mShowsEmojiKey)
            .setSplitLayoutEnabled(settingsValues.mIsSplitKeyboardEnabled)
            .setOneHandedModeEnabled(oneHandedModeEnabled)
            .setInternalAction(internalAction)
            .build()
        try {
            mState!!.onLoadKeyboard(currentAutoCapsState, currentRecapitalizeState, oneHandedModeEnabled)
        } catch (e: KeyboardLayoutSetException) {
            Log.e(TAG, "loading keyboard failed: " + e.mKeyboardId, e.cause)
            try {
                val defaults = SubtypeUtilsAdditional.createDefaultSubtype(mRichImm!!.currentSubtypeLocale)
                mKeyboardLayoutSet = builder.setKeyboardGeometry(keyboardWidth, keyboardHeight)
                    .setSubtype(RichInputMethodSubtype.get(defaults))
                    .setVoiceInputKeyEnabled(settingsValues.mShowsVoiceInputKey)
                    .setNumberRowEnabled(settingsValues.mShowsNumberRow)
                    .setNumberRowInSymbolsEnabled(settingsValues.mShowsNumberRowInSymbols)
                    .setCompactNumberRowInSymbolsEnabled(settingsValues.mCompactNumberRowInSymbols)
                    .setLanguageSwitchKeyEnabled(settingsValues.isLanguageSwitchKeyEnabled())
                    .setEmojiKeyEnabled(settingsValues.mShowsEmojiKey)
                    .setSplitLayoutEnabled(settingsValues.mIsSplitKeyboardEnabled)
                    .setOneHandedModeEnabled(oneHandedModeEnabled)
                    .build()
                mState!!.onLoadKeyboard(currentAutoCapsState, currentRecapitalizeState, oneHandedModeEnabled)
                showToast("error loading the keyboard, falling back to defaults", false)
            } catch (e2: KeyboardLayoutSetException) {
                Log.e(TAG, "even fallback to defaults failed: " + e2.mKeyboardId, e2.cause)
            }
        }
    }

    fun saveKeyboardState() {
        if (keyboard != null || isShowingEmojiPalettes || isShowingClipboardHistory) {
            mState?.onSaveKeyboardState()
        }
    }

    fun onHideWindow() {
        mKeyboardView?.onHideWindow()
        if (mOcrCameraView != null && mOcrCameraView!!.isShown) {
            mOcrCameraView!!.stopCamera()
        }
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        ScreenProfileProvider.invalidateCache()
        setThemeNeedsReload()
    }

    private fun setKeyboard(keyboardId: Int, toggleState: KeyboardSwitchState) {
        val keyboardView = mKeyboardView ?: return

        val currentSettingsValues = Settings.getValues()
        setMainKeyboardFrame(currentSettingsValues, toggleState)
        val oldKeyboard = keyboardView.keyboard
        val targetId: Int = if (KeyboardActionListenerImpl.sPersistentTextEditModeActive && (keyboardId == KeyboardId.ELEMENT_ALPHABET
                || keyboardId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED
                || keyboardId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED
                || keyboardId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED
                || keyboardId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED)
        ) {
            KeyboardId.ELEMENT_TEXT_EDIT
        } else {
            keyboardId
        }
        val newKeyboard = mKeyboardLayoutSet!!.getKeyboard(targetId)
        keyboardView.setKeyboard(newKeyboard)
        mCurrentInputView?.setKeyboardTopPadding(newKeyboard.mTopPadding)
        keyboardView.setKeyPreviewPopupEnabled(currentSettingsValues.mKeyPreviewPopupOn)
        keyboardView.updateShortcutKey(mRichImm!!.isShortcutImeReady)
        val subtypeChanged = oldKeyboard == null || newKeyboard.mId.mSubtype != oldKeyboard.mId.mSubtype
        val languageOnSpacebarFormatType = LanguageOnSpacebarUtils.getLanguageOnSpacebarFormatType(newKeyboard.mId.mSubtype)
        val hasMultipleEnabledIMEsOrSubtypes = mRichImm!!.hasMultipleEnabledIMEsOrSubtypes(true)
        keyboardView.startDisplayLanguageOnSpacebar(subtypeChanged, languageOnSpacebarFormatType, hasMultipleEnabledIMEsOrSubtypes)
    }

    val keyboard: Keyboard? get() = mKeyboardView?.keyboard

    fun resetKeyboardStateToAlphabet(currentAutoCapsState: Int, currentRecapitalizeState: RecapitalizeMode?) {
        mState?.onResetKeyboardStateToAlphabet(currentAutoCapsState, currentRecapitalizeState)
    }

    fun onPressKey(code: Int, isSinglePointer: Boolean, currentAutoCapsState: Int, currentRecapitalizeState: RecapitalizeMode?) {
        mState?.onPressKey(code, isSinglePointer, currentAutoCapsState, currentRecapitalizeState)
    }

    fun onReleaseKey(code: Int, withSliding: Boolean, currentAutoCapsState: Int, currentRecapitalizeState: RecapitalizeMode?) {
        mState?.onReleaseKey(code, withSliding, currentAutoCapsState, currentRecapitalizeState)
    }

    fun onFinishSlidingInput(currentAutoCapsState: Int, currentRecapitalizeState: RecapitalizeMode?) {
        mState?.onFinishSlidingInput(currentAutoCapsState, currentRecapitalizeState)
    }

    override fun setAlphabetKeyboard() {
        if (DEBUG_ACTION) Log.d(TAG, "setAlphabetKeyboard")
        setKeyboard(KeyboardId.ELEMENT_ALPHABET, KeyboardSwitchState.OTHER)
    }

    override fun setAlphabetManualShiftedKeyboard() {
        if (DEBUG_ACTION) Log.d(TAG, "setAlphabetManualShiftedKeyboard")
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED, KeyboardSwitchState.OTHER)
    }

    override fun setAlphabetAutomaticShiftedKeyboard() {
        if (DEBUG_ACTION) Log.d(TAG, "setAlphabetAutomaticShiftedKeyboard")
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED, KeyboardSwitchState.OTHER)
    }

    override fun setAlphabetShiftLockedKeyboard() {
        if (DEBUG_ACTION) Log.d(TAG, "setAlphabetShiftLockedKeyboard")
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED, KeyboardSwitchState.OTHER)
    }

    override fun setAlphabetShiftLockShiftedKeyboard() {
        if (DEBUG_ACTION) Log.d(TAG, "setAlphabetShiftLockShiftedKeyboard")
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED, KeyboardSwitchState.OTHER)
    }

    override fun setSymbolsKeyboard() {
        if (DEBUG_ACTION) Log.d(TAG, "setSymbolsKeyboard")
        setKeyboard(KeyboardId.ELEMENT_SYMBOLS, KeyboardSwitchState.OTHER)
    }

    override fun setSymbolsShiftedKeyboard() {
        if (DEBUG_ACTION) Log.d(TAG, "setSymbolsShiftedKeyboard")
        setKeyboard(KeyboardId.ELEMENT_SYMBOLS_SHIFTED, KeyboardSwitchState.SYMBOLS_SHIFTED)
    }

    override fun setCustomKeyboard(customIndex: Int) {
        if (DEBUG_ACTION) Log.d(TAG, "setCustomKeyboard: $customIndex")
        val elementId = when (customIndex) {
            1 -> KeyboardId.ELEMENT_CUSTOM1
            2 -> KeyboardId.ELEMENT_CUSTOM2
            3 -> KeyboardId.ELEMENT_CUSTOM3
            4 -> KeyboardId.ELEMENT_CUSTOM4
            5 -> KeyboardId.ELEMENT_CUSTOM5
            else -> KeyboardId.ELEMENT_ALPHABET
        }
        setKeyboard(elementId, KeyboardSwitchState.OTHER)
    }

    fun isImeSuppressedByHardwareKeyboard(
        settingsValues: SettingsValues,
        toggleState: KeyboardSwitchState
    ): Boolean {
        if (toggleState == KeyboardSwitchState.EMOJI || toggleState == KeyboardSwitchState.CLIPBOARD) {
            return false
        }
        return settingsValues.mHasHardwareKeyboard && (toggleState == KeyboardSwitchState.HIDDEN || settingsValues.mShowToolbarOnly)
    }

    private fun setMainKeyboardFrame(
        settingsValues: SettingsValues,
        toggleState: KeyboardSwitchState
    ) {
        if (isOcrShowing) {
            mKeyboardView?.let {
                it.visibility = View.INVISIBLE
                it.isClickable = false
                it.isFocusable = false
            }
            if (mOcrCameraView != null && mOcrCameraView!!.isShown) {
                mOcrCameraView!!.bringToFront()
            } else if (mOcrResultView != null && mOcrResultView!!.isShown) {
                mOcrResultView!!.bringToFront()
            }
            mCurrentInputView?.let { it.post { it.requestApplyInsets() } }
            return
        }
        val visibility = if (isImeSuppressedByHardwareKeyboard(settingsValues, toggleState)) View.GONE else View.VISIBLE
        val stripVisibility = if (settingsValues.mToolbarMode == ToolbarMode.HIDDEN) View.GONE else View.VISIBLE
        mStripContainer?.visibility = stripVisibility
        PointerTracker.switchTo(mKeyboardView)
        if (PointerTracker.sPersistentTouchpadModeActive) {
            mKeyboardView?.visibility = if (visibility == View.VISIBLE) View.INVISIBLE else View.GONE
        } else {
            mKeyboardView?.visibility = visibility
        }
        mMainKeyboardFrame?.visibility = visibility
        mKeyboardViewWrapper?.visibility = if (Settings.getInstance().readShowToolbarOnly()) View.GONE else View.VISIBLE
        mEmojiPalettesView?.let {
            it.visibility = View.GONE
            it.stopEmojiPalettes()
        }
        mEmojiTabStripView?.visibility = View.GONE
        mClipboardStripScrollView?.visibility = View.GONE
        mOcrStripScrollView?.visibility = View.GONE
        mSuggestionStripView?.visibility = stripVisibility
        mClipboardHistoryView?.let {
            it.visibility = View.GONE
            it.stopClipboardHistory()
        }
        mHandwritingView?.let {
            if (it.isShown) it.stopHandwriting()
            it.visibility = View.GONE
        }
        mOcrCameraView?.let {
            if (it.isShown) it.stopCamera()
            it.visibility = View.GONE
        }
        mOcrResultView?.visibility = View.GONE

        if (PointerTracker.sPersistentTouchpadModeActive) {
            mTouchpadView?.let { touchpad ->
                val kbView = mKeyboardView
                touchpad.visibility = visibility
                touchpad.applyColors(Settings.getValues().mColors)
                if (kbView != null) {
                    touchpad.setPadding(
                        kbView.paddingLeft,
                        kbView.paddingTop,
                        kbView.paddingRight,
                        kbView.paddingBottom
                    )
                }
            }
        } else {
            mTouchpadView?.visibility = View.GONE
        }
    }

    override fun setEmojiKeyboard() {
        if (DEBUG_ACTION) Log.d(TAG, "setEmojiKeyboard")
        PointerTracker.sPersistentTouchpadModeActive = false
        mTouchpadView?.visibility = View.GONE
        KeyboardActionListenerImpl.sPersistentTextEditModeActive = false
        mMainKeyboardFrame?.visibility = View.VISIBLE
        mKeyboardView?.visibility = View.GONE
        val splitToolbar = Settings.getValues().mSplitToolbar
        mSuggestionStripView?.visibility = if (splitToolbar) View.VISIBLE else View.GONE
        mStripContainer?.visibility = getSecondaryStripVisibility()
        mClipboardStripScrollView?.visibility = View.GONE
        mEmojiTabStripView?.visibility = View.VISIBLE
        mClipboardHistoryView?.visibility = View.GONE
        mEmojiPalettesView?.let {
            it.startEmojiPalettes(mKeyboardView!!.keyVisualAttribute, mLatinIME!!.currentInputEditorInfo, mLatinIME!!.mKeyboardActionListener)
            it.visibility = View.VISIBLE
        }
        if (splitToolbar && mSuggestionStripView != null) {
            mSuggestionStripView!!.updateSplitToolbarState()
        }
    }

    override fun setClipboardKeyboard() {
        if (DEBUG_ACTION) Log.d(TAG, "setClipboardKeyboard")
        PointerTracker.sPersistentTouchpadModeActive = false
        mTouchpadView?.visibility = View.GONE
        KeyboardActionListenerImpl.sPersistentTextEditModeActive = false
        mMainKeyboardFrame?.visibility = View.VISIBLE
        mKeyboardView?.visibility = View.GONE
        mEmojiTabStripView?.visibility = View.GONE
        mSuggestionStripView?.visibility = View.GONE
        mStripContainer?.visibility = getSecondaryStripVisibility()
        mClipboardStripScrollView?.let { scrollView ->
            scrollView.post { scrollView.scrollTo(0, 0) }
            Settings.getValues().mColors.setBackground(scrollView, ColorType.STRIP_BACKGROUND)
            scrollView.visibility = View.VISIBLE
        }
        mEmojiPalettesView?.visibility = View.GONE
        mClipboardHistoryView?.let {
            it.startClipboardHistory(mLatinIME!!.clipboardHistoryManager, mKeyboardView!!.keyVisualAttribute, mLatinIME!!.currentInputEditorInfo, mLatinIME!!.mKeyboardActionListener)
            it.visibility = View.VISIBLE
        }
    }

    fun setHandwritingKeyboard() {
        if (DEBUG_ACTION) Log.d(TAG, "setHandwritingKeyboard")
        PointerTracker.sPersistentTouchpadModeActive = false
        mTouchpadView?.visibility = View.GONE
        KeyboardActionListenerImpl.sPersistentTextEditModeActive = false
        mMainKeyboardFrame?.visibility = View.VISIBLE
        mKeyboardView?.visibility = View.GONE
        mEmojiTabStripView?.visibility = View.GONE
        mSuggestionStripView?.visibility = View.VISIBLE
        mStripContainer?.visibility = View.VISIBLE
        mClipboardStripScrollView?.visibility = View.GONE
        mEmojiPalettesView?.visibility = View.GONE
        mClipboardHistoryView?.visibility = View.GONE

        mHandwritingView?.let { handwritingView ->
            val subtype = mRichImm!!.currentSubtype
            val subtypeLanguage = subtype.locale.toLanguageTag()
            val effectiveLanguage = HandwritingLoader.getEffectiveLanguage(mLatinIME!!, subtypeLanguage)
            handwritingView.startHandwriting(
                mLatinIME!!.currentInputEditorInfo,
                mLatinIME!!.mKeyboardActionListener,
                effectiveLanguage
            )
            handwritingView.visibility = View.VISIBLE
        }
    }

    val isHandwritingShowing: Boolean get() = mHandwritingView != null && mHandwritingView!!.isShown

    fun clearHandwritingCanvas() {
        mHandwritingView?.clearCanvasAndComposition()
    }

    fun showOcrCamera() {
        if (DEBUG_ACTION) Log.d(TAG, "showOcrCamera")
        PointerTracker.sPersistentTouchpadModeActive = false
        mTouchpadView?.visibility = View.GONE
        KeyboardActionListenerImpl.sPersistentTextEditModeActive = false
        mMainKeyboardFrame?.visibility = View.VISIBLE
        mKeyboardView?.let {
            it.visibility = View.INVISIBLE
            it.isClickable = false
            it.isFocusable = false
        }
        mEmojiTabStripView?.visibility = View.GONE
        mSuggestionStripView?.visibility = View.GONE
        mStripContainer?.visibility = View.GONE
        mClipboardStripScrollView?.visibility = View.GONE
        mEmojiPalettesView?.visibility = View.GONE
        mClipboardHistoryView?.visibility = View.GONE
        mHandwritingView?.let {
            if (it.isShown) it.stopHandwriting()
            it.visibility = View.GONE
        }
        mOcrResultView?.visibility = View.GONE
        mOcrCameraView?.let { cameraView ->
            val ocrCameraHeight = ResourceUtils.getOcrCameraHeight(mThemeContext!!.resources, Settings.getValues())
            val lp = cameraView.layoutParams
            if (lp != null) {
                lp.height = ocrCameraHeight
                cameraView.layoutParams = lp
            }
            cameraView.visibility = View.VISIBLE
            cameraView.bringToFront()
            cameraView.startCamera()
        }
        requestInputViewLayoutAndInsets()
    }

    fun showOcrResult(lines: List<String>) {
        if (DEBUG_ACTION) Log.d(TAG, "showOcrResult")
        PointerTracker.sPersistentTouchpadModeActive = false
        mTouchpadView?.visibility = View.GONE
        KeyboardActionListenerImpl.sPersistentTextEditModeActive = false
        mMainKeyboardFrame?.visibility = View.VISIBLE
        mKeyboardView?.let {
            it.visibility = View.INVISIBLE
            it.isClickable = false
            it.isFocusable = false
        }
        mEmojiTabStripView?.visibility = View.GONE
        mSuggestionStripView?.visibility = View.GONE
        mClipboardStripScrollView?.visibility = View.GONE
        mEmojiPalettesView?.visibility = View.GONE
        mClipboardHistoryView?.visibility = View.GONE
        mHandwritingView?.let {
            if (it.isShown) it.stopHandwriting()
            it.visibility = View.GONE
        }
        mOcrStripScrollView?.let {
            Settings.getValues().mColors.setBackground(it, ColorType.STRIP_BACKGROUND)
            it.visibility = View.VISIBLE
        }
        mStripContainer?.visibility = View.VISIBLE
        mOcrCameraView?.let {
            it.stopCamera()
            it.visibility = View.GONE
        }
        mOcrResultView?.let { resultView ->
            val keyboardHeight = ResourceUtils.getKeyboardHeight(mThemeContext!!.resources, Settings.getValues())
            val lp = resultView.layoutParams
            if (lp != null) {
                lp.height = keyboardHeight
                resultView.layoutParams = lp
            }
            resultView.setResultText(lines)
            resultView.applyColors(Settings.getValues().mColors)
            resultView.visibility = View.VISIBLE
            resultView.bringToFront()
        }
        requestInputViewLayoutAndInsets()
    }

    fun hideOcrPanels() {
        mOcrStripScrollView?.visibility = View.GONE
        mSuggestionStripView?.visibility = View.VISIBLE
        mStripContainer?.visibility = View.VISIBLE
        mOcrCameraView?.let {
            if (it.isShown) it.stopCamera()
            it.visibility = View.GONE
        }
        mOcrResultView?.visibility = View.GONE
        mKeyboardView?.let {
            it.visibility = View.VISIBLE
            it.isClickable = true
            it.isFocusable = true
        }
        requestInputViewLayoutAndInsets()
        setAlphabetKeyboard()
    }

    private fun requestInputViewLayoutAndInsets() {
        mCurrentInputView?.let { view ->
            if (view.isInLayout) {
                view.post { view.requestLayout() }
            } else {
                view.requestLayout()
            }
            view.post { view.requestApplyInsets() }
        }
    }

    val isOcrCameraShowing: Boolean
        get() = mOcrCameraView != null && (mOcrCameraView!!.isShown || mOcrCameraView!!.visibility == View.VISIBLE)

    val isOcrShowing: Boolean
        get() = isOcrCameraShowing || (mOcrResultView != null && (mOcrResultView!!.isShown || mOcrResultView!!.visibility == View.VISIBLE))

    override fun setNumpadKeyboard() {
        if (DEBUG_ACTION) Log.d(TAG, "setNumpadKeyboard")
        setKeyboard(KeyboardId.ELEMENT_NUMPAD, KeyboardSwitchState.OTHER)
    }

    override fun toggleNumpad(
        withSliding: Boolean,
        autoCapsFlags: Int,
        recapitalizeMode: RecapitalizeMode?,
        forceReturnToAlpha: Boolean
    ) {
        if (DEBUG_ACTION) Log.d(TAG, "toggleNumpad")
        mState?.toggleNumpad(withSliding, autoCapsFlags, recapitalizeMode, forceReturnToAlpha, true)
    }

    enum class KeyboardSwitchState(val mKeyboardId: Int) {
        HIDDEN(-1),
        SYMBOLS_SHIFTED(KeyboardId.ELEMENT_SYMBOLS_SHIFTED),
        EMOJI(KeyboardId.ELEMENT_EMOJI_RECENTS),
        CLIPBOARD(KeyboardId.ELEMENT_CLIPBOARD),
        OTHER(-1)
    }

    val keyboardSwitchState: KeyboardSwitchState
        get() {
            val hidden = !isShowingEmojiPalettes && !isShowingClipboardHistory
                    && (mKeyboardLayoutSet == null || mKeyboardView == null || !mKeyboardView!!.isShown)
            return when {
                hidden -> KeyboardSwitchState.HIDDEN
                isShowingEmojiPalettes -> KeyboardSwitchState.EMOJI
                isShowingClipboardHistory -> KeyboardSwitchState.CLIPBOARD
                isShowingKeyboardId(KeyboardId.ELEMENT_SYMBOLS_SHIFTED) -> KeyboardSwitchState.SYMBOLS_SHIFTED
                else -> KeyboardSwitchState.OTHER
            }
        }

    fun onToggleKeyboard(toggleState: KeyboardSwitchState) {
        val currentState = keyboardSwitchState
        Log.w(TAG, "onToggleKeyboard() : Current = $currentState : Toggle = $toggleState")
        if (currentState == toggleState) {
            mLatinIME?.stopShowingInputView()
            mLatinIME?.hideWindow()
            setAlphabetKeyboard()
        } else {
            mLatinIME?.startShowingInputView(true)
            when (toggleState) {
                KeyboardSwitchState.EMOJI -> setEmojiKeyboard()
                KeyboardSwitchState.CLIPBOARD -> setClipboardKeyboard()
                else -> {
                    mEmojiPalettesView?.let {
                        it.stopEmojiPalettes()
                        it.visibility = View.GONE
                    }
                    mClipboardHistoryView?.let {
                        it.stopClipboardHistory()
                        it.visibility = View.GONE
                    }
                    mMainKeyboardFrame?.visibility = View.VISIBLE
                    mKeyboardView?.visibility = View.VISIBLE
                    setKeyboard(toggleState.mKeyboardId, toggleState)
                }
            }
        }
    }

    override fun requestUpdatingShiftState(autoCapsFlags: Int, recapitalizeMode: RecapitalizeMode?) {
        if (DEBUG_ACTION) {
            Log.d(TAG, "requestUpdatingShiftState: autoCapsFlags=" + CapsModeUtils.flagsToString(autoCapsFlags) + " recapitalizeMode=" + recapitalizeMode)
        }
        mState?.onUpdateShiftState(autoCapsFlags, recapitalizeMode)
    }

    override fun startDoubleTapShiftKeyTimer() {
        if (DEBUG_TIMER_ACTION) Log.d(TAG, "startDoubleTapShiftKeyTimer")
        mKeyboardView?.startDoubleTapShiftKeyTimer()
    }

    override fun cancelDoubleTapShiftKeyTimer() {
        if (DEBUG_TIMER_ACTION) Log.d(TAG, "setAlphabetKeyboard")
        mKeyboardView?.cancelDoubleTapShiftKeyTimer()
    }

    override fun setOneHandedModeEnabled(enabled: Boolean) {
        setOneHandedModeEnabled(enabled, false)
    }

    fun setOneHandedModeEnabled(enabled: Boolean, force: Boolean) {
        val wrapper = mKeyboardViewWrapper ?: return
        if (!force && wrapper.oneHandedModeEnabled == enabled) return
        val settings = Settings.getInstance()
        wrapper.oneHandedModeEnabled = enabled
        wrapper.oneHandedGravity = settings.current.mOneHandedModeGravity
        settings.writeOneHandedModeEnabled(enabled)
        reloadKeyboard()
    }

    override fun switchOneHandedMode() {
        val wrapper = mKeyboardViewWrapper ?: return
        wrapper.switchOneHandedModeSide()
        Settings.getInstance().writeOneHandedModeGravity(wrapper.oneHandedGravity)
    }

    override fun toggleFloatingKeyboard() {
        mLatinIME?.floatingKeyboardManager?.toggle()
    }

    fun showTouchpadView() {
        val touchpad = mTouchpadView ?: return
        val kbView = mKeyboardView ?: return
        kbView.visibility = View.INVISIBLE
        mEmojiPalettesView?.visibility = View.GONE
        mClipboardHistoryView?.visibility = View.GONE
        mKeyboardViewWrapper?.let { wrapper ->
            wrapper.findViewById<View>(R.id.btn_stop_one_handed_mode)?.visibility = View.GONE
            wrapper.findViewById<View>(R.id.btn_switch_one_handed_mode)?.visibility = View.GONE
            wrapper.findViewById<View>(R.id.btn_resize_one_handed_mode)?.visibility = View.GONE
        }
        if (Settings.getValues().mTouchpadFullscreen) {
            mStripContainer?.visibility = View.GONE
        }
        touchpad.setPadding(
            kbView.paddingLeft,
            kbView.paddingTop,
            kbView.paddingRight,
            kbView.paddingBottom
        )
        touchpad.applyColors(Settings.getValues().mColors)
        touchpad.visibility = View.VISIBLE
        mMainKeyboardFrame?.visibility = View.VISIBLE
    }

    fun hideTouchpadView() {
        val touchpad = mTouchpadView ?: return
        touchpad.visibility = View.GONE
        mKeyboardView?.let {
            it.visibility = View.VISIBLE
            it.alpha = 1.0f
        }
        mStripContainer?.visibility = if (Settings.getValues().mToolbarMode == ToolbarMode.HIDDEN) View.GONE else View.VISIBLE
        mKeyboardViewWrapper?.let { wrapper ->
            if (wrapper.oneHandedModeEnabled) {
                wrapper.findViewById<View>(R.id.btn_stop_one_handed_mode)?.visibility = View.VISIBLE
                wrapper.findViewById<View>(R.id.btn_switch_one_handed_mode)?.visibility = View.VISIBLE
                wrapper.findViewById<View>(R.id.btn_resize_one_handed_mode)?.visibility = View.VISIBLE
            }
        }
    }

    val touchpadView: TouchpadView? get() = mTouchpadView

    fun showTextEditView() {
        setKeyboard(KeyboardId.ELEMENT_TEXT_EDIT, KeyboardSwitchState.OTHER)
    }

    fun hideTextEditView() {
        setAlphabetKeyboard()
    }

    fun toggleSplitKeyboardMode() {
        val settings = Settings.getInstance()
        settings.writeSplitKeyboardEnabled(
            !settings.current.mIsSplitKeyboardEnabled,
            mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE
        )
        setOneHandedModeEnabled(settings.current.mOneHandedModeEnabled, true)
        reloadKeyboard()
    }

    fun reloadKeyboard() {
        if (mCurrentInputView == null) return
        mEmojiPalettesView?.clearKeyboardCache()
        mSuggestionStripView?.onFloatingKeyboardScaleChanged()
        reloadMainKeyboard()
    }

    fun reloadMainKeyboard() {
        val wasEmoji = isShowingEmojiPalettes
        val wasClipboard = isShowingClipboardHistory
        loadKeyboard(
            mLatinIME!!.currentInputEditorInfo, Settings.getValues(),
            mLatinIME!!.currentAutoCapsState, mLatinIME!!.currentRecapitalizeState, null
        )
        if (wasEmoji) {
            setEmojiKeyboard()
        } else if (wasClipboard) {
            setClipboardKeyboard()
        }
    }

    fun showToast(text: String, briefToast: Boolean) {
        val latinIme = mLatinIME ?: return
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            val toastLength = if (briefToast) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            val toast = Toast.makeText(latinIme, text, toastLength)
            toast.setGravity(Gravity.CENTER, 0, 0)
            toast.show()
        } else {
            val toastLength = if (briefToast) 2000 else 3500
            showFakeToast(text, toastLength)
        }
    }

    fun showLoadingAnimation() {
        mSuggestionStripView?.showLoadingAnimation()
    }

    fun hideLoadingAnimation() {
        mSuggestionStripView?.hideLoadingAnimation()
    }

    private fun showFakeToast(text: String, timeMillis: Int) {
        val fakeToastView = mFakeToastView ?: return
        val latinIme = mLatinIME ?: return
        if (fakeToastView.visibility == View.VISIBLE) return

        val appIcon = fakeToastView.compoundDrawables[0]
        if (appIcon != null) {
            val bound = fakeToastView.lineHeight
            appIcon.setBounds(0, 0, bound, bound)
            fakeToastView.setCompoundDrawables(appIcon, null, null, null)
        }
        fakeToastView.text = text
        fakeToastView.visibility = View.VISIBLE
        fakeToastView.bringToFront()
        fakeToastView.startAnimation(AnimationUtils.loadAnimation(latinIme, R.anim.fade_in))

        fakeToastView.postDelayed({
            fakeToastView.startAnimation(AnimationUtils.loadAnimation(latinIme, R.anim.fade_out))
            fakeToastView.visibility = View.GONE
        }, timeMillis.toLong())
    }

    override val isInDoubleTapShiftKeyTimeout: Boolean
        get() {
            if (DEBUG_TIMER_ACTION) Log.d(TAG, "isInDoubleTapShiftKeyTimeout")
            val keyboardView = mKeyboardView ?: return false
            return keyboardView.isInDoubleTapShiftKeyTimeout()
        }

    fun onEvent(event: Event, currentAutoCapsState: Int, currentRecapitalizeState: RecapitalizeMode?) {
        mState?.onEvent(event, currentAutoCapsState, currentRecapitalizeState)
    }

    fun isShowingKeyboardId(vararg keyboardIds: Int): Boolean {
        val keyboardView = mKeyboardView ?: return false
        if (!keyboardView.isShown) return false
        val kb = keyboardView.keyboard ?: return false
        val activeKeyboardId = kb.mId.mElementId
        for (id in keyboardIds) {
            if (activeKeyboardId == id) return true
        }
        return false
    }

    val isShowingEmojiPalettes: Boolean
        get() = mEmojiPalettesView != null && (mEmojiPalettesView!!.isShown || mEmojiPalettesView!!.visibility == View.VISIBLE)

    val isShowingClipboardHistory: Boolean
        get() = mClipboardHistoryView != null && mClipboardHistoryView!!.isShown

    val isShowingPopupKeysPanel: Boolean
        get() {
            if (isShowingEmojiPalettes || isShowingClipboardHistory) {
                return false
            }
            return mKeyboardView != null && mKeyboardView!!.isShowingPopupKeysPanel()
        }

    val isShowingStripContainer: Boolean
        get() = mStripContainer != null && mStripContainer!!.isShown

    val emojiPalettesView: EmojiPalettesView? get() = mEmojiPalettesView

    val visibleKeyboardView: View?
        get() = when {
            isOcrCameraShowing -> mOcrCameraView
            mOcrResultView != null && (mOcrResultView!!.isShown || mOcrResultView!!.visibility == View.VISIBLE) -> mOcrResultView
            isShowingEmojiPalettes -> mEmojiPalettesView
            isShowingClipboardHistory -> mClipboardHistoryView
            isHandwritingShowing -> mHandwritingView
            else -> mKeyboardView
        }

    val wrapperView: View? get() = mKeyboardViewWrapper

    val emojiTabStrip: View? get() = mEmojiTabStripView

    val clipboardStrip: LinearLayout? get() = mClipboardStripView

    val clipboardStripScrollView: HorizontalScrollView? get() = mClipboardStripScrollView

    val ocrStrip: LinearLayout? get() = mOcrStripView

    val ocrStripScrollView: HorizontalScrollView? get() = mOcrStripScrollView

    val mainKeyboardView: MainKeyboardView? get() = mKeyboardView

    val suggestionStripView: SuggestionStripView? get() = mSuggestionStripView

    val stripContainer: LinearLayout? get() = mStripContainer

    fun deallocateMemory() {
        mKeyboardView?.let {
            it.cancelAllOngoingEvents()
            it.deallocateMemory()
        }
        mEmojiPalettesView?.stopEmojiPalettes()
        mClipboardHistoryView?.stopClipboardHistory()
        mOcrCameraView?.release()
    }

    fun trimMemory() {
        mEmojiPalettesView?.clearKeyboardCache()
        mClipboardHistoryView?.stopClipboardHistory()
        PointerTracker.clearOldViewData()
        KeyboardLayoutSet.onSystemLocaleChanged()
    }

    @SuppressLint("InflateParams")
    fun onCreateInputView(displayContext: Context, isHardwareAcceleratedDrawingEnabled: Boolean): View {
        mCurrentInputView?.removeAllViews()
        mKeyboardView?.closing()
        PointerTracker.clearOldViewData()
        val prefs = displayContext.prefs()
        if (mSuggestionStripView != null) prefs.unregisterOnSharedPreferenceChangeListener(mSuggestionStripView)
        if (mClipboardHistoryView != null) prefs.unregisterOnSharedPreferenceChangeListener(mClipboardHistoryView)
        if (mThemeNeedsReload) {
            Settings.getInstance().loadSettings(
                displayContext, Settings.getValues().mLocale,
                Settings.getValues().mInputAttributes, Settings.getValues().mCurrentKeyboardScript
            )
        }

        updateKeyboardThemeAndContextThemeWrapper(displayContext, KeyboardTheme.getKeyboardTheme(displayContext))
        val inputView = LayoutInflater.from(mThemeContext).inflate(R.layout.input_view, null) as InputView
        mCurrentInputView = inputView
        mMainKeyboardFrame = inputView.findViewById(R.id.main_keyboard_frame)
        mEmojiPalettesView = inputView.findViewById(R.id.emoji_palettes_view)
        mClipboardHistoryView = inputView.findViewById(R.id.clipboard_history_view)
        mHandwritingView = inputView.findViewById(R.id.handwriting_view)
        mOcrCameraView = inputView.findViewById(R.id.ocr_camera_view)
        mOcrResultView = inputView.findViewById(R.id.ocr_result_view)
        mFakeToastView = inputView.findViewById(R.id.fakeToast)

        mOcrCameraView?.setListener(object : OcrCameraView.OcrViewListener {
            override fun onOcrTextExtracted(lines: List<String>) {
                showOcrResult(lines)
            }

            override fun onCloseOcr() {
                hideOcrPanels()
            }
        })

        mOcrResultView?.setListener(object : OcrResultView.OcrResultListener {
            override fun onInsertText(text: String) {
                mLatinIME?.onTextInput(text)
                hideOcrPanels()
            }

            override fun onRetake() {
                showOcrCamera()
            }

            override fun onClose() {
                hideOcrPanels()
            }
        })

        mKeyboardViewWrapper = inputView.findViewById(R.id.keyboard_view_wrapper)
        mKeyboardViewWrapper?.keyboardActionListener = mLatinIME?.mKeyboardActionListener
        val keyboardView = inputView.findViewById<MainKeyboardView>(R.id.keyboard_view)
        mKeyboardView = keyboardView
        keyboardView.setHardwareAcceleratedDrawingEnabled(isHardwareAcceleratedDrawingEnabled)
        keyboardView.setKeyboardActionListener(mLatinIME?.mKeyboardActionListener)
        mEmojiPalettesView?.setHardwareAcceleratedDrawingEnabled(isHardwareAcceleratedDrawingEnabled)
        mEmojiPalettesView?.setKeyboardActionListener(mLatinIME?.mKeyboardActionListener)
        mClipboardHistoryView?.setHardwareAcceleratedDrawingEnabled(isHardwareAcceleratedDrawingEnabled)
        val actionListener = mLatinIME?.mKeyboardActionListener
        if (actionListener != null) {
            mClipboardHistoryView?.keyboardActionListener = actionListener
        }
        mHandwritingView?.setHardwareAcceleratedDrawingEnabled(isHardwareAcceleratedDrawingEnabled)

        mEmojiTabStripView = inputView.findViewById(R.id.emoji_tab_strip)
        mClipboardStripView = inputView.findViewById(R.id.clipboard_strip)
        mClipboardStripScrollView = inputView.findViewById(R.id.clipboard_strip_scroll_view)
        mOcrStripView = inputView.findViewById(R.id.ocr_strip)
        mOcrStripScrollView = inputView.findViewById(R.id.ocr_strip_scroll_view)
        mSuggestionStripView = inputView.findViewById(R.id.suggestion_strip_view)
        mStripContainer = inputView.findViewById(R.id.strip_container)

        prefs.registerOnSharedPreferenceChangeListener(mSuggestionStripView)
        prefs.registerOnSharedPreferenceChangeListener(mClipboardHistoryView)
        PointerTracker.switchTo(keyboardView)

        val touchpadView = inputView.findViewById<TouchpadView>(R.id.touchpad_view)
        mTouchpadView = touchpadView
        if (PointerTracker.sPersistentTouchpadModeActive && touchpadView != null) {
            val listener = mLatinIME?.mKeyboardActionListener
            if (listener is KeyboardActionListenerImpl) {
                listener.setupTouchpadListener(touchpadView)
            }
        }

        keyboardView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (mTouchpadView != null && mTouchpadView!!.visibility == View.VISIBLE) {
                mTouchpadView!!.setPadding(
                    keyboardView.paddingLeft,
                    keyboardView.paddingTop,
                    keyboardView.paddingRight,
                    keyboardView.paddingBottom
                )
            }
        }

        return inputView
    }

    val floatingKeyboardManager: FloatingKeyboardManager? get() = mLatinIME?.floatingKeyboardManager

    val keyboardShiftMode: Int
        get() {
            val kb = keyboard ?: return WordComposer.CAPS_MODE_OFF
            return kb.mId.keyboardCapsMode
        }

    val currentKeyboardScript: String
        get() = mKeyboardLayoutSet?.getScript() ?: ScriptUtils.SCRIPT_UNKNOWN

    fun switchToSubtype(subtype: InputMethodSubtype?) {
        if (subtype != null) {
            mLatinIME?.switchToSubtype(subtype)
        }
    }

    val localeAndConfidenceInfo: String?
        get() = mLatinIME?.localeAndConfidenceInfo

    fun setThemeNeedsReload() {
        mThemeNeedsReload = true
        val latinIme = mLatinIME ?: return
        if (!latinIme.isInputViewShown) return

        latinIme.hideWindow()
        try {
            latinIme.showWindow(true)
        } catch (_: IllegalStateException) {
        }
    }

    companion object {
        private val TAG = KeyboardSwitcher::class.java.simpleName
        private const val DEBUG_ACTION = false
        private const val DEBUG_TIMER_ACTION = false

        @SuppressLint("StaticFieldLeak")
        private val sInstance = KeyboardSwitcher()

        @JvmStatic
        fun getInstance(): KeyboardSwitcher = sInstance

        @JvmStatic
        fun init(latinIme: LatinIME) {
            sInstance.initInternal(latinIme)
        }

        private fun getSecondaryStripVisibility(): Int {
            return if (Settings.getValues().mSecondaryStripVisible) View.VISIBLE else View.GONE
        }
    }
}
