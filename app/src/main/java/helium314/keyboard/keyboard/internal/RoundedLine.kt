/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class RoundedLine {
    private val mArc1 = RectF()
    private val mArc2 = RectF()
    private val mPath = Path()

    fun makePath(p1x: Float, p1y: Float, r1: Float, p2x: Float, p2y: Float, r2: Float): Path {
        mPath.rewind()
        val dx = (p2x - p1x).toDouble()
        val dy = (p2y - p1y).toDouble()
        val l = hypot(dx, dy)

        if (l == 0.0) {
            return mPath
        }

        val a = atan2(dy, dx)
        val dr = (r2 - r1).toDouble()
        val ar = asin(dr / l)
        val aa = a - (RIGHT_ANGLE + ar)
        val ab = a + (RIGHT_ANGLE + ar)

        val cosa = cos(aa).toFloat()
        val sina = sin(aa).toFloat()
        val cosb = cos(ab).toFloat()
        val sinb = sin(ab).toFloat()

        val p1ax = p1x + r1 * cosa
        val p1ay = p1y + r1 * sina
        val p1bx = p1x + r1 * cosb
        val p1by = p1y + r1 * sinb
        val p2ax = p2x + r2 * cosa
        val p2ay = p2y + r2 * sina
        val p2bx = p2x + r2 * cosb
        val p2by = p2y + r2 * sinb

        val angle = (aa * RADIAN_TO_DEGREE).toFloat()
        val ar2degree = (ar * 2.0 * RADIAN_TO_DEGREE).toFloat()
        val a1 = -180.0f + ar2degree
        val a2 = 180.0f + ar2degree

        mArc1.set(p1x, p1y, p1x, p1y)
        mArc1.inset(-r1, -r1)
        mArc2.set(p2x, p2y, p2x, p2y)
        mArc2.inset(-r2, -r2)

        mPath.moveTo(p1x, p1y)
        mPath.arcTo(mArc1, angle, a1)
        mPath.moveTo(p2x, p2y)
        mPath.arcTo(mArc2, angle, a2)

        mPath.moveTo(p1ax, p1ay)
        mPath.lineTo(p1x, p1y)
        mPath.lineTo(p1bx, p1by)
        mPath.lineTo(p2bx, p2by)
        mPath.lineTo(p2x, p2y)
        mPath.lineTo(p2ax, p2ay)
        mPath.close()

        return mPath
    }

    fun getBounds(outBounds: Rect) {
        mPath.computeBounds(mArc1, true)
        mArc1.roundOut(outBounds)
    }

    companion object {
        private const val RADIAN_TO_DEGREE = 180.0 / PI
        private const val RIGHT_ANGLE = PI / 2.0
    }
}
