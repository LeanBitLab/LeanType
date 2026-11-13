// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortWordSwapTest {
    @Test
    fun swappedNeighborsAreFound() {
        for ((typed, word) in listOf("dsa" to "das", "ihc" to "ich", "dei" to "die", "nud" to "und", "teh" to "the",
                "adn" to "and", "Dsa" to "Das", "IHC" to "ich", "eni" to "ein")) {
            assertTrue("$typed -> $word", ShortWordSwap.isSwapOfNeighbors(typed, word))
        }
    }

    @Test
    fun otherDifferencesAreNoSwap() {
        assertFalse(ShortWordSwap.isSwapOfNeighbors("das", "das")) // same word
        assertFalse(ShortWordSwap.isSwapOfNeighbors("dsa", "sad")) // not neighbors
        assertFalse(ShortWordSwap.isSwapOfNeighbors("das", "dar")) // a different letter
        assertFalse(ShortWordSwap.isSwapOfNeighbors("db", "bd")) // only 3 letters
        assertFalse(ShortWordSwap.isSwapOfNeighbors("ders", "dres")) // only 3 letters
        assertFalse(ShortWordSwap.isSwapOfNeighbors("a1b", "1ab")) // only letters
        assertFalse(ShortWordSwap.isSwapOfNeighbors("abc", "bca"))
    }
}
