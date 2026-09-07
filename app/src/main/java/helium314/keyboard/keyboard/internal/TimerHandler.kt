/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.os.Message
import android.os.SystemClock
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.PointerTracker
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.utils.LeakGuardHandlerWrapper

class TimerHandler(
    ownerInstance: DrawingProxy,
    private val mIgnoreAltCodeKeyTimeout: Int,
    private val mGestureRecognitionUpdateTime: Int
) : LeakGuardHandlerWrapper<DrawingProxy>(ownerInstance), TimerProxy {

    override fun handleMessage(msg: Message) {
        val drawingProxy = getOwnerInstance() ?: return
        when (msg.what) {
            MSG_TYPING_STATE_EXPIRED -> drawingProxy.startWhileTypingAnimation(DrawingProxy.FADE_IN)
            MSG_REPEAT_KEY -> {
                val tracker = msg.obj as PointerTracker
                tracker.onKeyRepeat(msg.arg1, msg.arg2)
            }
            MSG_LONGPRESS_KEY, MSG_LONGPRESS_SHIFT_KEY -> {
                cancelLongPressTimers()
                val tracker = msg.obj as PointerTracker
                tracker.onLongPressed()
            }
            MSG_UPDATE_BATCH_INPUT -> {
                val tracker = msg.obj as PointerTracker
                tracker.updateBatchInputByTimer(SystemClock.uptimeMillis())
                startUpdateBatchInputTimer(tracker)
            }
            MSG_DISMISS_KEY_PREVIEW -> drawingProxy.onKeyReleased(msg.obj as Key, false)
            MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT -> drawingProxy.dismissGestureFloatingPreviewTextWithoutDelay()
        }
    }

    override fun startKeyRepeatTimerOf(tracker: PointerTracker, repeatCount: Int, delay: Int) {
        val key = tracker.key ?: return
        if (delay == 0) return
        sendMessageDelayed(obtainMessage(MSG_REPEAT_KEY, key.code, repeatCount, tracker), delay.toLong())
    }

    private fun cancelKeyRepeatTimerOf(tracker: PointerTracker) {
        removeMessages(MSG_REPEAT_KEY, tracker)
    }

    fun cancelKeyRepeatTimers() {
        removeMessages(MSG_REPEAT_KEY)
    }

    fun isInKeyRepeat(): Boolean = hasMessages(MSG_REPEAT_KEY)

    override fun startLongPressTimerOf(tracker: PointerTracker, delay: Int) {
        val key = tracker.key ?: return
        val messageId = if (key.code == KeyCode.SHIFT) MSG_LONGPRESS_SHIFT_KEY else MSG_LONGPRESS_KEY
        sendMessageDelayed(obtainMessage(messageId, tracker), delay.toLong())
    }

    override fun cancelLongPressTimersOf(tracker: PointerTracker) {
        removeMessages(MSG_LONGPRESS_KEY, tracker)
        removeMessages(MSG_LONGPRESS_SHIFT_KEY, tracker)
    }

    override fun cancelLongPressShiftKeyTimer() {
        removeMessages(MSG_LONGPRESS_SHIFT_KEY)
    }

    fun cancelLongPressTimers() {
        removeMessages(MSG_LONGPRESS_KEY)
        removeMessages(MSG_LONGPRESS_SHIFT_KEY)
    }

    override fun startTypingStateTimer(typedKey: Key) {
        if (typedKey.isModifier() || typedKey.altCodeWhileTyping()) return

        val isTyping = isTypingState()
        removeMessages(MSG_TYPING_STATE_EXPIRED)
        val drawingProxy = getOwnerInstance() ?: return

        val typedCode = typedKey.code
        if (typedCode == Constants.CODE_SPACE || typedCode == Constants.CODE_ENTER) {
            if (isTyping) drawingProxy.startWhileTypingAnimation(DrawingProxy.FADE_IN)
            return
        }

        sendMessageDelayed(obtainMessage(MSG_TYPING_STATE_EXPIRED), mIgnoreAltCodeKeyTimeout.toLong())
        if (isTyping) return
        drawingProxy.startWhileTypingAnimation(DrawingProxy.FADE_OUT)
    }

    override fun isTypingState(): Boolean = hasMessages(MSG_TYPING_STATE_EXPIRED)

    override fun startDoubleTapShiftKeyTimer() {
        sendMessageDelayed(obtainMessage(MSG_DOUBLE_TAP_SHIFT_KEY), 300L)
    }

    override fun cancelDoubleTapShiftKeyTimer() {
        removeMessages(MSG_DOUBLE_TAP_SHIFT_KEY)
    }

    override fun isInDoubleTapShiftKeyTimeout(): Boolean = hasMessages(MSG_DOUBLE_TAP_SHIFT_KEY)

    override fun cancelKeyTimersOf(tracker: PointerTracker) {
        cancelKeyRepeatTimerOf(tracker)
        cancelLongPressTimersOf(tracker)
    }

    fun cancelAllKeyTimers() {
        cancelKeyRepeatTimers()
        cancelLongPressTimers()
    }

    override fun startUpdateBatchInputTimer(tracker: PointerTracker) {
        if (mGestureRecognitionUpdateTime <= 0) return
        removeMessages(MSG_UPDATE_BATCH_INPUT, tracker)
        sendMessageDelayed(obtainMessage(MSG_UPDATE_BATCH_INPUT, tracker), mGestureRecognitionUpdateTime.toLong())
    }

    override fun cancelUpdateBatchInputTimer(tracker: PointerTracker) {
        removeMessages(MSG_UPDATE_BATCH_INPUT, tracker)
    }

    override fun cancelAllUpdateBatchInputTimers() {
        removeMessages(MSG_UPDATE_BATCH_INPUT)
    }

    fun postDismissKeyPreview(key: Key, delay: Long) {
        sendMessageDelayed(obtainMessage(MSG_DISMISS_KEY_PREVIEW, key), delay)
    }

    fun postDismissGestureFloatingPreviewText(delay: Long) {
        sendMessageDelayed(obtainMessage(MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT), delay)
    }

    fun cancelAllMessages() {
        cancelAllKeyTimers()
        cancelDoubleTapShiftKeyTimer()
        cancelAllUpdateBatchInputTimers()
        removeMessages(MSG_DISMISS_KEY_PREVIEW)
        removeMessages(MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT)
    }

    companion object {
        const val MSG_TYPING_STATE_EXPIRED = 0
        const val MSG_REPEAT_KEY = 1
        const val MSG_LONGPRESS_KEY = 2
        const val MSG_LONGPRESS_SHIFT_KEY = 3
        const val MSG_DOUBLE_TAP_SHIFT_KEY = 4
        const val MSG_UPDATE_BATCH_INPUT = 5
        const val MSG_DISMISS_KEY_PREVIEW = 6
        const val MSG_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT = 7
    }
}
