// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import kotlin.math.roundToInt

import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues

enum class ScreenProfile {
    COMPACT,
    LARGE;

    fun isLarge(): Boolean = this == LARGE
    fun isCompact(): Boolean = this == COMPACT
}

object ScreenProfileProvider {
    private var cachedProfile: ScreenProfile? = null
    private var cachedConfigHash: Int = 0

    @JvmOverloads
    fun getScreenProfile(
        context: Context,
        configuration: Configuration? = null,
        settingsValues: SettingsValues? = null
    ): ScreenProfile {
        val isFoldableModeEnabled = settingsValues?.mFoldableMode
            ?: context.prefs().getBoolean(Settings.PREF_FOLDABLE_MODE, false)

        if (!isFoldableModeEnabled) {
            return ScreenProfile.COMPACT
        }

        val config = configuration ?: context.resources.configuration
        val currentHash = config.hashCode()
        cachedProfile?.let {
            if (cachedConfigHash == currentHash) return it
        }

        val widthPx = ResourceUtils.getDefaultKeyboardWidth(context)
        val density = context.resources.displayMetrics.density
        val calculatedDp = if (density > 0f) (widthPx / density).roundToInt() else 0
        val availableWidthDp = if (config.screenWidthDp > 0) config.screenWidthDp else calculatedDp

        val profile = when {
            availableWidthDp >= 600 -> ScreenProfile.LARGE
            config.smallestScreenWidthDp >= 600 -> ScreenProfile.LARGE
            else -> ScreenProfile.COMPACT
        }

        Log.i("ScreenProfile", "getScreenProfile -> $profile (screenWidthDp=${config.screenWidthDp}, smallestWidthDp=${config.smallestScreenWidthDp}, calculatedDp=$calculatedDp)")

        cachedProfile = profile
        cachedConfigHash = currentHash
        return profile
    }

    fun invalidateCache() {
        cachedProfile = null
        cachedConfigHash = 0
    }
}
