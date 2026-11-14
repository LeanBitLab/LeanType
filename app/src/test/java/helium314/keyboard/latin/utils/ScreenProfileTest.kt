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
    fun testScreenProfileStandardPhoneLandscapeIsCompact() {
        // Standard candybar phone in landscape (width >= 600, but height < 600 and not a foldable)
        val config = Configuration().apply {
            screenWidthDp = 800
            screenHeightDp = 400
            smallestScreenWidthDp = 400
        }
        val profile = ScreenProfileProvider.getScreenProfile(context, config)
        assertEquals(ScreenProfile.COMPACT, profile)
    }

    @Test
    fun testScreenProfileTabletBothDimensionsLargeIsLarge() {
        // Tablet where both width and height are >= 600 dp automatically gets LARGE profile
        val config = Configuration().apply {
            screenWidthDp = 800
            screenHeightDp = 800
            smallestScreenWidthDp = 800
        }
        val profile = ScreenProfileProvider.getScreenProfile(context, config)
        assertEquals(ScreenProfile.LARGE, profile)
    }

    @Test
    fun testScreenProfileFoldableUnfoldedIsLarge() {
        // Foldable device when unfolded gets LARGE profile automatically without manual toggle
        ScreenProfileProvider.isFoldableDeviceOverride = true
        val config = Configuration().apply {
            screenWidthDp = 800
            screenHeightDp = 750
            smallestScreenWidthDp = 750
        }
        val profile = ScreenProfileProvider.getScreenProfile(context, config)
        assertEquals(ScreenProfile.LARGE, profile)
    }

    @Test
    fun testScreenProfileFoldableUnfoldedPortraitRealisticIsLarge() {
        // Realistic book-style foldable unfolded in portrait (e.g. Galaxy Z Fold: width 588dp, height 706dp, smallestWidth 360dp)
        ScreenProfileProvider.isFoldableDeviceOverride = true
        val config = Configuration().apply {
            screenWidthDp = 588
            screenHeightDp = 706
            smallestScreenWidthDp = 360
        }
        val profile = ScreenProfileProvider.getScreenProfile(context, config)
        assertEquals(ScreenProfile.LARGE, profile)
    }

    @Test
    fun testScreenProfileFoldableUnfoldedWithoutHingeSensorIsLarge() {
        // Even when hinge sensor is unavailable, realistic unfolded dimensions (both >= 500dp) yield LARGE profile
        ScreenProfileProvider.isFoldableDeviceOverride = false
        val config = Configuration().apply {
            screenWidthDp = 588
            screenHeightDp = 706
            smallestScreenWidthDp = 360
        }
        val profile = ScreenProfileProvider.getScreenProfile(context, config)
        assertEquals(ScreenProfile.LARGE, profile)
    }

    @Test
    fun testScreenProfileFoldableFoldedLandscapeIsCompact() {
        // Foldable device when folded in landscape (width >= 600, but height < 600 and smallestScreenWidthDp < 600) gets COMPACT profile
        ScreenProfileProvider.isFoldableDeviceOverride = true
        val config = Configuration().apply {
            screenWidthDp = 880
            screenHeightDp = 390
            smallestScreenWidthDp = 390
        }
        val profile = ScreenProfileProvider.getScreenProfile(context, config)
        assertEquals(ScreenProfile.COMPACT, profile)
    }

    @Test
    fun testScreenProfileFoldableFoldedIsCompact() {
        // Foldable device when folded (width < 600) gets COMPACT profile
        ScreenProfileProvider.isFoldableDeviceOverride = true
        val config = Configuration().apply {
            screenWidthDp = 400
            screenHeightDp = 800
            smallestScreenWidthDp = 400
        }
        val profile = ScreenProfileProvider.getScreenProfile(context, config)
        assertEquals(ScreenProfile.COMPACT, profile)
    }

    @Test
    fun testUnfoldedHeightScaleDecoupledFromCompact() {
        val prefs = context.prefs()
        // Set compact (folded) portrait scale to 0.90f
        prefs.edit().putFloat("keyboard_height_scale_false", 0.90f).apply()
        // Set unfolded portrait scale to 0.60f
        prefs.edit().putFloat("keyboard_height_scale_large_false", 0.60f).apply()

        // COMPACT profile reads folded portrait scale
        val compactHeight = Settings.readHeightScale(prefs, landscape = false, profile = ScreenProfile.COMPACT)
        assertEquals(0.90f, compactHeight, 0.001f)

        // LARGE profile reads unfolded portrait scale
        val unfoldedHeight = Settings.readHeightScale(prefs, landscape = false, profile = ScreenProfile.LARGE)
        assertEquals(0.60f, unfoldedHeight, 0.001f)

        // Unfolded landscape defaults independently without inheriting compact portrait
        val unfoldedLandscape = Settings.readHeightScale(prefs, landscape = true, profile = ScreenProfile.LARGE)
        assertEquals(helium314.keyboard.latin.settings.Defaults.PREF_UNFOLDED_KEYBOARD_HEIGHT_SCALE[1], unfoldedLandscape, 0.001f)
    }

    @Test
    fun testUnfoldedSplitKeyboardPreference() {
        val prefs = context.prefs()
        // Case 1: Compact split disabled, unfolded explicitly enabled
        prefs.edit().putBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, false).apply()
        prefs.edit().putBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD_UNFOLDED, true).apply()

        val compactSplit = Settings.readSplitKeyboardEnabled(prefs, isLandscape = false, profile = ScreenProfile.COMPACT)
        assertEquals(false, compactSplit)

        val unfoldedSplit = Settings.readSplitKeyboardEnabled(prefs, isLandscape = false, profile = ScreenProfile.LARGE)
        assertEquals(true, unfoldedSplit)

        // Case 2: Compact split disabled, unfolded not explicitly set (must default to true independently)
        prefs.edit().clear().putBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, false).apply()
        val compactSplitDefault = Settings.readSplitKeyboardEnabled(prefs, isLandscape = false, profile = ScreenProfile.COMPACT)
        val unfoldedSplitDefault = Settings.readSplitKeyboardEnabled(prefs, isLandscape = false, profile = ScreenProfile.LARGE)
        assertEquals(false, compactSplitDefault)
        assertEquals(true, unfoldedSplitDefault)

        // Case 3: Compact split enabled, unfolded explicitly disabled
        prefs.edit().clear()
            .putBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, true)
            .putBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD_UNFOLDED, false)
            .apply()
        val compactSplitEnabled = Settings.readSplitKeyboardEnabled(prefs, isLandscape = false, profile = ScreenProfile.COMPACT)
        val unfoldedSplitDisabled = Settings.readSplitKeyboardEnabled(prefs, isLandscape = false, profile = ScreenProfile.LARGE)
        assertEquals(true, compactSplitEnabled)
        assertEquals(false, unfoldedSplitDisabled)
    }

    @Test
    fun testUnfoldedBottomPaddingScale() {
        val prefs = context.prefs()
        prefs.edit().putFloat("bottom_padding_scale_false", 1.2f).apply()
        prefs.edit().putFloat("bottom_padding_scale_large_false", 0.3f).apply()

        val compactPadding = Settings.readBottomPaddingScale(prefs, landscape = false, profile = ScreenProfile.COMPACT)
        assertEquals(1.2f, compactPadding, 0.001f)

        val unfoldedPadding = Settings.readBottomPaddingScale(prefs, landscape = false, profile = ScreenProfile.LARGE)
        assertEquals(0.3f, unfoldedPadding, 0.001f)
    }

    @Test
    fun testUnfoldedSidePaddingScaleDefaults() {
        val prefs = context.prefs()
        prefs.edit().clear().apply()

        val compactUnsplit = Settings.readSidePaddingScale(prefs, landscape = false, split = false, profile = ScreenProfile.COMPACT)
        assertEquals(0.15f, compactUnsplit, 0.001f)

        val unfoldedUnsplit = Settings.readSidePaddingScale(prefs, landscape = false, split = false, profile = ScreenProfile.LARGE)
        assertEquals(0.5f, unfoldedUnsplit, 0.001f)

        val unfoldedSplit = Settings.readSidePaddingScale(prefs, landscape = false, split = true, profile = ScreenProfile.LARGE)
        assertEquals(0.15f, unfoldedSplit, 0.001f)
    }

    @Test
    fun testUnfoldedSidePaddingScaleCustomPreferences() {
        val prefs = context.prefs()
        prefs.edit().clear().apply()

        // When custom compact key is set and custom unfolded key is set
        prefs.edit().putFloat("side_padding_scale_false_false", 0.2f).apply()
        prefs.edit().putFloat("side_padding_scale_large_false_false", 0.6f).apply()

        val compactPadding = Settings.readSidePaddingScale(prefs, landscape = false, split = false, profile = ScreenProfile.COMPACT)
        assertEquals(0.2f, compactPadding, 0.001f)

        val unfoldedPadding = Settings.readSidePaddingScale(prefs, landscape = false, split = false, profile = ScreenProfile.LARGE)
        assertEquals(0.6f, unfoldedPadding, 0.001f)

        // When unfolded key is removed, should fall back to compact custom key
        prefs.edit().remove("side_padding_scale_large_false_false").apply()
        val fallbackPadding = Settings.readSidePaddingScale(prefs, landscape = false, split = false, profile = ScreenProfile.LARGE)
        assertEquals(0.2f, fallbackPadding, 0.001f)
    }
}
