// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.internal

import android.content.Context
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.ResourceUtils

/**
 * Unified calculator for bottom row dimensions and positioning.
 * Ensures pixel-exact parity of the bottom row (spacebar row) across all views:
 * main keyboard, emoji palettes, clipboard history, handwriting, and secondary modes.
 */
object KeyboardBottomRowGeometry {

    data class Dimensions(
        val bottomRowY: Int,
        val bottomRowKeyHeight: Int,
        val bottomRowAreaHeight: Int,
        val bottomPadding: Int,
        val totalKeyboardHeight: Int
    )

    fun getDimensions(context: Context, sv: SettingsValues = Settings.getValues()): Dimensions {
        val res = context.resources
        val totalHeight = ResourceUtils.getSecondaryKeyboardHeight(res, sv)
        val isFloating = helium314.keyboard.latin.FloatingKeyboardManager.isFloatingMode(context)
        val topPadding = if (isFloating) 0 else res.getFraction(R.fraction.config_keyboard_top_padding_holo, totalHeight, totalHeight).toInt()
        val bottomPadding = if (isFloating) 0 else (res.getFraction(R.fraction.config_keyboard_bottom_padding_holo, totalHeight, totalHeight) * sv.mBottomPaddingScale).toInt()

        val verticalGap = if (sv.mNarrowKeyGaps) {
            val verticalGapStandard = res.getFraction(R.fraction.config_key_vertical_gap_holo, totalHeight, totalHeight)
            val verticalGapNarrow = res.getFraction(R.fraction.config_key_vertical_gap_holo_narrow, totalHeight, totalHeight)
            val level = sv.mNarrowKeyGapsLevel
            val hasBorders = sv.mThemeKeyBorders
            if (level <= 0) {
                verticalGapStandard.toInt()
            } else if (level == 1) {
                verticalGapNarrow.toInt()
            } else {
                var factor = ((10 - level).coerceIn(0, 9)) / 9f
                if (!hasBorders && factor < 0.1f) factor = 0.1f
                (verticalGapNarrow * factor).toInt()
            }
        } else {
            res.getFraction(R.fraction.config_key_vertical_gap_holo, totalHeight, totalHeight).toInt()
        }

        val baseHeight = totalHeight - topPadding - bottomPadding + verticalGap
        val rowCount = KeyboardParams.DEFAULT_KEYBOARD_ROWS + if (sv.mShowsNumberRow) 1 else 0
        val rowHeight = baseHeight.toFloat() / rowCount
        val bottomRowY = Math.round(topPadding.toFloat() + (rowCount - 1) * rowHeight)
        val bottomRowKeyHeight = (rowHeight - verticalGap).toInt()
        val bottomRowAreaHeight = totalHeight - bottomRowY

        val mainKb = KeyboardSwitcher.getInstance().keyboard
        // hex layouts have interlocking rows of their own, so they keep the calculated bottom row
        if (mainKb != null && mainKb.mOccupiedHeight == totalHeight && !mainKb.isHexagonal
                && (mainKb.mId.isAlphabetKeyboard || mainKb.mId.isAlphaOrSymbolKeyboard)) {
            val bottomKey = findBottomRowKey(mainKb.sortedKeys)
            if (bottomKey != null) {
                return Dimensions(
                    bottomRowY = bottomKey.y,
                    bottomRowKeyHeight = bottomKey.height,
                    bottomRowAreaHeight = mainKb.mOccupiedHeight - bottomKey.y,
                    bottomPadding = mainKb.mOccupiedHeight - (bottomKey.y + bottomKey.height),
                    totalKeyboardHeight = mainKb.mOccupiedHeight
                )
            }
        }

        return Dimensions(
            bottomRowY = bottomRowY,
            bottomRowKeyHeight = bottomRowKeyHeight,
            bottomRowAreaHeight = bottomRowAreaHeight,
            bottomPadding = bottomPadding,
            totalKeyboardHeight = totalHeight
        )
    }

    /**
     * A key of the lowest row. This is usually the space bar's row, but not every layout has the space bar there
     * (Japanese 12-key grid, custom layouts).
     */
    internal fun findBottomRowKey(keys: List<Key>): Key? = keys.filterNot { it.isSpacer }.maxByOrNull { it.y }
}
