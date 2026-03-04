// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.content.Intent
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.content.edit
import helium314.keyboard.latin.utils.protectedPrefs
import helium314.keyboard.latin.utils.getActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.settings.FeedbackManager
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.filePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun LoadEmojiLibPreference(
    title: String,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var isDownloading by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val locale = RichInputMethodManager.getInstance().currentSubtype.locale
    val lang = locale.language
    val dictName = "emoji_$lang.dict"
    val cachePath = DictionaryInfoUtils.getCacheDirectoryForLocale(locale, ctx)
    val libFile = File(cachePath, dictName)
    val isInstalled = libFile.exists()

    fun refreshAndLoad() {
        helium314.keyboard.keyboard.emoji.EmojiPalettesView.closeDictionaryFacilitator()
        // Force settings screen to recompose by updating a dummy pref or just updating local state so the preference knows it's installed.
        // The most direct way since we read `isInstalled` at composition is to just swap a boolean state here if needed,
        // but `isInstalled` is computed on every recompose.
        ctx.protectedPrefs().edit { putLong("emoji_lib_last_update", System.currentTimeMillis()) }
        (ctx.getActivity() as? helium314.keyboard.settings.SettingsActivity)?.let {
            it.prefChanged.value = it.prefChanged.value + 1
        }
    }

    fun startDownload() {
        isDownloading = true
        scope.launch(Dispatchers.IO) {
            try {
                val urlStr = "${Links.DICTIONARY_URL}${Links.DICTIONARY_DOWNLOAD_SUFFIX}${Links.DICTIONARY_EMOJI_CLDR_SUFFIX}$dictName"
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.connect()

                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Server returned HTTP ${conn.responseCode}")
                }

                if (cachePath != null) {
                    val targetFile = File(cachePath, dictName)
                    conn.inputStream.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        FeedbackManager.message(ctx, R.string.load_gesture_library_download_success) // Reusing success string
                        isDownloading = false
                        refreshAndLoad()
                    }
                } else {
                    throw IOException("Cache path is null")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    Toast.makeText(ctx, "Failed to download emoji dictionary", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val launcher = filePicker { uri ->
        if (cachePath != null) {
            val targetFile = File(cachePath, dictName)
            try {
                val tmpFile = File(ctx.filesDir.absolutePath + File.separator + "tmp_emoji_dict")
                FileUtils.copyContentUriToNewFile(uri, ctx, tmpFile)
                FileInputStream(tmpFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        FileUtils.copyStreamToOtherStream(input, output)
                    }
                }
                tmpFile.delete()
                FeedbackManager.message(ctx, "Emoji dictionary loaded successfully")
                refreshAndLoad()
            } catch (e: IOException) {
                Toast.makeText(ctx, "Failed to load emoji dictionary from file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Preference(
        name = title,
        description = summary,
        icon = icon,
        onClick = { showDialog = true }
    )

    if (showDialog) {
        ConfirmationDialog(
            onDismissRequest = { if (!isDownloading) showDialog = false },
            onConfirmed = {
                if (!isDownloading) {
                    startDownload()
                }
            },
            confirmButtonText = if (isDownloading) "Downloading..." else "Download",
            title = { Text(title) },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Download or load an emoji dictionary file for the current language ($lang) to enable emoji suggestions.")
                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }
                }
            },
            neutralButtonText = when {
                isDownloading -> null
                isInstalled -> "Delete"
                else -> "Load from file"
            },
            onNeutral = {
                if (isInstalled) {
                    libFile.delete()
                    refreshAndLoad()
                } else {
                    showDialog = false
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/octet-stream")
                    launcher.launch(intent)
                }
            }
        )
    }
}
