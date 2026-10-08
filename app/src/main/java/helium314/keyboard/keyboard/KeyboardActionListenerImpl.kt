// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import android.os.SystemClock
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
import helium314.keyboard.event.PhysicalKeyboardLayouts
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.EmojiAltPhysicalKeyDetector
import helium314.keyboard.latin.LastComposedWord
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
import helium314.keyboard.latin.settings.Defaults
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
    private var cursorMoveStepCount = 0

    override fun onPressKey(primaryCode: Int, repeatCount: Int, isSinglePointer: Boolean, hapticEvent: HapticEvent) {
        metaOnPressKey(primaryCode)
        if (primaryCode == KeyCode.SHIFT) inputLogic.onShiftKeyPressed()
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

    private val mConsumedPhysicalKeys = HashSet<Int>()
    private val mRemappedShortcutKeys = HashMap<Int, Int>()

    private fun isUnhandledNavigationKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_MOVE_HOME,
        KeyEvent.KEYCODE_MOVE_END,
        KeyEvent.KEYCODE_TAB,
        KeyEvent.KEYCODE_FORWARD_DEL -> true
        else -> false
    }

    override fun onKeyUp(keyCode: Int, keyEvent: KeyEvent): Boolean {
        emojiAltPhysicalKeyDetector.onKeyUp(keyEvent)
        if (!ProductionFlags.IS_HARDWARE_KEYBOARD_SUPPORTED)
            return false

        val remappedCode = mRemappedShortcutKeys.remove(keyCode)
        if (remappedCode != null) {
            val eventTime = SystemClock.uptimeMillis()
            connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, remappedCode, keyEvent.repeatCount, keyEvent.metaState, keyEvent.deviceId, keyEvent.scanCode, keyEvent.flags, keyEvent.source))
            return true
        }

        if (mConsumedPhysicalKeys.remove(keyCode)) {
            return true
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, keyEvent: KeyEvent): Boolean {
        emojiAltPhysicalKeyDetector.onKeyDown(keyEvent)
        if (!ProductionFlags.IS_HARDWARE_KEYBOARD_SUPPORTED)
            return false

        if (keyboardSwitcher.isShowingEmojiPalettes) {
            val emojiPalettesView = keyboardSwitcher.emojiPalettesView
            if (emojiPalettesView != null && emojiPalettesView.onHardwareKeyEvent(keyCode, keyEvent)) {
                mConsumedPhysicalKeys.add(keyCode)
                return true
            }
        }

        if (isUnhandledNavigationKey(keyCode) && inputLogic.isComposingWord) {
            inputLogic.finishInput()
        }

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
                if (picked) {
                    mConsumedPhysicalKeys.add(keyCode)
                    return true
                }
            }
        }

        val physicalLayoutPref = settings.current.mPhysicalKeyboardLayout
        val targetLayoutName = if (physicalLayoutPref == Defaults.PREF_PHYSICAL_KEYBOARD_LAYOUT) {
            val subtype = keyboardSwitcher.keyboard?.mId?.mSubtype ?: RichInputMethodManager.getInstance().currentSubtype
            subtype.mainLayoutName
        } else if (physicalLayoutPref == "system_default") {
            null
        } else {
            physicalLayoutPref
        }

        if (targetLayoutName != null && keyEvent.isCtrlPressed && keyCode != KeyEvent.KEYCODE_SPACE) {
            val remappedCode = PhysicalKeyboardLayouts.remapKeyCodeForShortcuts(keyCode, targetLayoutName)
            if (remappedCode != keyCode) {
                mRemappedShortcutKeys[keyCode] = remappedCode
                val eventTime = SystemClock.uptimeMillis()
                connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, remappedCode, keyEvent.repeatCount, keyEvent.metaState, keyEvent.deviceId, keyEvent.scanCode, keyEvent.flags, keyEvent.source))
                return true
            }
        }

        val event: Event
        if (settings.current.mLocale.language == "ko") { // todo: this does not appear to be the right place
            val subtype = keyboardSwitcher.keyboard?.mId?.mSubtype ?: RichInputMethodManager.getInstance().currentSubtype
            event = HangulEventDecoder.decodeHardwareKeyEvent(subtype, keyEvent) {
                getHardwareKeyEventDecoder(keyEvent.deviceId).decodeHardwareKey(keyEvent, targetLayoutName)
            }
        } else {
            event = getHardwareKeyEventDecoder(keyEvent.deviceId).decodeHardwareKey(keyEvent, targetLayoutName)
        }

        if (event.isHandled) {
            latinIME.onEvent(event)
            mConsumedPhysicalKeys.add(keyCode)
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
                if (sPersistentSelectionModeActive || connection.hasSelection()) {
                    val hadSelection = connection.hasSelection()
                    val selEnd = maxOf(connection.expectedSelectionStart, connection.expectedSelectionEnd)
                    sPersistentSelectionModeActive = false
                    if (hadSelection && selEnd >= 0) {
                        connection.setSelection(selEnd, selEnd)
                    }
                } else {
                    sPersistentSelectionModeActive = true
                }
                keyboardSwitcher.mainKeyboardView?.invalidateAllKeys()
                keyboardSwitcher.suggestionStripView?.updateToolbarButtonsActivatedState()
                return
            }
            KeyCode.ALPHA -> {
                sPersistentTextEditModeActive = false
                sPersistentSelectionModeActive = false
                keyboardSwitcher.hideTextEditView()
                keyboardSwitcher.suggestionStripView?.updateToolbarButtonsActivatedState()
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
            KeyCode.GIF -> {
                if (keyboardSwitcher.isGifPickerShowing) {
                    keyboardSwitcher.setAlphabetKeyboard()
                } else {
                    keyboardSwitcher.setGifPickerKeyboard()
                }
                return
            }
            KeyCode.TOGGLE_AUTOCORRECT -> return settings.toggleAutoCorrect()
            KeyCode.TOGGLE_AUTO_CAP, KeyCode.TOGGLE_FORCE_AUTO_CAPS -> {
                if (primaryCode == KeyCode.TOGGLE_AUTO_CAP) settings.toggleAutoCapitalization()
                else settings.toggleForceAutoCapitalization()
                // An automatic shift refresh must not consume an explicit one-shot Shift.
                if (keyboardSwitcher.keyboard?.mId?.isAlphabetShiftedManually != true) {
                    keyboardSwitcher.requestUpdatingShiftState(latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState)
                }
                keyboardSwitcher.suggestionStripView?.updateToolbarButtonsActivatedState()
                return
            }
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
                if (inputLogic.isComposingWord) {
                    inputLogic.finishInput()
                }
                val connection = inputLogic.connection
                val hadSelection = connection.hasSelection()
                if (hadSelection) {
                    connection.commitText("", 1)
                    deactivateSelectionMode()
                } else if (connection.hasTextAfterCursor()) {
                    connection.deleteSurroundingText(0, 1)
                } else {
                    val eventTime = android.os.SystemClock.uptimeMillis()
                    connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL, 0, 0))
                    connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_FORWARD_DEL, 0, 0))
                }
                return
            }
            KeyCode.SHIFT -> {
                if (keyboardSwitcher.keyboard?.mId?.mElementId == KeyboardId.ELEMENT_TEXT_EDIT || sPersistentTextEditModeActive) {
                    if (inputLogic.connection.hasSelection()) {
                        inputLogic.onCodeInput(settings.current, Event.createSoftwareKeypressEvent(KeyCode.SHIFT, 0, 0, 0, false), keyboardSwitcher.keyboardShiftMode, latinIME.mHandler)
                    } else {
                        sPersistentSelectionModeActive = !sPersistentSelectionModeActive
                        keyboardSwitcher.mainKeyboardView?.invalidateAllKeys()
                        keyboardSwitcher.suggestionStripView?.updateToolbarButtonsActivatedState()
                    }
                    return
                }
            }
            KeyCode.CAPS_LOCK -> {
                if (keyboardSwitcher.keyboard?.mId?.mElementId == KeyboardId.ELEMENT_TEXT_EDIT || sPersistentTextEditModeActive) {
                    if (inputLogic.connection.hasSelection()) {
                        inputLogic.onCodeInput(settings.current, Event.createSoftwareKeypressEvent(KeyCode.SHIFT, 0, 0, 0, false), keyboardSwitcher.keyboardShiftMode, latinIME.mHandler)
                    } else {
                        sPersistentSelectionModeActive = true
                        keyboardSwitcher.mainKeyboardView?.invalidateAllKeys()
                        keyboardSwitcher.suggestionStripView?.updateToolbarButtonsActivatedState()
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
                if (inputLogic.isComposingWord) {
                    inputLogic.finishInput()
                }
                val connection = inputLogic.connection
                val hadSelection = connection.hasSelection()
                if (hadSelection) {
                    connection.commitText("", 1)
                    deactivateSelectionMode()
                } else {
                    val eventTime = android.os.SystemClock.uptimeMillis()
                    connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL, 0, KeyEvent.META_CTRL_ON))
                    connection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_FORWARD_DEL, 0, KeyEvent.META_CTRL_ON))
                }
                return
            }
            KeyCode.CLIPBOARD_COPY_ALL -> {
                inputLogic.onCodeInput(settings.current, Event.createSoftwareKeypressEvent(KeyCode.CLIPBOARD_SELECT_ALL, 0, 0, 0, false), keyboardSwitcher.keyboardShiftMode, latinIME.mHandler)
                inputLogic.onCodeInput(settings.current, Event.createSoftwareKeypressEvent(KeyCode.CLIPBOARD_COPY, 0, 0, 0, false), keyboardSwitcher.keyboardShiftMode, latinIME.mHandler)
                return
            }
        }
        val mkv = keyboardSwitcher.mainKeyboardView
        val keyX = mkv?.getKeyX(x) ?: x
        val keyY = mkv?.getKeyY(y) ?: y

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
            Event.createSoftwareDeadEvent(primaryCode, 0, eventMetaState, keyX, keyY, null)
        } else {
            Event.createSoftwareKeypressEvent(primaryCode, eventMetaState, keyX, keyY, isKeyRepeat)
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
            isSpaceSwipeActive = true
            latinIME.isCursorGestureActive = true
            latinIME.mHandler.cancelResumeSuggestions()
            inputLogic.finishInput()
            keyboardSwitcher.mainKeyboardView?.alpha = 0.5f
            return true
        }
        if (requestCode == KeyboardActionListener.CODE_TOUCHPAD_OFF) {
            isSpaceSwipeActive = false
            latinIME.isCursorGestureActive = false
            inputLogic.restartSuggestionsOnWordTouchedByCursor(settings.current)
            keyboardSwitcher.mainKeyboardView?.alpha = 1.0f
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

    override fun onVerticalSpaceSwipe(steps: Int, action: Int): Boolean = when (action) {
        KeyboardActionListener.SWIPE_MOVE_CURSOR -> onMoveCursorVertically(steps)
        KeyboardActionListener.SWIPE_SWITCH_LANGUAGE -> onLanguageSlide(steps, isVertical = true)
        KeyboardActionListener.SWIPE_TOGGLE_NUMPAD -> toggleNumpad(false, false)
        KeyboardActionListener.SWIPE_HIDE_KEYBOARD -> {
            latinIME.requestHideSelf(0)
            true
        }
        KeyboardActionListener.SWIPE_TOUCHPAD_MODE -> {
            // Activate touchpad mode - the actual cursor movement will be handled in PointerTracker
            isSpaceSwipeActive = true
            latinIME.isCursorGestureActive = true
            latinIME.mHandler.cancelResumeSuggestions()
            inputLogic.finishInput()
            PointerTracker.setTouchpadModeActive(true)
            true
        }
        else -> false
    }

    override fun onEndSpaceSwipe(){
        initialSubtype = null
        subtypeSwitchCount = 0
        cursorMoveStepCount = 0
        if (isSpaceSwipeActive || latinIME.isCursorGestureActive) {
            isSpaceSwipeActive = false
            latinIME.isCursorGestureActive = false
            inputLogic.restartSuggestionsOnWordTouchedByCursor(settings.current)
        }
    }

    override fun toggleNumpad(withSliding: Boolean, forceReturnToAlpha: Boolean): Boolean {
        keyboardSwitcher.toggleNumpad(withSliding, latinIME.currentAutoCapsState, latinIME.currentRecapitalizeState, forceReturnToAlpha)
        return true
    }

    private var deleteSwipeInitialEnd = -1
    private var deleteSwipeWordBoundaries = emptyList<Int>()
    private var deleteSwipeLineBoundaries = emptyList<Int>()
    private var deleteSwipeWordIndex = 0
    private var deleteSwipeLineIndex = 0
    private var deleteSwipeCharOffset = 0
    private var deleteSwipeStepCount = 0

    override fun onMoveDeletePointer(steps: Int) {
        onMoveDeletePointer(steps, 0)
    }

    override fun onMoveDeletePointer(stepsX: Int, stepsY: Int) {
        inputLogic.finishInput()
        val currentEnd = connection.expectedSelectionEnd
        if (deleteSwipeInitialEnd == -1 || deleteSwipeInitialEnd != currentEnd) {
            deleteSwipeInitialEnd = currentEnd
            val textBefore = connection.getTextBeforeCursor(2000, 0)?.toString() ?: ""
            val baseStart = connection.expectedSelectionStart
            deleteSwipeWordBoundaries = getWordBoundariesBackwards(textBefore, baseStart)
            deleteSwipeLineBoundaries = getLineBoundariesBackwards(textBefore, baseStart)
            deleteSwipeWordIndex = 0
            deleteSwipeLineIndex = 0
            deleteSwipeCharOffset = 0
            deleteSwipeStepCount = 0
        }

        if (settings.current.mDeleteSwipeWordByWord) {
            onMoveDeletePointerWords2D(stepsX, stepsY)
        } else {
            onMoveDeletePointerChars2D(stepsX, stepsY)
        }
    }

    private fun onMoveDeletePointerChars2D(stepsX: Int, stepsY: Int) {
        val oldLineIndex = deleteSwipeLineIndex
        val oldCharOffset = deleteSwipeCharOffset

        if (stepsY != 0 && deleteSwipeLineBoundaries.isNotEmpty()) {
            deleteSwipeLineIndex = (deleteSwipeLineIndex - stepsY).coerceIn(0, deleteSwipeLineBoundaries.size - 1)
        }

        if (stepsX != 0) {
            val actual = actualSteps(stepsX)
            deleteSwipeCharOffset = (deleteSwipeCharOffset - actual).coerceAtLeast(0)
        }

        if (deleteSwipeLineIndex != oldLineIndex || deleteSwipeCharOffset != oldCharOffset) {
            val lineBaseStart = if (deleteSwipeLineBoundaries.isNotEmpty()) {
                deleteSwipeLineBoundaries[deleteSwipeLineIndex]
            } else {
                deleteSwipeInitialEnd
            }
            val finalStart = (lineBaseStart - deleteSwipeCharOffset).coerceIn(0, deleteSwipeInitialEnd)

            deleteSwipeStepCount++
            if (deleteSwipeStepCount % 2 != 0) {
                performHapticFeedback(HapticEvent.GESTURE_MOVE)
            }
            connection.setSelection(finalStart, deleteSwipeInitialEnd)
        }
    }

    private fun onMoveDeletePointerWords2D(stepsX: Int, stepsY: Int) {
        val oldLineIndex = deleteSwipeLineIndex
        val oldWordIndex = deleteSwipeWordIndex

        // Negative stepsY moves UP (select more lines), positive stepsY moves DOWN (deselect lines)
        if (stepsY != 0 && deleteSwipeLineBoundaries.isNotEmpty()) {
            deleteSwipeLineIndex = (deleteSwipeLineIndex - stepsY).coerceIn(0, deleteSwipeLineBoundaries.size - 1)
        }

        // Negative stepsX moves LEFT (select more words), positive stepsX moves RIGHT (deselect words)
        if (stepsX != 0) {
            deleteSwipeWordIndex = (deleteSwipeWordIndex - stepsX).coerceAtLeast(0)
        }

        if (deleteSwipeLineIndex != oldLineIndex || deleteSwipeWordIndex != oldWordIndex) {
            val lineBaseStart = if (deleteSwipeLineBoundaries.isNotEmpty()) {
                deleteSwipeLineBoundaries[deleteSwipeLineIndex]
            } else {
                deleteSwipeInitialEnd
            }

            val finalStart = if (deleteSwipeWordIndex == 0) {
                lineBaseStart
            } else {
                val wordsBeforeLine = if (deleteSwipeLineIndex == 0) {
                    deleteSwipeWordBoundaries
                } else {
                    val textBefore = connection.getTextBeforeCursor(2000, 0)?.toString() ?: ""
                    val currentLengthBeforeLine = (lineBaseStart - (deleteSwipeInitialEnd - textBefore.length)).coerceIn(0, textBefore.length)
                    getWordBoundariesBackwards(textBefore.take(currentLengthBeforeLine), lineBaseStart)
                }
                if (wordsBeforeLine.isNotEmpty()) {
                    val idx = deleteSwipeWordIndex.coerceIn(0, wordsBeforeLine.size - 1)
                    wordsBeforeLine[idx]
                } else {
                    lineBaseStart
                }
            }

            deleteSwipeStepCount++
            if (deleteSwipeStepCount % 2 != 0) {
                performHapticFeedback(HapticEvent.GESTURE_MOVE)
            }
            connection.setSelection(finalStart.coerceAtMost(deleteSwipeInitialEnd), deleteSwipeInitialEnd)
        }
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
        deleteSwipeInitialEnd = -1
        deleteSwipeWordBoundaries = emptyList()
        deleteSwipeLineBoundaries = emptyList()
        deleteSwipeWordIndex = 0
        deleteSwipeLineIndex = 0
        deleteSwipeCharOffset = 0
        deleteSwipeStepCount = 0
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
        mConsumedPhysicalKeys.clear()
        deleteSwipeInitialEnd = -1
        deleteSwipeWordBoundaries = emptyList()
        deleteSwipeLineBoundaries = emptyList()
        deleteSwipeWordIndex = 0
        deleteSwipeLineIndex = 0
        deleteSwipeCharOffset = 0
        deleteSwipeStepCount = 0
    }

    private fun onLanguageSlide(steps: Int, isVertical: Boolean = false): Boolean {
        val minDistance = if (isVertical) 1 else settings.current.mLanguageSwipeDistance
        if (abs(steps) < minDistance) return false
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
        if (!isSpaceSwipeActive) {
            isSpaceSwipeActive = true
            cursorMoveStepCount = 0
            latinIME.isCursorGestureActive = true
            latinIME.mHandler.cancelResumeSuggestions()
            inputLogic.finishInput()
        }
        cursorMoveStepCount++
        val shouldHaptic = cursorMoveStepCount % 2 != 0
        val code = if (steps < 0) {
            if (shouldHaptic) gestureMoveBackHaptics()
            KeyCode.ARROW_UP
        } else {
            if (shouldHaptic) gestureMoveForwardHaptics()
            KeyCode.ARROW_DOWN
        }
        repeat(abs(steps)) {
            onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        }
        return true
    }

    private fun onMoveCursorHorizontally(rawSteps: Int): Boolean {
        if (rawSteps == 0) return false
        // for RTL languages we want to invert pointer movement
        val rtl = RichInputMethodManager.getInstance().currentSubtype.isRtlSubtype
        val steps = if (rtl) -rawSteps else rawSteps

        if (!isSpaceSwipeActive) {
            isSpaceSwipeActive = true
            cursorMoveStepCount = 0
            latinIME.isCursorGestureActive = true
            latinIME.mHandler.cancelResumeSuggestions()
            inputLogic.finishInput()
        }

        cursorMoveStepCount++
        val shouldHaptic = cursorMoveStepCount % 2 != 0
        val code = if (steps < 0) {
            if (shouldHaptic) gestureMoveBackHaptics()
            if (rtl) KeyCode.ARROW_RIGHT else KeyCode.ARROW_LEFT
        } else {
            if (shouldHaptic) gestureMoveForwardHaptics(true)
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
            override fun onStartDragging() {
                latinIME.isCursorGestureActive = true
                latinIME.mHandler.cancelResumeSuggestions()
                inputLogic.finishInput()
            }
            override fun onStopDragging() {
                latinIME.isCursorGestureActive = false
                inputLogic.restartSuggestionsOnWordTouchedByCursor(settings.current)
            }
        })
    }



    companion object {
        var sPersistentTextEditModeActive = false
        var sPersistentSelectionModeActive = false

        fun deactivateSelectionMode() {
            if (sPersistentSelectionModeActive) {
                sPersistentSelectionModeActive = false
                KeyboardSwitcher.getInstance().mainKeyboardView?.invalidateAllKeys()
                KeyboardSwitcher.getInstance().suggestionStripView?.updateToolbarButtonsActivatedState()
            }
        }
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

        internal fun getLineBoundariesBackwards(text: String, endOffset: Int): List<Int> {
            val boundaries = mutableListOf<Int>()
            boundaries.add(endOffset)
            if (text.isEmpty()) return boundaries

            var currentEnd = text.length
            while (currentEnd > 0) {
                val lastNewline = text.lastIndexOf('\n', currentEnd - 1)
                if (lastNewline != -1) {
                    val lineStart = lastNewline + 1
                    if (currentEnd > lineStart) {
                        val segLength = currentEnd - lineStart
                        if (segLength > 50) {
                            var subEnd = currentEnd
                            while (subEnd - lineStart > 50) {
                                var breakPoint = (subEnd - 40).coerceAtLeast(lineStart)
                                while (breakPoint > lineStart && !Character.isWhitespace(text[breakPoint])) {
                                    breakPoint--
                                }
                                if (breakPoint <= lineStart) {
                                    breakPoint = (subEnd - 40).coerceAtLeast(lineStart)
                                }
                                val offset = endOffset - (text.length - breakPoint)
                                boundaries.add(offset)
                                subEnd = breakPoint
                            }
                        }
                        val offset = endOffset - (text.length - lineStart)
                        if (boundaries.last() != offset) {
                            boundaries.add(offset)
                        }
                    }
                    currentEnd = lastNewline
                } else {
                    if (currentEnd > 50) {
                        var subEnd = currentEnd
                        while (subEnd > 50) {
                            var breakPoint = (subEnd - 40).coerceAtLeast(0)
                            while (breakPoint > 0 && !Character.isWhitespace(text[breakPoint])) {
                                breakPoint--
                            }
                            if (breakPoint <= 0) {
                                breakPoint = (subEnd - 40).coerceAtLeast(0)
                            }
                            val offset = endOffset - (text.length - breakPoint)
                            boundaries.add(offset)
                            subEnd = breakPoint
                        }
                    }
                    val offset = endOffset - text.length
                    if (boundaries.last() != offset) {
                        boundaries.add(offset)
                    }
                    break
                }
            }
            return boundaries
        }

        internal fun getWordBoundariesBackwards(text: String, endOffset: Int): List<Int> {
            val boundaries = mutableListOf<Int>()
            var i = text.length
            val offset = endOffset - text.length
            boundaries.add(endOffset)

            while (i > 0) {
                // 1. Skip trailing whitespace
                while (i > 0) {
                    val cp = Character.codePointBefore(text, i)
                    if (Character.isWhitespace(cp)) {
                        i -= Character.charCount(cp)
                    } else {
                        break
                    }
                }
                if (i == 0) break

                // 2. Skip attached punctuation
                var skippedPunctuation = false
                while (i > 0) {
                    val cp = Character.codePointBefore(text, i)
                    if (!Character.isLetterOrDigit(cp) && !Character.isWhitespace(cp)) {
                        i -= Character.charCount(cp)
                        skippedPunctuation = true
                    } else {
                        break
                    }
                }

                // 3. Skip word characters
                var skippedWord = false
                while (i > 0) {
                    val cp = Character.codePointBefore(text, i)
                    if (Character.isLetterOrDigit(cp)) {
                        i -= Character.charCount(cp)
                        skippedWord = true
                    } else {
                        break
                    }
                }

                // 4. Consume preceding whitespace if a word or punctuation was skipped
                if (skippedWord || skippedPunctuation) {
                    while (i > 0) {
                        val cp = Character.codePointBefore(text, i)
                        if (Character.isWhitespace(cp)) {
                            i -= Character.charCount(cp)
                        } else {
                            break
                        }
                    }
                }

                boundaries.add((offset + i).coerceAtLeast(0))
            }
            return boundaries
        }
    }
}
