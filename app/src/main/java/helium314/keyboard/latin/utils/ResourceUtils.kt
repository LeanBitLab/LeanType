/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.TypedArray
import android.os.Build
import android.util.TypedValue
import android.view.WindowInsets
import android.view.WindowManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.SettingsValues

object ResourceUtils {

    const val UNDEFINED_RATIO = -1.0f
    const val UNDEFINED_DIMENSION = -1

    /**
     * When > 0, overrides the keyboard width for floating keyboard mode.
     * Set by FloatingKeyboardManager before triggering a keyboard reload.
     */
    private var sFloatingKeyboardWidthOverride = 0

    @JvmStatic
    fun setFloatingKeyboardWidth(widthPx: Int) {
        sFloatingKeyboardWidthOverride = widthPx
    }

    @JvmStatic
    fun getFloatingKeyboardWidth(): Int {
        return sFloatingKeyboardWidthOverride
    }

    private var sFloatingKeyboardScaleOverride = 0.0f

    @JvmStatic
    fun setFloatingKeyboardScale(scale: Float) {
        sFloatingKeyboardScaleOverride = scale
    }

    @JvmStatic
    fun getFloatingKeyboardScale(): Float {
        return sFloatingKeyboardScaleOverride
    }

    @JvmStatic
    fun getKeyboardWidth(ctx: Context, settingsValues: SettingsValues): Int {
        // Floating keyboard width takes priority
        if (sFloatingKeyboardWidthOverride > 0) {
            return sFloatingKeyboardWidthOverride
        }
        val defaultKeyboardWidth = getDefaultKeyboardWidth(ctx)
        if (settingsValues.mOneHandedModeEnabled) {
            return (settingsValues.mOneHandedModeScale * defaultKeyboardWidth).toInt()
        }
        return defaultKeyboardWidth
    }

    @JvmStatic
    fun getDefaultKeyboardWidth(ctx: Context): Int {
        if (Build.VERSION.SDK_INT < 35) {
            val dm = ctx.resources.displayMetrics
            return dm.widthPixels
        }
        // Since Android 15, insets aren't subtracted from DisplayMetrics.widthPixels, despite
        // targetSdk remaining set to 30.
        val wm = ctx.getSystemService(WindowManager::class.java)
        val windowMetrics = wm.currentWindowMetrics
        val windowBounds = windowMetrics.bounds
        val windowInsets = windowMetrics.windowInsets
        val insetTypes = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        val insets = windowInsets.getInsetsIgnoringVisibility(insetTypes)
        return windowBounds.width() - insets.left - insets.right
    }

    @JvmStatic
    fun getSuggestionsStripHeight(res: Resources): Int {
        val defaultHeight = res.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        if (sFloatingKeyboardScaleOverride > 0.0f) {
            return Math.max(
                (defaultHeight * sFloatingKeyboardScaleOverride).toInt(),
                (18 * res.displayMetrics.density).toInt()
            )
        }
        return defaultHeight
    }

    @JvmStatic
    fun getSecondaryKeyboardHeight(res: Resources, settingsValues: SettingsValues): Int {
        val keyboardHeight = getKeyboardHeight(res, settingsValues)
        if (settingsValues.mToolbarMode == ToolbarMode.HIDDEN && !settingsValues.mToolbarHidingGlobal) {
            // Small adjustment to match the height of the main keyboard which has a hidden strip container.
            return keyboardHeight - getSuggestionsStripHeight(res)
        }
        return keyboardHeight
    }

    @JvmStatic
    fun getKeyboardHeight(res: Resources, settingsValues: SettingsValues): Int {
        val defaultKeyboardHeight = getDefaultKeyboardHeight(res, settingsValues.mShowsNumberRow)
        var scale = settingsValues.mKeyboardHeightScale
        if (sFloatingKeyboardScaleOverride > 0.0f) {
            scale *= sFloatingKeyboardScaleOverride
        }
        // mKeyboardHeightScale Ranges from [.5,1.5], from xml/prefs_screen_appearance.xml
        return (defaultKeyboardHeight * scale).toInt()
    }

    @JvmStatic
    fun getOcrCameraHeight(res: Resources, settingsValues: SettingsValues?): Int {
        val baseHeight = if (settingsValues != null) {
            getKeyboardHeight(res, settingsValues)
        } else {
            getDefaultKeyboardHeight(res, false)
        }
        val dm = res.displayMetrics
        val isLandscape = res.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val multiplier = if (isLandscape) 1.25f else 1.40f
        val maxAllowed = (dm.heightPixels * if (isLandscape) 0.70f else 0.50f).toInt()
        return Math.min((baseHeight * multiplier).toInt(), maxAllowed)
    }

    @JvmStatic
    fun getDefaultKeyboardHeight(res: Resources, showsNumberRow: Boolean): Int {
        val dm = res.displayMetrics
        val keyboardHeight = res.getDimension(R.dimen.config_default_keyboard_height) * (if (showsNumberRow) 1.33f else 1f)
        val maxKeyboardHeight = res.getFraction(
            R.fraction.config_max_keyboard_height, dm.heightPixels, dm.heightPixels
        )
        var minKeyboardHeight = res.getFraction(
            R.fraction.config_min_keyboard_height, dm.heightPixels, dm.heightPixels
        )
        if (minKeyboardHeight < 0.0f) {
            // Specified fraction was negative, so it should be calculated against display width.
            minKeyboardHeight = -res.getFraction(
                R.fraction.config_min_keyboard_height, dm.widthPixels, dm.widthPixels
            )
        }
        // Keyboard height will not exceed maxKeyboardHeight and will not be less than minKeyboardHeight.
        return Math.max(Math.min(keyboardHeight, maxKeyboardHeight), minKeyboardHeight).toInt()
    }

    @JvmStatic
    fun isValidFraction(fraction: Float): Boolean {
        return fraction >= 0.0f
    }

    @JvmStatic
    fun isValidDimensionPixelSize(dimension: Int): Boolean {
        return dimension > 0
    }

    @JvmStatic
    fun getFraction(a: TypedArray, index: Int, defValue: Float): Float {
        val value = a.peekValue(index)
        if (value == null || !isFractionValue(value)) {
            return defValue
        }
        return a.getFraction(index, 1, 1, defValue)
    }

    @JvmStatic
    fun getFraction(a: TypedArray, index: Int): Float {
        return getFraction(a, index, UNDEFINED_RATIO)
    }

    @JvmStatic
    fun getDimensionPixelSize(a: TypedArray, index: Int): Int {
        val value = a.peekValue(index)
        if (value == null || !isDimensionValue(value)) {
            return UNDEFINED_DIMENSION
        }
        return a.getDimensionPixelSize(index, UNDEFINED_DIMENSION)
    }

    @JvmStatic
    fun getDimensionOrFraction(
        a: TypedArray,
        index: Int,
        base: Int,
        defValue: Float
    ): Float {
        val value = a.peekValue(index) ?: return defValue
        if (isFractionValue(value)) {
            return a.getFraction(index, base, base, defValue)
        } else if (isDimensionValue(value)) {
            return a.getDimension(index, defValue)
        }
        return defValue
    }

    @JvmStatic
    fun isFractionValue(v: TypedValue): Boolean {
        return v.type == TypedValue.TYPE_FRACTION
    }

    @JvmStatic
    fun isDimensionValue(v: TypedValue): Boolean {
        return v.type == TypedValue.TYPE_DIMENSION
    }

    @JvmStatic
    fun isNight(res: Resources): Boolean {
        return (res.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
