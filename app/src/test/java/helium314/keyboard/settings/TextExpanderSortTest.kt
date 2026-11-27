// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import helium314.keyboard.latin.utils.TextExpanderUtils
import helium314.keyboard.settings.screens.UserDictSortOrder
import helium314.keyboard.settings.screens.sortShortcuts
import org.junit.Assert.assertEquals
import org.junit.Test

class TextExpanderSortTest {
    // stored in the order of adding
    private val shortcuts = linkedMapOf(
        "brb" to 1, ";Addr" to 2, "${TextExpanderUtils.REGEX_PREFIX}\\d+eur" to 3, "Cya" to 4, "ack" to 5
    )

    private fun order(o: UserDictSortOrder) = sortShortcuts(shortcuts, o).map { it.value }

    @Test fun `oldest keeps the order of adding`() = assertEquals(listOf(1, 2, 3, 4, 5), order(UserDictSortOrder.OLDEST))

    @Test fun `last added reverses it`() = assertEquals(listOf(5, 4, 3, 2, 1), order(UserDictSortOrder.LAST_ADDED))

    @Test fun `alphabetical ignores case and sorts regex shortcuts by their label`() =
        // ";addr" < "ack" < "brb" < "cya" < "regex: \d+eur"
        assertEquals(listOf(2, 5, 1, 4, 3), order(UserDictSortOrder.ALPHABETICAL))
}
