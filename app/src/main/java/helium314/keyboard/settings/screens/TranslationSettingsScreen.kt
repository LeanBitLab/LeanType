// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.translation.TranslationLoader
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.TranslationModelDownloadDialog
import helium314.keyboard.settings.preferences.LoadTranslationPluginPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.settings.preferences.TranslationEnginePreference
import helium314.keyboard.settings.preferences.TranslationModePreference
import helium314.keyboard.settings.preferences.TranslationSourceLanguagePreference
import helium314.keyboard.settings.preferences.TranslationTargetLanguagePreference

@Composable
fun TranslationSettingsScreen(
    onClickBack: () -> Unit,
) {
    val context = LocalContext.current
    var translationInstalled by remember { mutableStateOf(TranslationLoader.hasPlugin(context)) }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.translation_settings_title),
        settings = emptyList()
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
                // Plugin Management Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory("Plugin Management")

                        LoadTranslationPluginPreference(
                            title = "Translation Plugin",
                            summary = if (translationInstalled) stringResource(R.string.libraries_status_active) else stringResource(R.string.libraries_status_not_installed),
                            icon = R.drawable.ic_translate,
                            onSuccess = { translationInstalled = TranslationLoader.hasPlugin(context) }
                        )
                    }
                }

                // General Translation Settings Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        // Translation Engine Selection (Auto / Plugin / AI) - Shown for all flavors with AI
                        if (BuildConfig.FLAVOR != "offline" || android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            TranslationEnginePreference()
                        }

                        // Translation Source Language Selection (Auto Detect by default)
                        TranslationSourceLanguagePreference()

                        // Translation Target Language Selection
                        TranslationTargetLanguagePreference()
                    }
                }

                // Offline ML Kit Translation Models Card
                if (translationInstalled) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column {
                            PreferenceCategory("Offline Models")

                            var showModelsDialog by remember { mutableStateOf(false) }
                            Preference(
                                name = stringResource(R.string.offline_translation_models_title),
                                description = stringResource(R.string.offline_translation_models_summary),
                                onClick = { showModelsDialog = true },
                                icon = R.drawable.ic_translate
                            )
                            if (showModelsDialog) {
                                val provider = remember { TranslationLoader.getProvider(context) }
                                if (provider != null) {
                                    TranslationModelDownloadDialog(
                                        provider = provider,
                                        onDismissRequest = { showModelsDialog = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
