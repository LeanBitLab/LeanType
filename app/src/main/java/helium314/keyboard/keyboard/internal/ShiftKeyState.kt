/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import helium314.keyboard.latin.utils.Log

class ShiftKeyState(name: String) : ModifierKeyState(name) {

    fun onPressOnShifted() {
        val oldState = mState
        mState = PRESSING_ON_SHIFTED
        if (DEBUG) Log.d(TAG, "$mName.onPressOnShifted: ${toString(oldState)} > $this")
    }

    override fun onOtherKeyPressed() {
        val oldState = mState
        if (oldState == PRESSING) {
            mState = CHORDING
        } else if (oldState == PRESSING_ON_SHIFTED) {
            mState = IGNORING
        }
        if (DEBUG) Log.d(TAG, "$mName.onOtherKeyPressed: ${toString(oldState)} > $this")
    }

    val isPressingOnShifted: Boolean get() = mState == PRESSING_ON_SHIFTED
    val isIgnoring: Boolean get() = mState == IGNORING

    override fun toString(): String = toString(mState)

    override fun toString(state: Int): String = when (state) {
        PRESSING_ON_SHIFTED -> "PRESSING_ON_SHIFTED"
        IGNORING -> "IGNORING"
        else -> super.toString(state)
    }

    companion object {
        const val PRESSING_ON_SHIFTED = 3
        const val IGNORING = 4
    }
}
