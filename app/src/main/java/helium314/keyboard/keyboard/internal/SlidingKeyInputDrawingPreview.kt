/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import helium314.keyboard.keyboard.PointerTracker
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.CoordinateUtils
import helium314.keyboard.latin.settings.Settings

class SlidingKeyInputDrawingPreview(mainKeyboardViewAttr: TypedArray) : AbstractDrawingPreview() {
    private val mPreviewBodyRadius: Float
    private var mShowsSlidingKeyInputPreview = false
    private val mPreviewFrom: IntArray = CoordinateUtils.newInstance()
    private val mPreviewTo: IntArray = CoordinateUtils.newInstance()

    private val mRoundedLine = RoundedLine()
    private val mPaint = Paint()

    init {
        val previewColor = Settings.getValues().mColors.get(ColorType.GESTURE_TRAIL)
        val previewRadius = mainKeyboardViewAttr.getDimension(
            R.styleable.MainKeyboardView_slidingKeyInputPreviewWidth, 0f
        ) / 2.0f
        val PERCENTAGE_INT = 100
        val previewBodyRatio = mainKeyboardViewAttr.getInt(
            R.styleable.MainKeyboardView_slidingKeyInputPreviewBodyRatio, PERCENTAGE_INT
        ).toFloat() / PERCENTAGE_INT.toFloat()
        mPreviewBodyRadius = previewRadius * previewBodyRatio

        val previewShadowRatioInt = mainKeyboardViewAttr.getInt(
            R.styleable.MainKeyboardView_slidingKeyInputPreviewShadowRatio, 0
        )
        if (previewShadowRatioInt > 0) {
            val previewShadowRatio = previewShadowRatioInt.toFloat() / PERCENTAGE_INT.toFloat()
            val shadowRadius = previewRadius * previewShadowRatio
            mPaint.setShadowLayer(shadowRadius, 0.0f, 0.0f, previewColor)
        }
        mPaint.color = previewColor
    }

    override fun onDeallocateMemory() {}

    fun dismissSlidingKeyInputPreview() {
        mShowsSlidingKeyInputPreview = false
        invalidateDrawingView()
    }

    override fun drawPreview(canvas: Canvas) {
        if (!isPreviewEnabled || !mShowsSlidingKeyInputPreview) return

        val radius = mPreviewBodyRadius
        val path = mRoundedLine.makePath(
            CoordinateUtils.x(mPreviewFrom).toFloat(),
            CoordinateUtils.y(mPreviewFrom).toFloat(),
            radius,
            CoordinateUtils.x(mPreviewTo).toFloat(),
            CoordinateUtils.y(mPreviewTo).toFloat(),
            radius
        )
        canvas.drawPath(path, mPaint)
    }

    override fun setPreviewPosition(tracker: PointerTracker) {
        tracker.getDownCoordinates(mPreviewFrom)
        tracker.getLastCoordinates(mPreviewTo)
        mShowsSlidingKeyInputPreview = true
        invalidateDrawingView()
    }
}
