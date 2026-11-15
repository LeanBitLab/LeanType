// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.style

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomTextStylesTest {
    private val upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val lower = "abcdefghijklmnopqrstuvwxyz"
    // shifted copies of the alphabet are enough to verify the mapping
    private val fancyUpper = "ⒶⒷⒸⒹⒺⒻⒼⒽⒾⒿⓀⓁⓂⓃⓄⓅⓆⓇⓈⓉⓊⓋⓌⓍⓎⓏ"
    private val fancyLower = "ⓐⓑⓒⓓⓔⓕⓖⓗⓘⓙⓚⓛⓜⓝⓞⓟⓠⓡⓢⓣⓤⓥⓦⓧⓨⓩ"
    private val fancyDigits = "⓪①②③④⑤⑥⑦⑧⑨"

    @AfterTest fun reset() = UnicodeStyleEngine.setCustomStyles("")

    @Test fun validatesLines() {
        assertTrue(UnicodeStyleEngine.isValidCustomStylesText(""))
        assertTrue(UnicodeStyleEngine.isValidCustomStylesText("Mine: $fancyUpper$fancyLower"))
        assertTrue(UnicodeStyleEngine.isValidCustomStylesText("Mine: $fancyUpper $fancyLower $fancyDigits\n\nTwo: $fancyUpper$fancyLower"))
        assertFalse(UnicodeStyleEngine.isValidCustomStylesText("no colon $fancyUpper$fancyLower"))
        assertFalse(UnicodeStyleEngine.isValidCustomStylesText(": $fancyUpper$fancyLower"))
        assertFalse(UnicodeStyleEngine.isValidCustomStylesText("Short: $fancyUpper"))
        assertFalse(UnicodeStyleEngine.isValidCustomStylesText("Mine: $fancyUpper$fancyLower\nMine: $fancyUpper$fancyLower"))
    }

    @Test fun transformsAndUndoes() {
        UnicodeStyleEngine.setCustomStyles("Mine: $fancyUpper$fancyLower$fancyDigits")
        val style = assertNotNull(UnicodeStyleEngine.getAllStyles().firstOrNull { it.name == "Mine" })
        val styled = UnicodeStyleEngine.transformText("Hi 42!", style.id)
        assertEquals("Ⓗⓘ ④②!", styled)
        assertEquals("Hi 42!", UnicodeStyleEngine.toPlainText(styled))
        assertTrue(UnicodeStyleEngine.isStyledCharacter('Ⓗ'.code))
    }

    @Test fun acceptsFullWidthColon() {
        assertTrue(UnicodeStyleEngine.isValidCustomStylesText("Mine\uFF1A $fancyUpper$fancyLower"))
        UnicodeStyleEngine.setCustomStyles("Mine\uFF1A$fancyUpper$fancyLower")
        assertEquals("ⓐ", UnicodeStyleEngine.transformText("a", "custom_Mine"))
    }

    @Test fun acceptsThePastedExamples() {
        val paren = (0 until 26).joinToString("") { String(Character.toChars(0x1F110 + it)) } +
            (0 until 26).joinToString("") { String(Character.toChars(0x249C + it)) }
        val bubble = fancyUpper + fancyLower + fancyDigits
        assertTrue(UnicodeStyleEngine.isValidCustomStylesText("Paren: $paren\nBubble: $bubble"))
        assertTrue(UnicodeStyleEngine.isValidCustomStylesText("Paren: $paren\r\nBubble: $bubble\r\n"))
    }

    @Test fun ignoresInvisibleCharactersFromPasting() {
        // circled M is also an emoji, so apps like to add a variation selector after it
        val withSelector = fancyUpper.replace("Ⓜ", "Ⓜ\uFE0F") + fancyLower
        assertTrue(UnicodeStyleEngine.isValidCustomStylesText("Mine: $withSelector"))
        assertTrue(UnicodeStyleEngine.isValidCustomStylesText("Mine: \u200B$fancyUpper\u200D$fancyLower\uFEFF"))
        UnicodeStyleEngine.setCustomStyles("Mine: $withSelector")
        assertEquals("ⓐⓂ", UnicodeStyleEngine.transformText("aM", "custom_Mine"))
    }

    @Test fun lettersOnlyKeepsDigits() {
        UnicodeStyleEngine.setCustomStyles("Mine: $fancyUpper$fancyLower")
        assertEquals("ⓐ1", UnicodeStyleEngine.transformText("a1", "custom_Mine"))
    }

    @Test fun surrogatePairsStayIntact() {
        val bold = String(Character.toChars(0x1D400)) // MATHEMATICAL BOLD CAPITAL A
        val letters = (0 until 52).joinToString("") { String(Character.toChars(0x1D400 + it)) }
        UnicodeStyleEngine.setCustomStyles("Wide: $letters")
        assertEquals(bold + String(Character.toChars(0x1D400 + 26)), UnicodeStyleEngine.transformText("Aa", "custom_Wide"))
    }

    @Test fun removingStylesDropsThem() {
        UnicodeStyleEngine.setCustomStyles("Mine: $fancyUpper$fancyLower")
        assertNotNull(UnicodeStyleEngine.getStyle("custom_Mine"))
        UnicodeStyleEngine.setCustomStyles("")
        assertNull(UnicodeStyleEngine.getStyle("custom_Mine"))
        assertNotNull(UnicodeStyleEngine.getStyle("math_bold"))
    }
}
