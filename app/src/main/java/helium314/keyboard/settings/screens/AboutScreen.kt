// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.htmlToAnnotated
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsContainer
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import helium314.keyboard.settings.previewDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import androidx.core.content.edit
import java.util.Locale
import helium314.keyboard.settings.FeedbackManager

@Composable
fun AboutScreen(
    onClickBack: () -> Unit,
) {
    val items = listOf(
        SettingsWithoutKey.APP,
        SettingsWithoutKey.SETUP_WIZARD,
        SettingsWithoutKey.VERSION,
        SettingsWithoutKey.RESTART_IME,
        SettingsWithoutKey.LICENSE,
        SettingsWithoutKey.HIDDEN_FEATURES,
        SettingsWithoutKey.GITHUB_FEATURES,
        SettingsWithoutKey.GITHUB,
        SettingsWithoutKey.SAVE_LOG,
    )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_about),
        settings = items
    )
}

fun createAboutSettings(context: Context) = listOf(
    Setting(context, SettingsWithoutKey.APP, R.string.english_ime_name, R.string.app_slogan) {
        Preference(
            name = it.title,
            description = it.description,
            onClick = { },
            icon = R.mipmap.ic_launcher_round
        )
    },
    Setting(context, SettingsWithoutKey.SETUP_WIZARD, R.string.setup_wizard_title, R.string.setup_welcome_summary) {
        val ctx = LocalContext.current
        Preference(
            name = it.title,
            description = it.description,
            onClick = {
                (ctx.getActivity() as? SettingsActivity)?.reopenWelcomeWizard()
            },
            icon = R.drawable.ic_ime_switcher
        )
    },
    Setting(context, SettingsWithoutKey.VERSION, R.string.version) {
        var count by rememberSaveable { mutableIntStateOf(0) }
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val versionDescription = stringResource(R.string.version_text, BuildConfig.VERSION_NAME)
        Preference(
            name = it.title,
            description = versionDescription,
            onClick = {
                if (prefs.getBoolean(DebugSettings.PREF_SHOW_DEBUG_SETTINGS, Defaults.PREF_SHOW_DEBUG_SETTINGS) || BuildConfig.DEBUG)
                    return@Preference
                count++
                if (count < 5) return@Preference
                prefs.edit { putBoolean(DebugSettings.PREF_SHOW_DEBUG_SETTINGS, true) }
                FeedbackManager.message(ctx, R.string.prefs_debug_settings_enabled)
            },
            icon = R.drawable.ic_settings_about
        )
    },
    Setting(context, SettingsWithoutKey.RESTART_IME, R.string.restart_ime_title, R.string.restart_ime_summary) {
        val ctx = LocalContext.current
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = it.title,
            description = it.description,
            onClick = { showDialog = true },
            icon = R.drawable.ic_redo
        )
        if (showDialog) {
            ThreeButtonAlertDialog(
                onDismissRequest = { showDialog = false },
                onConfirmed = {
                    showDialog = false
                    val intent = Intent(ctx, SettingsActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    ctx.startActivity(intent)
                    Runtime.getRuntime().exit(0)
                },
                title = { Text(stringResource(R.string.restart_ime_dialog_title)) },
                icon = { Icon(painterResource(R.drawable.ic_redo), null) },
                content = { Text(stringResource(R.string.restart_ime_dialog_message)) },
                confirmButtonText = stringResource(R.string.restart_ime_confirm),
                cancelButtonText = stringResource(android.R.string.cancel)
            )
        }
    },
    Setting(context, SettingsWithoutKey.LICENSE, R.string.license, R.string.gnu_gpl) {
        val ctx = LocalContext.current
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = it.title,
            description = it.description,
            onClick = { showDialog = true },
            icon = R.drawable.ic_settings_about_license
        )
        if (showDialog) {
            helium314.keyboard.settings.dialogs.PreferenceDialog(
                onDismissRequest = { showDialog = false },
                title = stringResource(R.string.license),
                showCloseButton = true,
                buttons = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showDialog = false
                                val intent = Intent(Intent.ACTION_VIEW, Links.LICENSE.toUri())
                                ctx.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.gnu_gpl))
                        }
                    }
                }
            ) {
                Text(
                    text = "LeanType is licensed under GNU GPL v3.0.\n\nThird-Party Speech Components:\n• whisper.cpp — MIT License (github.com/ggerganov/whisper.cpp)\n• Vosk Speech Recognition — Apache 2.0 (alphacephei.com/vosk)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    },
    Setting(context, SettingsWithoutKey.HIDDEN_FEATURES, R.string.hidden_features_title, R.string.hidden_features_summary) {
        val ctx = LocalContext.current
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = it.title,
            description = it.description,
            onClick = { showDialog = true },
            icon = R.drawable.ic_settings_about_hidden_features
        )
        if (showDialog) {
            val link = ("<a href=\"https://developer.android.com/reference/android/content/Context#createDeviceProtectedStorageContext()\">"
                    + ctx.getString(R.string.hidden_features_text) + "</a>")
            val message = ctx.getString(R.string.hidden_features_message, link)
            ThreeButtonAlertDialog(
                onDismissRequest = { showDialog = false },
                onConfirmed = { showDialog = false },
                title = { Text(stringResource(R.string.hidden_features_title)) },
                icon = { Icon(painterResource(R.drawable.ic_settings_about_hidden_features), null) },
                content = { Text(message.htmlToAnnotated()) },
                scrollContent = true,
                confirmButtonText = stringResource(R.string.dialog_close),
                cancelButtonText = null
            )
        }
    },
    Setting(context, SettingsWithoutKey.GITHUB_FEATURES, R.string.about_features_link, R.string.about_features_link_description) {
        val ctx = LocalContext.current
        Preference(
            name = it.title,
            description = it.description,
            onClick = {
                val intent = Intent()
                intent.data = Links.FEATURES_URL.toUri()
                intent.action = Intent.ACTION_VIEW
                ctx.startActivity(intent)
            },
            icon = R.drawable.ic_settings_about_wiki
        )
    },
    Setting(context, SettingsWithoutKey.GITHUB, R.string.about_github_link) {
        val ctx = LocalContext.current
        Preference(
            name = it.title,
            description = it.description,
            onClick = {
                val intent = Intent()
                intent.data = Links.GITHUB.toUri()
                intent.action = Intent.ACTION_VIEW
                ctx.startActivity(intent)
            },
            icon = R.drawable.ic_settings_about_github
        )
    },

    Setting(context, SettingsWithoutKey.SAVE_LOG, R.string.save_log) { setting ->
        var showDialog by rememberSaveable { mutableStateOf(false) }
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                ctx.getActivity()?.contentResolver?.openOutputStream(uri)?.use { os ->
                    os.bufferedWriter().use { writer ->
                        // Stream the logcat line by line to avoid allocating a multi-MB
                        // String in memory (the IME process can OOM on long-running devices).
                        ProcessBuilder("logcat", "-d", "-b", "all", "*:W").start().inputStream.use { stream ->
                            stream.bufferedReader().useLines { lines: Sequence<String> ->
                                for (line: String in lines) {
                                    writer.write(line)
                                    writer.newLine()
                                }
                            }
                        }
                        writer.newLine()
                        writer.newLine()
                        for (line in Log.getLog()) {
                            writer.write(line.toString())
                            writer.newLine()
                        }
                    }
                }
            }
        }
        Preference(
            name = setting.title,
            description = setting.description,
            onClick = { showDialog = true },
            icon = R.drawable.ic_settings_about_log
        )
        if (showDialog) {
            helium314.keyboard.settings.dialogs.PreferenceDialog(
                onDismissRequest = { showDialog = false },
                title = stringResource(R.string.log_options_title),
                showCloseButton = true,
                buttons = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showDialog = false
                                val date = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Calendar.getInstance().time)
                                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .putExtra(
                                        Intent.EXTRA_TITLE,
                                        ctx.getString(R.string.english_ime_name)
                                            .replace(" ", "_") + "_log_$date.txt"
                                    )
                                    .setType("text/plain")
                                launcher.launch(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.save_log_to_file))
                        }
                        OutlinedButton(
                            onClick = {
                                showDialog = false
                                scope.launch(Dispatchers.IO) {
                                    val logsDir = File(ctx.cacheDir, "logs")
                                    logsDir.mkdirs()
                                    val date = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Calendar.getInstance().time)
                                    val logFile = File(logsDir, "${ctx.getString(R.string.english_ime_name).replace(" ", "_")}_log_$date.txt")
                                    logFile.bufferedWriter().use { writer ->
                                        try {
                                            ProcessBuilder("logcat", "-d", "-b", "all", "*:W").start().inputStream.use { stream ->
                                                stream.bufferedReader().useLines { lines ->
                                                    for (line in lines) {
                                                        writer.write(line)
                                                        writer.newLine()
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("AboutScreen", "Error reading logcat for share", e)
                                        }
                                        writer.newLine()
                                        writer.newLine()
                                        for (line in Log.getLog()) {
                                            writer.write(line.toString())
                                            writer.newLine()
                                        }
                                    }
                                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", logFile)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        clipData = android.content.ClipData.newRawUri("", uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    scope.launch(Dispatchers.Main) {
                                        ctx.startActivity(Intent.createChooser(shareIntent, ctx.getString(R.string.share_log)))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.share_log))
                        }
                        Button(
                            onClick = {
                                showDialog = false
                                Log.clear()
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        ProcessBuilder("logcat", "-c").start().waitFor()
                                    } catch (e: Exception) {
                                        Log.e("AboutScreen", "Error clearing logcat", e)
                                    }
                                }
                                Toast.makeText(ctx, R.string.log_cleared, Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.clear_log))
                        }
                    }
                }
            )
        }
    },
)

@Preview
@Composable
private fun Preview() {
    SettingsActivity.settingsContainer = SettingsContainer(LocalContext.current)
    Theme(previewDark) {
        Surface {
            AboutScreen {  }
        }
    }
}
