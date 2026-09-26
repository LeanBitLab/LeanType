// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.event

import android.view.KeyCharacterMap
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhysicalKeyboardLayoutsTest {

    @Test
    fun testAzertyLetterMapping() {
        // Q -> a / A
        assertEquals('a'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_Q, 0, "azerty"))
        assertEquals('A'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_Q, KeyEvent.META_SHIFT_ON, "azerty"))

        // A -> q / Q
        assertEquals('q'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_A, 0, "azerty"))
        assertEquals('Q'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_A, KeyEvent.META_SHIFT_ON, "azerty"))

        // W -> z / Z
        assertEquals('z'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_W, 0, "azerty"))
        assertEquals('Z'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_W, KeyEvent.META_SHIFT_ON, "azerty"))

        // Z -> w / W
        assertEquals('w'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_Z, 0, "azerty"))
        assertEquals('W'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_Z, KeyEvent.META_SHIFT_ON, "azerty"))

        // Semicolon -> m / M
        assertEquals('m'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_SEMICOLON, 0, "azerty"))
        assertEquals('M'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_SEMICOLON, KeyEvent.META_SHIFT_ON, "azerty"))
    }

    @Test
    fun testAzertyNumberAndPunctuationMapping() {
        // Number row: unshifted is symbols/accents, shifted is digits
        assertEquals('&'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_1, 0, "azerty"))
        assertEquals('1'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_1, KeyEvent.META_SHIFT_ON, "azerty"))
        assertEquals('é'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_2, 0, "azerty"))
        assertEquals('2'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_2, KeyEvent.META_SHIFT_ON, "azerty"))
        assertEquals('è'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_7, 0, "azerty"))
        assertEquals('7'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_7, KeyEvent.META_SHIFT_ON, "azerty"))
        assertEquals('à'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_0, 0, "azerty"))
        assertEquals('0'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_0, KeyEvent.META_SHIFT_ON, "azerty"))

        // Punctuation: M -> , / ?
        assertEquals(','.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_M, 0, "azerty"))
        assertEquals('?'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_M, KeyEvent.META_SHIFT_ON, "azerty"))

        // Comma -> ; / .
        assertEquals(';'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_COMMA, 0, "azerty"))
        assertEquals('.'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_COMMA, KeyEvent.META_SHIFT_ON, "azerty"))
    }

    @Test
    fun testAzertyDeadKeys() {
        // [ key on AZERTY is dead circumflex (unshifted) and dead diaeresis (shifted)
        val deadCircumflex = PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_LEFT_BRACKET, 0, "azerty")
        assertEquals(KeyCharacterMap.COMBINING_ACCENT or 0x02C6, deadCircumflex)

        val deadUmlaut = PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.META_SHIFT_ON, "azerty")
        assertEquals(KeyCharacterMap.COMBINING_ACCENT or 0x00A8, deadUmlaut)
    }

    @Test
    fun testAzertyAltGr() {
        // E with AltGr -> Euro (€)
        assertEquals('€'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_E, KeyEvent.META_ALT_RIGHT_ON, "azerty"))

        // 0 with AltGr -> @
        assertEquals('@'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_0, KeyEvent.META_ALT_RIGHT_ON, "azerty"))
    }

    @Test
    fun testAzertyCapsLock() {
        // CapsLock ON gives uppercase
        assertEquals('A'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_Q, KeyEvent.META_CAPS_LOCK_ON, "azerty"))

        // CapsLock ON + Shift gives lowercase
        assertEquals('a'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_Q, KeyEvent.META_CAPS_LOCK_ON or KeyEvent.META_SHIFT_ON, "azerty"))

        // CapsLock on AZERTY number row types digits
        assertEquals('1'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_1, KeyEvent.META_CAPS_LOCK_ON, "azerty"))
    }

    @Test
    fun testAzertyShortcutRemapping() {
        // Physical Q (where A is) -> KEYCODE_A for Ctrl+A
        assertEquals(KeyEvent.KEYCODE_A, PhysicalKeyboardLayouts.remapKeyCodeForShortcuts(KeyEvent.KEYCODE_Q, "azerty"))
        assertEquals(KeyEvent.KEYCODE_Q, PhysicalKeyboardLayouts.remapKeyCodeForShortcuts(KeyEvent.KEYCODE_A, "azerty"))
        assertEquals(KeyEvent.KEYCODE_Z, PhysicalKeyboardLayouts.remapKeyCodeForShortcuts(KeyEvent.KEYCODE_W, "azerty"))
        assertEquals(KeyEvent.KEYCODE_W, PhysicalKeyboardLayouts.remapKeyCodeForShortcuts(KeyEvent.KEYCODE_Z, "azerty"))
        assertEquals(KeyEvent.KEYCODE_C, PhysicalKeyboardLayouts.remapKeyCodeForShortcuts(KeyEvent.KEYCODE_C, "azerty"))
    }

    @Test
    fun testQwertzMapping() {
        assertEquals('z'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_Y, 0, "qwertz"))
        assertEquals('Z'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_Y, KeyEvent.META_SHIFT_ON, "qwertz"))
        assertEquals('y'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_Z, 0, "qwertz"))
        assertEquals('Y'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_Z, KeyEvent.META_SHIFT_ON, "qwertz"))
        assertEquals('ö'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_SEMICOLON, 0, "qwertz"))
        assertEquals('Ö'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_SEMICOLON, KeyEvent.META_SHIFT_ON, "qwertz"))
        assertEquals('ä'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_APOSTROPHE, 0, "qwertz"))
        assertEquals('ü'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_LEFT_BRACKET, 0, "qwertz"))

        // Shortcut remapping
        assertEquals(KeyEvent.KEYCODE_Z, PhysicalKeyboardLayouts.remapKeyCodeForShortcuts(KeyEvent.KEYCODE_Y, "qwertz"))
        assertEquals(KeyEvent.KEYCODE_Y, PhysicalKeyboardLayouts.remapKeyCodeForShortcuts(KeyEvent.KEYCODE_Z, "qwertz"))
    }

    @Test
    fun testDvorakMapping() {
        assertEquals('\''.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_Q, 0, "dvorak"))
        assertEquals('"'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_Q, KeyEvent.META_SHIFT_ON, "dvorak"))
        assertEquals('a'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_A, 0, "dvorak"))
        assertEquals('o'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_S, 0, "dvorak"))
        assertEquals('e'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_D, 0, "dvorak"))
        assertEquals('u'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_F, 0, "dvorak"))
    }

    @Test
    fun testColemakMapping() {
        assertEquals('f'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_E, 0, "colemak"))
        assertEquals('p'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_R, 0, "colemak"))
        assertEquals('g'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_T, 0, "colemak"))
        assertEquals('j'.code, PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_Y, 0, "colemak"))
    }

    @Test
    fun testSystemDefaultAndQwertyPassthrough() {
        // System default and QWERT Y should return null so system KeyCharacterMap handles them
        assertNull(PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_Q, 0, "system_default"))
        assertNull(PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_Q, 0, "qwerty"))
        assertNull(PhysicalKeyboardLayouts.mapHardwareKey(KeyEvent.KEYCODE_Q, 0, "pcqwerty"))
    }
}
