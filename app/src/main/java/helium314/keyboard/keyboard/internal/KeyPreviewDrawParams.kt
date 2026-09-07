/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.content.res.TypedArray
import android.view.View
import helium314.keyboard.latin.R

class KeyPreviewDrawParams(mainKeyboardViewAttr: TypedArray) {
    @JvmField val mPreviewOffset: Int
    @JvmField val mPreviewBackgroundResId: Int
    private var mShowPopup: Boolean = true

    private var mVisibleWidth: Int = 0
    private var mVisibleHeight: Int = 0
    private var mVisibleOffset: Int = 0

    init {
        mPreviewOffset = mainKeyboardViewAttr.getDimensionPixelOffset(R.styleable.MainKeyboardView_keyPreviewOffset, 0)
        mPreviewBackgroundResId = mainKeyboardViewAttr.getResourceId(R.styleable.MainKeyboardView_keyPreviewBackground, 0)
    }

    fun setVisibleOffset(previewVisibleOffset: Int) {
        mVisibleOffset = previewVisibleOffset
    }

    fun getVisibleOffset(): Int = mVisibleOffset

    fun setGeometry(previewTextView: View) {
        val previewWidth = previewTextView.measuredWidth
        val previewHeight = previewTextView.measuredHeight
        mVisibleWidth = previewWidth - previewTextView.paddingLeft - previewTextView.paddingRight
        mVisibleHeight = previewHeight - previewTextView.paddingTop - previewTextView.paddingBottom
        setVisibleOffset(-previewTextView.paddingBottom / 2)
    }

    fun getVisibleWidth(): Int = mVisibleWidth

    fun getVisibleHeight(): Int = mVisibleHeight

    fun setPopupEnabled(enabled: Boolean) {
        mShowPopup = enabled
    }

    fun isPopupEnabled(): Boolean = mShowPopup
}
