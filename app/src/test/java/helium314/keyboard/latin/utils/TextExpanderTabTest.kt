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

        // Image shortcut without label: previewLabel is empty "" (renders only the image thumbnail)
        val imageWithoutLabelPrefixMatches = TextExpanderUtils.getMatchingSuggestions("d", "d", context)
        assertEquals("", imageWithoutLabelPrefixMatches.first().previewLabel)
        val imageWithoutLabelFullMatches = TextExpanderUtils.getMatchingSuggestions("dog", "dog", context)
        assertEquals("", imageWithoutLabelFullMatches.first().previewLabel)
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
