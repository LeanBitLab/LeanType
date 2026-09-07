/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

class TypingTimeRecorder(
    private val mStaticTimeThresholdAfterFastTyping: Int,
    private val mSuppressKeyPreviewAfterBatchInputDuration: Int
) {
    private var mLastTypingTime: Long = 0
    private var mLastLetterTypingTime: Long = 0
    private var mLastBatchInputTime: Long = 0

    fun isInFastTyping(eventTime: Long): Boolean {
        val elapsedTimeSinceLastLetterTyping = eventTime - mLastLetterTypingTime
        return elapsedTimeSinceLastLetterTyping < mStaticTimeThresholdAfterFastTyping
    }

    private fun wasLastInputTyping(): Boolean = mLastTypingTime >= mLastBatchInputTime

    fun onCodeInput(code: Int, eventTime: Long) {
        if (Character.isLetter(code)) {
            if (wasLastInputTyping() || eventTime - mLastTypingTime < mStaticTimeThresholdAfterFastTyping) {
                mLastLetterTypingTime = eventTime
            }
        } else {
            if (eventTime - mLastLetterTypingTime < mStaticTimeThresholdAfterFastTyping) {
                mLastLetterTypingTime = eventTime
            }
        }
        mLastTypingTime = eventTime
    }

    fun onEndBatchInput(eventTime: Long) {
        mLastBatchInputTime = eventTime
    }

    val lastLetterTypingTime: Long get() = mLastLetterTypingTime

    fun needsToSuppressKeyPreviewPopup(eventTime: Long): Boolean =
        !wasLastInputTyping() && eventTime - mLastBatchInputTime < mSuppressKeyPreviewAfterBatchInputDuration
}
