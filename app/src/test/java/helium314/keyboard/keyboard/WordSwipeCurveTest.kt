// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordSwipeCurveTest {

    private fun count(distance: Int, current: Int = 0) = WordSwipeCurve.wordCount(distance.toFloat(), current)

    // moves the finger in 1 dp steps and returns the word count after each move
    private fun drag(from: Int, to: Int, start: Int): List<Int> {
        var words = start
        return (if (from <= to) from..to else from downTo to).map { words = count(it, words); words }
    }

    @Test
    fun `boundaries get closer together down to the minimum step`() {
        assertEquals(listOf(24, 80, 124, 160, 190, 215, 235, 255), (1..8).map { WordSwipeCurve.boundary(it) })
    }

    @Test
    fun `a short flick selects one word`() {
        assertEquals(0, count(23))
        assertEquals(1, count(24))
        assertEquals(1, count(79))
        assertEquals(2, count(80))
    }

    @Test
    fun `long swipes still select many words`() {
        assertEquals(2, count(96))
        assertEquals(3, count(144))
        assertEquals(7, count(240))
        assertEquals(13, count(360))
    }

    @Test
    fun `moving back a little past a boundary keeps the word, more deselects it`() {
        assertEquals(2, count(80 - WordSwipeCurve.BACK_SLACK, current = 2))
        assertEquals(1, count(80 - WordSwipeCurve.BACK_SLACK - 1, current = 2))
        assertEquals(1, count(24 - WordSwipeCurve.BACK_SLACK, current = 1))
        assertEquals(0, count(24 - WordSwipeCurve.BACK_SLACK - 1, current = 1))
    }

    @Test
    fun `jitter at a boundary does not flicker`() {
        var words = count(80)
        for (distance in listOf(79, 81, 77, 80, 72, 83)) {
            words = count(distance, words)
            assertEquals(2, words, "at $distance")
        }
    }

    @Test
    fun `dragging out and back changes one word at a time and ends at nothing selected`() {
        val out = drag(0, 400, 0)
        val back = drag(400, 0, out.last())
        for (counts in listOf(out, back))
            assertTrue(counts.zipWithNext().all { (a, b) -> kotlin.math.abs(a - b) <= 1 })
        assertEquals(0, back.last())
    }

    @Test
    fun `one large move counts every word on the way`() {
        assertEquals(5, count(190))
        assertEquals(0, count(0, current = 5))
        assertEquals(-2, count(-50, current = 3))
        assertEquals(3, count(130, current = -2))
    }

    @Test
    fun `right of the start the steps stay even`() {
        assertEquals(0, count(-23))
        assertEquals(-1, count(-24))
        assertEquals(-3, count(-72))
        assertEquals(-1, count(-13, current = -1))
        assertEquals(0, count(-11, current = -1))
    }
}
