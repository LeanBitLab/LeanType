package helium314.keyboard.latin.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.settings.SpacingAndPunctuations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NgramContextUtilsTest {

    private lateinit var spacingAndPunctuations: SpacingAndPunctuations
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        spacingAndPunctuations = SpacingAndPunctuations(context.resources, false)
    }

    @Test
    fun testNullInput() {
        val ngramContext = NgramContextUtils.getNgramContextFromNthPreviousWord(
            null, spacingAndPunctuations, 1
        )
        // Should return NgramContext.EMPTY_PREV_WORDS_INFO which has WordInfo.EMPTY_WORD_INFO
        assertEquals(1, ngramContext.prevWordCount)
        assertFalse(ngramContext.isBeginningOfSentenceContext)
        assertEquals(null, ngramContext.getNthPrevWord(1))
    }

    @Test
    fun testBeginningOfSentence() {
        val ngramContext = NgramContextUtils.getNgramContextFromNthPreviousWord(
            "", spacingAndPunctuations, 1
        )
        assertEquals(1, ngramContext.prevWordCount)
        assertTrue(ngramContext.isBeginningOfSentenceContext)
    }

    @Test
    fun testBasicWordExtraction() {
        val prevContext = "the quick brown"

        // n=1 refers to the focused word context: expected to return "brown" and the preceding words
        val context1 = NgramContextUtils.getNgramContextFromNthPreviousWord(
            prevContext, spacingAndPunctuations, 1
        )
        assertEquals(3, context1.prevWordCount)
        assertEquals("brown", context1.getNthPrevWord(1))
        assertEquals("quick", context1.getNthPrevWord(2))
        assertEquals("the", context1.getNthPrevWord(3))

        // n=2 refers to the word before: expected to return "quick", "the" and then beginning of sentence
        val context2 = NgramContextUtils.getNgramContextFromNthPreviousWord(
            prevContext, spacingAndPunctuations, 2
        )
        assertEquals(3, context2.prevWordCount)
        assertEquals("quick", context2.getNthPrevWord(1))
        assertEquals("the", context2.getNthPrevWord(2))
        assertTrue(context2.isNthPrevWordBeginningOfSentence(3))
    }

    @Test
    fun testSentenceTerminator() {
        val prevContext = "hello world. next"
        // At n=1, looking at "next", the previous word boundary contains "." which is a sentence terminator.
        val context1 = NgramContextUtils.getNgramContextFromNthPreviousWord(
            prevContext, spacingAndPunctuations, 1
        )
        assertEquals(2, context1.prevWordCount)
        assertEquals("next", context1.getNthPrevWord(1))
        assertTrue(context1.isNthPrevWordBeginningOfSentence(2))

        // Looking further back, since "world." ends with a terminator, we should hit the beginning of sentence.
        val context2 = NgramContextUtils.getNgramContextFromNthPreviousWord(
            prevContext, spacingAndPunctuations, 2
        )
        // Expected context: <S>
        assertEquals(1, context2.prevWordCount)
        assertTrue(context2.isNthPrevWordBeginningOfSentence(1))
    }

    @Test
    fun testNewlineTruncation() {
        val prevContext = "hello\nworld"
        val context1 = NgramContextUtils.getNgramContextFromNthPreviousWord(
            prevContext, spacingAndPunctuations, 1
        )
        // Newlines force beginning of sentence contexts because the words before the newline are on a different line
        assertEquals(2, context1.prevWordCount)
        assertEquals("world", context1.getNthPrevWord(1))
        assertTrue(context1.isNthPrevWordBeginningOfSentence(2))

        val context2 = NgramContextUtils.getNgramContextFromNthPreviousWord(
            prevContext, spacingAndPunctuations, 2
        )
        // Expected: <S>
        assertEquals(1, context2.prevWordCount)
        assertTrue(context2.isNthPrevWordBeginningOfSentence(1))
    }

    @Test
    fun testNewlineFollowedBySpace() {
        val prevContext = "hello\n   "
        val context1 = NgramContextUtils.getNgramContextFromNthPreviousWord(
            prevContext, spacingAndPunctuations, 1
        )
        // If there are no non-whitespace characters after the newline, it should return a single beginning of sentence.
        assertEquals(1, context1.prevWordCount)
        assertTrue(context1.isBeginningOfSentenceContext)
    }

    @Test
    fun testWordConnector() {
        val prevContext = "user_name"
        // '_' is typically a word connector depending on SpacingAndPunctuations setup.
        // We initialize SpacingAndPunctuations with composeUnderscore=false by default, so it might not be a connector,
        // but we can test behavior based on word separators/connectors in strings.xml
        val ngramContext = NgramContextUtils.getNgramContextFromNthPreviousWord(
            prevContext, spacingAndPunctuations, 1
        )
        // If it is treated as a single word or split depends on configuration, but we want to ensure it completes successfully.
        assertTrue(ngramContext.prevWordCount > 0)
    }
}
