package helium314.keyboard.latin.utils

import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.dictionary.Dictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class SuggestionResultsTest {

    private fun createWordInfo(word: String, score: Int = 100, kindAndFlags: Int = SuggestedWordInfo.KIND_TYPED): SuggestedWordInfo {
        val dummyDictionary = mock(Dictionary::class.java)
        return SuggestedWordInfo(
            word,
            "", // prevWordsContext
            score,
            kindAndFlags,
            dummyDictionary,
            SuggestedWordInfo.NOT_AN_INDEX,
            SuggestedWordInfo.NOT_A_CONFIDENCE
        )
    }

    @Test
    fun testCapacityEnforcement() {
        val results = SuggestionResults(3, false, false)
        results.add(createWordInfo("a", 100))
        results.add(createWordInfo("b", 90))
        results.add(createWordInfo("c", 80))

        assertEquals(3, results.size)

        // This should push out "c" because 95 is higher than 80
        val added = results.add(createWordInfo("d", 95))
        assertTrue(added)
        assertEquals(3, results.size)

        val words = results.map { it.mWord }
        assertTrue(words.contains("a"))
        assertTrue(words.contains("d"))
        assertTrue(words.contains("b"))
        assertFalse(words.contains("c"))

        // This should not be added because 70 is lower than all current elements (100, 95, 90)
        val addedLower = results.add(createWordInfo("e", 70))
        assertFalse(addedLower)
        assertEquals(3, results.size)
        assertFalse(results.map { it.mWord }.contains("e"))
    }

    @Test
    fun testComparatorLogic() {
        val results = SuggestionResults(5, false, false)
        // Same score, different lengths
        results.add(createWordInfo("longword", 100))
        results.add(createWordInfo("short", 100))

        val it = results.iterator()
        assertEquals("short", it.next().mWord)
        assertEquals("longword", it.next().mWord)

        results.clear()

        // Same score, same length, alphabetical sort
        results.add(createWordInfo("bbbbb", 100))
        results.add(createWordInfo("aaaaa", 100))

        val it2 = results.iterator()
        assertEquals("aaaaa", it2.next().mWord)
        assertEquals("bbbbb", it2.next().mWord)
    }

    @Test
    fun testCopyConstructor() {
        val original = SuggestionResults(3, true, true)
        original.add(createWordInfo("word", 100))

        val copy = original.copy()
        assertEquals(original.mIsBeginningOfSentence, copy.mIsBeginningOfSentence)
        assertEquals(original.mFirstSuggestionExceedsConfidenceThreshold, copy.mFirstSuggestionExceedsConfidenceThreshold)
        assertEquals(original.size, copy.size)
        assertEquals("word", copy.first().mWord)
    }

    @Test
    fun testAddAllNullable() {
        val results = SuggestionResults(3, false, false)
        assertFalse(results.addAll(null))
        assertEquals(0, results.size)
    }
}
