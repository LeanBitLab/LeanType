/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

class HermiteInterpolator {
    private var mXCoords: IntArray? = null
    private var mYCoords: IntArray? = null
    private var mMinPos = 0
    private var mMaxPos = 0

    // Working variables exposed as public fields for zero-allocation output
    var mP1X = 0
    var mP1Y = 0
    var mP2X = 0
    var mP2Y = 0
    var mSlope1X = 0f
    var mSlope1Y = 0f
    var mSlope2X = 0f
    var mSlope2Y = 0f
    var mInterpolatedX = 0f
    var mInterpolatedY = 0f

    fun reset(xCoords: IntArray, yCoords: IntArray, minPos: Int, maxPos: Int) {
        mXCoords = xCoords
        mYCoords = yCoords
        mMinPos = minPos
        mMaxPos = maxPos
    }

    fun setInterval(p0: Int, p1: Int, p2: Int, p3: Int) {
        val xCoords = mXCoords!!
        val yCoords = mYCoords!!
        mP1X = xCoords[p1]
        mP1Y = yCoords[p1]
        mP2X = xCoords[p2]
        mP2Y = yCoords[p2]

        val ax = mP2X - mP1X
        val ay = mP2Y - mP1Y

        if (p0 >= mMinPos) {
            mSlope1X = (mP2X - xCoords[p0]) / 2.0f
            mSlope1Y = (mP2Y - yCoords[p0]) / 2.0f
        } else if (p3 < mMaxPos) {
            val bx = (xCoords[p3] - mP1X) / 2.0f
            val by = (yCoords[p3] - mP1Y) / 2.0f
            val crossProdAB = ax * by - ay * bx
            val dotProdAB = ax * bx + ay * by
            val normASquare = ax * ax + ay * ay
            val invHalfNormASquare = 1.0f / normASquare / 2.0f
            mSlope1X = invHalfNormASquare * (dotProdAB * ax + crossProdAB * ay)
            mSlope1Y = invHalfNormASquare * (dotProdAB * ay - crossProdAB * ax)
        } else {
            mSlope1X = ax.toFloat()
            mSlope1Y = ay.toFloat()
        }

        if (p3 < mMaxPos) {
            mSlope2X = (xCoords[p3] - mP1X) / 2.0f
            mSlope2Y = (yCoords[p3] - mP1Y) / 2.0f
        } else if (p0 >= mMinPos) {
            val bx = (mP2X - xCoords[p0]) / 2.0f
            val by = (mP2Y - yCoords[p0]) / 2.0f
            val crossProdAB = ax * by - ay * bx
            val dotProdAB = ax * bx + ay * by
            val normASquare = ax * ax + ay * ay
            val invHalfNormASquare = 1.0f / normASquare / 2.0f
            mSlope2X = invHalfNormASquare * (dotProdAB * ax + crossProdAB * ay)
            mSlope2Y = invHalfNormASquare * (dotProdAB * ay - crossProdAB * ax)
        } else {
            mSlope2X = ax.toFloat()
            mSlope2Y = ay.toFloat()
        }
    }

    fun interpolate(t: Float) {
        val omt = 1.0f - t
        val tm2 = 2.0f * t
        val k1 = 1.0f + tm2
        val k2 = 3.0f - tm2
        val omt2 = omt * omt
        val t2 = t * t
        mInterpolatedX = (k1 * mP1X + t * mSlope1X) * omt2 + (k2 * mP2X - omt * mSlope2X) * t2
        mInterpolatedY = (k1 * mP1Y + t * mSlope1Y) * omt2 + (k2 * mP2Y - omt * mSlope2Y) * t2
    }
}
