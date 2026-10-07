package helium314.keyboard.latin.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordTokenizerTest {

    @Test
    fun normalizeForLookup_emptyOrSingleChar() {
        assertEquals("", WordTokenizer.normalizeForLookup(""))
        assertEquals("a", WordTokenizer.normalizeForLookup("a"))
        assertEquals(".", WordTokenizer.normalizeForLookup("."))
    }

    @Test
    fun normalizeForLookup_noPunctuation() {
        assertEquals("hello", WordTokenizer.normalizeForLookup("hello"))
        assertEquals("well-known", WordTokenizer.normalizeForLookup("well-known"))
        assertEquals("don't", WordTokenizer.normalizeForLookup("don't"))
    }

    @Test
    fun normalizeForLookup_leadingPunctuation() {
        assertEquals("hello", WordTokenizer.normalizeForLookup("(hello"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("[hello"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("{hello"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("\"hello"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("'hello"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("«hello"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("¿hello"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("¡hello"))
    }

    @Test
    fun normalizeForLookup_trailingPunctuation() {
        assertEquals("hello", WordTokenizer.normalizeForLookup("hello."))
        assertEquals("hello", WordTokenizer.normalizeForLookup("hello,"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("hello!"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("hello?"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("hello:"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("hello;"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("hello)"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("hello]"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("hello}"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("hello\""))
        assertEquals("hello", WordTokenizer.normalizeForLookup("hello»"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("hello…"))
    }

    @Test
    fun normalizeForLookup_bothPunctuation() {
        assertEquals("hello", WordTokenizer.normalizeForLookup("\"hello\""))
        assertEquals("hello", WordTokenizer.normalizeForLookup("(hello)"))
        assertEquals("hello'", WordTokenizer.normalizeForLookup("'hello'"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("¿hello?"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("¡hello!"))
        assertEquals("hello", WordTokenizer.normalizeForLookup("«hello»"))
    }

    @Test
    fun normalizeForLookup_urlsAndEmails() {
        assertEquals("user@example.com", WordTokenizer.normalizeForLookup("user@example.com"))
        assertEquals("www.example.com", WordTokenizer.normalizeForLookup("www.example.com"))
        // Note: the current logic doesn't strip punctuation if it contains @ or >1 dot.
        assertEquals("(user@example.com)", WordTokenizer.normalizeForLookup("(user@example.com)"))
    }

    @Test
    fun normalizeForLookup_allPunctuation() {
        assertEquals("()", WordTokenizer.normalizeForLookup("()"))
        assertEquals("...", WordTokenizer.normalizeForLookup("...")) // Note: contains >1 dot, returned as is
        assertEquals("!", WordTokenizer.normalizeForLookup("!"))
    }

    @Test
    fun isContraction_valid() {
        assertTrue(WordTokenizer.isContraction("don't"))
        assertTrue(WordTokenizer.isContraction("I'm"))
        assertTrue(WordTokenizer.isContraction("they're"))
    }

    @Test
    fun isContraction_invalid() {
        assertFalse(WordTokenizer.isContraction(""))
        assertFalse(WordTokenizer.isContraction("a"))
        assertFalse(WordTokenizer.isContraction("it"))
        assertFalse(WordTokenizer.isContraction("'t"))
        assertFalse(WordTokenizer.isContraction("dont"))
        assertFalse(WordTokenizer.isContraction("'hello"))
        assertFalse(WordTokenizer.isContraction("hello'"))
        assertFalse(WordTokenizer.isContraction("1'2"))
        assertFalse(WordTokenizer.isContraction("a'2"))
        assertFalse(WordTokenizer.isContraction("1'a"))
    }

    @Test
    fun isContraction_surrogatePairs() {
        // Deseret capital letter YEE (\uD801\uDC37) + 's
        val word = "\uD801\uDC37's"
        // Character.isLetter on single char fails for surrogate pairs.
        // It checks word[apostropheIdx - 1], which is \uDC37 (low surrogate) and not a letter on its own.
        assertFalse(WordTokenizer.isContraction(word))
    }

    @Test
    fun splitContraction_valid() {
        assertEquals("don" to "t", WordTokenizer.splitContraction("don't"))
        assertEquals("they" to "re", WordTokenizer.splitContraction("they're"))
        assertEquals("I" to "m", WordTokenizer.splitContraction("I'm"))
    }

    @Test
    fun splitContraction_invalid() {
        assertNull(WordTokenizer.splitContraction("dont"))
        assertNull(WordTokenizer.splitContraction("'hello"))
        assertNull(WordTokenizer.splitContraction("hello'"))
        assertNull(WordTokenizer.splitContraction(""))
        assertNull(WordTokenizer.splitContraction("1'2"))
        val surrogateWord = "\uD801\uDC37's"
        assertNull(WordTokenizer.splitContraction(surrogateWord))
    }

    @Test
    fun normalizeForHistory_tests() {
        assertEquals("hello", WordTokenizer.normalizeForHistory("Hello"))
        assertEquals("hello", WordTokenizer.normalizeForHistory("(HELLO)"))
        assertEquals("don't", WordTokenizer.normalizeForHistory("DON'T"))
        assertEquals("user@example.com", WordTokenizer.normalizeForHistory("User@Example.Com"))
        assertEquals("well-known", WordTokenizer.normalizeForHistory("Well-Known"))
    }
}
