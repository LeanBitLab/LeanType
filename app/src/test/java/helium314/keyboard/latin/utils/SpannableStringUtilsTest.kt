package helium314.keyboard.latin.utils

import android.text.SpannableString
import android.text.Spanned
import android.text.style.SuggestionSpan
import android.text.style.URLSpan
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SpannableStringUtilsTest {

    @Test
    fun testConcatWithNonParagraphSuggestionSpansOnly_empty() {
        val result = SpannableStringUtils.concatWithNonParagraphSuggestionSpansOnly()
        assertEquals("", result)
    }

    @Test
    fun testConcatWithNonParagraphSuggestionSpansOnly_singleString() {
        val result = SpannableStringUtils.concatWithNonParagraphSuggestionSpansOnly("hello")
        assertEquals("hello", result)
    }

    @Test
    fun testConcatWithNonParagraphSuggestionSpansOnly_multipleStrings() {
        val result = SpannableStringUtils.concatWithNonParagraphSuggestionSpansOnly("hello", " ", "world")
        assertEquals("hello world", result)
    }

    @Test
    fun testConcatWithNonParagraphSuggestionSpansOnly_withSpans() {
        val spanned1 = SpannableString("hello ")
        val span1 = SuggestionSpan(Locale.ENGLISH, arrayOf("hi"), 0)
        spanned1.setSpan(span1, 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Add SPAN_PARAGRAPH flag to verify it gets stripped
        val spanned2 = SpannableString("world")
        val span2 = SuggestionSpan(Locale.ENGLISH, arrayOf("earth"), 0)
        spanned2.setSpan(span2, 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_PARAGRAPH)

        val result = SpannableStringUtils.concatWithNonParagraphSuggestionSpansOnly(spanned1, spanned2)
        assertEquals("hello world", result.toString())
        assertTrue(result is Spanned)

        val spannedResult = result as Spanned
        val spans = spannedResult.getSpans(0, result.length, SuggestionSpan::class.java)
        assertEquals(2, spans.size)

        // Check first span
        assertEquals(0, spannedResult.getSpanStart(spans[0]))
        assertEquals(5, spannedResult.getSpanEnd(spans[0]))
        assertEquals(0, spannedResult.getSpanFlags(spans[0]) and Spanned.SPAN_PARAGRAPH)

        // Check second span (its offset should be adjusted, and SPAN_PARAGRAPH stripped)
        assertEquals(6, spannedResult.getSpanStart(spans[1]))
        assertEquals(11, spannedResult.getSpanEnd(spans[1]))
        // Robolectric returns 0 when Spanned.SPAN_PARAGRAPH.inv() is applied to SPAN_EXCLUSIVE_EXCLUSIVE or SPAN_PARAGRAPH sometimes.
        // Wait, SPAN_EXCLUSIVE_EXCLUSIVE is 33, SPAN_PARAGRAPH is 51.
        // 33 and 51.inv() = 33 & -52 = (33 is 00100001, -52 is 11001100).
        // 00100001 & 11001100 = 00000000 = 0
        // Wait, SPAN_EXCLUSIVE_EXCLUSIVE is actually 33. SPAN_PARAGRAPH is 51?
        // Let's just check that SPAN_PARAGRAPH flag is NOT set.
        assertEquals(0, spannedResult.getSpanFlags(spans[1]) and Spanned.SPAN_PARAGRAPH)
    }

    @Test
    fun testHasUrlSpans() {
        val text = SpannableString("Visit http://example.com today")
        val urlSpan = URLSpan("http://example.com")
        text.setSpan(urlSpan, 6, 24, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        assertTrue(SpannableStringUtils.hasUrlSpans(text, 6, 24))
        // test range intersections based on hasUrlSpans logic (startIndex - 1, endIndex + 1)
        assertTrue(SpannableStringUtils.hasUrlSpans(text, 10, 15))
        assertFalse(SpannableStringUtils.hasUrlSpans(text, 0, 4))
        assertFalse(SpannableStringUtils.hasUrlSpans(text, 26, 30))

        // Unspanned text
        assertFalse(SpannableStringUtils.hasUrlSpans("Visit http://example.com today", 6, 24))
    }

    @Test
    fun testSplit_unspanned() {
        val text = "a,b,c,"

        val splitNoTrailing = SpannableStringUtils.split(text, ",", false)
        assertArrayEquals(arrayOf<CharSequence>("a", "b", "c"), splitNoTrailing)

        val splitTrailing = SpannableStringUtils.split(text, ",", true)
        assertArrayEquals(arrayOf<CharSequence>("a", "b", "c", ""), splitTrailing)
    }

    @Test
    fun testSplit_spanned() {
        val text = SpannableString("a,b,c,")

        val splitNoTrailing = SpannableStringUtils.split(text, ",", false)
        assertEquals(3, splitNoTrailing.size)
        assertEquals("a", splitNoTrailing[0].toString())
        assertEquals("b", splitNoTrailing[1].toString())
        assertEquals("c", splitNoTrailing[2].toString())

        val splitTrailing = SpannableStringUtils.split(text, ",", true)
        assertEquals(4, splitTrailing.size)
        assertEquals("a", splitTrailing[0].toString())
        assertEquals("b", splitTrailing[1].toString())
        assertEquals("c", splitTrailing[2].toString())
        assertEquals("", splitTrailing[3].toString())

        val textNoMatch = SpannableString("abc")
        val splitNoMatch = SpannableStringUtils.split(textNoMatch, ",", false)
        assertEquals(1, splitNoMatch.size)
        assertEquals("abc", splitNoMatch[0].toString())
    }
}
