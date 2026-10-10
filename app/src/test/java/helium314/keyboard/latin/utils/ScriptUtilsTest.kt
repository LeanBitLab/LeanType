package helium314.keyboard.latin.utils

import helium314.keyboard.latin.utils.ScriptUtils.script
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class ScriptUtilsTest {

    @Test
    fun testScriptSupportsUppercase() {
        assertTrue(ScriptUtils.scriptSupportsUppercase(Locale("en"))) // Latin
        assertTrue(ScriptUtils.scriptSupportsUppercase(Locale("ru"))) // Cyrillic
        assertTrue(ScriptUtils.scriptSupportsUppercase(Locale("el"))) // Greek
        assertTrue(ScriptUtils.scriptSupportsUppercase(Locale("hy"))) // Armenian

        assertFalse(ScriptUtils.scriptSupportsUppercase(Locale("ar"))) // Arabic
        assertFalse(ScriptUtils.scriptSupportsUppercase(Locale("th"))) // Thai
        assertFalse(ScriptUtils.scriptSupportsUppercase(Locale("ko"))) // Hangul
    }

    @Test
    fun testIsLetterPartOfScript() {
        // Latin
        assertTrue(ScriptUtils.isLetterPartOfScript('a'.code, ScriptUtils.SCRIPT_LATIN))
        assertTrue(ScriptUtils.isLetterPartOfScript('Z'.code, ScriptUtils.SCRIPT_LATIN))
        assertTrue(ScriptUtils.isLetterPartOfScript(0x00E9, ScriptUtils.SCRIPT_LATIN)) // é
        assertFalse(ScriptUtils.isLetterPartOfScript('1'.code, ScriptUtils.SCRIPT_LATIN))
        assertFalse(ScriptUtils.isLetterPartOfScript(0x03B1, ScriptUtils.SCRIPT_LATIN)) // Greek alpha

        // Arabic
        assertTrue(ScriptUtils.isLetterPartOfScript(0x0627, ScriptUtils.SCRIPT_ARABIC)) // Alif
        assertFalse(ScriptUtils.isLetterPartOfScript('a'.code, ScriptUtils.SCRIPT_ARABIC))

        // Cyrillic
        assertTrue(ScriptUtils.isLetterPartOfScript(0x0410, ScriptUtils.SCRIPT_CYRILLIC)) // Cyrillic Capital A
        assertFalse(ScriptUtils.isLetterPartOfScript('A'.code, ScriptUtils.SCRIPT_CYRILLIC))

        // Hangul
        assertTrue(ScriptUtils.isLetterPartOfScript(0xAC00, ScriptUtils.SCRIPT_HANGUL)) // Ga

        // Unknown
        assertTrue(ScriptUtils.isLetterPartOfScript('x'.code, ScriptUtils.SCRIPT_UNKNOWN))
        assertTrue(ScriptUtils.isLetterPartOfScript(0x1F600, ScriptUtils.SCRIPT_UNKNOWN)) // Emoji

        // Invalid script
        try {
            ScriptUtils.isLetterPartOfScript('a'.code, "InvalidScript")
            fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals("Unknown value of script: InvalidScript", e.message)
        }
    }

    @Test
    fun testLocaleScriptExtension() {
        assertEquals(ScriptUtils.SCRIPT_ARABIC, Locale("ar").script())
        assertEquals(ScriptUtils.SCRIPT_CYRILLIC, Locale("ru").script())
        assertEquals(ScriptUtils.SCRIPT_HANGUL, Locale("ko").script())
        assertEquals(ScriptUtils.SCRIPT_DEVANAGARI, Locale("hi").script())

        // Fallback
        assertEquals(ScriptUtils.SCRIPT_LATIN, Locale("en").script())
        assertEquals(ScriptUtils.SCRIPT_LATIN, Locale("ja").script()) // Unmapped defaults to Latin

        // ZZ fallback
        assertEquals(ScriptUtils.SCRIPT_LATIN, Locale("en", "ZZ").script())
    }

    @Test
    fun testNeedsWordSegmentation() {
        assertTrue(ScriptUtils.needsWordSegmentation(Locale("th")))
        assertFalse(ScriptUtils.needsWordSegmentation(Locale("en")))
        assertFalse(ScriptUtils.needsWordSegmentation(Locale("ar")))
    }

    @Test
    fun testIsScriptRtl() {
        assertTrue(ScriptUtils.isScriptRtl(ScriptUtils.SCRIPT_ARABIC))
        assertTrue(ScriptUtils.isScriptRtl(ScriptUtils.SCRIPT_HEBREW))

        assertFalse(ScriptUtils.isScriptRtl(ScriptUtils.SCRIPT_LATIN))
        assertFalse(ScriptUtils.isScriptRtl(ScriptUtils.SCRIPT_CYRILLIC))
        assertFalse(ScriptUtils.isScriptRtl(ScriptUtils.SCRIPT_THAI))
    }
}
