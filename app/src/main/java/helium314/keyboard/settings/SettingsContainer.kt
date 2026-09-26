// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.R
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.screens.createAboutSettings
import helium314.keyboard.settings.screens.createAdvancedSettings
import helium314.keyboard.settings.screens.createAppearanceSettings
import helium314.keyboard.settings.screens.createBackgroundServicesSettings
import helium314.keyboard.settings.screens.createCorrectionSettings
import helium314.keyboard.settings.screens.createSuggestionsSettings
import helium314.keyboard.settings.screens.createGestureTypingSettings
import helium314.keyboard.settings.screens.createLanguageSettings
import helium314.keyboard.settings.screens.createLayoutSettings
import helium314.keyboard.settings.screens.createOcrSettings
import helium314.keyboard.settings.screens.createPreferencesSettings
import helium314.keyboard.settings.screens.createSoundSettings
import helium314.keyboard.settings.screens.createTextExpanderSettings
import helium314.keyboard.settings.screens.createToolbarSettings
import helium314.keyboard.latin.voice.createVoiceSettings

class SettingsContainer(context: Context) {
    private val list = createSettings(context)
    private val map: Map<String, Setting> = HashMap<String, Setting>(list.size).apply {
        list.forEach {
            putIfAbsent(it.key, it)
        }
    }

    operator fun get(key: Any): Setting? = map[key]

    // filtering could be more elaborate, but should be good enough for a start
    // always have all settings in search, because:
    //  don't show disabled settings -> users confused
    //  show as disabled (i.e. no interaction possible) -> users confused
    //  show, but change will not do anything because another setting needs to be enabled first -> probably best
    fun filter(searchTerm: String): List<Setting> {
        val term = searchTerm.lowercase()
        val titleMatch = mutableListOf<Setting>()
        val titleWordMatch = mutableListOf<Setting>()
        val descriptionMatch = mutableListOf<Setting>()

        list.forEach { setting ->
            val titleLower = setting.titleLower
            if (titleLower.startsWith(term)) {
                titleMatch.add(setting)
            } else if (setting.titleWords.any { it.startsWith(term) }) {
                titleWordMatch.add(setting)
            } else if (setting.descriptionWords?.any { it.startsWith(term) } == true) {
                descriptionMatch.add(setting)
            }
        }

        return titleMatch + titleWordMatch + descriptionMatch
    }
}

@Immutable
class Setting(
    val key: String,
    val title: String,
    val description: String? = null,
    private val content: @Composable (Setting) -> Unit
) {
    constructor(
        context: Context,
        key: String,
        @StringRes titleId: Int,
        @StringRes descriptionId: Int? = null,
        content: @Composable (Setting) -> Unit
    ) : this(
        key = key,
        title = context.getString(titleId),
        description = descriptionId?.let { context.getString(it) },
        content = content
    )

    val titleLower = title.lowercase()
    val titleWords = titleLower.split(' ')
    val descriptionWords = description?.lowercase()?.split(' ')

    @Composable
    fun Preference() {
        content(this)
    }
}

/**
 * Single-Source-of-Truth Module definition for a Settings screen.
 * Automatically generates navigation cards and aggregates child settings for search indexing.
 */
class SettingsModule(
    val key: String,
    val destination: String,
    @StringRes val titleId: Int? = null,
    val titleString: String? = null,
    @DrawableRes val iconRes: Int? = null,
    val provider: ((Context) -> List<Setting>)? = null
) {
    fun getTitle(context: Context): String =
        titleString ?: (titleId?.let { context.getString(it) } ?: "")

    fun createNavigationSetting(context: Context): Setting {
        val title = getTitle(context)
        return Setting(
            key = key,
            title = title,
            content = {
                Preference(
                    name = title,
                    onClick = { SettingsDestination.navigateTo(destination) },
                    icon = iconRes
                ) { NextScreenIcon() }
            }
        )
    }
}

private val modules = listOf(
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_SECONDARY_LAYOUTS, SettingsDestination.Layouts, R.string.settings_screen_secondary_layouts, iconRes = R.drawable.ic_ime_switcher, provider = ::createLayoutSettings),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_LANGUAGES_AND_LAYOUTS, SettingsDestination.Languages, R.string.language_and_layouts_title, iconRes = R.drawable.ic_settings_languages, provider = ::createLanguageSettings),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_LANGUAGES, SettingsDestination.LanguagesList, R.string.languages_title, iconRes = R.drawable.ic_settings_languages),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_PREFERENCES, SettingsDestination.Preferences, R.string.settings_screen_preferences, iconRes = R.drawable.ic_settings_preferences, provider = ::createPreferencesSettings),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_APPEARANCE, SettingsDestination.Appearance, R.string.settings_screen_appearance, iconRes = R.drawable.ic_settings_appearance, provider = ::createAppearanceSettings),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_TOOLBAR, SettingsDestination.Toolbar, R.string.settings_screen_toolbar, iconRes = R.drawable.ic_settings_toolbar, provider = ::createToolbarSettings),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_GESTURES, SettingsDestination.GestureTyping, R.string.settings_screen_gesture, iconRes = R.drawable.ic_settings_gesture, provider = ::createGestureTypingSettings),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_TEXT_CORRECTION, SettingsDestination.TextCorrection, R.string.settings_screen_correction, iconRes = R.drawable.ic_settings_correction, provider = ::createCorrectionSettings),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_SUGGESTIONS, SettingsDestination.Suggestions, R.string.settings_screen_suggestions, iconRes = R.drawable.ic_settings_suggestions, provider = ::createSuggestionsSettings),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_AI_INTEGRATION, SettingsDestination.AIIntegration, R.string.settings_screen_ai_integration, iconRes = R.drawable.ic_proofread),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_TEXT_EXPANDER, SettingsDestination.TextExpander, titleString = "Text Expander", iconRes = R.drawable.ic_edit, provider = ::createTextExpanderSettings),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_ADVANCED, SettingsDestination.Advanced, R.string.settings_screen_advanced, iconRes = R.drawable.ic_settings_advanced, provider = ::createAdvancedSettings),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_ABOUT, SettingsDestination.About, R.string.settings_screen_about, iconRes = R.drawable.ic_settings_about, provider = ::createAboutSettings),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_OCR, SettingsDestination.OCR, R.string.ocr_settings_title, iconRes = R.drawable.ic_ocr, provider = ::createOcrSettings),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_LIBRARIES, SettingsDestination.Libraries, R.string.libraries_hub_title, iconRes = R.drawable.ic_emoji_objects),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_BACKGROUND_SERVICES, SettingsDestination.BackgroundServices, titleString = "Background Services", provider = ::createBackgroundServicesSettings),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_DICTIONARIES, SettingsDestination.Dictionaries, R.string.dictionary_settings_category, iconRes = R.drawable.ic_dictionary),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_PERSONAL_DICTIONARIES, SettingsDestination.PersonalDictionaries, R.string.edit_personal_dictionary, iconRes = R.drawable.ic_dictionary),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_BLOCKED_WORDS, SettingsDestination.BlockedWords, R.string.edit_blocked_words, iconRes = R.drawable.ic_bin),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_SOUND, SettingsDestination.Sound, R.string.sound_packs_title, iconRes = R.drawable.ic_play_arrow, provider = ::createSoundSettings),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_VOICE, SettingsDestination.OfflineVoice, R.string.voice_input_title, iconRes = R.drawable.sym_keyboard_voice_holo, provider = ::createVoiceSettings),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_TRANSLATION, SettingsDestination.Translation, R.string.translation_settings_title, iconRes = R.drawable.ic_translate),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_HANDWRITING, SettingsDestination.Handwriting, R.string.libraries_hub_handwriting_title, iconRes = R.drawable.ic_edit),
    SettingsModule(SettingsWithoutKey.SCREEN_NAV_UPDATES, SettingsDestination.Updates, R.string.settings_screen_updates, iconRes = R.drawable.ic_settings_updates)
)

private fun createSettings(context: Context): List<Setting> = buildList {
    modules.forEach { module ->
        add(module.createNavigationSetting(context))
        module.provider?.invoke(context)?.let { addAll(it) }
    }
}

object SettingsWithoutKey {
    const val EDIT_PERSONAL_DICTIONARY = "edit_personal_dictionary"
    const val APP = "app"
    const val VERSION = "version"
    const val LICENSE = "license"
    const val HIDDEN_FEATURES = "hidden_features"
    const val GITHUB = "github"
    const val SPONSOR = "sponsor"
    const val GITHUB_FEATURES = "github_features"
    const val SAVE_LOG = "save_log"
    const val BACKUP_RESTORE = "backup_restore"
    const val PERSIST_FLOATING_KEYBOARD = "persist_floating_keyboard"
    const val REMEMBER_FLOATING_KEYBOARD = "remember_floating_keyboard"
    const val DEBUG_SETTINGS = "screen_debug"
    const val LOAD_GESTURE_LIB = "load_gesture_library"
    const val BACKGROUND_IMAGE = "background_image"
    const val BACKGROUND_IMAGE_LANDSCAPE = "background_image_landscape"
    const val CUSTOM_FONT = "custom_font"
    const val CUSTOM_EMOJI_FONT = "custom_emoji_font"
    const val GEMINI_API_KEY = "gemini_api_key"
    const val GEMINI_MODEL = "gemini_model"
    const val GEMINI_TARGET_LANGUAGE = "gemini_target_language"
    const val TRANSLATION_SOURCE_LANGUAGE = "pref_translation_source_language"
    const val TRANSLATE_GROQ_MODEL = "translate_groq_model"
    const val TRANSLATE_GEMINI_MODEL = "translate_gemini_model"
    const val TRANSLATE_HUGGINGFACE_MODEL = "translate_huggingface_model"
    const val VOICE_GROQ_MODEL = "voice_groq_model"
    const val VOICE_GEMINI_MODEL = "voice_gemini_model"
    const val VOICE_HUGGINGFACE_MODEL = "voice_huggingface_model"
    const val OFFLINE_MODEL_PATH = "offline_model_path"
    const val AI_PROVIDER = "ai_provider"
    const val GROQ_TOKEN = "groq_token"
    const val HUGGINGFACE_TOKEN = "huggingface_token"
    const val HUGGINGFACE_MODEL = "huggingface_model"
    const val HUGGINGFACE_ENDPOINT = "huggingface_endpoint"
    const val GROQ_MODEL = "groq_model"
    const val CUSTOM_AI_KEYS = "custom_ai_keys"
    const val OFFLINE_KEEP_MODEL_LOADED = "offline_keep_model_loaded"
    const val AI_ALLOW_INSECURE_CONNECTIONS = "ai_allow_insecure_connections"
    const val TRANSLATION_ENGINE = "pref_translation_method"
    const val LOAD_OFFLINE_AI_PLUGIN = "load_offline_ai_plugin"
    const val BACKGROUND_SERVICES = "background_services"
    const val CLOUD_AI_MAX_TOKENS = "cloud_ai_max_tokens"
    const val APP_QUIRKS = "app_quirks"

    // Screen Navigation Keys for Settings Search:
    const val SCREEN_NAV_SECONDARY_LAYOUTS = "screen_nav_secondary_layouts"
    const val SCREEN_NAV_LANGUAGES = "screen_nav_languages"
    const val SCREEN_NAV_LANGUAGES_AND_LAYOUTS = "screen_nav_languages_and_layouts"
    const val SCREEN_NAV_PREFERENCES = "screen_nav_preferences"
    const val SCREEN_NAV_APPEARANCE = "screen_nav_appearance"
    const val SCREEN_NAV_TOOLBAR = "screen_nav_toolbar"
    const val SCREEN_NAV_GESTURES = "screen_nav_gestures"
    const val SCREEN_NAV_TEXT_CORRECTION = "screen_nav_text_correction"
    const val SCREEN_NAV_SUGGESTIONS = "screen_nav_suggestions"
    const val SCREEN_NAV_AI_INTEGRATION = "screen_nav_ai_integration"
    const val SCREEN_NAV_TEXT_EXPANDER = "screen_nav_text_expander"
    const val SCREEN_NAV_ADVANCED = "screen_nav_advanced"
    const val SCREEN_NAV_ABOUT = "screen_nav_about"
    const val SCREEN_NAV_LIBRARIES = "screen_nav_libraries"
    const val SCREEN_NAV_OCR = "screen_nav_ocr"
    const val SCREEN_NAV_DICTIONARIES = "screen_nav_dictionaries"
    const val SCREEN_NAV_BACKGROUND_SERVICES = "screen_nav_background_services"
    const val SCREEN_NAV_SOUND = "screen_nav_sound"
    const val SCREEN_NAV_VOICE = "screen_nav_voice"
    const val SCREEN_NAV_TRANSLATION = "screen_nav_translation"
    const val SCREEN_NAV_HANDWRITING = "screen_nav_handwriting"
    const val SCREEN_NAV_UPDATES = "screen_nav_updates"
    const val SCREEN_NAV_PERSONAL_DICTIONARIES = "screen_nav_personal_dictionaries"
    const val SCREEN_NAV_BLOCKED_WORDS = "screen_nav_blocked_words"
}
