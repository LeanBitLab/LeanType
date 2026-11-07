// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.style

/**
 * Definition and capabilities of a Unicode text style.
 */
data class StyleDefinition(
    val id: String,
    val name: String,
    val category: StyleCategory,
    val samplePreview: String,
    val supportsLiveTyping: Boolean = true,
    val isReversed: Boolean = false,
    val combiningMark: Int = 0,
    val mapCodePoint: (Int) -> Int = { it }
)
