// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import android.text.InputType
import android.util.SparseArray
import android.view.KeyEvent
import android.view.inputmethod.InputMethodSubtype
import androidx.core.util.forEach
import helium314.keyboard.event.Event
import helium314.keyboard.event.HangulEventDecoder
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.event.HardwareEventDecoder
import helium314.keyboard.event.HardwareKeyboardEventDecoder
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.EmojiAltPhysicalKeyDetector
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.common.combiningRange
import helium314.keyboard.latin.common.loopOverCodePoints
import helium314.keyboard.latin.common.loopOverCodePointsBackwards
import helium314.keyboard.latin.define.ProductionFlags
import helium314.keyboard.latin.inputlogic.InputLogic
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.SubtypeSettings
import kotlin.math.abs
import kotlin.math.min

class KeyboardActionListenerImpl(private val latinIME: LatinIME, private val inputLogic: InputLogic) : KeyboardActionListener {

    private val connection = inputLogic.connection
    private val emojiAltPhysicalKeyDetector by lazy { EmojiAltPhysicalKeyDetector(latinIME.resources) }

    // We expect to have only one decoder in almost all cases, hence the default capacity of 1.
    // If it turns out we need several, it will get grown seamlessly.
    private val hardwareEventDecoders: SparseArray<HardwareEventDecoder> = SparseArray(1)

    private val keyboardSwitcher = KeyboardSwitcher.getInstance()
    private val settings = Settings.getInstance()
    private val audioAndHapticFeedbackManager = AudioAndHapticFeedbackManager.getInstance()

    // language slide state
    private var initialSubtype: InputMethodSubtype? = null
    private var subtypeSwitchCount = 0

    // space swipe state
    private var isSpaceSwipeActive = false

    override fun onPressKey(primaryCode: Int, repeatCount: Int, isSinglePointer: Boolean, hapticEvent: HapticEvent) {
        metaOnPressKey(primaryCode)
        keyboardSwitcher.onPressKey(primaryCode, isSinglePointer, latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState)
        // we need to use LatinIME for handling of key-down audio and haptics
        latinIME.hapticAndAudioFeedback(primaryCode, repeatCount, hapticEvent)
    }

    override fun onLongPressKey(primaryCode: Int) {
        metaOnLongPressKey(primaryCode)
        performHapticFeedback(HapticEvent.KEY_LONG_PRESS)
    }

    override fun onReleaseKey(primaryCode: Int, withSliding: Boolean) {
        metaOnReleaseKey(primaryCode)
        keyboardSwitcher.onReleaseKey(primaryCode, withSliding, latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState)
    }

    override fun onKeyUp(keyCode: Int, keyEvent: KeyEvent): Boolean {
        emojiAltPhysicalKeyDetector.onKeyUp(keyEvent)
        if (!ProductionFlags.IS_HARDWARE_KEYBOARD_SUPPORTED)
            return false

        return false
    }

    override fun onKeyDown(keyCode: Int, keyEvent: KeyEvent): Boolean {
        emojiAltPhysicalKeyDetector.onKeyDown(keyEvent)
        if (!ProductionFlags.IS_HARDWARE_KEYBOARD_SUPPORTED)
            return false

        val mode = settings.current.mPhysicalKeyboardSuggestionShortcuts
        if (mode != "disabled" && keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_9) {
            val visualPos = keyCode - KeyEvent.KEYCODE_1
            val isMatchingTrigger = when (mode) {
                "alt" -> keyEvent.isAltPressed && !keyEvent.isCtrlPressed
                "ctrl" -> keyEvent.isCtrlPressed && !keyEvent.isAltPressed
                "number" -> inputLogic.isComposingWord || (keyboardSwitcher.suggestionStripView?.visibility == android.view.View.VISIBLE)
                else -> false
            }
            if (isMatchingTrigger) {
                val picked = keyboardSwitcher.suggestionStripView?.pickSuggestionByVisualPosition(visualPos) ?: false
                if (picked) return true
            }
        }

        val event: Event
        if (settings.current.mLocale.language == "ko") { // todo: this does not appear to be the right place
            val subtype = keyboardSwitcher.keyboard?.mId?.mSubtype ?: RichInputMethodManager.getInstance().currentSubtype
            event = HangulEventDecoder.decodeHardwareKeyEvent(subtype, keyEvent) {
                getHardwareKeyEventDecoder(keyEvent.deviceId).decodeHardwareKey(keyEvent)
            }
        } else {
            event = getHardwareKeyEventDecoder(keyEvent.deviceId).decodeHardwareKey(keyEvent)
        }

        if (event.isHandled) {
            inputLogic.onCodeInput(
                settings.current, event,
                keyboardSwitcher.getKeyboardShiftMode(), // TODO: this is not necessarily correct for a hardware keyboard right now
                latinIME.mHandler
            )
            return true
        }
        return false
    }

    override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) {
        val isArrow = primaryCode == KeyCode.ARROW_LEFT || primaryCode == KeyCode.ARROW_RIGHT || primaryCode == KeyCode.ARROW_UP || primaryCode == KeyCode.ARROW_DOWN
        if (isArrow) {
            val isSelecting = keyboardSwitcher.keyboard?.mId?.isAlphabetShiftedManually == true || sPersistentSelectionModeActive
            if (isSelecting) {
                val androidKeyCode = when (primaryCode) {
                    KeyCode.ARROW_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                    KeyCode.ARROW_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
                    KeyCode.ARROW_UP -> KeyEvent.KEYCODE_DPAD_UP
                    KeyCode.ARROW_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
                    else -> 0
                }
                if (androidKeyCode != 0) {
                    val eventTime = android.os.SystemClock.uptimeMillis()
                    connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0))
                    connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, androidKeyCode, 0, KeyEvent.META_SHIFT_ON))
                    connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, androidKeyCode, 0, KeyEvent.META_SHIFT_ON))
                    connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0))
                }
                return
            }
        }
        when (primaryCode) {
            KeyCode.TOGGLE_SELECTION_MODE -> {
                sPersistentSelectionModeActive = !sPersistentSelectionModeActive
                keyboardSwitcher.mainKeyboardView?.invalidateAllKeys()
                keyboardSwitcher.suggestionStripView?.findViewById<android.view.ViewGroup>(R.id.toolbar)?.let {
                    helium314.keyboard.latin.utils.setToolbarButtonsActivatedStateOnPrefChange(it, Settings.PREF_AUTO_CORRECTION)
                }
                return
            }
            KeyCode.ALPHA -> {
                sPersistentTextEditModeActive = false
                sPersistentSelectionModeActive = false
                keyboardSwitcher.hideTextEditView()
            }
            KeyCode.HANDWRITING -> {
                if (keyboardSwitcher.isHandwritingShowing) {
                    keyboardSwitcher.setAlphabetKeyboard()
                } else {
                    keyboardSwitcher.setHandwritingKeyboard()
                }
                return
            }
            KeyCode.OCR -> {
                if (keyboardSwitcher.isOcrShowing) {
                    keyboardSwitcher.hideOcrPanels()
                } else {
                    keyboardSwitcher.showOcrCamera()
                }
                return
            }
            KeyCode.TOGGLE_AUTOCORRECT -> return settings.toggleAutoCorrect()
            KeyCode.TOGGLE_INCOGNITO_MODE -> {
                settings.toggleAlwaysIncognitoMode()
                // Invalidate keyboard to update spacebar incognito icon immediately
                keyboardSwitcher.mainKeyboardView?.invalidateAllKeys()
                return
            }
            KeyCode.TOGGLE_TOUCHPAD_MODE -> {
                PointerTracker.sPersistentTouchpadModeActive = !PointerTracker.sPersistentTouchpadModeActive
                if (PointerTracker.sPersistentTouchpadModeActive) {
                    sPersistentTextEditModeActive = false
                    keyboardSwitcher.hideTextEditView()
                    
                    val touchpadView = keyboardSwitcher.touchpadView
                    if (touchpadView != null) {
                        setupTouchpadListener(touchpadView)
                        keyboardSwitcher.showTouchpadView()
                    }
                } else {
                    keyboardSwitcher.hideTouchpadView()
                }
                return
            }
            KeyCode.TOGGLE_TEXT_EDIT_MODE -> {
                sPersistentTextEditModeActive = !sPersistentTextEditModeActive
                if (sPersistentTextEditModeActive) {
                    PointerTracker.sPersistentTouchpadModeActive = false
                    keyboardSwitcher.hideTouchpadView()
                    keyboardSwitcher.showTextEditView()
                } else {
                    keyboardSwitcher.hideTextEditView()
                }
                return
            }
            KeyCode.FORWARD_DELETE -> {
                val connection = inputLogic.connection
                val eventTime = android.os.SystemClock.uptimeMillis()
                connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL, 0, 0))
                connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_FORWARD_DEL, 0, 0))
                return
            }
            KeyCode.SHIFT -> {
                if (keyboardSwitcher.keyboard?.mId?.mElementId == KeyboardId.ELEMENT_TEXT_EDIT || sPersistentTextEditModeActive) {
                    if (inputLogic.connection.hasSelection()) {
                        inputLogic.onCodeInput(settings.current, Event.createSoftwareKeypressEvent(KeyCode.SHIFT, 0, 0, 0, false), keyboardSwitcher.getKeyboardShiftMode(), latinIME.mHandler)
                    } else {
                        sPersistentSelectionModeActive = !sPersistentSelectionModeActive
                        keyboardSwitcher.mainKeyboardView?.invalidateAllKeys()
                    }
                    return
                }
            }
            KeyCode.CAPS_LOCK -> {
                if (keyboardSwitcher.keyboard?.mId?.mElementId == KeyboardId.ELEMENT_TEXT_EDIT || sPersistentTextEditModeActive) {
                    if (inputLogic.connection.hasSelection()) {
                        inputLogic.onCodeInput(settings.current, Event.createSoftwareKeypressEvent(KeyCode.SHIFT, 0, 0, 0, false), keyboardSwitcher.getKeyboardShiftMode(), latinIME.mHandler)
                    } else {
                        sPersistentSelectionModeActive = true
                        keyboardSwitcher.mainKeyboardView?.invalidateAllKeys()
                    }
                    return
                }
            }
            KeyCode.DELETE_WORD -> {
                val connection = inputLogic.connection
                val eventTime = android.os.SystemClock.uptimeMillis()
                connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0, KeyEvent.META_CTRL_ON))
                connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0, KeyEvent.META_CTRL_ON))
                return
            }
            KeyCode.FORWARD_DELETE_WORD -> {
                val connection = inputLogic.connection
                val eventTime = android.os.SystemClock.uptimeMillis()
                connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL, 0, KeyEvent.META_CTRL_ON))
                connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_FORWARD_DEL, 0, KeyEvent.META_CTRL_ON))
                return
            }
            KeyCode.CLIPBOARD_COPY_ALL -> {
                inputLogic.onCodeInput(settings.current, Event.createSoftwareKeypressEvent(KeyCode.CLIPBOARD_SELECT_ALL, 0, 0, 0, false), keyboardSwitcher.getKeyboardShiftMode(), latinIME.mHandler)
                inputLogic.onCodeInput(settings.current, Event.createSoftwareKeypressEvent(KeyCode.CLIPBOARD_COPY, 0, 0, 0, false), keyboardSwitcher.getKeyboardShiftMode(), latinIME.mHandler)
                return
            }
        }
        val mkv = keyboardSwitcher.mainKeyboardView

        val isEditingNav = primaryCode == KeyCode.WORD_LEFT || primaryCode == KeyCode.WORD_RIGHT
                || primaryCode == KeyCode.MOVE_START_OF_PAGE || primaryCode == KeyCode.MOVE_END_OF_PAGE
                || primaryCode == KeyCode.MOVE_START_OF_LINE || primaryCode == KeyCode.MOVE_END_OF_LINE
                || primaryCode == KeyCode.PAGE_UP || primaryCode == KeyCode.PAGE_DOWN
        val eventMetaState = if (isEditingNav && (keyboardSwitcher.keyboard?.mId?.isAlphabetShiftedManually == true || sPersistentSelectionModeActive)) {
            metaState or KeyEvent.META_SHIFT_ON
        } else {
            metaState
        }

        // checking if the character is a combining accent
        val event = if (primaryCode in combiningRange) { // todo: should this be done later, maybe in inputLogic?
            Event.createSoftwareDeadEvent(primaryCode, 0, eventMetaState, mkv.getKeyX(x), mkv.getKeyY(y), null)
        } else {
            Event.createSoftwareKeypressEvent(primaryCode, eventMetaState, mkv.getKeyX(x), mkv.getKeyY(y), isKeyRepeat)
        }
        latinIME.onEvent(event)
        metaAfterCodeInput(primaryCode)
    }

    override fun onTextInput(text: String?) = latinIME.onTextInput(text)

    override fun onImageSelected(imageUri: String) = latinIME.onImageSelected(imageUri)

    override fun onStartBatchInput() = latinIME.onStartBatchInput()

    override fun onUpdateBatchInput(batchPointers: InputPointers) = latinIME.onUpdateBatchInput(batchPointers)

    override fun onEndBatchInput(batchPointers: InputPointers) = latinIME.onEndBatchInput(batchPointers)

    override fun onCancelBatchInput() = latinIME.onCancelBatchInput()

    // User released a finger outside any key
    override fun onCancelInput() { }

    override fun onFinishSlidingInput() =
        keyboardSwitcher.onFinishSlidingInput(latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState)

    override fun onCustomRequest(requestCode: Int): Boolean {
        if (requestCode == Constants.CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER) {
            return latinIME.showInputPickerDialog()
        }
        if (requestCode == KeyboardActionListener.CODE_TOUCHPAD_ON) {
            keyboardSwitcher.getMainKeyboardView()?.alpha = 0.5f
            return true
        }
        if (requestCode == KeyboardActionListener.CODE_TOUCHPAD_OFF) {
            keyboardSwitcher.getMainKeyboardView()?.alpha = 1.0f
            return true
        }
        return false
    }

    override fun onHorizontalSpaceSwipe(steps: Int): Boolean = when (Settings.getValues().mSpaceSwipeHorizontal) {
        KeyboardActionListener.SWIPE_MOVE_CURSOR -> onMoveCursorHorizontally(steps)
        KeyboardActionListener.SWIPE_SWITCH_LANGUAGE -> onLanguageSlide(steps)
        KeyboardActionListener.SWIPE_TOGGLE_NUMPAD -> toggleNumpad(false, false)
        else -> false
    }

    override fun onVerticalSpaceSwipe(steps: Int): Boolean = when (Settings.getValues().mSpaceSwipeVertical) {
        KeyboardActionListener.SWIPE_MOVE_CURSOR -> onMoveCursorVertically(steps)
        KeyboardActionListener.SWIPE_SWITCH_LANGUAGE -> onLanguageSlide(steps)
        KeyboardActionListener.SWIPE_TOGGLE_NUMPAD -> toggleNumpad(false, false)
        KeyboardActionListener.SWIPE_HIDE_KEYBOARD -> {
            latinIME.requestHideSelf(0)
            true
        }
        KeyboardActionListener.SWIPE_TOUCHPAD_MODE -> {
            // Activate touchpad mode - the actual cursor movement will be handled in PointerTracker
            PointerTracker.setTouchpadModeActive(true)
            true
        }
        else -> false
    }

    override fun onEndSpaceSwipe(){
        initialSubtype = null
        subtypeSwitchCount = 0
        if (isSpaceSwipeActive) {
            isSpaceSwipeActive = false
            inputLogic.restartSuggestionsOnWordTouchedByCursor(settings.current)
        }
    }

    override fun toggleNumpad(withSliding: Boolean, forceReturnToAlpha: Boolean): Boolean {
        keyboardSwitcher.toggleNumpad(withSliding, latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState, forceReturnToAlpha)
        return true
    }

    override fun onMoveDeletePointer(steps: Int) {
        inputLogic.finishInput()
        val end = connection.expectedSelectionEnd
        val actualSteps = actualSteps(steps)
        val start = connection.expectedSelectionStart + actualSteps
        if (start > end) return
        gestureMoveBackHaptics()
        connection.setSelection(start, end)
    }

    private fun actualSteps(steps: Int): Int {
        var actualSteps = 0
        // corrected steps to avoid splitting chars belonging to the same codepoint
        if (steps > 0) {
            val text = connection.getSelectedText(0) ?: return steps
            loopOverCodePoints(text) { cp, charCount ->
                actualSteps += charCount
                actualSteps >= steps
            }
        } else {
            val text = connection.getTextBeforeCursor(-steps * 4, 0) ?: return steps
            loopOverCodePointsBackwards(text) { cp, charCount ->
                actualSteps -= charCount
                actualSteps <= steps
            }
        }
        return actualSteps
    }

    override fun onUpWithDeletePointerActive() {
        if (!connection.hasSelection()) return
        inputLogic.finishInput()
        onCodeInput(KeyCode.DELETE, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        keyboardSwitcher.requestUpdatingShiftState(
            inputLogic.getCurrentAutoCapsState(settings.current),
            inputLogic.getCurrentRecapitalizeState()
        )
    }

    override fun resetMetaState() {
        metaState = 0
    }

    private fun onLanguageSlide(steps: Int): Boolean {
        if (abs(steps) < settings.current.mLanguageSwipeDistance) return false
        val subtypes = SubtypeSettings.getEnabledSubtypes(true)
        if (subtypes.size <= 1) { // only allow if we have more than one subtype
            return false
        }
        // decide next or previous dependent on up or down
        val current = RichInputMethodManager.getInstance().currentSubtype.rawSubtype
        var wantedIndex = subtypes.indexOf(current) + if (steps > 0) 1 else -1
        wantedIndex %= subtypes.size
        if (wantedIndex < 0) {
            wantedIndex += subtypes.size
        }
        val newSubtype = subtypes[wantedIndex]

        // do not switch if we would switch to the initial subtype after cycling all other subtypes
        if (initialSubtype == null) initialSubtype = current
        if (initialSubtype == newSubtype) {
            if ((subtypeSwitchCount > 0 && steps > 0) || (subtypeSwitchCount < 0 && steps < 0)) {
                return true
            }
        }
        if (steps > 0) subtypeSwitchCount++ else subtypeSwitchCount--

        keyboardSwitcher.switchToSubtype(newSubtype)
        return true
    }

    private fun onMoveCursorVertically(steps: Int): Boolean {
        if (steps == 0) return false
        val code = if (steps < 0) {
            gestureMoveBackHaptics()
            KeyCode.ARROW_UP
        } else {
            gestureMoveForwardHaptics()
            KeyCode.ARROW_DOWN
        }
        onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        return true
    }

    private fun onMoveCursorHorizontally(rawSteps: Int): Boolean {
        if (rawSteps == 0) return false
        // for RTL languages we want to invert pointer movement
        val rtl = RichInputMethodManager.getInstance().currentSubtype.isRtlSubtype
        val steps = if (rtl) -rawSteps else rawSteps

        if (!isSpaceSwipeActive) {
            isSpaceSwipeActive = true
            inputLogic.finishInput()
        }

        val code = if (steps < 0) {
            gestureMoveBackHaptics()
            if (rtl) KeyCode.ARROW_RIGHT else KeyCode.ARROW_LEFT
        } else {
            gestureMoveForwardHaptics(true)
            if (rtl) KeyCode.ARROW_LEFT else KeyCode.ARROW_RIGHT
        }
        repeat(abs(steps)) {
            onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        }
        return true
    }

    private fun gestureMoveBackHaptics() {
        if (connection.canDeleteCharacters()) {
            performHapticFeedback(HapticEvent.GESTURE_MOVE)
        }
    }

    // hasTextAfterCursor is used because text before the cursor is cached, going through the InputConnection can be slow
    private fun gestureMoveForwardHaptics(hasTextAfterCursor: Boolean? = null) {
        if (hasTextAfterCursor ?: connection.hasTextAfterCursor()) {
            performHapticFeedback(HapticEvent.GESTURE_MOVE)
        }
    }

    private fun performHapticFeedback(hapticEvent: HapticEvent) {
        audioAndHapticFeedbackManager.performHapticFeedback(keyboardSwitcher.visibleKeyboardView, hapticEvent)
    }

    private fun getHardwareKeyEventDecoder(deviceId: Int): HardwareEventDecoder {
        hardwareEventDecoders.get(deviceId)?.let { return it }

        // TODO: create the decoder according to the specification
        val newDecoder = HardwareKeyboardEventDecoder(deviceId)
        hardwareEventDecoders.put(deviceId, newDecoder)
        return newDecoder
    }

    // -------------------------- meta state handling -----------------------------

    // current state
    // press enables meta
    // release keeps meta enabled, unless there was a onCodeInput for a different key in between
    // onCodeInput ends the meta if it was enabled
    // long press on meta key also ends meta so popups are handled properly
    // sliding from a meta key to some other words too, though this was not intended (and there are no sliding key input graphics)

    // todo: move meta state tracking to KeyboardState? seems more suitable, also for handling sliding input
    //  but the issue is that meta state is used in Event to determine whether it's a functional Event (does not add a character)
    //  (and also it's in the hardware keyEvents which are handled by onKeyUp/Down, but that should be manageable)

    /** actual Android metaState like in KeyEvent */
    private var metaState = 0

    /** keeps track of the state of meta keys by (HeliBoard) KeyCodes */
    private val metaPressStates = SparseArray<MetaPressState>(4)

    // todo: lock and non-lock versions interact badly: when any of them is released, the meta state is removed
    //  this is not wanted, especially because the state of the other key is not affected (still looks pressed)
    private fun metaOnPressKey(primaryCode: Int) {
        val metaCode = primaryCode.toMetaState() ?: return
        if (primaryCode.isMetaLock()) {
            // if unset -> lock, otherwise set to UNSET_ON_RELEASE so it's unset on release
            if (metaPressStates[primaryCode] != MetaPressState.LOCKED) {
                metaPressStates[primaryCode] = MetaPressState.LOCKED
                keyboardSwitcher.mainKeyboardView?.updateLockState(primaryCode, true)
                metaState = metaState or metaCode
            } else {
                metaPressStates[primaryCode] = MetaPressState.UNSET_ON_RELEASE
            }
            return
        }
        if (metaPressStates[primaryCode] == MetaPressState.RELEASED_BUT_ACTIVE) {
            // meta key is pressed again without other input -> should be disabled on release
            metaPressStates[primaryCode] = MetaPressState.UNSET_ON_RELEASE
        } else {
            // otherwise just press it normally
            metaPressStates[primaryCode] = MetaPressState.PRESSED
        }
        metaState = metaState or metaCode
        // pressed graphics are set anyway, no need to lock it
    }

    // looks like this is not called if there are no popups
    private fun metaOnLongPressKey(primaryCode: Int) {
        if (metaPressStates[primaryCode] != MetaPressState.PRESSED) return
        // we long-pressed a meta key that has popups -> disable so the meta state is not used for the popup
        metaPressStates[primaryCode] = MetaPressState.UNSET
        keyboardSwitcher.mainKeyboardView?.updateLockState(primaryCode, false)
        val metaCode = primaryCode.toMetaState() ?: return
        metaState = metaState and metaCode.inv()
    }

    private fun metaOnReleaseKey(primaryCode: Int) {
        val metaCode = primaryCode.toMetaState() ?: return
        val metaPressState = metaPressStates[primaryCode]
        if (metaPressState == MetaPressState.UNSET_ON_RELEASE) {
            metaPressStates[primaryCode] = MetaPressState.UNSET
            metaState = metaState and metaCode.inv()
            keyboardSwitcher.mainKeyboardView?.updateLockState(primaryCode, false)
        } else if (metaPressState == MetaPressState.PRESSED) {
            metaPressStates[primaryCode] = MetaPressState.RELEASED_BUT_ACTIVE
            keyboardSwitcher.mainKeyboardView?.updateLockState(primaryCode, true)
        }
    }

    private fun metaAfterCodeInput(primaryCode: Int) {
        val metaCode = primaryCode.toMetaState()
        if (metaCode != null) {
            // meta key might be a popup key, we just toggle between set and unset
            val metaPressState = metaPressStates[primaryCode] ?: MetaPressState.UNSET
            if (metaPressState == MetaPressState.UNSET) {
                metaPressStates[primaryCode] = MetaPressState.SET
                metaState = metaState or metaCode
                keyboardSwitcher.mainKeyboardView?.updateLockState(primaryCode, true)
            } else if (metaPressState == MetaPressState.SET) {
                metaPressStates[primaryCode] = MetaPressState.UNSET
                metaState = metaState and metaCode.inv()
                keyboardSwitcher.mainKeyboardView?.updateLockState(primaryCode, false)
            }
        } else if (metaState != 0) {
            // non-meta key -> unset all set / released_but_active, and mark pressed as UNSET_ON_RELEASE
            metaPressStates.forEach { key, value ->
                if (value == MetaPressState.RELEASED_BUT_ACTIVE || value == MetaPressState.SET) {
                    metaPressStates[key] = MetaPressState.UNSET
                    keyboardSwitcher.mainKeyboardView?.updateLockState(key, false)
                    val metaCode = key.toMetaState() ?: return@forEach
                    metaState = metaState and metaCode.inv()
                } else if (value == MetaPressState.PRESSED) {
                    metaPressStates[key] = MetaPressState.UNSET_ON_RELEASE
                }
            }
        }
    }

    fun setupTouchpadListener(touchpadView: TouchpadView) {
        touchpadView.setTouchpadListener(object : TouchpadView.TouchpadListener {
            override fun onCursorMove(keyCode: Int, isSelecting: Boolean) {
                if (isSelecting) {
                    val androidKeyCode = when (keyCode) {
                        KeyCode.ARROW_UP -> KeyEvent.KEYCODE_DPAD_UP
                        KeyCode.ARROW_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
                        KeyCode.ARROW_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                        KeyCode.ARROW_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
                        else -> 0
                    }
                    if (androidKeyCode != 0) {
                        val eventTime = android.os.SystemClock.uptimeMillis()
                        // Send SHIFT down to force selection mode at the InputConnection level
                        connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0))
                        
                        connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, androidKeyCode, 0, KeyEvent.META_SHIFT_ON))
                        connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, androidKeyCode, 0, KeyEvent.META_SHIFT_ON))
                        
                        // Release SHIFT
                        connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0))
                    }
                } else {
                    onCodeInput(keyCode, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                }
            }
            override fun onSingleTap() {
                onCodeInput(Constants.CODE_SPACE, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
            }
            override fun onDoubleTap() {
                onCodeInput(KeyCode.CLIPBOARD_SELECT_WORD, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
            }
            override fun onScroll(direction: Int) {
                onCodeInput(direction, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
            }
            override fun onTwoFingerDoubleTap() {
                if (connection.hasSelection()) {
                    onCodeInput(KeyCode.CLIPBOARD_COPY, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                } else {
                    onCodeInput(KeyCode.CLIPBOARD_PASTE, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                }
            }
            override fun onThreeFingerTap() {
                onCodeInput(KeyCode.CLIPBOARD_PASTE, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
            }
            override fun onThreeFingerDoubleTap() {
                if (connection.hasSelection()) {
                    onCodeInput(KeyCode.CLIPBOARD_CUT, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                } else {
                    onCodeInput(KeyCode.CLIPBOARD_SELECT_ALL, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                }
            }
            override fun onThreeFingerSwipeLeft() {
                if (connection.hasSelection()) {
                    onCodeInput(KeyCode.DELETE, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                } else {
                    onCodeInput(KeyCode.CLIPBOARD_SELECT_WORD, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                    onCodeInput(KeyCode.DELETE, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                }
            }
            override fun onThreeFingerSwipeRight() {
                // Empty for future use
            }
            override fun onThreeFingerSwipeUp() {
                onCodeInput(KeyCode.UNDO, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
            }
            override fun onThreeFingerSwipeDown() {
                onCodeInput(KeyCode.REDO, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
            }
            override fun onClose() {
                PointerTracker.sPersistentTouchpadModeActive = false
                keyboardSwitcher.hideTouchpadView()
            }
        })
    }



    companion object {
        @JvmField
        var sPersistentTextEditModeActive = false
        @JvmField
        var sPersistentSelectionModeActive = false
        private enum class MetaPressState {
            UNSET, // default state, not active
            SET, // enabled without onPressKey (e.g. in popup)
            PRESSED, // key is pressed
            UNSET_ON_RELEASE, // key is pressed, but state will be unset on release
            RELEASED_BUT_ACTIVE, // key was released without UNSET_ON_RELEASE state, meta state is still set
            LOCKED, // key is locked and will be released only by pressing the same key again
        }

        private fun Int.toMetaState() = when (this) {
            KeyCode.CTRL, KeyCode.CTRL_LOCK -> KeyEvent.META_CTRL_ON
            KeyCode.CTRL_LEFT               -> KeyEvent.META_CTRL_LEFT_ON
            KeyCode.CTRL_RIGHT              -> KeyEvent.META_CTRL_RIGHT_ON
            KeyCode.ALT, KeyCode.ALT_LOCK   -> KeyEvent.META_ALT_ON
            KeyCode.ALT_LEFT                -> KeyEvent.META_ALT_LEFT_ON
            KeyCode.ALT_RIGHT               -> KeyEvent.META_ALT_RIGHT_ON
            KeyCode.FN, KeyCode.FN_LOCK     -> KeyEvent.META_FUNCTION_ON
            KeyCode.META, KeyCode.META_LOCK -> KeyEvent.META_META_ON
            KeyCode.META_LEFT               -> KeyEvent.META_META_LEFT_ON
            KeyCode.META_RIGHT              -> KeyEvent.META_META_RIGHT_ON
            else -> null
        }

        private fun Int.isMetaLock() = this == KeyCode.CTRL_LOCK || this == KeyCode.ALT_LOCK || this == KeyCode.FN_LOCK || this == KeyCode.META_LOCK
    }
}
