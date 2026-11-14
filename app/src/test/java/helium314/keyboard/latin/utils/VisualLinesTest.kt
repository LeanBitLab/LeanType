// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import org.junit.Test
import kotlin.test.assertEquals

class VisualLinesTest {
    // one entry per char, lines of the given lengths, 10px high, no linefeed flags
    private fun geometry(vararg lineLengths: Int): Triple<FloatArray, FloatArray, BooleanArray> {
        val tops = mutableListOf<Float>()
        lineLengths.forEachIndexed { line, len -> repeat(len) { tops.add(line * 10f) } }
        val t = tops.toFloatArray()
        return Triple(t, FloatArray(t.size) { t[it] + 10f }, BooleanArray(t.size))
    }

    @Test
    fun `soft wrapped lines are found by vertical position`() {
        val (t, b, f) = geometry(5, 3, 4)
        assertEquals(listOf(100, 105, 108), VisualLines.lineStarts(100, t, b, f))
    }

    @Test
    fun `line feed starts a new line`() {
        val t = floatArrayOf(0f, 0f, 0f, 10f, 10f)
        val b = floatArrayOf(10f, 10f, 10f, 20f, 20f)
        val f = booleanArrayOf(false, false, true, false, false)
        assertEquals(listOf(0, 3), VisualLines.lineStarts(0, t, b, f))
    }

    @Test
    fun `line feed flag alone is enough when geometry is identical`() {
        val t = FloatArray(4)
        val b = FloatArray(4) { 10f }
        val f = booleanArrayOf(false, true, false, false)
        assertEquals(listOf(0, 2), VisualLines.lineStarts(0, t, b, f))
    }

    @Test
    fun `empty input gives no lines`() {
        assertEquals(emptyList(), VisualLines.lineStarts(0, floatArrayOf(), floatArrayOf(), booleanArrayOf()))
    }

    // three lines of 5 chars, 10px wide chars: x of char i on its line = i * 10
    private fun geometry3(): VisualLines.LineGeometry {
        val lefts = FloatArray(15) { (it % 5) * 10f }
        val rights = FloatArray(15) { (it % 5) * 10f + 10f }
        return VisualLines.LineGeometry(100, lefts, rights, BooleanArray(15), listOf(100, 105, 110))
    }

    @Test
    fun `line index of offsets`() {
        val g = geometry3()
        assertEquals(-1, g.lineIndexOf(99))
        assertEquals(0, g.lineIndexOf(100))
        assertEquals(0, g.lineIndexOf(104))
        assertEquals(1, g.lineIndexOf(105))
        assertEquals(2, g.lineIndexOf(114))
    }

    @Test
    fun `same column one line up`() {
        val g = geometry3()
        // cursor at 113 (column 3 of the last line) -> column 3 of the line above is 108, then 103
        assertEquals(listOf(113, 108, 103), g.boundaries(113, g.xAt(113)))
    }

    @Test
    fun `column is kept on a shorter line`() {
        val lefts = floatArrayOf(0f, 10f, 0f, 10f, 20f, 30f, 40f)
        val rights = FloatArray(7) { lefts[it] + 10f }
        // first line has 2 chars (offsets 0,1), second 5 chars (2..6)
        val g = VisualLines.LineGeometry(0, lefts, rights, BooleanArray(7), listOf(0, 2))
        assertEquals(1, g.columnOffset(0, 40f))
        assertEquals(listOf(6, 1, 0), g.boundaries(6, g.xAt(6)))
    }

    @Test
    fun `cursor on the empty last line is below the last known line`() {
        // two lines of 5 chars, each ending with a line feed: the text ends with an empty line without characters
        val lefts = FloatArray(10) { (it % 5) * 10f }
        val rights = FloatArray(10) { (it % 5) * 10f + 10f }
        val lineFeed = BooleanArray(10).also { it[4] = true; it[9] = true }
        val g = VisualLines.LineGeometry(0, lefts, rights, lineFeed, listOf(0, 5))
        assertEquals(1, g.lineIndexOf(9))
        assertEquals(2, g.lineIndexOf(10))
        assertEquals(10, g.columnOffset(2, 0f))
        // the line directly above the empty line is not skipped
        assertEquals(listOf(10, 5, 0), g.boundaries(10, g.xAt(10)))
    }

    @Test
    fun `x at the end of the text uses the right edge`() {
        val g = geometry3()
        assertEquals(50f, g.xAt(115))
    }
}
