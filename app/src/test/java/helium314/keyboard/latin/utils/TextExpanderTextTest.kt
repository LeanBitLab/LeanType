// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import helium314.keyboard.latin.utils.TextExpanderText.ErrorKind
import helium314.keyboard.latin.utils.TextExpanderUtils.ShortcutEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextExpanderTextTest {
    private val sample = linkedMapOf(
        ";cic" to ShortcutEntry("Du bist ein Experte.\n\n## Aufgabe\nFasse zusammen:\n%clipboard%", ";"),
        "mfg" to ShortcutEntry("Mit freundlichen Grüßen\nChristian"),
        TextExpanderUtils.REGEX_PREFIX + "\\d+eur" to ShortcutEntry("Euro"),
    )

    @Test fun `format and parse give the same shortcuts`() {
        val text = TextExpanderText.format(sample)
        assertTrue(text.startsWith("=== ;cic ===\nDu bist"))
        assertTrue(text.contains("\n\n=== regex: \\d+eur ===\nEuro\n"))
        val result = TextExpanderText.parse(text, sample)
        assertEquals(emptyList<TextExpanderText.Error>(), result.errors)
        assertEquals(sample, result.entries)
        assertTrue(TextExpanderText.diff(sample, result.entries).isEmpty)
    }

    @Test fun `empty lines inside a text stay, around it they are removed`() {
        val result = TextExpanderText.parse("\n=== a ===\n\nline 1\n\nline 2\n\n\n=== b ===\nx", emptyMap())
        assertEquals("line 1\n\nline 2", result.entries["a"]!!.template)
        assertEquals("x", result.entries["b"]!!.template)
    }

    @Test fun `prefix is taken from leading non letters for new shortcuts`() {
        val result = TextExpanderText.parse("=== ;cic ===\nx\n=== ->x ===\ny\n=== :: ===\nz", emptyMap())
        assertEquals(";", result.entries[";cic"]!!.prefix)
        assertEquals("->", result.entries["->x"]!!.prefix)
        assertEquals("", result.entries["::"]!!.prefix)
    }

    @Test fun `existing text with trailing line break is unchanged`() {
        val existing = mapOf("a" to ShortcutEntry("text\n"))
        val result = TextExpanderText.parse(TextExpanderText.format(existing), existing)
        assertEquals("text\n", result.entries["a"]!!.template)
        assertTrue(TextExpanderText.diff(existing, result.entries).isEmpty)
    }

    @Test fun `diff lists new, changed and removed shortcuts`() {
        val text = "=== ;cic ===\nneu\n=== neu ===\nx"
        val diff = TextExpanderText.diff(sample, TextExpanderText.parse(text, sample).entries)
        assertEquals(listOf("neu"), diff.added)
        assertEquals(listOf(";cic"), diff.changed)
        assertEquals(listOf("mfg", "regex: \\d+eur"), diff.removed)
    }

    @Test fun `errors are reported with line numbers`() {
        val text = "vorher\n=== ===\nx\n=== a b ===\nx\n=== leer ===\n\n=== d ===\n1\n=== d ===\n2\n=== regex: ( ===\nx"
        val errors = TextExpanderText.parse(text, emptyMap()).errors
        assertEquals(listOf(
            TextExpanderText.Error(1, ErrorKind.TEXT_BEFORE_FIRST),
            TextExpanderText.Error(2, ErrorKind.EMPTY_SHORTCUT),
            TextExpanderText.Error(4, ErrorKind.SPACE_IN_SHORTCUT, "a b"),
            TextExpanderText.Error(6, ErrorKind.EMPTY_TEXT, "leer"),
            TextExpanderText.Error(10, ErrorKind.DUPLICATE, "d"),
            TextExpanderText.Error(12, ErrorKind.INVALID_REGEX, "("),
        ), errors)
    }

    @Test fun `headers give names and line offsets`() {
        val text = "=== a ===\nx\n=== b ===\ny"
        assertEquals(listOf("a" to 0, "b" to 12), TextExpanderText.headers(text))
    }

    @Test fun `empty text means no shortcuts`() {
        val result = TextExpanderText.parse("  \n", sample)
        assertTrue(result.entries.isEmpty())
        assertEquals(3, TextExpanderText.diff(sample, result.entries).removed.size)
    }
}
