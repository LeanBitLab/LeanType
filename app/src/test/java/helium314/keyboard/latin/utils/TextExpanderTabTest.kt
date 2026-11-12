// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap

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
            "Olp" to TextExpanderUtils.ShortcutEntry(template = "H", prefix = ""),
            ";addr" to TextExpanderUtils.ShortcutEntry(template = "456 Oak Ave", prefix = ";"),
            "cat" to TextExpanderUtils.ShortcutEntry(template = "Cute Kitty", prefix = "", imageUri = "/path/to/cat.png"),
            "dog" to TextExpanderUtils.ShortcutEntry(template = "", prefix = "", imageUri = "/path/to/dog.png")
        )
        TextExpanderUtils.saveShortcuts(context, shortcuts)

        // Unprefixed text shortcuts: require exact match (partial prefixes do not hijack normal words)
        assertEquals(emptyList<TextExpanderUtils.SuggestionMatch>(), TextExpanderUtils.getMatchingSuggestions("a", "a", context))
        assertEquals(emptyList<TextExpanderUtils.SuggestionMatch>(), TextExpanderUtils.getMatchingSuggestions("ad", "ad", context))
        assertEquals(emptyList<TextExpanderUtils.SuggestionMatch>(), TextExpanderUtils.getMatchingSuggestions("Ol", "Ol", context))
        val textFullMatches = TextExpanderUtils.getMatchingSuggestions("addr", "addr", context)
        assertEquals("123 Main St", textFullMatches.first().previewLabel)
        val olpFullMatches = TextExpanderUtils.getMatchingSuggestions("olp", "olp", context)
        assertEquals("H", olpFullMatches.first().previewLabel)

        // Prefixed text shortcuts: keep prefix autocomplete ("shortcut: preview") -> exact shows only preview
        val prefixedMatches = TextExpanderUtils.getMatchingSuggestions("ad", ";ad", context)
        assertEquals("addr: 456 Oak Ave", prefixedMatches.first().previewLabel)
        val prefixedFullMatches = TextExpanderUtils.getMatchingSuggestions("addr", ";addr", context)
        assertEquals("456 Oak Ave", prefixedFullMatches.first().previewLabel)

        // Image shortcut with label: only matches full shortcut, always shows only the label (never "$shortcut: $preview")
        assertEquals(emptyList<TextExpanderUtils.SuggestionMatch>(), TextExpanderUtils.getMatchingSuggestions("c", "c", context))
        assertEquals(emptyList<TextExpanderUtils.SuggestionMatch>(), TextExpanderUtils.getMatchingSuggestions("ca", "ca", context))
        val imageWithLabelFullMatches = TextExpanderUtils.getMatchingSuggestions("cat", "cat", context)
        assertEquals("Cute Kitty", imageWithLabelFullMatches.first().previewLabel)

        // Image shortcut without label: only matches full shortcut, previewLabel is empty "" (renders only the image thumbnail)
        assertEquals(emptyList<TextExpanderUtils.SuggestionMatch>(), TextExpanderUtils.getMatchingSuggestions("d", "d", context))
        assertEquals(emptyList<TextExpanderUtils.SuggestionMatch>(), TextExpanderUtils.getMatchingSuggestions("do", "do", context))
        val imageWithoutLabelFullMatches = TextExpanderUtils.getMatchingSuggestions("dog", "dog", context)
        assertEquals("", imageWithoutLabelFullMatches.first().previewLabel)
    }

    @Test
    fun testImageShortcutRequiresExactMatchAndPrefixedShortcutsWork() {
        val context = RuntimeEnvironment.getApplication()
        context.prefs().edit()
            .putBoolean(TextExpanderUtils.PREF_ENABLED, true)
            .putBoolean(TextExpanderUtils.PREF_SUGGESTIONS, true)
            .commit()

        TextExpanderUtils.saveShortcuts(
            context,
            mapOf("you" to TextExpanderUtils.ShortcutEntry(template = "", prefix = "", imageUri = "/path/to/you.png"))
        )
        assertEquals(emptyList<TextExpanderUtils.SuggestionMatch>(), TextExpanderUtils.getMatchingSuggestions("y", "y", context))
        assertEquals(emptyList<TextExpanderUtils.SuggestionMatch>(), TextExpanderUtils.getMatchingSuggestions("yo", "yo", context))
        val exactYou = TextExpanderUtils.getMatchingSuggestions("you", "you", context)
        assertEquals(1, exactYou.size)
        assertEquals("/path/to/you.png", exactYou.single().imageUri)

        // When both "y" and "you" image shortcuts exist, typing "y" only returns "y"
        TextExpanderUtils.saveShortcuts(
            context,
            mapOf(
                "y" to TextExpanderUtils.ShortcutEntry(template = "", prefix = "", imageUri = "/path/to/y.png"),
                "you" to TextExpanderUtils.ShortcutEntry(template = "", prefix = "", imageUri = "/path/to/you.png")
            )
        )
        val matchesY = TextExpanderUtils.getMatchingSuggestions("y", "y", context)
        assertEquals(listOf("y"), matchesY.map { it.key })
        assertEquals(listOf("/path/to/y.png"), matchesY.map { it.imageUri })

        // Prefixed shortcuts stored as prefix + shortcut in key work cleanly without double-prefixing
        TextExpanderUtils.saveShortcuts(
            context,
            mapOf(
                ";you" to TextExpanderUtils.ShortcutEntry(template = "", prefix = ";", imageUri = "/path/to/p_you.png"),
                ";cic" to TextExpanderUtils.ShortcutEntry(template = "Hello world", prefix = ";")
            )
        )
        assertEquals(emptyList<TextExpanderUtils.SuggestionMatch>(), TextExpanderUtils.getMatchingSuggestions("y", ";y", context))
        val prefixedImage = TextExpanderUtils.getMatchingSuggestions("you", ";you", context)
        assertEquals(1, prefixedImage.size)
        assertEquals(1, prefixedImage.single().prefixLength)
        assertEquals("/path/to/p_you.png", prefixedImage.single().imageUri)

        val prefixedTextPrefix = TextExpanderUtils.getMatchingSuggestions("c", ";c", context)
        assertEquals(1, prefixedTextPrefix.size)
        assertEquals(1, prefixedTextPrefix.single().prefixLength)
        assertEquals("cic: Hello world", prefixedTextPrefix.single().previewLabel)

        val prefixedTextExact = TextExpanderUtils.getMatchingSuggestions("cic", ";cic", context)
        assertEquals(1, prefixedTextExact.size)
        assertEquals(1, prefixedTextExact.single().prefixLength)
        assertEquals("Hello world", prefixedTextExact.single().previewLabel)
    }

    @Test
    fun testGetExpandedWordForTypedPropagatesImageUri() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.prefs()
        prefs.edit()
            .putBoolean(TextExpanderUtils.PREF_ENABLED, true)
            .putBoolean(TextExpanderUtils.PREF_SUGGESTIONS_ONLY, false)
            .commit()

        val logoPath = "/data/user/0/app/files/text_expander_images/img_logo.png"
        val regexPath = "/data/user/0/app/files/text_expander_images/img_regex.jpg"
        val shortcuts = mapOf(
            "logo" to TextExpanderUtils.ShortcutEntry(template = "Company Logo", prefix = "", imageUri = logoPath),
            TextExpanderUtils.REGEX_PREFIX + "!pic" to TextExpanderUtils.ShortcutEntry(template = "", prefix = "", imageUri = regexPath)
        )
        TextExpanderUtils.saveShortcuts(context, shortcuts)

        val directResult = TextExpanderUtils.getExpandedWordForTyped("logo", "logo", context)
        assertNotNull(directResult)
        assertEquals(logoPath, directResult!!.imageUri)

        val regexResult = TextExpanderUtils.getExpandedWordForTyped(null, "!pic", context)
        assertNotNull(regexResult)
        assertEquals(regexPath, regexResult!!.imageUri)
    }

    @Test
    fun testClearCachePreservesImageThumbnailCache() {
        val context = RuntimeEnvironment.getApplication()
        val managedDir = TextExpanderImageUtils.getImagesDir(context)
        val imageFile = File(managedDir, "img_preserve_thumb.png")
        val bmp = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        FileOutputStream(imageFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()

        val initialDrawable = TextExpanderImageUtils.getThumbnailDrawable(context, imageFile.name, 40, 26)
        assertNotNull(initialDrawable)

        TextExpanderUtils.clearCache()

        val cachedAfterClear = TextExpanderImageUtils.getCachedThumbnailDrawable(context, imageFile.name, 40, 26)
        assertNotNull(cachedAfterClear)
    }
}
