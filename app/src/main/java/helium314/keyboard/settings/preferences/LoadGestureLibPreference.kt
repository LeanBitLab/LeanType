// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ChecksumCalculator
import helium314.keyboard.latin.utils.GestureLibraryDownloader
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.protectedPrefs
import helium314.keyboard.settings.FeedbackManager
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.filePicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

@SuppressLint("ApplySharedPref")
@Composable
fun LoadGestureLibPreference(
    title: String,
    summary: String? = if (JniUtils.sHaveNativeGestureLib) stringResource(R.string.libraries_status_active) else stringResource(R.string.libraries_status_not_installed),
    @DrawableRes icon: Int? = null,
    restartOnSuccess: Boolean = true,
    onSuccess: (() -> Unit)? = null,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var isDownloading by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val prefs = ctx.protectedPrefs()
    val abi = Build.SUPPORTED_ABIS[0]
    val libFile = File(ctx.filesDir?.absolutePath + File.separator + JniUtils.JNI_LIB_IMPORT_FILE_NAME)
    val scope = rememberCoroutineScope()

    fun renameToLibFileAndRestart(file: File, checksum: String) {
        libFile.setWritable(true)
        libFile.delete()
        prefs.edit(commit = true) {
            putString(Settings.PREF_LIBRARY_CHECKSUM, checksum)
            putBoolean("pref_gesture_lib_just_installed", true)
            putBoolean(Settings.PREF_GESTURE_INPUT, true)
        }
        file.copyTo(libFile)
        libFile.setReadOnly()
        file.delete()
        try {
            ctx.sendBroadcast(Intent(helium314.keyboard.dictionarypack.DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION))
        } catch (e: Exception) { }
        onSuccess?.invoke()
        isDownloading = false
        showDialog = false
        if (restartOnSuccess) {
            scope.launch {
                FeedbackManager.message(ctx, "Gesture library loaded. Restarting...")
                delay(3000)
                Runtime.getRuntime().exit(0)
            }
        }
    }

    fun startDownload() {
        isDownloading = true
        scope.launch {
            GestureLibraryDownloader.downloadLibrary(ctx).fold(
                onSuccess = { downloadedFile ->
                    val checksum = ChecksumCalculator.checksum(downloadedFile) ?: ""
                    renameToLibFileAndRestart(downloadedFile, checksum)
                },
                onFailure = { error ->
                    isDownloading = false
                    val errorMsg = ctx.getString(R.string.load_gesture_library_download_failed, error.message ?: "Unknown error")
                    FeedbackManager.message(ctx, errorMsg)
                }
            )
        }
    }

    var tempFilePath: String? by rememberSaveable { mutableStateOf(null) }
    val launcher = filePicker { uri ->
        val tmpfile = File(ctx.filesDir.absolutePath + File.separator + "tmplib")
        try {
            val otherTemporaryFile = File(ctx.filesDir.absolutePath + File.separator + "tmpfile")
            FileUtils.copyContentUriToNewFile(uri, ctx, otherTemporaryFile)
            val inputStream = FileInputStream(otherTemporaryFile)
            val outputStream = FileOutputStream(tmpfile)
            outputStream.use {
                tmpfile.setReadOnly()
                FileUtils.copyStreamToOtherStream(inputStream, it)
            }
            otherTemporaryFile.delete()

            val checksum = ChecksumCalculator.checksum(tmpfile) ?: ""
            if (checksum == JniUtils.expectedDefaultChecksum()) {
                renameToLibFileAndRestart(tmpfile, checksum)
            } else {
                tempFilePath = tmpfile.absolutePath
            }
        } catch (e: IOException) {
            tmpfile.delete()
        }
    }

    Preference(
        name = title,
        description = summary,
        icon = icon,
        onClick = { showDialog = true }
    )

    val hasInternet = remember {
        ctx.packageManager.checkPermission(
            "android.permission.INTERNET",
            ctx.packageName
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    if (showDialog) {
        val isInstalled = libFile.exists() || JniUtils.sHaveNativeGestureLib
        helium314.keyboard.settings.dialogs.PreferenceDialog(
            onDismissRequest = { if (!isDownloading) showDialog = false },
            title = stringResource(R.string.load_gesture_library),
            showCloseButton = !isDownloading,
            buttons = {
                if (isDownloading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.load_gesture_library_downloading),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isInstalled) {
                            if (hasInternet) {
                                Button(
                                    onClick = { startDownload() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.load_gesture_library_button_download))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        showDialog = false
                                        val url = GestureLibraryDownloader.getDownloadUrl() ?: "https://github.com/Helium314/HeliBoard/releases"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        try {
                                            ctx.startActivity(intent)
                                            android.widget.Toast.makeText(ctx, "Opening browser to download library… use 'Load from file' after download", android.widget.Toast.LENGTH_LONG).show()
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.load_gesture_library_button_download))
                                }
                            }
                        }

                        if (!isInstalled) {
                            OutlinedButton(
                                onClick = {
                                    showDialog = false
                                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                        .setType("application/octet-stream")
                                    launcher.launch(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.load_gesture_library_button_load))
                            }
                        }

                        if (isInstalled) {
                            Button(
                                onClick = {
                                    libFile.delete()
                                    prefs.edit(commit = true) { remove(Settings.PREF_LIBRARY_CHECKSUM) }
                                    onSuccess?.invoke()
                                    showDialog = false
                                    if (restartOnSuccess) {
                                        scope.launch {
                                            FeedbackManager.message(ctx, "Gesture library removed. Restarting...")
                                            delay(3000)
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
                                Text(stringResource(R.string.load_gesture_library_button_delete))
                            }
                        }
                    }
                }
            }
        ) {
            Text(
                text = stringResource(R.string.load_gesture_library_message, abi),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    val currentTempPath = tempFilePath
    if (currentTempPath != null)
        ConfirmationDialog(
            onDismissRequest = {
                File(currentTempPath).delete()
                tempFilePath = null
            },
            content = { Text(stringResource(R.string.checksum_mismatch_message, abi)) },
            onConfirmed = {
                val tempFile = File(currentTempPath)
                renameToLibFileAndRestart(tempFile, ChecksumCalculator.checksum(tempFile) ?: "")
            }
        )
}
