// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.event

import android.view.KeyCharacterMap
import android.view.KeyEvent

/**
 * High-performance hardware keycode to character translator for external physical keyboards.
 * Supports standard layout definitions (AZERTY, QWERTZ, Dvorak, Colemak, Colemak Mod-DH, Workman, Bépo)
 * and resolves dead-key accents and shortcut modifier translations.
 */
object PhysicalKeyboardLayouts {

    /**
     * Maps an incoming hardware keycode and metastate to a Unicode code point or dead-key accent
     * based on [layoutName]. Returns null if the key is not remapped by this layout.
     */
    fun mapHardwareKey(keyCode: Int, metaState: Int, layoutName: String): Int? {
        val cleanLayout = layoutName.lowercase().substringBefore("+").substringBefore("_compact").substringBefore("_extended")
        if (cleanLayout == "qwerty" || cleanLayout == "pcqwerty" || cleanLayout == "system_default") {
            return null
        }

        val isShiftPressed = (metaState and (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_RIGHT_ON)) != 0
        val isCapsLockOn = (metaState and KeyEvent.META_CAPS_LOCK_ON) != 0
        val isAltGrPressed = (metaState and KeyEvent.META_ALT_RIGHT_ON) != 0 ||
            ((metaState and KeyEvent.META_ALT_ON) != 0 && (metaState and KeyEvent.META_CTRL_ON) != 0)

        return when (cleanLayout) {
            "azerty" -> mapAzerty(keyCode, isShiftPressed, isCapsLockOn, isAltGrPressed)
            "qwertz", "hungarian_extended_qwertz" -> mapQwertz(keyCode, isShiftPressed, isCapsLockOn, isAltGrPressed)
            "dvorak" -> mapDvorak(keyCode, isShiftPressed, isCapsLockOn)
            "colemak" -> mapColemak(keyCode, isShiftPressed, isCapsLockOn)
            "colemak_dh" -> mapColemakDh(keyCode, isShiftPressed, isCapsLockOn)
            "workman" -> mapWorkman(keyCode, isShiftPressed, isCapsLockOn)
            "bepo" -> mapBepo(keyCode, isShiftPressed, isCapsLockOn)
            else -> null
        }
    }

    /**
     * Resolves the semantic keycode when Ctrl shortcuts are triggered (e.g. Ctrl+A, Ctrl+Z on AZERTY).
     * Returns the target keycode matching the printed keycap on the physical keyboard.
     */
    fun remapKeyCodeForShortcuts(keyCode: Int, layoutName: String): Int {
        val cleanLayout = layoutName.lowercase().substringBefore("+").substringBefore("_compact").substringBefore("_extended")
        return when (cleanLayout) {
            "azerty" -> when (keyCode) {
                KeyEvent.KEYCODE_Q -> KeyEvent.KEYCODE_A
                KeyEvent.KEYCODE_A -> KeyEvent.KEYCODE_Q
                KeyEvent.KEYCODE_W -> KeyEvent.KEYCODE_Z
                KeyEvent.KEYCODE_Z -> KeyEvent.KEYCODE_W
                KeyEvent.KEYCODE_SEMICOLON -> KeyEvent.KEYCODE_M
                KeyEvent.KEYCODE_M -> KeyEvent.KEYCODE_COMMA
                KeyEvent.KEYCODE_COMMA -> KeyEvent.KEYCODE_SEMICOLON
                else -> keyCode
            }
            "qwertz", "hungarian_extended_qwertz" -> when (keyCode) {
                KeyEvent.KEYCODE_Y -> KeyEvent.KEYCODE_Z
                KeyEvent.KEYCODE_Z -> KeyEvent.KEYCODE_Y
                else -> keyCode
            }
            "dvorak" -> when (keyCode) {
                KeyEvent.KEYCODE_COMMA -> KeyEvent.KEYCODE_W
                KeyEvent.KEYCODE_PERIOD -> KeyEvent.KEYCODE_V
                KeyEvent.KEYCODE_SLASH -> KeyEvent.KEYCODE_Z
                KeyEvent.KEYCODE_R -> KeyEvent.KEYCODE_P
                KeyEvent.KEYCODE_T -> KeyEvent.KEYCODE_Y
                KeyEvent.KEYCODE_Y -> KeyEvent.KEYCODE_F
                KeyEvent.KEYCODE_U -> KeyEvent.KEYCODE_G
                KeyEvent.KEYCODE_I -> KeyEvent.KEYCODE_C
                KeyEvent.KEYCODE_O -> KeyEvent.KEYCODE_R
                KeyEvent.KEYCODE_P -> KeyEvent.KEYCODE_L
                KeyEvent.KEYCODE_S -> KeyEvent.KEYCODE_O
                KeyEvent.KEYCODE_D -> KeyEvent.KEYCODE_E
                KeyEvent.KEYCODE_F -> KeyEvent.KEYCODE_U
                KeyEvent.KEYCODE_G -> KeyEvent.KEYCODE_I
                KeyEvent.KEYCODE_H -> KeyEvent.KEYCODE_D
                KeyEvent.KEYCODE_J -> KeyEvent.KEYCODE_H
                KeyEvent.KEYCODE_K -> KeyEvent.KEYCODE_T
                KeyEvent.KEYCODE_L -> KeyEvent.KEYCODE_N
                KeyEvent.KEYCODE_SEMICOLON -> KeyEvent.KEYCODE_S
                KeyEvent.KEYCODE_X -> KeyEvent.KEYCODE_Q
                KeyEvent.KEYCODE_C -> KeyEvent.KEYCODE_J
                KeyEvent.KEYCODE_V -> KeyEvent.KEYCODE_K
                KeyEvent.KEYCODE_B -> KeyEvent.KEYCODE_X
                KeyEvent.KEYCODE_N -> KeyEvent.KEYCODE_B
                else -> keyCode
            }
            else -> keyCode
        }
    }

    private fun mapAzerty(keyCode: Int, isShift: Boolean, isCaps: Boolean, isAltGr: Boolean): Int? {
        if (isAltGr) {
            return when (keyCode) {
                KeyEvent.KEYCODE_2 -> KeyCharacterMap.COMBINING_ACCENT or 0x02DC // ~ (tilde dead key)
                KeyEvent.KEYCODE_3 -> '#'.code
                KeyEvent.KEYCODE_4 -> '{'.code
                KeyEvent.KEYCODE_5 -> '['.code
                KeyEvent.KEYCODE_6 -> '|'.code
                KeyEvent.KEYCODE_7 -> KeyCharacterMap.COMBINING_ACCENT or 0x02CB // ` (grave dead key)
                KeyEvent.KEYCODE_8 -> '\\'.code
                KeyEvent.KEYCODE_9 -> '^'.code
                KeyEvent.KEYCODE_0 -> '@'.code
                KeyEvent.KEYCODE_MINUS -> ']'.code
                KeyEvent.KEYCODE_EQUALS -> '}'.code
                KeyEvent.KEYCODE_E -> '€'.code
                KeyEvent.KEYCODE_RIGHT_BRACKET -> '¤'.code
                else -> null
            }
        }
        val isUpper = isShift xor isCaps
        val isNumRowShifted = isShift or isCaps
        return when (keyCode) {
            // Number row
            KeyEvent.KEYCODE_GRAVE -> '²'.code
            KeyEvent.KEYCODE_1 -> if (isNumRowShifted) '1'.code else '&'.code
            KeyEvent.KEYCODE_2 -> if (isNumRowShifted) '2'.code else 'é'.code
            KeyEvent.KEYCODE_3 -> if (isNumRowShifted) '3'.code else '"'.code
            KeyEvent.KEYCODE_4 -> if (isNumRowShifted) '4'.code else '\''.code
            KeyEvent.KEYCODE_5 -> if (isNumRowShifted) '5'.code else '('.code
            KeyEvent.KEYCODE_6 -> if (isNumRowShifted) '6'.code else '-'.code
            KeyEvent.KEYCODE_7 -> if (isNumRowShifted) '7'.code else 'è'.code
            KeyEvent.KEYCODE_8 -> if (isNumRowShifted) '8'.code else '_'.code
            KeyEvent.KEYCODE_9 -> if (isNumRowShifted) '9'.code else 'ç'.code
            KeyEvent.KEYCODE_0 -> if (isNumRowShifted) '0'.code else 'à'.code
            KeyEvent.KEYCODE_MINUS -> if (isNumRowShifted) '°'.code else ')'.code
            KeyEvent.KEYCODE_EQUALS -> if (isNumRowShifted) '+'.code else '='.code

            // Row 1
            KeyEvent.KEYCODE_Q -> if (isUpper) 'A'.code else 'a'.code
            KeyEvent.KEYCODE_W -> if (isUpper) 'Z'.code else 'z'.code
            KeyEvent.KEYCODE_E -> if (isUpper) 'E'.code else 'e'.code
            KeyEvent.KEYCODE_R -> if (isUpper) 'R'.code else 'r'.code
            KeyEvent.KEYCODE_T -> if (isUpper) 'T'.code else 't'.code
            KeyEvent.KEYCODE_Y -> if (isUpper) 'Y'.code else 'y'.code
            KeyEvent.KEYCODE_U -> if (isUpper) 'U'.code else 'u'.code
            KeyEvent.KEYCODE_I -> if (isUpper) 'I'.code else 'i'.code
            KeyEvent.KEYCODE_O -> if (isUpper) 'O'.code else 'o'.code
            KeyEvent.KEYCODE_P -> if (isUpper) 'P'.code else 'p'.code
            KeyEvent.KEYCODE_LEFT_BRACKET -> if (isShift) KeyCharacterMap.COMBINING_ACCENT or 0x00A8 else KeyCharacterMap.COMBINING_ACCENT or 0x02C6 // ¨ or ^
            KeyEvent.KEYCODE_RIGHT_BRACKET -> if (isShift) '£'.code else '$'.code

            // Row 2
            KeyEvent.KEYCODE_A -> if (isUpper) 'Q'.code else 'q'.code
            KeyEvent.KEYCODE_S -> if (isUpper) 'S'.code else 's'.code
            KeyEvent.KEYCODE_D -> if (isUpper) 'D'.code else 'd'.code
            KeyEvent.KEYCODE_F -> if (isUpper) 'F'.code else 'f'.code
            KeyEvent.KEYCODE_G -> if (isUpper) 'G'.code else 'g'.code
            KeyEvent.KEYCODE_H -> if (isUpper) 'H'.code else 'h'.code
            KeyEvent.KEYCODE_J -> if (isUpper) 'J'.code else 'j'.code
            KeyEvent.KEYCODE_K -> if (isUpper) 'K'.code else 'k'.code
            KeyEvent.KEYCODE_L -> if (isUpper) 'L'.code else 'l'.code
            KeyEvent.KEYCODE_SEMICOLON -> if (isUpper) 'M'.code else 'm'.code
            KeyEvent.KEYCODE_APOSTROPHE -> if (isShift) '%'.code else 'ù'.code
            KeyEvent.KEYCODE_BACKSLASH -> if (isShift) 'µ'.code else '*'.code

            // Row 3
            KeyEvent.KEYCODE_Z -> if (isUpper) 'W'.code else 'w'.code
            KeyEvent.KEYCODE_X -> if (isUpper) 'X'.code else 'x'.code
            KeyEvent.KEYCODE_C -> if (isUpper) 'C'.code else 'c'.code
            KeyEvent.KEYCODE_V -> if (isUpper) 'V'.code else 'v'.code
            KeyEvent.KEYCODE_B -> if (isUpper) 'B'.code else 'b'.code
            KeyEvent.KEYCODE_N -> if (isUpper) 'N'.code else 'n'.code
            KeyEvent.KEYCODE_M -> if (isShift) '?'.code else ','.code
            KeyEvent.KEYCODE_COMMA -> if (isShift) '.'.code else ';'.code
            KeyEvent.KEYCODE_PERIOD -> if (isShift) '/'.code else ':'.code
            KeyEvent.KEYCODE_SLASH -> if (isShift) '§'.code else '!'.code

            else -> null
        }
    }

    private fun mapQwertz(keyCode: Int, isShift: Boolean, isCaps: Boolean, isAltGr: Boolean): Int? {
        if (isAltGr) {
            return when (keyCode) {
                KeyEvent.KEYCODE_2 -> '²'.code
                KeyEvent.KEYCODE_3 -> '³'.code
                KeyEvent.KEYCODE_7 -> '{'.code
                KeyEvent.KEYCODE_8 -> '['.code
                KeyEvent.KEYCODE_9 -> ']'.code
                KeyEvent.KEYCODE_0 -> '}'.code
                KeyEvent.KEYCODE_MINUS -> '\\'.code
                KeyEvent.KEYCODE_Q -> '@'.code
                KeyEvent.KEYCODE_E -> '€'.code
                KeyEvent.KEYCODE_RIGHT_BRACKET -> '~'.code
                KeyEvent.KEYCODE_M -> 'µ'.code
                else -> null
            }
        }
        val isUpper = isShift xor isCaps
        return when (keyCode) {
            KeyEvent.KEYCODE_Y -> if (isUpper) 'Z'.code else 'z'.code
            KeyEvent.KEYCODE_Z -> if (isUpper) 'Y'.code else 'y'.code
            KeyEvent.KEYCODE_LEFT_BRACKET -> if (isUpper) 'Ü'.code else 'ü'.code
            KeyEvent.KEYCODE_SEMICOLON -> if (isUpper) 'Ö'.code else 'ö'.code
            KeyEvent.KEYCODE_APOSTROPHE -> if (isUpper) 'Ä'.code else 'ä'.code
            KeyEvent.KEYCODE_MINUS -> if (isShift) '?'.code else 'ß'.code
            KeyEvent.KEYCODE_EQUALS -> if (isShift) KeyCharacterMap.COMBINING_ACCENT or 0x02CB else KeyCharacterMap.COMBINING_ACCENT or 0x00B4 // ` or ´
            KeyEvent.KEYCODE_RIGHT_BRACKET -> if (isShift) '*'.code else '+'.code
            KeyEvent.KEYCODE_SLASH -> if (isShift) '_'.code else '-'.code
            else -> null
        }
    }

    private fun mapDvorak(keyCode: Int, isShift: Boolean, isCaps: Boolean): Int? {
        val isUpper = isShift xor isCaps
        return when (keyCode) {
            KeyEvent.KEYCODE_APOSTROPHE -> if (isShift) '_'.code else '-'.code
            KeyEvent.KEYCODE_COMMA -> if (isUpper) 'W'.code else 'w'.code
            KeyEvent.KEYCODE_PERIOD -> if (isUpper) 'V'.code else 'v'.code
            KeyEvent.KEYCODE_SLASH -> if (isUpper) 'Z'.code else 'z'.code
            KeyEvent.KEYCODE_Q -> if (isShift) '"'.code else '\''.code
            KeyEvent.KEYCODE_W -> if (isShift) '<'.code else ','.code
            KeyEvent.KEYCODE_E -> if (isShift) '>'.code else '.'.code
            KeyEvent.KEYCODE_R -> if (isUpper) 'P'.code else 'p'.code
            KeyEvent.KEYCODE_T -> if (isUpper) 'Y'.code else 'y'.code
            KeyEvent.KEYCODE_Y -> if (isUpper) 'F'.code else 'f'.code
            KeyEvent.KEYCODE_U -> if (isUpper) 'G'.code else 'g'.code
            KeyEvent.KEYCODE_I -> if (isUpper) 'C'.code else 'c'.code
            KeyEvent.KEYCODE_O -> if (isUpper) 'R'.code else 'r'.code
            KeyEvent.KEYCODE_P -> if (isUpper) 'L'.code else 'l'.code
            KeyEvent.KEYCODE_LEFT_BRACKET -> if (isShift) '?'.code else '/'.code
            KeyEvent.KEYCODE_RIGHT_BRACKET -> if (isShift) '+'.code else '='.code
            KeyEvent.KEYCODE_A -> if (isUpper) 'A'.code else 'a'.code
            KeyEvent.KEYCODE_S -> if (isUpper) 'O'.code else 'o'.code
            KeyEvent.KEYCODE_D -> if (isUpper) 'E'.code else 'e'.code
            KeyEvent.KEYCODE_F -> if (isUpper) 'U'.code else 'u'.code
            KeyEvent.KEYCODE_G -> if (isUpper) 'I'.code else 'i'.code
            KeyEvent.KEYCODE_H -> if (isUpper) 'D'.code else 'd'.code
            KeyEvent.KEYCODE_J -> if (isUpper) 'H'.code else 'h'.code
            KeyEvent.KEYCODE_K -> if (isUpper) 'T'.code else 't'.code
            KeyEvent.KEYCODE_L -> if (isUpper) 'N'.code else 'n'.code
            KeyEvent.KEYCODE_SEMICOLON -> if (isUpper) 'S'.code else 's'.code
            KeyEvent.KEYCODE_Z -> if (isShift) ':'.code else ';'.code
            KeyEvent.KEYCODE_X -> if (isUpper) 'Q'.code else 'q'.code
            KeyEvent.KEYCODE_C -> if (isUpper) 'J'.code else 'j'.code
            KeyEvent.KEYCODE_V -> if (isUpper) 'K'.code else 'k'.code
            KeyEvent.KEYCODE_B -> if (isUpper) 'X'.code else 'x'.code
            KeyEvent.KEYCODE_N -> if (isUpper) 'B'.code else 'b'.code
            KeyEvent.KEYCODE_M -> if (isUpper) 'M'.code else 'm'.code
            else -> null
        }
    }

    private fun mapColemak(keyCode: Int, isShift: Boolean, isCaps: Boolean): Int? {
        val isUpper = isShift xor isCaps
        return when (keyCode) {
            KeyEvent.KEYCODE_E -> if (isUpper) 'F'.code else 'f'.code
            KeyEvent.KEYCODE_R -> if (isUpper) 'P'.code else 'p'.code
            KeyEvent.KEYCODE_T -> if (isUpper) 'G'.code else 'g'.code
            KeyEvent.KEYCODE_Y -> if (isUpper) 'J'.code else 'j'.code
            KeyEvent.KEYCODE_U -> if (isUpper) 'L'.code else 'l'.code
            KeyEvent.KEYCODE_I -> if (isUpper) 'U'.code else 'u'.code
            KeyEvent.KEYCODE_O -> if (isUpper) 'Y'.code else 'y'.code
            KeyEvent.KEYCODE_P -> if (isShift) ':'.code else ';'.code
            KeyEvent.KEYCODE_S -> if (isUpper) 'R'.code else 'r'.code
            KeyEvent.KEYCODE_D -> if (isUpper) 'S'.code else 's'.code
            KeyEvent.KEYCODE_F -> if (isUpper) 'T'.code else 't'.code
            KeyEvent.KEYCODE_G -> if (isUpper) 'D'.code else 'd'.code
            KeyEvent.KEYCODE_J -> if (isUpper) 'N'.code else 'n'.code
            KeyEvent.KEYCODE_K -> if (isUpper) 'E'.code else 'e'.code
            KeyEvent.KEYCODE_L -> if (isUpper) 'I'.code else 'i'.code
            KeyEvent.KEYCODE_SEMICOLON -> if (isUpper) 'O'.code else 'o'.code
            KeyEvent.KEYCODE_N -> if (isUpper) 'K'.code else 'k'.code
            else -> null
        }
    }

    private fun mapColemakDh(keyCode: Int, isShift: Boolean, isCaps: Boolean): Int? {
        val isUpper = isShift xor isCaps
        return when (keyCode) {
            KeyEvent.KEYCODE_D -> if (isUpper) 'S'.code else 's'.code
            KeyEvent.KEYCODE_F -> if (isUpper) 'T'.code else 't'.code
            KeyEvent.KEYCODE_G -> if (isUpper) 'G'.code else 'g'.code
            KeyEvent.KEYCODE_H -> if (isUpper) 'M'.code else 'm'.code
            KeyEvent.KEYCODE_J -> if (isUpper) 'N'.code else 'n'.code
            KeyEvent.KEYCODE_K -> if (isUpper) 'E'.code else 'e'.code
            KeyEvent.KEYCODE_V -> if (isUpper) 'D'.code else 'd'.code
            KeyEvent.KEYCODE_B -> if (isUpper) 'V'.code else 'v'.code
            KeyEvent.KEYCODE_M -> if (isUpper) 'H'.code else 'h'.code
            else -> mapColemak(keyCode, isShift, isCaps)
        }
    }

    private fun mapWorkman(keyCode: Int, isShift: Boolean, isCaps: Boolean): Int? {
        val isUpper = isShift xor isCaps
        return when (keyCode) {
            KeyEvent.KEYCODE_W -> if (isUpper) 'D'.code else 'd'.code
            KeyEvent.KEYCODE_E -> if (isUpper) 'R'.code else 'r'.code
            KeyEvent.KEYCODE_R -> if (isUpper) 'W'.code else 'w'.code
            KeyEvent.KEYCODE_T -> if (isUpper) 'B'.code else 'b'.code
            KeyEvent.KEYCODE_Y -> if (isUpper) 'J'.code else 'j'.code
            KeyEvent.KEYCODE_U -> if (isUpper) 'F'.code else 'f'.code
            KeyEvent.KEYCODE_I -> if (isUpper) 'U'.code else 'u'.code
            KeyEvent.KEYCODE_O -> if (isUpper) 'P'.code else 'p'.code
            KeyEvent.KEYCODE_P -> if (isShift) ':'.code else ';'.code
            KeyEvent.KEYCODE_D -> if (isUpper) 'H'.code else 'h'.code
            KeyEvent.KEYCODE_F -> if (isUpper) 'T'.code else 't'.code
            KeyEvent.KEYCODE_H -> if (isUpper) 'Y'.code else 'y'.code
            KeyEvent.KEYCODE_J -> if (isUpper) 'N'.code else 'n'.code
            KeyEvent.KEYCODE_K -> if (isUpper) 'E'.code else 'e'.code
            KeyEvent.KEYCODE_L -> if (isUpper) 'O'.code else 'o'.code
            KeyEvent.KEYCODE_SEMICOLON -> if (isUpper) 'I'.code else 'i'.code
            KeyEvent.KEYCODE_C -> if (isUpper) 'M'.code else 'm'.code
            KeyEvent.KEYCODE_V -> if (isUpper) 'C'.code else 'c'.code
            KeyEvent.KEYCODE_B -> if (isUpper) 'V'.code else 'v'.code
            KeyEvent.KEYCODE_N -> if (isUpper) 'K'.code else 'k'.code
            KeyEvent.KEYCODE_M -> if (isUpper) 'L'.code else 'l'.code
            else -> null
        }
    }

    private fun mapBepo(keyCode: Int, isShift: Boolean, isCaps: Boolean): Int? {
        val isUpper = isShift xor isCaps
        return when (keyCode) {
            KeyEvent.KEYCODE_Q -> if (isUpper) 'B'.code else 'b'.code
            KeyEvent.KEYCODE_W -> if (isUpper) 'É'.code else 'é'.code
            KeyEvent.KEYCODE_E -> if (isUpper) 'P'.code else 'p'.code
            KeyEvent.KEYCODE_R -> if (isUpper) 'O'.code else 'o'.code
            KeyEvent.KEYCODE_T -> if (isUpper) 'È'.code else 'è'.code
            KeyEvent.KEYCODE_Y -> if (isShift) '!'.code else '^'.code
            KeyEvent.KEYCODE_U -> if (isUpper) 'V'.code else 'v'.code
            KeyEvent.KEYCODE_I -> if (isUpper) 'D'.code else 'd'.code
            KeyEvent.KEYCODE_O -> if (isUpper) 'L'.code else 'l'.code
            KeyEvent.KEYCODE_P -> if (isUpper) 'J'.code else 'j'.code
            KeyEvent.KEYCODE_LEFT_BRACKET -> if (isUpper) 'Z'.code else 'z'.code
            KeyEvent.KEYCODE_RIGHT_BRACKET -> if (isUpper) 'W'.code else 'w'.code

            KeyEvent.KEYCODE_A -> if (isUpper) 'A'.code else 'a'.code
            KeyEvent.KEYCODE_S -> if (isUpper) 'U'.code else 'u'.code
            KeyEvent.KEYCODE_D -> if (isUpper) 'I'.code else 'i'.code
            KeyEvent.KEYCODE_F -> if (isUpper) 'E'.code else 'e'.code
            KeyEvent.KEYCODE_G -> if (isShift) ','.code else ';'.code
            KeyEvent.KEYCODE_H -> if (isUpper) 'C'.code else 'c'.code
            KeyEvent.KEYCODE_J -> if (isUpper) 'T'.code else 't'.code
            KeyEvent.KEYCODE_K -> if (isUpper) 'S'.code else 's'.code
            KeyEvent.KEYCODE_L -> if (isUpper) 'R'.code else 'r'.code
            KeyEvent.KEYCODE_SEMICOLON -> if (isUpper) 'N'.code else 'n'.code
            KeyEvent.KEYCODE_APOSTROPHE -> if (isUpper) 'M'.code else 'm'.code
            KeyEvent.KEYCODE_BACKSLASH -> if (isUpper) 'Ç'.code else 'ç'.code

            KeyEvent.KEYCODE_Z -> if (isUpper) 'À'.code else 'à'.code
            KeyEvent.KEYCODE_X -> if (isUpper) 'Y'.code else 'y'.code
            KeyEvent.KEYCODE_C -> if (isUpper) 'X'.code else 'x'.code
            KeyEvent.KEYCODE_V -> if (isShift) ':'.code else '.'.code
            KeyEvent.KEYCODE_B -> if (isUpper) 'K'.code else 'k'.code
            KeyEvent.KEYCODE_N -> if (isShift) '?'.code else '\''.code
            KeyEvent.KEYCODE_M -> if (isUpper) 'Q'.code else 'q'.code
            KeyEvent.KEYCODE_COMMA -> if (isUpper) 'G'.code else 'g'.code
            KeyEvent.KEYCODE_PERIOD -> if (isUpper) 'H'.code else 'h'.code
            KeyEvent.KEYCODE_SLASH -> if (isUpper) 'F'.code else 'f'.code
            else -> null
        }
    }
}
