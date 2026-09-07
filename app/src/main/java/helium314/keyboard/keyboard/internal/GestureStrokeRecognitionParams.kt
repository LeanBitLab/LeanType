/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.content.res.TypedArray
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.ResourceUtils

class GestureStrokeRecognitionParams {
    @JvmField val mStaticTimeThresholdAfterFastTyping: Int
    @JvmField val mDetectFastMoveSpeedThreshold: Float
    @JvmField val mDynamicThresholdDecayDuration: Int
    @JvmField val mDynamicTimeThresholdFrom: Int
    @JvmField val mDynamicTimeThresholdTo: Int
    @JvmField val mDynamicDistanceThresholdFrom: Float
    @JvmField val mDynamicDistanceThresholdTo: Float
    @JvmField val mSamplingMinimumDistance: Float
    @JvmField val mRecognitionMinimumTime: Int
    @JvmField val mRecognitionSpeedThreshold: Float

    private constructor() {
        mStaticTimeThresholdAfterFastTyping = 500
        mDetectFastMoveSpeedThreshold = 1.5f
        mDynamicThresholdDecayDuration = 450
        mDynamicTimeThresholdFrom = 300
        mDynamicTimeThresholdTo = 20
        mDynamicDistanceThresholdFrom = 6.0f
        mDynamicDistanceThresholdTo = 0.35f
        mSamplingMinimumDistance = 1.0f / 8.0f
        mRecognitionMinimumTime = 100
        mRecognitionSpeedThreshold = 5.5f
    }

    constructor(mainKeyboardViewAttr: TypedArray) {
        mStaticTimeThresholdAfterFastTyping = mainKeyboardViewAttr.getInt(
            R.styleable.MainKeyboardView_gestureStaticTimeThresholdAfterFastTyping,
            DEFAULT.mStaticTimeThresholdAfterFastTyping
        )
        mDetectFastMoveSpeedThreshold = ResourceUtils.getFraction(
            mainKeyboardViewAttr,
            R.styleable.MainKeyboardView_gestureDetectFastMoveSpeedThreshold,
            DEFAULT.mDetectFastMoveSpeedThreshold
        )
        mDynamicThresholdDecayDuration = mainKeyboardViewAttr.getInt(
            R.styleable.MainKeyboardView_gestureDynamicThresholdDecayDuration,
            DEFAULT.mDynamicThresholdDecayDuration
        )
        mDynamicTimeThresholdFrom = mainKeyboardViewAttr.getInt(
            R.styleable.MainKeyboardView_gestureDynamicTimeThresholdFrom,
            DEFAULT.mDynamicTimeThresholdFrom
        )
        mDynamicTimeThresholdTo = mainKeyboardViewAttr.getInt(
            R.styleable.MainKeyboardView_gestureDynamicTimeThresholdTo,
            DEFAULT.mDynamicTimeThresholdTo
        )
        mDynamicDistanceThresholdFrom = ResourceUtils.getFraction(
            mainKeyboardViewAttr,
            R.styleable.MainKeyboardView_gestureDynamicDistanceThresholdFrom,
            DEFAULT.mDynamicDistanceThresholdFrom
        )
        mDynamicDistanceThresholdTo = ResourceUtils.getFraction(
            mainKeyboardViewAttr,
            R.styleable.MainKeyboardView_gestureDynamicDistanceThresholdTo,
            DEFAULT.mDynamicDistanceThresholdTo
        )
        mSamplingMinimumDistance = ResourceUtils.getFraction(
            mainKeyboardViewAttr,
            R.styleable.MainKeyboardView_gestureSamplingMinimumDistance,
            DEFAULT.mSamplingMinimumDistance
        )
        mRecognitionMinimumTime = mainKeyboardViewAttr.getInt(
            R.styleable.MainKeyboardView_gestureRecognitionMinimumTime,
            DEFAULT.mRecognitionMinimumTime
        )
        mRecognitionSpeedThreshold = ResourceUtils.getFraction(
            mainKeyboardViewAttr,
            R.styleable.MainKeyboardView_gestureRecognitionSpeedThreshold,
            DEFAULT.mRecognitionSpeedThreshold
        )
    }

    companion object {
        @JvmField val DEFAULT: GestureStrokeRecognitionParams = GestureStrokeRecognitionParams()
    }
}
