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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.handwriting.HandwritingLoader
import helium314.keyboard.latin.ocr.OcrPluginLoader
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.sound.SoundPackImporter
import helium314.keyboard.latin.sound.SoundPackUrls
import helium314.keyboard.latin.translation.TranslationLoader
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import android.content.Context
import helium314.keyboard.settings.NextScreenIcon
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.settings.preferences.SwitchPreference

@Composable
fun LibrariesHubScreen(
    onClickBack: () -> Unit,
    onClickOfflineVoice: () -> Unit = {},
    onClickTranslation: () -> Unit = {},
    onClickHandwriting: () -> Unit = {},
    onClickOcr: () -> Unit = {},
    onClickAIIntegration: () -> Unit = {},
    onClickSound: () -> Unit = {},
    onClickAppProfiles: () -> Unit = { SettingsDestination.navigateTo(SettingsDestination.AppQuirks) },
    onClickGif: () -> Unit = { SettingsDestination.navigateTo(SettingsDestination.Gif) },
) {
    val context = LocalContext.current
    val prefs = remember { context.prefs() }
    val uriHandler = LocalUriHandler.current

    val b = (context.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0) {
        // Trigger recomposition on preference changes
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.plugins_title),
        settings = emptyList(),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
                // Section 1: Active Plugin Engines
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory(stringResource(R.string.libraries_active_engines_title))

                        // Offline AI Plugin (Only available in offline flavor)
                        val isOfflineAiSupported = BuildConfig.FLAVOR == "offline" &&
                            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        if (isOfflineAiSupported) {
                            val aiPluginInstalled = helium314.keyboard.latin.ai.OfflineAiLoader.hasPlugin(context)
                            val aiSummary = when {
                                aiPluginInstalled -> {
                                    val version = helium314.keyboard.latin.ai.OfflineAiLoader.getPluginVersion(context)
                                    if (version != null) "${stringResource(R.string.libraries_status_active)} (v$version)"
                                    else stringResource(R.string.libraries_status_active)
                                }
                                else -> stringResource(R.string.libraries_status_not_installed)
                            }
                            Preference(
                                name = stringResource(R.string.load_offline_ai_plugin),
                                description = aiSummary,
                                onClick = onClickAIIntegration,
                                icon = R.drawable.ic_proofread
                            ) { NextScreenIcon() }
                        }

                        // Handwriting Input Plugin (ML Kit based)
                        val isHandwritingSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        val handwritingInstalled = isHandwritingSupported && HandwritingLoader.hasPlugin(context)
                        val summary = when {
                            !isHandwritingSupported -> stringResource(R.string.libraries_requires_android, "8.0+")
                            handwritingInstalled -> stringResource(R.string.libraries_status_active)
                            else -> stringResource(R.string.libraries_status_not_installed)
                        }
                        Preference(
                            name = stringResource(R.string.libraries_hub_handwriting_title),
                            description = summary,
                            onClick = if (isHandwritingSupported) onClickHandwriting else ({}),
                            enabled = isHandwritingSupported,
                            icon = R.drawable.ic_edit
                        ) { if (isHandwritingSupported) NextScreenIcon() }

                        // Text Recognition (OCR) Plugin (ML Kit based)
                        val isOcrSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                        val ocrInstalled = isOcrSupported && OcrPluginLoader.hasPlugin(context)
                        val ocrSummary = when {
                            !isOcrSupported -> stringResource(R.string.libraries_requires_android, "8.0+")
                            ocrInstalled -> stringResource(R.string.libraries_status_active)
                            else -> stringResource(R.string.libraries_status_not_installed)
                        }
                        Preference(
                            name = stringResource(R.string.ocr_title),
                            description = ocrSummary,
                            onClick = if (isOcrSupported) onClickOcr else ({}),
                            enabled = isOcrSupported,
                            icon = R.drawable.ic_ocr
                        ) { if (isOcrSupported) NextScreenIcon() }

                        // Voice Input
                        val richImm = remember { helium314.keyboard.latin.RichInputMethodManager.getInstance() }
                        val voiceProvider = richImm.currentVoiceProvider
                        val voicePluginManager = remember { helium314.keyboard.latin.voice.VoicePluginManager(context) }
                        val voiceInstalled = voicePluginManager.isPluginInstalled()
                        val voiceSummary = when (voiceProvider) {
                            com.leanbitlab.leantype.voice.VoiceConstants.VOICE_PROVIDER_ONLINE -> stringResource(R.string.voice_provider_online_ai)
                            com.leanbitlab.leantype.voice.VoiceConstants.VOICE_PROVIDER_OFFLINE -> {
                                if (voiceInstalled) stringResource(R.string.libraries_status_active)
                                else stringResource(R.string.libraries_status_not_installed)
                            }
                            com.leanbitlab.leantype.voice.VoiceConstants.VOICE_PROVIDER_THIRD_PARTY -> stringResource(R.string.voice_provider_system_third_party)
                            com.leanbitlab.leantype.voice.VoiceConstants.VOICE_PROVIDER_NONE -> stringResource(R.string.voice_provider_none)
                            else -> if (voiceInstalled) stringResource(R.string.installed) else stringResource(R.string.libraries_status_not_installed)
                        }
                        Preference(
                            name = stringResource(R.string.voice_input_title),
                            description = voiceSummary,
                            onClick = onClickOfflineVoice,
                            icon = R.drawable.sym_keyboard_voice_holo
                        ) { NextScreenIcon() }

                        // Translation Settings Screen (available for all flavors)
                        val isTranslationSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N
                        val translationInstalled = isTranslationSupported && TranslationLoader.hasPlugin(context)
                        val translationSummary = when {
                            !isTranslationSupported -> stringResource(R.string.libraries_requires_android, "7.0+")
                            translationInstalled -> stringResource(R.string.libraries_status_active)
                            else -> stringResource(R.string.libraries_status_not_installed)
                        }
                        Preference(
                            name = stringResource(R.string.translation_settings_title),
                            description = translationSummary,
                            onClick = if (isTranslationSupported) onClickTranslation else ({}),
                            enabled = isTranslationSupported,
                            icon = R.drawable.ic_translate
                        ) { if (isTranslationSupported) NextScreenIcon() }

                        // GIF Search Plugin
                        val gifManager = remember { helium314.keyboard.latin.gif.GifPluginManager.get(context) }
                        val gifState = gifManager.state.collectAsState()
                        val gifSummary = when (gifState.value) {
                            helium314.keyboard.latin.gif.GifPluginManager.State.CONNECTED -> stringResource(R.string.libraries_status_active)
                            helium314.keyboard.latin.gif.GifPluginManager.State.UNTRUSTED_PLUGIN -> stringResource(R.string.gif_error_untrusted)
                            helium314.keyboard.latin.gif.GifPluginManager.State.NOT_INSTALLED -> stringResource(R.string.libraries_status_not_installed)
                            else -> stringResource(R.string.installed)
                        }
                        Preference(
                            name = stringResource(R.string.gif_settings_title),
                            description = gifSummary,
                            onClick = onClickGif,
                            icon = R.drawable.ic_gif
                        ) { NextScreenIcon() }

                        // Keypress Audio & Sound Packs Plugin
                        val currentSoundStyle = prefs.getString(Settings.PREF_KEYPRESS_SOUND_STYLE, Defaults.PREF_KEYPRESS_SOUND_STYLE) ?: Defaults.PREF_KEYPRESS_SOUND_STYLE
                        val soundStyleName = when {
                            currentSoundStyle == SoundPackUrls.SYSTEM_DEFAULT_ID -> stringResource(R.string.prefs_keypress_sound_style_system)
                            else -> SoundPackImporter.getManifest(context, currentSoundStyle)?.name ?: currentSoundStyle
                        }
                        val soundEnabled = prefs.getBoolean(Settings.PREF_SOUND_ON, Defaults.PREF_SOUND_ON)
                        val soundSummary = if (soundEnabled) {
                            "${stringResource(R.string.libraries_status_active)} ($soundStyleName)"
                        } else {
                            stringResource(R.string.physical_keyboard_shortcut_disabled)
                        }
                        Preference(
                            name = stringResource(R.string.sound_packs_title),
                            description = soundSummary,
                            onClick = onClickSound,
                            icon = R.drawable.ic_play_arrow
                        ) { NextScreenIcon() }
                    }
                }

                // Section 2: App Profiles
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory(stringResource(R.string.settings_category_app_profiles))

                        Preference(
                            name = stringResource(R.string.app_quirks_title),
                            description = stringResource(R.string.app_quirks_summary),
                            onClick = onClickAppProfiles,
                            icon = R.drawable.ic_settings_preferences
                        ) { NextScreenIcon() }

                        SwitchPreference(
                            name = stringResource(R.string.save_subtype_per_app),
                            key = Settings.PREF_SAVE_SUBTYPE_PER_APP,
                            default = Defaults.PREF_SAVE_SUBTYPE_PER_APP,
                            icon = R.drawable.ic_ime_switcher
                        )
                    }
                }

                // Section 3: Storage & Maintenance
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory(stringResource(R.string.libraries_storage_category_title))

                        Preference(
                            name = stringResource(R.string.libraries_storage_cache_title),
                            description = stringResource(R.string.libraries_storage_cache_desc),
                            onClick = { SettingsDestination.navigateTo(SettingsDestination.Storage) },
                            icon = R.drawable.ic_settings_advanced
                        ) { NextScreenIcon() }
                    }
                }

                // Section 4: Documentation
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory(stringResource(R.string.libraries_documentation_title))

                        Preference(
                            name = stringResource(R.string.libraries_features_guide_title),
                            description = stringResource(R.string.libraries_features_guide_desc),
                            onClick = { uriHandler.openUri("https://github.com/LeanBitLab/HeliboardL/blob/main/docs/FEATURES.md") },
                            icon = R.drawable.ic_settings_about_wiki
                        ) { NextScreenIcon() }
                    }
                }
            }
        }
    }

fun createLibrariesSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_SAVE_SUBTYPE_PER_APP, R.string.save_subtype_per_app) {
        SwitchPreference(it, Defaults.PREF_SAVE_SUBTYPE_PER_APP, icon = R.drawable.ic_ime_switcher)
    },
    Setting(context, SettingsWithoutKey.APP_QUIRKS, R.string.app_quirks_title) {
        Preference(
            name = stringResource(R.string.app_quirks_title),
            description = stringResource(R.string.app_quirks_summary),
            onClick = { SettingsDestination.navigateTo(SettingsDestination.AppQuirks) },
            icon = R.drawable.ic_settings_preferences
        ) { NextScreenIcon() }
    },
)
