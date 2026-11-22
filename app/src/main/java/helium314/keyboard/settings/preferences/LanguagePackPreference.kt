// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.LanguagePacks
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.settings.FeedbackManager
import helium314.keyboard.settings.dialogs.PreferenceDialog
import helium314.keyboard.settings.filePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Download, import or remove the translation pack for the current app language (Android 11+). */
@Composable
fun LanguagePackPreference() {
    val ctx = LocalContext.current
    if (!remember { LanguagePacks.isNeeded(ctx) }) return
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) } // null: not downloading
    val installed = remember { LanguagePacks.installedPack(ctx) != null }
    val language = remember { LanguagePacks.displayName(ctx) }
    val hasInternet = remember {
        ctx.packageManager.checkPermission("android.permission.INTERNET", ctx.packageName) == PackageManager.PERMISSION_GRANTED
    }

    fun finish(result: LanguagePacks.Result) {
        progress = null
        showDialog = false
        when (result) {
            LanguagePacks.Result.OK -> ctx.getActivity()?.recreate() // show the new strings
            LanguagePacks.Result.INVALID -> FeedbackManager.message(ctx, R.string.language_pack_invalid)
            LanguagePacks.Result.WRONG_BUILD -> FeedbackManager.message(ctx, R.string.language_pack_wrong_build)
            LanguagePacks.Result.DOWNLOAD_FAILED -> FeedbackManager.message(ctx, R.string.language_pack_download_failed)
        }
    }

    val launcher = filePicker { uri ->
        scope.launch {
            val result = withContext(Dispatchers.IO) { LanguagePacks.importFromUri(ctx, uri) }
            finish(result)
        }
    }

    Preference(
        name = stringResource(R.string.language_pack_title),
        description = stringResource(if (installed) R.string.language_pack_installed else R.string.language_pack_missing, language),
        icon = R.drawable.ic_settings_languages,
        onClick = { showDialog = true }
    )
    if (!showDialog) return
    PreferenceDialog(
        onDismissRequest = { if (progress == null) showDialog = false },
        title = stringResource(R.string.language_pack_dialog_title, language),
        showCloseButton = progress == null,
        buttons = {
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val currentProgress = progress
                if (currentProgress != null) {
                    LinearProgressIndicator(progress = { currentProgress }, modifier = Modifier.fillMaxWidth())
                    return@Column
                }
                Button(onClick = {
                    if (!hasInternet) { // offline flavor: get the file in the browser, then import it
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LanguagePacks.RELEASES_URL)))
                        return@Button
                    }
                    progress = 0f
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            LanguagePacks.download(ctx) { p -> scope.launch { progress = p } }
                        }
                        finish(result)
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.language_pack_download)) }
                OutlinedButton(onClick = {
                    launcher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"))
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.language_pack_import)) }
                if (installed) {
                    OutlinedButton(onClick = {
                        LanguagePacks.remove(ctx)
                        finish(LanguagePacks.Result.OK)
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.language_pack_remove)) }
                }
            }
        }
    ) {
        Text(stringResource(R.string.language_pack_dialog_message, language), style = MaterialTheme.typography.bodyMedium)
    }
}
