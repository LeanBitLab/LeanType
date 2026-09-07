/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.common.ResizableIntArray
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import java.util.Locale
import kotlin.math.hypot

class GestureStrokeRecognitionPoints(
    private val mPointerId: Int,
    private val mRecognitionParams: GestureStrokeRecognitionParams
) {
    private val mEventTimes = ResizableIntArray(Constants.DEFAULT_GESTURE_POINTS_CAPACITY)
    private val mXCoordinates = ResizableIntArray(Constants.DEFAULT_GESTURE_POINTS_CAPACITY)
    private val mYCoordinates = ResizableIntArray(Constants.DEFAULT_GESTURE_POINTS_CAPACITY)

    private var mKeyWidth = 0
    private var mMinYCoordinate = 0
    private var mMaxYCoordinate = 0
    private var mDetectFastMoveSpeedThreshold = 0
    private var mDetectFastMoveTime = 0
    private var mDetectFastMoveX = 0
    private var mDetectFastMoveY = 0
    private var mAfterFastTyping = false
    private var mGestureDynamicDistanceThresholdFrom = 0
    private var mGestureDynamicDistanceThresholdTo = 0
    private var mGestureSamplingMinimumDistance = 0
    private var mLastMajorEventTime: Long = 0
    private var mLastMajorEventX = 0
    private var mLastMajorEventY = 0
    private var mGestureRecognitionSpeedThreshold = 0
    private var mIncrementalRecognitionSize = 0
    private var mLastIncrementalBatchSize = 0

    fun setKeyboardGeometry(keyWidth: Int, keyboardHeight: Int) {
        mKeyWidth = keyWidth
        mMinYCoordinate = -(keyboardHeight * EXTRA_GESTURE_TRAIL_AREA_ABOVE_KEYBOARD_RATIO).toInt()
        mMaxYCoordinate = keyboardHeight
        mDetectFastMoveSpeedThreshold = (keyWidth * mRecognitionParams.mDetectFastMoveSpeedThreshold).toInt()
        mGestureDynamicDistanceThresholdFrom = (keyWidth * mRecognitionParams.mDynamicDistanceThresholdFrom).toInt()
        mGestureDynamicDistanceThresholdTo = (keyWidth * mRecognitionParams.mDynamicDistanceThresholdTo).toInt()
        mGestureSamplingMinimumDistance = (keyWidth * mRecognitionParams.mSamplingMinimumDistance).toInt()
        mGestureRecognitionSpeedThreshold = (keyWidth * mRecognitionParams.mRecognitionSpeedThreshold).toInt()
        if (DEBUG) {
            Log.d(TAG, String.format(Locale.US,
                "[%d] setKeyboardGeometry: keyWidth=%3d tT=%3d >> %3d tD=%3d >> %3d",
                mPointerId, keyWidth,
                mRecognitionParams.mDynamicTimeThresholdFrom,
                mRecognitionParams.mDynamicTimeThresholdTo,
                mGestureDynamicDistanceThresholdFrom,
                mGestureDynamicDistanceThresholdTo))
        }
    }

    val length: Int
        get() = mEventTimes.length

    fun addDownEventPoint(x: Int, y: Int, elapsedTimeSinceFirstDown: Int, elapsedTimeSinceLastTyping: Int) {
        reset()
        if (elapsedTimeSinceLastTyping < Settings.getValues().mGestureFastTypingCooldown) {
            mAfterFastTyping = true
        }
        if (DEBUG) {
            Log.d(TAG, String.format(Locale.US, "[%d] onDownEvent: dT=%3d%s", mPointerId,
                elapsedTimeSinceLastTyping, if (mAfterFastTyping) " afterFastTyping" else ""))
        }
        addEventPoint(x, y, elapsedTimeSinceFirstDown, true)
    }

    private fun getGestureDynamicDistanceThreshold(deltaTime: Int): Int {
        if (!mAfterFastTyping || deltaTime >= mRecognitionParams.mDynamicThresholdDecayDuration) {
            return mGestureDynamicDistanceThresholdTo
        }
        val decayedThreshold = (mGestureDynamicDistanceThresholdFrom - mGestureDynamicDistanceThresholdTo) * deltaTime / mRecognitionParams.mDynamicThresholdDecayDuration
        return mGestureDynamicDistanceThresholdFrom - decayedThreshold
    }

    private fun getGestureDynamicTimeThreshold(deltaTime: Int): Int {
        if (!mAfterFastTyping || deltaTime >= mRecognitionParams.mDynamicThresholdDecayDuration) {
            return mRecognitionParams.mDynamicTimeThresholdTo
        }
        val decayedThreshold = (mRecognitionParams.mDynamicTimeThresholdFrom - mRecognitionParams.mDynamicTimeThresholdTo) * deltaTime / mRecognitionParams.mDynamicThresholdDecayDuration
        return mRecognitionParams.mDynamicTimeThresholdFrom - decayedThreshold
    }

    fun isStartOfAGesture(): Boolean {
        if (!hasDetectedFastMove()) return false
        val size = length
        if (size <= 0) return false
        val lastIndex = size - 1
        val deltaTime = mEventTimes.get(lastIndex) - mDetectFastMoveTime
        if (deltaTime < 0) return false
        val deltaDistance = getDistance(mXCoordinates.get(lastIndex), mYCoordinates.get(lastIndex), mDetectFastMoveX, mDetectFastMoveY)
        val distanceThreshold = getGestureDynamicDistanceThreshold(deltaTime)
        val timeThreshold = getGestureDynamicTimeThreshold(deltaTime)
        val isStartOfAGesture = deltaTime >= timeThreshold && deltaDistance >= distanceThreshold
        if (DEBUG) {
            Log.d(TAG, String.format(Locale.US, "[%d] isStartOfAGesture: dT=%3d tT=%3d dD=%3d tD=%3d%s%s",
                mPointerId, deltaTime, timeThreshold,
                deltaDistance, distanceThreshold,
                if (mAfterFastTyping) " afterFastTyping" else "",
                if (isStartOfAGesture) " startOfAGesture" else ""))
        }
        return isStartOfAGesture
    }

    fun duplicateLastPointWith(time: Int) {
        val lastIndex = length - 1
        if (lastIndex >= 0) {
            val x = mXCoordinates.get(lastIndex)
            val y = mYCoordinates.get(lastIndex)
            if (DEBUG) {
                Log.d(TAG, String.format(Locale.US, "[%d] duplicateLastPointWith: %d,%d|%d", mPointerId, x, y, time))
            }
            appendPoint(x, y, time)
            updateIncrementalRecognitionSize(x, y, time)
        }
    }

    private fun reset() {
        mIncrementalRecognitionSize = 0
        mLastIncrementalBatchSize = 0
        mEventTimes.length = 0
        mXCoordinates.length = 0
        mYCoordinates.length = 0
        mLastMajorEventTime = 0
        mDetectFastMoveTime = 0
        mAfterFastTyping = false
    }

    private fun appendPoint(x: Int, y: Int, time: Int) {
        val lastIndex = length - 1
        if (lastIndex >= 0 && mEventTimes.get(lastIndex) > time) {
            Log.w(TAG, String.format(Locale.US, "[%d] drop stale event: %d,%d|%d last: %d,%d|%d", mPointerId,
                x, y, time, mXCoordinates.get(lastIndex), mYCoordinates.get(lastIndex), mEventTimes.get(lastIndex)))
            return
        }
        mEventTimes.add(time)
        mXCoordinates.add(x)
        mYCoordinates.add(y)
    }

    private fun updateMajorEvent(x: Int, y: Int, time: Int) {
        mLastMajorEventTime = time.toLong()
        mLastMajorEventX = x
        mLastMajorEventY = y
    }

    private fun hasDetectedFastMove(): Boolean = mDetectFastMoveTime > 0

    private fun detectFastMove(x: Int, y: Int, time: Int): Int {
        val size = length
        val lastIndex = size - 1
        val lastX = mXCoordinates.get(lastIndex)
        val lastY = mYCoordinates.get(lastIndex)
        val dist = getDistance(lastX, lastY, x, y)
        val msecs = time - mEventTimes.get(lastIndex)
        if (msecs > 0) {
            val pixels = getDistance(lastX, lastY, x, y)
            val pixelsPerSec = pixels * MSEC_PER_SEC
            if (DEBUG_SPEED) {
                val speed = pixelsPerSec.toFloat() / msecs / mKeyWidth
                Log.d(TAG, String.format(Locale.US, "[%d] detectFastMove: speed=%5.2f", mPointerId, speed))
            }
            if (!hasDetectedFastMove() && pixelsPerSec > mDetectFastMoveSpeedThreshold * msecs) {
                if (DEBUG) {
                    val speed = pixelsPerSec.toFloat() / msecs / mKeyWidth
                    Log.d(TAG, String.format(Locale.US,
                        "[%d] detectFastMove: speed=%5.2f T=%3d points=%3d fastMove",
                        mPointerId, speed, time, size))
                }
                mDetectFastMoveTime = time
                mDetectFastMoveX = x
                mDetectFastMoveY = y
            }
        }
        return dist
    }

    fun addEventPoint(x: Int, y: Int, time: Int, isMajorEvent: Boolean): Boolean {
        val size = length
        if (size <= 0) {
            appendPoint(x, y, time)
            updateMajorEvent(x, y, time)
        } else {
            val distance = detectFastMove(x, y, time)
            if (distance > mGestureSamplingMinimumDistance) {
                appendPoint(x, y, time)
            }
        }
        if (isMajorEvent) {
            updateIncrementalRecognitionSize(x, y, time)
            updateMajorEvent(x, y, time)
        }
        return y >= mMinYCoordinate && y < mMaxYCoordinate
    }

    private fun updateIncrementalRecognitionSize(x: Int, y: Int, time: Int) {
        val msecs = (time - mLastMajorEventTime).toInt()
        if (msecs <= 0) return
        val pixels = getDistance(mLastMajorEventX, mLastMajorEventY, x, y)
        val pixelsPerSec = pixels * MSEC_PER_SEC
        if (pixelsPerSec < mGestureRecognitionSpeedThreshold * msecs) {
            mIncrementalRecognitionSize = length
        }
    }

    fun hasRecognitionTimePast(currentTime: Long, lastRecognitionTime: Long): Boolean {
        return currentTime > lastRecognitionTime + mRecognitionParams.mRecognitionMinimumTime
    }

    fun appendAllBatchPoints(out: InputPointers) {
        appendBatchPoints(out, length)
    }

    fun appendIncrementalBatchPoints(out: InputPointers) {
        appendBatchPoints(out, mIncrementalRecognitionSize)
    }

    private fun appendBatchPoints(out: InputPointers, size: Int) {
        val length = size - mLastIncrementalBatchSize
        if (length <= 0) return
        out.append(mPointerId, mEventTimes, mXCoordinates, mYCoordinates, mLastIncrementalBatchSize, length)
        mLastIncrementalBatchSize = size
    }

    companion object {
        private const val TAG = "GestureStrokeRecognitionPoints"
        private const val DEBUG = false
        private const val DEBUG_SPEED = false
        const val EXTRA_GESTURE_TRAIL_AREA_ABOVE_KEYBOARD_RATIO = 0.25f
        private const val MSEC_PER_SEC = 1000

        private fun getDistance(x1: Int, y1: Int, x2: Int, y2: Int): Int {
            return hypot((x1 - x2).toDouble(), (y1 - y2).toDouble()).toInt()
        }
    }
}
