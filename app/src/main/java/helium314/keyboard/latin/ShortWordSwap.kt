// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import java.util.Locale

/**
 * Exception to LeanType's rule that unknown words of up to 3 letters are not auto-corrected (see [Suggest]):
 * two neighboring letters typed in the wrong order, like "dsa" for "das", "ihc" for "ich" or "teh" for "the",
 * are corrected. Abbreviations like "db" or "yt" stay, their swapped form is no word.
 */
object ShortWordSwap {
    const val LENGTH = 3

    /** Whether [word] is [typed] with two neighboring (different) letters swapped, case is ignored */
    fun isSwapOfNeighbors(typed: String, word: String): Boolean {
        if (typed.length != LENGTH || word.length != LENGTH) return false
        val a = typed.lowercase(Locale.ROOT)
        val b = word.lowercase(Locale.ROOT)
        val i = a.indices.firstOrNull { a[it] != b[it] } ?: return false
        if (i + 1 >= a.length) return false
        return a[i] == b[i + 1] && a[i + 1] == b[i] && a.substring(i + 2) == b.substring(i + 2)
            && a.all { it.isLetter() }
    }
}
