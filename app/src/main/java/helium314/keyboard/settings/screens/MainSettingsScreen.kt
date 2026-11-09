// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.NextScreenIcon
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.updates.AppUpdatesManager
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.SponsorDialog
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.previewDark

@Composable
fun MainSettingsScreen(
    onClickUpdates: () -> Unit,
    onClickAbout: () -> Unit,
    onClickTextCorrection: () -> Unit,
    onClickSuggestions: () -> Unit,
    onClickPreferences: () -> Unit,
    onClickToolbar: () -> Unit,
    onClickGestureTyping: () -> Unit,
    onClickAdvanced: () -> Unit,
    onClickAppearance: () -> Unit,
    onClickLanguage: () -> Unit,
    onClickLibraries: () -> Unit,
    onClickAIIntegration: () -> Unit,
    onClickBack: () -> Unit,
) {
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.ime_settings),
        settings = emptyList(),
    ) {
        val enabledSubtypes = remember { SubtypeSettings.getEnabledSubtypes(true) }
        val enabledSubtypesDescription = remember(enabledSubtypes) {
            enabledSubtypes.joinToString(", ") { it.displayName() }
        }
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val neverShowAgain = prefs.getBoolean(
            Settings.PREF_DONT_SHOW_SPONSOR_DIALOG,
            Defaults.PREF_DONT_SHOW_SPONSOR_DIALOG
        )
        val lastShown = prefs.getLong(
            Settings.PREF_LAST_SPONSOR_DIALOG_SHOWN,
            Defaults.PREF_LAST_SPONSOR_DIALOG_SHOWN
        )
        var settingsOpenCount by remember { mutableStateOf(prefs.getInt("settings_open_count", 0)) }
        LaunchedEffect(Unit) {
            val newCount = settingsOpenCount + 1
            prefs.edit { putInt("settings_open_count", newCount) }
            settingsOpenCount = newCount
            AppUpdatesManager.checkUpdatesIfNeeded(ctx)
        }
        val updateTrigger by AppUpdatesManager.updateState.collectAsState()
        val hasAnyUpdate = remember(updateTrigger) { AppUpdatesManager.hasAnyUpdate(ctx) }
        val sixDaysMs = 6L * 24 * 60 * 60 * 1000
        val shouldShow = !neverShowAgain && (
            (lastShown == 0L && settingsOpenCount >= 2) ||
            (lastShown > 0L && System.currentTimeMillis() - lastShown >= sixDaysMs)
        )
        var showSponsorDialog by rememberSaveable { mutableStateOf(false) }
        
        if (shouldShow) {
            LaunchedEffect(Unit) {
                showSponsorDialog = true
            }
        }
        
        if (showSponsorDialog) {
            LaunchedEffect(Unit) {
                prefs.edit { putLong(Settings.PREF_LAST_SPONSOR_DIALOG_SHOWN, System.currentTimeMillis()) }
            }
            SponsorDialog(
                onDismissRequest = { showSponsorDialog = false },
                onSponsorGitHub = {
                    val intent = Intent()
                    intent.data = Links.SPONSOR.toUri()
                    intent.action = Intent.ACTION_VIEW
                    ctx.startActivity(intent)
                },
                onSponsorOpenCollective = {
                    val intent = Intent()
                    intent.data = Links.OPEN_COLLECTIVE.toUri()
                    intent.action = Intent.ACTION_VIEW
                    ctx.startActivity(intent)
                },
                prefs = prefs
            )
        }
        
        val imm = remember { ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }
        val isImeEnabled = UncachedInputMethodManagerUtils.isThisImeEnabled(ctx, imm)
        val isImeCurrent = UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)

        Scaffold(contentWindowInsets = WindowInsets(0)) { innerPadding ->
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(vertical = 8.dp)
            ) {
                if (!isImeEnabled || !isImeCurrent) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        onClick = {
                            (ctx.getActivity() as? SettingsActivity)?.reopenWelcomeWizard()
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ime_switcher),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(end = 12.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.setup_not_active_title, stringResource(R.string.english_ime_name)),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.setup_not_active_summary),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            NextScreenIcon()
                        }
                    }
                }

                // Group 1: General (AI, Languages, Preferences, Appearance, Toolbar)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        if (BuildConfig.FLAVOR == "standard" || BuildConfig.FLAVOR == "standardfull") {
                            Preference(
                                name = stringResource(R.string.settings_screen_ai_integration),
                                onClick = onClickAIIntegration,
                                icon = R.drawable.ic_proofread
                            ) { NextScreenIcon() }
                        }
                        Preference(
                            name = stringResource(R.string.plugins_title),
                            onClick = onClickLibraries,
                            icon = R.drawable.ic_emoji_objects
                        ) { NextScreenIcon() }
                        Preference(
                            name = stringResource(R.string.language_and_layouts_title),
                            description = enabledSubtypesDescription,
                            onClick = onClickLanguage,
                            icon = R.drawable.ic_settings_languages
                        ) { NextScreenIcon() }
                        Preference(
                            name = stringResource(R.string.settings_screen_preferences),
                            onClick = onClickPreferences,
                            icon = R.drawable.ic_settings_preferences
                        ) { NextScreenIcon() }
                        Preference(
                            name = stringResource(R.string.settings_screen_appearance),
                            onClick = onClickAppearance,
                            icon = R.drawable.ic_settings_appearance
                        ) { NextScreenIcon() }
                        Preference(
                            name = stringResource(R.string.settings_screen_toolbar),
                            onClick = onClickToolbar,
                            icon = R.drawable.ic_settings_toolbar
                        ) { NextScreenIcon() }
                    }
                }

                // Group 2: Typing (Gesture, Correction, Dictionaries)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        Preference(
                            name = stringResource(R.string.settings_screen_gesture),
                            onClick = onClickGestureTyping,
                            icon = R.drawable.ic_settings_gesture
                        ) { NextScreenIcon() }
                        Preference(
                            name = stringResource(R.string.settings_screen_correction),
                            onClick = onClickTextCorrection,
                            icon = R.drawable.ic_settings_correction
                        ) { NextScreenIcon() }
                        Preference(
                            name = stringResource(R.string.settings_screen_suggestions),
                            onClick = onClickSuggestions,
                            icon = R.drawable.ic_settings_suggestions
                        ) { NextScreenIcon() }
                        Preference(
                            name = stringResource(R.string.text_expander_title),
                            onClick = { helium314.keyboard.settings.SettingsDestination.navigateTo(helium314.keyboard.settings.SettingsDestination.TextExpander) },
                            icon = R.drawable.ic_edit
                        ) { NextScreenIcon() }
                    }
                }

                // Group 3: Other (Advanced, About)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        Preference(
                            name = stringResource(R.string.settings_screen_advanced),
                            onClick = onClickAdvanced,
                            icon = R.drawable.ic_settings_advanced
                        ) { NextScreenIcon() }
                        Preference(
                            name = stringResource(R.string.settings_screen_updates),
                            onClick = onClickUpdates,
                            icon = R.drawable.ic_settings_updates
                        ) { NextScreenIcon(hasUpdateBadge = hasAnyUpdate) }
                        Preference(
                            name = stringResource(R.string.settings_screen_about),
                            onClick = onClickAbout,
                            icon = R.drawable.ic_settings_about
                        ) { NextScreenIcon() }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewScreen() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            MainSettingsScreen({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}
