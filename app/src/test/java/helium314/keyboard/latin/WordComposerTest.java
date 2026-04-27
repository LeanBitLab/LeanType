package helium314.keyboard.latin;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.common.CoordinateUtils;
import helium314.keyboard.latin.define.DebugFlags;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
public class WordComposerTest {

    @Test
    public void testSetCursorPositionWithinWord() throws Exception {
        final WordComposer wordComposer = new WordComposer();

        // Initial state
        Field cursorPositionField = WordComposer.class.getDeclaredField("mCursorPositionWithinWord");
        cursorPositionField.setAccessible(true);
        assertEquals(0, cursorPositionField.getInt(wordComposer));

        // Set to a new value
        wordComposer.setCursorPositionWithinWord(5);

        // Verify state is updated via reflection
        assertEquals(5, cursorPositionField.getInt(wordComposer));

        // Test behavioral effects
        wordComposer.reset();

        // Create a composing word of size 3
        int[] codePoints = new int[] { 'a', 'b', 'c' };
        int[] coordinates = CoordinateUtils.newCoordinateArray(3, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE);
        wordComposer.setComposingWord(codePoints, coordinates);

        assertTrue(wordComposer.isComposingWord());
        assertEquals(3, wordComposer.size());

        // Set cursor to front (0)
        wordComposer.setCursorPositionWithinWord(0);
        assertTrue(wordComposer.isCursorInFrontOfComposingWord());
        assertTrue(wordComposer.isCursorFrontOrMiddleOfComposingWord());

        // Set cursor to middle (1)
        wordComposer.setCursorPositionWithinWord(1);
        assertFalse(wordComposer.isCursorInFrontOfComposingWord());
        assertTrue(wordComposer.isCursorFrontOrMiddleOfComposingWord());

        // Set cursor to end (3)
        wordComposer.setCursorPositionWithinWord(3);
        assertFalse(wordComposer.isCursorInFrontOfComposingWord());
        assertFalse(wordComposer.isCursorFrontOrMiddleOfComposingWord());

        // Test error condition for invalid cursor position
        boolean originalDebugState = DebugFlags.DEBUG_ENABLED;
        try {
            DebugFlags.DEBUG_ENABLED = true;
            // Set an out-of-bounds cursor position (4 > size 3)
            wordComposer.setCursorPositionWithinWord(4);
            try {
                wordComposer.isCursorFrontOrMiddleOfComposingWord();
                fail("Should throw RuntimeException for invalid cursor position when DEBUG_ENABLED is true");
            } catch (RuntimeException e) {
                // Expected exception
                assertTrue(e.getMessage().contains("Wrong cursor position"));
            }
        } finally {
            DebugFlags.DEBUG_ENABLED = originalDebugState;
        }
    }
}
