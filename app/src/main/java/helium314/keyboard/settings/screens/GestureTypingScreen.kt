// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.previewDark
import androidx.core.content.edit
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.preferences.ListPreference
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.isMainDictionaryMissing
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.preferences.LoadGestureLibPreference

@Composable
fun GestureTypingScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val hasGestureLib = JniUtils.sHaveNativeGestureLib
    val gestureFloatingPreviewEnabled = prefs.getBoolean(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, Defaults.PREF_GESTURE_FLOATING_PREVIEW_TEXT)
    val gestureEnabled = prefs.getBoolean(Settings.PREF_GESTURE_INPUT, Defaults.PREF_GESTURE_INPUT)
    val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(true)
    val missingDict = enabledSubtypes.any { isMainDictionaryMissing(ctx, it.locale()) }
    
    val items = buildList {
        add(R.string.settings_category_configuration)
        add(Settings.PREF_GESTURE_INPUT)
        if (helium314.keyboard.latin.BuildConfig.BUILD_TYPE != "nouserlib") {
            add(SettingsWithoutKey.LOAD_GESTURE_LIB)
        }

        if (hasGestureLib && gestureEnabled) {
            add(R.string.settings_category_visuals)
            add(Settings.PREF_GESTURE_PREVIEW_TRAIL)
            add(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT)
            if (gestureFloatingPreviewEnabled)
                add(Settings.PREF_GESTURE_FLOATING_PREVIEW_DYNAMIC)
            if (prefs.getBoolean(Settings.PREF_GESTURE_PREVIEW_TRAIL, Defaults.PREF_GESTURE_PREVIEW_TRAIL) || gestureFloatingPreviewEnabled)
                add(Settings.PREF_GESTURE_TRAIL_FADEOUT_DURATION)

            add(R.string.settings_category_behavior)
            add(Settings.PREF_GESTURE_SPACE_AWARE)
            add(Settings.PREF_GESTURE_FAST_TYPING_COOLDOWN)
            add(Settings.PREF_AUTOSPACE_BEFORE_GESTURE_TYPING)
            add(Settings.PREF_AUTOSPACE_AFTER_GESTURE_TYPING)
        }

        add(R.string.settings_category_gestures_advanced)
        add(Settings.PREF_FULL_KEYBOARD_SWIPE)
        if (Settings.readFullKeyboardSwipe(prefs) != KeyboardActionListener.SWIPE_NO_ACTION
            || Settings.readHorizontalSpaceSwipe(prefs) == KeyboardActionListener.SWIPE_SWITCH_LANGUAGE) {
            add(Settings.PREF_LANGUAGE_SWIPE_DISTANCE)
        }
        add(Settings.PREF_SPACE_HORIZONTAL_SWIPE)
        add(Settings.PREF_SPACE_VERTICAL_SWIPE)
        add(Settings.PREF_SPACE_VERTICAL_DOWN_SWIPE)
        add(Settings.PREF_TOOLBAR_SWIPE_UP_GESTURE)
        add(Settings.PREF_TOOLBAR_SWIPE_DOWN_GESTURE)
        add(Settings.PREF_VERTICAL_SWIPE_THRESHOLD)
        add(Settings.PREF_DELETE_SWIPE)
        // only useful where the editor can be asked for its lines (requestTextBoundsInfo, Android 14+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            add(Settings.PREF_DELETE_SWIPE_EXACT_LINES)

        add(R.string.settings_category_touchpad)
        add(Settings.PREF_TOUCHPAD_SENSITIVITY)
        add(Settings.PREF_TOUCHPAD_FULLSCREEN)
    }
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_gesture),
        settings = items,
        headerContent = if (hasGestureLib && gestureEnabled && missingDict) {
            {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_dictionary_download),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.glide_typing_no_dict_warning_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.glide_typing_no_dict_warning_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { SettingsDestination.navigateTo(SettingsDestination.Dictionaries) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(stringResource(R.string.download_dictionaries_button))
                        }
                    }
                }
            }
        } else null
    )
}

fun createGestureTypingSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_GESTURE_INPUT, R.string.gesture_input, R.string.gesture_input_summary) {
        val ctx = LocalContext.current
        SwitchPreference(it, Defaults.PREF_GESTURE_INPUT) { enabled ->
            if (enabled) {
                ctx.prefs().edit {
                    putString(Settings.PREF_FULL_KEYBOARD_SWIPE, "none")
                    putBoolean(Settings.PREF_FULL_SWIPE_SWITCH_LANGUAGE, false)
                }
            }
        }
    },
    Setting(context, Settings.PREF_GESTURE_PREVIEW_TRAIL, R.string.gesture_preview_trail) {
        SwitchPreference(it, Defaults.PREF_GESTURE_PREVIEW_TRAIL)
    },
    Setting(context, Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT,
        R.string.gesture_floating_preview_static, R.string.gesture_floating_preview_static_summary)
    {
        SwitchPreference(it, Defaults.PREF_GESTURE_FLOATING_PREVIEW_TEXT)
    },
    Setting(context, Settings.PREF_GESTURE_FLOATING_PREVIEW_DYNAMIC,
        R.string.gesture_floating_preview_text, R.string.gesture_floating_preview_dynamic_summary)
    { def ->
        val ctx = LocalContext.current
        SwitchPreference(def, Defaults.PREF_GESTURE_FLOATING_PREVIEW_DYNAMIC) {
            // is this complexity and 2 pref keys for one setting really needed?
            // default value is based on system reduced motion
            val default = Settings.readGestureDynamicPreviewDefault(ctx)
            val followingSystem = it == default
            // allow the default to be overridden
            ctx.prefs().edit { putBoolean(Settings.PREF_GESTURE_DYNAMIC_PREVIEW_FOLLOW_SYSTEM, followingSystem) }
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    },
    Setting(context, Settings.PREF_GESTURE_SPACE_AWARE, R.string.gesture_space_aware, R.string.gesture_space_aware_summary) {
        SwitchPreference(it, Defaults.PREF_GESTURE_SPACE_AWARE)
    },
    Setting(context, Settings.PREF_AUTOSPACE_AFTER_GESTURE_TYPING, R.string.autospace_after_gesture_typing) {
        SwitchPreference(it, Defaults.PREF_AUTOSPACE_AFTER_GESTURE_TYPING)
    },
    Setting(context, Settings.PREF_AUTOSPACE_BEFORE_GESTURE_TYPING, R.string.autospace_before_gesture_typing) {
        SwitchPreference(it, Defaults.PREF_AUTOSPACE_BEFORE_GESTURE_TYPING)
    },
    Setting(context, Settings.PREF_GESTURE_FAST_TYPING_COOLDOWN, R.string.gesture_fast_typing_cooldown) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = Defaults.PREF_GESTURE_FAST_TYPING_COOLDOWN,
            range = 0f..500f,
            description = {
                if (it <= 0) stringResource(R.string.gesture_fast_typing_cooldown_instant)
                else stringResource(R.string.abbreviation_unit_milliseconds, it.toString())
            }
        )
    },
    Setting(context, Settings.PREF_GESTURE_TRAIL_FADEOUT_DURATION, R.string.gesture_trail_fadeout_duration) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = Defaults.PREF_GESTURE_TRAIL_FADEOUT_DURATION,
            range = 100f..1900f,
            description = { stringResource(R.string.abbreviation_unit_milliseconds, (it + 100).toString()) },
            stepSize = 10,
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, SettingsWithoutKey.LOAD_GESTURE_LIB, R.string.load_gesture_library, R.string.load_gesture_library_summary) {
        LoadGestureLibPreference(it.title)
    },
    Setting(context, Settings.PREF_FULL_KEYBOARD_SWIPE, R.string.full_keyboard_swipe_gesture, R.string.full_keyboard_swipe_gesture_summary) {
        val items = listOf(
            stringResource(R.string.switch_language) to "switch_language",
            stringResource(R.string.space_swipe_move_cursor_entry) to "move_cursor",
            stringResource(R.string.space_swipe_toggle_numpad_entry) to "toggle_numpad",
            stringResource(R.string.space_swipe_hide_keyboard_entry) to "hide_keyboard",
            stringResource(R.string.action_none) to "none",
        )
        val ctx = LocalContext.current
        ListPreference(it, items, Defaults.PREF_FULL_KEYBOARD_SWIPE) { selected ->
            if (selected != "none") {
                ctx.prefs().edit { putBoolean(Settings.PREF_GESTURE_INPUT, false) }
            }
        }
    },
    Setting(context, Settings.PREF_LANGUAGE_SWIPE_DISTANCE, R.string.prefs_language_swipe_distance) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_LANGUAGE_SWIPE_DISTANCE,
            range = 2f..18f,
            description = { it.toString() }
        )
    },
    Setting(context, Settings.PREF_SPACE_HORIZONTAL_SWIPE, R.string.show_horizontal_space_swipe) {
        val items = listOf(
            stringResource(R.string.space_swipe_move_cursor_entry) to "move_cursor",
            stringResource(R.string.switch_language) to "switch_language",
            stringResource(R.string.space_swipe_toggle_numpad_entry) to "toggle_numpad",
            stringResource(R.string.action_none) to "none",
        )
        ListPreference(it, items, Defaults.PREF_SPACE_HORIZONTAL_SWIPE)
    },
    Setting(context, Settings.PREF_SPACE_VERTICAL_SWIPE, R.string.show_vertical_space_swipe) {
        val items = listOf(
            stringResource(R.string.space_swipe_move_cursor_entry) to "move_cursor",
            stringResource(R.string.switch_language) to "switch_language",
            stringResource(R.string.space_swipe_toggle_numpad_entry) to "toggle_numpad",
            stringResource(R.string.space_swipe_hide_keyboard_entry) to "hide_keyboard",
            stringResource(R.string.space_swipe_touchpad_mode_entry) to "touchpad_mode",
            stringResource(R.string.action_none) to "none",
        )
        ListPreference(it, items, Defaults.PREF_SPACE_VERTICAL_SWIPE)
    },
    Setting(context, Settings.PREF_SPACE_VERTICAL_DOWN_SWIPE, R.string.show_vertical_down_space_swipe) {
        val items = listOf(
            stringResource(R.string.space_swipe_move_cursor_entry) to "move_cursor",
            stringResource(R.string.switch_language) to "switch_language",
            stringResource(R.string.space_swipe_toggle_numpad_entry) to "toggle_numpad",
            stringResource(R.string.space_swipe_hide_keyboard_entry) to "hide_keyboard",
            stringResource(R.string.space_swipe_touchpad_mode_entry) to "touchpad_mode",
            stringResource(R.string.action_none) to "none",
        )
        ListPreference(it, items, Defaults.PREF_SPACE_VERTICAL_DOWN_SWIPE)
    },
    Setting(context, Settings.PREF_TOOLBAR_SWIPE_UP_GESTURE, R.string.toolbar_swipe_up_gesture) {
        val items = listOf(
            stringResource(R.string.space_swipe_move_cursor_entry) to "move_cursor",
            stringResource(R.string.switch_language) to "switch_language",
            stringResource(R.string.space_swipe_toggle_numpad_entry) to "toggle_numpad",
            stringResource(R.string.space_swipe_hide_keyboard_entry) to "hide_keyboard",
            stringResource(R.string.space_swipe_touchpad_mode_entry) to "touchpad_mode",
            stringResource(R.string.toolbar_swipe_more_suggestions_entry) to "more_suggestions",
            stringResource(R.string.action_none) to "none",
        )
        ListPreference(it, items, Defaults.PREF_TOOLBAR_SWIPE_UP_GESTURE)
    },
    Setting(context, Settings.PREF_TOOLBAR_SWIPE_DOWN_GESTURE, R.string.toolbar_swipe_down_gesture) {
        val items = listOf(
            stringResource(R.string.space_swipe_move_cursor_entry) to "move_cursor",
            stringResource(R.string.switch_language) to "switch_language",
            stringResource(R.string.space_swipe_toggle_numpad_entry) to "toggle_numpad",
            stringResource(R.string.space_swipe_hide_keyboard_entry) to "hide_keyboard",
            stringResource(R.string.space_swipe_touchpad_mode_entry) to "touchpad_mode",
            stringResource(R.string.toolbar_swipe_more_suggestions_entry) to "more_suggestions",
            stringResource(R.string.action_none) to "none",
        )
        val prefs = LocalContext.current.prefs()
        val defaultVal = if (!prefs.contains(Settings.PREF_TOOLBAR_SWIPE_DOWN_GESTURE)
            && prefs.getBoolean(Settings.PREF_TOOLBAR_SWIPE_DOWN_DISMISS, Defaults.PREF_TOOLBAR_SWIPE_DOWN_DISMISS)) {
            "hide_keyboard"
        } else {
            Defaults.PREF_TOOLBAR_SWIPE_DOWN_GESTURE
        }
        ListPreference(it, items, defaultVal)
    },
    Setting(context, Settings.PREF_VERTICAL_SWIPE_THRESHOLD, R.string.vertical_swipe_threshold, R.string.vertical_swipe_threshold_summary) {
        SliderPreference(
            name = it.title,
            key = it.key,
            default = Defaults.PREF_VERTICAL_SWIPE_THRESHOLD,
            range = 10f..60f,
            stepSize = 1,
            description = { value ->
                if (value.toInt() == Defaults.PREF_VERTICAL_SWIPE_THRESHOLD) {
                    "${value.toInt()} dp (${stringResource(R.string.button_default)})"
                } else {
                    "${value.toInt()} dp"
                }
            }
        )
    },
    Setting(context, Settings.PREF_TOUCHPAD_SENSITIVITY, R.string.touchpad_sensitivity) {
        SliderPreference(
            name = it.title,
            key = it.key,
            default = Defaults.PREF_TOUCHPAD_SENSITIVITY,
            range = 0f..100f,
            description = { value -> value.toInt().toString() }
        )
    },
    Setting(context, Settings.PREF_TOUCHPAD_FULLSCREEN, R.string.touchpad_fullscreen, R.string.touchpad_fullscreen_summary) {
        SwitchPreference(it, Defaults.PREF_TOUCHPAD_FULLSCREEN)
    },
    Setting(context, Settings.PREF_DELETE_SWIPE, R.string.delete_swipe, R.string.delete_swipe_summary) {
        SwitchPreference(it, Defaults.PREF_DELETE_SWIPE)
    },
    Setting(context, Settings.PREF_DELETE_SWIPE_WORD_BY_WORD, R.string.delete_swipe_word_by_word, R.string.delete_swipe_word_by_word_summary) {
        val prefs = LocalContext.current.prefs()
        SwitchPreference(
            it,
            Defaults.PREF_DELETE_SWIPE_WORD_BY_WORD,
            enabled = prefs.getBoolean(Settings.PREF_DELETE_SWIPE, Defaults.PREF_DELETE_SWIPE)
        )
    },
    Setting(context, Settings.PREF_DELETE_SWIPE_EXACT_LINES, R.string.delete_swipe_exact_lines, R.string.delete_swipe_exact_lines_summary) {
        val prefs = LocalContext.current.prefs()
        SwitchPreference(
            it,
            Defaults.PREF_DELETE_SWIPE_EXACT_LINES,
            enabled = prefs.getBoolean(Settings.PREF_DELETE_SWIPE, Defaults.PREF_DELETE_SWIPE)
        )
    },
)

@Preview
@Composable
private fun Preview() {
    JniUtils.sHaveGestureLib = true
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            GestureTypingScreen { }
        }
    }
}
