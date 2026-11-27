package helium314.keyboard.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsContainerTest {

    private lateinit var container: SettingsContainer

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        container = SettingsContainer(context)
    }

    @Test
    fun testFilterFunctionality() {
        // Just verify it doesn't crash and returns some results for empty or common strings
        val res = container.filter("a")
        assertTrue(res.isNotEmpty())
    }

    @Test
    fun searchJumpOpensTheScreenThatShowsTheSetting() {
        val expected = mapOf(
            SettingsWithoutKey.GEMINI_API_KEY to SettingsDestination.AIIntegration,
            SettingsWithoutKey.AI_PROVIDER to SettingsDestination.AIIntegration,
            SettingsWithoutKey.TRANSLATION_ENGINE to SettingsDestination.Translation,
            SettingsWithoutKey.APP_QUIRKS to SettingsDestination.Advanced,
            SettingsWithoutKey.KEYPRESS_AUDIO to SettingsDestination.Preferences,
            SettingsWithoutKey.BACKUP_RESTORE to SettingsDestination.Advanced,
            helium314.keyboard.latin.settings.Settings.PREF_SAVE_SUBTYPE_PER_APP to SettingsDestination.Languages,
            helium314.keyboard.latin.settings.Settings.PREF_LANGUAGE_SWIPE_DISTANCE to SettingsDestination.GestureTyping,
            helium314.keyboard.latin.settings.Settings.PREF_CUSTOM_CURRENCY_KEY to SettingsDestination.Preferences,
            helium314.keyboard.latin.settings.Settings.PREF_TIMESTAMP_FORMAT to SettingsDestination.Preferences,
        )
        expected.forEach { (key, destination) -> assertEquals(key, destination, container.screenOf(key)) }
    }

    @Test
    fun keywordsPointToSettingsAndFindWhatTheSearchMisses() = checkKeywords()

    @Test
    @Config(qualifiers = "de")
    fun germanKeywordsPointToSettingsAndFindWhatTheSearchMisses() = checkKeywords()

    private fun checkKeywords() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = context.resources.getStringArray(R.array.settings_search_keywords)
        assertTrue(entries.isNotEmpty())
        entries.forEach { assertTrue("no key or no words: $it", it.substringBefore('|', "").isNotBlank() && it.substringAfter('|').isNotBlank()) }
        container.keywords.forEach { (key, words) ->
            val setting = container[key]
            assertNotNull("no setting $key", setting)
            words.forEach { word ->
                // a word the search finds anyway is not needed
                assertNull("\"$word\" already finds $key", container.match(setting!!, word))
                assertTrue("\"$word\" doesn't find $key", setting in container.filter(word))
            }
        }
    }

    @Test
    fun keywordsAreParsed() {
        val keywords = SettingsContainer.parseKeywords(arrayOf("a|Color colour", "b|x,y", "|nothing", "a|dark"))
        assertEquals(mapOf("a" to listOf("color", "colour", "dark"), "b" to listOf("x", "y")), keywords)
        assertFalse(keywords.containsKey(""))
    }

    @Test
    fun testFilterPerformance() {
        val searches = listOf("a", "b", "c", "theme", "color", "sound", "vib", "dictionary", "key", "layout")

        // Warmup
        for (i in 1..10) {
            for (s in searches) {
                container.filter(s)
            }
        }

        val time = measureNanoTime {
            for (i in 1..100) {
                for (s in searches) {
                    container.filter(s)
                }
            }
        }
        println("Filter performance: ${time / 1_000_000} ms")
    }
}
