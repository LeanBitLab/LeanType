/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.graphics.Typeface
import helium314.keyboard.latin.utils.ResourceUtils

class KeyDrawParams {
    var mTypeface: Typeface = Typeface.DEFAULT
    var mLetterSize: Int = 0
    var mLabelSize: Int = 0
    var mLargeLetterSize: Int = 0
    var mHintLetterSize: Int = 0
    var mShiftedLetterHintSize: Int = 0
    var mHintLabelSize: Int = 0
    var mPreviewTextSize: Int = 0
    var mTextColor: Int = 0
    var mTextInactivatedColor: Int = 0
    var mTextShadowColor: Int = 0
    var mFunctionalTextColor: Int = 0
    var mHintLetterColor: Int = 0
    var mHintLabelColor: Int = 0
    var mShiftedLetterHintInactivatedColor: Int = 0
    var mShiftedLetterHintActivatedColor: Int = 0
    var mPreviewTextColor: Int = 0
    var mHintLabelVerticalAdjustment: Float = 0f
    var mLabelOffCenterRatio: Float = 0f
    var mHintLabelOffCenterRatio: Float = 0f
    var mAnimAlpha: Int = 0

    constructor()

    private constructor(copyFrom: KeyDrawParams) {
        mTypeface = copyFrom.mTypeface
        mLetterSize = copyFrom.mLetterSize
        mLabelSize = copyFrom.mLabelSize
        mLargeLetterSize = copyFrom.mLargeLetterSize
        mHintLetterSize = copyFrom.mHintLetterSize
        mShiftedLetterHintSize = copyFrom.mShiftedLetterHintSize
        mHintLabelSize = copyFrom.mHintLabelSize
        mPreviewTextSize = copyFrom.mPreviewTextSize
        mTextColor = copyFrom.mTextColor
        mTextInactivatedColor = copyFrom.mTextInactivatedColor
        mTextShadowColor = copyFrom.mTextShadowColor
        mFunctionalTextColor = copyFrom.mFunctionalTextColor
        mHintLetterColor = copyFrom.mHintLetterColor
        mHintLabelColor = copyFrom.mHintLabelColor
        mShiftedLetterHintInactivatedColor = copyFrom.mShiftedLetterHintInactivatedColor
        mShiftedLetterHintActivatedColor = copyFrom.mShiftedLetterHintActivatedColor
        mPreviewTextColor = copyFrom.mPreviewTextColor
        mHintLabelVerticalAdjustment = copyFrom.mHintLabelVerticalAdjustment
        mLabelOffCenterRatio = copyFrom.mLabelOffCenterRatio
        mHintLabelOffCenterRatio = copyFrom.mHintLabelOffCenterRatio
        mAnimAlpha = copyFrom.mAnimAlpha
    }

    fun updateParams(keyHeight: Int, attr: KeyVisualAttributes?) {
        if (attr == null) return
        if (attr.mTypeface != null) mTypeface = attr.mTypeface

        mLetterSize = selectTextSizeFromDimensionOrRatio(keyHeight, attr.mLetterSize, attr.mLetterRatio, mLetterSize)
        mLabelSize = selectTextSizeFromDimensionOrRatio(keyHeight, attr.mLabelSize, attr.mLabelRatio, mLabelSize)
        mLargeLetterSize = selectTextSize(keyHeight, attr.mLargeLetterRatio, mLargeLetterSize)
        mHintLetterSize = selectTextSize(keyHeight, attr.mHintLetterRatio, mHintLetterSize)
        mShiftedLetterHintSize = selectTextSize(keyHeight, attr.mShiftedLetterHintRatio, mShiftedLetterHintSize)
        mHintLabelSize = selectTextSize(keyHeight, attr.mHintLabelRatio, mHintLabelSize)
        mPreviewTextSize = selectTextSize(keyHeight, attr.mPreviewTextRatio, mPreviewTextSize)

        mTextColor = selectColor(attr.mTextColor, mTextColor)
        mTextInactivatedColor = selectColor(attr.mTextInactivatedColor, mTextInactivatedColor)
        mTextShadowColor = selectColor(attr.mTextShadowColor, mTextShadowColor)
        mFunctionalTextColor = selectColor(attr.mFunctionalTextColor, mFunctionalTextColor)
        mHintLetterColor = selectColor(attr.mHintLetterColor, mHintLetterColor)
        mHintLabelColor = selectColor(attr.mHintLabelColor, mHintLabelColor)
        mShiftedLetterHintInactivatedColor = selectColor(attr.mShiftedLetterHintInactivatedColor, mShiftedLetterHintInactivatedColor)
        mShiftedLetterHintActivatedColor = selectColor(attr.mShiftedLetterHintActivatedColor, mShiftedLetterHintActivatedColor)
        mPreviewTextColor = selectColor(attr.mPreviewTextColor, mPreviewTextColor)

        mHintLabelVerticalAdjustment = selectFloatIfNonZero(attr.mHintLabelVerticalAdjustment, mHintLabelVerticalAdjustment)
        mLabelOffCenterRatio = selectFloatIfNonZero(attr.mLabelOffCenterRatio, mLabelOffCenterRatio)
        mHintLabelOffCenterRatio = selectFloatIfNonZero(attr.mHintLabelOffCenterRatio, mHintLabelOffCenterRatio)
    }

    fun mayCloneAndUpdateParams(keyHeight: Int, attr: KeyVisualAttributes?): KeyDrawParams {
        if (attr == null) return this
        val newParams = KeyDrawParams(this)
        newParams.updateParams(keyHeight, attr)
        return newParams
    }

    companion object {
        private fun selectTextSizeFromDimensionOrRatio(keyHeight: Int, dimens: Int, ratio: Float, defaultDimens: Int): Int {
            if (ResourceUtils.isValidDimensionPixelSize(dimens)) return dimens
            if (ResourceUtils.isValidFraction(ratio)) return (keyHeight * ratio).toInt()
            return defaultDimens
        }

        private fun selectTextSize(keyHeight: Int, ratio: Float, defaultSize: Int): Int {
            if (ResourceUtils.isValidFraction(ratio)) return (keyHeight * ratio).toInt()
            return defaultSize
        }

        private fun selectColor(attrColor: Int, defaultColor: Int): Int {
            return if (attrColor != 0) attrColor else defaultColor
        }

        private fun selectFloatIfNonZero(attrFloat: Float, defaultFloat: Float): Float {
            return if (attrFloat != 0f) attrFloat else defaultFloat
        }
    }
}
