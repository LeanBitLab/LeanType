/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.content.Context
import android.util.AttributeSet
import android.util.SparseIntArray
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.internal.keyboard_parser.LocaleKeyboardInfos
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import java.util.ArrayList
import java.util.Comparator
import java.util.Locale
import java.util.TreeSet

open class KeyboardParams {
    lateinit var mId: KeyboardId
    @JvmField var mThemeId: Int = 0

    @JvmField var mOccupiedHeight: Int = 0
    @JvmField var mOccupiedWidth: Int = 0

    @JvmField var mBaseHeight: Int = 0
    @JvmField var mBaseWidth: Int = 0

    @JvmField var mTopPadding: Int = 0
    @JvmField var mBottomPadding: Int = 0
    @JvmField var mLeftPadding: Int = 0
    @JvmField var mRightPadding: Int = 0

    @JvmField var mKeyVisualAttributes: KeyVisualAttributes? = null

    @JvmField var mDefaultRowHeight: Float = 0f
    @JvmField var mDefaultKeyWidth: Float = 0f
    @JvmField var mRelativeHorizontalGap: Float = 0f
    @JvmField var mRelativeVerticalGap: Float = 0f
    @JvmField var mDefaultAbsoluteRowHeight: Int = 0
    @JvmField var mDefaultAbsoluteKeyWidth: Int = 0
    @JvmField var mHorizontalGap: Int = 0
    @JvmField var mVerticalGap: Int = 0

    @JvmField var mPopupKeysTemplate: Int = 0
    @JvmField var mMaxPopupKeysKeyboardColumn: Int = 0
    @JvmField var mAbsolutePopupKeyWidth: Int = 0

    @JvmField var GRID_WIDTH: Int = 0
    @JvmField var GRID_HEIGHT: Int = 0

    @JvmField val mSortedKeys: TreeSet<Key> = TreeSet(ROW_COLUMN_COMPARATOR)
    @JvmField val mShiftKeys: ArrayList<Key> = ArrayList()
    @JvmField val mAltCodeKeysWhileTyping: ArrayList<Key> = ArrayList()
    @JvmField val mIconsSet: KeyboardIconsSet = KeyboardIconsSet.instance
    @JvmField val mSecondaryLocales: List<Locale> = Settings.getValues().mSecondaryLocales
    @JvmField val mPopupKeyTypes: ArrayList<String> = ArrayList()
    @JvmField val mPopupKeyLabelSources: ArrayList<String> = ArrayList()

    private val mUniqueKeysCache: UniqueKeysCache
    @JvmField var mAllowRedundantPopupKeys: Boolean = false
    lateinit var mLocaleKeyboardInfos: LocaleKeyboardInfos
    @JvmField var setTabletExtraKeys: Boolean = false

    @JvmField var mMostCommonKeyHeight: Int = 0
    @JvmField var mMostCommonKeyWidth: Int = 0

    @JvmField var mProximityCharsCorrectionEnabled: Boolean = false

    @JvmField var baseKeys: List<Key.KeyParams>? = null

    @JvmField val mTouchPositionCorrection: TouchPositionCorrection = TouchPositionCorrection()

    private var mMaxHeightCount = 0
    private var mMaxWidthCount = 0
    private val mHeightHistogram = SparseIntArray()
    private val mWidthHistogram = SparseIntArray()

    constructor() : this(UniqueKeysCache.NO_CACHE)

    constructor(keysCache: UniqueKeysCache) {
        mUniqueKeysCache = keysCache
    }

    protected fun clearKeys() {
        mSortedKeys.clear()
        mShiftKeys.clear()
        clearHistogram()
    }

    fun onAddKey(newKey: Key) {
        val key = mUniqueKeysCache.getUniqueKey(newKey)
        val isSpacer = key.isSpacer
        if (isSpacer && key.width == 0) return
        mSortedKeys.add(key)
        if (isSpacer) return
        updateHistogram(key)
        if (key.code == KeyCode.SHIFT) {
            mShiftKeys.add(key)
        }
        if (key.altCodeWhileTyping()) {
            mAltCodeKeysWhileTyping.add(key)
        }
    }

    fun removeRedundantPopupKeys() {
        if (mAllowRedundantPopupKeys || baseKeys == null) return
        val lettersOnBaseLayout = PopupKeySpec.LettersOnBaseLayout()
        for (key in baseKeys!!) {
            lettersOnBaseLayout.addLetter(key)
        }
        val allKeys = ArrayList(mSortedKeys)
        mSortedKeys.clear()
        for (key in allKeys) {
            val filteredKey = Key.removeRedundantPopupKeys(key, lettersOnBaseLayout)
            mSortedKeys.add(mUniqueKeysCache.getUniqueKey(filteredKey))
        }
        baseKeys = null
    }

    private fun clearHistogram() {
        mMostCommonKeyHeight = 0
        mMaxHeightCount = 0
        mHeightHistogram.clear()

        mMaxWidthCount = 0
        mMostCommonKeyWidth = 0
        mWidthHistogram.clear()
    }

    private fun updateHistogram(histogram: SparseIntArray, key: Int): Int {
        val index = histogram.indexOfKey(key)
        val count = (if (index >= 0) histogram.get(key) else 0) + 1
        histogram.put(key, count)
        return count
    }

    private fun updateHistogram(key: Key) {
        val height = key.height + mVerticalGap
        val heightCount = updateHistogram(mHeightHistogram, height)
        if (heightCount > mMaxHeightCount) {
            mMaxHeightCount = heightCount
            mMostCommonKeyHeight = height
        }

        val width = key.width + mHorizontalGap
        val widthCount = updateHistogram(mWidthHistogram, width)
        if (widthCount > mMaxWidthCount) {
            mMaxWidthCount = widthCount
            mMostCommonKeyWidth = width
        }
    }

    fun readAttributes(context: Context, attr: AttributeSet?) {
        val keyboardAttr = context.obtainStyledAttributes(attr, R.styleable.Keyboard, R.attr.keyboardStyle, R.style.Keyboard)
        val keyAttr = if (attr == null) {
            context.obtainStyledAttributes(attr, R.styleable.Keyboard_Key)
        } else {
            context.resources.obtainAttributes(attr, R.styleable.Keyboard_Key)
        }
        try {
            val height = mId.mHeight
            val width = mId.mWidth
            mOccupiedHeight = height
            mOccupiedWidth = width
            mTopPadding = keyboardAttr.getFraction(R.styleable.Keyboard_keyboardTopPadding, height, height, 0f).toInt()
            mBottomPadding = (keyboardAttr.getFraction(R.styleable.Keyboard_keyboardBottomPadding, height, height, 0f) * Settings.getValues().mBottomPaddingScale).toInt()
            mLeftPadding = (keyboardAttr.getFraction(R.styleable.Keyboard_keyboardLeftPadding, width, width, 0f) * Settings.getValues().mSidePaddingScale).toInt()
            mRightPadding = (keyboardAttr.getFraction(R.styleable.Keyboard_keyboardRightPadding, width, width, 0f) * Settings.getValues().mSidePaddingScale).toInt()

            mBaseWidth = mOccupiedWidth - mLeftPadding - mRightPadding
            val defaultKeyWidthFactor = if (context.resources.getInteger(R.integer.config_screen_metrics) > 2) 0.9f else 1f
            val alphaSymbolKeyWidth = keyAttr.getFraction(R.styleable.Keyboard_Key_keyWidth, 1, 1, defaultKeyWidthFactor / DEFAULT_KEYBOARD_COLUMNS)
            mDefaultKeyWidth = if (mId.isNumberLayout) 0.17f else alphaSymbolKeyWidth
            mDefaultAbsoluteKeyWidth = (mDefaultKeyWidth * mBaseWidth).toInt()
            mAbsolutePopupKeyWidth = (alphaSymbolKeyWidth * mBaseWidth).toInt()

            var horizontalGapStandard = keyboardAttr.getFraction(R.styleable.Keyboard_horizontalGap, 1, 1, 0.03f)
            var verticalGapStandard = keyboardAttr.getFraction(R.styleable.Keyboard_verticalGap, 1, 1, 0.03f)

            if (Settings.getValues().mNarrowKeyGaps) {
                var horizontalGapNarrow = keyboardAttr.getFraction(R.styleable.Keyboard_horizontalGapNarrow, 1, 1, 0.015f)
                var verticalGapNarrow = keyboardAttr.getFraction(R.styleable.Keyboard_verticalGapNarrow, 1, 1, 0.015f)
                if (horizontalGapNarrow <= 0) horizontalGapNarrow = 0.015f
                if (verticalGapNarrow <= 0) verticalGapNarrow = 0.015f

                val level = Settings.getValues().mNarrowKeyGapsLevel
                val hasBorders = Settings.getValues().mThemeKeyBorders

                if (level <= 0) {
                    mRelativeHorizontalGap = horizontalGapStandard
                    mRelativeVerticalGap = verticalGapStandard
                } else if (level == 1) {
                    mRelativeHorizontalGap = horizontalGapNarrow
                    mRelativeVerticalGap = verticalGapNarrow
                } else {
                    var factor = maxOf(0f, minOf(1f, (10 - level) / 9.0f))
                    if (!hasBorders && factor < 0.1f) {
                        factor = 0.1f
                    }
                    mRelativeHorizontalGap = horizontalGapNarrow * factor
                    mRelativeVerticalGap = verticalGapNarrow * factor
                }
            } else {
                mRelativeHorizontalGap = horizontalGapStandard
                mRelativeVerticalGap = verticalGapStandard
            }
            mHorizontalGap = (mRelativeHorizontalGap * width).toInt()
            mVerticalGap = (mRelativeVerticalGap * height).toInt()

            mBaseHeight = mOccupiedHeight - mTopPadding - mBottomPadding + mVerticalGap
            mDefaultRowHeight = ResourceUtils.getDimensionOrFraction(keyboardAttr, R.styleable.Keyboard_rowHeight, 1, 1f / DEFAULT_KEYBOARD_ROWS)
            if (mDefaultRowHeight > 1) {
                mDefaultAbsoluteRowHeight = mDefaultRowHeight.toInt()
                mDefaultRowHeight *= -1f
            } else {
                mDefaultAbsoluteRowHeight = (mDefaultRowHeight * mBaseHeight).toInt()
            }

            mKeyVisualAttributes = KeyVisualAttributes.newInstance(keyAttr)

            mPopupKeysTemplate = keyboardAttr.getResourceId(R.styleable.Keyboard_popupKeysTemplate, 0)
            mMaxPopupKeysKeyboardColumn = keyAttr.getInt(R.styleable.Keyboard_Key_maxPopupKeysColumn, 5)

            mThemeId = keyboardAttr.getInt(R.styleable.Keyboard_themeId, 0)
            mIconsSet.loadIcons(context)

            val touchPositionResId = keyboardAttr.getResourceId(R.styleable.Keyboard_touchPositionCorrectionData, 0)
            if (touchPositionResId != 0) {
                val actualId = if (mId.isAlphabetKeyboard) touchPositionResId else R.array.touch_position_correction_data_default
                val data = context.resources.getStringArray(actualId)
                mTouchPositionCorrection.load(data)
            }
        } finally {
            keyAttr.recycle()
            keyboardAttr.recycle()
        }
        setTabletExtraKeys = Settings.getInstance().isTablet && !mId.mSubtype.isCustom
    }

    companion object {
        private const val DEFAULT_KEYBOARD_COLUMNS = 10
        const val DEFAULT_KEYBOARD_ROWS = 4

        private val ROW_COLUMN_COMPARATOR = Comparator<Key> { lhs, rhs ->
            if (lhs.y < rhs.y) return@Comparator -1
            if (lhs.y > rhs.y) return@Comparator 1
            if (lhs.x < rhs.x) return@Comparator -1
            if (lhs.x > rhs.x) return@Comparator 1
            0
        }
    }
}
