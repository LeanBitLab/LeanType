// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import org.junit.Test
import kotlin.test.assertEquals

class WordDeleteSwipeTest {

    @Test
    fun `empty text returns single boundary at offset`() {
        val boundaries = KeyboardActionListenerImpl.getWordBoundariesBackwards("", 0)
        assertEquals(listOf(0), boundaries)
    }

    @Test
    fun `single word boundaries`() {
        val text = "hello"
        val boundaries = KeyboardActionListenerImpl.getWordBoundariesBackwards(text, text.length)
        assertEquals(listOf(5, 0), boundaries)
        assertEquals("hello", text.substring(boundaries[1], boundaries[0]))
    }

    @Test
    fun `multiple words selection progression`() {
        val text = "one two three"
        val boundaries = KeyboardActionListenerImpl.getWordBoundariesBackwards(text, text.length)
        assertEquals(listOf(13, 7, 3, 0), boundaries)
        assertEquals(" three", text.substring(boundaries[1], boundaries[0]))
        assertEquals(" two three", text.substring(boundaries[2], boundaries[0]))
        assertEquals("one two three", text.substring(boundaries[3], boundaries[0]))
    }

    @Test
    fun `words with punctuation attached`() {
        val text = "Hello, world!"
        val boundaries = KeyboardActionListenerImpl.getWordBoundariesBackwards(text, text.length)
        assertEquals(listOf(13, 6, 0), boundaries)
        assertEquals(" world!", text.substring(boundaries[1], boundaries[0]))
        assertEquals("Hello, world!", text.substring(boundaries[2], boundaries[0]))
    }

    @Test
    fun `trailing spaces before cursor`() {
        val text = "first second  "
        val boundaries = KeyboardActionListenerImpl.getWordBoundariesBackwards(text, text.length)
        assertEquals(listOf(14, 5, 0), boundaries)
        assertEquals(" second  ", text.substring(boundaries[1], boundaries[0]))
        assertEquals("first second  ", text.substring(boundaries[2], boundaries[0]))
    }

    @Test
    fun `handles absolute offset correctly`() {
        val text = "hello world"
        val endOffset = 100
        val boundaries = KeyboardActionListenerImpl.getWordBoundariesBackwards(text, endOffset)
        // text length is 11, offset is 100 - 11 = 89
        assertEquals(listOf(100, 94, 89), boundaries)
    }

    @Test
    fun `empty text line boundaries returns single boundary at offset`() {
        val boundaries = KeyboardActionListenerImpl.getLineBoundariesBackwards("", 0)
        assertEquals(listOf(0), boundaries)
    }

    @Test
    fun `single line without newline`() {
        val text = "hello world"
        val boundaries = KeyboardActionListenerImpl.getLineBoundariesBackwards(text, text.length)
        assertEquals(listOf(11, 0), boundaries)
    }

    @Test
    fun `multiple lines selection progression with newlines`() {
        val text = "first line\nsecond line\nthird line"
        val boundaries = KeyboardActionListenerImpl.getLineBoundariesBackwards(text, text.length)
        // text length: "first line\n" (11) + "second line\n" (12) + "third line" (10) = 33
        // Line 3 start: index 23
        // Line 2 start: index 11
        // Line 1 start: index 0
        assertEquals(listOf(33, 23, 11, 0), boundaries)
        assertEquals("third line", text.substring(boundaries[1], boundaries[0]))
        assertEquals("second line\nthird line", text.substring(boundaries[2], boundaries[0]))
        assertEquals("first line\nsecond line\nthird line", text.substring(boundaries[3], boundaries[0]))
    }

    @Test
    fun `handles absolute offset with lines correctly`() {
        val text = "line 1\nline 2"
        val endOffset = 50
        val boundaries = KeyboardActionListenerImpl.getLineBoundariesBackwards(text, endOffset)
        // text length is 13: "line 1\n" (7) + "line 2" (6)
        // base offset is 50 - 13 = 37
        // line 2 start is 37 + 7 = 44
        // line 1 start is 37
        assertEquals(listOf(50, 44, 37), boundaries)
    }

    @Test
    fun `diagonal swipe word boundaries before line start`() {
        val initialText = "first line\nsecond line\nthird line"
        val endOffset = initialText.length // 33
        val lineBoundaries = KeyboardActionListenerImpl.getLineBoundariesBackwards(initialText, endOffset)
        assertEquals(listOf(33, 23, 11, 0), lineBoundaries)

        // At line index 1 (start of line 3, offset 23), swiping left selects words in line 2
        val lineBaseStart = lineBoundaries[1] // 23
        val textStartOffset = endOffset - initialText.length // 0
        val charCountBeforeLine = (lineBaseStart - textStartOffset).coerceIn(0, initialText.length)
        val textBeforeLine = initialText.substring(0, charCountBeforeLine)
        val wordsBeforeLine = KeyboardActionListenerImpl.getWordBoundariesBackwards(textBeforeLine, lineBaseStart)

        // Text before line 3 is "first line\nsecond line\n" (length 23)
        // Word boundaries backwards (including delimiter):
        // 23, 17 (" line\n"), 10 ("second"), 5 (" line\n"), 0 ("first")
        assertEquals(listOf(23, 17, 10, 5, 0), wordsBeforeLine)
        assertEquals(23, wordsBeforeLine[0])
        assertEquals(17, wordsBeforeLine[1])
        assertEquals(" line\n", initialText.substring(wordsBeforeLine[1], lineBaseStart))
        assertEquals("\nsecond line\n", initialText.substring(wordsBeforeLine[2], lineBaseStart))
    }

    @Test
    fun `forward word boundaries are ascending and end at the swipe start`() {
        val text = "one two three"
        // from the start of the text: positions between words, nearest first, ending at the cursor
        assertEquals(listOf(3, 7, 13), KeyboardActionListenerImpl.getWordBoundariesForwards(text, text.length, 0))
        // from the middle of the text only boundaries after that point remain
        assertEquals(listOf(7, 13), KeyboardActionListenerImpl.getWordBoundariesForwards(text, text.length, 3))
    }

    @Test
    fun `forward word boundaries are empty when there is nothing to deselect`() {
        val text = "one two three"
        assertEquals(emptyList(), KeyboardActionListenerImpl.getWordBoundariesForwards(text, text.length, text.length))
        assertEquals(emptyList(), KeyboardActionListenerImpl.getWordBoundariesForwards("", 0, 0))
    }

    @Test
    fun `forward word boundaries match backward boundaries`() {
        // selecting by swiping left and deselecting by swiping right step over the same positions
        val text = "first line\nsecond line\nthird line"
        val end = text.length // 33
        val lineBase = KeyboardActionListenerImpl.getLineBoundariesBackwards(text, end)[1] // 23
        assertEquals(23, lineBase)
        val forward = KeyboardActionListenerImpl.getWordBoundariesForwards(text, end, lineBase)
        assertEquals(listOf(28, 33), forward)
        assertEquals("third", text.substring(lineBase, forward[0]).trim())
        assertEquals("third line", text.substring(lineBase, forward[1]))
    }

    @Test
    fun `forward word boundaries respect text offset`() {
        // text before the cursor does not start at offset 0 of the field
        val text = "alpha beta gamma"
        val endOffset = 100
        val startOffset = endOffset - text.length // 84
        assertEquals(
            listOf(startOffset + 5, startOffset + 10, endOffset),
            KeyboardActionListenerImpl.getWordBoundariesForwards(text, endOffset, startOffset)
        )
    }
}
