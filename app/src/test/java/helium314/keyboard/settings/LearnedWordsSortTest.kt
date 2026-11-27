// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import helium314.keyboard.latin.LearnedWord
import helium314.keyboard.settings.screens.sortLearnedWords
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LearnedWordsSortTest {
    @Test fun `most used first, ties alphabetical ignoring case`() {
        val words = listOf(
            LearnedWord("zebra", Locale.ENGLISH, 2),
            LearnedWord("Apple", Locale.ENGLISH, 5),
            LearnedWord("banana", Locale.ENGLISH, 2),
            LearnedWord("apricot", Locale.ENGLISH, 2),
        )
        assertEquals(listOf("Apple", "apricot", "banana", "zebra"), sortLearnedWords(words).map { it.word })
    }
}
