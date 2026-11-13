// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.media.AudioManager
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import helium314.keyboard.keyboard.internal.keyboard_parser.POPUP_KEYS_ALL
import helium314.keyboard.keyboard.internal.keyboard_parser.POPUP_KEYS_MAIN
import helium314.keyboard.keyboard.internal.keyboard_parser.POPUP_KEYS_MORE
import helium314.keyboard.keyboard.internal.keyboard_parser.POPUP_KEYS_NORMAL
import helium314.keyboard.keyboard.internal.keyboard_parser.morePopupKeysResId
import helium314.keyboard.settings.fineTuneExpanded
import helium314.keyboard.settings.NextScreenIcon
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.ExpandablePreference
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.previewDark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

@Composable
fun PreferencesScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val activity = ctx.getActivity() as? SettingsActivity
    val prefChanged = (activity?.prefChanged ?: MutableStateFlow(0)).collectAsState()

    val hasVibrator = AudioAndHapticFeedbackManager.getInstance().hasVibrator()
    val vibrateOn = hasVibrator && prefs.getBoolean(Settings.PREF_VIBRATE_ON, Defaults.PREF_VIBRATE_ON)
    val hasAmplitude = AudioAndHapticFeedbackManager.getInstance().hasAmplitudeControl()
    val showNumberRow = prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, Defaults.PREF_SHOW_NUMBER_ROW)
    val showNumberRowInSymbols = prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS, Defaults.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS)
    val showHints = prefs.getBoolean(Settings.PREF_SHOW_HINTS, Defaults.PREF_SHOW_HINTS)
    val clipboardHistoryEnabled = prefs.getBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, Defaults.PREF_ENABLE_CLIPBOARD_HISTORY)

    val fineTuneHapticsExpanded = fineTuneExpanded(prefs, Settings.PREF_EXPAND_FINE_TUNE_HAPTICS, Defaults.PREF_EXPAND_FINE_TUNE_HAPTICS)
    val fineTuneAdditionalKeysExpanded = fineTuneExpanded(prefs, Settings.PREF_EXPAND_FINE_TUNE_ADDITIONAL_KEYS, Defaults.PREF_EXPAND_FINE_TUNE_ADDITIONAL_KEYS)
    val fineTuneClipboardExpanded = fineTuneExpanded(prefs, Settings.PREF_EXPAND_FINE_TUNE_CLIPBOARD, Defaults.PREF_EXPAND_FINE_TUNE_CLIPBOARD)

    val items = remember(
        hasVibrator, vibrateOn, hasAmplitude, showNumberRow, showNumberRowInSymbols, showHints, clipboardHistoryEnabled,
        fineTuneHapticsExpanded, fineTuneAdditionalKeysExpanded, fineTuneClipboardExpanded, prefChanged.value
    ) {
        listOf(
            // Input & Haptics
            R.string.settings_category_input,
            Settings.PREF_KEY_LONGPRESS_TIMEOUT,
            Settings.PREF_EMOJI_SKIN_TONE,
            Settings.PREF_SHOW_EMOJI_KEY,
            if (hasVibrator) Settings.PREF_VIBRATE_ON else null,
            if (vibrateOn) Settings.PREF_VIBRATION_DURATION_SETTINGS else null,
            if (vibrateOn) Settings.PREF_EXPAND_FINE_TUNE_HAPTICS else null,
            if (vibrateOn && fineTuneHapticsExpanded) Settings.PREF_HAPTIC_ENGINE else null,
            if (vibrateOn && fineTuneHapticsExpanded && hasAmplitude)
                Settings.PREF_VIBRATION_AMPLITUDE_SETTINGS else null,
            if (vibrateOn && fineTuneHapticsExpanded) Settings.PREF_VIBRATE_IN_DND_MODE else null,

            // Additional keys
            R.string.settings_category_additional_keys,
            Settings.PREF_SHOW_NUMBER_ROW,
            Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY,
            Settings.PREF_SPACE_TO_CHANGE_LANG,
            Settings.PREF_EXPAND_FINE_TUNE_ADDITIONAL_KEYS,
            if (fineTuneAdditionalKeysExpanded && SubtypeSettings.getEnabledSubtypes(true).any { it.locale().language in localesWithLocalizedNumberRow })
                Settings.PREF_LOCALIZED_NUMBER_ROW else null,
            if (fineTuneAdditionalKeysExpanded && SubtypeSettings.getEnabledSubtypes(true).any { it.locale().language in localesWithLocalizedNumberRow })
                Settings.PREF_LOCALIZED_NUMBERS_IN_POPUPS else null,
            if (fineTuneAdditionalKeysExpanded && showHints && showNumberRow)
                Settings.PREF_SHOW_NUMBER_ROW_HINTS else null,
            if (fineTuneAdditionalKeysExpanded && !showNumberRow)
                Settings.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS else null,
            if (fineTuneAdditionalKeysExpanded && !showNumberRow && showNumberRowInSymbols)
                Settings.PREF_COMPACT_NUMBER_ROW_IN_SYMBOLS else null,
            if (fineTuneAdditionalKeysExpanded) Settings.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD else null,
            if (fineTuneAdditionalKeysExpanded) Settings.PREF_LANGUAGE_SWITCH_KEY else null,
            if (fineTuneAdditionalKeysExpanded) Settings.PREF_DIRECT_IME_SWITCH_TARGET else null,
            if (fineTuneAdditionalKeysExpanded) Settings.PREF_REMOVE_REDUNDANT_POPUPS else null,
            if (fineTuneAdditionalKeysExpanded) Settings.PREF_MORE_POPUP_KEYS else null,

            // Window modes
            R.string.settings_category_window_modes,
            Settings.PREF_REMEMBER_FLOATING_KEYBOARD,
            Settings.PREF_FLOATING_KEYBOARD_LANDSCAPE,
            Settings.PREF_PERSIST_TEXT_EDIT_MODE,

            // Clipboard history
            R.string.settings_category_clipboard_history,
            Settings.PREF_ENABLE_CLIPBOARD_HISTORY,
            if (clipboardHistoryEnabled) Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME else null,
            if (clipboardHistoryEnabled) Settings.PREF_CLIPBOARD_SKIP_SENSITIVE else null,
            if (clipboardHistoryEnabled) Settings.PREF_EXPAND_FINE_TUNE_CLIPBOARD else null,
            if (clipboardHistoryEnabled && fineTuneClipboardExpanded)
                Settings.PREF_CLIPBOARD_HISTORY_PINNED_FIRST else null,
            if (clipboardHistoryEnabled && fineTuneClipboardExpanded)
                Settings.PREF_CLIPBOARD_FOLD_PINNED else null,
            if (clipboardHistoryEnabled && fineTuneClipboardExpanded)
                Settings.PREF_CLIPBOARD_FONT_SCALE else null,
        )
    }
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_preferences),
        settings = items
    )
}

fun createPreferencesSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_KEY_LONGPRESS_TIMEOUT, R.string.prefs_key_longpress_timeout_settings) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_KEY_LONGPRESS_TIMEOUT,
            range = 100f..700f,
            description = { stringResource(R.string.abbreviation_unit_milliseconds, it.toString()) }
        )
    },
    Setting(context, Settings.PREF_MORE_POPUP_KEYS, R.string.show_popup_keys_title) {
        val items = listOf(POPUP_KEYS_NORMAL, POPUP_KEYS_MAIN, POPUP_KEYS_MORE, POPUP_KEYS_ALL).map { setting ->
            stringResource(morePopupKeysResId(setting)) to setting
        }
        ListPreference(it, items, Defaults.PREF_MORE_POPUP_KEYS) { KeyboardLayoutSet.onSystemLocaleChanged() }
    },
    Setting(context, Settings.PREF_EMOJI_SKIN_TONE, R.string.prefs_emoji_skin_tone) { setting ->
        val items = listOf(
            stringResource(R.string.prefs_emoji_skin_tone_neutral) to "",
            "\uD83C\uDFFB" to "\uD83C\uDFFB",
            "\uD83C\uDFFC" to "\uD83C\uDFFC",
            "\uD83C\uDFFD" to "\uD83C\uDFFD",
            "\uD83C\uDFFE" to "\uD83C\uDFFE",
            "\uD83C\uDFFF" to "\uD83C\uDFFF"
        )
        ListPreference(setting, items, Defaults.PREF_EMOJI_SKIN_TONE) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_VIBRATE_ON, R.string.vibrate_on_keypress) {
        SwitchPreference(it, Defaults.PREF_VIBRATE_ON)
    },
    Setting(context, Settings.PREF_VIBRATE_IN_DND_MODE, R.string.vibrate_in_dnd_mode) {
        SwitchPreference(it, Defaults.PREF_VIBRATE_IN_DND_MODE)
    },
    Setting(context, Settings.PREF_SHOW_NUMBER_ROW, R.string.number_row, R.string.number_row_summary) {
        SwitchPreference(it, Defaults.PREF_SHOW_NUMBER_ROW) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS, R.string.number_row_in_symbols) {
        SwitchPreference(it, Defaults.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_COMPACT_NUMBER_ROW_IN_SYMBOLS, R.string.compact_number_row_in_symbols, R.string.compact_number_row_in_symbols_summary) {
        SwitchPreference(it, Defaults.PREF_COMPACT_NUMBER_ROW_IN_SYMBOLS) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_LOCALIZED_NUMBER_ROW, R.string.localized_number_row, R.string.localized_number_row_summary) {
        SwitchPreference(it, Defaults.PREF_LOCALIZED_NUMBER_ROW) {
            KeyboardLayoutSet.onSystemLocaleChanged()
            KeyboardSwitcher.getInstance().reloadKeyboard()
        }
    },
    Setting(context, Settings.PREF_LOCALIZED_NUMBERS_IN_POPUPS, R.string.localized_numbers_in_popups, R.string.localized_numbers_in_popups_summary) {
        SwitchPreference(it, Defaults.PREF_LOCALIZED_NUMBERS_IN_POPUPS) {
            KeyboardLayoutSet.onSystemLocaleChanged()
            KeyboardSwitcher.getInstance().reloadKeyboard()
        }
    },
    Setting(context, Settings.PREF_SHOW_NUMBER_ROW_HINTS, R.string.number_row_hints) {
        SwitchPreference(it, Defaults.PREF_SHOW_NUMBER_ROW_HINTS) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD, R.string.prefs_long_press_symbol_for_numpad) {
        SwitchPreference(it, Defaults.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD)
    },
    Setting(context, Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, R.string.show_language_switch_key) {
        SwitchPreference(it, Defaults.PREF_SHOW_LANGUAGE_SWITCH_KEY) { KeyboardSwitcher.getInstance().reloadKeyboard() }
    },
    Setting(context, Settings.PREF_LANGUAGE_SWITCH_KEY, R.string.language_switch_key_behavior) {
        ListPreference(
            it,
            listOf(
                stringResource(R.string.switch_language) to "internal",
                stringResource(R.string.language_switch_key_switch_input_method) to "input_method",
                stringResource(R.string.language_switch_key_switch_both) to "both"
            ),
            Defaults.PREF_LANGUAGE_SWITCH_KEY
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_SPACE_TO_CHANGE_LANG,
        R.string.prefs_long_press_keyboard_to_change_lang,
        R.string.prefs_long_press_keyboard_to_change_lang_summary)
    {
        SwitchPreference(it, Defaults.PREF_SPACE_TO_CHANGE_LANG)
    },
    Setting(context, Settings.PREF_DIRECT_IME_SWITCH_TARGET, R.string.direct_ime_switch_title, R.string.direct_ime_switch_summary) {
        ListPreference(
            it,
            getDirectImeSwitchItems(context),
            Defaults.PREF_DIRECT_IME_SWITCH_TARGET
        )
    },
    Setting(context, Settings.PREF_SHOW_EMOJI_KEY, R.string.show_emoji_key) {
        SwitchPreference(it, Defaults.PREF_SHOW_EMOJI_KEY) { KeyboardSwitcher.getInstance().reloadKeyboard() }
    },
    Setting(context, Settings.PREF_REMOVE_REDUNDANT_POPUPS,
        R.string.remove_redundant_popups, R.string.remove_redundant_popups_summary)
    {
        SwitchPreference(it, Defaults.PREF_REMOVE_REDUNDANT_POPUPS) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_REMEMBER_FLOATING_KEYBOARD, R.string.remember_floating_keyboard_title, R.string.remember_floating_keyboard_summary) {
        val ctx = LocalContext.current
        SwitchPreference(it, Defaults.PREF_REMEMBER_FLOATING_KEYBOARD, onCheckedChange = { enabled ->
            if (!enabled) {
                LatinIME.getInstance()?.floatingKeyboardManager?.clearFloatingActiveState()
                    ?: DeviceProtectedUtils.getSharedPreferences(ctx, "floating_keyboard_prefs")
                        .edit().putBoolean("floating_is_active", false).apply()
            }
        })
    },
    Setting(context, Settings.PREF_FLOATING_KEYBOARD_LANDSCAPE, R.string.floating_keyboard_landscape_title, R.string.floating_keyboard_landscape_summary) {
        SwitchPreference(it, Defaults.PREF_FLOATING_KEYBOARD_LANDSCAPE, onCheckedChange = { enabled ->
            if (!enabled) {
                LatinIME.getInstance()?.floatingKeyboardManager?.let { fkm ->
                    if (fkm.isAutoLandscapeFloating) {
                        fkm.hide(showDockedKeyboard = true)
                    }
                }
            }
        })
    },
    Setting(context, Settings.PREF_PERSIST_TEXT_EDIT_MODE, R.string.persist_text_edit_mode_title, R.string.persist_text_edit_mode_summary) {
        SwitchPreference(it, Defaults.PREF_PERSIST_TEXT_EDIT_MODE)
    },
    Setting(context, Settings.PREF_ENABLE_CLIPBOARD_HISTORY,
        R.string.enable_clipboard_history, R.string.enable_clipboard_history_summary)
    {
        val ctx = LocalContext.current
        SwitchPreference(it, Defaults.PREF_ENABLE_CLIPBOARD_HISTORY) { ClipboardDao.getInstance(ctx)?.clearNonPinned() }
    },
    Setting(context, Settings.PREF_CLIPBOARD_SKIP_SENSITIVE,
        R.string.clipboard_skip_sensitive, R.string.clipboard_skip_sensitive_summary) {
        SwitchPreference(it, Defaults.PREF_CLIPBOARD_SKIP_SENSITIVE)
    },
    Setting(context, Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME, R.string.clipboard_history_retention_time) { setting ->
        val ctx = LocalContext.current
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_CLIPBOARD_HISTORY_RETENTION_TIME,
            description = {
                if (it > 120) stringResource(R.string.settings_no_limit)
                else stringResource(R.string.abbreviation_unit_minutes, it.toString())
            },
            range = 1f..121f,
        ) { ClipboardDao.getInstance(ctx)?.clearOldClips(true) }
    },
    Setting(context, Settings.PREF_CLIPBOARD_HISTORY_PINNED_FIRST, R.string.clipboard_history_pinned_first) {
        SwitchPreference(it, Defaults.PREF_CLIPBOARD_HISTORY_PINNED_FIRST)
    },
    Setting(context, Settings.PREF_CLIPBOARD_FOLD_PINNED, R.string.clipboard_fold_pinned) {
        SwitchPreference(it, Defaults.PREF_CLIPBOARD_FOLD_PINNED)
    },
    Setting(context, Settings.PREF_CLIPBOARD_FONT_SCALE, R.string.clipboard_font_scale) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = Defaults.PREF_CLIPBOARD_FONT_SCALE,
            range = 0.5f..1.5f,
            description = { "${(100 * it).roundToInt()}%" }
        )
    },
    Setting(context, Settings.PREF_HAPTIC_ENGINE, R.string.prefs_haptic_engine) { setting ->
        val items = listOf(
            stringResource(R.string.haptic_engine_system) to Settings.HAPTIC_ENGINE_SYSTEM,
            stringResource(R.string.haptic_engine_primitives) to Settings.HAPTIC_ENGINE_PRIMITIVES,
            stringResource(R.string.haptic_engine_oneshot) to Settings.HAPTIC_ENGINE_ONESHOT,
        )
        ListPreference(setting, items, Defaults.PREF_HAPTIC_ENGINE) {
            AudioAndHapticFeedbackManager.getInstance().performHapticFeedback(null, HapticEvent.KEY_PRESS)
        }
    },
    Setting(context, Settings.PREF_VIBRATION_DURATION_SETTINGS, R.string.prefs_keypress_vibration_duration_settings) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_VIBRATION_DURATION_SETTINGS,
            description = {
                if (it < 0) stringResource(R.string.settings_system_default)
                else stringResource(R.string.abbreviation_unit_milliseconds, it.toString())
            },
            range = -1f..100f,
            onValueChanged = { it?.let { AudioAndHapticFeedbackManager.getInstance().vibrate(it.toLong()) } }
        )
    },
    Setting(context, Settings.PREF_VIBRATION_AMPLITUDE_SETTINGS, R.string.prefs_keypress_vibration_amplitude_settings) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_VIBRATION_AMPLITUDE_SETTINGS,
            description = {
                if (it < 0) stringResource(R.string.settings_system_default)
                else "${it.toInt()}%"
            },
            range = -1f..100f,
            onValueChanged = { it?.let {
                val duration = context.prefs().getInt(Settings.PREF_VIBRATION_DURATION_SETTINGS, Defaults.PREF_VIBRATION_DURATION_SETTINGS)
                val safeDuration = if (duration >= 0) duration else 15
                AudioAndHapticFeedbackManager.getInstance().vibrate(safeDuration.toLong(), it.toInt())
            } }
        )
    },
    Setting(context, Settings.PREF_EXPAND_FINE_TUNE_HAPTICS,
        R.string.fine_tune_haptics_title, R.string.fine_tune_haptics_summary
    ) {
        ExpandablePreference(it, Defaults.PREF_EXPAND_FINE_TUNE_HAPTICS)
    },
    Setting(context, Settings.PREF_EXPAND_FINE_TUNE_ADDITIONAL_KEYS,
        R.string.fine_tune_additional_keys_title, R.string.fine_tune_additional_keys_summary
    ) {
        ExpandablePreference(it, Defaults.PREF_EXPAND_FINE_TUNE_ADDITIONAL_KEYS)
    },
    Setting(context, Settings.PREF_EXPAND_FINE_TUNE_CLIPBOARD,
        R.string.fine_tune_clipboard_title, R.string.fine_tune_clipboard_summary
    ) {
        ExpandablePreference(it, Defaults.PREF_EXPAND_FINE_TUNE_CLIPBOARD)
    },
)

// todo (later): not good to have it hardcoded, but reading a bunch of files may be noticeably slow
private val localesWithLocalizedNumberRow = listOf("ar", "bn", "fa", "gu", "hi", "kn", "mr", "ne", "ur")

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            PreferencesScreen { }
        }
    }
}

private fun getDirectImeSwitchItems(context: Context): List<Pair<String, String>> {
    val pm = context.packageManager
    val richImm = RichInputMethodManager.getInstance()
    val thisImi = richImm.inputMethodInfoOfThisIme
    val enabledImis = richImm.inputMethodManager.enabledInputMethodList
        .sortedBy { it.hashCode() }.sortedBy { it.loadLabel(pm).toString() }
    
    val items = mutableListOf<Pair<String, String>>()
    items.add(context.getString(R.string.direct_ime_switch_none) to "")

    enabledImis.forEach { imi ->
        val isThisIme = (thisImi != null && imi == thisImi) || imi.packageName == context.packageName
        val subtypes = if (!isThisIme) richImm.getEnabledInputMethodSubtypes(imi, true)
            else richImm.getEnabledInputMethodSubtypes(imi, true).sortedBy { it.displayName() }
        if (subtypes.isEmpty()) {
            val label = imi.loadLabel(pm).toString()
            val value = "${imi.id};"
            items.add(label to value)
        } else {
            subtypes.forEach { subtype ->
                if (!subtype.isAuxiliary) {
                    val subtypeName = if (isThisIme) {
                        subtype.displayName()
                    } else {
                        subtype.getDisplayName(context, imi.packageName, imi.serviceInfo.applicationInfo)
                    }
                    val label = if (subtypeName.isBlank()) imi.loadLabel(pm).toString() 
                        else "$subtypeName (${imi.loadLabel(pm)})"
                    val value = "${imi.id};${subtype.hashCode()}"
                    items.add(label to value)
                }
            }
        }
    }
    return items
}
