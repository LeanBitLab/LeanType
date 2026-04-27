package helium314.keyboard.latin.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SpacedTokensTest {

    @Test
    fun `empty string returns empty list`() {
        val tokens = SpacedTokens("").toList()
        assertEquals(0, tokens.size)
    }

    @Test
    fun `string with only spaces returns empty list`() {
        val tokens = SpacedTokens("   ").toList()
        assertEquals(0, tokens.size)
    }

    @Test
    fun `string with one token without spaces returns one token`() {
        val tokens = SpacedTokens("word").toList()
        assertEquals(listOf("word"), tokens)
    }

    @Test
    fun `string with multiple tokens separated by single spaces returns tokens`() {
        val tokens = SpacedTokens("this is a test").toList()
        assertEquals(listOf("this", "is", "a", "test"), tokens)
    }

    @Test
    fun `string with multiple tokens separated by multiple spaces returns tokens`() {
        val tokens = SpacedTokens("this  is   a    test").toList()
        assertEquals(listOf("this", "is", "a", "test"), tokens)
    }

    @Test
    fun `string with leading spaces returns tokens`() {
        val tokens = SpacedTokens("  leading").toList()
        assertEquals(listOf("leading"), tokens)
    }

    @Test
    fun `string with trailing spaces returns tokens`() {
        val tokens = SpacedTokens("trailing  ").toList()
        assertEquals(listOf("trailing"), tokens)
    }

    @Test
    fun `string with leading and trailing spaces returns tokens`() {
        val tokens = SpacedTokens("   both   ").toList()
        assertEquals(listOf("both"), tokens)
    }

    @Test
    fun `string with different types of whitespace returns tokens`() {
        val tokens = SpacedTokens("token1\ttoken2\ntoken3\rtoken4").toList()
        assertEquals(listOf("token1", "token2", "token3", "token4"), tokens)
    }

    @Test
    fun `string with punctuations as tokens returns tokens`() {
        val tokens = SpacedTokens("word1, word2!").toList()
        assertEquals(listOf("word1,", "word2!"), tokens)
    }

    @Test
    fun `string with emojis as tokens returns tokens`() {
        val tokens = SpacedTokens("hello 🌍!").toList()
        assertEquals(listOf("hello", "🌍!"), tokens)
    }

}
