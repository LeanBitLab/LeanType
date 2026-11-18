// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.internal

import android.content.Context
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.common.Constants
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
        // hex layouts have their space keys in a middle row, so the space key does not mark the bottom row
        if (mainKb != null && mainKb.mOccupiedHeight == totalHeight && !mainKb.isHexagonal
                && (mainKb.mId.isAlphabetKeyboard || mainKb.mId.isAlphaOrSymbolKeyboard)) {
            val spaceKey = mainKb.sortedKeys.firstOrNull { it.code == Constants.CODE_SPACE }
            if (spaceKey != null) {
                return Dimensions(
                    bottomRowY = spaceKey.y,
                    bottomRowKeyHeight = spaceKey.height,
                    bottomRowAreaHeight = mainKb.mOccupiedHeight - spaceKey.y,
                    bottomPadding = mainKb.mOccupiedHeight - (spaceKey.y + spaceKey.height),
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
}
