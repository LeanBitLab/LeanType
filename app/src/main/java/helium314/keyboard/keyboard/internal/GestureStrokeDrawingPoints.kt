/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import helium314.keyboard.latin.common.ResizableIntArray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class GestureStrokeDrawingPoints(private val mDrawingParams: GestureStrokeDrawingParams) {
    private val mPreviewEventTimes = ResizableIntArray(PREVIEW_CAPACITY)
    private val mPreviewXCoordinates = ResizableIntArray(PREVIEW_CAPACITY)
    private val mPreviewYCoordinates = ResizableIntArray(PREVIEW_CAPACITY)

    private var mStrokeId = 0
    private var mLastPreviewSize = 0
    private val mInterpolator = HermiteInterpolator()
    private var mLastInterpolatedPreviewIndex = 0

    private var mLastX = 0
    private var mLastY = 0
    private var mDistanceFromLastSample = 0.0

    fun getGestureStrokeId(): Int = mStrokeId

    fun onDownEvent(x: Int, y: Int, elapsedTimeSinceFirstDown: Int) {
        reset()
        onMoveEvent(x, y, elapsedTimeSinceFirstDown)
    }

    fun onMoveEvent(x: Int, y: Int, elapsedTimeSinceFirstDown: Int) {
        if (needsSampling(x, y)) {
            mPreviewEventTimes.add(elapsedTimeSinceFirstDown)
            mPreviewXCoordinates.add(x)
            mPreviewYCoordinates.add(y)
        }
    }

    fun interpolateStrokeAndReturnStartIndexOfLastSegment(
        lastInterpolatedIndex: Int,
        eventTimes: ResizableIntArray,
        xCoords: ResizableIntArray,
        yCoords: ResizableIntArray,
        types: ResizableIntArray
    ): Int {
        val size = mPreviewEventTimes.length
        val pt = mPreviewEventTimes.primitiveArray
        val px = mPreviewXCoordinates.primitiveArray
        val py = mPreviewYCoordinates.primitiveArray
        mInterpolator.reset(px, py, 0, size)

        var lastInterpolatedDrawIndex = lastInterpolatedIndex
        var d1 = lastInterpolatedIndex

        for (p2 in mLastInterpolatedPreviewIndex + 1 until size) {
            val p1 = p2 - 1
            val p0 = p1 - 1
            val p3 = p2 + 1
            mLastInterpolatedPreviewIndex = p1
            lastInterpolatedDrawIndex = d1
            mInterpolator.setInterval(p0, p1, p2, p3)

            val m1 = atan2(mInterpolator.mSlope1Y.toDouble(), mInterpolator.mSlope1X.toDouble())
            val m2 = atan2(mInterpolator.mSlope2Y.toDouble(), mInterpolator.mSlope2X.toDouble())
            val deltaAngle = abs(angularDiff(m2, m1))

            val segmentsByAngle = ceil(deltaAngle / mDrawingParams.mMaxInterpolationAngularThreshold).toInt()
            val deltaDistance = hypot((mInterpolator.mP1X - mInterpolator.mP2X).toDouble(), (mInterpolator.mP1Y - mInterpolator.mP2Y).toDouble())
            val segmentsByDistance = ceil(deltaDistance / mDrawingParams.mMaxInterpolationDistanceThreshold).toInt()
            val segments = min(mDrawingParams.mMaxInterpolationSegments, max(segmentsByAngle, segmentsByDistance))

            val t1 = eventTimes.get(d1)
            val dt = pt[p2] - pt[p1]
            d1++

            for (i in 1 until segments) {
                val t = i.toFloat() / segments
                mInterpolator.interpolate(t)
                eventTimes.addAt(d1, (dt * t).toInt() + t1)
                xCoords.addAt(d1, mInterpolator.mInterpolatedX.toInt())
                yCoords.addAt(d1, mInterpolator.mInterpolatedY.toInt())
                if (GestureTrailDrawingPoints.DEBUG_SHOW_POINTS) {
                    types.addAt(d1, GestureTrailDrawingPoints.POINT_TYPE_INTERPOLATED)
                }
                d1++
            }

            eventTimes.addAt(d1, pt[p2])
            xCoords.addAt(d1, px[p2])
            yCoords.addAt(d1, py[p2])
            if (GestureTrailDrawingPoints.DEBUG_SHOW_POINTS) {
                types.addAt(d1, GestureTrailDrawingPoints.POINT_TYPE_SAMPLED)
            }
        }
        return lastInterpolatedDrawIndex
    }

    fun appendPreviewStroke(
        eventTimes: ResizableIntArray,
        xCoords: ResizableIntArray,
        yCoords: ResizableIntArray,
        types: ResizableIntArray
    ) {
        val length = mPreviewEventTimes.length - mLastPreviewSize
        if (length <= 0) return

        eventTimes.append(mPreviewEventTimes, mLastPreviewSize, length)
        xCoords.append(mPreviewXCoordinates, mLastPreviewSize, length)
        yCoords.append(mPreviewYCoordinates, mLastPreviewSize, length)
        if (GestureTrailDrawingPoints.DEBUG_SHOW_POINTS) {
            types.fill(GestureTrailDrawingPoints.POINT_TYPE_SAMPLED, types.length, length)
        }
        mLastPreviewSize = mPreviewEventTimes.length
    }

    private fun reset() {
        mStrokeId++
        mLastPreviewSize = 0
        mLastInterpolatedPreviewIndex = 0
        mPreviewEventTimes.length = 0
        mPreviewXCoordinates.length = 0
        mPreviewYCoordinates.length = 0
    }

    private fun needsSampling(x: Int, y: Int): Boolean {
        mDistanceFromLastSample += hypot((x - mLastX).toDouble(), (y - mLastY).toDouble())
        mLastX = x
        mLastY = y
        val isDownEvent = mPreviewEventTimes.length == 0
        if (mDistanceFromLastSample >= mDrawingParams.mMinSamplingDistance || isDownEvent) {
            mDistanceFromLastSample = 0.0
            return true
        }
        return false
    }

    companion object {
        const val PREVIEW_CAPACITY = 256
        private const val TWO_PI = PI * 2.0

        private fun angularDiff(a1: Double, a0: Double): Double {
            var deltaAngle = a1 - a0
            while (deltaAngle > PI) deltaAngle -= TWO_PI
            while (deltaAngle < -PI) deltaAngle += TWO_PI
            return deltaAngle
        }
    }
}
