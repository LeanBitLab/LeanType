// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.cjk.CjkPluginLoader
import helium314.keyboard.settings.FeedbackManager
import helium314.keyboard.settings.dialogs.PreferenceDialog
import helium314.keyboard.settings.filePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LoadCjkPluginPreference(
    title: String,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    restartOnSuccess: Boolean = true,
    onSuccess: (() -> Unit)? = null,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var isDownloading by rememberSaveable { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var remoteVersion by remember { mutableStateOf<String?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val hasInternet = remember {
        ctx.packageManager.checkPermission(
            "android.permission.INTERNET",
            ctx.packageName
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    var hasPlugin by remember { mutableStateOf(CjkPluginLoader.hasPlugin(ctx)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPlugin = CjkPluginLoader.hasPlugin(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val localVersion = remember(hasPlugin) { CjkPluginLoader.getPluginVersion(ctx) }
    val updateAvailable = remember(localVersion, remoteVersion) {
        val remVersion = remoteVersion
        if (localVersion != null && remVersion != null) {
            PluginUpdateHelper.isUpdateAvailable(localVersion, remVersion)
        } else {
            false
        }
    }

    LaunchedEffect(hasPlugin) {
        if (!hasInternet) return@LaunchedEffect
        isCheckingUpdate = true
        scope.launch(Dispatchers.IO) {
            PluginUpdateHelper.fetchLatestReleaseTag("LeanBitLab/LeanType-CJK-Plugin")?.let {
                remoteVersion = it
            }
            isCheckingUpdate = false
        }
    }

    val launcher = filePicker { uri ->
        val success = CjkPluginLoader.loadPluginFromUri(ctx, uri)
        showDialog = false
        if (success) {
            FeedbackManager.message(ctx, R.string.cjk_plugin_loaded_restarting)
            hasPlugin = true
            onSuccess?.invoke()
            if (restartOnSuccess) {
                scope.launch {
                    delay(2000)
                    Runtime.getRuntime().exit(0)
                }
            }
        } else {
            FeedbackManager.message(ctx, R.string.load_cjk_plugin_failed)
        }
    }

    fun startDownload() {
        if (!hasInternet) {
            showDialog = false
            val url = "https://github.com/LeanBitLab/LeanType-CJK-Plugin/releases"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                ctx.startActivity(intent)
                Toast.makeText(ctx, ctx.getString(R.string.cjk_plugin_open_browser_toast), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, ctx.getString(R.string.open_browser_failed_format, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
            }
            return
        }

        isDownloading = true
        downloadProgress = 0f
        scope.launch(Dispatchers.IO) {
            val tempFile = File(ctx.cacheDir, "cjk_plugin_download.apk")
            if (tempFile.exists()) tempFile.delete()

            val downloaded = CjkPluginLoader.downloadPluginApk(ctx, remoteVersion, tempFile) { progress ->
                downloadProgress = progress
            }

            withContext(Dispatchers.Main) {
                isDownloading = false
                downloadProgress = 0f
                showDialog = false
                if (downloaded) {
                    val success = CjkPluginLoader.importPluginFromTempFile(ctx, tempFile)
                    if (success) {
                        FeedbackManager.message(ctx, R.string.load_cjk_plugin_success)
                        onSuccess?.invoke()
                        if (restartOnSuccess) {
                            scope.launch {
                                delay(2000)
                                Runtime.getRuntime().exit(0)
                            }
                        }
                    } else {
                        FeedbackManager.message(ctx, R.string.load_cjk_plugin_failed)
                    }
                } else {
                    FeedbackManager.message(ctx, R.string.load_cjk_plugin_failed)
                }
            }
        }
    }

    val effectiveSummary = when {
        isDownloading -> stringResource(R.string.cjk_plugin_downloading)
        hasPlugin -> if (localVersion != null) stringResource(R.string.cjk_plugin_active_version_status, localVersion) else stringResource(R.string.cjk_plugin_active_status)
        else -> summary
    }

    Preference(
        name = title,
        description = effectiveSummary,
        icon = icon,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        PreferenceDialog(
            onDismissRequest = { if (!isDownloading) showDialog = false },
            title = stringResource(R.string.load_cjk_plugin),
            showCloseButton = !isDownloading,
            buttons = {
                if (isDownloading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (downloadProgress > 0f) {
                                    stringResource(R.string.downloading) + " ${(downloadProgress * 100).toInt()}%"
                                } else {
                                    stringResource(R.string.downloading)
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        LinearProgressIndicator(
                            progress = { if (downloadProgress > 0f) downloadProgress else 0.5f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!hasPlugin || updateAvailable) {
                            Button(
                                onClick = { startDownload() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val buttonText = when {
                                    updateAvailable -> stringResource(R.string.cjk_plugin_button_update_version, remoteVersion ?: "")
                                    remoteVersion != null -> stringResource(R.string.cjk_plugin_button_download_version, remoteVersion ?: "")
                                    else -> stringResource(R.string.cjk_plugin_button_download)
                                }
                                Text(buttonText)
                            }
                        }


                        if (!hasPlugin) {
                            OutlinedButton(
                                onClick = {
                                    showDialog = false
                                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                        .setType("*/*")
                                    try {
                                        launcher.launch(intent)
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.cjk_plugin_button_load_storage))
                            }
                        }

                        if (hasPlugin) {
                            Button(
                                onClick = {
                                    CjkPluginLoader.removePlugin(ctx)
                                    FeedbackManager.message(ctx, R.string.cjk_plugin_removed_restarting)
                                    hasPlugin = false
                                    onSuccess?.invoke()
                                    showDialog = false
                                    if (restartOnSuccess) {
                                        scope.launch {
                                            delay(2000)
                                            Runtime.getRuntime().exit(0)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.load_cjk_plugin_button_delete))
                            }
                        }
                    }
                }
            }
        ) {
            val message = when {
                hasPlugin && updateAvailable -> stringResource(R.string.cjk_plugin_update_available_dialog, localVersion.orEmpty(), remoteVersion ?: "")
                hasPlugin -> stringResource(R.string.cjk_plugin_active_warning, localVersion.orEmpty())
                remoteVersion != null -> stringResource(R.string.cjk_plugin_download_dialog_version_msg, remoteVersion ?: "")
                else -> stringResource(R.string.cjk_plugin_download_dialog_msg)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
