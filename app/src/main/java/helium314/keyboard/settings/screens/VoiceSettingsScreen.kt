// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.leanbitlab.leantype.voice.ModelImportRequest
import com.leanbitlab.leantype.voice.ModelState
import com.leanbitlab.leantype.voice.VoiceConstants
import com.leanbitlab.leantype.voice.VoiceEngineInfo
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.Links
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.PreferenceDialog
import helium314.keyboard.settings.dialogs.VoiceModelDownloadDialog
import helium314.keyboard.settings.filePicker
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.preferences.TextInputPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.settings.SettingsActivity
import androidx.compose.runtime.collectAsState
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun VoiceSettingsScreen(
    onClickBack: () -> Unit,
    onClickAIIntegration: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.prefs()

    val prefChangeCounter = (context.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    val isOnlineFlavor = BuildConfig.FLAVOR == "standard" || BuildConfig.FLAVOR == "standardfull"
    val richImm = remember { RichInputMethodManager.getInstance() }
    val currentProvider = remember(prefChangeCounter?.value) { richImm.currentVoiceProvider }
    val isOfflineVoiceEnabled = currentProvider == VoiceConstants.VOICE_PROVIDER_OFFLINE
    val isOnlineVoiceEnabled = isOnlineFlavor && currentProvider == VoiceConstants.VOICE_PROVIDER_ONLINE
    val isThirdPartyVoiceEnabled = currentProvider == VoiceConstants.VOICE_PROVIDER_THIRD_PARTY
    val isVoiceDisabled = currentProvider == VoiceConstants.VOICE_PROVIDER_NONE

    var isMicPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isMicPermissionGranted = granted
    }

    val pluginManager = remember(context) { VoicePluginManager(context) }
    var engineInfo by remember { mutableStateOf<VoiceEngineInfo?>(pluginManager.getInfo()) }
    var isPluginConnected by remember { mutableStateOf(pluginManager.isPluginConnected()) }
    var isPluginInstalled by remember { mutableStateOf(pluginManager.isPluginInstalled()) }
    val pluginVersion = remember(isPluginInstalled) {
        try {
            context.packageManager.getPackageInfo(VoiceConstants.VOICE_PLUGIN_PACKAGE, 0).versionName
        } catch (_: Exception) {
            null
        }
    }
    var isInitialConnectionPending by remember { mutableStateOf(!isPluginConnected && isPluginInstalled) }
    val installedWhisperPref = remember(prefs) { prefs.getString("installed_model_${VoiceConstants.ENGINE_WHISPER}", null) }
    var whisperState by remember {
        mutableStateOf<ModelState?>(
            pluginManager.getModelState(VoiceConstants.ENGINE_WHISPER)
                ?: if (installedWhisperPref != null) ModelState(VoiceConstants.ENGINE_WHISPER, ModelState.STATE_READY, null) else null
        )
    }
    var showModelDownloadDialog by remember { mutableStateOf(false) }
    var showVoicePluginDialog by rememberSaveable { mutableStateOf(false) }

    var remoteVersion by remember { mutableStateOf<String?>(null) }
    val hasInternet = remember { VoiceDownloadDispatcher.hasInternetPermission(context) }
    var updateAvailable by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(isPluginInstalled) {
        if (!hasInternet) return@LaunchedEffect
        isCheckingUpdate = true
        scope.launch(Dispatchers.IO) {
            try {
                val url = URL(Links.VOICE_PLUGIN_RELEASES_API)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "HeliboardL")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.connect()
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val match = tagNameRegex.find(response)
                    if (match != null) {
                        val tag = match.groupValues[1]
                        remoteVersion = tag
                        if (isPluginInstalled && pluginVersion != null) {
                            updateAvailable = isUpdateAvailable(pluginVersion, tag)
                        }
                    }
                }
            } catch (_: Exception) {
                // ignore network errors
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    val updatePluginStatus = {
        isPluginInstalled = pluginManager.isPluginInstalled()
        if (pluginManager.isPluginConnected()) {
            isPluginConnected = true
            isInitialConnectionPending = false
            engineInfo = pluginManager.getInfo()
            whisperState = pluginManager.getModelState(VoiceConstants.ENGINE_WHISPER)
        } else if (!isInitialConnectionPending) {
            isPluginConnected = false
            engineInfo = null
            whisperState = if (installedWhisperPref != null) {
                ModelState(VoiceConstants.ENGINE_WHISPER, ModelState.STATE_READY, null)
            } else null
        }
    }

    DisposableEffect(context) {
        pluginManager.setConnectionListener(object : VoicePluginManager.PluginConnectionListener {
            override fun onPluginConnected(info: VoiceEngineInfo?) {
                isPluginConnected = true
                isInitialConnectionPending = false
                engineInfo = info
                updatePluginStatus()
            }

            override fun onPluginDisconnected() {
                isPluginConnected = false
                isInitialConnectionPending = false
                engineInfo = null
                whisperState = null
            }
        })
        val bound = pluginManager.bindIfNeeded()
        if (!bound) {
            isInitialConnectionPending = false
            updatePluginStatus()
        }

        onDispose {
            pluginManager.unbind()
        }
    }

    LaunchedEffect(Unit) {
        if (isInitialConnectionPending) {
            kotlinx.coroutines.delay(1200)
            isInitialConnectionPending = false
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            updatePluginStatus()
            val delayMs = if (whisperState?.state == ModelState.STATE_READY) 5000L else 1500L
            kotlinx.coroutines.delay(delayMs)
        }
    }

    val whisperPicker = filePicker { uri ->
        scope.launch(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val size = pfd.statSize
                    val request = ModelImportRequest(
                        engineType = VoiceConstants.ENGINE_WHISPER,
                        language = "multilingual",
                        sha256 = null,
                        sizeBytes = size,
                        file = pfd
                    )
                    if (!pluginManager.isPluginConnected()) {
                        pluginManager.bindIfNeeded()
                    }
                    pluginManager.importModelSafely(request)
                    withContext(Dispatchers.Main) {
                        prefs.edit().putString("installed_model_${VoiceConstants.ENGINE_WHISPER}", "custom").apply()
                        Toast.makeText(context, "Whisper model import dispatched", Toast.LENGTH_SHORT).show()
                        updatePluginStatus()
                    }
                }
            } catch (e: Exception) {
                Log.e("VoiceSettingsScreen", "Failed to import Whisper model", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Model import failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val providerItems = remember(isOnlineFlavor) {
        val list = mutableListOf<Pair<String, String>>()
        list.add(context.getString(R.string.voice_provider_offline) to VoiceConstants.VOICE_PROVIDER_OFFLINE)
        if (isOnlineFlavor) {
            list.add(context.getString(R.string.voice_provider_online) to VoiceConstants.VOICE_PROVIDER_ONLINE)
        }
        list.add(context.getString(R.string.voice_provider_third_party) to VoiceConstants.VOICE_PROVIDER_THIRD_PARTY)
        list.add(context.getString(R.string.voice_provider_none) to VoiceConstants.VOICE_PROVIDER_NONE)
        list
    }

    val providerSetting = remember(isOnlineFlavor, currentProvider) {
        Setting(
            key = VoiceConstants.PREF_VOICE_PROVIDER,
            title = context.getString(R.string.voice_provider_title),
            description = context.getString(R.string.voice_provider_summary)
        ) {
            ListPreference(
                setting = it,
                items = providerItems,
                default = currentProvider,
                icon = R.drawable.sym_keyboard_voice_holo,
                onChanged = { newProvider ->
                    richImm.setVoiceProvider(newProvider)
                }
            )
        }
    }

    val voiceAppItems = remember(context, prefChangeCounter?.value) {
        val pm = context.packageManager
        val imis = mutableMapOf<String, String>()
        richImm.shortcuts.forEach {
            val label = it.imi.loadLabel(pm)?.toString() ?: it.imi.packageName
            imis[it.imi.id] = label
        }
        richImm.getInstalledVoiceImis().forEach {
            val label = it.loadLabel(pm)?.toString() ?: it.packageName
            if (!imis.containsKey(it.id)) {
                imis[it.id] = label
            }
        }
        val list = mutableListOf<Pair<String, String>>()
        list.add(context.getString(R.string.voice_third_party_system_default) to VoiceConstants.VOICE_APP_SYSTEM_DEFAULT)
        imis.forEach { (id, label) ->
            list.add(label to id)
        }
        list
    }

    val voiceAppSetting = remember(voiceAppItems) {
        Setting(
            key = VoiceConstants.PREF_VOICE_THIRD_PARTY_APP,
            title = context.getString(R.string.voice_third_party_app_title)
        ) {
            ListPreference(
                setting = it,
                items = voiceAppItems,
                default = VoiceConstants.VOICE_APP_SYSTEM_DEFAULT,
                icon = R.drawable.ic_settings_preferences
            )
        }
    }

    val whisperKeepLoadedSetting = remember {
        Setting(
            key = VoiceConstants.PREF_VOICE_WHISPER_KEEP_LOADED_SECONDS,
            title = "Keep Whisper Loaded"
        ) {
            ListPreference(
                setting = it,
                items = listOf(
                    "Always keep in memory" to "-1",
                    "Keep in memory for 15 minutes" to "900",
                    "Keep in memory for 5 minutes" to "300",
                    "Keep in memory for 1 minute" to "60",
                    "Unload immediately after session" to "0"
                ),
                default = "300",
                icon = R.drawable.ic_settings_advanced
            )
        }
    }

    val voiceLanguageItems = remember(context) { buildVoiceLanguageEntries(context) }
    val voiceLanguageSetting = remember {
        Setting(
            key = VoiceConstants.PREF_VOICE_LANGUAGE,
            title = context.getString(R.string.pref_voice_language_title)
        ) {
            ListPreference(
                setting = it,
                items = voiceLanguageItems,
                default = VoiceConstants.VOICE_LANG_FOLLOW_KEYBOARD,
                icon = R.drawable.ic_settings_languages
            )
        }
    }

    val silenceTimeoutSetting = remember {
        Setting(
            key = VoiceConstants.PREF_VOICE_SILENCE_TIMEOUT_SECONDS,
            title = "Silence Timeout"
        ) {
            ListPreference(
                setting = it,
                items = listOf(
                    "2 seconds (Fastest)" to "2",
                    "3 seconds" to "3",
                    "5 seconds (Recommended)" to "5",
                    "7 seconds" to "7",
                    "10 seconds" to "10",
                    "15 seconds" to "15",
                    "Never (Listen until mic tapped)" to "0"
                ),
                default = "5",
                icon = R.drawable.ic_settings_preferences
            )
        }
    }

    val micSensitivitySetting = remember {
        Setting(
            key = VoiceConstants.PREF_VOICE_MIC_SENSITIVITY,
            title = "Microphone Sensitivity"
        ) {
            ListPreference(
                setting = it,
                items = listOf(
                    "High (Quiet rooms / Whisper)" to "high",
                    "Standard (Recommended)" to "normal",
                    "Low (Noisy environments / In-car)" to "low"
                ),
                default = "normal",
                icon = R.drawable.sym_keyboard_voice_holo
            )
        }
    }

    val maxDurationSetting = remember {
        Setting(
            key = VoiceConstants.PREF_VOICE_MAX_DURATION_SECONDS,
            title = "Max Recording Duration"
        ) {
            ListPreference(
                setting = it,
                items = listOf(
                    "15 seconds" to "15",
                    "30 seconds (Default)" to "30",
                    "60 seconds" to "60",
                    "Unlimited" to "0"
                ),
                default = "30",
                icon = R.drawable.ic_settings_preferences
            )
        }
    }

    val smartPunctuationSetting = remember {
        Setting(
            key = VoiceConstants.PREF_VOICE_SMART_PUNCTUATION,
            title = "Smart Punctuation",
            description = "Automatically add punctuation and sentence capitalization"
        ) {
            SwitchPreference(
                setting = it,
                default = true,
                icon = R.drawable.ic_settings_correction
            )
        }
    }

    val autoRetrySetting = remember {
        Setting(
            key = VoiceConstants.PREF_VOICE_AUTO_RETRY,
            title = context.getString(R.string.pref_voice_auto_retry_title),
            description = context.getString(R.string.pref_voice_auto_retry_summary)
        ) {
            SwitchPreference(
                setting = it,
                default = true,
                icon = R.drawable.ic_redo_rounded
            )
        }
    }

    val cpuThreadsSetting = remember {
        Setting(
            key = VoiceConstants.PREF_VOICE_CPU_THREADS,
            title = "CPU Inference Threads"
        ) {
            ListPreference(
                setting = it,
                items = listOf(
                    "2 threads (Battery saver)" to "2",
                    "4 threads (Recommended)" to "4",
                    "6 threads (High performance)" to "6",
                    "8 threads (Maximum speed)" to "8"
                ),
                default = "4",
                icon = R.drawable.ic_settings_advanced
            )
        }
    }

    val customPromptSetting = remember {
        Setting(
            key = VoiceConstants.PREF_VOICE_CUSTOM_PROMPT,
            title = "Vocabulary & Context Prompt",
            description = "Guide Whisper with technical terms, names, slang, or jargon"
        ) {
            TextInputPreference(
                setting = it,
                default = "",
                icon = R.drawable.ic_edit
            )
        }
    }

    if (showModelDownloadDialog) {
        VoiceModelDownloadDialog(
            onDismissRequest = { showModelDownloadDialog = false },
            pluginManager = pluginManager,
            whisperState = whisperState,
            onRefresh = { updatePluginStatus() },
            onImportLocalFile = {
                val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                whisperPicker.launch(intent)
            }
        )
    }

    if (showVoicePluginDialog) {
        PreferenceDialog(
            onDismissRequest = { showVoicePluginDialog = false },
            title = "Voice Plugin",
            showCloseButton = true,
            buttons = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isPluginInstalled || updateAvailable) {
                        Button(
                            onClick = {
                                showVoicePluginDialog = false
                                val url = if (updateAvailable) "${Links.VOICE_PLUGIN_REPO}/releases" else Links.VOICE_PLUGIN_REPO
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (updateAvailable) "View Update on GitHub" else "Download Plugin from GitHub")
                        }
                    }
                    if (isPluginInstalled) {
                        if (!isPluginConnected && !isInitialConnectionPending && !updateAvailable) {
                            Button(
                                onClick = {
                                    pluginManager.bindIfNeeded()
                                    updatePluginStatus()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Connect")
                            }
                        }
                        Button(
                            onClick = {
                                showVoicePluginDialog = false
                                val appInfoIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${VoiceConstants.VOICE_PLUGIN_PACKAGE}")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(appInfoIntent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Uninstall")
                        }
                    }
                }
            }
        ) {
            val message = when {
                isPluginInstalled && updateAvailable -> "An update is available for the voice plugin!\nInstalled version: $pluginVersion\nLatest version: $remoteVersion\n\nVisit the GitHub release page to download and update."
                isPluginConnected -> "Voice plugin is active (version ${pluginVersion ?: "v1.0.0"}).\n\nLeanType Voice Plugin handles high-performance on-device Whisper speech-to-text inference."
                isPluginInstalled -> "Voice plugin is installed on this device, but currently disconnected.\n\nTap Connect to establish connection."
                remoteVersion != null -> "Download the latest voice plugin (version $remoteVersion) from GitHub to enable private, fast offline voice typing."
                else -> "Offline voice input requires the LeanType Voice Plugin (com.leanbitlab.leantype.voice.offline).\n\nDownload the voice plugin from GitHub to enable private, fast offline voice typing."
            }
            Text(message)
        }
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = context.getString(R.string.voice_input_title),
        settings = emptyList()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
                // Card 1: Voice Input Provider & Routing
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory(stringResource(R.string.voice_provider_title))

                        providerSetting.Preference()

                        if (isOfflineVoiceEnabled) {
                            val voicePluginSummary = remember(isPluginInstalled, isPluginConnected, pluginVersion, updateAvailable, remoteVersion) {
                                when {
                                    updateAvailable -> "Update available ($pluginVersion → $remoteVersion)"
                                    isPluginConnected -> "Active (${pluginVersion ?: "v1.0.0"})"
                                    isPluginInstalled -> "Installed (Disconnected)"
                                    else -> "Not installed"
                                }
                            }

                            Preference(
                                name = "Voice Plugin",
                                description = voicePluginSummary,
                                icon = R.drawable.sym_keyboard_voice_holo,
                                onClick = { showVoicePluginDialog = true }
                            )
                        }

                        if (isOnlineVoiceEnabled) {
                            val service = remember { helium314.keyboard.latin.utils.ProofreadService(context) }
                            val provider = service.getProvider()
                            val voiceModelName = when (provider) {
                                helium314.keyboard.latin.utils.ProofreadService.AIProvider.GROQ ->
                                    service.getVoiceGroqModel().ifBlank { helium314.keyboard.latin.utils.GroqModels.DEFAULT_VOICE_MODEL }
                                helium314.keyboard.latin.utils.ProofreadService.AIProvider.GEMINI ->
                                    service.getVoiceGeminiModel().ifBlank { helium314.keyboard.latin.utils.ProofreadService.DEFAULT_VOICE_GEMINI_MODEL }
                                helium314.keyboard.latin.utils.ProofreadService.AIProvider.OPENAI ->
                                    service.getVoiceHuggingFaceModel().ifBlank { helium314.keyboard.latin.utils.ProofreadService.DEFAULT_VOICE_HF_MODEL }
                            }
                            Preference(
                                name = "AI Provider & Voice Model",
                                description = "${provider.name} • $voiceModelName",
                                icon = R.drawable.ic_proofread,
                                onClick = onClickAIIntegration
                            )
                            autoRetrySetting.Preference()
                        }

                        if (isThirdPartyVoiceEnabled) {
                            if (voiceAppItems.size <= 1 && !richImm.hasInstalledVoiceImis()) {
                                Preference(
                                    name = stringResource(R.string.voice_no_app_found),
                                    description = null,
                                    icon = R.drawable.sym_keyboard_voice_holo,
                                    onClick = {}
                                )
                            }
                            voiceAppSetting.Preference()
                            Preference(
                                name = stringResource(R.string.voice_open_system_settings),
                                description = stringResource(R.string.voice_open_system_settings_summary),
                                icon = R.drawable.ic_settings_preferences,
                                onClick = {
                                    val intent = Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            })
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open settings: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }

                        if (isVoiceDisabled) {
                            Preference(
                                name = stringResource(R.string.voice_provider_none),
                                description = stringResource(R.string.voice_provider_none_summary),
                                icon = R.drawable.sym_keyboard_voice_holo,
                                onClick = {}
                            )
                        }
                    }
                }

                if (isOfflineVoiceEnabled || isOnlineVoiceEnabled) {
                    // Card 2: Permissions
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column {
                            PreferenceCategory("Permissions")

                            Preference(
                                name = "Microphone Permission",
                                description = if (isMicPermissionGranted) "Permission granted" else "Tap to grant microphone permission for voice dictation",
                                onClick = {
                                    if (!isMicPermissionGranted) {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                icon = R.drawable.sym_keyboard_voice_holo
                            )
                        }
                    }

                    // Card 3: Engine & Models / Speech Language
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column {
                            PreferenceCategory(if (isOfflineVoiceEnabled) "Engine & Models" else "Speech Language")

                            if (isOfflineVoiceEnabled) {
                                val (badgeText, badgeContainerColor, badgeContentColor) = when (whisperState?.state) {
                                    ModelState.STATE_READY -> Triple("Ready", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                                    ModelState.STATE_LOADING -> Triple("Loading…", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                    ModelState.STATE_ERROR -> Triple("Error", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                    else -> if (isPluginConnected) {
                                        Triple("No model", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else if (isInitialConnectionPending) {
                                        Triple("Connecting…", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        Triple("Disconnected", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Preference(
                                    name = "Manage & Download Models",
                                    description = null,
                                    icon = R.drawable.sym_keyboard_voice_holo,
                                    onClick = {
                                        showModelDownloadDialog = true
                                    },
                                    value = {
                                        androidx.compose.material3.Surface(
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                            color = badgeContainerColor
                                        ) {
                                            Text(
                                                text = badgeText,
                                                color = badgeContentColor,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                )
                            }

                            voiceLanguageSetting.Preference()
                        }
                    }

                    // Card 4: Dictation & Behavior
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column {
                            PreferenceCategory("Dictation & Behavior")

                            smartPunctuationSetting.Preference()
                            silenceTimeoutSetting.Preference()
                            micSensitivitySetting.Preference()
                            maxDurationSetting.Preference()
                        }
                    }

                    // Card 5: Performance & Advanced
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column {
                            PreferenceCategory("Performance & Advanced")

                            if (isOfflineVoiceEnabled) {
                                cpuThreadsSetting.Preference()
                            }
                            customPromptSetting.Preference()
                            if (isOfflineVoiceEnabled) {
                                whisperKeepLoadedSetting.Preference()
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

private val WHISPER_LANGUAGE_CODES = arrayOf(
    "af", "am", "ar", "as", "az", "ba", "be", "bg", "bn", "bo", "br", "bs", "ca", "cs", "cy", "da",
    "de", "el", "en", "es", "et", "eu", "fa", "fi", "fo", "fr", "gl", "gu", "ha", "haw", "he", "hi",
    "hr", "ht", "hu", "hy", "id", "is", "it", "ja", "jw", "ka", "kk", "km", "kn", "ko", "la", "lb",
    "ln", "lo", "lt", "lv", "mg", "mi", "mk", "ml", "mn", "mr", "ms", "mt", "my", "ne", "nl", "nn",
    "no", "oc", "pa", "pl", "ps", "pt", "ro", "ru", "sa", "sd", "si", "sk", "sl", "sn", "so", "sq",
    "sr", "su", "sv", "sw", "ta", "te", "tg", "th", "tk", "tl", "tr", "tt", "uk", "ur", "uz", "vi",
    "yi", "yo", "yue", "zh"
)

private fun buildVoiceLanguageEntries(context: android.content.Context): List<Pair<String, String>> {
    val sysLocale = context.resources.configuration.locales[0]
    val list = mutableListOf<Pair<String, String>>()
    list.add(context.getString(R.string.voice_lang_follow_keyboard) to VoiceConstants.VOICE_LANG_FOLLOW_KEYBOARD)
    list.add(context.getString(R.string.voice_lang_auto_detect) to VoiceConstants.VOICE_LANG_AUTO)

    val langItems = WHISPER_LANGUAGE_CODES.map { code ->
        val loc = java.util.Locale.forLanguageTag(code)
        val name = loc.getDisplayName(sysLocale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(sysLocale) else it.toString() }
        "$name ($code)" to code
    }.sortedBy { it.first.lowercase(sysLocale) }

    list.addAll(langItems)
    return list
}

private fun isUpdateAvailable(local: String, remote: String): Boolean {
    val cleanLocal = local.removePrefix("v").trim()
    val cleanRemote = remote.removePrefix("v").trim()
    if (cleanLocal == cleanRemote) return false

    val localParts = cleanLocal.split(".").mapNotNull { it.toIntOrNull() }
    val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }

    val maxLength = maxOf(localParts.size, remoteParts.size)
    for (i in 0 until maxLength) {
        val localPart = localParts.getOrElse(i) { 0 }
        val remotePart = remoteParts.getOrElse(i) { 0 }
        if (remotePart > localPart) return true
        if (localPart > remotePart) return false
    }
    return false
}

private val tagNameRegex = "\"tag_name\"\\s*:\\s*\"([^\"]+)\"".toRegex()

fun createVoiceSettings(context: Context): List<Setting> = listOf(
    Setting(
        key = VoiceConstants.PREF_VOICE_PROVIDER,
        title = context.getString(R.string.voice_provider_title),
        description = context.getString(R.string.voice_provider_summary)
    ) { setting ->
        val richImm = RichInputMethodManager.getInstance()
        val currentProvider = richImm.currentVoiceProvider
        val isOnlineFlavor = BuildConfig.FLAVOR == "standard" || BuildConfig.FLAVOR == "standardfull"
        val providerItems = buildList<Pair<String, String>> {
            if (isOnlineFlavor) {
                add(context.getString(R.string.voice_provider_online) to VoiceConstants.VOICE_PROVIDER_ONLINE)
            }
            add(context.getString(R.string.voice_provider_offline) to VoiceConstants.VOICE_PROVIDER_OFFLINE)
            add(context.getString(R.string.voice_provider_third_party) to VoiceConstants.VOICE_PROVIDER_THIRD_PARTY)
            add(context.getString(R.string.voice_provider_none) to VoiceConstants.VOICE_PROVIDER_NONE)
        }
        ListPreference(
            setting = setting,
            items = providerItems,
            default = currentProvider,
            icon = R.drawable.sym_keyboard_voice_holo,
            onChanged = { newProvider ->
                richImm.setVoiceProvider(newProvider)
            }
        )
    }
)
