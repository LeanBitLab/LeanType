package helium314.keyboard.latin.utils

import android.text.TextUtils
import helium314.keyboard.latin.WordComposer
import helium314.keyboard.latin.settings.SpacingAndPunctuations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class CapsModeUtilsTest {

    @Test
    fun testApplyAutoCapsMode() {
        assertEquals("HELLO", CapsModeUtils.applyAutoCapsMode("hello", WordComposer.CAPS_MODE_AUTO_SHIFT_LOCKED, Locale.US))
        assertEquals("ÁÉÍ", CapsModeUtils.applyAutoCapsMode("áéí", WordComposer.CAPS_MODE_AUTO_SHIFT_LOCKED, Locale.US))

        assertEquals("Hello", CapsModeUtils.applyAutoCapsMode("hello", WordComposer.CAPS_MODE_AUTO_SHIFTED, Locale.US))
        assertEquals("Áéí", CapsModeUtils.applyAutoCapsMode("áéí", WordComposer.CAPS_MODE_AUTO_SHIFTED, Locale.US))

        assertEquals("hello", CapsModeUtils.applyAutoCapsMode("hello", WordComposer.CAPS_MODE_OFF, Locale.US))
        assertEquals("hello", CapsModeUtils.applyAutoCapsMode("hello", WordComposer.CAPS_MODE_MANUAL_SHIFTED, Locale.US))
        assertEquals("hello", CapsModeUtils.applyAutoCapsMode("hello", WordComposer.CAPS_MODE_MANUAL_SHIFT_LOCKED, Locale.US))
    }

    @Test
    fun testIsAutoCapsMode() {
        assertTrue(CapsModeUtils.isAutoCapsMode(WordComposer.CAPS_MODE_AUTO_SHIFTED))
        assertTrue(CapsModeUtils.isAutoCapsMode(WordComposer.CAPS_MODE_AUTO_SHIFT_LOCKED))

        assertFalse(CapsModeUtils.isAutoCapsMode(WordComposer.CAPS_MODE_OFF))
        assertFalse(CapsModeUtils.isAutoCapsMode(WordComposer.CAPS_MODE_MANUAL_SHIFTED))
        assertFalse(CapsModeUtils.isAutoCapsMode(WordComposer.CAPS_MODE_MANUAL_SHIFT_LOCKED))
    }

    @Test
    fun testFlagsToString() {
        assertEquals("none", CapsModeUtils.flagsToString(0))
        assertEquals("characters", CapsModeUtils.flagsToString(TextUtils.CAP_MODE_CHARACTERS))
        assertEquals("words", CapsModeUtils.flagsToString(TextUtils.CAP_MODE_WORDS))
        assertEquals("sentences", CapsModeUtils.flagsToString(TextUtils.CAP_MODE_SENTENCES))
        assertEquals("characters|words|sentences", CapsModeUtils.flagsToString(TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES))
        assertEquals("unknown<0xffff0000>", CapsModeUtils.flagsToString(0xFFFF0000.toInt()))
    }

    private fun mockSpacingAndPunctuations(
        usesAmericanTypography: Boolean = false,
        usesGermanRules: Boolean = false,
        sentenceSeparators: Set<Int> = setOf('.'.code, '?'.code, '!'.code),
        abbreviationMarkers: Set<Int> = setOf('.'.code),
        sentenceTerminators: Set<Int> = setOf('.'.code, '?'.code, '!'.code)
    ): SpacingAndPunctuations {
        val mock = mock(SpacingAndPunctuations::class.java)
        `when`(mock.mUsesAmericanTypography).thenReturn(usesAmericanTypography)
        `when`(mock.mUsesGermanRules).thenReturn(usesGermanRules)

        `when`(mock.isSentenceSeparator(org.mockito.ArgumentMatchers.anyInt())).thenAnswer { invocation ->
            val code = invocation.getArgument<Int>(0)
            sentenceSeparators.contains(code)
        }
        `when`(mock.isAbbreviationMarker(org.mockito.ArgumentMatchers.anyInt())).thenAnswer { invocation ->
            val code = invocation.getArgument<Int>(0)
            abbreviationMarkers.contains(code)
        }
        `when`(mock.isSentenceTerminator(org.mockito.ArgumentMatchers.anyInt())).thenAnswer { invocation ->
            val code = invocation.getArgument<Int>(0)
            sentenceTerminators.contains(code)
        }
        return mock
    }

    @Test
    fun testGetCapsMode_emptyString() {
        val sp = mockSpacingAndPunctuations()
        val modes = TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES

        assertEquals(modes, CapsModeUtils.getCapsMode("", modes, sp, false))
        assertEquals(modes, CapsModeUtils.getCapsMode("", modes, sp, true))
    }

    @Test
    fun testGetCapsMode_sentences() {
        val sp = mockSpacingAndPunctuations(abbreviationMarkers = setOf())
        val modes = TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES

        assertEquals(modes, CapsModeUtils.getCapsMode("Hello. ", modes, sp, false))
        assertEquals(modes, CapsModeUtils.getCapsMode("Hello.", modes, sp, true))

        assertEquals(modes, CapsModeUtils.getCapsMode("Hello\n", modes, sp, false))
        assertEquals(modes, CapsModeUtils.getCapsMode("Hello\n", modes, sp, true))

        assertEquals(TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS, CapsModeUtils.getCapsMode("Hello ", modes, sp, false))
        assertEquals(TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS, CapsModeUtils.getCapsMode("Hello", modes, sp, true))

        assertEquals(modes, CapsModeUtils.getCapsMode("Hello. \"", modes, sp, false))

        assertEquals(TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS, CapsModeUtils.getCapsMode("123 ", modes, sp, false))
    }

    @Test
    fun testGetCapsMode_americanTypography() {
        val sp = mockSpacingAndPunctuations(usesAmericanTypography = true, abbreviationMarkers = setOf())
        val modes = TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES

        assertEquals(modes, CapsModeUtils.getCapsMode("Hello.\" ", modes, sp, false))
        assertEquals(modes, CapsModeUtils.getCapsMode("Hello.\" ", modes, sp, true))
    }

    @Test
    fun testGetCapsMode_germanRules() {
        val sp = mockSpacingAndPunctuations(usesGermanRules = true, abbreviationMarkers = setOf())
        val modes = TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES

        assertEquals(TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS, CapsModeUtils.getCapsMode("Hello,\n", modes, sp, false))
        assertEquals(TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS, CapsModeUtils.getCapsMode("Hello,\n", modes, sp, true))

        assertEquals(TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS, CapsModeUtils.getCapsMode("Hello, 123 ", modes, sp, false))
    }

    @Test
    fun testGetCapsMode_abbreviationMarker() {
        val sp = mockSpacingAndPunctuations(
            sentenceSeparators = setOf('.'.code),
            abbreviationMarkers = setOf('.'.code),
            sentenceTerminators = setOf('.'.code)
        )
        val modes = TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES

        // It is an abbreviation marker, so it resolves to caps at the end of the state machine traversal
        assertEquals(modes, CapsModeUtils.getCapsMode("Dr. ", modes, sp, false))
        assertEquals(modes, CapsModeUtils.getCapsMode("Dr.", modes, sp, true))
    }
}
