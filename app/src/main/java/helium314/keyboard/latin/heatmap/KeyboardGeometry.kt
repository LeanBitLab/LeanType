// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.heatmap

import android.content.res.Configuration
import android.content.res.Resources
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings

/** [TypingHeatmap.KeyGeometry] for a [Keyboard] */
class KeyboardGeometry(private val keyboard: Keyboard, landscape: Boolean = isLandscape()) : TypingHeatmap.KeyGeometry {
    override val layoutId = layoutId(keyboard, landscape)

    companion object {
        fun isLandscape() = Resources.getSystem().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        /** floating and one-handed keyboards are held differently, so they get their own data */
        private fun modeSuffix(): String = when {
            LatinIME.getInstance()?.floatingKeyboardManager?.isFloating == true -> " (floating)"
            Settings.getValues().mOneHandedModeEnabled -> " (one-handed)"
            else -> ""
        }

        private class IdCache(val keyboard: Keyboard, val landscape: Boolean, val mode: String, val id: String)
        @Volatile private var idCache: IdCache? = null

        /** Id for the data of this keyboard; cached, as it is used for every touch event */
        fun layoutId(keyboard: Keyboard, landscape: Boolean = isLandscape()): String {
            val mode = modeSuffix()
            val cache = idCache
            if (cache != null && cache.keyboard === keyboard && cache.landscape == landscape && cache.mode == mode)
                return cache.id
            val id = keyboard.mId.mSubtype.mainLayoutName + (if (landscape) " (landscape)" else "") + mode
            idCache = IdCache(keyboard, landscape, mode, id)
            return id
        }
    }

    override val source: Any get() = keyboard

    override val aspectRatio: Float
        get() = if (keyboard.mOccupiedWidth > 0) keyboard.mOccupiedHeight.toFloat() / keyboard.mOccupiedWidth else 0.35f

    override fun keyRect(codePoint: Int): IntArray? {
        val key = keyboard.getKey(codePoint)
            ?: keyboard.getKey(Character.toUpperCase(codePoint))
            ?: keyboard.getKey(Character.toTitleCase(codePoint))
            ?: return null
        return intArrayOf(key.x, key.y, key.width, key.height)
    }

    override fun snapshot(): List<TypingHeatmap.KeyShape> {
        val w = keyboard.mOccupiedWidth.toFloat()
        val h = keyboard.mOccupiedHeight.toFloat()
        if (w <= 0 || h <= 0) return emptyList()
        return keyboard.sortedKeys.filter { it.width > 0 && it.height > 0 && !it.isSpacer }.map { key ->
            val label = when (key.code) {
                Constants.CODE_SPACE -> "␣"
                Constants.CODE_ENTER -> "⏎"
                KeyCode.DELETE -> "⌫"
                KeyCode.SHIFT -> "⇧"
                else -> key.label ?: ""
            }
            TypingHeatmap.KeyShape(
                Character.toLowerCase(key.code), label.lowercase(),
                key.x / w, key.y / h, key.width / w, key.height / h
            )
        }
    }
}
