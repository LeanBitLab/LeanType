// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import helium314.keyboard.compat.locale
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.utils.LocaleUtils
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import helium314.keyboard.latin.translation.TranslationModelImporter
import helium314.keyboard.latin.utils.cleanUnusedMainDicts
import helium314.keyboard.latin.utils.prefs
import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import helium314.keyboard.settings.dialogs.NewDictionaryDialog
import helium314.keyboard.settings.dialogs.PreferenceDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// todo: with compose, app startup is slower and UI needs some "warmup" time to be snappy
//  maybe baseline profiles help?
//  https://developer.android.com/codelabs/android-baseline-profiles-improve
//  https://developer.android.com/codelabs/jetpack-compose-performance#2
//  https://developer.android.com/topic/performance/baselineprofiles/overview
// todo: consider viewModel, at least for LanguageScreen and ColorsScreen it might help making them less awkward and complicated
open class SettingsActivity : ComponentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    override fun attachBaseContext(newBase: Context) {
        val prefs = DeviceProtectedUtils.getSharedPreferences(newBase)
        val lang = prefs.getString(Settings.PREF_APP_LANGUAGE, Defaults.PREF_APP_LANGUAGE) ?: Defaults.PREF_APP_LANGUAGE
        val wrapped = LocaleUtils.wrapContextWithLocale(newBase, lang)
        super.attachBaseContext(wrapped)
    }

    private val prefs by lazy { this.prefs() }
    val prefChanged = MutableStateFlow(0) // simple counter, as the only relevant information is that something changed
    fun prefChanged() = prefChanged.value++
    private val dictUriFlow = MutableStateFlow<Uri?>(null)
    private val cachedDictionaryFile by lazy { File(this.cacheDir.path + File.separator + "temp_dict") }
    private val crashReportFiles = MutableStateFlow<List<File>>(emptyList())
    private var paused = true
    private var onReopenWelcomeWizard: (() -> Unit)? = null

    fun reopenWelcomeWizard() {
        onReopenWelcomeWizard?.invoke()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_setup_wizard", false)) {
            reopenWelcomeWizard()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lang = prefs.getString(Settings.PREF_APP_LANGUAGE, Defaults.PREF_APP_LANGUAGE) ?: Defaults.PREF_APP_LANGUAGE
        LocaleUtils.applyAppLanguageToResources(this, lang)
        Settings.getInstance().loadSettings(this)
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            cleanUnusedMainDicts(this)
            TranslationModelImporter.migrateLegacyModels(this)
            helium314.keyboard.latin.handwriting.HandwritingModelImporter.migrateLegacyModels(this)
        }
        crashReportFiles.value = findCrashReports(false)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

        settingsContainer = SettingsContainer(this)

        val spellchecker = intent?.getBooleanExtra("spellchecker", false) ?: false

        val cv = ComposeView(context = this)
        setContentView(cv)
        cv.setContent {
            Theme {
                Surface {
                    val dictUri by dictUriFlow.collectAsState()
                    val crashReports by crashReportFiles.collectAsState()
                    val crashFilePicker = filePicker { saveCrashReports(it) }
                    val launchedFromIme = intent?.getBooleanExtra("from_ime", false) ?: false
                    val openSetupWizard = intent?.getBooleanExtra("open_setup_wizard", false) ?: false
                    val setupDismissed = prefs.getBoolean(Settings.PREF_SETUP_WIZARD_DISMISSED, false)
                    var showWelcomeWizard by rememberSaveable { mutableStateOf(
                        openSetupWizard || (!launchedFromIme && !setupDismissed && (
                            !UncachedInputMethodManagerUtils.isThisImeCurrent(this, imm)
                                    || !UncachedInputMethodManagerUtils.isThisImeEnabled(this, imm)
                        ))
                    ) }
                    onReopenWelcomeWizard = {
                        showWelcomeWizard = true
                    }
                    val snackbarHostState = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        FeedbackManager.messages.collect { message ->
                            snackbarHostState.showSnackbar(message)
                        }
                    }

                    if (spellchecker)
                        Scaffold(
                            contentWindowInsets = WindowInsets.safeDrawing,
                            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }
                        ) { innerPadding ->
                            Column(Modifier.padding(innerPadding)) {
                                TopAppBar(
                                    title = { Text(stringResource(R.string.android_spell_checker_settings)) },
                                    windowInsets = WindowInsets(0),
                                    navigationIcon = {
                                        BackButton { this@SettingsActivity.finish() }
                                    },
                                )
                                settingsContainer[Settings.PREF_USE_APPS]?.Preference()
                                settingsContainer[Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE]?.Preference()
                            }
                        }
                    else {
                        val startScreen = intent?.getStringExtra("screen")
                        Scaffold(
                            contentWindowInsets = WindowInsets.safeDrawing,
                            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }
                        ) { scaffoldPadding ->
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(scaffoldPadding)
                            ) {
                                SettingsNavHost(onClickBack = { this@SettingsActivity.finish() }, startDestination = startScreen)
                                if (showWelcomeWizard) {
                                    WelcomeWizard(
                                        close = {
                                            prefs.edit().putBoolean(Settings.PREF_SETUP_WIZARD_DISMISSED, true).apply()
                                            showWelcomeWizard = false
                                        },
                                        finish = {
                                            prefs.edit().putBoolean(Settings.PREF_SETUP_WIZARD_DISMISSED, true).apply()
                                            this@SettingsActivity.finish()
                                        }
                                    )
                                } else if (crashReports.isNotEmpty()) {
                                    PreferenceDialog(
                                        onDismissRequest = { crashReportFiles.value = emptyList() },
                                        title = stringResource(R.string.crash_reports_dialog_title),
                                        showCloseButton = true,
                                        buttons = {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 16.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                                                addCategory(Intent.CATEGORY_OPENABLE)
                                                                putExtra(Intent.EXTRA_TITLE, "crash_reports.zip")
                                                                type = "application/zip"
                                                            }
                                                            crashFilePicker.launch(intent)
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text(stringResource(R.string.crash_report_save))
                                                    }
                                                    Button(
                                                        onClick = { shareCrashReports() },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text(stringResource(R.string.crash_report_share))
                                                    }
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            crashReports.forEach { it.delete() }
                                                            crashReportFiles.value = emptyList()
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.error,
                                                            contentColor = MaterialTheme.colorScheme.onError
                                                        ),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text(stringResource(R.string.delete))
                                                    }
                                                    OutlinedButton(
                                                        onClick = { crashReportFiles.value = emptyList() },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text(stringResource(R.string.button_ignore))
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Text(stringResource(R.string.crash_reports_dialog_message))
                                    }
                                }
                            }
                        }
                    }
                    if (dictUri != null) {
                        NewDictionaryDialog(
                            onDismissRequest = { dictUriFlow.value = null },
                            cachedFile = cachedDictionaryFile,
                            mainLocale = null
                        )
                    }
                }
            }
        }

        if (intent?.action == Intent.ACTION_VIEW) {
            intent?.data?.let {
                cachedDictionaryFile.delete()
                FileUtils.copyContentUriToNewFile(it, this, cachedDictionaryFile)
                dictUriFlow.value = it
            }
            intent = null
        }

        enableEdgeToEdge()
    }

    override fun onStart() {
        super.onStart()
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        setForceTheme(null, null)
        paused = true
    }

    override fun onResume() {
        super.onResume()
        paused = false
    }

    fun setForceTheme(theme: String?, night: Boolean?) {
        if (paused) return
        if (forceTheme == theme && forceNight == night)
            return
        forceTheme = theme
        forceNight = night
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    private fun findCrashReports(onlyUnprotected: Boolean): List<File> {
        val unprotected = DeviceProtectedUtils.getFilesDir(this)?.listFiles().orEmpty()
        if (onlyUnprotected)
            return unprotected.filter { it.name.startsWith("crash_report") }

        // Use internal filesDir - same reliability as getExternalFilesDir but without
        // triggering IStorageManager.mkdirs() failures on restricted or early-boot devices.
        val internalFiles = filesDir?.listFiles()?.toList().orEmpty()
        return (internalFiles + unprotected).filter { it.name.startsWith("crash_report") }
    }

    private fun saveCrashReports(uri: Uri) {
        val files = findCrashReports(false)
        if (files.isEmpty()) return
        runCatching {
            contentResolver.openOutputStream(uri)?.use {
                val bos = BufferedOutputStream(it)
                val z = ZipOutputStream(bos)
                for (file in files) {
                    val f = FileInputStream(file)
                    z.putNextEntry(ZipEntry(file.name))
                    FileUtils.copyStreamToOtherStream(f, z)
                    f.close()
                    z.closeEntry()
                }
                z.close()
                bos.close()
                for (file in files) {
                    file.delete()
                }
            }
            crashReportFiles.value = emptyList()
        }
    }

    private fun shareCrashReports() {
        val files = findCrashReports(false)
        if (files.isEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val logsDir = File(cacheDir, "logs")
                logsDir.mkdirs()
                val zipFile = File(logsDir, "crash_reports.zip")
                zipFile.outputStream().use { os ->
                    val bos = BufferedOutputStream(os)
                    val z = ZipOutputStream(bos)
                    for (file in files) {
                        FileInputStream(file).use { fis ->
                            z.putNextEntry(ZipEntry(file.name))
                            FileUtils.copyStreamToOtherStream(fis, z)
                            z.closeEntry()
                        }
                    }
                    z.close()
                    bos.close()
                }
                for (file in files) {
                    file.delete()
                }
                withContext(Dispatchers.Main) {
                    crashReportFiles.value = emptyList()
                    val uri = FileProvider.getUriForFile(
                        this@SettingsActivity,
                        "${packageName}.fileprovider",
                        zipFile
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.crash_report_share)))
                }
            }
        }
    }

    companion object {
        // public write so compose previews can show the screens
        // having it in a companion object is not ideal as it will stay in memory even after settings are closed
        // but it's small enough to not care
        lateinit var settingsContainer: SettingsContainer

        var forceNight: Boolean? = null
        var forceTheme: String? = null
    }

    override fun onSharedPreferenceChanged(prefereces: SharedPreferences?, key: String?) {
        prefChanged()
        if (key == Settings.PREF_APP_LANGUAGE) {
            val lang = prefs.getString(Settings.PREF_APP_LANGUAGE, Defaults.PREF_APP_LANGUAGE) ?: Defaults.PREF_APP_LANGUAGE
            LocaleUtils.applyAppLanguageToResources(this, lang)
            settingsContainer = SettingsContainer(this)
            recreate()
        }
    }
}

// duplicate of SettingsActivity so we can launch it when the app icon is disabled in Android 9 and older
class SettingsActivity2 : SettingsActivity()
