// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leanbitlab.leantype.gif.ProviderInfo
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.gif.GifPluginManager
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.PreferenceDialog
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import kotlinx.coroutines.launch

private const val TAG = "GifSettingsScreen"

@Composable
fun GifSettingsScreen(
    onClickBack: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember { GifPluginManager.get(context) }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.prefs() }
    var activeProviderId by remember {
        mutableStateOf(prefs.getString(Settings.PREF_GIF_PROVIDER, "") ?: "")
    }

    DisposableEffect(manager) {
        manager.acquire("settings")
        onDispose {
            manager.release("settings")
        }
    }

    val connectionState by manager.state.collectAsState()

    var providers by remember { mutableStateOf<List<ProviderInfo>>(manager.cachedProviders) }
    var isLoadingProviders by remember { mutableStateOf(false) }
    var selectedProviderForConfig by remember { mutableStateOf<ProviderInfo?>(null) }

    fun refreshProviders() {
        if (connectionState == GifPluginManager.State.CONNECTED) {
            scope.launch {
                if (providers.isEmpty()) {
                    isLoadingProviders = true
                }
                val result = manager.call { engine -> engine.listProviders() }
                result.onSuccess { list ->
                    providers = list
                    manager.updateCachedProviders(list)
                }.onFailure { e ->
                    Log.w(TAG, "Failed to list providers", e)
                }
                isLoadingProviders = false
            }
        }
    }

    LaunchedEffect(connectionState) {
        if (connectionState == GifPluginManager.State.CONNECTED) {
            refreshProviders()
        } else if (connectionState == GifPluginManager.State.NOT_INSTALLED || connectionState == GifPluginManager.State.UNTRUSTED_PLUGIN) {
            providers = emptyList()
        }
    }

    var showGifPluginDialog by rememberSaveable { mutableStateOf(false) }

    if (showGifPluginDialog) {
        PreferenceDialog(
            onDismissRequest = { showGifPluginDialog = false },
            title = stringResource(R.string.gif_plugin_status_title),
            showCloseButton = true,
            buttons = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (connectionState == GifPluginManager.State.NOT_INSTALLED) {
                        Button(
                            onClick = {
                                showGifPluginDialog = false
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Links.GIF_PLUGIN_REPO)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.gif_download_plugin))
                        }
                    } else {
                        if (connectionState != GifPluginManager.State.CONNECTED && connectionState != GifPluginManager.State.CONNECTING) {
                            Button(
                                onClick = {
                                    manager.retry()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.gif_retry))
                            }
                        }
                        Button(
                            onClick = {
                                showGifPluginDialog = false
                                val appInfoIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${GifPluginManager.PLUGIN_PACKAGE}")
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
                            Text(stringResource(R.string.voice_plugin_uninstall))
                        }
                    }
                }
            }
        ) {
            val message = when (connectionState) {
                GifPluginManager.State.NOT_INSTALLED -> stringResource(R.string.gif_plugin_not_installed_desc)
                GifPluginManager.State.UNTRUSTED_PLUGIN -> stringResource(R.string.gif_plugin_untrusted_desc)
                GifPluginManager.State.CONNECTED -> stringResource(R.string.libraries_status_active)
                GifPluginManager.State.CONNECTING -> stringResource(R.string.gif_connecting)
                GifPluginManager.State.UNAVAILABLE -> stringResource(R.string.gif_plugin_unavailable_desc)
                GifPluginManager.State.PLUGIN_TOO_OLD -> stringResource(R.string.gif_error_plugin_too_old)
                GifPluginManager.State.HOST_TOO_OLD -> stringResource(R.string.gif_error_host_too_old)
                else -> connectionState.name
            }
            Text(message)
        }
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.gif_settings_title),
        settings = emptyList()
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column {
                    PreferenceCategory(stringResource(R.string.gif_plugin_status_title))

                    val gifPluginSummary = when (connectionState) {
                        GifPluginManager.State.CONNECTED -> stringResource(R.string.libraries_status_active)
                        GifPluginManager.State.CONNECTING -> stringResource(R.string.gif_connecting)
                        GifPluginManager.State.NOT_INSTALLED -> stringResource(R.string.libraries_status_not_installed)
                        GifPluginManager.State.UNTRUSTED_PLUGIN -> stringResource(R.string.gif_error_untrusted)
                        GifPluginManager.State.PLUGIN_TOO_OLD -> stringResource(R.string.gif_error_plugin_too_old)
                        GifPluginManager.State.HOST_TOO_OLD -> stringResource(R.string.gif_error_host_too_old)
                        GifPluginManager.State.UNAVAILABLE -> stringResource(R.string.gif_error_unavailable)
                        else -> connectionState.name
                    }

                    Preference(
                        name = stringResource(R.string.gif_settings_title),
                        description = gifPluginSummary,
                        icon = R.drawable.ic_gif,
                        onClick = { showGifPluginDialog = true }
                    )
                }
            }

            val showProvidersCard = connectionState != GifPluginManager.State.NOT_INSTALLED &&
                connectionState != GifPluginManager.State.UNTRUSTED_PLUGIN
            if (showProvidersCard) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory(stringResource(R.string.gif_providers_title))

                        if (isLoadingProviders && providers.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else if (providers.isEmpty()) {
                            val emptyText = if (connectionState == GifPluginManager.State.CONNECTING) {
                                stringResource(R.string.gif_connecting)
                            } else {
                                stringResource(R.string.gif_no_results)
                            }
                            Text(
                                text = emptyText,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val effectiveActiveId = when {
                                activeProviderId == "disabled" -> ""
                                activeProviderId.isNotEmpty() -> {
                                    if (Settings.isGifProviderEnabled(prefs, activeProviderId)) activeProviderId else ""
                                }
                                else -> {
                                    providers.firstOrNull { it.configured && Settings.isGifProviderEnabled(prefs, it.id) }?.id ?: ""
                                }
                            }
                            providers.forEach { provider ->
                                val isEnabled = Settings.isGifProviderEnabled(prefs, provider.id)
                                val isActive = provider.configured && isEnabled && provider.id == effectiveActiveId
                                val statusText = when {
                                    isActive -> "${stringResource(R.string.gif_provider_configured)} • ${stringResource(R.string.gif_provider_active)}"
                                    !isEnabled -> stringResource(R.string.gif_provider_disabled)
                                    provider.configured -> stringResource(R.string.gif_provider_configured)
                                    else -> stringResource(R.string.gif_provider_not_configured)
                                }
                                Preference(
                                    name = provider.displayName,
                                    description = "${provider.id} ($statusText)",
                                    onClick = {
                                        selectedProviderForConfig = provider
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedProviderForConfig?.let { provider ->
        val targetField = provider.credentialFields.firstOrNull() ?: "api_key"
        val prefKey = "gif_cred_${provider.id}_${targetField}"
        var credentialInput by remember(provider.id) {
            mutableStateOf(prefs.getString(prefKey, "") ?: "")
        }

        LaunchedEffect(provider.id) {
            val result = manager.call { engine -> engine.getCredential(provider.id, targetField) }
            result.onSuccess { savedKey ->
                if (!savedKey.isNullOrEmpty()) {
                    credentialInput = savedKey
                    prefs.edit().putString(prefKey, savedKey).apply()
                }
            }
        }

        val effectiveActiveId = when {
            activeProviderId == "disabled" -> ""
            activeProviderId.isNotEmpty() -> {
                if (Settings.isGifProviderEnabled(prefs, activeProviderId)) activeProviderId else ""
            }
            else -> {
                providers.firstOrNull { it.configured && Settings.isGifProviderEnabled(prefs, it.id) }?.id ?: ""
            }
        }
        val isEnabled = Settings.isGifProviderEnabled(prefs, provider.id)
        val isCurrentActive = provider.configured && isEnabled && provider.id == effectiveActiveId

        PreferenceDialog(
            onDismissRequest = { selectedProviderForConfig = null },
            title = if (provider.credentialFields.isNotEmpty()) {
                stringResource(R.string.gif_dialog_title)
            } else {
                stringResource(R.string.gif_settings_title)
            },
            buttons = {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (provider.configured && provider.credentialFields.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val result = manager.call { engine ->
                                        engine.clearCredential(provider.id)
                                    }
                                    result.onSuccess {
                                        prefs.edit().remove(prefKey).apply()
                                        Settings.setGifProviderEnabled(prefs, provider.id, false)
                                        if (activeProviderId == provider.id) {
                                            val fallback = providers.firstOrNull { it.id != provider.id && it.configured && Settings.isGifProviderEnabled(prefs, it.id) }?.id ?: "disabled"
                                            prefs.edit().putString(Settings.PREF_GIF_PROVIDER, fallback).apply()
                                            activeProviderId = fallback
                                        }
                                        Toast.makeText(context, R.string.gif_api_key_cleared, Toast.LENGTH_SHORT).show()
                                        selectedProviderForConfig = null
                                        refreshProviders()
                                    }.onFailure {
                                        Toast.makeText(context, R.string.gif_error_generic, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.storage_button_clear))
                        }
                    }
                    if (provider.configured) {
                        Button(
                            onClick = {
                                val willBeEnabled = !isEnabled
                                Settings.setGifProviderEnabled(prefs, provider.id, willBeEnabled)
                                if (willBeEnabled) {
                                    prefs.edit().putString(Settings.PREF_GIF_PROVIDER, provider.id).apply()
                                    activeProviderId = provider.id
                                } else {
                                    if (activeProviderId == provider.id || effectiveActiveId == provider.id) {
                                        val nextActive = providers.firstOrNull { it.id != provider.id && it.configured && Settings.isGifProviderEnabled(prefs, it.id) }?.id
                                        val newActive = nextActive ?: "disabled"
                                        prefs.edit().putString(Settings.PREF_GIF_PROVIDER, newActive).apply()
                                        activeProviderId = newActive
                                    }
                                }
                                selectedProviderForConfig = null
                            }
                        ) {
                            Text(stringResource(if (isEnabled) R.string.gif_disable else R.string.gif_enable))
                        }
                    }
                    if (provider.credentialFields.isNotEmpty()) {
                        Button(
                            onClick = {
                                if (credentialInput.isNotBlank()) {
                                    scope.launch {
                                        val trimmed = credentialInput.trim()
                                        val result = manager.call { engine ->
                                            engine.setCredential(provider.id, targetField, trimmed)
                                        }
                                        result.onSuccess {
                                            Settings.setGifProviderEnabled(prefs, provider.id, true)
                                            prefs.edit()
                                                .putString(prefKey, trimmed)
                                                .putString(Settings.PREF_GIF_PROVIDER, provider.id)
                                                .apply()
                                            activeProviderId = provider.id
                                            Toast.makeText(context, R.string.gif_api_key_saved, Toast.LENGTH_SHORT).show()
                                            selectedProviderForConfig = null
                                            refreshProviders()
                                        }.onFailure {
                                            Toast.makeText(context, R.string.gif_error_generic, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            enabled = credentialInput.isNotBlank()
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.titleSmall
                )
                if (provider.configured) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            isCurrentActive -> "${stringResource(R.string.gif_provider_configured)} • ${stringResource(R.string.gif_provider_active)}"
                            !isEnabled -> stringResource(R.string.gif_provider_disabled)
                            else -> stringResource(R.string.gif_provider_configured)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isCurrentActive -> MaterialTheme.colorScheme.primary
                            !isEnabled -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                if (provider.id == "local") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent().apply {
                                component = android.content.ComponentName("com.leanbitlab.leantype.gif", "com.leanbitlab.leantype.gif.MainActivity")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, R.string.gif_error_unavailable, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.gif_manage_local_media))
                    }
                } else if (provider.credentialFields.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = credentialInput,
                        onValueChange = { credentialInput = it },
                        label = { Text(stringResource(R.string.gif_enter_api_key)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val uriHandler = LocalUriHandler.current
                    val apiKeyUrl = when (provider.id) {
                        "giphy" -> "https://developers.giphy.com/dashboard/"
                        "klipy" -> "https://developer.klipy.co/"
                        else -> null
                    }
                    if (apiKeyUrl != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = { uriHandler.openUri(apiKeyUrl) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(stringResource(R.string.gif_get_api_key))
                        }
                    }
                }
            }
        }
    }
}
