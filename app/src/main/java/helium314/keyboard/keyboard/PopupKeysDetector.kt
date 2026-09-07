/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard

class PopupKeysDetector(slideAllowance: Float) : KeyDetector() {
    private val mSlideAllowanceSquare: Int = (slideAllowance * slideAllowance).toInt()

    // Top slide allowance is slightly longer (sqrt(2) times) than other edges.
    private val mSlideAllowanceSquareTop: Int = mSlideAllowanceSquare * 2

    override fun alwaysAllowsKeySelectionByDraggingFinger(): Boolean {
        return true
    }

    override fun detectHitKey(x: Int, y: Int): Key? {
        val keyboard = getKeyboard() ?: return null
        val touchX = getTouchX(x)
        val touchY = getTouchY(y)

        var nearestKey: Key? = null
        var nearestDist = if (y < 0) mSlideAllowanceSquareTop else mSlideAllowanceSquare
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
