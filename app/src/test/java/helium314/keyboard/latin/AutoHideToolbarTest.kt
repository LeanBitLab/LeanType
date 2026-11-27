// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoHideToolbarTest {
    @Test fun `typed word suggestions hide the toolbar`() {
        assertTrue(shouldAutoHideToolbar(true, false, true, SuggestedWords.INPUT_STYLE_TYPING))
    }

    @Test fun `suggestions of a finished swipe hide the toolbar before the word is composing`() {
        // #870: the swipe's suggestions are set before its word is inserted
        assertTrue(shouldAutoHideToolbar(true, false, false, SuggestedWords.INPUT_STYLE_TAIL_BATCH))
    }

    @Test fun `suggestions while still swiping keep the toolbar`() {
        assertFalse(shouldAutoHideToolbar(true, false, false, SuggestedWords.INPUT_STYLE_UPDATE_BATCH))
    }

    @Test fun `predictions keep the toolbar`() {
        assertFalse(shouldAutoHideToolbar(true, false, false, SuggestedWords.INPUT_STYLE_PREDICTION))
    }

    @Test fun `nothing is hidden without the setting or without suggestions`() {
        assertFalse(shouldAutoHideToolbar(false, false, true, SuggestedWords.INPUT_STYLE_TAIL_BATCH))
        assertFalse(shouldAutoHideToolbar(true, true, false, SuggestedWords.INPUT_STYLE_TAIL_BATCH))
    }
}
