// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.settings

import androidx.core.content.edit
import helium314.keyboard.latin.utils.prefs
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SpaceLongPressTest {
    private val prefs get() = RuntimeEnvironment.getApplication().prefs()

    @Before fun clear() = prefs.edit { clear() }

    @Test fun `default shows the input method picker`() {
        assertEquals("input_method_picker", Settings.readSpaceLongPress(prefs))
    }

    @Test fun `the old switch turned off means nothing`() {
        prefs.edit { putBoolean(Settings.PREF_SPACE_TO_CHANGE_LANG, false) }
        assertEquals("none", Settings.readSpaceLongPress(prefs))
    }

    @Test fun `the old switch turned on keeps the input method picker`() {
        prefs.edit { putBoolean(Settings.PREF_SPACE_TO_CHANGE_LANG, true) }
        assertEquals("input_method_picker", Settings.readSpaceLongPress(prefs))
    }

    @Test fun `the new setting wins over the old switch`() {
        prefs.edit {
            putBoolean(Settings.PREF_SPACE_TO_CHANGE_LANG, false)
            putString(Settings.PREF_SPACE_LONG_PRESS, "switch_language")
        }
        assertEquals("switch_language", Settings.readSpaceLongPress(prefs))
    }
}
