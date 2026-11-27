// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

/**
 * Finger travel of the word-by-word delete swipe. The first words need more travel, so a short flick
 * deletes just one word, and the steps get shorter after that for selecting many words.
 * Distances are in dp from where the swipe started, positive to the left.
 */
internal object WordSwipeCurve {
    /** travel to select the 1st, 2nd, ... word */
    private val leftSteps = intArrayOf(24, 56, 44, 36, 30, 25)
    private const val MIN_STEP = 20

    /** right of the start (deselecting after a line swipe) there is little room, so the steps stay even, see #693 */
    private const val RIGHT_STEP = 24

    /** moving back this far past the position that selected a word deselects it again, less would flicker */
    const val BACK_SLACK = 12

    /** the distance that selects [words] words, negative [words] are right of the start */
    fun boundary(words: Int): Int {
        if (words <= 0) return words * RIGHT_STEP
        var distance = 0
        for (i in 0 until words) distance += leftSteps.getOrElse(i) { MIN_STEP }
        return distance
    }

    /** the number of words at [distance], when [current] words were selected before this move */
    fun wordCount(distance: Float, current: Int): Int {
        var words = current
        while (words > 0 && distance < boundary(words) - BACK_SLACK) words--
        while (words < 0 && distance > boundary(words) + BACK_SLACK) words++
        if (words >= 0) while (distance >= boundary(words + 1)) words++
        if (words <= 0) while (distance <= boundary(words - 1)) words--
        return words
    }
}
