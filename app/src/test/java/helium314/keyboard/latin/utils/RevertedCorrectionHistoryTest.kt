// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RevertedCorrectionHistoryTest {

    @Before
    fun setUp() {
        RevertedCorrectionHistory.clear()
    }

    @After
    fun tearDown() {
        RevertedCorrectionHistory.clear()
    }

    @Test
    fun testRecordAndCheckRecentlyReverted() {
        assertFalse(RevertedCorrectionHistory.isRecentlyReverted("thier", "their"))

        RevertedCorrectionHistory.record("thier", "their")
        assertTrue(RevertedCorrectionHistory.isRecentlyReverted("thier", "their"))
        // Case insensitive matching
        assertTrue(RevertedCorrectionHistory.isRecentlyReverted("Thier", "Their"))

        // Different candidate or typed word
        assertFalse(RevertedCorrectionHistory.isRecentlyReverted("thier", "there"))
        assertFalse(RevertedCorrectionHistory.isRecentlyReverted("other", "their"))
    }

    @Test
    fun testRemove() {
        RevertedCorrectionHistory.record("thier", "their")
        assertTrue(RevertedCorrectionHistory.isRecentlyReverted("thier", "their"))

        RevertedCorrectionHistory.remove("thier")
        assertFalse(RevertedCorrectionHistory.isRecentlyReverted("thier", "their"))
    }

    @Test
    fun testClear() {
        RevertedCorrectionHistory.record("wrod1", "word1")
        RevertedCorrectionHistory.record("wrod2", "word2")
        assertTrue(RevertedCorrectionHistory.isRecentlyReverted("wrod1", "word1"))
        assertTrue(RevertedCorrectionHistory.isRecentlyReverted("wrod2", "word2"))

        RevertedCorrectionHistory.clear()
        assertFalse(RevertedCorrectionHistory.isRecentlyReverted("wrod1", "word1"))
        assertFalse(RevertedCorrectionHistory.isRecentlyReverted("wrod2", "word2"))
    }
}
