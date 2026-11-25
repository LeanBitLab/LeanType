// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GestureTrailCurveTest {
    private val progress = GestureTrailDrawingPoints.Companion::progress
    private val smooth = GestureTrailDrawingPoints.Companion::smoothStep
    private val width = GestureTrailDrawingPoints.Companion::trailWidth

    @Test fun `progress is clamped and survives bad durations`() {
        assertEquals(0f, progress(-50, 200))
        assertEquals(0f, progress(0, 200))
        assertEquals(0.5f, progress(100, 200))
        assertEquals(1f, progress(500, 200))
        assertEquals(1f, progress(10, 0)) // a lifespan of 0 must not divide by zero
        assertEquals(1f, progress(10, -5))
        assertEquals(0f, progress(0, 0))
    }

    @Test fun `smooth step has the right ends, is monotonic and clamps`() {
        assertEquals(0f, smooth(0f))
        assertEquals(1f, smooth(1f))
        assertEquals(0.5f, smooth(0.5f), 1e-6f)
        assertEquals(0f, smooth(-3f))
        assertEquals(1f, smooth(7f))
        var last = -1f
        for (i in 0..100) {
            val v = smooth(i / 100f)
            assertTrue(v >= last)
            last = v
        }
    }

    @Test fun `fade starts and ends softly`() {
        // almost no change right after the start and right before the end, unlike a straight line
        assertTrue(smooth(0.05f) < 0.05f)
        assertTrue(1f - smooth(0.95f) < 0.05f)
    }

    @Test fun `trail width goes from start to end width and never leaves that range`() {
        assertEquals(10f, width(10f, 2.5f, 0, 300))
        assertEquals(2.5f, width(10f, 2.5f, 300, 300))
        assertEquals(2.5f, width(10f, 2.5f, 9999, 300))
        assertEquals(10f, width(10f, 2.5f, -20, 300))
        assertEquals(6.25f, width(10f, 2.5f, 150, 300), 1e-4f)
        for (t in 0..300 step 10) assertTrue(width(10f, 2.5f, t, 300) in 2.5f..10f)
        assertEquals(2.5f, width(10f, 2.5f, 5, 0)) // zero linger duration: treat as fully aged
    }

    @Test fun `head stays wider than a straight taper early on`() {
        val straight = 10f - 7.5f * (60f / 300f)
        assertTrue(width(10f, 2.5f, 60, 300) > straight)
    }
}
