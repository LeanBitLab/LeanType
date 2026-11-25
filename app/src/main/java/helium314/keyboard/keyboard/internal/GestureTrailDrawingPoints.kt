/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import helium314.keyboard.latin.common.ResizableIntArray
import kotlin.math.max
import kotlin.math.roundToInt

class GestureTrailDrawingPoints {
    private val mXCoordinates = ResizableIntArray(DEFAULT_CAPACITY)
    private val mYCoordinates = ResizableIntArray(DEFAULT_CAPACITY)
    private val mEventTimes = ResizableIntArray(DEFAULT_CAPACITY)
    private val mPointTypes = ResizableIntArray(if (DEBUG_SHOW_POINTS) DEFAULT_CAPACITY else 0)

    private var mCurrentStrokeId = -1
    private var mCurrentTimeBase: Long = 0
    private var mTrailStartIndex = 0
    private var mLastInterpolatedDrawIndex = 0

    fun addStroke(stroke: GestureStrokeDrawingPoints, downTime: Long) {
        synchronized(mEventTimes) {
            addStrokeLocked(stroke, downTime)
        }
    }

    private fun addStrokeLocked(stroke: GestureStrokeDrawingPoints, downTime: Long) {
        val trailSize = mEventTimes.length
        stroke.appendPreviewStroke(mEventTimes, mXCoordinates, mYCoordinates, mPointTypes)
        if (mEventTimes.length == trailSize) return

        val eventTimes = mEventTimes.primitiveArray
        val strokeId = stroke.getGestureStrokeId()

        val lastInterpolatedIndex = if (strokeId == mCurrentStrokeId) mLastInterpolatedDrawIndex else trailSize
        mLastInterpolatedDrawIndex = stroke.interpolateStrokeAndReturnStartIndexOfLastSegment(
            lastInterpolatedIndex, mEventTimes, mXCoordinates, mYCoordinates, mPointTypes
        )

        if (strokeId != mCurrentStrokeId) {
            val elapsedTime = (downTime - mCurrentTimeBase).toInt()
            for (i in mTrailStartIndex until trailSize) {
                eventTimes[i] -= elapsedTime
            }
            val xCoords = mXCoordinates.primitiveArray
            xCoords[trailSize] = markAsDownEvent(xCoords[trailSize])
            mCurrentTimeBase = downTime - eventTimes[trailSize]
            mCurrentStrokeId = strokeId
        }
    }

    private val mRoundedLine = RoundedLine()
    private val mRoundedLineBounds = Rect()

    fun drawGestureTrail(
        canvas: Canvas,
        paint: Paint,
        outBoundsRect: Rect,
        params: GestureTrailDrawingParams
    ): Boolean {
        synchronized(mEventTimes) {
            return drawGestureTrailLocked(canvas, paint, outBoundsRect, params)
        }
    }

    private fun drawGestureTrailLocked(
        canvas: Canvas,
        paint: Paint,
        outBoundsRect: Rect,
        params: GestureTrailDrawingParams
    ): Boolean {
        outBoundsRect.setEmpty()
        val trailSize = mEventTimes.length
        if (trailSize == 0) return false

        val eventTimes = mEventTimes.primitiveArray
        val xCoords = mXCoordinates.primitiveArray
        val yCoords = mYCoordinates.primitiveArray
        val pointTypes = mPointTypes.primitiveArray
        val sinceDown = (SystemClock.uptimeMillis() - mCurrentTimeBase).toInt()

        var startIndex = mTrailStartIndex
        while (startIndex < trailSize) {
            val elapsedTime = sinceDown - eventTimes[startIndex]
            if (elapsedTime < params.mTrailLingerDuration) break
            startIndex++
        }
        mTrailStartIndex = startIndex

        if (startIndex < trailSize) {
            paint.color = params.mTrailColor
            paint.style = Paint.Style.FILL
            val roundedLine = mRoundedLine

            var p1x = getXCoordValue(xCoords[startIndex])
            var p1y = yCoords[startIndex]
            val lastTime = sinceDown - eventTimes[startIndex]
            var r1 = getWidth(lastTime, params) / 2.0f

            for (i in startIndex + 1 until trailSize) {
                val elapsedTime = sinceDown - eventTimes[i]
                val p2x = getXCoordValue(xCoords[i])
                val p2y = yCoords[i]
                val r2 = getWidth(elapsedTime, params) / 2.0f

                if (!isDownEventXCoord(xCoords[i])) {
                    val body1 = r1 * params.mTrailBodyRatio
                    val body2 = r2 * params.mTrailBodyRatio
                    val path = roundedLine.makePath(
                        p1x.toFloat(), p1y.toFloat(), body1,
                        p2x.toFloat(), p2y.toFloat(), body2
                    )

                    if (!path.isEmpty) {
                        roundedLine.getBounds(mRoundedLineBounds)
                        if (params.mTrailShadowEnabled) {
                            val shadow2 = r2 * params.mTrailShadowRatio
                            paint.setShadowLayer(shadow2, 0.0f, 0.0f, params.mTrailColor)
                            val shadowInset = -kotlin.math.ceil(shadow2).toInt()
                            mRoundedLineBounds.inset(shadowInset, shadowInset)
                        }
                        outBoundsRect.union(mRoundedLineBounds)
                        paint.alpha = getAlpha(elapsedTime, params)
                        canvas.drawPath(path, paint)
                    }
                }
                p1x = p2x
                p1y = p2y
                r1 = r2
            }

            if (DEBUG_SHOW_POINTS) {
                debugDrawPoints(canvas, startIndex, trailSize, paint)
            }
        }

        val newSize = trailSize - startIndex
        if (newSize < startIndex) {
            mTrailStartIndex = 0
            if (newSize > 0) {
                System.arraycopy(eventTimes, startIndex, eventTimes, 0, newSize)
                System.arraycopy(xCoords, startIndex, xCoords, 0, newSize)
                System.arraycopy(yCoords, startIndex, yCoords, 0, newSize)
                if (DEBUG_SHOW_POINTS) {
                    System.arraycopy(pointTypes, startIndex, pointTypes, 0, newSize)
                }
            }
            mEventTimes.length = newSize
            mXCoordinates.length = newSize
            mYCoordinates.length = newSize
            if (DEBUG_SHOW_POINTS) {
                mPointTypes.length = newSize
            }
            mLastInterpolatedDrawIndex = max(mLastInterpolatedDrawIndex - startIndex, 0)
        }
        return newSize > 0
    }

    private fun debugDrawPoints(canvas: Canvas, startIndex: Int, endIndex: Int, paint: Paint) {
        val xCoords = mXCoordinates.primitiveArray
        val yCoords = mYCoordinates.primitiveArray
        val pointTypes = mPointTypes.primitiveArray

        paint.isAntiAlias = false
        paint.strokeWidth = 0f
        for (i in startIndex until endIndex) {
            paint.color = when (pointTypes[i]) {
                POINT_TYPE_INTERPOLATED -> Color.RED
                POINT_TYPE_SAMPLED -> 0xFFA000FF.toInt()
                else -> Color.GREEN
            }
            canvas.drawPoint(getXCoordValue(xCoords[i]).toFloat(), yCoords[i].toFloat(), paint)
        }
        paint.isAntiAlias = true
    }

    companion object {
        const val DEBUG_SHOW_POINTS = false
        const val POINT_TYPE_SAMPLED = 1
        const val POINT_TYPE_INTERPOLATED = 2

        private val DEFAULT_CAPACITY = GestureStrokeDrawingPoints.PREVIEW_CAPACITY
        private const val DOWN_EVENT_MARKER = -128

        private fun markAsDownEvent(xCoord: Int): Int = DOWN_EVENT_MARKER - xCoord
        private fun isDownEventXCoord(xCoordOrMark: Int): Boolean = xCoordOrMark <= DOWN_EVENT_MARKER
        private fun getXCoordValue(xCoordOrMark: Int): Int =
            if (isDownEventXCoord(xCoordOrMark)) DOWN_EVENT_MARKER - xCoordOrMark else xCoordOrMark

        private fun getAlpha(elapsedTime: Int, params: GestureTrailDrawingParams): Int {
            val fullAlpha = Color.alpha(params.mTrailColor)
            val visible = 1f - smoothStep(progress(elapsedTime - params.mFadeoutStartDelay, params.mFadeoutDuration))
            return (fullAlpha * visible).roundToInt().coerceIn(0, fullAlpha)
        }

        private fun getWidth(elapsedTime: Int, params: GestureTrailDrawingParams): Float =
            trailWidth(params.mTrailStartWidth, params.mTrailEndWidth, elapsedTime, params.mTrailLingerDuration)

        /** Fraction of [duration] that has passed after [elapsed] ms, between 0 and 1. A duration of 0 or less counts as done. */
        // internal for testing
        internal fun progress(elapsed: Int, duration: Int): Float =
            if (elapsed <= 0) 0f else if (duration <= 0 || elapsed >= duration) 1f else elapsed.toFloat() / duration

        /** Eases in and out (zero slope at both ends), so a fade starts and ends softly instead of with a kink. */
        // internal for testing
        internal fun smoothStep(t: Float): Float {
            val x = t.coerceIn(0f, 1f)
            return x * x * (3f - 2f * x)
        }

        /** The head keeps its full width for a moment and the tail thins out gently, instead of a straight taper. */
        // internal for testing
        internal fun trailWidth(startWidth: Float, endWidth: Float, elapsed: Int, lingerDuration: Int): Float =
            startWidth - (startWidth - endWidth) * smoothStep(progress(elapsed, lingerDuration))
    }
}
