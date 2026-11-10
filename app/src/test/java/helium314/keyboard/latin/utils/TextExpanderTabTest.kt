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

    @Test
    fun testImageShortcutSuggestionsLabelFormatting() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.prefs()
        prefs.edit()
            .putBoolean(TextExpanderUtils.PREF_ENABLED, true)
            .putBoolean(TextExpanderUtils.PREF_SUGGESTIONS, true)
            .commit()

        val shortcuts = mapOf(
            "addr" to TextExpanderUtils.ShortcutEntry(template = "123 Main St", prefix = ""),
            "cat" to TextExpanderUtils.ShortcutEntry(template = "Cute Kitty", prefix = "", imageUri = "/path/to/cat.png"),
            "dog" to TextExpanderUtils.ShortcutEntry(template = "", prefix = "", imageUri = "/path/to/dog.png")
        )
        TextExpanderUtils.saveShortcuts(context, shortcuts)

        // Text shortcut: prefix typed -> shows "shortcut: preview", full typed -> shows "preview"
        val textPrefixMatches = TextExpanderUtils.getMatchingSuggestions("ad", "ad", context)
        assertEquals("addr: 123 Main St", textPrefixMatches.first().previewLabel)
        val textFullMatches = TextExpanderUtils.getMatchingSuggestions("addr", "addr", context)
        assertEquals("123 Main St", textFullMatches.first().previewLabel)

        // Image shortcut with label: always shows only the label (never "$shortcut: $preview")
        val imageWithLabelPrefixMatches = TextExpanderUtils.getMatchingSuggestions("c", "c", context)
        assertEquals("Cute Kitty", imageWithLabelPrefixMatches.first().previewLabel)
        val imageWithLabelFullMatches = TextExpanderUtils.getMatchingSuggestions("cat", "cat", context)
        assertEquals("Cute Kitty", imageWithLabelFullMatches.first().previewLabel)

        // Image shortcut without label: always shows only the shortcut trigger
        val imageWithoutLabelPrefixMatches = TextExpanderUtils.getMatchingSuggestions("d", "d", context)
        assertEquals("dog", imageWithoutLabelPrefixMatches.first().previewLabel)
        val imageWithoutLabelFullMatches = TextExpanderUtils.getMatchingSuggestions("dog", "dog", context)
        assertEquals("dog", imageWithoutLabelFullMatches.first().previewLabel)
    }
}
