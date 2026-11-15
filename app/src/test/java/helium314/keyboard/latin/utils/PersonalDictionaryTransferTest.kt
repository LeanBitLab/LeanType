// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import helium314.keyboard.latin.utils.PersonalDictionaryTransfer.Entry
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersonalDictionaryTransferTest {
    private fun roundTrip(entries: List<Entry>): List<Entry> {
        val out = StringWriter()
        assertEquals(entries.size, PersonalDictionaryTransfer.write(entries.asSequence(), out))
        return PersonalDictionaryTransfer.parse(out.toString().reader().buffered())
    }

    @Test fun `entries survive a round trip`() {
        val entries = listOf(
            Entry("LeanType", null, null),
            Entry("omw", "on my way", "en_US"),
            Entry("grüß", null, "de_DE"),
        )
        assertEquals(entries, roundTrip(entries))
    }

    @Test fun `locale as stored is kept, language tags are converted`() {
        assertEquals("en_US", PersonalDictionaryTransfer.normalizeLocale("en_US"))
        assertEquals("en_US", PersonalDictionaryTransfer.normalizeLocale("en-US"))
        assertEquals("de", PersonalDictionaryTransfer.normalizeLocale("de"))
        assertNull(PersonalDictionaryTransfer.normalizeLocale(""))
        assertNull(PersonalDictionaryTransfer.normalizeLocale("all"))
        assertNull(PersonalDictionaryTransfer.normalizeLocale(null))
    }

    @Test fun `gboard export with other column order is read`() {
        val text = "# Gboard Dictionary format:shortcut\tword\tlanguage_tag\n" +
            "brb\tbe right back\ten-US\n" +
            "\tplain\t\n"
        val entries = PersonalDictionaryTransfer.parse(text.reader().buffered())
        assertEquals(listOf(Entry("be right back", "brb", "en_US"), Entry("plain", null, null)), entries)
    }

    @Test fun `blank lines and comments are skipped`() {
        val text = "# comment\n\nword\t\t\n   \t\t\n"
        assertEquals(listOf(Entry("word", null, null)), PersonalDictionaryTransfer.parse(text.reader().buffered()))
    }
}
