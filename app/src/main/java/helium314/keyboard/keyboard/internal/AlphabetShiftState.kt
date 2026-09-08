/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import helium314.keyboard.latin.utils.Log

class AlphabetShiftState {
    private var mState = UNSHIFTED

    fun setShifted(newShiftState: Boolean) {
        val oldState = mState
        if (newShiftState) {
            when (oldState) {
                UNSHIFTED -> mState = MANUAL_SHIFTED
                AUTOMATIC_SHIFTED -> mState = MANUAL_SHIFTED_FROM_AUTO
                SHIFT_LOCKED -> mState = SHIFT_LOCK_SHIFTED
            }
        } else {
            when (oldState) {
                MANUAL_SHIFTED, MANUAL_SHIFTED_FROM_AUTO, AUTOMATIC_SHIFTED -> mState = UNSHIFTED
                SHIFT_LOCK_SHIFTED -> mState = SHIFT_LOCKED
            }
        }
        if (DEBUG) Log.d(TAG, "setShifted($newShiftState): ${toString(oldState)} > $this")
    }

    fun setShiftLocked(newShiftLockState: Boolean) {
        val oldState = mState
        if (newShiftLockState) {
            when (oldState) {
                UNSHIFTED, MANUAL_SHIFTED, MANUAL_SHIFTED_FROM_AUTO, AUTOMATIC_SHIFTED -> mState = SHIFT_LOCKED
            }
        } else {
            mState = UNSHIFTED
        }
        if (DEBUG) Log.d(TAG, "setShiftLocked($newShiftLockState): ${toString(oldState)} > $this")
    }

    fun setAutomaticShifted() {
        val oldState = mState
        mState = AUTOMATIC_SHIFTED
        if (DEBUG) Log.d(TAG, "setAutomaticShifted: ${toString(oldState)} > $this")
    }

    val isShiftedOrShiftLocked: Boolean get() = mState != UNSHIFTED
    val isShiftLocked: Boolean get() = mState == SHIFT_LOCKED || mState == SHIFT_LOCK_SHIFTED
    val isShiftLockShifted: Boolean get() = mState == SHIFT_LOCK_SHIFTED
    val isAutomaticShifted: Boolean get() = mState == AUTOMATIC_SHIFTED
    val isManualShifted: Boolean get() = mState == MANUAL_SHIFTED || mState == MANUAL_SHIFTED_FROM_AUTO || mState == SHIFT_LOCK_SHIFTED
    val isManualShiftedFromAutomaticShifted: Boolean get() = mState == MANUAL_SHIFTED_FROM_AUTO

    override fun toString(): String = toString(mState)

    companion object {
        private const val TAG = "AlphabetShiftState"
        private const val DEBUG = false

        private const val UNSHIFTED = 0
        private const val MANUAL_SHIFTED = 1
        private const val MANUAL_SHIFTED_FROM_AUTO = 2
        private const val AUTOMATIC_SHIFTED = 3
        private const val SHIFT_LOCKED = 4
        private const val SHIFT_LOCK_SHIFTED = 5

        private fun toString(state: Int): String = when (state) {
            UNSHIFTED -> "UNSHIFTED"
            MANUAL_SHIFTED -> "MANUAL_SHIFTED"
            MANUAL_SHIFTED_FROM_AUTO -> "MANUAL_SHIFTED_FROM_AUTO"
            AUTOMATIC_SHIFTED -> "AUTOMATIC_SHIFTED"
            SHIFT_LOCKED -> "SHIFT_LOCKED"
            SHIFT_LOCK_SHIFTED -> "SHIFT_LOCK_SHIFTED"
            else -> "UNKNOWN"
        }
    }
}
