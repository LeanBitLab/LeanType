/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.graphics.Canvas
import android.view.View
import helium314.keyboard.keyboard.PointerTracker

abstract class AbstractDrawingPreview {
    private var mDrawingView: View? = null
    private var mPreviewEnabled: Boolean = false
    private var mHasValidGeometry: Boolean = false

    fun setDrawingView(drawingView: DrawingPreviewPlacerView) {
        mDrawingView = drawingView
        drawingView.addPreview(this)
    }

    protected fun invalidateDrawingView() {
        mDrawingView?.invalidate()
    }

    protected val isPreviewEnabled: Boolean
        get() = mPreviewEnabled && mHasValidGeometry

    fun setPreviewEnabled(enabled: Boolean) {
        mPreviewEnabled = enabled
    }

    open fun setKeyboardViewGeometry(originCoords: IntArray, width: Int, height: Int) {
        mHasValidGeometry = (width > 0 && height > 0)
    }

    abstract fun onDeallocateMemory()
    abstract fun drawPreview(canvas: Canvas)
    abstract fun setPreviewPosition(tracker: PointerTracker)
}
