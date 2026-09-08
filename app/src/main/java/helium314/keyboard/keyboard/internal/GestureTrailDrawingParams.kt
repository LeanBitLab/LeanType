/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.content.res.TypedArray
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

class GestureTrailDrawingParams(mainKeyboardViewAttr: TypedArray) {
    val mTrailColor: Int
    val mTrailStartWidth: Float
    val mTrailEndWidth: Float
    val mTrailBodyRatio: Float
    val mTrailShadowEnabled: Boolean
    val mTrailShadowRatio: Float
    val mFadeoutStartDelay: Int
    val mFadeoutDuration: Int
    val mUpdateInterval: Int
    val mTrailLingerDuration: Int

    init {
        mTrailColor = Settings.getValues().mColors.get(ColorType.GESTURE_TRAIL)
        mTrailStartWidth = mainKeyboardViewAttr.getDimension(R.styleable.MainKeyboardView_gestureTrailStartWidth, 0.0f)
        mTrailEndWidth = mainKeyboardViewAttr.getDimension(R.styleable.MainKeyboardView_gestureTrailEndWidth, 0.0f)

        val PERCENTAGE_INT = 100
        mTrailBodyRatio = mainKeyboardViewAttr.getInt(R.styleable.MainKeyboardView_gestureTrailBodyRatio, PERCENTAGE_INT).toFloat() / PERCENTAGE_INT.toFloat()

        val trailShadowRatioInt = mainKeyboardViewAttr.getInt(R.styleable.MainKeyboardView_gestureTrailShadowRatio, 0)
        mTrailShadowEnabled = trailShadowRatioInt > 0
        mTrailShadowRatio = trailShadowRatioInt.toFloat() / PERCENTAGE_INT.toFloat()

        mFadeoutStartDelay = if (GestureTrailDrawingPoints.DEBUG_SHOW_POINTS) {
            FADEOUT_START_DELAY_FOR_DEBUG
        } else {
            mainKeyboardViewAttr.getInt(R.styleable.MainKeyboardView_gestureTrailFadeoutStartDelay, 0)
        }

        mFadeoutDuration = if (GestureTrailDrawingPoints.DEBUG_SHOW_POINTS) {
            FADEOUT_DURATION_FOR_DEBUG
        } else {
            Settings.getValues().mGestureTrailFadeoutDuration
        }

        mTrailLingerDuration = mFadeoutStartDelay + mFadeoutDuration
        mUpdateInterval = mainKeyboardViewAttr.getInt(R.styleable.MainKeyboardView_gestureTrailUpdateInterval, 0)
    }

    companion object {
        private const val FADEOUT_START_DELAY_FOR_DEBUG = 2000 // millisecond
        private const val FADEOUT_DURATION_FOR_DEBUG = 200 // millisecond
    }
}
