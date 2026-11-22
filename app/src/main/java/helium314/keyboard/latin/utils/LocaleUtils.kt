package helium314.keyboard.latin.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import helium314.keyboard.compat.locale
import helium314.keyboard.latin.R
import java.util.Locale

object LocaleUtils {
    fun parseLocale(localeTag: String): Locale {
        return if (localeTag.contains("-")) {
            val parts = localeTag.split("-")
            Locale(parts[0], parts[1])
        } else {
            Locale(localeTag)
        }
    }

    fun wrapContextWithLocale(context: Context, localeTag: String): Context {
        val config = Configuration(context.resources.configuration)
        if (localeTag.isEmpty() || localeTag == "system") {
            val systemConfig = Resources.getSystem().configuration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(systemConfig.locales)
            } else {
                @Suppress("DEPRECATION")
                config.locale = systemConfig.locale
            }
        } else {
            val locale = parseLocale(localeTag)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val localeList = android.os.LocaleList(locale)
                config.setLocales(localeList)
            } else {
                @Suppress("DEPRECATION")
                config.locale = locale
            }
        }
        return context.createConfigurationContext(config)
    }

    fun applyAppLanguageToResources(context: Context, localeTag: String) {
        val res = context.resources
        val config = Configuration(res.configuration)
        if (localeTag.isEmpty() || localeTag == "system") {
            val systemConfig = Resources.getSystem().configuration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(systemConfig.locales)
            } else {
                @Suppress("DEPRECATION")
                config.locale = systemConfig.locale
            }
        } else {
            val locale = parseLocale(localeTag)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(android.os.LocaleList(locale))
            } else {
                @Suppress("DEPRECATION")
                config.locale = locale
            }
        }
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)
        try {
            val appRes = context.applicationContext.resources
            if (appRes != null && appRes !== res) {
                @Suppress("DEPRECATION")
                appRes.updateConfiguration(config, appRes.displayMetrics)
            }
        } catch (_: Throwable) {}
    }

    val localeCodes = listOf(
        "en", "af", "ar", "be", "bg", "bn", "ca", "cs", "da", "de", "dv", "el", "es", "es-US", "et", "eu", "fa", "fi", "fr", "gl", "hi", "hr", "hu", "in", "is", "it", "iw", "ja", "ka", "ko", "lt", "ms", "my", "nb", "nl", "pl", "pt", "pt-BR", "pt-PT", "ro", "ru", "sl", "sr", "sv", "ta", "tr", "uk", "ur", "uz", "vi", "zh-CN", "zh-TW"
    )

    fun getAppLanguageItems(context: Context): List<Pair<String, String>> {
        val items = mutableListOf<Pair<String, String>>()
        items.add(context.getString(R.string.app_language_system) to "")
        for (code in localeCodes) {
            val locale = parseLocale(code)
            val name = locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }
            items.add(name to code)
        }
        return items
    }
}
