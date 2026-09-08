// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.graphics.drawable.VectorDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.dpToPx
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.upgradeToolbarPrefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ToolbarKeysCustomizer
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.ReorderSwitchPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.previewDark

@Composable
fun ToolbarScreen(
    onClickBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = context.prefs()
    LaunchedEffect(Unit) {
        upgradeToolbarPrefs(prefs)
    }
    val b = (context.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val toolbarMode = Settings.readToolbarMode(prefs)
    val clipboardToolbarVisible = toolbarMode != ToolbarMode.HIDDEN
        || !prefs.getBoolean(Settings.PREF_TOOLBAR_HIDING_GLOBAL, Defaults.PREF_TOOLBAR_HIDING_GLOBAL)
    val isSplitToolbar = prefs.getBoolean(Settings.PREF_SPLIT_TOOLBAR, Defaults.PREF_SPLIT_TOOLBAR)
    val items = listOf(
        if (!isSplitToolbar) Settings.PREF_TOOLBAR_MODE else null,
        Settings.PREF_SPLIT_TOOLBAR,
        Settings.PREF_AUTO_SPAN_TOOLBAR_KEYS,
        if (toolbarMode == ToolbarMode.HIDDEN) Settings.PREF_TOOLBAR_HIDING_GLOBAL else null,
        Settings.PREF_TOOLBAR_KEYS,
        if (!isSplitToolbar) Settings.PREF_PINNED_TOOLBAR_KEYS else null,
        Settings.PREF_CLIPBOARD_TOOLBAR_KEYS,
        Settings.PREF_TOOLBAR_KEYS_ALIGNMENT,
        Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES,
        Settings.PREF_TOOLBAR_LONG_PRESS_HINT,
        if (toolbarMode == ToolbarMode.EXPANDABLE && !isSplitToolbar) Settings.PREF_QUICK_PIN_TOOLBAR_KEYS else null,
        if (toolbarMode == ToolbarMode.EXPANDABLE && !isSplitToolbar) Settings.PREF_AUTO_SHOW_TOOLBAR else null,
        if (toolbarMode == ToolbarMode.EXPANDABLE && !isSplitToolbar) Settings.PREF_AUTO_SHOW_TOOLBAR_ON_SELECT else null,
        if (toolbarMode == ToolbarMode.EXPANDABLE && !isSplitToolbar) Settings.PREF_AUTO_HIDE_TOOLBAR else null,
        if (toolbarMode == ToolbarMode.EXPANDABLE && !isSplitToolbar) Settings.PREF_AUTO_HIDE_PINNED_KEYS else null,
        if (toolbarMode == ToolbarMode.EXPANDABLE) Settings.PREF_REMEMBER_TOOLBAR_STATE else null,
        if (toolbarMode != ToolbarMode.HIDDEN) Settings.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD else null,
        if (toolbarMode != ToolbarMode.HIDDEN) Settings.PREF_VARIABLE_TOOLBAR_DIRECTION else null,
        Settings.PREF_TOOLBAR_SWIPE_DOWN_DISMISS,
    )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_toolbar),
        settings = items
    )
}

fun createToolbarSettings(context: Context): List<Setting> {
    val filter = { name: String ->
        val lowerName = name.lowercase()
        when {
            lowerName.startsWith("custom_ai_") || lowerName == "proofread" -> {
                if (BuildConfig.FLAVOR == "offline") {
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                } else {
                    BuildConfig.FLAVOR == "standard" || BuildConfig.FLAVOR == "standardfull"
                }
            }
            else -> true
        }
    }
    return listOfNotNull(
        Setting(context, Settings.PREF_TOOLBAR_MODE, R.string.toolbar_mode) { setting ->
            val ctx = LocalContext.current
            val items =
                ToolbarMode.entries.map { it.name.lowercase().getStringResourceOrName("toolbar_mode_", ctx) to it.name }
            ListPreference(
                setting,
                items,
                Defaults.PREF_TOOLBAR_MODE
            ) {
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            }
        },
        Setting(context, Settings.PREF_TOOLBAR_HIDING_GLOBAL, R.string.toolbar_hiding_global) {
            SwitchPreference(it, Defaults.PREF_TOOLBAR_HIDING_GLOBAL) {
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            }
        },
        Setting(context, Settings.PREF_AUTO_SPAN_TOOLBAR_KEYS, R.string.auto_span_toolbar_keys, R.string.auto_span_toolbar_keys_summary) {
            SwitchPreference(it, Defaults.PREF_AUTO_SPAN_TOOLBAR_KEYS) {
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            }
        },
        Setting(context, Settings.PREF_TOOLBAR_KEYS, R.string.toolbar_keys) {
            ReorderSwitchPreference(it, Defaults.PREF_TOOLBAR_KEYS, filter)
        },
        Setting(context, Settings.PREF_PINNED_TOOLBAR_KEYS, R.string.pinned_toolbar_keys) {
            ReorderSwitchPreference(it, Defaults.PREF_PINNED_TOOLBAR_KEYS, filter)
        },
        Setting(context, Settings.PREF_CLIPBOARD_TOOLBAR_KEYS, R.string.clipboard_toolbar_keys) {
            ReorderSwitchPreference(it, Defaults.PREF_CLIPBOARD_TOOLBAR_KEYS, filter)
        },
        Setting(context, Settings.PREF_TOOLBAR_KEYS_ALIGNMENT, R.string.toolbar_keys_alignment, R.string.toolbar_keys_alignment_summary) { setting ->
            val items = listOf(
                stringResource(R.string.toolbar_keys_alignment_left) to "left",
                stringResource(R.string.toolbar_keys_alignment_center) to "center",
                stringResource(R.string.toolbar_keys_alignment_right) to "right"
            )
            ListPreference(
                setting,
                items,
                Defaults.PREF_TOOLBAR_KEYS_ALIGNMENT
            ) {
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            }
        },
        Setting(context, Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES, R.string.customize_toolbar_key_codes) {
            var showDialog by rememberSaveable { mutableStateOf(false) }
            Preference(
                name = it.title,
                onClick = { showDialog = true },
            )
            if (showDialog)
                ToolbarKeysCustomizer(
                    key = it.key,
                    onDismissRequest = { showDialog = false }
                )
        },
        Setting(context, Settings.PREF_QUICK_PIN_TOOLBAR_KEYS,
            R.string.quick_pin_toolbar_keys, R.string.quick_pin_toolbar_keys_summary)
        {
            SwitchPreference(it, Defaults.PREF_QUICK_PIN_TOOLBAR_KEYS) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
        },
        Setting(context, Settings.PREF_TOOLBAR_LONG_PRESS_HINT,
            R.string.toolbar_long_press_hint, R.string.toolbar_long_press_hint_summary)
        {
            SwitchPreference(it, Defaults.PREF_TOOLBAR_LONG_PRESS_HINT) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
        },
        Setting(context, Settings.PREF_AUTO_SHOW_TOOLBAR, R.string.auto_show_toolbar_open, R.string.auto_show_toolbar_summary)
        {
            SwitchPreference(it, Defaults.PREF_AUTO_SHOW_TOOLBAR)
        },
        Setting(context, Settings.PREF_AUTO_SHOW_TOOLBAR_ON_SELECT, R.string.auto_show_toolbar_select, R.string.auto_show_toolbar_select_summary)
        {
            SwitchPreference(it, Defaults.PREF_AUTO_SHOW_TOOLBAR_ON_SELECT)
        },
        Setting(context, Settings.PREF_AUTO_HIDE_TOOLBAR, R.string.auto_hide_toolbar, R.string.auto_hide_toolbar_summary)
        {
            SwitchPreference(it, Defaults.PREF_AUTO_HIDE_TOOLBAR)
        },
        Setting(context, Settings.PREF_AUTO_HIDE_PINNED_KEYS, R.string.auto_hide_pinned_keys, R.string.auto_hide_pinned_keys_summary)
        {
            SwitchPreference(it, Defaults.PREF_AUTO_HIDE_PINNED_KEYS) { _ ->
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            }
        },
        Setting(context, Settings.PREF_REMEMBER_TOOLBAR_STATE, R.string.remember_toolbar_state, R.string.remember_toolbar_state_summary)
        {
            SwitchPreference(it, Defaults.PREF_REMEMBER_TOOLBAR_STATE)
        },
        Setting(context, Settings.PREF_TOOLBAR_SWIPE_DOWN_DISMISS,
            R.string.toolbar_swipe_down_dismiss, R.string.toolbar_swipe_down_dismiss_summary)
        {
            SwitchPreference(it, Defaults.PREF_TOOLBAR_SWIPE_DOWN_DISMISS)
        },
        Setting(context, Settings.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD,
            R.string.toolbar_only_with_hw_keyboard, R.string.toolbar_only_with_hw_keyboard_summary)
        {
            SwitchPreference(it, Defaults.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD) {
                KeyboardSwitcher.getInstance().setThemeNeedsReload() // necessary for updating insets
            }
        },
        Setting(context, Settings.PREF_VARIABLE_TOOLBAR_DIRECTION,
            R.string.var_toolbar_direction, R.string.var_toolbar_direction_summary)
        {
            SwitchPreference(it, Defaults.PREF_VARIABLE_TOOLBAR_DIRECTION)
        },
        Setting(context, Settings.PREF_SPLIT_TOOLBAR, R.string.split_toolbar, R.string.split_toolbar_summary) {
            val prefs = LocalContext.current.prefs()
            SwitchPreference(it, Defaults.PREF_SPLIT_TOOLBAR) { isEnabled ->
                if (isEnabled) {
                    val currentMode = Settings.readToolbarMode(prefs)
                    if (currentMode == ToolbarMode.TOOLBAR_KEYS || currentMode == ToolbarMode.SUGGESTION_STRIP) {
                        prefs.edit {
                            putString(Settings.PREF_TOOLBAR_MODE, ToolbarMode.EXPANDABLE.name)
                        }
                    }
                    prefs.edit {
                        putBoolean(Settings.PREF_AUTO_SHOW_TOOLBAR, false)
                        putBoolean(Settings.PREF_AUTO_HIDE_TOOLBAR, false)
                        putBoolean(Settings.PREF_AUTO_SHOW_TOOLBAR_ON_SELECT, false)
                        putBoolean(Settings.PREF_QUICK_PIN_TOOLBAR_KEYS, false)
                    }
                } else {
                    // Restore pinned keys to default if they were previously cleared
                    val currentPinned = prefs.getString(Settings.PREF_PINNED_TOOLBAR_KEYS, "")
                    if (currentPinned.isNullOrEmpty()) {
                        prefs.edit {
                            putString(Settings.PREF_PINNED_TOOLBAR_KEYS, Defaults.PREF_PINNED_TOOLBAR_KEYS)
                        }
                    }
                }
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            }
        },
        Setting(
            context,
            Settings.PREF_SHOW_DOWNLOAD_BUTTON_IN_TOOLBAR,
            R.string.show_download_button_in_toolbar,
            R.string.show_download_button_in_toolbar_summary
        ) {
            SwitchPreference(it, Defaults.PREF_SHOW_DOWNLOAD_BUTTON_IN_TOOLBAR) {
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            }
        }
    )
}

@Composable
fun KeyboardIconsSet.GetIcon(name: String?) {
    val ctx = LocalContext.current
    val drawable = getNewDrawable(name, ctx)
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        val iconId = iconIds[name?.lowercase()]
        if (drawable is VectorDrawable && iconId != null)
            Icon(painterResource(iconId), name, Modifier.fillMaxSize(0.8f))
        else if (drawable != null) {
            val px = with(LocalDensity.current) { 40.dp.toPx() }.toInt()
            Icon(drawable.toBitmap(px, px).asImageBitmap(), name, Modifier.fillMaxSize(0.8f))
        }
    }
}

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            ToolbarScreen { }
        }
    }
}
