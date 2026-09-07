/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.PointerTracker

interface TimerProxy {
    fun startTypingStateTimer(typedKey: Key)
    fun isTypingState(): Boolean
    fun startKeyRepeatTimerOf(tracker: PointerTracker, repeatCount: Int, delay: Int)
    fun startLongPressTimerOf(tracker: PointerTracker, delay: Int)
    fun cancelLongPressTimersOf(tracker: PointerTracker)
    fun cancelLongPressShiftKeyTimer()
    fun cancelKeyTimersOf(tracker: PointerTracker)
    fun startDoubleTapShiftKeyTimer()
    fun cancelDoubleTapShiftKeyTimer()
    fun isInDoubleTapShiftKeyTimeout(): Boolean
    fun startUpdateBatchInputTimer(tracker: PointerTracker)
    fun cancelUpdateBatchInputTimer(tracker: PointerTracker)
    fun cancelAllUpdateBatchInputTimers()

    private class Adapter : TimerProxy {
        override fun startTypingStateTimer(typedKey: Key) {}
        override fun isTypingState(): Boolean = false
        override fun startKeyRepeatTimerOf(tracker: PointerTracker, repeatCount: Int, delay: Int) {}
        override fun startLongPressTimerOf(tracker: PointerTracker, delay: Int) {}
        override fun cancelLongPressTimersOf(tracker: PointerTracker) {}
        override fun cancelLongPressShiftKeyTimer() {}
        override fun cancelKeyTimersOf(tracker: PointerTracker) {}
        override fun startDoubleTapShiftKeyTimer() {}
        override fun cancelDoubleTapShiftKeyTimer() {}
        override fun isInDoubleTapShiftKeyTimeout(): Boolean = false
        override fun startUpdateBatchInputTimer(tracker: PointerTracker) {}
        override fun cancelUpdateBatchInputTimer(tracker: PointerTracker) {}
        override fun cancelAllUpdateBatchInputTimers() {}
    }

    companion object {
        val NULL: TimerProxy = Adapter()
    }
}
