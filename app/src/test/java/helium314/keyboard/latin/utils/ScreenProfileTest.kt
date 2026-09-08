// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScreenProfileTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.prefs().edit().clear().apply()
        ScreenProfileProvider.invalidateCache()
    }

    @Test
    fun testScreenProfileDefaultIsCompact() {
        // By default (pref_foldable_mode = false), getScreenProfile must return COMPACT
        val config = Configuration().apply {
            screenWidthDp = 800
            smallestScreenWidthDp = 800
        }
        val profile = ScreenProfileProvider.getScreenProfile(context, config)
        assertEquals(ScreenProfile.COMPACT, profile)
    }

    @Test
    fun testScreenProfileOptInLargeWhenFoldableModeEnabled() {
        // When pref_foldable_mode is true and width >= 600 dp, getScreenProfile returns LARGE
        context.prefs().edit().putBoolean(Settings.PREF_FOLDABLE_MODE, true).apply()
        ScreenProfileProvider.invalidateCache()

        val config = Configuration().apply {
            screenWidthDp = 800
            smallestScreenWidthDp = 800
        }
        val profile = ScreenProfileProvider.getScreenProfile(context, config)
        assertEquals(ScreenProfile.LARGE, profile)
    }

    @Test
    fun testScreenProfileFoldedOuterScreenIsCompact() {
        // When pref_foldable_mode is true, but device is folded (screenWidthDp < 600, though smallestScreenWidthDp >= 600),
        // it must return COMPACT
        context.prefs().edit().putBoolean(Settings.PREF_FOLDABLE_MODE, true).apply()
        ScreenProfileProvider.invalidateCache()

        val config = Configuration().apply {
            screenWidthDp = 400
            smallestScreenWidthDp = 800
        }
        val profile = ScreenProfileProvider.getScreenProfile(context, config)
        assertEquals(ScreenProfile.COMPACT, profile)
    }
}
