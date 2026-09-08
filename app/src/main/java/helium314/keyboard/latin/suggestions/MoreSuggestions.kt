/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.suggestions

import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.internal.KeyboardBuilder
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.utils.TypefaceUtils

class MoreSuggestions internal constructor(
    params: MoreSuggestionsParam,
    val mSuggestedWords: SuggestedWords
) : Keyboard(params) {

    internal class MoreSuggestionsParam : KeyboardParams() {
        val mWidths = IntArray(SuggestedWords.MAX_SUGGESTIONS)
        val mRowNumbers = IntArray(SuggestedWords.MAX_SUGGESTIONS)
        val mColumnOrders = IntArray(SuggestedWords.MAX_SUGGESTIONS)
        val mNumColumnsInRow = IntArray(SuggestedWords.MAX_SUGGESTIONS)
        var mNumRows = 0
        var mDivider: Drawable? = null
        var mDividerWidth = 0

        fun layout(
            suggestedWords: SuggestedWords, fromIndex: Int,
            maxWidth: Int, minWidth: Int, maxRow: Int, paint: Paint,
            context: Context
        ): Int {
            clearKeys()
            mDivider = ContextCompat.getDrawable(context, R.drawable.more_suggestions_divider)
            mDividerWidth = mDivider?.intrinsicWidth ?: 0
            val padding = context.resources.getDimension(R.dimen.config_more_suggestions_key_horizontal_padding)

            var row = 0
            var index = fromIndex
            var rowStartIndex = fromIndex
            val size = minOf(suggestedWords.size(), SuggestedWords.MAX_SUGGESTIONS)
            while (index < size) {
                val word = if (isIndexSubjectToAutoCorrection(suggestedWords, index)) {
                    // INDEX_OF_AUTO_CORRECTION and INDEX_OF_TYPED_WORD got swapped.
                    suggestedWords.getLabel(SuggestedWords.INDEX_OF_TYPED_WORD)
                } else {
                    suggestedWords.getLabel(index)
                }
                // TODO: Should take care of text x-scaling.
                mWidths[index] = (TypefaceUtils.getStringWidth(word, paint) + padding).toInt()
                val numColumn = index - rowStartIndex + 1
                val columnWidth = (maxWidth - mDividerWidth * (numColumn - 1)) / numColumn
                if (numColumn > MAX_COLUMNS_IN_ROW || !fitInWidth(rowStartIndex, index + 1, columnWidth)) {
                    if ((row + 1) >= maxRow) {
                        break
                    }
                    mNumColumnsInRow[row] = index - rowStartIndex
                    rowStartIndex = index
                    row++
                }
                mColumnOrders[index] = index - rowStartIndex
                mRowNumbers[index] = row
                index++
            }
            mNumColumnsInRow[row] = index - rowStartIndex
            mNumRows = row + 1
            val maxW = maxOf(minWidth, calcurateMaxRowWidth(fromIndex, index))
            mBaseWidth = maxW
            mOccupiedWidth = maxW
            val maxH = mNumRows * mDefaultAbsoluteRowHeight + mVerticalGap
            mBaseHeight = maxH
            mOccupiedHeight = maxH
            return index - fromIndex
        }

        private fun fitInWidth(startIndex: Int, endIndex: Int, width: Int): Boolean {
            for (index in startIndex until endIndex) {
                if (mWidths[index] > width) return false
            }
            return true
        }

        private fun calcurateMaxRowWidth(startIndex: Int, endIndex: Int): Int {
            var maxRowWidth = 0
            var index = startIndex
            for (row in 0 until mNumRows) {
                val numColumnInRow = mNumColumnsInRow[row]
                var maxKeyWidth = 0
                while (index < endIndex && mRowNumbers[index] == row) {
                    maxKeyWidth = maxOf(maxKeyWidth, mWidths[index])
                    index++
                }
                maxRowWidth = maxOf(maxRowWidth, maxKeyWidth * numColumnInRow + mDividerWidth * (numColumnInRow - 1))
            }
            return maxRowWidth
        }

        private val COLUMN_ORDER_TO_NUMBER = arrayOf(
            intArrayOf(0), // center
            intArrayOf(1, 0), // right-left
            intArrayOf(1, 0, 2) // center-left-right
        )

        fun getNumColumnInRow(index: Int): Int {
            return mNumColumnsInRow[mRowNumbers[index]]
        }

        fun getColumnNumber(index: Int): Int {
            val columnOrder = mColumnOrders[index]
            val numColumn = getNumColumnInRow(index)
            return COLUMN_ORDER_TO_NUMBER[numColumn - 1][columnOrder]
        }

        fun getX(index: Int): Int {
            val columnNumber = getColumnNumber(index)
            return columnNumber * (getWidth(index) + mDividerWidth)
        }

        fun getY(index: Int): Int {
            val row = mRowNumbers[index]
            return (mNumRows - 1 - row) * mDefaultAbsoluteRowHeight + mTopPadding
        }

        fun getWidth(index: Int): Int {
            val numColumnInRow = getNumColumnInRow(index)
            return (mOccupiedWidth - mDividerWidth * (numColumnInRow - 1)) / numColumnInRow
        }

        fun markAsEdgeKey(key: Key, index: Int) {
            val row = mRowNumbers[index]
            if (row == 0) key.markAsBottomEdge(this)
            if (row == mNumRows - 1) key.markAsTopEdge(this)

            val numColumnInRow = mNumColumnsInRow[row]
            val column = getColumnNumber(index)
            if (column == 0) key.markAsLeftEdge(this)
            if (column == numColumnInRow - 1) key.markAsRightEdge(this)
        }

        companion object {
            private const val MAX_COLUMNS_IN_ROW = 3
        }
    }

    internal class Builder(context: Context, private val mPaneView: MoreSuggestionsView) :
        KeyboardBuilder<MoreSuggestionsParam>(context, MoreSuggestionsParam()) {

        private var mSuggestedWords: SuggestedWords? = null
        private var mFromIndex = 0
        private var mToIndex = 0

        fun layout(
            suggestedWords: SuggestedWords, fromIndex: Int,
            maxWidth: Int, minWidth: Int, maxRow: Int,
            parentKeyboard: Keyboard
        ): Builder {
            val xmlId = R.xml.kbd_suggestions_pane_template
            mParams.mId = parentKeyboard.mId
            readAttributes(xmlId)
            mParams.mVerticalGap = parentKeyboard.mVerticalGap / 2
            mParams.mTopPadding = mParams.mVerticalGap
            mPaneView.updateKeyboardGeometry(mParams.mDefaultAbsoluteRowHeight)
            val count = mParams.layout(
                suggestedWords, fromIndex, maxWidth, minWidth, maxRow,
                mPaneView.newLabelPaint(null), mContext
            )
            mFromIndex = fromIndex
            mToIndex = fromIndex + count
            mSuggestedWords = suggestedWords
            return this
        }

        override fun build(): MoreSuggestions {
            val suggestedWords = mSuggestedWords ?: return MoreSuggestions(mParams, SuggestedWords.getEmptyInstance())
            val params = mParams
            val dividerDrawable = params.mDivider
            for (index in mFromIndex until mToIndex) {
                val x = params.getX(index)
                val y = params.getY(index)
                val width = params.getWidth(index)
                val word: String
                val info: String?
                if (isIndexSubjectToAutoCorrection(suggestedWords, index)) {
                    // INDEX_OF_AUTO_CORRECTION and INDEX_OF_TYPED_WORD got swapped.
                    word = suggestedWords.getLabel(SuggestedWords.INDEX_OF_TYPED_WORD)
                    info = suggestedWords.getDebugString(SuggestedWords.INDEX_OF_TYPED_WORD)
                } else {
                    word = suggestedWords.getLabel(index)
                    info = suggestedWords.getDebugString(index)
                }
                val key = MoreSuggestionKey(word, info, index, params)
                params.markAsEdgeKey(key, index)
                params.onAddKey(key)
                val columnNumber = params.getColumnNumber(index)
                val numColumnInRow = params.getNumColumnInRow(index)
                if (columnNumber < numColumnInRow - 1 && dividerDrawable != null) {
                    val divider = Divider(params, dividerDrawable, x + width, y, params.mDividerWidth, params.mDefaultAbsoluteRowHeight)
                    params.onAddKey(divider)
                }
            }
            return MoreSuggestions(params, suggestedWords)
        }
    }

    internal class MoreSuggestionKey(
        word: String, info: String?, index: Int, params: MoreSuggestionsParam
    ) : Key(
        word, null, KeyCode.MULTIPLE_CODE_POINTS,
        word, info, 0, BACKGROUND_TYPE_NORMAL,
        params.getX(index), params.getY(index), params.getWidth(index),
        params.mDefaultAbsoluteRowHeight, params.mHorizontalGap, params.mVerticalGap
    ) {
        val mSuggestedWordIndex: Int = index
    }

    private class Divider(
        params: KeyboardParams, icon: Drawable, x: Int, y: Int, width: Int, height: Int
    ) : Key.Spacer(params, x, y, width, height) {
        private val mIcon: Drawable = icon
        override fun getIcon(iconSet: KeyboardIconsSet?, alpha: Int): Drawable {
            mIcon.alpha = 128
            return mIcon
        }
    }

    companion object {
        internal fun isIndexSubjectToAutoCorrection(suggestedWords: SuggestedWords, index: Int): Boolean {
            return suggestedWords.mWillAutoCorrect && index == SuggestedWords.INDEX_OF_AUTO_CORRECTION
        }
    }
}
