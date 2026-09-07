/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.widget.RelativeLayout
import helium314.keyboard.latin.common.CoordinateUtils

class DrawingPreviewPlacerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs) {

    private val mKeyboardViewOrigin: IntArray = CoordinateUtils.newInstance()
    private val mPreviews = ArrayList<AbstractDrawingPreview>()

    init {
        setWillNotDraw(false)
    }

    fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        if (!enabled) return
        val layerPaint = Paint()
        layerPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        setLayerType(LAYER_TYPE_HARDWARE, layerPaint)
    }

    fun addPreview(preview: AbstractDrawingPreview) {
        if (!mPreviews.contains(preview)) {
            mPreviews.add(preview)
        }
    }

    fun setKeyboardViewGeometry(originCoords: IntArray, width: Int, height: Int) {
        CoordinateUtils.copy(mKeyboardViewOrigin, originCoords)
        val count = mPreviews.size
        for (i in 0 until count) {
            mPreviews[i].setKeyboardViewGeometry(originCoords, width, height)
        }
    }

    fun deallocateMemory() {
        val count = mPreviews.size
        for (i in 0 until count) {
            mPreviews[i].onDeallocateMemory()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        deallocateMemory()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val originX = CoordinateUtils.x(mKeyboardViewOrigin)
        val originY = CoordinateUtils.y(mKeyboardViewOrigin)
        canvas.translate(originX.toFloat(), originY.toFloat())
        val count = mPreviews.size
        for (i in 0 until count) {
            mPreviews[i].drawPreview(canvas)
        }
        canvas.translate(-originX.toFloat(), -originY.toFloat())
    }
}
