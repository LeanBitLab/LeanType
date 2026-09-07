/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.content.res.TypedArray
import helium314.keyboard.latin.R

class GestureStrokeDrawingParams(mainKeyboardViewAttr: TypedArray) {
    val mMinSamplingDistance: Double
    val mMaxInterpolationAngularThreshold: Double
    val mMaxInterpolationDistanceThreshold: Double
    val mMaxInterpolationSegments: Int

    init {
        mMinSamplingDistance = mainKeyboardViewAttr.getDimension(
            R.styleable.MainKeyboardView_gestureTrailMinSamplingDistance,
            DEFAULT_MIN_SAMPLING_DISTANCE
        ).toDouble()

        val interpolationAngularDegree = mainKeyboardViewAttr.getInteger(
            R.styleable.MainKeyboardView_gestureTrailMaxInterpolationAngularThreshold, 0
        )
        mMaxInterpolationAngularThreshold = if (interpolationAngularDegree <= 0) {
            Math.toRadians(DEFAULT_MAX_INTERPOLATION_ANGULAR_THRESHOLD.toDouble())
        } else {
            Math.toRadians(interpolationAngularDegree.toDouble())
        }

        mMaxInterpolationDistanceThreshold = mainKeyboardViewAttr.getDimension(
            R.styleable.MainKeyboardView_gestureTrailMaxInterpolationDistanceThreshold,
            DEFAULT_MAX_INTERPOLATION_DISTANCE_THRESHOLD
        ).toDouble()

        mMaxInterpolationSegments = mainKeyboardViewAttr.getInteger(
            R.styleable.MainKeyboardView_gestureTrailMaxInterpolationSegments,
            DEFAULT_MAX_INTERPOLATION_SEGMENTS
        )
    }

    companion object {
        private const val DEFAULT_MIN_SAMPLING_DISTANCE = 0.0f // dp
        private const val DEFAULT_MAX_INTERPOLATION_ANGULAR_THRESHOLD = 15 // in degree
        private const val DEFAULT_MAX_INTERPOLATION_DISTANCE_THRESHOLD = 0.0f // dp
        private const val DEFAULT_MAX_INTERPOLATION_SEGMENTS = 4
    }
}
