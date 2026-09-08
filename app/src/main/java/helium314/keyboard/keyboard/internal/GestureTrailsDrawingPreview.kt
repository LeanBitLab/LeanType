/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import helium314.keyboard.keyboard.PointerTracker

class GestureTrailsDrawingPreview(mainKeyboardViewAttr: TypedArray) : AbstractDrawingPreview(), Runnable {
    private val mGestureTrails = SparseArray<GestureTrailDrawingPoints>()
    private val mDrawingParams = GestureTrailDrawingParams(mainKeyboardViewAttr)
    private val mGesturePaint: Paint
    private var mOffscreenWidth = 0
    private var mOffscreenHeight = 0
    private var mOffscreenOffsetY = 0
    private var mOffscreenBuffer: Bitmap? = null
    private val mOffscreenCanvas = Canvas()
    private val mOffscreenSrcRect = Rect()
    private val mDirtyRect = Rect()
    private val mGestureTrailBoundsRect = Rect()

    private val mDrawingHandler = Handler(Looper.getMainLooper())

    init {
        val gesturePaint = Paint()
        gesturePaint.isAntiAlias = true
        gesturePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        mGesturePaint = gesturePaint
    }

    override fun setKeyboardViewGeometry(originCoords: IntArray, width: Int, height: Int) {
        super.setKeyboardViewGeometry(originCoords, width, height)
        mOffscreenOffsetY = (height * GestureStrokeRecognitionPoints.EXTRA_GESTURE_TRAIL_AREA_ABOVE_KEYBOARD_RATIO).toInt()
        mOffscreenWidth = width
        mOffscreenHeight = mOffscreenOffsetY + height
    }

    override fun onDeallocateMemory() {
        freeOffscreenBuffer()
    }

    private fun freeOffscreenBuffer() {
        mOffscreenCanvas.setBitmap(null)
        mOffscreenCanvas.setMatrix(null)
        mOffscreenBuffer?.recycle()
        mOffscreenBuffer = null
    }

    private fun mayAllocateOffscreenBuffer() {
        val buffer = mOffscreenBuffer
        if (buffer != null && buffer.width == mOffscreenWidth && buffer.height == mOffscreenHeight) {
            return
        }
        freeOffscreenBuffer()
        mOffscreenBuffer = Bitmap.createBitmap(mOffscreenWidth, mOffscreenHeight, Bitmap.Config.ARGB_8888)
        mOffscreenCanvas.setBitmap(mOffscreenBuffer)
        mOffscreenCanvas.translate(0f, mOffscreenOffsetY.toFloat())
    }

    private fun drawGestureTrails(offscreenCanvas: Canvas, paint: Paint, dirtyRect: Rect): Boolean {
        if (!dirtyRect.isEmpty()) {
            paint.color = Color.TRANSPARENT
            paint.style = Paint.Style.FILL
            offscreenCanvas.drawRect(dirtyRect, paint)
        }
        dirtyRect.setEmpty()
        var needsUpdatingGestureTrail = false

        synchronized(mGestureTrails) {
            val trailsCount = mGestureTrails.size()
            for (index in 0 until trailsCount) {
                val trail = mGestureTrails.valueAt(index)
                needsUpdatingGestureTrail = needsUpdatingGestureTrail or trail.drawGestureTrail(offscreenCanvas, paint, mGestureTrailBoundsRect, mDrawingParams)
                dirtyRect.union(mGestureTrailBoundsRect)
            }
        }
        return needsUpdatingGestureTrail
    }

    override fun run() {
        invalidateDrawingView()
    }

    override fun drawPreview(canvas: Canvas) {
        if (!isPreviewEnabled) return
        mayAllocateOffscreenBuffer()
        val needsUpdatingGestureTrail = drawGestureTrails(mOffscreenCanvas, mGesturePaint, mDirtyRect)
        if (needsUpdatingGestureTrail) {
            mDrawingHandler.removeCallbacks(this)
            mDrawingHandler.postDelayed(this, mDrawingParams.mUpdateInterval.toLong())
        }
        if (!mDirtyRect.isEmpty()) {
            val buffer = mOffscreenBuffer ?: return
            mOffscreenSrcRect.set(mDirtyRect)
            mOffscreenSrcRect.offset(0, mOffscreenOffsetY)
            canvas.drawBitmap(buffer, mOffscreenSrcRect, mDirtyRect, null)
        }
    }

    override fun setPreviewPosition(tracker: PointerTracker) {
        if (!isPreviewEnabled) return
        val trail: GestureTrailDrawingPoints
        synchronized(mGestureTrails) {
            trail = mGestureTrails.get(tracker.mPointerId) ?: GestureTrailDrawingPoints().also {
                mGestureTrails.put(tracker.mPointerId, it)
            }
        }
        trail.addStroke(tracker.gestureStrokeDrawingPoints, tracker.downTime)
        invalidateDrawingView()
    }
}
