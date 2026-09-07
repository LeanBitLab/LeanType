/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard

import android.content.res.Resources
import android.content.res.TypedArray
import android.os.SystemClock
import android.view.MotionEvent
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.internal.BatchInputArbiter
import helium314.keyboard.keyboard.internal.BatchInputArbiter.BatchInputArbiterListener
import helium314.keyboard.keyboard.internal.BogusMoveEventDetector
import helium314.keyboard.keyboard.internal.DrawingProxy
import helium314.keyboard.keyboard.internal.GestureEnabler
import helium314.keyboard.keyboard.internal.GestureStrokeDrawingParams
import helium314.keyboard.keyboard.internal.GestureStrokeDrawingPoints
import helium314.keyboard.keyboard.internal.GestureStrokeRecognitionParams
import helium314.keyboard.keyboard.internal.PointerTrackerQueue
import helium314.keyboard.keyboard.internal.TimerProxy
import helium314.keyboard.keyboard.internal.TypingTimeRecorder
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.CoordinateUtils
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.dpToPx
import java.util.ArrayList
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

class PointerTracker private constructor(
    @JvmField val mPointerId: Int
) : PointerTrackerQueue.Element, BatchInputArbiterListener {

    internal class PointerTrackerParams(mainKeyboardViewAttr: TypedArray) {
        val mKeySelectionByDraggingFinger: Boolean = mainKeyboardViewAttr.getBoolean(
            R.styleable.MainKeyboardView_keySelectionByDraggingFinger, false
        )
        val mTouchNoiseThresholdTime: Int = mainKeyboardViewAttr.getInt(
            R.styleable.MainKeyboardView_touchNoiseThresholdTime, 0
        )
        val mTouchNoiseThresholdDistance: Int = mainKeyboardViewAttr.getDimensionPixelSize(
            R.styleable.MainKeyboardView_touchNoiseThresholdDistance, 0
        )
        val mSuppressKeyPreviewAfterBatchInputDuration: Int = mainKeyboardViewAttr.getInt(
            R.styleable.MainKeyboardView_suppressKeyPreviewAfterBatchInputDuration, 0
        )
        val mKeyRepeatStartTimeout: Int = mainKeyboardViewAttr.getInt(
            R.styleable.MainKeyboardView_keyRepeatStartTimeout, 0
        )
        val mKeyRepeatInterval: Int = mainKeyboardViewAttr.getInt(
            R.styleable.MainKeyboardView_keyRepeatInterval, 0
        )
    }

    // The KeyDetector is set whenever the down event is processed. Also this is updated
    // when new Keyboard is set by setKeyDetector(KeyDetector).
    private var mKeyDetector = KeyDetector()
    private var mKeyboard: Keyboard? = null
    private val mBogusMoveEventDetector = BogusMoveEventDetector()

    private var mIsDetectingGesture = false // per PointerTracker.

    // The position and time at which first down event occurred.
    private var mDownTime = 0L
    private val mDownCoordinates = CoordinateUtils.newInstance()
    private var mUpTime = 0L

    // The current key where this pointer is.
    private var mCurrentKey: Key? = null
    // The position where the current key was recognized for the first time.
    private var mKeyX = 0
    private var mKeyY = 0

    // Last pointer position.
    private var mLastX = 0
    private var mLastY = 0
    private var mStartX = 0
    private var mStartY = 0
    private var mStartTime = 0L
    private var mInHorizontalSwipe = false
    private var mInVerticalSwipe = false

    // true if keyboard layout has been changed.
    private var mKeyboardLayoutHasBeenChanged = false
    private var keyboardChangeOccupiedHeightDifference = 0

    // true if this pointer is no longer triggering any action because it has been canceled.
    private var mIsTrackingForActionDisabled = false

    // the popup keys panel currently being shown. equals null if no panel is active.
    private var mPopupKeysPanel: PopupKeysPanel? = null

    // true if this pointer is in the dragging finger mode.
    private var mIsInDraggingFinger = false
    // true if this pointer is sliding from a modifier key and in the sliding key input mode,
    // so that further modifier keys should be ignored.
    private var mIsInSlidingKeyInput = false
    // if not a NOT_A_CODE, the key of this code is repeating
    private var mCurrentRepeatingKeyCode = Constants.NOT_A_CODE

    // true if dragging finger is allowed.
    private var mIsAllowedDraggingFinger = false
    // true if a keyswipe gesture is enabled and warranted.
    private var mKeySwipeAllowed = false

    private var mInTouchpadMode = false
    private var mTouchpadLastX = 0
    private var mTouchpadLastY = 0
    // Accumulators for fractional movement
    private var mTouchpadAccX = 0
    private var mTouchpadAccY = 0

    private val mBatchInputArbiter: BatchInputArbiter = BatchInputArbiter(
        mPointerId,
        sGestureStrokeRecognitionParams ?: GestureStrokeRecognitionParams.DEFAULT
    )
    val gestureStrokeDrawingPoints: GestureStrokeDrawingPoints = GestureStrokeDrawingPoints(
        sGestureStrokeDrawingParams!!
    )

    // Returns true if keyboard has been changed by this callback.
    private fun callListenerOnPressAndCheckKeyboardLayoutChange(key: Key, repeatCount: Int): Boolean {
        // While gesture input is going on, this method should be a no-operation. But when gesture
        // input has been canceled, sInGesture and mIsDetectingGesture are set to false.
        // To keep this method is a no-operation, mIsTrackingForActionDisabled should also be taken account of.
        if (sInGesture || mIsDetectingGesture || mIsTrackingForActionDisabled) {
            return false
        }
        val ignoreModifierKey = mIsInDraggingFinger && key.isModifier()
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format(Locale.US, "[%d] onPress    : %s%s%s%s", mPointerId,
                Constants.printableCode(key.code),
                if (ignoreModifierKey) " ignoreModifier" else "",
                if (key.isEnabled) "" else " disabled",
                if (repeatCount > 0) " repeatCount=$repeatCount" else ""))
        }
        if (ignoreModifierKey) {
            return false
        }
        if (key.isEnabled) {
            sListener.onPressKey(key.code, repeatCount, getActivePointerTrackerCount() == 1, HapticEvent.KEY_PRESS)
            val keyboardLayoutHasBeenChanged = mKeyboardLayoutHasBeenChanged
            mKeyboardLayoutHasBeenChanged = false
            getTimerProxy().startTypingStateTimer(key)
            return keyboardLayoutHasBeenChanged
        }
        return false
    }

    // Note that we need primaryCode argument because the keyboard may in shifted state and the
    // primaryCode is different from Key.mKeyCode.
    private fun callListenerOnCodeInput(
        key: Key, primaryCode: Int, x: Int, y: Int, eventTime: Long, isKeyRepeat: Boolean
    ) {
        val ignoreModifierKey = mIsInDraggingFinger && key.isModifier() && key.code != KeyCode.NUMPAD
        val altersCode = key.altCodeWhileTyping() && getTimerProxy().isTypingState() && !isClearlyInsideKey(key, x, y)
        val code = if (altersCode) key.altCode else primaryCode
        if (DEBUG_LISTENER) {
            val output = if (code == KeyCode.MULTIPLE_CODE_POINTS) key.outputText else Constants.printableCode(code)
            Log.d(TAG, String.format(Locale.US, "[%d] onCodeInput: %4d %4d %s%s%s%s", mPointerId, x, y,
                output, if (ignoreModifierKey) " ignoreModifier" else "",
                if (altersCode) " altersCode" else "", if (key.isEnabled) "" else " disabled"))
        }
        if (ignoreModifierKey) {
            return
        }
        if (key.isEnabled || altersCode) {
            sTypingTimeRecorder?.onCodeInput(code, eventTime)
            if (code == KeyCode.MULTIPLE_CODE_POINTS) {
                sListener.onTextInput(key.outputText)
            } else if (code != KeyCode.NOT_SPECIFIED) {
                if (mKeyboard?.hasProximityCharsCorrection(code) == true) {
                    sListener.onCodeInput(code, x, y, isKeyRepeat)
                } else {
                    sListener.onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, isKeyRepeat)
                }
            }
        }
    }

    // Note that we need primaryCode argument because the keyboard may be in shifted state and the
    // primaryCode is different from Key.mKeyCode.
    private fun callListenerOnRelease(key: Key, primaryCode: Int, withSliding: Boolean) {
        if (sInGesture || mIsDetectingGesture || mIsTrackingForActionDisabled) {
            return
        }
        val ignoreModifierKey = mIsInDraggingFinger && key.isModifier()
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format(Locale.US, "[%d] onRelease  : %s%s%s%s", mPointerId,
                Constants.printableCode(primaryCode),
                if (withSliding) " sliding" else "", if (ignoreModifierKey) " ignoreModifier" else "",
                if (key.isEnabled) "" else " disabled"))
        }
        if (ignoreModifierKey) {
            return
        }
        if (key.isEnabled) {
            sListener.onReleaseKey(primaryCode, withSliding)
        }
    }

    private fun callListenerOnFinishSlidingInput() {
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format(Locale.US, "[%d] onFinishSlidingInput", mPointerId))
        }
        sListener.onFinishSlidingInput()
    }

    private fun callListenerOnCancelInput() {
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format(Locale.US, "[%d] onCancelInput", mPointerId))
        }
        sListener.onCancelInput()
    }

    private fun setKeyDetectorInner(keyDetector: KeyDetector) {
        val keyboard = keyDetector.getKeyboard() ?: return
        if (keyDetector === mKeyDetector && keyboard === mKeyboard) {
            return
        }
        if (mKeyboard != null) {
            // changing keyboards may change height
            // since y is measured from top of view, this change needs to be considered in some places
            keyboardChangeOccupiedHeightDifference = keyboard.mOccupiedHeight - mKeyboard!!.mOccupiedHeight
        }
        mKeyDetector = keyDetector
        mKeyboard = keyboard
        // Mark that keyboard layout has been changed.
        mKeyboardLayoutHasBeenChanged = true
        val keyWidth = keyboard.mMostCommonKeyWidth
        val keyHeight = keyboard.mMostCommonKeyHeight
        mBatchInputArbiter.setKeyboardGeometry(keyWidth, keyboard.mOccupiedHeight)
        mBogusMoveEventDetector.setKeyboardGeometry(keyWidth, keyHeight)
    }

    override fun isInDraggingFinger(): Boolean = mIsInDraggingFinger

    val key: Key? get() = mCurrentKey

    override fun isModifier(): Boolean = mCurrentKey?.isModifier() == true

    fun getKeyOn(x: Int, y: Int): Key? = mKeyDetector.detectHitKey(x, y)

    private fun setReleasedKeyGraphics(key: Key?, withAnimation: Boolean) {
        if (key == null || sDrawingProxy == null) {
            return
        }

        sDrawingProxy?.onKeyReleased(key, withAnimation)

        if (key.isShift()) {
            mKeyboard?.mShiftKeys?.forEach { shiftKey ->
                if (shiftKey !== key) {
                    sDrawingProxy?.onKeyReleased(shiftKey, false)
                }
            }
        }

        if (key.altCodeWhileTyping()) {
            val altCode = key.altCode
            val altKey = mKeyboard?.getKey(altCode)
            if (altKey != null) {
                sDrawingProxy?.onKeyReleased(altKey, false)
            }
            mKeyboard?.mAltCodeKeysWhileTyping?.forEach { k ->
                if (k !== key && k.altCode == altCode) {
                    sDrawingProxy?.onKeyReleased(k, false)
                }
            }
        }
    }

    private fun setPressedKeyGraphics(key: Key?, eventTime: Long) {
        if (key == null) {
            return
        }

        // Even if the key is disabled, it should respond if it is in the altCodeWhileTyping state.
        val altersCode = key.altCodeWhileTyping() && getTimerProxy().isTypingState()
        val needsToUpdateGraphics = key.isEnabled || altersCode
        if (!needsToUpdateGraphics || sDrawingProxy == null) {
            return
        }

        val noKeyPreview = sInGesture || needsToSuppressKeyPreviewPopup(eventTime)
        sDrawingProxy?.onKeyPressed(key, !noKeyPreview)

        if (key.isShift()) {
            mKeyboard?.mShiftKeys?.forEach { shiftKey ->
                if (shiftKey !== key) {
                    sDrawingProxy?.onKeyPressed(shiftKey, false)
                }
            }
        }

        if (altersCode) {
            val altCode = key.altCode
            val altKey = mKeyboard?.getKey(altCode)
            if (altKey != null) {
                sDrawingProxy?.onKeyPressed(altKey, false)
            }
            mKeyboard?.mAltCodeKeysWhileTyping?.forEach { k ->
                if (k !== key && k.altCode == altCode) {
                    sDrawingProxy?.onKeyPressed(k, false)
                }
            }
        }
    }

    fun getLastCoordinates(outCoords: IntArray) {
        CoordinateUtils.set(outCoords, mLastX, mLastY)
    }

    val downTime: Long get() = mDownTime

    fun getDownCoordinates(outCoords: IntArray) {
        CoordinateUtils.copy(outCoords, mDownCoordinates)
    }

    private fun onDownKey(x: Int, y: Int, eventTime: Long): Key? {
        mDownTime = eventTime
        CoordinateUtils.set(mDownCoordinates, x, y)
        mBogusMoveEventDetector.onDownKey()
        return onMoveToNewKey(onMoveKeyInternal(x, y), x, y)
    }

    private fun onMoveKeyInternal(x: Int, y: Int): Key? {
        mBogusMoveEventDetector.onMoveKey(getDistance(x, y, mLastX, mLastY))
        mLastX = x
        mLastY = y
        return mKeyDetector.detectHitKey(x, y)
    }

    private fun onMoveKey(x: Int, y: Int): Key? = onMoveKeyInternal(x, y)

    private fun onMoveToNewKey(newKey: Key?, x: Int, y: Int): Key? {
        mCurrentKey = newKey
        mKeyX = x
        mKeyY = y
        return newKey
    }

    private fun isOldestTrackerInQueue(): Boolean = sPointerTrackerQueue.getOldestElement() === this

    // Implements BatchInputArbiterListener.
    override fun onStartBatchInput() {
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format(Locale.US, "[%d] onStartBatchInput", mPointerId))
        }
        sListener.onStartBatchInput()
        dismissAllPopupKeysPanels()
        getTimerProxy().cancelLongPressTimersOf(this)
    }

    private fun showGestureTrail() {
        if (mIsTrackingForActionDisabled) {
            return
        }
        // A gesture floating preview text will be shown at the oldest pointer/finger on the screen.
        sDrawingProxy?.showGestureTrail(this, isOldestTrackerInQueue())
    }

    fun updateBatchInputByTimer(syntheticMoveEventTime: Long) {
        mBatchInputArbiter.updateBatchInputByTimer(syntheticMoveEventTime, this)
    }

    // Implements BatchInputArbiterListener.
    override fun onUpdateBatchInput(aggregatedPointers: InputPointers, moveEventTime: Long) {
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format(Locale.US, "[%d] onUpdateBatchInput: batchPoints=%d", mPointerId,
                aggregatedPointers.pointerSize))
        }
        sListener.onUpdateBatchInput(aggregatedPointers)
    }

    // Implements BatchInputArbiterListener.
    override fun onStartUpdateBatchInputTimer() {
        getTimerProxy().startUpdateBatchInputTimer(this)
    }

    // Implements BatchInputArbiterListener.
    override fun onEndBatchInput(aggregatedPointers: InputPointers, upEventTime: Long) {
        sTypingTimeRecorder?.onEndBatchInput(upEventTime)
        getTimerProxy().cancelAllUpdateBatchInputTimers()
        if (mIsTrackingForActionDisabled) {
            return
        }
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format(Locale.US, "[%d] onEndBatchInput   : batchPoints=%d",
                mPointerId, aggregatedPointers.pointerSize))
        }
        sListener.onEndBatchInput(aggregatedPointers)
    }

    private fun cancelBatchInput() {
        cancelAllPointerTrackers()
        mIsDetectingGesture = false
        if (!sInGesture) {
            return
        }
        sInGesture = false
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format(Locale.US, "[%d] onCancelBatchInput", mPointerId))
        }
        sListener.onCancelBatchInput()
    }

    fun processMotionEvent(me: MotionEvent, keyDetector: KeyDetector) {
        val action = me.actionMasked
        val eventTime = me.eventTime
        if (action == MotionEvent.ACTION_MOVE) {
            // When this pointer is the only active pointer and is showing a popup keys panel,
            // we should ignore other pointers' motion event.
            val shouldIgnoreOtherPointers = isShowingPopupKeysPanel() && getActivePointerTrackerCount() == 1
            val pointerCount = me.pointerCount
            for (index in 0 until pointerCount) {
                val id = me.getPointerId(index)
                if (shouldIgnoreOtherPointers && id != mPointerId) {
                    continue
                }
                val x = me.getX(index).toInt()
                val y = me.getY(index).toInt()
                val tracker = getPointerTracker(id)
                tracker.onMoveEvent(x, y, eventTime, me)
            }
            return
        }
        val index = me.actionIndex
        val x = me.getX(index).toInt()
        val y = me.getY(index).toInt()
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> onDownEvent(x, y, eventTime, keyDetector)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> onUpEvent(x, y, eventTime)
            MotionEvent.ACTION_CANCEL -> onCancelEvent(x, y, eventTime)
        }
    }

    private fun onDownEvent(
        x: Int, y: Int, eventTime: Long, keyDetector: KeyDetector
    ) {
        setKeyDetectorInner(keyDetector)
        if (DEBUG_EVENT) {
            printTouchEvent("onDownEvent:", x, y, eventTime)
        }
        // Naive up-to-down noise filter.
        val deltaT = eventTime - mUpTime
        val params = sParams
        if (params != null && deltaT < params.mTouchNoiseThresholdTime) {
            val distance = getDistance(x, y, mLastX, mLastY)
            if (distance < params.mTouchNoiseThresholdDistance) {
                if (DEBUG_MODE) {
                    Log.w(TAG, String.format(Locale.US, "[%d] onDownEvent: ignore potential noise: time=%d distance=%d",
                        mPointerId, deltaT, distance))
                }
                cancelTrackingForAction()
                return
            }
        }

        val key = getKeyOn(x, y)
        mBogusMoveEventDetector.onActualDownEvent(x, y)
        if (key != null && key.isModifier()) {
            if (sInGesture) {
                // Make sure not to interrupt an active gesture
                return
            } else {
                // Before processing a down event of modifier key, all pointers
                // already being tracked should be released.
                sPointerTrackerQueue.releaseAllPointers(eventTime)
            }
        }
        sPointerTrackerQueue.add(this)
        onDownEventInternal(x, y, eventTime)
        if (!sGestureEnabler.shouldHandleGesture()) {
            return
        }
        // A gesture should start only from a non-modifier key. Note that the gesture detection is
        // disabled when the key is repeating.
        mIsDetectingGesture = (mKeyboard?.mId?.isAlphabetKeyboard == true) &&
                key != null && !key.isModifier() && !mKeySwipeAllowed && !sInKeySwipe
        if (mIsDetectingGesture) {
            mBatchInputArbiter.addDownEventPoint(x, y, eventTime,
                sTypingTimeRecorder?.lastLetterTypingTime ?: 0L, getActivePointerTrackerCount())
            gestureStrokeDrawingPoints.onDownEvent(
                x, y, mBatchInputArbiter.getElapsedTimeSinceFirstDown(eventTime)
            )
        }
    }

    fun isShowingPopupKeysPanel(): Boolean = mPopupKeysPanel != null

    private fun dismissPopupKeysPanel() {
        if (isShowingPopupKeysPanel()) {
            mPopupKeysPanel?.dismissPopupKeysPanel()
            mPopupKeysPanel = null
        }
    }

    private fun onDownEventInternal(x: Int, y: Int, eventTime: Long) {
        var key = onDownKey(x, y, eventTime)
        val isEmojiClipBottomRow = mKeyboard?.mId?.isEmojiClipBottomRow == true
        mIsAllowedDraggingFinger = (sParams?.mKeySelectionByDraggingFinger == true) ||
                (key != null && key.isModifier() && !(isEmojiClipBottomRow && key.code == KeyCode.ALPHA)) ||
                mKeyDetector.alwaysAllowsKeySelectionByDraggingFinger()
        if (key != null && isSwiper(key.code) && !sInGesture) {
            mKeySwipeAllowed = true
            sInKeySwipe = true
        }
        mKeyboardLayoutHasBeenChanged = false
        mIsTrackingForActionDisabled = false
        resetKeySelectionByDraggingFinger()
        if (key != null) {
            if (callListenerOnPressAndCheckKeyboardLayoutChange(key, 0)) {
                val yOffset = keyboardChangeOccupiedHeightDifference
                keyboardChangeOccupiedHeightDifference = 0
                CoordinateUtils.set(mDownCoordinates, x, y + yOffset)
                key = onDownKey(x, y + yOffset, eventTime)
            }

            if (key != null) {
                startRepeatKey(key)
                startLongPressTimer(key)
                setPressedKeyGraphics(key, eventTime)
            }
            mStartX = x
            mStartY = y
            mStartTime = System.currentTimeMillis()
        }
    }

    private fun startKeySelectionByDraggingFinger(key: Key) {
        if (!mIsInDraggingFinger) {
            val code = key.code
            val isEmojiClipBottomRow = mKeyboard?.mId?.isEmojiClipBottomRow == true
            mIsInSlidingKeyInput = key.isModifier() && code != KeyCode.CTRL_LOCK && code != KeyCode.ALT_LOCK &&
                    code != KeyCode.FN_LOCK && code != KeyCode.META_LOCK && !(isEmojiClipBottomRow && code == KeyCode.ALPHA)
        }
        mIsInDraggingFinger = true
    }

    private fun resetKeySelectionByDraggingFinger() {
        mIsInDraggingFinger = false
        mIsInSlidingKeyInput = false
        sDrawingProxy?.showSlidingKeyInputPreview(null)
    }

    private fun isSwiper(code: Int): Boolean {
        val sv = Settings.getValues()
        return when (code) {
            Constants.CODE_SPACE -> sv.mSpaceSwipeHorizontal != KeyboardActionListener.SWIPE_NO_ACTION ||
                    sv.mSpaceSwipeVertical != KeyboardActionListener.SWIPE_NO_ACTION
            KeyCode.DELETE -> sv.mDeleteSwipeEnabled
            else -> false
        }
    }

    private fun onGestureMoveEvent(
        x: Int, y: Int, eventTime: Long, isMajorEvent: Boolean, key: Key?
    ) {
        if (!mIsDetectingGesture || sInKeySwipe) {
            return
        }
        val onValidArea = mBatchInputArbiter.addMoveEventPoint(
            x, y, eventTime, isMajorEvent, this
        )
        // If the move event goes out from valid batch input area, cancel batch input.
        if (!onValidArea) {
            cancelBatchInput()
            return
        }
        gestureStrokeDrawingPoints.onMoveEvent(
            x, y, mBatchInputArbiter.getElapsedTimeSinceFirstDown(eventTime)
        )
        // If the PopupKeysPanel is showing then do not attempt to enter gesture mode.
        if (isShowingPopupKeysPanel()) {
            return
        }
        if (!sInGesture && key != null && Character.isLetter(key.code) && mBatchInputArbiter.mayStartBatchInput(this)) {
            sListener.resetMetaState() // avoid metaState getting stuck, doesn't work with gesture typing anyway
            sInGesture = true
        }
        if (sInGesture) {
            if (key != null) {
                mBatchInputArbiter.updateBatchInput(eventTime, this)
            }
            showGestureTrail()
        }
    }

    private fun onMoveEvent(x: Int, y: Int, eventTime: Long, me: MotionEvent?) {
        if (DEBUG_MOVE_EVENT) {
            printTouchEvent("onMoveEvent:", x, y, eventTime)
        }
        if (mIsTrackingForActionDisabled) {
            return
        }

        if (sGestureEnabler.shouldHandleGesture() && me != null) {
            // Add historical points to gesture path.
            val pointerIndex = me.findPointerIndex(mPointerId)
            val historicalSize = me.historySize
            for (h in 0 until historicalSize) {
                val historicalX = me.getHistoricalX(pointerIndex, h).toInt()
                val historicalY = me.getHistoricalY(pointerIndex, h).toInt()
                val historicalTime = me.getHistoricalEventTime(h)
                onGestureMoveEvent(historicalX, historicalY, historicalTime, false, null)
            }
        }

        if (isShowingPopupKeysPanel()) {
            val panel = mPopupKeysPanel ?: return
            val translatedX = panel.translateX(x)
            val translatedY = panel.translateY(y)
            panel.onMoveEvent(translatedX, translatedY, mPointerId, eventTime)
            onMoveKey(x, y)
            if (mIsInSlidingKeyInput) {
                sDrawingProxy?.showSlidingKeyInputPreview(this)
            }
            return
        }
        onMoveEventInternal(x, y, eventTime)
    }

    private fun processDraggingFingerInToNewKey(newKey: Key, x: Int, y: Int, eventTime: Long) {
        var key: Key? = newKey
        if (callListenerOnPressAndCheckKeyboardLayoutChange(key!!, 0)) {
            key = onMoveKey(x, y)
        }
        onMoveToNewKey(key, x, y)
        if (mIsTrackingForActionDisabled) {
            return
        }
        if (key != null) {
            startLongPressTimer(key)
            setPressedKeyGraphics(key, eventTime)
        }
    }

    private fun processProximateBogusDownMoveUpEventHack(
        key: Key, x: Int, y: Int, eventTime: Long, oldKey: Key, lastX: Int, lastY: Int
    ) {
        if (DEBUG_MODE) {
            val mostCommonKeyWidth = (mKeyboard?.mMostCommonKeyWidth ?: 0).toDouble()
            val mostCommonKeyHeight = (mKeyboard?.mMostCommonKeyHeight ?: 0).toDouble()
            val keyDiagonal = hypot(mostCommonKeyWidth, mostCommonKeyHeight).toFloat()
            val radiusRatio = mBogusMoveEventDetector.getDistanceFromDownEvent(x, y) / keyDiagonal
            Log.w(TAG, String.format(Locale.US, "[%d] onMoveEvent: bogus down-move-up event (raidus=%.2f key diagonal) is translated to up[%d,%d,%s]/down[%d,%d,%s] events",
                mPointerId, radiusRatio,
                lastX, lastY, Constants.printableCode(oldKey.code),
                x, y, Constants.printableCode(key.code)))
        }
        onUpEventInternal(x, y, eventTime)
        onDownEventInternal(x, y, eventTime)
    }

    private fun processDraggingFingerOutFromOldKey(oldKey: Key) {
        setReleasedKeyGraphics(oldKey, true)
        callListenerOnRelease(oldKey, oldKey.code, true)
        startKeySelectionByDraggingFinger(oldKey)
        getTimerProxy().cancelKeyTimersOf(this)
    }

    private fun dragFingerFromOldKeyToNewKey(
        key: Key, x: Int, y: Int, eventTime: Long, oldKey: Key, lastX: Int, lastY: Int
    ) {
        processDraggingFingerOutFromOldKey(oldKey)
        startRepeatKey(key)
        if (mIsAllowedDraggingFinger) {
            processDraggingFingerInToNewKey(key, x, y, eventTime)
        } else if (sTypingTimeRecorder?.isInFastTyping(eventTime) == true && mBogusMoveEventDetector.isCloseToActualDownEvent(x, y)) {
            processProximateBogusDownMoveUpEventHack(key, x, y, eventTime, oldKey, lastX, lastY)
        } else if (getActivePointerTrackerCount() > 1 && !sPointerTrackerQueue.hasModifierKeyOlderThan(this)) {
            if (DEBUG_MODE) {
                Log.w(TAG, String.format(Locale.US, "[%d] onMoveEvent: detected sliding finger while multi touching", mPointerId))
            }
            onUpEvent(x, y, eventTime)
            cancelTrackingForAction()
            setReleasedKeyGraphics(oldKey, true)
        } else {
            if (!mIsDetectingGesture) {
                cancelTrackingForAction()
            }
            setReleasedKeyGraphics(oldKey, true)
        }
    }

    private fun dragFingerOutFromOldKey(oldKey: Key, x: Int, y: Int) {
        processDraggingFingerOutFromOldKey(oldKey)
        if (mIsAllowedDraggingFinger) {
            onMoveToNewKey(null, x, y)
        } else {
            if (!mIsDetectingGesture) {
                cancelTrackingForAction()
            }
        }
    }

    private fun oneShotSwipe(swipeSetting: Int): Boolean = when (swipeSetting) {
        KeyboardActionListener.SWIPE_NO_ACTION,
        KeyboardActionListener.SWIPE_TOGGLE_NUMPAD,
        KeyboardActionListener.SWIPE_HIDE_KEYBOARD -> true
        else -> false
    }

    private fun onKeySwipe(code: Int, x: Int, y: Int, eventTime: Long) {
        val sv = Settings.getValues()
        val fastTypingTimeout = 2 * sv.mKeyLongpressTimeout / 3
        if (code != KeyCode.DELETE && System.currentTimeMillis() < mStartTime + fastTypingTimeout &&
            sTypingTimeRecorder?.isInFastTyping(eventTime) == true) {
            return
        }
        if (code == Constants.CODE_SPACE) {
            val dX = x - mStartX
            val dY = y - mStartY

            // Check if touchpad mode is active and we're in it
            if ((sTouchpadModeActive || sPersistentTouchpadModeActive) && !mInTouchpadMode) {
                mInTouchpadMode = true
                mTouchpadLastX = x
                mTouchpadLastY = y
                mTouchpadAccX = 0
                mTouchpadAccY = 0
                if (!sPersistentTouchpadModeActive) {
                    sListener.onCustomRequest(KeyboardActionListener.CODE_TOUCHPAD_ON)
                }
                return
            }

            if (mInTouchpadMode) {
                val deltaX = x - mTouchpadLastX
                val deltaY = y - mTouchpadLastY

                mTouchpadLastX = x
                mTouchpadLastY = y

                val accFactorX = 1.0f + (((deltaX xor (deltaX shr 31)) - (deltaX shr 31)).toFloat() / TOUCHPAD_ACCELERATION_FACTOR)
                val accFactorY = 1.0f + (((deltaY xor (deltaY shr 31)) - (deltaY shr 31)).toFloat() / TOUCHPAD_ACCELERATION_FACTOR)

                mTouchpadAccX += (deltaX * accFactorX).toInt()
                mTouchpadAccY += (deltaY * accFactorY).toInt()

                val currentTime = System.currentTimeMillis()
                if (currentTime - sLastTouchpadSensitivityUpdateTime > TOUCHPAD_SENSITIVITY_UPDATE_INTERVAL_MS) {
                    sCachedTouchpadSensitivity = Settings.getInstance().current.mTouchpadSensitivity
                    sLastTouchpadSensitivityUpdateTime = currentTime
                }
                val moveThreshold = 70 - (sCachedTouchpadSensitivity * 0.6f).toInt()

                while (mTouchpadAccX >= moveThreshold || mTouchpadAccX <= -moveThreshold) {
                    val positive = mTouchpadAccX > 0
                    val direction = if (positive) KeyCode.ARROW_RIGHT else KeyCode.ARROW_LEFT
                    sListener.onCodeInput(direction, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                    mTouchpadAccX -= if (positive) moveThreshold else -moveThreshold
                }

                while (mTouchpadAccY >= moveThreshold || mTouchpadAccY <= -moveThreshold) {
                    val positive = mTouchpadAccY > 0
                    val direction = if (positive) KeyCode.ARROW_DOWN else KeyCode.ARROW_UP
                    sListener.onCodeInput(direction, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                    mTouchpadAccY -= if (positive) moveThreshold else -moveThreshold
                }
                return
            }

            // Vertical movement
            val stepsY = dY / sPointerStep
            if (stepsY != 0 && abs(dX) < abs(dY) && !mInHorizontalSwipe) {
                if (!mInVerticalSwipe) {
                    getTimerProxy().cancelKeyTimersOf(this)
                    mInVerticalSwipe = true
                } else if (oneShotSwipe(sv.mSpaceSwipeVertical)) {
                    return
                }
                if (sListener.onVerticalSpaceSwipe(stepsY)) {
                    mStartY += stepsY * sPointerStep
                }
                return
            }

            // Horizontal movement
            val stepsX = dX / sPointerStep
            if (stepsX != 0 && !mInVerticalSwipe) {
                if (!mInHorizontalSwipe) {
                    getTimerProxy().cancelKeyTimersOf(this)
                    mInHorizontalSwipe = true
                } else if (oneShotSwipe(sv.mSpaceSwipeHorizontal)) {
                    return
                }
                if (sListener.onHorizontalSpaceSwipe(stepsX)) {
                    mStartX += stepsX * sPointerStep
                }
            }
        } else if (code == KeyCode.DELETE) {
            val steps = (x - mStartX) / sPointerStep
            if (steps != 0) {
                if (!mInHorizontalSwipe) {
                    getTimerProxy().cancelKeyTimersOf(this)
                    mInHorizontalSwipe = true
                }
                mStartX += steps * sPointerStep
                sListener.onMoveDeletePointer(steps)
            }
        }
    }

    private fun onMoveEventInternal(x: Int, y: Int, eventTime: Long) {
        val oldKey = mCurrentKey

        if (mKeySwipeAllowed) {
            onKeySwipe(oldKey?.code ?: 0, x, y, eventTime)
            return
        }

        val newKey = onMoveKey(x, y)
        val lastX = mLastX
        val lastY = mLastY

        if (sGestureEnabler.shouldHandleGesture()) {
            onGestureMoveEvent(x, y, eventTime, true, newKey)
            if (sInGesture) {
                mCurrentKey = null
                setReleasedKeyGraphics(oldKey, true)
                return
            }
        }

        if (newKey != null) {
            if (oldKey != null && isMajorEnoughMoveToBeOnNewKey(x, y, eventTime, newKey)) {
                dragFingerFromOldKeyToNewKey(newKey, x, y, eventTime, oldKey, lastX, lastY)
            } else if (oldKey == null) {
                processDraggingFingerInToNewKey(newKey, x, y, eventTime)
            }
        } else {
            if (oldKey != null && isMajorEnoughMoveToBeOnNewKey(x, y, eventTime, newKey)) {
                dragFingerOutFromOldKey(oldKey, x, y)
            }
        }
        if (mIsInSlidingKeyInput) {
            sDrawingProxy?.showSlidingKeyInputPreview(this)
        }
    }

    private fun onUpEvent(x: Int, y: Int, eventTime: Long) {
        if (DEBUG_EVENT) {
            printTouchEvent("onUpEvent  :", x, y, eventTime)
        }

        getTimerProxy().cancelUpdateBatchInputTimer(this)
        if (!sInGesture) {
            if (mCurrentKey?.isModifier() == true) {
                sPointerTrackerQueue.releaseAllPointersExcept(this, eventTime)
            } else {
                sPointerTrackerQueue.releaseAllPointersOlderThan(this, eventTime)
            }
        }
        onUpEventInternal(x, y, eventTime)
        sPointerTrackerQueue.remove(this)
    }

    override fun onPhantomUpEvent(eventTime: Long) {
        if (DEBUG_EVENT) {
            printTouchEvent("onPhntEvent:", mLastX, mLastY, eventTime)
        }
        onUpEventInternal(mLastX, mLastY, eventTime)
        cancelTrackingForAction()
    }

    private fun onUpEventInternal(x: Int, y: Int, eventTime: Long) {
        getTimerProxy().cancelKeyTimersOf(this)
        val isInDraggingFinger = mIsInDraggingFinger
        val isInSlidingKeyInput = mIsInSlidingKeyInput
        resetKeySelectionByDraggingFinger()
        mIsDetectingGesture = false
        val currentKey = mCurrentKey
        mCurrentKey = null
        val currentRepeatingKeyCode = mCurrentRepeatingKeyCode
        mCurrentRepeatingKeyCode = Constants.NOT_A_CODE
        setReleasedKeyGraphics(currentKey, true)

        if (mInHorizontalSwipe && currentKey?.code == KeyCode.DELETE) {
            sListener.onUpWithDeletePointerActive()
        }

        if (isShowingPopupKeysPanel()) {
            if (!mIsTrackingForActionDisabled) {
                val panel = mPopupKeysPanel
                if (panel != null) {
                    val translatedX = panel.translateX(x)
                    val translatedY = panel.translateY(y)
                    panel.onUpEvent(translatedX, translatedY, mPointerId, eventTime)
                }
            }
            dismissPopupKeysPanel()
            if (isInSlidingKeyInput) {
                callListenerOnFinishSlidingInput()
            }
            return
        }

        if (mKeySwipeAllowed) {
            mKeySwipeAllowed = false
            sInKeySwipe = false

            val wasTouchpad = mInTouchpadMode
            if (mInTouchpadMode) {
                mInTouchpadMode = false
                if (!sPersistentTouchpadModeActive) {
                    sTouchpadModeActive = false
                    sListener.onCustomRequest(KeyboardActionListener.CODE_TOUCHPAD_OFF)
                }
            }

            if (mInHorizontalSwipe || mInVerticalSwipe || wasTouchpad) {
                mInHorizontalSwipe = false
                mInVerticalSwipe = false
                sListener.onEndSpaceSwipe()
                return
            }
        }

        if (sInGesture) {
            if (currentKey != null) {
                callListenerOnRelease(currentKey, currentKey.code, true)
            }
            if (mBatchInputArbiter.mayEndBatchInput(eventTime, getActivePointerTrackerCount(), this)) {
                sInGesture = false
            }
            showGestureTrail()
            return
        }

        if (mIsTrackingForActionDisabled) {
            return
        }
        if (currentKey != null && currentKey.isRepeatable() && (currentKey.code == currentRepeatingKeyCode) && !isInDraggingFinger) {
            return
        }
        detectAndSendKey(currentKey, mKeyX, mKeyY, eventTime)
        if (isInSlidingKeyInput) {
            callListenerOnFinishSlidingInput()
        }
    }

    override fun cancelTrackingForAction() {
        if (isShowingPopupKeysPanel()) {
            return
        }
        mIsTrackingForActionDisabled = true
    }

    val isInOperation: Boolean get() = !mIsTrackingForActionDisabled

    fun onLongPressed() {
        getTimerProxy().cancelLongPressTimersOf(this)
        if (isShowingPopupKeysPanel()) {
            return
        }
        val key = key ?: return
        sListener.onLongPressKey(key.code)
        if (key.hasNoPanelAutoPopupKey()) {
            cancelKeyTracking()
            val popupKey = key.popupKeys?.getOrNull(0) ?: return
            val popupKeyCode = popupKey.mCode
            sListener.onPressKey(popupKeyCode, 0, true, HapticEvent.NO_HAPTICS)
            sListener.onCodeInput(popupKeyCode, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
            sListener.onReleaseKey(popupKeyCode, false)
            return
        }
        val code = key.code
        val sv = Settings.getValues()
        if (code == KeyCode.LANGUAGE_SWITCH || (code == Constants.CODE_SPACE && key.popupKeys == null && sv.mSpaceForLangChange)) {
            if (sListener.onCustomRequest(Constants.CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER)) {
                cancelKeyTracking()
                sListener.onReleaseKey(code, false)
                return
            }
        }
        if (code == KeyCode.SYMBOL_ALPHA && sv.mLongPressSymbolsForNumpad) {
            sListener.toggleNumpad(true, true)
            return
        }

        setReleasedKeyGraphics(key, false)
        val drawingProxy = sDrawingProxy ?: return
        val popupKeysPanel = drawingProxy.showPopupKeysKeyboard(key, this) ?: return
        val translatedX = popupKeysPanel.translateX(mLastX)
        val translatedY = popupKeysPanel.translateY(mLastY)
        popupKeysPanel.onDownEvent(translatedX, translatedY, mPointerId, SystemClock.uptimeMillis())
        mPopupKeysPanel = popupKeysPanel
        if (mKeySwipeAllowed) {
            mKeySwipeAllowed = false
            sInKeySwipe = false
        }
    }

    private fun cancelKeyTracking() {
        resetKeySelectionByDraggingFinger()
        cancelTrackingForAction()
        setReleasedKeyGraphics(mCurrentKey, true)
        sPointerTrackerQueue.remove(this)
    }

    private fun onCancelEvent(x: Int, y: Int, eventTime: Long) {
        if (DEBUG_EVENT) {
            printTouchEvent("onCancelEvt:", x, y, eventTime)
        }

        cancelBatchInput()
        cancelAllPointerTrackers()
        sPointerTrackerQueue.releaseAllPointers(eventTime)
        onCancelEventInternal()
    }

    private fun onCancelEventInternal() {
        getTimerProxy().cancelKeyTimersOf(this)
        setReleasedKeyGraphics(mCurrentKey, true)
        resetKeySelectionByDraggingFinger()
        dismissPopupKeysPanel()
    }

    private fun isMajorEnoughMoveToBeOnNewKey(
        x: Int, y: Int, eventTime: Long, newKey: Key?
    ): Boolean {
        val curKey = mCurrentKey
        if (newKey === curKey) {
            return false
        }
        if (curKey == null) {
            return true
        }
        val keyHysteresisDistanceSquared = mKeyDetector.getKeyHysteresisDistanceSquared(mIsInSlidingKeyInput)
        val distanceFromKeyEdgeSquared = curKey.squaredDistanceToEdge(x, y)
        val mostCommonKeyWidth = mKeyboard?.mMostCommonKeyWidth ?: 0
        if (distanceFromKeyEdgeSquared >= keyHysteresisDistanceSquared) {
            if (DEBUG_MODE && mostCommonKeyWidth > 0) {
                val distanceToEdgeRatio = sqrt(distanceFromKeyEdgeSquared.toDouble()).toFloat() / mostCommonKeyWidth
                Log.d(TAG, String.format(Locale.US, "[%d] isMajorEnoughMoveToBeOnNewKey: %.2f key width from key edge",
                    mPointerId, distanceToEdgeRatio))
            }
            return true
        }
        if (!mIsAllowedDraggingFinger && sTypingTimeRecorder?.isInFastTyping(eventTime) == true &&
            mBogusMoveEventDetector.hasTraveledLongDistance(x, y)) {
            if (DEBUG_MODE) {
                val mostCommonKeyHeight = mKeyboard?.mMostCommonKeyHeight ?: 0
                val keyDiagonal = hypot(mostCommonKeyWidth.toDouble(), mostCommonKeyHeight.toDouble()).toFloat()
                if (keyDiagonal > 0f) {
                    val lengthFromDownRatio = mBogusMoveEventDetector.getAccumulatedDistanceFromDownKey() / keyDiagonal
                    Log.d(TAG, String.format(Locale.US, "[%d] isMajorEnoughMoveToBeOnNewKey: %.2f key diagonal from virtual down point",
                        mPointerId, lengthFromDownRatio))
                }
            }
            return true
        }
        return false
    }

    private fun startLongPressTimer(key: Key?) {
        getTimerProxy().cancelLongPressShiftKeyTimer()
        if (sInGesture || key == null || !key.isLongPressEnabled) {
            return
        }
        if (mIsInDraggingFinger && key.popupKeys == null) {
            return
        }

        val delay = getLongPressTimeout(key.code)
        if (delay <= 0) {
            return
        }
        getTimerProxy().startLongPressTimerOf(this, delay)
    }

    private fun getLongPressTimeout(code: Int): Int {
        val longpressTimeout = Settings.getValues().mKeyLongpressTimeout
        return when {
            code == KeyCode.SHIFT || code == KeyCode.SYMBOL_ALPHA -> longpressTimeout * 3 / 2
            mIsInSlidingKeyInput -> longpressTimeout * 3
            else -> longpressTimeout
        }
    }

    private fun isClearlyInsideKey(key: Key, x: Int, y: Int): Boolean {
        return x > key.x + key.width * 0.15 && x < key.x + key.width * 0.85 &&
                y > key.y + key.height * 0.15 && y < key.y + key.height * 0.85
    }

    private fun detectAndSendKey(key: Key?, x: Int, y: Int, eventTime: Long) {
        if (key == null) {
            callListenerOnCancelInput()
            return
        }

        val code = key.code
        callListenerOnCodeInput(key, code, x, y, eventTime, false)
        callListenerOnRelease(key, code, false)
    }

    private fun startRepeatKey(key: Key?) {
        if (sInGesture || key == null || !key.isRepeatable() || mIsInDraggingFinger) {
            return
        }
        val startRepeatCount = 1
        startKeyRepeatTimer(startRepeatCount)
    }

    fun onKeyRepeat(code: Int, repeatCount: Int) {
        val key = key
        if (key == null || key.code != code) {
            mCurrentRepeatingKeyCode = Constants.NOT_A_CODE
            return
        }
        mCurrentRepeatingKeyCode = code
        if (mKeySwipeAllowed) {
            mKeySwipeAllowed = false
            sInKeySwipe = false
        }
        mIsDetectingGesture = false
        val nextRepeatCount = repeatCount + 1
        startKeyRepeatTimer(nextRepeatCount)
        callListenerOnPressAndCheckKeyboardLayoutChange(key, repeatCount)
        callListenerOnCodeInput(key, code, mKeyX, mKeyY, SystemClock.uptimeMillis(), true)
    }

    private fun startKeyRepeatTimer(repeatCount: Int) {
        val params = sParams
        val delay = if (repeatCount == 1) params?.mKeyRepeatStartTimeout ?: 0 else params?.mKeyRepeatInterval ?: 0
        getTimerProxy().startKeyRepeatTimerOf(this, repeatCount, delay)
    }

    private fun printTouchEvent(title: String, x: Int, y: Int, eventTime: Long) {
        val key = mKeyDetector.detectHitKey(x, y)
        val code = if (key == null) "none" else Constants.printableCode(key.code)
        Log.d(TAG, String.format(Locale.US, "[%d]%s%s %4d %4d %5d %s", mPointerId,
            if (mIsTrackingForActionDisabled) "-" else " ", title, x, y, eventTime, code))
    }

    companion object {
        private val TAG = PointerTracker::class.java.simpleName
        private const val DEBUG_EVENT = false
        private const val DEBUG_MOVE_EVENT = false
        private const val DEBUG_LISTENER = false
        private val DEBUG_MODE = DebugFlags.DEBUG_ENABLED || DEBUG_EVENT

        private val sProxyMap = WeakHashMap<DrawingProxy, Array<Any?>>(4)

        @JvmStatic
        fun clearOldViewData() {
            sProxyMap.clear()
            sDrawingProxy = null
        }

        @JvmStatic
        fun switchTo(drawingProxy: DrawingProxy?) {
            if (drawingProxy == null) return
            sDrawingProxy = drawingProxy
            val thatArray = sProxyMap[drawingProxy] ?: return
            sParams = thatArray[0] as? PointerTrackerParams
            sGestureStrokeRecognitionParams = thatArray[1] as? GestureStrokeRecognitionParams
            sGestureStrokeDrawingParams = thatArray[2] as? GestureStrokeDrawingParams
            sTypingTimeRecorder = thatArray[3] as? TypingTimeRecorder
            sTimerProxy = (thatArray[4] as? TimerProxy) ?: TimerProxy.NULL
            @Suppress("UNCHECKED_CAST")
            sTrackers = (thatArray[5] as? ArrayList<PointerTracker>) ?: ArrayList()
        }

        private fun getTimerProxy(): TimerProxy = sTimerProxy

        private val sGestureEnabler = GestureEnabler()

        private var sParams: PointerTrackerParams? = null
        private val sPointerStep = 10.dpToPx(Resources.getSystem())
        private var sGestureStrokeRecognitionParams: GestureStrokeRecognitionParams? = null
        private var sGestureStrokeDrawingParams: GestureStrokeDrawingParams? = null

        private var sTrackers = ArrayList<PointerTracker>()
        private val sPointerTrackerQueue = PointerTrackerQueue()

        private var sDrawingProxy: DrawingProxy? = null
        private var sTimerProxy: TimerProxy = TimerProxy.NULL
        private var sListener: KeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER

        private var sInGesture = false
        private var sTypingTimeRecorder: TypingTimeRecorder? = null
        private var sInKeySwipe = false

        @JvmField
        var sPersistentTouchpadModeActive = false
        private var sTouchpadModeActive = false

        @Volatile
        private var sCachedTouchpadSensitivity = -1
        @Volatile
        private var sLastTouchpadSensitivityUpdateTime = 0L
        private const val TOUCHPAD_SENSITIVITY_UPDATE_INTERVAL_MS = 100
        private const val TOUCHPAD_ACCELERATION_FACTOR = 50.0f

        @JvmStatic
        fun setTouchpadModeActive(active: Boolean) {
            sTouchpadModeActive = active
        }

        @JvmStatic
        fun isTouchpadModeActive(): Boolean = sTouchpadModeActive

        @JvmStatic
        fun init(
            mainKeyboardViewAttr: TypedArray,
            timerProxy: TimerProxy?,
            drawingProxy: DrawingProxy
        ) {
            sParams = PointerTrackerParams(mainKeyboardViewAttr)
            sGestureStrokeRecognitionParams = GestureStrokeRecognitionParams(mainKeyboardViewAttr)
            sGestureStrokeDrawingParams = GestureStrokeDrawingParams(mainKeyboardViewAttr)
            sTypingTimeRecorder = TypingTimeRecorder(
                sGestureStrokeRecognitionParams!!.mStaticTimeThresholdAfterFastTyping,
                sParams!!.mSuppressKeyPreviewAfterBatchInputDuration
            )

            val res = mainKeyboardViewAttr.resources
            BogusMoveEventDetector.init(res)

            sTimerProxy = timerProxy ?: TimerProxy.NULL
            sDrawingProxy = drawingProxy
            sTrackers = ArrayList()

            sProxyMap[drawingProxy] = arrayOf(
                sParams,
                sGestureStrokeRecognitionParams,
                sGestureStrokeDrawingParams,
                sTypingTimeRecorder,
                sTimerProxy,
                sTrackers
            )
        }

        @JvmStatic
        fun setMainDictionaryAvailability(mainDictionaryAvailable: Boolean) {
            sGestureEnabler.setMainDictionaryAvailability(mainDictionaryAvailable)
        }

        @JvmStatic
        fun setGestureHandlingEnabledByUser(gestureHandlingEnabledByUser: Boolean) {
            sGestureEnabler.setGestureHandlingEnabledByUser(gestureHandlingEnabledByUser)
        }

        @JvmStatic
        fun setClipboardInlineInputActive(active: Boolean) {
            val changed = sGestureEnabler.setClipboardInlineInputActive(active)
            if (changed && active) {
                sPointerTrackerQueue.cancelAllPointerTrackers()
            }
        }

        @JvmStatic
        fun getPointerTracker(id: Int): PointerTracker {
            val trackers = sTrackers
            for (i in trackers.size..id) {
                val tracker = PointerTracker(i)
                trackers.add(tracker)
            }
            return trackers[id]
        }

        @JvmStatic
        fun isAnyInDraggingFinger(): Boolean = sPointerTrackerQueue.isAnyInDraggingFinger()

        @JvmStatic
        fun cancelAllPointerTrackers() {
            sPointerTrackerQueue.cancelAllPointerTrackers()
        }

        @JvmStatic
        fun setKeyboardActionListener(listener: KeyboardActionListener) {
            sListener = listener
        }

        @JvmStatic
        fun setKeyDetector(keyDetector: KeyDetector) {
            val keyboard = keyDetector.getKeyboard() ?: return
            val trackersSize = sTrackers.size
            for (i in 0 until trackersSize) {
                val tracker = sTrackers[i]
                tracker.setKeyDetectorInner(keyDetector)
            }
            sGestureEnabler.setPasswordMode(keyboard.mId.passwordInput())
        }

        @JvmStatic
        fun setReleasedKeyGraphicsToAllKeys() {
            val trackersSize = sTrackers.size
            for (i in 0 until trackersSize) {
                val tracker = sTrackers[i]
                tracker.setReleasedKeyGraphics(tracker.key, true)
            }
        }

        @JvmStatic
        fun dismissAllPopupKeysPanels() {
            val trackersSize = sTrackers.size
            for (i in 0 until trackersSize) {
                val tracker = sTrackers[i]
                tracker.dismissPopupKeysPanel()
            }
        }

        @JvmStatic
        fun getActivePointerTrackerCount(): Int = sPointerTrackerQueue.size()

        private fun needsToSuppressKeyPreviewPopup(eventTime: Long): Boolean {
            if (!sGestureEnabler.shouldHandleGesture()) {
                return false
            }
            return sTypingTimeRecorder?.needsToSuppressKeyPreviewPopup(eventTime) == true
        }

        private fun getDistance(x1: Int, y1: Int, x2: Int, y2: Int): Int =
            hypot((x1 - x2).toDouble(), (y1 - y2).toDouble()).toInt()
    }
}
