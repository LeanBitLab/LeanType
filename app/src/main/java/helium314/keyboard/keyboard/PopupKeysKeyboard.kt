/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard

import android.content.Context
import android.graphics.Paint
import helium314.keyboard.keyboard.internal.KeyboardBuilder
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.PopupKeySpec
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.utils.TypefaceUtils
import kotlin.math.max
import kotlin.math.min

class PopupKeysKeyboard(params: PopupKeysKeyboardParams) : Keyboard(params) {
    private val mDefaultKeyCoordX: Int = params.defaultKeyCoordX + params.mAbsolutePopupKeyWidth / 2

    fun getDefaultCoordX(): Int = mDefaultKeyCoordX

    class PopupKeysKeyboardParams : KeyboardParams() {
        var mIsPopupKeysFixedOrder = false
        var mTopRowAdjustment = 0
        var mNumRows = 0
        var mNumColumns = 0
        var mTopKeys = 0
        var mLeftKeys = 0
        var mRightKeys = 0 // includes default key.
        var mDividerWidth = 0
        var mColumnWidth = 0

        /**
         * Set keyboard parameters of popup keys keyboard.
         *
         * @param numKeys number of keys in this popup keys keyboard.
         * @param numColumn number of columns of this popup keys keyboard.
         * @param keyWidth popup keys keyboard key width in pixel, including horizontal gap.
         * @param rowHeight popup keys keyboard row height in pixel, including vertical gap.
         * @param coordXInParent coordinate x of the key preview in parent keyboard.
         * @param parentKeyboardWidth parent keyboard width in pixel.
         * @param isPopupKeysFixedColumn true if popup keys keyboard should have
         *   `numColumn` columns. Otherwise popup keys keyboard should have
         *   `numColumn` columns at most.
         * @param isPopupKeysFixedOrder true if the order of popup keys is determined by the order in
         *   the popup keys' specification. Otherwise the order of popup keys is automatically
         *   determined.
         * @param dividerWidth width of divider, zero for no dividers.
         */
        fun setParameters(
            numKeys: Int, numColumn: Int, keyWidth: Int, rowHeight: Int,
            coordXInParent: Int, parentKeyboardWidth: Int,
            isPopupKeysFixedColumn: Boolean, isPopupKeysFixedOrder: Boolean,
            dividerWidth: Int
        ) {
            mIsPopupKeysFixedOrder = isPopupKeysFixedOrder
            if (parentKeyboardWidth / keyWidth < min(numKeys, numColumn)) {
                throw IllegalArgumentException(
                    "Keyboard is too small to hold popup keys: $parentKeyboardWidth $keyWidth $numKeys $numColumn"
                )
            }
            mDefaultAbsoluteKeyWidth = keyWidth
            mDefaultAbsoluteRowHeight = rowHeight

            mNumRows = (numKeys + numColumn - 1) / numColumn
            val numColumns = if (isPopupKeysFixedColumn) min(numKeys, numColumn)
            else getOptimizedColumns(numKeys, numColumn)
            mNumColumns = numColumns
            val topKeys = numKeys % numColumns
            mTopKeys = if (topKeys == 0) numColumns else topKeys

            val numLeftKeys = (numColumns - 1) / 2
            val numRightKeys = numColumns - numLeftKeys // including default key.
            // Maximum number of keys we can layout both side of the parent key
            val maxLeftKeys = coordXInParent / keyWidth
            val maxRightKeys = (parentKeyboardWidth - coordXInParent) / keyWidth
            var leftKeys: Int
            var rightKeys: Int
            if (numLeftKeys > maxLeftKeys) {
                leftKeys = maxLeftKeys
                rightKeys = numColumns - leftKeys
            } else if (numRightKeys > maxRightKeys + 1) {
                rightKeys = maxRightKeys + 1 // include default key
                leftKeys = numColumns - rightKeys
            } else {
                leftKeys = numLeftKeys
                rightKeys = numRightKeys
            }
            // If the left keys fill the left side of the parent key, entire popup keys keyboard
            // should be shifted to the right unless the parent key is on the left edge.
            if (maxLeftKeys == leftKeys && leftKeys > 0) {
                leftKeys--
                rightKeys++
            }
            // If the right keys fill the right side of the parent key, entire popup keys
            // should be shifted to the left unless the parent key is on the right edge.
            if (maxRightKeys == rightKeys - 1 && rightKeys > 1) {
                leftKeys++
                rightKeys--
            }
            mLeftKeys = leftKeys
            mRightKeys = rightKeys

            // Adjustment of the top row.
            mTopRowAdjustment = if (isPopupKeysFixedOrder) getFixedOrderTopRowAdjustment()
            else getAutoOrderTopRowAdjustment()
            mDividerWidth = dividerWidth
            mColumnWidth = mDefaultAbsoluteKeyWidth + mDividerWidth
            mBaseWidth = mNumColumns * mColumnWidth - mDividerWidth
            mOccupiedWidth = mBaseWidth
            // Need to subtract the bottom row's gutter only.
            mBaseHeight = mNumRows * mDefaultAbsoluteRowHeight - mVerticalGap + mTopPadding + mBottomPadding
            mOccupiedHeight = mBaseHeight
        }

        private fun getFixedOrderTopRowAdjustment(): Int {
            if (mNumRows == 1 || mTopKeys % 2 == 1 || mTopKeys == mNumColumns || mLeftKeys == 0 || mRightKeys == 1) {
                return 0
            }
            return -1
        }

        private fun getAutoOrderTopRowAdjustment(): Int {
            if (mNumRows == 1 || mTopKeys == 1 || mNumColumns % 2 == mTopKeys % 2 || mLeftKeys == 0 || mRightKeys == 1) {
                return 0
            }
            return -1
        }

        // Return key position according to column count (0 is default).
        fun getColumnPos(n: Int): Int {
            return if (mIsPopupKeysFixedOrder) getFixedOrderColumnPos(n) else getAutomaticColumnPos(n)
        }

        private fun getFixedOrderColumnPos(n: Int): Int {
            val col = n % mNumColumns
            val row = n / mNumColumns
            if (!isTopRow(row)) {
                return col - mLeftKeys
            }
            val rightSideKeys = mTopKeys / 2
            val leftSideKeys = mTopKeys - (rightSideKeys + 1)
            val pos = col - leftSideKeys
            val numLeftKeys = mLeftKeys + mTopRowAdjustment
            val numRightKeys = mRightKeys - 1
            return if (numRightKeys >= rightSideKeys && numLeftKeys >= leftSideKeys) {
                pos
            } else if (numRightKeys < rightSideKeys) {
                pos - (rightSideKeys - numRightKeys)
            } else { // numLeftKeys < leftSideKeys
                pos + (leftSideKeys - numLeftKeys)
            }
        }

        private fun getAutomaticColumnPos(n: Int): Int {
            val col = n % mNumColumns
            val row = n / mNumColumns
            var leftKeys = mLeftKeys
            if (isTopRow(row)) {
                leftKeys += mTopRowAdjustment
            }
            if (col == 0) {
                // default position.
                return 0
            }

            var pos = 0
            var right = 1 // include default position key.
            var left = 0
            var i = 0
            while (true) {
                // Assign right key if available.
                if (right < mRightKeys) {
                    pos = right
                    right++
                    i++
                }
                if (i >= col) break
                // Assign left key if available.
                if (left < leftKeys) {
                    left++
                    pos = -left
                    i++
                }
                if (i >= col) break
            }
            return pos
        }

        private fun getTopRowEmptySlots(numKeys: Int, numColumns: Int): Int {
            val remainings = numKeys % numColumns
            return if (remainings == 0) 0 else numColumns - remainings
        }

        private fun getOptimizedColumns(numKeys: Int, maxColumns: Int): Int {
            var numColumns = min(numKeys, maxColumns)
            while (getTopRowEmptySlots(numKeys, numColumns) >= mNumRows) {
                numColumns--
            }
            return numColumns
        }

        val defaultKeyCoordX: Int
            get() = mLeftKeys * mColumnWidth + mLeftPadding


        fun getX(n: Int, row: Int): Int {
            val x = getColumnPos(n) * mColumnWidth + defaultKeyCoordX
            if (isTopRow(row)) {
                return x + mTopRowAdjustment * (mColumnWidth / 2)
            }
            return x
        }

        fun getY(row: Int): Int {
            return (mNumRows - 1 - row) * mDefaultAbsoluteRowHeight + mTopPadding
        }

        fun markAsEdgeKey(key: Key, row: Int) {
            if (row == 0) key.markAsTopEdge(this)
            if (isTopRow(row)) key.markAsBottomEdge(this)
        }

        private fun isTopRow(rowCount: Int): Boolean {
            return mNumRows > 1 && rowCount == mNumRows - 1
        }
    }

    class Builder(
        context: Context,
        private val mParentKey: Key,
        keyboard: Keyboard?,
        isSinglePopupKeyWithPreview: Boolean,
        keyPreviewVisibleWidth: Int,
        keyPreviewVisibleHeight: Int,
        paintToMeasure: Paint
    ) : KeyboardBuilder<PopupKeysKeyboardParams>(context, PopupKeysKeyboardParams()) {

        init {
            keyboard?.mId?.let { mParams.mId = it }
            if (keyboard?.mPopupKeysTemplate != null) {
                readAttributes(keyboard.mPopupKeysTemplate)
            }

            // TODO: Popup keys keyboard's vertical gap is currently calculated heuristically.
            // Should revise the algorithm.
            mParams.mVerticalGap = (keyboard?.mVerticalGap ?: 0) / 2
            // This PopupKeysKeyboard is invoked from the parent key.

            val keyWidth: Int
            val rowHeight: Int
            if (isSinglePopupKeyWithPreview) {
                // Use pre-computed width and height if this popup keys keyboard has only one key to
                // mitigate visual flicker between key preview and popup keys keyboard.
                // Caveats for the visual assets: To achieve this effect, both the key preview
                // backgrounds and the popup keys keyboard panel background have the exact same
                // left/right/top paddings. The bottom paddings of both backgrounds don't need to
                // be considered because the vertical positions of both backgrounds were already
                // adjusted with their bottom paddings deducted.
                keyWidth = keyPreviewVisibleWidth
                rowHeight = keyPreviewVisibleHeight + mParams.mVerticalGap
            } else {
                val padding = context.resources.getDimension(
                    R.dimen.config_popup_keys_keyboard_key_horizontal_padding
                ) + (if (mParentKey.hasLabelsInPopupKeys()) mParams.mAbsolutePopupKeyWidth * LABEL_PADDING_RATIO else 0.0f)
                keyWidth = getMaxKeyWidth(mParentKey, mParams.mAbsolutePopupKeyWidth, padding, paintToMeasure)
                rowHeight = keyboard?.mMostCommonKeyHeight ?: 0
            }
            val dividerWidth = if (mParentKey.needsDividersInPopupKeys()) {
                (keyWidth * DIVIDER_RATIO).toInt()
            } else {
                0
            }
            val popupKeys = mParentKey.popupKeys ?: emptyArray()
            val defaultColumns = mParentKey.popupKeysColumnNumber
            val keyboardWidth = keyboard?.mId?.mWidth ?: 0
            val spaceForKeys = if (keyWidth > 0) keyboardWidth / keyWidth else 0
            val finalNumColumns = if (spaceForKeys >= min(popupKeys.size, defaultColumns)) {
                defaultColumns
            } else {
                if (spaceForKeys > 0) spaceForKeys else defaultColumns // in last case setParameters will throw an exception
            }
            mParams.setParameters(
                popupKeys.size, finalNumColumns, keyWidth,
                rowHeight, mParentKey.x + mParentKey.width / 2, keyboardWidth,
                mParentKey.isPopupKeysFixedColumn, mParentKey.isPopupKeysFixedOrder, dividerWidth
            )
        }

        override fun build(): PopupKeysKeyboard {
            val params = mParams
            val popupKeyFlags = mParentKey.popupKeyLabelFlags
            val popupKeys = mParentKey.popupKeys ?: emptyArray()
            val background = if (mParentKey.hasActionKeyPopups()) Key.BACKGROUND_TYPE_ACTION else Key.BACKGROUND_TYPE_NORMAL
            for (n in popupKeys.indices) {
                val popupKeySpec = popupKeys[n]
                val row = n / params.mNumColumns
                val x = params.getX(n, row)
                val y = params.getY(row)
                val key = popupKeySpec.buildKey(x, y, popupKeyFlags, background, params)
                params.markAsEdgeKey(key, row)
                params.onAddKey(key)

                val pos = params.getColumnPos(n)
                // The "pos" value represents the offset from the default position. Negative means
                // left of the default position.
                if (params.mDividerWidth > 0 && pos != 0) {
                    val dividerX = if (pos > 0) x - params.mDividerWidth else x + params.mAbsolutePopupKeyWidth
                    val divider = PopupKeyDivider(
                        params, dividerX, y, params.mDividerWidth, params.mDefaultAbsoluteRowHeight
                    )
                    params.onAddKey(divider)
                }
            }
            return PopupKeysKeyboard(params)
        }

        companion object {
            private const val LABEL_PADDING_RATIO = 0.2f
            private const val DIVIDER_RATIO = 0.2f

            private fun getMaxKeyWidth(
                parentKey: Key, minKeyWidth: Int,
                padding: Float, paint: Paint
            ): Int {
                var maxWidth = minKeyWidth
                val popupKeys = parentKey.popupKeys ?: return maxWidth
                for (spec in popupKeys) {
                    val label = spec.mLabel
                    // If the label is single letter, minKeyWidth is enough to hold the label.
                    if (label != null && StringUtils.codePointCount(label) > 1) {
                        maxWidth = max(
                            maxWidth,
                            (TypefaceUtils.getStringWidth(label, paint) + padding).toInt()
                        )
                    }
                }
                return maxWidth
            }
        }
    }

    // Used as a divider maker. A divider is drawn by PopupKeysKeyboardView.
    class PopupKeyDivider(params: KeyboardParams, x: Int, y: Int, width: Int, height: Int) :
        Key.Spacer(params, x, y, width, height)
}
