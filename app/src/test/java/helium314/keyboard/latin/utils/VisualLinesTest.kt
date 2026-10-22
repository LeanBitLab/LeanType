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

    @Test
    fun `boundaries start at base and go upwards`() {
        assertEquals(listOf(60, 45, 20, 0), VisualLines.buildBoundaries(60, listOf(0, 20, 45), true, emptyList(), 0))
    }

    @Test
    fun `no duplicate when base is already a line start`() {
        assertEquals(listOf(45, 20, 0), VisualLines.buildBoundaries(45, listOf(0, 20, 45), true, emptyList(), 0))
    }

    @Test
    fun `partial first line is dropped and estimate continues`() {
        assertEquals(listOf(60, 45, 25, 0), VisualLines.buildBoundaries(60, listOf(30, 45), false, listOf(60, 45, 25, 0), 0))
    }

    @Test
    fun `entries before the known text are not used`() {
        assertEquals(listOf(60, 45, 30), VisualLines.buildBoundaries(60, listOf(0, 30, 45), true, emptyList(), 30))
    }
}
