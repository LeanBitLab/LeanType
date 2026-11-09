// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TextExpanderTabTest {
    @Test
    fun tabPlaceholderInsertsATabCharacter() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals("Name\tValue", TextExpanderUtils.expand("Name%tab%Value", context))
        assertEquals("plain text", TextExpanderUtils.expand("plain text", context))
    }
}
