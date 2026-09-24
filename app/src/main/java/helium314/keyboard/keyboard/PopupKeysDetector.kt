/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard

import helium314.keyboard.latin.settings.Settings

class PopupKeysDetector(private val mSlideAllowance: Float) : KeyDetector() {
    private val mSlideAllowanceSquare: Int = (mSlideAllowance * mSlideAllowance).toInt()

    // Top slide allowance is slightly longer (sqrt(2) times) than other edges.
    private val mSlideAllowanceSquareTop: Int = mSlideAllowanceSquare * 2

    override fun alwaysAllowsKeySelectionByDraggingFinger(): Boolean {
        return true
    }

    override fun detectHitKey(x: Int, y: Int): Key? {
        val keyboard = getKeyboard() ?: return null
        val touchX = getTouchX(x)
        val touchY = getTouchY(y)

        val density = android.content.res.Resources.getSystem().displayMetrics.density
        val extraDownwardAllowance = maxOf(0f, Settings.getValues().mPopupKeysVerticalOffset * density)
        val downwardAllowance = mSlideAllowance + extraDownwardAllowance
        val allowanceSquareDown = (downwardAllowance * downwardAllowance).toInt()

        var nearestKey: Key? = null
        var nearestDist = if (y < 0) mSlideAllowanceSquareTop else allowanceSquareDown
        for (key in keyboard.sortedKeys) {
            val dist = key.squaredDistanceToEdge(touchX, touchY)
            if (dist < nearestDist) {
                nearestKey = key
                nearestDist = dist
            }
        }
        return nearestKey
    }
}
