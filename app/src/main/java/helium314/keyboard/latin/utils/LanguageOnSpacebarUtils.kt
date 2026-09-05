/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import android.view.inputmethod.InputMethodSubtype
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.settings.Settings
import java.util.Locale

/**
 * This class determines that the language name on the spacebar should be displayed in what format.
 */
object LanguageOnSpacebarUtils {
    const val FORMAT_TYPE_NONE = 0
    const val FORMAT_TYPE_LANGUAGE_ONLY = 1
    const val FORMAT_TYPE_FULL_LOCALE = 2

    private var sEnabledSubtypes: List<InputMethodSubtype> = emptyList()
    private var sIsSystemLanguageSameAsInputLanguage = false

    @JvmStatic
    fun getLanguageOnSpacebarFormatType(subtype: RichInputMethodSubtype): Int {
        if (Settings.getValues().mSpaceBarText.isNotEmpty()) {
            return FORMAT_TYPE_FULL_LOCALE
        }
        if (subtype.isNoLanguage) {
            return FORMAT_TYPE_FULL_LOCALE
        }
        // Only this subtype is enabled and equals to the system locale.
        if (sEnabledSubtypes.size < 2 && sIsSystemLanguageSameAsInputLanguage && Settings.getValues().mSecondaryLocales.isEmpty()) {
            return FORMAT_TYPE_NONE
        }
        val locale = subtype.locale
        val keyboardLanguage = locale.language
        val keyboardLayout = subtype.mainLayoutName
        var sameLanguageAndLayoutCount = 0
        for (ims in sEnabledSubtypes) {
            val language = ims.locale().language
            if (keyboardLanguage == language && keyboardLayout == ims.mainLayoutNameOrQwerty()) {
                sameLanguageAndLayoutCount++
            }
        }
        // Display full locale name only when there are multiple subtypes that have the same
        // locale and keyboard layout. Otherwise displaying language name is enough.
        return if (sameLanguageAndLayoutCount > 1) FORMAT_TYPE_FULL_LOCALE else FORMAT_TYPE_LANGUAGE_ONLY
    }

    @JvmStatic
    fun setEnabledSubtypes(enabledSubtypes: List<InputMethodSubtype>) {
        sEnabledSubtypes = enabledSubtypes
    }

    @JvmStatic
    fun onSubtypeChanged(
        subtype: RichInputMethodSubtype,
        implicitlyEnabledSubtype: Boolean,
        systemLocale: Locale
    ) {
        val newLocale = subtype.locale
        if (systemLocale == newLocale) {
            sIsSystemLanguageSameAsInputLanguage = true
            return
        }
        if (systemLocale.language != newLocale.language) {
            sIsSystemLanguageSameAsInputLanguage = false
            return
        }
        // If the subtype is enabled explicitly, the language name should be displayed even when
        // the keyboard language and the system language are equal.
        sIsSystemLanguageSameAsInputLanguage = implicitlyEnabledSubtype
    }
}
