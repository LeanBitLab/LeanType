package helium314.keyboard.latin.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TranslationUtilsTest {

    private lateinit var prefs: SharedPreferences

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    @Test
    fun getLanguageHistory_emptyPrefs_returnsEmptyList() {
        val history = TranslationUtils.getLanguageHistory(prefs)
        assertTrue(history.isEmpty())
    }

    @Test
    fun getLanguageHistory_validPrefs_returnsParsedList() {
        prefs.edit().putString("pref_translation_language_history", "en|English\nes|Spanish").apply()
        val history = TranslationUtils.getLanguageHistory(prefs)
        assertEquals(2, history.size)
        assertEquals("English" to "en", history[0])
        assertEquals("Spanish" to "es", history[1])
    }

    @Test
    fun getLanguageHistory_malformedPrefs_skipsMalformedItems() {
        prefs.edit().putString("pref_translation_language_history", "en|English\nmalformed\nes|Spanish").apply()
        val history = TranslationUtils.getLanguageHistory(prefs)
        assertEquals(2, history.size)
        assertEquals("English" to "en", history[0])
        assertEquals("Spanish" to "es", history[1])
    }

    @Test
    fun getRemovedLanguages_emptyPrefs_returnsEmptySet() {
        val removed = TranslationUtils.getRemovedLanguages(prefs)
        assertTrue(removed.isEmpty())
    }

    @Test
    fun getRemovedLanguages_validPrefs_returnsParsedSet() {
        prefs.edit().putString("pref_removed_translation_languages", "en,es").apply()
        val removed = TranslationUtils.getRemovedLanguages(prefs)
        assertEquals(2, removed.size)
        assertTrue(removed.contains("en"))
        assertTrue(removed.contains("es"))
    }

    @Test
    fun saveLanguageHistory_addNewLanguage_addsToTop() {
        TranslationUtils.saveLanguageHistory(prefs, "English", "en")
        TranslationUtils.saveLanguageHistory(prefs, "Spanish", "es")

        val history = TranslationUtils.getLanguageHistory(prefs)
        assertEquals(2, history.size)
        assertEquals("Spanish" to "es", history[0])
        assertEquals("English" to "en", history[1])
    }

    @Test
    fun saveLanguageHistory_existingLanguage_movesToTop() {
        TranslationUtils.saveLanguageHistory(prefs, "English", "en")
        TranslationUtils.saveLanguageHistory(prefs, "Spanish", "es")
        TranslationUtils.saveLanguageHistory(prefs, "English", "en")

        val history = TranslationUtils.getLanguageHistory(prefs)
        assertEquals(2, history.size)
        assertEquals("English" to "en", history[0])
        assertEquals("Spanish" to "es", history[1])
    }

    @Test
    fun saveLanguageHistory_removesFromRemovedSet() {
        prefs.edit().putString("pref_removed_translation_languages", "en,es").apply()
        TranslationUtils.saveLanguageHistory(prefs, "English", "en")

        val removed = TranslationUtils.getRemovedLanguages(prefs)
        assertEquals(1, removed.size)
        assertTrue(removed.contains("es"))
    }

    @Test
    fun saveLanguageHistory_caseInsensitiveUpdate() {
        TranslationUtils.saveLanguageHistory(prefs, "English", "en")
        TranslationUtils.saveLanguageHistory(prefs, "english", "EN")

        val history = TranslationUtils.getLanguageHistory(prefs)
        assertEquals(1, history.size)
        assertEquals("english" to "EN", history[0])
    }

    @Test
    fun removeLanguageHistory_removesFromHistoryAndAddsToRemovedSet() {
        TranslationUtils.saveLanguageHistory(prefs, "English", "en")
        TranslationUtils.saveLanguageHistory(prefs, "Spanish", "es")

        TranslationUtils.removeLanguageHistory(prefs, "en")

        val history = TranslationUtils.getLanguageHistory(prefs)
        assertEquals(1, history.size)
        assertEquals("Spanish" to "es", history[0])

        val removed = TranslationUtils.getRemovedLanguages(prefs)
        assertEquals(1, removed.size)
        assertTrue(removed.contains("en"))
    }

    @Test
    fun isSameLanguage_sameCode_returnsTrue() {
        assertTrue(TranslationUtils.isSameLanguage("English" to "en", "eng" to "en"))
    }

    @Test
    fun isSameLanguage_sameName_returnsTrue() {
        assertTrue(TranslationUtils.isSameLanguage("English" to "en", "English" to "eng"))
    }

    @Test
    fun isSameLanguage_caseInsensitiveMatch_returnsTrue() {
        assertTrue(TranslationUtils.isSameLanguage("English" to "en", "ENGLISH" to "EN"))
    }

    @Test
    fun isSameLanguage_different_returnsFalse() {
        assertFalse(TranslationUtils.isSameLanguage("English" to "en", "Spanish" to "es"))
    }
}
