/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextUtils
import helium314.keyboard.keyboard.PointerTracker
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.CoordinateUtils
import helium314.keyboard.latin.settings.Settings
import kotlin.math.max
import kotlin.math.min

open class GestureFloatingTextDrawingPreview(mainKeyboardViewAttr: TypedArray) : AbstractDrawingPreview() {

    protected class GesturePreviewTextParams(mainKeyboardViewAttr: TypedArray) {
        @JvmField val mGesturePreviewDynamic: Boolean
        @JvmField val mGesturePreviewTextOffset: Int
        @JvmField val mGesturePreviewTextHeight: Int
        @JvmField val mGesturePreviewHorizontalPadding: Float
        @JvmField val mGesturePreviewVerticalPadding: Float
        @JvmField val mGesturePreviewRoundRadius: Float
        @JvmField val mDisplayWidth: Int

        private val mGesturePreviewTextSize: Int
        private val mGesturePreviewTextColor: Int
        private val mGesturePreviewColor: Int
        private val mPaint = Paint()

        init {
            val colors = Settings.getValues().mColors
            mGesturePreviewDynamic = Settings.getValues().mGestureFloatingPreviewDynamicEnabled
            mGesturePreviewTextSize = mainKeyboardViewAttr.getDimensionPixelSize(
                R.styleable.MainKeyboardView_gestureFloatingPreviewTextSize, 0)
            mGesturePreviewTextColor = colors.get(ColorType.KEY_TEXT)
            mGesturePreviewTextOffset = mainKeyboardViewAttr.getDimensionPixelOffset(
                R.styleable.MainKeyboardView_gestureFloatingPreviewTextOffset, 0)
            mGesturePreviewColor = colors.get(ColorType.GESTURE_PREVIEW)
            mGesturePreviewHorizontalPadding = mainKeyboardViewAttr.getDimension(
                R.styleable.MainKeyboardView_gestureFloatingPreviewHorizontalPadding, 0.0f)
            mGesturePreviewVerticalPadding = mainKeyboardViewAttr.getDimension(
                R.styleable.MainKeyboardView_gestureFloatingPreviewVerticalPadding, 0.0f)
            mGesturePreviewRoundRadius = mainKeyboardViewAttr.getDimension(
                R.styleable.MainKeyboardView_gestureFloatingPreviewRoundRadius, 0.0f)
            mDisplayWidth = mainKeyboardViewAttr.resources.displayMetrics.widthPixels

            val textPaint = textPaint
            val textRect = Rect()
            textPaint.getTextBounds(TEXT_HEIGHT_REFERENCE_CHAR, 0, 1, textRect)
            mGesturePreviewTextHeight = textRect.height()
        }

        val textPaint: Paint
            get() {
                mPaint.isAntiAlias = true
                mPaint.textAlign = Paint.Align.CENTER
                mPaint.textSize = mGesturePreviewTextSize.toFloat()
                mPaint.typeface = Settings.getInstance().customTypeface
                mPaint.color = mGesturePreviewTextColor
                return mPaint
            }

        val backgroundPaint: Paint
            get() {
                mPaint.color = mGesturePreviewColor
                return mPaint
            }

        companion object {
            private val TEXT_HEIGHT_REFERENCE_CHAR = charArrayOf('M')
        }
    }

    private val mParams = GesturePreviewTextParams(mainKeyboardViewAttr)
    private val mGesturePreviewRectangle = RectF()
    private var mPreviewTextX = 0
    private var mPreviewTextY = 0
    private var mSuggestedWords: SuggestedWords = SuggestedWords.getEmptyInstance()
    private val mLastPointerCoords: IntArray = CoordinateUtils.newInstance()

    override fun onDeallocateMemory() {}

    fun dismissGestureFloatingPreviewText() {
        setSuggestedWords(SuggestedWords.getEmptyInstance())
    }

    fun setSuggestedWords(suggestedWords: SuggestedWords) {
        if (!isPreviewEnabled) return
        mSuggestedWords = suggestedWords
        updatePreviewPosition()
    }

    override fun setPreviewPosition(tracker: PointerTracker) {
        if (!isPreviewEnabled) return
        tracker.getLastCoordinates(mLastPointerCoords)
        updatePreviewPosition()
    }

    override fun drawPreview(canvas: Canvas) {
        if (!isPreviewEnabled || mSuggestedWords.isEmpty || TextUtils.isEmpty(mSuggestedWords.getWord(0))) {
            return
        }
        val round = mParams.mGesturePreviewRoundRadius
        canvas.drawRoundRect(mGesturePreviewRectangle, round, round, mParams.backgroundPaint)
        val text = mSuggestedWords.getWord(0)
        canvas.drawText(text, mPreviewTextX.toFloat(), mPreviewTextY.toFloat(), mParams.textPaint)
    }

    protected fun updatePreviewPosition() {
        if (mSuggestedWords.isEmpty || TextUtils.isEmpty(mSuggestedWords.getWord(0))) {
            invalidateDrawingView()
            return
        }
        val text = mSuggestedWords.getWord(0)

        val textHeight = mParams.mGesturePreviewTextHeight
        val textWidth = mParams.textPaint.measureText(text)
        val hPad = mParams.mGesturePreviewHorizontalPadding
        val vPad = mParams.mGesturePreviewVerticalPadding
        val rectWidth = textWidth + hPad * 2.0f
        val rectHeight = textHeight + vPad * 2.0f

        val rectX = if (mParams.mGesturePreviewDynamic) {
            min(max(CoordinateUtils.x(mLastPointerCoords).toFloat() - rectWidth / 2.0f, 0.0f), mParams.mDisplayWidth.toFloat() - rectWidth)
        } else {
            (mParams.mDisplayWidth.toFloat() - rectWidth) / 2.0f
        }
        val rectY = if (mParams.mGesturePreviewDynamic) {
            CoordinateUtils.y(mLastPointerCoords).toFloat() - mParams.mGesturePreviewTextOffset.toFloat() - rectHeight
        } else {
            -mParams.mGesturePreviewTextOffset.toFloat() - rectHeight
        }
        mGesturePreviewRectangle.set(rectX, rectY, rectX + rectWidth, rectY + rectHeight)

        mPreviewTextX = (rectX + hPad + textWidth / 2.0f).toInt()
        mPreviewTextY = (rectY + vPad).toInt() + textHeight
        invalidateDrawingView()
    }
}
