/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.content.res.Resources
import android.util.DisplayMetrics
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.utils.Log
import kotlin.math.abs
import kotlin.math.hypot

class BogusMoveEventDetector {
    private var mAccumulatedDistanceThreshold = 0
    private var mRadiusThreshold = 0

    var mAccumulatedDistanceFromDownKey = 0
        private set
    private var mActualDownX = 0
    private var mActualDownY = 0

    fun setKeyboardGeometry(keyWidth: Int, keyHeight: Int) {
        val keyDiagonal = hypot(keyWidth.toFloat(), keyHeight.toFloat())
        mAccumulatedDistanceThreshold = (keyDiagonal * BOGUS_MOVE_ACCUMULATED_DISTANCE_THRESHOLD).toInt()
        mRadiusThreshold = (keyDiagonal * BOGUS_MOVE_RADIUS_THRESHOLD).toInt()
    }

    fun onActualDownEvent(x: Int, y: Int) {
        mActualDownX = x
        mActualDownY = y
    }

    fun onDownKey() {
        mAccumulatedDistanceFromDownKey = 0
    }

    fun onMoveKey(distance: Int) {
        mAccumulatedDistanceFromDownKey += distance
    }

    fun hasTraveledLongDistance(x: Int, y: Int): Boolean {
        if (!sNeedsProximateBogusDownMoveUpEventHack) return false
        val dx = abs(x - mActualDownX)
        val dy = abs(y - mActualDownY)
        return dx >= dy && mAccumulatedDistanceFromDownKey >= mAccumulatedDistanceThreshold
    }

    fun getAccumulatedDistanceFromDownKey(): Int = mAccumulatedDistanceFromDownKey

    fun getDistanceFromDownEvent(x: Int, y: Int): Int = getDistance(x, y, mActualDownX, mActualDownY)

    fun isCloseToActualDownEvent(x: Int, y: Int): Boolean =
        sNeedsProximateBogusDownMoveUpEventHack && getDistanceFromDownEvent(x, y) < mRadiusThreshold

    companion object {
        private const val TAG = "BogusMoveEventDetector"
        private const val BOGUS_MOVE_ACCUMULATED_DISTANCE_THRESHOLD = 0.53f
        private const val BOGUS_MOVE_RADIUS_THRESHOLD = 1.14f

        @JvmField
        var sNeedsProximateBogusDownMoveUpEventHack = false

        @JvmStatic
        fun init(res: Resources) {
            val screenMetrics = res.getInteger(R.integer.config_screen_metrics)
            val isLargeTablet = screenMetrics == Constants.SCREEN_METRICS_LARGE_TABLET
            val isSmallTablet = screenMetrics == Constants.SCREEN_METRICS_SMALL_TABLET
            val densityDpi = res.displayMetrics.densityDpi
            val hasLowDensityScreen = densityDpi < DisplayMetrics.DENSITY_HIGH
            val needsTheHack = isLargeTablet || (isSmallTablet && hasLowDensityScreen)

            if (DebugFlags.DEBUG_ENABLED) {
                val sw = res.configuration.smallestScreenWidthDp
                Log.d(TAG, "needsProximateBogusDownMoveUpEventHack=$needsTheHack smallestScreenWidthDp=$sw densityDpi=$densityDpi screenMetrics=$screenMetrics")
            }
            sNeedsProximateBogusDownMoveUpEventHack = needsTheHack
        }

        private fun getDistance(x1: Int, y1: Int, x2: Int, y2: Int): Int =
            hypot((x1 - x2).toDouble(), (y1 - y2).toDouble()).toInt()
    }
}
