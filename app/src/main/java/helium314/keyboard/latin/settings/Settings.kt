/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.settings

import android.provider.Settings.Global
import kotlinx.serialization.json.Json
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import helium314.keyboard.compat.locale
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.utils.BitmapUtils
import helium314.keyboard.latin.utils.CenterCropDrawable
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.runInLocale
import helium314.keyboard.latin.utils.ScriptUtils

import helium314.keyboard.latin.utils.StatsUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.clearCustomToolbarKeyCodes
import helium314.keyboard.latin.utils.getCustomKeyCode
import helium314.keyboard.latin.utils.getCustomLongpressKeyCode
import helium314.keyboard.latin.utils.ToolbarMode
import java.io.File
import java.util.Locale
import helium314.keyboard.latin.settings.Defaults.default
import java.util.concurrent.locks.ReentrantLock

class Settings private constructor() : SharedPreferences.OnSharedPreferenceChangeListener {
    private var mContext: Context? = null
    private var mPrefs: SharedPreferences? = null
    private var mSettingsValues: SettingsValues? = null
    private val mSettingsValuesLock = ReentrantLock()

    private fun onCreate(context: Context) {
        mContext = context
        val prefs = context.prefs()
        mPrefs = prefs
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    fun onDestroy() {
        mPrefs?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (dontReloadOnChanged.contains(key) || (key != null && key.startsWith(PREF_SAVED_APP_SUBTYPE_PREFIX))) return
        mSettingsValuesLock.lock()
        try {
            val context = mContext
            val currentValues = mSettingsValues
            if (context == null || currentValues == null) {
                Log.w(TAG, "onSharedPreferenceChanged called before loadSettings.")
                return
            }
            clearCustomToolbarKeyCodes()
            loadSettings(context, currentValues.mLocale, currentValues.mInputAttributes, currentValues.mCurrentKeyboardScript)
            StatsUtils.onLoadSettings(currentValues)
            helium314.keyboard.latin.LatinIME.sSettingsDirty = true
        } finally {
            mSettingsValuesLock.unlock()
        }
        if (PREF_ADDITIONAL_SUBTYPES == key) {
            mContext?.let { SubtypeSettings.reloadEnabledSubtypes(it) }
        }
    }

    fun loadSettings(context: Context) {
        if (mSettingsValues != null) return
        val locale = context.resources.configuration.locale()
        val inputAttributes = InputAttributes(EditorInfo(), false, context.packageName)
        loadSettings(context, locale, inputAttributes, ScriptUtils.SCRIPT_UNKNOWN)
    }

    fun loadSettings(context: Context, locale: Locale?, inputAttributes: InputAttributes, currentKeyboardScript: String) {
        mSettingsValuesLock.lock()
        mContext = context
        try {
            val prefs = mPrefs ?: context.prefs().also { mPrefs = it }
            Log.i(TAG, "loadSettings")
            val actualLocale = locale ?: context.resources.configuration.locale()
            mSettingsValues = runInLocale(context, actualLocale) { ctx ->
                SettingsValues(ctx, prefs, ctx.resources, inputAttributes, currentKeyboardScript)
            }
        } finally {
            mSettingsValuesLock.unlock()
        }
    }

    fun stopListener() {
        mPrefs?.unregisterOnSharedPreferenceChangeListener(this)
    }

    fun startListener() {
        mPrefs?.registerOnSharedPreferenceChangeListener(this)
    }

    val current: SettingsValues
        get() = mSettingsValues ?: getValues()

    fun toggleAutoCorrect() {
        val prefs = mPrefs ?: return
        val oldValue = prefs.getBoolean(PREF_AUTO_CORRECTION, Defaults.PREF_AUTO_CORRECTION)
        prefs.edit().putBoolean(PREF_AUTO_CORRECTION, !oldValue).apply()
    }

    fun toggleAlwaysIncognitoMode() {
        val prefs = mPrefs ?: return
        val oldValue = prefs.getBoolean(PREF_ALWAYS_INCOGNITO_MODE, Defaults.PREF_ALWAYS_INCOGNITO_MODE)
        prefs.edit().putBoolean(PREF_ALWAYS_INCOGNITO_MODE, !oldValue).apply()
    }

    fun writeOneHandedModeEnabled(enabled: Boolean) {
        val settingsValues = mSettingsValues ?: return
        val prefs = mPrefs ?: return
        val landscape = settingsValues.mDisplayOrientation == Configuration.ORIENTATION_LANDSCAPE
        val index = findIndexOfDefaultSetting(landscape, settingsValues.mIsSplitKeyboardEnabled)
        val key = createPrefKeyForBooleanSettings(PREF_ONE_HANDED_MODE_PREFIX, index, 2)
        prefs.edit().putBoolean(key, enabled).apply()
    }

    fun writeOneHandedModeScale(scale: Float?) {
        if (scale == null) return
        val settingsValues = mSettingsValues ?: return
        val prefs = mPrefs ?: return
        val landscape = settingsValues.mDisplayOrientation == Configuration.ORIENTATION_LANDSCAPE
        val index = findIndexOfDefaultSetting(landscape, settingsValues.mIsSplitKeyboardEnabled)
        val key = createPrefKeyForBooleanSettings(PREF_ONE_HANDED_SCALE_PREFIX, index, 2)
        prefs.edit().putFloat(key, scale).apply()
    }

    fun writeOneHandedModeGravity(gravity: Int) {
        val settingsValues = mSettingsValues ?: return
        val prefs = mPrefs ?: return
        val landscape = settingsValues.mDisplayOrientation == Configuration.ORIENTATION_LANDSCAPE
        val index = findIndexOfDefaultSetting(landscape, settingsValues.mIsSplitKeyboardEnabled)
        val key = createPrefKeyForBooleanSettings(PREF_ONE_HANDED_GRAVITY_PREFIX, index, 2)
        prefs.edit().putInt(key, gravity).apply()
    }

    fun writeSplitKeyboardEnabled(enabled: Boolean, isLandscape: Boolean) {
        val settingsValues = mSettingsValues ?: return
        val prefs = mPrefs ?: return
        val basePref = if (isLandscape) PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE else PREF_ENABLE_SPLIT_KEYBOARD
        val profilePref = getProfileAwarePrefKey(basePref, settingsValues.mScreenProfile)
        prefs.edit().putBoolean(profilePref, enabled).apply()
    }

    fun readShowToolbarOnly(): Boolean {
        val settingsValues = mSettingsValues ?: return false
        val prefs = mPrefs ?: return false
        return settingsValues.mHasHardwareKeyboard && prefs.getBoolean(PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD, Defaults.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD)
    }

    fun readClipboardHistoryPinnedFirst(): Boolean {
        val prefs = mPrefs ?: mContext?.prefs() ?: return Defaults.PREF_CLIPBOARD_HISTORY_PINNED_FIRST
        return prefs.getBoolean(PREF_CLIPBOARD_HISTORY_PINNED_FIRST, Defaults.PREF_CLIPBOARD_HISTORY_PINNED_FIRST)
    }

    fun readClipboardFoldPinned(): Boolean {
        val prefs = mPrefs ?: mContext?.prefs() ?: return Defaults.PREF_CLIPBOARD_FOLD_PINNED
        return prefs.getBoolean(PREF_CLIPBOARD_FOLD_PINNED, Defaults.PREF_CLIPBOARD_FOLD_PINNED)
    }

    val isTablet: Boolean
        get() = mContext?.resources?.getInteger(R.integer.config_screen_metrics)?.let { it >= 3 } ?: false

    @SuppressLint("DiscouragedApi")
    fun getStringResIdByName(name: String): Int {
        val context = mContext ?: return 0
        return context.resources.getIdentifier(name, "string", context.packageName)
    }

    fun getInLocale(@StringRes resId: Int, locale: java.util.Locale): String {
        val context = mContext ?: return ""
        return runInLocale(context, locale) { ctx -> ctx.getString(resId) }
    }

    fun readCustomCurrencyKey(): String {
        return mPrefs?.getString(PREF_CUSTOM_CURRENCY_KEY, Defaults.PREF_CUSTOM_CURRENCY_KEY) ?: Defaults.PREF_CUSTOM_CURRENCY_KEY
    }

    fun getCustomToolbarKeyCode(key: ToolbarKey): Int? {
        val prefs = mPrefs ?: return null
        return getCustomKeyCode(key, prefs)
    }

    fun getCustomToolbarLongpressCode(key: ToolbarKey): Int? {
        val prefs = mPrefs ?: return null
        return getCustomLongpressKeyCode(key, prefs)
    }

    fun saveSubtypeForApp(subtype: RichInputMethodSubtype, packageName: String?) {
        if (isSubtypePerApp() && !packageName.isNullOrEmpty()) {
            mPrefs?.edit()?.putString(PREF_SAVED_APP_SUBTYPE_PREFIX + packageName, subtype.rawSubtype.toSettingsSubtype().toPref())?.apply()
        }
    }

    fun getSubtypeForApp(packageName: String?): RichInputMethodSubtype? {
        if (!isSubtypePerApp() || packageName.isNullOrEmpty()) return null
        val subtypePref = mPrefs?.getString(PREF_SAVED_APP_SUBTYPE_PREFIX + packageName, null) ?: return null
        val settingsSubtype = subtypePref.toSettingsSubtype()
        var subtype = settingsSubtype.toEnabledSubtype()
        if (subtype == null) {
            subtype = RichInputMethodManager.getInstance().findSubtypeForHintLocale(settingsSubtype.locale)
        }
        return subtype?.let { RichInputMethodSubtype.get(it) }
    }

    private fun isSubtypePerApp(): Boolean {
        return mPrefs?.getBoolean(PREF_SAVE_SUBTYPE_PER_APP, Defaults.PREF_SAVE_SUBTYPE_PER_APP) ?: Defaults.PREF_SAVE_SUBTYPE_PER_APP
    }

    fun useSystemEmoji(): Boolean {
        return mPrefs?.getBoolean(PREF_USE_SYSTEM_EMOJI, Defaults.PREF_USE_SYSTEM_EMOJI) ?: Defaults.PREF_USE_SYSTEM_EMOJI
    }

    @get:JvmName("customTypefaceProperty")
    val customTypeface: Typeface?
        get() = getCustomTypeface()

    fun getCustomTypeface(): Typeface? {
        if (!sCustomTypefaceLoaded) {
            val context = mContext
            if (context != null) {
                try {
                    sCachedTypeface = Typeface.createFromFile(getCustomFontFile(context))
                } catch (ignored: Exception) {
                }
            }
        }
        sCustomTypefaceLoaded = true
        return sCachedTypeface
    }

    @get:JvmName("customEmojiTypefaceProperty")
    val customEmojiTypeface: Typeface?
        get() = getCustomEmojiTypeface()

    fun getCustomEmojiTypeface(): Typeface? {
        if (useSystemEmoji()) return null
        if (!sCustomEmojiTypefaceLoaded) {
            val context = mContext
            if (context != null) {
                try {
                    sCachedEmojiTypeface = Typeface.createFromFile(getCustomEmojiFontFile(context))
                } catch (ignored: Exception) {
                }
            }
        }
        sCustomEmojiTypefaceLoaded = true
        return sCachedEmojiTypeface
    }

    companion object {
        private const val TAG = "Settings"

        const val PREF_THEME_STYLE = "theme_style"
        const val PREF_ICON_STYLE = "icon_style"
        const val PREF_THEME_COLORS = "theme_colors"
        const val PREF_THEME_COLORS_NIGHT = "theme_colors_night"
        const val PREF_THEME_KEY_BORDERS = "theme_key_borders"
        const val PREF_KEY_BORDER_RADIUS = "key_border_radius"
        const val PREF_KEY_BORDER_RADIUS_FUNCTIONAL = "key_border_radius_functional"
        const val PREF_THEME_DAY_NIGHT = "theme_auto_day_night"
        const val PREF_USER_COLORS_PREFIX = "user_colors_"
        const val PREF_USER_ALL_COLORS_PREFIX = "user_all_colors_"
        const val PREF_USER_MORE_COLORS_PREFIX = "user_more_colors_"

        const val PREF_CUSTOM_ICON_NAMES = "custom_icon_names"
        const val PREF_TOOLBAR_CUSTOM_KEY_CODES = "toolbar_custom_key_codes"
        const val PREF_LAYOUT_PREFIX = "layout_"

        const val PREF_AUTO_CAP = "auto_cap"
        const val PREF_VIBRATE_ON = "vibrate_on"
        const val PREF_VIBRATE_IN_DND_MODE = "vibrate_in_dnd_mode"
        const val PREF_SOUND_ON = "sound_on"
        const val PREF_SUGGEST_EMOJIS = "suggest_emojis"
        const val PREF_INLINE_EMOJI_SEARCH = "inline_emoji_search"
        const val PREF_SHOW_EMOJI_DESCRIPTIONS = "show_emoji_descriptions"
        const val PREF_POPUP_ON = "popup_on"
        const val PREF_AUTO_CORRECTION = "auto_correction"
        const val PREF_AUTO_CORRECT_TRIGGER = "auto_correction_trigger"
        const val PREF_MORE_AUTO_CORRECTION = "more_auto_correction"
        const val PREF_AUTO_CORRECT_THRESHOLD = "auto_correct_threshold"
        const val PREF_AUTOCORRECT_SHORTCUTS = "autocorrect_shortcuts"
        const val PREF_BACKSPACE_REVERTS_AUTOCORRECT = "backspace_reverts_autocorrect"
        const val PREF_CENTER_SUGGESTION_TEXT_TO_ENTER = "center_suggestion_text_to_enter"
        const val PREF_SHOW_SUGGESTIONS = "show_suggestions"
        const val PREF_ALWAYS_SHOW_SUGGESTIONS = "always_show_suggestions"
        const val PREF_ALWAYS_SHOW_SUGGESTIONS_EXCEPT_WEB_TEXT = "always_show_suggestions_except_web_text"
        const val PREF_KEY_USE_PERSONALIZED_DICTS = "use_personalized_dicts"
        const val PREF_KEY_USE_DOUBLE_SPACE_PERIOD = "use_double_space_period"
        const val PREF_BLOCK_POTENTIALLY_OFFENSIVE = "block_potentially_offensive"
        const val PREF_SHOW_LANGUAGE_SWITCH_KEY = "show_language_switch_key"
        const val PREF_LANGUAGE_SWITCH_KEY = "language_switch_key"
        const val PREF_DIRECT_IME_SWITCH_TARGET = "direct_ime_switch_target"
        const val PREF_APP_LANGUAGE = "pref_app_language"
        const val PREF_SHOW_EMOJI_KEY = "show_emoji_key"
        const val PREF_VARIABLE_TOOLBAR_DIRECTION = "var_toolbar_direction"
        const val PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD = "only_toolbar_with_hw_keyboard"
        const val PREF_PHYSICAL_KEYBOARD_SUGGESTION_SHORTCUTS = "pref_physical_keyboard_suggestion_shortcuts"
        const val PREF_ADDITIONAL_SUBTYPES = "additional_subtypes"

        const val PREF_ENABLE_SPELL_CHECKER_SERVICE = "enable_spell_checker_service"
        const val PREF_ENABLE_CONTACTS_OBSERVER = "enable_contacts_observer"
        const val PREF_ENABLE_CLIPBOARD_LISTENER = "enable_clipboard_listener"
        const val PREF_ENABLE_SMS_OTP_RECEIVER = "enable_sms_otp_receiver"
        const val PREF_ENABLE_APP_SYNC_LISTENER = "enable_app_sync_listener"
        const val PREF_FOLDABLE_MODE = "pref_foldable_mode"
        const val PREF_ENABLE_SPLIT_KEYBOARD = "split_keyboard"
        const val PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE = "split_keyboard_landscape"
        const val PREF_SPLIT_SPACER_SCALE_PREFIX = "split_spacer_scale"
        const val PREF_KEYBOARD_HEIGHT_SCALE_PREFIX = "keyboard_height_scale"
        const val PREF_BOTTOM_PADDING_SCALE_PREFIX = "bottom_padding_scale"
        const val PREF_SIDE_PADDING_SCALE_PREFIX = "side_padding_scale"
        const val PREF_FONT_SCALE = "font_scale"
        const val PREF_EMOJI_FONT_SCALE = "emoji_font_scale"
        const val PREF_USE_SYSTEM_EMOJI = "use_system_emoji"
        const val PREF_EMOJI_KEY_FIT = "emoji_key_fit"
        const val PREF_EMOJI_SKIN_TONE = "emoji_skin_tone"
        const val PREF_SPACE_HORIZONTAL_SWIPE = "horizontal_space_swipe"
        const val PREF_SPACE_VERTICAL_SWIPE = "vertical_space_swipe"
        const val PREF_DELETE_SWIPE = "delete_swipe"
        const val PREF_AUTOSPACE_AFTER_PUNCTUATION = "autospace_after_punctuation"
        const val PREF_AUTOSPACE_AFTER_EMOJI = "autospace_after_emoji"
        const val PREF_AUTOSPACE_AFTER_SUGGESTION = "autospace_after_suggestion"
        const val PREF_AUTOSPACE_AFTER_GESTURE_TYPING = "autospace_after_gesture_typing"
        const val PREF_AUTOSPACE_BEFORE_GESTURE_TYPING = "autospace_before_gesture_typing"
        const val PREF_SHIFT_REMOVES_AUTOSPACE = "shift_removes_autospace"
        const val PREF_PRESERVE_SPACE_BEFORE_PUNCTUATION = "preserve_space_before_punctuation"
        const val PREF_ALWAYS_INCOGNITO_MODE = "always_incognito_mode"
        const val PREF_BIGRAM_PREDICTIONS = "next_word_prediction"
        const val PREF_PRIORITIZE_PERSONAL_SUGGESTIONS = "prioritize_personal_suggestions"
        const val PREF_SUGGESTION_BALANCE = "suggestion_balance"
        const val SUGGESTION_BALANCE_DICTIONARY_FOCUSED = 1
        const val SUGGESTION_BALANCE_CONSERVATIVE = 2
        const val SUGGESTION_BALANCE_BALANCED = 3
        const val SUGGESTION_BALANCE_PERSONALIZED = 4
        const val SUGGESTION_BALANCE_HIGHLY_PERSONALIZED = 5
        const val PREF_NEXT_WORD_BOOST_LEVEL = "next_word_boost_level"
        const val PREF_NEXT_WORD_STRICT_NGRAM = "next_word_strict_ngram"
        const val PREF_IMMEDIATE_AUTO_SPACE = "immediate_auto_space"
        const val PREF_FIRST_WORD_PREDICTIONS = "first_word_prediction"
        const val PREF_SUGGEST_PUNCTUATION = "suggest_punctuation"
        const val PREF_SUGGEST_CLIPBOARD_CONTENT = "suggest_clipboard_content"
        const val PREF_GESTURE_INPUT = "gesture_input"
        const val PREF_GESTURE_METHOD = "gesture_method"
        const val PREF_VIBRATION_DURATION_SETTINGS = "vibration_duration_settings"
        const val PREF_VIBRATION_AMPLITUDE_SETTINGS = "vibration_amplitude_settings"
        const val PREF_KEYPRESS_SOUND_VOLUME = "keypress_sound_volume"
        const val PREF_KEYPRESS_SOUND_STYLE = "keypress_sound_style"
        const val PREF_SOUND_PITCH_SCALE = "sound_pitch_scale"
        const val PREF_SOUND_RANDOM_PITCH = "sound_random_pitch"
        const val PREF_SOUND_STEREO_PAN = "sound_stereo_pan"
        const val PREF_SOUND_DYNAMIC_VELOCITY = "sound_dynamic_velocity"
        const val PREF_SOUND_MUTE_IN_SILENT = "sound_mute_in_silent"
        const val PREF_SOUND_MUTE_IN_DND = "sound_mute_in_dnd"
        const val PREF_SOUND_VOL_SPACE = "sound_vol_space"
        const val PREF_SOUND_VOL_DELETE = "sound_vol_delete"
        const val PREF_SOUND_VOL_ENTER = "sound_vol_enter"
        const val PREF_SOUND_VOL_MODIFIERS = "sound_vol_modifiers"
        const val PREF_KEY_LONGPRESS_TIMEOUT = "key_longpress_timeout"
        const val PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY = "enable_emoji_alt_physical_key"
        const val PREF_GESTURE_PREVIEW_TRAIL = "gesture_preview_trail"
        const val PREF_GESTURE_FLOATING_PREVIEW_TEXT = "gesture_floating_preview_text"
        const val PREF_GESTURE_FLOATING_PREVIEW_DYNAMIC = "gesture_floating_preview_dynamic"
        const val PREF_GESTURE_DYNAMIC_PREVIEW_FOLLOW_SYSTEM = "gesture_dynamic_preview_follow_system"
        const val PREF_GESTURE_SPACE_AWARE = "gesture_space_aware"
        const val PREF_GESTURE_FAST_TYPING_COOLDOWN = "gesture_fast_typing_cooldown"
        const val PREF_GESTURE_TRAIL_FADEOUT_DURATION = "gesture_trail_fadeout_duration"
        const val PREF_SHOW_SETUP_WIZARD_ICON = "show_setup_wizard_icon"
        const val PREF_USE_CONTACTS = "use_contacts"
        const val PREF_USE_APPS = "use_apps"
        const val PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD = "long_press_symbols_for_numpad"
        const val PREF_DISABLE_MULTI_WORD_SUGGESTIONS = "disable_multi_word_suggestions"

        const val PREF_ONE_HANDED_MODE_PREFIX = "one_handed_mode_enabled"
        const val PREF_ONE_HANDED_GRAVITY_PREFIX = "one_handed_mode_gravity"
        const val PREF_ONE_HANDED_SCALE_PREFIX = "one_handed_mode_scale"

        const val PREF_SHOW_NUMBER_ROW = "show_number_row"
        const val PREF_SHOW_NUMBER_ROW_IN_SYMBOLS = "show_number_row_in_symbols"
        const val PREF_COMPACT_NUMBER_ROW_IN_SYMBOLS = "compact_number_row_in_symbols"
        const val PREF_LOCALIZED_NUMBER_ROW = "localized_number_row"
        const val PREF_SHOW_NUMBER_ROW_HINTS = "show_number_row_hints"
        const val PREF_CUSTOM_CURRENCY_KEY = "custom_currency_key"

        const val PREF_SHOW_HINTS = "show_hints"
        const val PREF_POPUP_KEYS_ORDER = "popup_keys_order"
        const val PREF_POPUP_KEYS_LABELS_ORDER = "popup_keys_labels_order"
        const val PREF_SHOW_POPUP_HINTS = "show_popup_hints"
        const val PREF_MORE_POPUP_KEYS = "more_popup_keys"
        const val PREF_SHOW_TLD_POPUP_KEYS = "show_tld_popup_keys"

        const val PREF_SPACE_TO_CHANGE_LANG = "prefs_long_press_keyboard_to_change_lang"
        const val PREF_LANGUAGE_SWIPE_DISTANCE = "language_swipe_distance"
        const val PREF_TOUCHPAD_SENSITIVITY = "touchpad_sensitivity"
        const val PREF_TOUCHPAD_FULLSCREEN = "touchpad_fullscreen"
        const val PREF_PERSIST_FLOATING_KEYBOARD = "persist_floating_keyboard"
        const val PREF_REMEMBER_FLOATING_KEYBOARD = "remember_floating_keyboard"
        const val PREF_PERSIST_TEXT_EDIT_MODE = "persist_text_edit_mode"
        const val PREF_FORCE_AUTO_CAPS = "force_auto_caps"
        const val PREF_OFFLINE_TEMP = "offline_temp"
        const val PREF_OFFLINE_TOP_P = "offline_top_p"
        const val PREF_OFFLINE_TOP_K = "offline_top_k"
        const val PREF_OFFLINE_MIN_P = "offline_min_p"
        const val PREF_OFFLINE_SHOW_THINKING = "offline_show_thinking"
        const val PREF_OFFLINE_SYSTEM_PROMPT = "offline_system_prompt"
        const val PREF_OFFLINE_TRANSLATE_SYSTEM_PROMPT = "offline_translate_system_prompt"
        const val PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE = "offline_translate_target_language"
        const val PREF_OFFLINE_MAX_TOKENS = "offline_max_tokens"
        const val PREF_OFFLINE_KEEP_MODEL_LOADED = "offline_keep_model_loaded"
        const val PREF_AI_ALLOW_INSECURE_CONNECTIONS = "ai_allow_insecure_connections"
        const val PREF_CLOUD_AI_MAX_TOKENS = "cloud_ai_max_tokens"

        const val PREF_ENABLE_CLIPBOARD_HISTORY = "enable_clipboard_history"
        const val PREF_SUGGEST_SCREENSHOTS = "suggest_screenshots"
        const val PREF_COMPRESS_SCREENSHOTS = "compress_screenshots"
        const val PREF_AUTO_READ_OTP = "auto_read_otp"
        const val PREF_OTP_ALLOWED_SMS_PACKAGE = "otp_allowed_sms_package"
        const val PREF_INLINE_MATH_CALCULATION = "pref_inline_calculator_suggestions"
        const val PREF_CLIPBOARD_HISTORY_RETENTION_TIME = "clipboard_history_retention_time"
        const val PREF_CLIPBOARD_HISTORY_PINNED_FIRST = "clipboard_history_pinned_first"
        const val PREF_CLIPBOARD_FOLD_PINNED = "clipboard_fold_pinned"
        const val PREF_CLEAR_CLIPBOARD_ICON = "clear_clipboard_icon"

        const val PREF_ADD_TO_PERSONAL_DICTIONARY = "add_to_personal_dictionary"
        const val PREF_ADD_TO_PERSONAL_DICT_THRESHOLD = "add_to_personal_dict_threshold"
        const val PREF_NAVBAR_COLOR = "navbar_color"
        const val PREF_NARROW_KEY_GAPS = "narrow_key_gaps"
        const val PREF_NARROW_KEY_GAPS_LEVEL = "narrow_key_gaps_level"
        const val PREF_ENABLED_SUBTYPES = "enabled_subtypes"
        const val PREF_SELECTED_SUBTYPE = "selected_subtype"
        const val PREF_URL_DETECTION = "url_detection"
        const val PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG = "dont_show_missing_dict_dialog"
        const val PREF_QUICK_PIN_TOOLBAR_KEYS = "quick_pin_toolbar_keys"
        const val PREF_TOOLBAR_LONG_PRESS_HINT = "toolbar_long_press_hint"
        const val PREF_DISABLE_NETWORK = "disable_network"
        const val PREF_PINNED_TOOLBAR_KEYS = "pinned_toolbar_keys"
        const val PREF_TOOLBAR_KEYS = "toolbar_keys"
        const val PREF_AUTO_SHOW_TOOLBAR = "auto_show_toolbar"
        const val PREF_AUTO_SHOW_TOOLBAR_ON_SELECT = "auto_show_toolbar_on_select"
        const val PREF_AUTO_HIDE_TOOLBAR = "auto_hide_toolbar"
        const val PREF_TOOLBAR_SWIPE_DOWN_DISMISS = "toolbar_swipe_down_dismiss"
        const val PREF_AUTO_HIDE_PINNED_KEYS = "auto_hide_pinned_keys"
        const val PREF_REMEMBER_TOOLBAR_STATE = "remember_toolbar_state"
        const val PREF_TOOLBAR_EXPANDED = "toolbar_expanded"
        const val PREF_CLIPBOARD_TOOLBAR_KEYS = "clipboard_toolbar_keys"
        const val PREF_ABC_AFTER_EMOJI = "abc_after_emoji"
        const val PREF_ABC_AFTER_CLIP = "abc_after_clip"
        const val PREF_ABC_AFTER_SYMBOL_SPACE = "abc_after_symbol_space"
        const val PREF_ABC_AFTER_NUMPAD_SPACE = "abc_after_numpad_space"
        const val PREF_REMOVE_REDUNDANT_POPUPS = "remove_redundant_popups"
        const val PREF_SPACE_BAR_TEXT = "space_bar_text"
        const val PREF_TIMESTAMP_FORMAT = "timestamp_format"
        const val PREF_TOOLBAR_MODE = "toolbar_mode"
        const val PREF_TOOLBAR_HIDING_GLOBAL = "toolbar_hiding_global"
        const val PREF_SPLIT_TOOLBAR = "split_toolbar"
        const val PREF_AUTO_SPAN_TOOLBAR_KEYS = "auto_span_toolbar_keys"
        const val PREF_TOOLBAR_KEYS_ALIGNMENT = "toolbar_keys_alignment"
        const val PREF_CLIPBOARD_KEYS_ALIGNMENT = "clipboard_keys_alignment"
        const val PREF_SHOW_DOWNLOAD_BUTTON_IN_TOOLBAR = "show_download_button_in_toolbar"

        const val PREF_EMOJI_MAX_SDK = "emoji_max_sdk"
        const val PREF_EMOJI_RECENT_KEYS = "emoji_recent_keys"
        const val PREF_LAST_SHOWN_EMOJI_CATEGORY_ID = "last_shown_emoji_category_id"
        const val PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID = "last_shown_emoji_category_page_id"

        const val PREF_VERSION_CODE = "version_code"
        const val PREF_LIBRARY_CHECKSUM = "lib_checksum"
        const val PREF_SAVE_SUBTYPE_PER_APP = "save_subtype_per_app"
        const val PREF_SAVED_APP_SUBTYPE_PREFIX = "saved_app_subtype_"
        const val PREF_DONT_SHOW_SPONSOR_DIALOG = "dont_show_sponsor_dialog"
        const val PREF_LAST_SPONSOR_DIALOG_SHOWN = "last_sponsor_dialog_shown"

        private val sInstance = Settings()

        private val sCachedBackgroundImages = arrayOfNulls<Drawable>(4)
        private var sCachedTypeface: Typeface? = null
        private var sCustomTypefaceLoaded = false
        private var sCachedEmojiTypeface: Typeface? = null
        private var sCustomEmojiTypefaceLoaded = false

        private val dontReloadOnChanged = hashSetOf(
            PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID,
            PREF_LAST_SHOWN_EMOJI_CATEGORY_ID,
            PREF_EMOJI_RECENT_KEYS,
            PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG,
            PREF_SELECTED_SUBTYPE
        )

        fun getInstance(): Settings = sInstance

        fun getValues(): SettingsValues {
            if (sInstance.mSettingsValues == null) {
                sInstance.mContext?.let { sInstance.loadSettings(it) }
            }
            return requireNotNull(sInstance.mSettingsValues) { "SettingsValues must be initialized" }
        }

        fun getCurrentContext(): Context = requireNotNull(sInstance.mContext) { "Context not initialized" }

        fun init(context: Context) {
            sInstance.onCreate(context)
        }

        fun readScreenMetrics(res: Resources): Int = res.getInteger(R.integer.config_screen_metrics)

        fun readVibrationEnabled(prefs: SharedPreferences): Boolean =
            prefs.getBoolean(PREF_VIBRATE_ON, Defaults.PREF_VIBRATE_ON) && AudioAndHapticFeedbackManager.getInstance().hasVibrator()

        fun readGestureDynamicPreviewEnabled(prefs: SharedPreferences): Boolean {
            val followSystem = prefs.getBoolean(PREF_GESTURE_DYNAMIC_PREVIEW_FOLLOW_SYSTEM, Defaults.PREF_GESTURE_DYNAMIC_PREVIEW_FOLLOW_SYSTEM)
            val defValue = Defaults.PREF_GESTURE_DYNAMIC_PREVIEW_FOLLOW_SYSTEM
            val curValue = prefs.getBoolean(PREF_GESTURE_FLOATING_PREVIEW_DYNAMIC, defValue)
            return if (followSystem) defValue else curValue
        }

        fun readGestureDynamicPreviewDefault(context: Context): Boolean {
            return getTransitionAnimationScale(context) != 0.0f
        }

        fun readDefaultGestureFastTypingCooldown(res: Resources): Int =
            res.getInteger(R.integer.config_gesture_static_time_threshold_after_fast_typing)

        fun readToolbarMode(prefs: SharedPreferences): ToolbarMode {
            val raw = prefs.getString(PREF_TOOLBAR_MODE, Defaults.PREF_TOOLBAR_MODE) ?: Defaults.PREF_TOOLBAR_MODE
            return runCatching { ToolbarMode.valueOf(raw) }.getOrDefault(ToolbarMode.valueOf(Defaults.PREF_TOOLBAR_MODE))
        }

        fun readHorizontalSpaceSwipe(prefs: SharedPreferences): Int {
            return when (prefs.getString(PREF_SPACE_HORIZONTAL_SWIPE, Defaults.PREF_SPACE_HORIZONTAL_SWIPE)) {
                "move_cursor" -> KeyboardActionListener.SWIPE_MOVE_CURSOR
                "switch_language" -> KeyboardActionListener.SWIPE_SWITCH_LANGUAGE
                "toggle_numpad" -> KeyboardActionListener.SWIPE_TOGGLE_NUMPAD
                else -> KeyboardActionListener.SWIPE_NO_ACTION
            }
        }

        fun readVerticalSpaceSwipe(prefs: SharedPreferences): Int {
            return when (prefs.getString(PREF_SPACE_VERTICAL_SWIPE, Defaults.PREF_SPACE_VERTICAL_SWIPE)) {
                "move_cursor" -> KeyboardActionListener.SWIPE_MOVE_CURSOR
                "switch_language" -> KeyboardActionListener.SWIPE_SWITCH_LANGUAGE
                "toggle_numpad" -> KeyboardActionListener.SWIPE_TOGGLE_NUMPAD
                "hide_keyboard" -> KeyboardActionListener.SWIPE_HIDE_KEYBOARD
                "touchpad_mode" -> KeyboardActionListener.SWIPE_TOUCHPAD_MODE
                else -> KeyboardActionListener.SWIPE_NO_ACTION
            }
        }

        fun readFullscreenModeAllowed(res: Resources): Boolean = res.getBoolean(R.bool.config_fullscreen_mode_allowed)

        fun readShowSetupWizardIcon(prefs: SharedPreferences, context: Context): Boolean {
            if (!prefs.contains(PREF_SHOW_SETUP_WIZARD_ICON)) {
                val appInfo = context.applicationInfo
                val isApplicationInSystemImage = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                return !isApplicationInSystemImage
            }
            return prefs.getBoolean(PREF_SHOW_SETUP_WIZARD_ICON, Defaults.PREF_SHOW_SETUP_WIZARD_ICON)
        }

        fun readOneHandedModeEnabled(prefs: SharedPreferences, landscape: Boolean, split: Boolean): Boolean {
            val index = findIndexOfDefaultSetting(landscape, split)
            val key = createPrefKeyForBooleanSettings(PREF_ONE_HANDED_MODE_PREFIX, index, 2)
            return prefs.getBoolean(key, Defaults.PREF_ONE_HANDED_MODE)
        }

        fun readOneHandedModeScale(prefs: SharedPreferences, landscape: Boolean, split: Boolean): Float {
            val index = findIndexOfDefaultSetting(landscape, split)
            val key = createPrefKeyForBooleanSettings(PREF_ONE_HANDED_SCALE_PREFIX, index, 2)
            return prefs.getFloat(key, Defaults.PREF_ONE_HANDED_SCALE)
        }

        fun readOneHandedModeGravity(prefs: SharedPreferences, landscape: Boolean, split: Boolean): Int {
            val index = findIndexOfDefaultSetting(landscape, split)
            val key = createPrefKeyForBooleanSettings(PREF_ONE_HANDED_GRAVITY_PREFIX, index, 2)
            return prefs.getInt(key, Defaults.PREF_ONE_HANDED_GRAVITY)
        }

        fun readSplitKeyboardEnabled(prefs: SharedPreferences, isLandscape: Boolean): Boolean {
            return readSplitKeyboardEnabled(prefs, isLandscape, helium314.keyboard.latin.utils.ScreenProfile.COMPACT)
        }

        fun readSplitKeyboardEnabled(prefs: SharedPreferences, isLandscape: Boolean, profile: helium314.keyboard.latin.utils.ScreenProfile): Boolean {
            val basePref = if (isLandscape) PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE else PREF_ENABLE_SPLIT_KEYBOARD
            val defaultValue = profile.isLarge() || if (isLandscape) Defaults.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE else Defaults.PREF_ENABLE_SPLIT_KEYBOARD
            return getProfileAwareBoolean(prefs, basePref, profile, defaultValue)
        }

        fun readSplitSpacerScale(prefs: SharedPreferences, landscape: Boolean): Float {
            val index = findIndexOfDefaultSetting(landscape)
            val defaults = Defaults.PREF_SPLIT_SPACER_SCALE
            val defaultValue = defaults[index]
            return prefs.getFloat(createPrefKeyForBooleanSettings(PREF_SPLIT_SPACER_SCALE_PREFIX, index, 1), defaultValue)
        }

        fun readBottomPaddingScale(prefs: SharedPreferences, landscape: Boolean): Float {
            val index = findIndexOfDefaultSetting(landscape)
            val defaults = Defaults.PREF_BOTTOM_PADDING_SCALE
            val defaultValue = defaults[index]
            return prefs.getFloat(createPrefKeyForBooleanSettings(PREF_BOTTOM_PADDING_SCALE_PREFIX, index, 1), defaultValue)
        }

        fun readSidePaddingScale(prefs: SharedPreferences, landscape: Boolean, split: Boolean): Float {
            val index = findIndexOfDefaultSetting(landscape, split)
            val defaults = Defaults.PREF_SIDE_PADDING_SCALE
            val defaultValue = defaults[index]
            return prefs.getFloat(createPrefKeyForBooleanSettings(PREF_SIDE_PADDING_SCALE_PREFIX, index, 2), defaultValue)
        }

        fun readHeightScale(prefs: SharedPreferences, landscape: Boolean): Float {
            return readHeightScale(prefs, landscape, helium314.keyboard.latin.utils.ScreenProfile.COMPACT)
        }

        fun readHeightScale(prefs: SharedPreferences, landscape: Boolean, profile: helium314.keyboard.latin.utils.ScreenProfile): Float {
            val index = findIndexOfDefaultSetting(landscape)
            val defaults = Defaults.PREF_KEYBOARD_HEIGHT_SCALE
            val defaultValue = defaults[index]
            val basePref = createPrefKeyForBooleanSettings(PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, index, 1)
            return getProfileAwareFloat(prefs, basePref, profile, defaultValue)
        }

        fun readHasHardwareKeyboard(conf: Configuration): Boolean {
            return conf.keyboard != Configuration.KEYBOARD_NOKEYS && conf.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
        }

        fun readUserBackgroundImage(context: Context, night: Boolean): Drawable? {
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val index = (if (night) 1 else 0) + (if (landscape) 2 else 0)
            if (sCachedBackgroundImages[index] != null) return sCachedBackgroundImages[index]

            var image = getCustomBackgroundFile(context, night, landscape)
            if (!image.isFile && landscape) {
                image = getCustomBackgroundFile(context, night, false)
            }
            if (!image.isFile) return null
            return try {
                val bm = BitmapUtils.decodeSampledBitmap(image, 2048, true) ?: return null
                sCachedBackgroundImages[index] = CenterCropDrawable(bm)
                sCachedBackgroundImages[index]
            } catch (e: Exception) {
                null
            }
        }

        fun getCustomBackgroundFile(context: Context, night: Boolean, landscape: Boolean): File {
            return File(
                DeviceProtectedUtils.getFilesDir(context),
                "custom_background_image" + (if (landscape) "_landscape" else "") + (if (night) "_night" else "")
            )
        }

        fun clearCachedBackgroundImages() {
            sCachedBackgroundImages.fill(null)
        }

        fun getDayNightContext(context: Context, wantNight: Boolean): Context {
            val isNight = ResourceUtils.isNight(context.resources)
            if (isNight == wantNight) return context
            val config = Configuration(context.resources.configuration)
            val night = config.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val uiModeWithNightBitsZero = config.uiMode - night
            config.uiMode = uiModeWithNightBitsZero + if (wantNight) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            val wrapper = ContextThemeWrapper(context, R.style.platformActivityTheme)
            wrapper.applyOverrideConfiguration(config)
            return wrapper
        }

        fun getCustomFontFile(context: Context): File = File(DeviceProtectedUtils.getFilesDir(context), "custom_font")

        fun getCustomEmojiFontFile(context: Context): File = File(DeviceProtectedUtils.getFilesDir(context), "custom_emoji_font")

        fun readDefaultLayoutName(type: LayoutType, prefs: SharedPreferences): String =
            prefs.getString(PREF_LAYOUT_PREFIX + type.name, type.default) ?: type.default

        fun writeDefaultLayoutName(name: String?, type: LayoutType, prefs: SharedPreferences) {
            if (name == null)
                prefs.edit().remove(PREF_LAYOUT_PREFIX + type.name).apply()
            else
                prefs.edit().putString(PREF_LAYOUT_PREFIX + type.name, name).apply()
        }

        fun clearCachedTypeface() {
            sCachedTypeface = null
            sCustomTypefaceLoaded = false
            sCachedEmojiTypeface = null
            sCustomEmojiTypefaceLoaded = false
        }
    }
}

fun customIconNames(prefs: SharedPreferences) = runCatching {
    val raw = prefs.getString(Settings.PREF_CUSTOM_ICON_NAMES, Defaults.PREF_CUSTOM_ICON_NAMES) ?: Defaults.PREF_CUSTOM_ICON_NAMES
    Json.decodeFromString<Map<String, String>>(raw)
}.getOrElse { emptyMap() }

@SuppressLint("DiscouragedApi")
fun customIconIds(context: Context, prefs: SharedPreferences) = customIconNames(prefs)
    .mapNotNull { entry ->
        val id = runCatching { context.resources.getIdentifier(entry.value, "drawable", context.packageName) }.getOrNull()
        if (id != null && id != 0) entry.key to id else null
    }

/** Derive an index from a number of boolean [settingValues], used to access the matching default value in a defaults array */
fun findIndexOfDefaultSetting(vararg settingValues: Boolean): Int {
    var i = -1
    return settingValues.sumOf { i++; if (it) 1.shl(i) else 0 }
}

/** Create pref key that is derived from a [number] of boolean conditions. The [index] is as created by [findIndexOfDefaultSetting]. */
fun createPrefKeyForBooleanSettings(prefix: String, index: Int, number: Int): String =
    "${prefix}_${Array(number) { index.shr(it) % 2 == 1 }.joinToString("_")}"

fun getTransitionAnimationScale(context: Context) =
    Global.getFloat(context.contentResolver, Global.TRANSITION_ANIMATION_SCALE, 1f)

fun getProfileAwarePrefKey(key: String, profile: helium314.keyboard.latin.utils.ScreenProfile): String =
    "${key}_${profile.name.lowercase()}"

fun getProfileAwareBoolean(prefs: SharedPreferences, key: String, profile: helium314.keyboard.latin.utils.ScreenProfile, defaultValue: Boolean): Boolean {
    val profileKey = getProfileAwarePrefKey(key, profile)
    if (prefs.contains(profileKey)) return prefs.getBoolean(profileKey, defaultValue)
    if (prefs.contains(key)) return prefs.getBoolean(key, defaultValue)
    return defaultValue
}

fun getProfileAwareFloat(prefs: SharedPreferences, key: String, profile: helium314.keyboard.latin.utils.ScreenProfile, defaultValue: Float): Float {
    val profileKey = getProfileAwarePrefKey(key, profile)
    if (prefs.contains(profileKey)) return prefs.getFloat(profileKey, defaultValue)
    if (prefs.contains(key)) return prefs.getFloat(key, defaultValue)
    return defaultValue
}
