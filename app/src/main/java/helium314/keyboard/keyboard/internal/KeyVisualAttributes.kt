/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.content.res.TypedArray
import android.graphics.Typeface
import android.util.SparseIntArray
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils

class KeyVisualAttributes private constructor(keyAttr: TypedArray) {
    @JvmField val mTypeface: Typeface?
    @JvmField val mLetterRatio: Float
    @JvmField val mLetterSize: Int
    @JvmField val mLabelRatio: Float
    @JvmField val mLabelSize: Int
    @JvmField val mLargeLetterRatio: Float
    @JvmField val mHintLetterRatio: Float
    @JvmField val mShiftedLetterHintRatio: Float
    @JvmField val mHintLabelRatio: Float
    @JvmField val mPreviewTextRatio: Float

    @JvmField val mTextColor: Int
    @JvmField val mTextInactivatedColor: Int
    @JvmField val mTextShadowColor: Int
    @JvmField val mFunctionalTextColor: Int
    @JvmField val mHintLetterColor: Int
    @JvmField val mHintLabelColor: Int
    @JvmField val mShiftedLetterHintInactivatedColor: Int
    @JvmField val mShiftedLetterHintActivatedColor: Int
    @JvmField val mPreviewTextColor: Int

    @JvmField val mHintLabelVerticalAdjustment: Float
    @JvmField val mLabelOffCenterRatio: Float
    @JvmField val mHintLabelOffCenterRatio: Float

    init {
        if (keyAttr.hasValue(R.styleable.Keyboard_Key_keyTypeface)) {
            mTypeface = Typeface.defaultFromStyle(keyAttr.getInt(R.styleable.Keyboard_Key_keyTypeface, Typeface.NORMAL))
        } else {
            mTypeface = null
        }

        mLetterRatio = ResourceUtils.getFraction(keyAttr, R.styleable.Keyboard_Key_keyLetterSize)
        mLetterSize = ResourceUtils.getDimensionPixelSize(keyAttr, R.styleable.Keyboard_Key_keyLetterSize)
        mLabelRatio = ResourceUtils.getFraction(keyAttr, R.styleable.Keyboard_Key_keyLabelSize)
        mLabelSize = ResourceUtils.getDimensionPixelSize(keyAttr, R.styleable.Keyboard_Key_keyLabelSize)
        mLargeLetterRatio = ResourceUtils.getFraction(keyAttr, R.styleable.Keyboard_Key_keyLargeLetterRatio)
        mHintLetterRatio = ResourceUtils.getFraction(keyAttr, R.styleable.Keyboard_Key_keyHintLetterRatio)
        mShiftedLetterHintRatio = ResourceUtils.getFraction(keyAttr, R.styleable.Keyboard_Key_keyShiftedLetterHintRatio)
        mHintLabelRatio = ResourceUtils.getFraction(keyAttr, R.styleable.Keyboard_Key_keyHintLabelRatio)
        mPreviewTextRatio = ResourceUtils.getFraction(keyAttr, R.styleable.Keyboard_Key_keyPreviewTextRatio)

        val colors = Settings.getValues().mColors
        mTextColor = colors.get(ColorType.KEY_TEXT)
        mTextInactivatedColor = keyAttr.getColor(R.styleable.Keyboard_Key_keyTextInactivatedColor, 0)
        mTextShadowColor = keyAttr.getColor(R.styleable.Keyboard_Key_keyTextShadowColor, 0)
        mFunctionalTextColor = colors.get(ColorType.FUNCTIONAL_KEY_TEXT)
        mHintLetterColor = colors.get(ColorType.KEY_HINT_TEXT)
        mHintLabelColor = colors.get(ColorType.KEY_TEXT)
        mShiftedLetterHintInactivatedColor = keyAttr.getColor(R.styleable.Keyboard_Key_keyShiftedLetterHintInactivatedColor, 0)
        mShiftedLetterHintActivatedColor = keyAttr.getColor(R.styleable.Keyboard_Key_keyShiftedLetterHintActivatedColor, 0)
        mPreviewTextColor = colors.get(ColorType.KEY_PREVIEW_TEXT)

        mHintLabelVerticalAdjustment = ResourceUtils.getFraction(keyAttr, R.styleable.Keyboard_Key_keyHintLabelVerticalAdjustment, 0.0f)
        mLabelOffCenterRatio = ResourceUtils.getFraction(keyAttr, R.styleable.Keyboard_Key_keyLabelOffCenterRatio, 0.0f)
        mHintLabelOffCenterRatio = ResourceUtils.getFraction(keyAttr, R.styleable.Keyboard_Key_keyHintLabelOffCenterRatio, 0.0f)
    }

    companion object {
        private val VISUAL_ATTRIBUTE_IDS = intArrayOf(
            R.styleable.Keyboard_Key_keyTypeface,
            R.styleable.Keyboard_Key_keyLetterSize,
            R.styleable.Keyboard_Key_keyLabelSize,
            R.styleable.Keyboard_Key_keyLargeLetterRatio,
            R.styleable.Keyboard_Key_keyHintLetterRatio,
            R.styleable.Keyboard_Key_keyShiftedLetterHintRatio,
            R.styleable.Keyboard_Key_keyHintLabelRatio,
            R.styleable.Keyboard_Key_keyPreviewTextRatio,
            R.styleable.Keyboard_Key_keyTextInactivatedColor,
            R.styleable.Keyboard_Key_keyTextShadowColor,
            R.styleable.Keyboard_Key_keyShiftedLetterHintInactivatedColor,
            R.styleable.Keyboard_Key_keyShiftedLetterHintActivatedColor,
            R.styleable.Keyboard_Key_keyHintLabelVerticalAdjustment,
            R.styleable.Keyboard_Key_keyLabelOffCenterRatio,
            R.styleable.Keyboard_Key_keyHintLabelOffCenterRatio
        )
        private val sVisualAttributeIds = SparseIntArray()
        private const val ATTR_DEFINED = 1
        private const val ATTR_NOT_FOUND = 0

        init {
            for (attrId in VISUAL_ATTRIBUTE_IDS) {
                sVisualAttributeIds.put(attrId, ATTR_DEFINED)
            }
        }

        @JvmStatic
        fun newInstance(keyAttr: TypedArray): KeyVisualAttributes? {
            val indexCount = keyAttr.indexCount
            for (i in 0 until indexCount) {
                val attrId = keyAttr.getIndex(i)
                if (sVisualAttributeIds.get(attrId, ATTR_NOT_FOUND) == ATTR_NOT_FOUND) {
                    continue
                }
                return KeyVisualAttributes(keyAttr)
            }
            return null
        }
    }
}
