// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.utils.TextExpanderUtils
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.protectedPrefs
import helium314.keyboard.settings.preferences.BackupCategory
import helium314.keyboard.settings.preferences.restoreMainPreferences
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupPreferencesTest {
    private lateinit var context: Context
    private lateinit var credentialContext: Context
    private lateinit var prefs: SharedPreferences
    private val allCategories = BackupCategory.entries.toSet()
    private val shortcuts = mapOf(";greet" to TextExpanderUtils.ShortcutEntry("Hello\n\"reader\"", ";"))

    @Before
    fun setup() {
        credentialContext = ApplicationProvider.getApplicationContext<Context>()
        context = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            credentialContext.createDeviceProtectedStorageContext() else credentialContext
        // Bind the process-wide cache to this test's storage context, not application setup's.
        DeviceProtectedUtils::class.java.getDeclaredField("prefs").apply { isAccessible = true }.set(null, null)
        prefs = context.prefs()
        assertTrue(prefs.edit().clear().commit())
        assertTrue(credentialContext.protectedPrefs().edit().clear().commit())
        TextExpanderUtils.clearCache()
    }

    @Test
    fun emptyProtectedArchiveDoesNotEraseRestoredTextExpander() {
        TextExpanderUtils.saveShortcuts(context, shortcuts)
        val saved = requireNotNull(prefs.getString(TextExpanderUtils.PREF_DATA, null))
        // Both accessors resolve to the same file when the activity's default storage is device-protected.
        val protectedPrefs = context.protectedPrefs()
        assertEquals(saved, protectedPrefs.getString(TextExpanderUtils.PREF_DATA, null))
        assertTrue(prefs.edit().clear().commit())
        assertTrue(TextExpanderUtils.getShortcuts(context).isEmpty())
        restoreMainPreferences(context, lines(mapOf(TextExpanderUtils.PREF_DATA to saved)),
            lines(emptyMap()), allCategories)
        assertEquals(shortcuts, TextExpanderUtils.getShortcuts(context))
    }

    @Test
    fun separateStoresKeepSeparateArchives() {
        val other = credentialContext.protectedPrefs()
        assertFalse(credentialContext.isDeviceProtectedStorage)
        restoreMainPreferences(credentialContext, lines(mapOf("main" to "one")),
            lines(mapOf("protected" to "two")), allCategories)
        assertEquals(mapOf("main" to "one"), prefs.all)
        assertEquals(mapOf("protected" to "two"), other.all)
    }

    @Test
    fun absentArchiveDoesNotClearItsStore() {
        val other = credentialContext.protectedPrefs()
        assertTrue(other.edit().clear().putString("untouched", "keep").commit())
        restoreMainPreferences(credentialContext, lines(mapOf("main" to "one")), null, allCategories)
        assertEquals("keep", other.getString("untouched", null))
    }

    @Test
    fun categoryFilteringIsPreserved() {
        assertTrue(prefs.edit().putString("theme_style", "keep").putString("pref_text_expander_data", "old").commit())
        restoreMainPreferences(context,
            lines(mapOf("pref_text_expander_data" to "{}")), lines(emptyMap()),
            setOf(BackupCategory.DICTIONARY_HISTORY))
        assertEquals("keep", prefs.getString("theme_style", null))
        assertEquals("{}", prefs.getString(TextExpanderUtils.PREF_DATA, null))
    }

    @Test
    fun generalSettingsDoNotRestoreTextExpander() {
        restoreMainPreferences(context, lines(mapOf(TextExpanderUtils.PREF_DATA to "{}")),
            null, setOf(BackupCategory.GENERAL_SETTINGS))
        assertFalse(prefs.contains(TextExpanderUtils.PREF_DATA))
    }

    @Test
    fun sharedStoreRetainsBothArchivesWithMainValuesTakingPriority() {
        assertTrue(prefs.edit().putString("old", "remove").commit())
        restoreMainPreferences(context, lines(mapOf("common" to "main", "mainOnly" to "one")),
            lines(mapOf("common" to "legacy", "protectedOnly" to "two")), allCategories)
        assertEquals(mapOf("common" to "main", "mainOnly" to "one", "protectedOnly" to "two"), prefs.all)
    }

    @Test
    fun sharedStoreAcceptsProtectedOnlyBackup() {
        restoreMainPreferences(context, null, lines(mapOf("legacy" to "keep")), allCategories)
        assertEquals(mapOf("legacy" to "keep"), prefs.all)
    }

    @Test
    fun missingBothArchivesLeavesPreferencesUntouched() {
        assertTrue(prefs.edit().putString("old", "keep").commit())
        restoreMainPreferences(context, null, null, allCategories)
        assertEquals(mapOf("old" to "keep"), prefs.all)
    }

    @Test
    fun malformedArchiveReportsFailureWithoutClearingStore() {
        assertTrue(prefs.edit().putString("old", "keep").commit())
        assertFailsWith<IllegalStateException> {
            restoreMainPreferences(context, listOf("string settings", "{broken"),
                lines(mapOf("protectedOnly" to "two")), allCategories)
        }
        assertEquals(mapOf("old" to "keep"), prefs.all)
    }

    @Test
    @Config(sdk = [23])
    fun preDirectBootDeviceAlsoPreservesBothArchives() {
        restoreMainPreferences(context, lines(mapOf("main" to "one")),
            lines(mapOf("protected" to "two")), allCategories)
        assertEquals(mapOf("main" to "one", "protected" to "two"), prefs.all)
    }

    @Test
    fun sharedStoreRestoresEveryPreferenceType() {
        val data = listOf(
            "boolean settings", Json.encodeToString(mapOf("enabled" to true)),
            "int settings", Json.encodeToString(mapOf("count" to 2)),
            "long settings", Json.encodeToString(mapOf("time" to 3L)),
            "float settings", Json.encodeToString(mapOf("scale" to 0.5f)),
            "string settings", Json.encodeToString(mapOf("text" to "line\n\"quote\"")),
            "string set settings", Json.encodeToString(mapOf("set" to setOf("one", "two")))
        )
        restoreMainPreferences(context, data, lines(emptyMap()), allCategories)
        assertEquals(mapOf("enabled" to true, "count" to 2, "time" to 3L, "scale" to 0.5f,
            "text" to "line\n\"quote\"", "set" to setOf("one", "two")), prefs.all)
    }

    private fun lines(strings: Map<String, String>) = listOf("string settings", Json.encodeToString(strings))
}
