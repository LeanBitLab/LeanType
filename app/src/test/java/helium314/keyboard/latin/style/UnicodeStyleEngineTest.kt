// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.style

import android.text.InputType
import android.view.inputmethod.EditorInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UnicodeStyleEngineTest {

    @Test
    fun testFrakturExceptions() {
        val fraktur = UnicodeStyleEngine.getStyle("math_fraktur")!!
        // Check capital C -> U+212D (BLACK-LETTER CAPITAL C)
        assertEquals(0x212D, fraktur.mapCodePoint('C'.code))
        // Check capital H -> U+210C (BLACK-LETTER CAPITAL H)
        assertEquals(0x210C, fraktur.mapCodePoint('H'.code))
        // Check capital I -> U+2111 (BLACK-LETTER CAPITAL I)
        assertEquals(0x2111, fraktur.mapCodePoint('I'.code))
        // Check capital R -> U+211C (BLACK-LETTER CAPITAL R)
        assertEquals(0x211C, fraktur.mapCodePoint('R'.code))
        // Check capital Z -> U+2128 (BLACK-LETTER CAPITAL Z)
        assertEquals(0x2128, fraktur.mapCodePoint('Z'.code))

        // Normal letters
        assertEquals(0x1D504, fraktur.mapCodePoint('A'.code))
        assertEquals(0x1D505, fraktur.mapCodePoint('B'.code))
        assertEquals(0x1D51E, fraktur.mapCodePoint('a'.code))
    }

    @Test
    fun testScriptExceptions() {
        val script = UnicodeStyleEngine.getStyle("math_script")!!
        // BMP Letterlike uppercase: B, E, F, H, I, L, M, R
        assertEquals(0x212C, script.mapCodePoint('B'.code))
        assertEquals(0x2130, script.mapCodePoint('E'.code))
        assertEquals(0x2131, script.mapCodePoint('F'.code))
        assertEquals(0x210B, script.mapCodePoint('H'.code))
        assertEquals(0x2110, script.mapCodePoint('I'.code))
        assertEquals(0x2112, script.mapCodePoint('L'.code))
        assertEquals(0x2133, script.mapCodePoint('M'.code))
        assertEquals(0x211B, script.mapCodePoint('R'.code))

        // BMP Letterlike lowercase: e, g, o
        assertEquals(0x212F, script.mapCodePoint('e'.code))
        assertEquals(0x210A, script.mapCodePoint('g'.code))
        assertEquals(0x2134, script.mapCodePoint('o'.code))

        // Normal letters
        assertEquals(0x1D49C, script.mapCodePoint('A'.code))
        assertEquals(0x1D4B6, script.mapCodePoint('a'.code))
    }

    @Test
    fun testDoubleStruckExceptions() {
        val ds = UnicodeStyleEngine.getStyle("math_double_struck")!!
        // BMP Letterlike uppercase: C, H, N, P, Q, R, Z
        assertEquals(0x2102, ds.mapCodePoint('C'.code))
        assertEquals(0x210D, ds.mapCodePoint('H'.code))
        assertEquals(0x2115, ds.mapCodePoint('N'.code))
        assertEquals(0x2119, ds.mapCodePoint('P'.code))
        assertEquals(0x211A, ds.mapCodePoint('Q'.code))
        assertEquals(0x211D, ds.mapCodePoint('R'.code))
        assertEquals(0x2124, ds.mapCodePoint('Z'.code))

        // Digits
        assertEquals(0x1D7D8, ds.mapCodePoint('0'.code))
        assertEquals(0x1D7E1, ds.mapCodePoint('9'.code))
    }

    @Test
    fun testItalicPlanckConstant() {
        val italic = UnicodeStyleEngine.getStyle("math_italic")!!
        assertEquals(0x210E, italic.mapCodePoint('h'.code))
        assertEquals(0x1D434, italic.mapCodePoint('A'.code))
        assertEquals(0x1D44E, italic.mapCodePoint('a'.code))
    }

    @Test
    fun testTransformText() {
        val input = "Hello World 123"
        val bold = UnicodeStyleEngine.transformText(input, "math_bold")
        assertNotEquals(input, bold)
        val reverted = UnicodeStyleEngine.toPlainText(bold)
        assertEquals(input, reverted)

        val circled = UnicodeStyleEngine.transformText("ABC 123", "enclosed_circled")
        assertEquals("ⒶⒷⒸ ①②③", circled)
        assertEquals("ABC 123", UnicodeStyleEngine.toPlainText(circled))

        val fullwidth = UnicodeStyleEngine.transformText("Hello", "aesthetic_fullwidth")
        assertEquals("Ｈｅｌｌｏ", fullwidth)
        assertEquals("Hello", UnicodeStyleEngine.toPlainText(fullwidth))
    }

    @Test
    fun testCombiningMarks() {
        val input = "test"
        val strike = UnicodeStyleEngine.transformText(input, "decorative_strikethrough")
        assertTrue(strike.contains('\u0336'))
        assertEquals("test", UnicodeStyleEngine.toPlainText(strike))

        val underline = UnicodeStyleEngine.transformText(input, "decorative_underline")
        assertTrue(underline.contains('\u0332'))
        assertEquals("test", UnicodeStyleEngine.toPlainText(underline))
    }

    @Test
    fun testInvertedText() {
        val input = "hello"
        val inverted = UnicodeStyleEngine.transformText(input, "transform_inverted")
        // 'h' -> ɥ, 'e' -> ǝ, 'l' -> l, 'o' -> o reversed: "oןןǝɥ"
        assertEquals("oןןǝɥ", inverted)
    }

    @Test
    fun testLiveKeystrokeMapping() {
        val frakturA = UnicodeStyleEngine.mapKeystroke('A'.code, "math_fraktur")
        assertEquals("\uD835\uDD04", frakturA)

        // Strikethrough live keypress
        val strikeB = UnicodeStyleEngine.mapKeystroke('b'.code, "decorative_strikethrough")
        assertEquals("b\u0336", strikeB)

        // Inverted does not support live typing (returns plain key)
        val invertedC = UnicodeStyleEngine.mapKeystroke('c'.code, "transform_inverted")
        assertEquals("c", invertedC)
    }

    @Test
    fun testAllStylesHaveNonEmptyDefinitions() {
        val all = UnicodeStyleEngine.getAllStyles()
        assertTrue(all.size >= 20)
        for (style in all) {
            assertTrue(style.id.isNotBlank())
            assertTrue(style.name.isNotBlank())
            assertTrue(style.samplePreview.isNotBlank())
        }
    }

    @Test
    fun testTransformAlreadyStyledText() {
        // Direct conversion of already-styled text into another style
        val fraktur = UnicodeStyleEngine.transformText("Hello", "math_fraktur")
        assertEquals("ℌ𝔢𝔩𝔩𝔬", fraktur)

        val scriptFromFraktur = UnicodeStyleEngine.transformText(fraktur, "math_script")
        assertEquals("ℋℯ𝓁𝓁ℴ", scriptFromFraktur)

        val boldFromScript = UnicodeStyleEngine.transformText(scriptFromFraktur, "math_bold")
        assertEquals("𝐇𝐞𝐥𝐥𝐨", boldFromScript)

        val plainFromBold = UnicodeStyleEngine.toPlainText(boldFromScript)
        assertEquals("Hello", plainFromBold)
    }

    @Test
    fun testIsStyledCharacter() {
        assertTrue(UnicodeStyleEngine.isStyledCharacter(0x1D505)) // 𝔅 Fraktur B
        assertTrue(UnicodeStyleEngine.isStyledCharacter(0x1D524)) // 𝔤 Fraktur g
        assertTrue(UnicodeStyleEngine.isStyledCharacter(0x210C))  // ℌ Fraktur H (Letterlike BMP)
        assertTrue(UnicodeStyleEngine.isStyledCharacter(0x0336))  // Combining Strikethrough

        assertFalse(UnicodeStyleEngine.isStyledCharacter('A'.code))
        assertFalse(UnicodeStyleEngine.isStyledCharacter('z'.code))
        assertFalse(UnicodeStyleEngine.isStyledCharacter('5'.code))
        assertFalse(UnicodeStyleEngine.isStyledCharacter(' '.code))
    }

    @Test
    fun testIsLiveStyleAllowed() {
        assertFalse(UnicodeStyleEngine.isLiveStyleAllowed(null))

        val passwordInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        assertFalse(UnicodeStyleEngine.isLiveStyleAllowed(passwordInfo))

        val uriInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        assertFalse(UnicodeStyleEngine.isLiveStyleAllowed(uriInfo))

        val emailInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        assertFalse(UnicodeStyleEngine.isLiveStyleAllowed(emailInfo))

        val normalInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        assertTrue(UnicodeStyleEngine.isLiveStyleAllowed(normalInfo))
    }

    @Test
    fun testLiveStyleBatchTransformAndDictionaryIsolation() {
        UnicodeStyleEngine.activeLiveStyleId = "math_fraktur"
        val rawBatch = "hello"
        val styled = UnicodeStyleEngine.transformText(rawBatch, UnicodeStyleEngine.activeLiveStyleId!!)
        assertEquals("𝔥𝔢𝔩𝔩𝔬", styled)

        // Ensure toPlainText normalizes it back for dictionary learning and text expander
        val plain = UnicodeStyleEngine.toPlainText(styled)
        assertEquals("hello", plain)
        UnicodeStyleEngine.clearActiveLiveStyle()
    }
}
