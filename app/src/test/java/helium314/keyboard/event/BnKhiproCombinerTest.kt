// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BnKhiproCombinerTest {
    private val combiner = BnKhiproCombiner()

    private fun type(c: Char): Event =
        combiner.processEvent(null, Event.createSoftwareKeypressEvent(c.code, 0, 0, 0, 0, false))

    @Test fun backslashEntersBlinderAndLatinPassesThrough() {
        assertTrue(type('\\').isConsumed) // trigger is not typed
        val e = type('k')
        assertFalse(e.isConsumed)
        assertEquals('k'.code, e.codePoint)
    }

    @Test fun semicolonLeavesBlinder() {
        type('\\')
        assertTrue(type(';').isConsumed)
        assertTrue(type('k').isConsumed) // composed by Khipro again
    }

    @Test fun doubleSemicolonTypesLiteralAndStaysInBlinder() {
        type('\\')
        type(';')
        val e = type(';')
        assertFalse(e.isConsumed)
        assertEquals(';'.code, e.codePoint)
        assertFalse(type('k').isConsumed)
    }

    @Test fun questionMarkDoesNotEnterBlinder() {
        combiner.reset()
        type('?')
        combiner.reset()
        assertTrue(type('k').isConsumed)
    }

    @Test fun newInputFieldLeavesBlinder() {
        type('\\')
        combiner.onStartInput()
        assertTrue(type('k').isConsumed)
    }
}
