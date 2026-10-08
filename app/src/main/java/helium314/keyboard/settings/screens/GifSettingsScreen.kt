// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leanbitlab.leantype.gif.ProviderInfo
import helium314.keyboard.latin.R
import helium314.keyboard.latin.gif.GifPluginManager
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.dialogs.PreferenceDialog
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import kotlinx.coroutines.launch

private const val TAG = "GifSettingsScreen"
private const val PLUGIN_RELEASES_URL = "https://github.com/LeanBitLab/LeanType/releases"

@Composable
fun GifSettingsScreen(
    onClickBack: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember { GifPluginManager.get(context) }
    val scope = rememberCoroutineScope()

    DisposableEffect(manager) {
        manager.acquire("settings")
        onDispose {
            manager.release("settings")
        }
    }

    val connectionState by manager.state.collectAsState()

    var providers by remember { mutableStateOf<List<ProviderInfo>>(emptyList()) }
    var isLoadingProviders by remember { mutableStateOf(false) }
    var selectedProviderForConfig by remember { mutableStateOf<ProviderInfo?>(null) }

    fun refreshProviders() {
        if (connectionState == GifPluginManager.State.CONNECTED) {
            scope.launch {
                isLoadingProviders = true
                val result = manager.call { engine -> engine.listProviders() }
                result.onSuccess { list ->
                    providers = list
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
        } else {
            providers = emptyList()
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.gif_plugin_status_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    when (connectionState) {
                        GifPluginManager.State.NOT_INSTALLED -> {
                            Text(
                                text = stringResource(R.string.gif_plugin_not_installed_desc),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PLUGIN_RELEASES_URL)).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                }
                            ) {
                                Text(stringResource(R.string.gif_download_plugin))
                            }
                        }
                        GifPluginManager.State.UNTRUSTED_PLUGIN -> {
                            Text(
                                text = stringResource(R.string.gif_plugin_untrusted_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        GifPluginManager.State.CONNECTED -> {
                            Text(
                                text = stringResource(R.string.libraries_status_active),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        GifPluginManager.State.CONNECTING -> {
                            Text(
                                text = stringResource(R.string.gif_connecting),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        GifPluginManager.State.UNAVAILABLE -> {
                            Text(
                                text = stringResource(R.string.gif_error_unavailable),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.gif_plugin_unavailable_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        GifPluginManager.State.PLUGIN_TOO_OLD -> {
                            Text(
                                text = stringResource(R.string.gif_error_plugin_too_old),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        GifPluginManager.State.HOST_TOO_OLD -> {
                            Text(
                                text = stringResource(R.string.gif_error_host_too_old),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                            Text(
                                text = connectionState.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            if (connectionState == GifPluginManager.State.CONNECTED || providers.isNotEmpty()) {
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

                        if (isLoadingProviders) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else if (providers.isEmpty()) {
                            Text(
                                text = stringResource(R.string.gif_no_results),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            providers.forEach { provider ->
                                val statusText = if (provider.configured) {
                                    stringResource(R.string.gif_provider_configured)
                                } else {
                                    stringResource(R.string.gif_provider_not_configured)
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
        var credentialInput by remember { mutableStateOf("") }
        val targetField = provider.credentialFields.firstOrNull() ?: "api_key"

        PreferenceDialog(
            onDismissRequest = { selectedProviderForConfig = null },
            title = stringResource(R.string.gif_dialog_title),
            buttons = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (provider.configured) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val result = manager.call { engine ->
                                        engine.clearCredential(provider.id)
                                    }
                                    result.onSuccess {
                                        Toast.makeText(context, R.string.gif_api_key_cleared, Toast.LENGTH_SHORT).show()
                                        selectedProviderForConfig = null
                                        refreshProviders()
                                    }.onFailure {
                                        Toast.makeText(context, R.string.gif_error_generic, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Text(stringResource(R.string.gif_clear_key))
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f, fill = false))
                    TextButton(onClick = { selectedProviderForConfig = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Button(
                        onClick = {
                            if (credentialInput.isNotBlank()) {
                                scope.launch {
                                    val result = manager.call { engine ->
                                        engine.setCredential(provider.id, targetField, credentialInput.trim())
                                    }
                                    result.onSuccess {
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
                        Text(stringResource(R.string.gif_save))
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
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = credentialInput,
                    onValueChange = { credentialInput = it },
                    label = { Text(stringResource(R.string.gif_enter_api_key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
