// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.os.Build
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ColorThemePickerDialog
import helium314.keyboard.settings.dialogs.CustomizeIconsDialog
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.BackgroundImagePref
import helium314.keyboard.settings.preferences.CustomFontPreference
import helium314.keyboard.settings.preferences.MultiSliderPreference
import helium314.keyboard.settings.preferences.TextInputPreference
import helium314.keyboard.settings.previewDark
import androidx.core.content.edit
import helium314.keyboard.latin.settings.Settings

@Composable
fun AppearanceScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val dayNightMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, Defaults.PREF_THEME_DAY_NIGHT)
    val items = listOf(
        R.string.settings_screen_theme,
        Settings.PREF_THEME_STYLE,
        Settings.PREF_ICON_STYLE,
        Settings.PREF_CUSTOM_ICON_NAMES,
        Settings.PREF_THEME_COLORS,
        Settings.PREF_THEME_KEY_BORDERS,
        if (prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, Defaults.PREF_THEME_KEY_BORDERS))
            Settings.PREF_KEY_BORDER_RADIUS else null,
        if (prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, Defaults.PREF_THEME_KEY_BORDERS))
            Settings.PREF_KEY_BORDER_RADIUS_FUNCTIONAL else null,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            Settings.PREF_THEME_DAY_NIGHT else null,
        if (dayNightMode) Settings.PREF_THEME_COLORS_NIGHT else null,
        Settings.PREF_NAVBAR_COLOR,
        SettingsWithoutKey.BACKGROUND_IMAGE,
        SettingsWithoutKey.BACKGROUND_IMAGE_LANDSCAPE,
        R.string.settings_category_miscellaneous,
        Settings.PREF_PERSIST_FLOATING_KEYBOARD,
        Settings.PREF_REMEMBER_FLOATING_KEYBOARD,
        // ponytail: persist text edit mode settings item
        Settings.PREF_PERSIST_TEXT_EDIT_MODE,
        Settings.PREF_ENABLE_SPLIT_KEYBOARD,
        Settings.PREF_FOLDABLE_MODE,
        Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE,
        if (prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE, Defaults.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE)
            || prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, Defaults.PREF_ENABLE_SPLIT_KEYBOARD))
            Settings.PREF_SPLIT_SPACER_SCALE_PREFIX else null,
        Settings.PREF_NARROW_KEY_GAPS,
        if (prefs.getBoolean(Settings.PREF_NARROW_KEY_GAPS, Defaults.PREF_NARROW_KEY_GAPS))
            Settings.PREF_NARROW_KEY_GAPS_LEVEL else null,
        Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX,
        Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX,
        Settings.PREF_SIDE_PADDING_SCALE_PREFIX,
        Settings.PREF_SPACE_BAR_TEXT,
        SettingsWithoutKey.CUSTOM_FONT,
        Settings.PREF_FONT_SCALE,
        SettingsWithoutKey.CUSTOM_EMOJI_FONT,
        Settings.PREF_EMOJI_FONT_SCALE,
        Settings.PREF_USE_SYSTEM_EMOJI,
        if (prefs.getFloat(Settings.PREF_EMOJI_FONT_SCALE, Defaults.PREF_EMOJI_FONT_SCALE) != 1f)
            Settings.PREF_EMOJI_KEY_FIT else null,
        if (prefs.getInt(Settings.PREF_EMOJI_MAX_SDK, 0) >= 24)
            Settings.PREF_EMOJI_SKIN_TONE else null,
    )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_appearance),
        settings = items
    )
}

fun createAppearanceSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_THEME_STYLE, R.string.theme_style) { setting ->
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val items = KeyboardTheme.STYLES.map {
            it.getStringResourceOrName("style_name_", ctx) to it
        }
        ListPreference(
            setting,
            items,
            Defaults.PREF_ICON_STYLE
        ) {
            if (it != KeyboardTheme.STYLE_HOLO) {
                if (prefs.getString(Settings.PREF_THEME_COLORS, Defaults.PREF_THEME_COLORS) == KeyboardTheme.THEME_HOLO_WHITE)
                    prefs.edit { remove(Settings.PREF_THEME_COLORS) }
                if (prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, Defaults.PREF_THEME_COLORS_NIGHT) == KeyboardTheme.THEME_HOLO_WHITE)
                    prefs.edit { remove(Settings.PREF_THEME_COLORS_NIGHT) }
            }
            KeyboardIconsSet.needsReload = true // only relevant for Settings.PREF_CUSTOM_ICON_NAMES
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    },
    Setting(context, Settings.PREF_ICON_STYLE, R.string.icon_style) { setting ->
        val ctx = LocalContext.current
        val items = KeyboardTheme.STYLES.map { it.getStringResourceOrName("style_name_", ctx) to it }
        ListPreference(
            setting,
            items,
            Defaults.PREF_ICON_STYLE
        ) {
            KeyboardIconsSet.needsReload = true // only relevant for Settings.PREF_CUSTOM_ICON_NAMES
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    },
    Setting(context, Settings.PREF_CUSTOM_ICON_NAMES, R.string.customize_icons) { setting ->
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = setting.title,
            onClick = { showDialog = true }
        )
        if (showDialog) {
            KeyboardIconsSet.instance.loadIcons(LocalContext.current)
            CustomizeIconsDialog(setting.key) { showDialog = false }
        }
    },
    Setting(context, Settings.PREF_THEME_COLORS, R.string.theme_colors) { setting ->
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = setting.title,
            description = (prefs.getString(setting.key, Defaults.PREF_THEME_COLORS) ?: Defaults.PREF_THEME_COLORS).getStringResourceOrName("theme_name_", ctx),
            onClick = { showDialog = true }
        )
        if (showDialog)
            ColorThemePickerDialog(
                onDismissRequest = { showDialog = false },
                setting = setting,
                isNight = false,
                default = Defaults.PREF_THEME_COLORS
            )
    },
    Setting(context, Settings.PREF_THEME_COLORS_NIGHT, R.string.theme_colors_night) { setting ->
        val ctx = LocalContext.current
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        val prefs = ctx.prefs()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = setting.title,
            description = (prefs.getString(setting.key, Defaults.PREF_THEME_COLORS_NIGHT) ?: Defaults.PREF_THEME_COLORS_NIGHT).getStringResourceOrName("theme_name_", ctx),
            onClick = { showDialog = true }
        )
        if (showDialog)
            ColorThemePickerDialog(
                onDismissRequest = { showDialog = false },
                setting = setting,
                isNight = true,
                default = Defaults.PREF_THEME_COLORS_NIGHT
            )
    },
    Setting(context, Settings.PREF_THEME_KEY_BORDERS, R.string.key_borders) {
        SwitchPreference(it, Defaults.PREF_THEME_KEY_BORDERS) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEY_BORDER_RADIUS, R.string.key_border_radius) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_KEY_BORDER_RADIUS,
            range = 0f..20f,
            stepSize = 1,
            description = { radius ->
                val isDef = radius.toInt() == Defaults.PREF_KEY_BORDER_RADIUS.toInt() || radius < 0f
                if (isDef) "${Defaults.PREF_KEY_BORDER_RADIUS.toInt()}dp (${stringResource(R.string.button_default)})"
                else "${radius.toInt()}dp"
            }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEY_BORDER_RADIUS_FUNCTIONAL, R.string.key_border_radius_functional) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_KEY_BORDER_RADIUS_FUNCTIONAL,
            range = 0f..50f,
            stepSize = 1,
            description = { radius ->
                val isDef = radius.toInt() == Defaults.PREF_KEY_BORDER_RADIUS_FUNCTIONAL.toInt() || radius < 0f
                if (isDef) "${Defaults.PREF_KEY_BORDER_RADIUS_FUNCTIONAL.toInt()}dp (${stringResource(R.string.button_default)})"
                else "${radius.toInt()}dp"
            }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_THEME_DAY_NIGHT, R.string.day_night_mode, R.string.day_night_mode_summary) {
        SwitchPreference(it, Defaults.PREF_THEME_DAY_NIGHT) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_NAVBAR_COLOR, R.string.theme_navbar, R.string.day_night_mode_summary) {
        SwitchPreference(it, Defaults.PREF_NAVBAR_COLOR)
    },
    Setting(context, SettingsWithoutKey.BACKGROUND_IMAGE, R.string.customize_background_image) {
        BackgroundImagePref(it, false)
    },
    Setting(context, SettingsWithoutKey.BACKGROUND_IMAGE_LANDSCAPE,
        R.string.customize_background_image_landscape, R.string.summary_customize_background_image_landscape)
    {
        BackgroundImagePref(it, true)
    },
    Setting(context, Settings.PREF_ENABLE_SPLIT_KEYBOARD, R.string.enable_split_keyboard) {
        SwitchPreference(it, Defaults.PREF_ENABLE_SPLIT_KEYBOARD) { KeyboardSwitcher.getInstance().reloadKeyboard() }
    },
    Setting(context, Settings.PREF_FOLDABLE_MODE, R.string.pref_foldable_mode_title, R.string.pref_foldable_mode_summary) {
        SwitchPreference(it, false) {
            helium314.keyboard.latin.utils.ScreenProfileProvider.invalidateCache()
            KeyboardSwitcher.getInstance().reloadKeyboard()
        }
    },
    Setting(context, Settings.PREF_PERSIST_FLOATING_KEYBOARD, R.string.persist_floating_keyboard_title, R.string.persist_floating_keyboard_summary) {
        SwitchPreference(it, Defaults.PREF_PERSIST_FLOATING_KEYBOARD)
    },
    Setting(context, Settings.PREF_REMEMBER_FLOATING_KEYBOARD, R.string.remember_floating_keyboard_title, R.string.remember_floating_keyboard_summary) {
        SwitchPreference(it, Defaults.PREF_REMEMBER_FLOATING_KEYBOARD)
    },
    // ponytail: persist text edit mode preference widget
    Setting(context, Settings.PREF_PERSIST_TEXT_EDIT_MODE, R.string.persist_text_edit_mode_title, R.string.persist_text_edit_mode_summary) {
        SwitchPreference(it, Defaults.PREF_PERSIST_TEXT_EDIT_MODE)
    },
    Setting(context, Settings.PREF_SPLIT_SPACER_SCALE_PREFIX, R.string.split_spacer_scale) { setting ->
        MultiSliderPreference(
            name = setting.title,
            baseKey = setting.key,
            dimensions = listOf(stringResource(R.string.landscape)),
            defaults = Defaults.PREF_SPLIT_SPACER_SCALE,
            range = 0.5f..2f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE, R.string.enable_split_keyboard_landscape) {
        SwitchPreference(it, Defaults.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE) { KeyboardSwitcher.getInstance().reloadKeyboard() }
    },
    Setting(context, Settings.PREF_NARROW_KEY_GAPS, R.string.prefs_narrow_key_gaps) {
        SwitchPreference(it, Defaults.PREF_NARROW_KEY_GAPS) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_NARROW_KEY_GAPS_LEVEL, R.string.prefs_narrow_key_gaps_level) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_NARROW_KEY_GAPS_LEVEL,
            range = 0f..10f,
            stepSize = 1,
            description = { level -> if (level.toInt() == 0) "0 (Standard)" else level.toInt().toString() }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, R.string.prefs_keyboard_height_scale) { setting ->
        MultiSliderPreference(
            name = setting.title,
            baseKey = setting.key,
            dimensions = listOf(stringResource(R.string.landscape)),
            defaults = Defaults.PREF_KEYBOARD_HEIGHT_SCALE,
            range = 0.3f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, R.string.prefs_bottom_padding_scale) { setting ->
        MultiSliderPreference(
            name = setting.title,
            baseKey = setting.key,
            dimensions = listOf(stringResource(R.string.landscape)),
            defaults = Defaults.PREF_BOTTOM_PADDING_SCALE,
            range = 0f..5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_SIDE_PADDING_SCALE_PREFIX, R.string.prefs_side_padding_scale) { setting ->
        MultiSliderPreference(
            name = setting.title,
            baseKey = setting.key,
            dimensions = listOf(stringResource(R.string.landscape), stringResource(R.string.split)),
            defaults = Defaults.PREF_SIDE_PADDING_SCALE,
            range = 0f..3f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_SPACE_BAR_TEXT, R.string.prefs_space_bar_text) {
        TextInputPreference(it, Defaults.PREF_SPACE_BAR_TEXT)
    },
    Setting(context, SettingsWithoutKey.CUSTOM_FONT, R.string.custom_font) {
        CustomFontPreference(it, Settings.getCustomFontFile(LocalContext.current), R.string.custom_font)
    },
    Setting(context, Settings.PREF_FONT_SCALE, R.string.prefs_font_scale) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = Defaults.PREF_FONT_SCALE,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, SettingsWithoutKey.CUSTOM_EMOJI_FONT, R.string.custom_emoji_font) {
        CustomFontPreference(it, Settings.getCustomEmojiFontFile(LocalContext.current), R.string.custom_emoji_font)
    },
    Setting(context, Settings.PREF_EMOJI_FONT_SCALE, R.string.prefs_emoji_font_scale) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_EMOJI_FONT_SCALE,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_USE_SYSTEM_EMOJI, R.string.prefs_use_system_emoji, R.string.prefs_use_system_emoji_summary) { setting ->
        val ctx = LocalContext.current
        SwitchPreference(setting, Defaults.PREF_USE_SYSTEM_EMOJI) { newValue ->
            ctx.prefs().edit(commit = true) { putBoolean(Settings.PREF_USE_SYSTEM_EMOJI, newValue) }
            Runtime.getRuntime().exit(0)
        }
    },
    Setting(context, Settings.PREF_EMOJI_KEY_FIT, R.string.prefs_emoji_key_fit) {
        SwitchPreference(it, Defaults.PREF_EMOJI_KEY_FIT) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
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
)

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            AppearanceScreen { }
        }
    }
}
