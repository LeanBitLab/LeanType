/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import helium314.keyboard.accessibility.AccessibilityUtils

class GestureEnabler {
    private var mShouldHandleGesture = false
    private var mMainDictionaryAvailable = false
    private var mGestureHandlingEnabledByInputField = false
    private var mGestureHandlingEnabledByUser = false
    private var mClipboardInlineInputActive = false

    private fun updateGestureHandlingMode() {
        mShouldHandleGesture = mMainDictionaryAvailable &&
                mGestureHandlingEnabledByInputField &&
                mGestureHandlingEnabledByUser &&
                !mClipboardInlineInputActive &&
                !AccessibilityUtils.instance.isTouchExplorationEnabled
    }

    fun setClipboardInlineInputActive(active: Boolean): Boolean {
        if (mClipboardInlineInputActive == active) return false
        mClipboardInlineInputActive = active
        updateGestureHandlingMode()
        return true
    }

    fun setMainDictionaryAvailability(mainDictionaryAvailable: Boolean) {
        mMainDictionaryAvailable = mainDictionaryAvailable
        updateGestureHandlingMode()
    }

    fun setGestureHandlingEnabledByUser(gestureHandlingEnabledByUser: Boolean) {
        mGestureHandlingEnabledByUser = gestureHandlingEnabledByUser
        updateGestureHandlingMode()
    }

    fun setPasswordMode(passwordMode: Boolean) {
        mGestureHandlingEnabledByInputField = !passwordMode
        updateGestureHandlingMode()
    }

    fun shouldHandleGesture(): Boolean = mShouldHandleGesture
}
