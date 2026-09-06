/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.emoji

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class EmojiCategoryPageIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val mPaint = Paint()
    private var mCategoryPageSize = 0
    private var mCurrentCategoryPageId = 0
    private var mOffset = 0.0f

    // package-private in Java, exposed to EmojiPalettesView
    @JvmField
    var mWidth: Int = 0

    fun setColors(foregroundColor: Int, backgroundColor: Int) {
        mPaint.color = foregroundColor
        setBackgroundColor(backgroundColor)
    }

    fun setCategoryPageId(size: Int, id: Int, offset: Float) {
        mCategoryPageSize = size
        mCurrentCategoryPageId = id
        mOffset = offset
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (mCategoryPageSize <= 1) {
            // If the category is not set yet or contains only one category,
            // just clear and return.
            canvas.drawColor(0)
            return
        }
        val height = height.toFloat()
        val leftPadding = paddingLeft.toFloat()
        val width = mWidth - leftPadding - paddingRight
        val unitWidth = width / mCategoryPageSize
        val left = min(unitWidth * mCurrentCategoryPageId + mOffset * unitWidth, width - unitWidth)
        val top = 0.0f
        val right = min(left + unitWidth, width)
        val bottom = height * BOTTOM_MARGIN_RATIO
        canvas.drawRect(left + leftPadding, top, right + leftPadding, bottom, mPaint)
    }

    companion object {
        private const val BOTTOM_MARGIN_RATIO = 1.0f
    }
}
