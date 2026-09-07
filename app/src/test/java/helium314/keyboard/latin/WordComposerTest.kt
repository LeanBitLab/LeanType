package helium314.keyboard.latin

import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.CoordinateUtils
import helium314.keyboard.latin.define.DebugFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WordComposerTest {

    @Test
    @Throws(Exception::class)
    fun testSetCursorPositionWithinWord() {
        val wordComposer = WordComposer()

        // Initial state
        val cursorPositionField = WordComposer::class.java.getDeclaredField("mCursorPositionWithinWord")
        cursorPositionField.isAccessible = true
        assertEquals(0, cursorPositionField.getInt(wordComposer))

        // Set to a new value
        wordComposer.setCursorPositionWithinWord(5)

        // Verify state is updated via reflection
        assertEquals(5, cursorPositionField.getInt(wordComposer))

        // Test behavioral effects
        wordComposer.reset()

        // Create a composing word of size 3
        val codePoints = intArrayOf('a'.code, 'b'.code, 'c'.code)
        val coordinates = CoordinateUtils.newCoordinateArray(3, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE)
        wordComposer.setComposingWord(codePoints, coordinates)

        assertTrue(wordComposer.isComposingWord())
        assertEquals(3, wordComposer.size())

        // Set cursor to front (0)
        wordComposer.setCursorPositionWithinWord(0)
        assertTrue(wordComposer.isCursorInFrontOfComposingWord())
        assertTrue(wordComposer.isCursorFrontOrMiddleOfComposingWord())

        // Set cursor to middle (1)
        wordComposer.setCursorPositionWithinWord(1)
        assertFalse(wordComposer.isCursorInFrontOfComposingWord())
        assertTrue(wordComposer.isCursorFrontOrMiddleOfComposingWord())

        // Set cursor to end (3)
        wordComposer.setCursorPositionWithinWord(3)
        assertFalse(wordComposer.isCursorInFrontOfComposingWord())
        assertFalse(wordComposer.isCursorFrontOrMiddleOfComposingWord())

        // Test error condition for invalid cursor position
        val originalDebugState = DebugFlags.DEBUG_ENABLED
        try {
            DebugFlags.DEBUG_ENABLED = true
            // Set an out-of-bounds cursor position (4 > size 3)
            wordComposer.setCursorPositionWithinWord(4)
            try {
                wordComposer.isCursorFrontOrMiddleOfComposingWord()
                fail("Should throw RuntimeException for invalid cursor position when DEBUG_ENABLED is true")
            } catch (e: RuntimeException) {
                // Expected exception
                assertTrue(e.message!!.contains("Wrong cursor position"))
            }
        } finally {
            DebugFlags.DEBUG_ENABLED = originalDebugState
        }
    }
}
