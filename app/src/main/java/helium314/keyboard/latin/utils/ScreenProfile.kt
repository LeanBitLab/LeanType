// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.annotation.VisibleForTesting
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
    private var isFoldableDeviceCached: Boolean? = null

    @VisibleForTesting
    var isFoldableDeviceOverride: Boolean? = null

    fun isFoldableDevice(context: Context): Boolean {
        isFoldableDeviceOverride?.let { return it }
        isFoldableDeviceCached?.let { return it }
        val hasHingeSensor = try {
            context.packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle")
        } catch (_: Exception) {
            false
        }
        isFoldableDeviceCached = hasHingeSensor
        return hasHingeSensor
    }

    @JvmOverloads
    fun getScreenProfile(
        context: Context,
        configuration: Configuration? = null,
        settingsValues: SettingsValues? = null
    ): ScreenProfile {
        val config = configuration ?: context.resources.configuration
        val currentHash = config.hashCode()
        cachedProfile?.let {
            if (cachedConfigHash == currentHash) return it
        }

        val widthPx = ResourceUtils.getDefaultKeyboardWidth(context)
        val density = context.resources.displayMetrics.density
        val calculatedDp = if (density > 0f) (widthPx / density).roundToInt() else 0
        val availableWidthDp = if (config.screenWidthDp > 0) config.screenWidthDp else calculatedDp
        val heightPx = context.resources.displayMetrics.heightPixels
        val availableHeightDp = if (config.screenHeightDp > 0) config.screenHeightDp else if (density > 0f) (heightPx / density).roundToInt() else 0

        val isFoldable = isFoldableDevice(context)
        val isBothDimensionsLarge = (availableWidthDp >= 500 && availableHeightDp >= 500)
            || (isFoldable && availableWidthDp >= 500 && availableHeightDp >= 450)
        val isSmallestWidthLarge = config.smallestScreenWidthDp >= 600

        // For phones (including foldables when folded in landscape or portrait):
        // neither both dimensions are large nor is the smallest screen width >= 600.
        if (!isBothDimensionsLarge && !isSmallestWidthLarge) {
            return ScreenProfile.COMPACT
        }

        val profile = ScreenProfile.LARGE

        Log.i("ScreenProfile", "getScreenProfile -> $profile (screenWidthDp=${config.screenWidthDp}, smallestWidthDp=${config.smallestScreenWidthDp}, calculatedDp=$calculatedDp)")

        cachedProfile = profile
        cachedConfigHash = currentHash
        return profile
    }

    fun invalidateCache() {
        cachedProfile = null
        cachedConfigHash = 0
        isFoldableDeviceCached = null
        isFoldableDeviceOverride = null
    }
}
