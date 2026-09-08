/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import helium314.keyboard.latin.utils.Log

open class ModifierKeyState(protected val mName: String) {
    protected var mState = RELEASING

    open fun onPress() {
        val oldState = mState
        mState = PRESSING
        if (DEBUG) Log.d(TAG, "$mName.onPress: ${toString(oldState)} > $this")
    }

    open fun onRelease() {
        val oldState = mState
        mState = RELEASING
        if (DEBUG) Log.d(TAG, "$mName.onRelease: ${toString(oldState)} > $this")
    }

    open fun onOtherKeyPressed() {
        val oldState = mState
        if (oldState == PRESSING) mState = CHORDING
        if (DEBUG) Log.d(TAG, "$mName.onOtherKeyPressed: ${toString(oldState)} > $this")
    }

    val isPressing: Boolean get() = mState == PRESSING
    val isReleasing: Boolean get() = mState == RELEASING
    val isChording: Boolean get() = mState == CHORDING

    override fun toString(): String = toString(mState)

    protected open fun toString(state: Int): String = when (state) {
        RELEASING -> "RELEASING"
        PRESSING -> "PRESSING"
        CHORDING -> "CHORDING"
        else -> "UNKNOWN"
    }

    companion object {
        const val TAG = "ModifierKeyState"
        const val DEBUG = false

        const val RELEASING = 0
        const val PRESSING = 1
        const val CHORDING = 2
    }
}
