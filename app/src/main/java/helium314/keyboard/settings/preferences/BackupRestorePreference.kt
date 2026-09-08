// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Checkbox
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import helium314.keyboard.latin.AppUpgrade
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.database.Database
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.protectedPrefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.InfoDialog
import helium314.keyboard.settings.filePicker
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import androidx.core.content.edit
import helium314.keyboard.settings.FeedbackManager

@Composable
fun BackupRestorePreference(setting: Setting) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    var error: String? by rememberSaveable { mutableStateOf(null) }
    var selectedCategories by remember {
        mutableStateOf(
            setOf(
                BackupCategory.LAYOUTS,
                BackupCategory.THEME_APPEARANCE,
                BackupCategory.DICTIONARY_HISTORY,
                BackupCategory.CLIPBOARD,
                BackupCategory.GENERAL_SETTINGS
            )
        )
    }
    val backupLauncher = backupLauncher(selectedCategories) { error = it }
    val restoreLauncher = restoreLauncher(selectedCategories) { error = it }
    Preference(name = setting.title, onClick = { showDialog = true })
    if (showDialog) {
        ConfirmationDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            content = {
                Column {
                    Text(
                        text = stringResource(R.string.backup_select_items),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    val categories = listOf(
                        BackupCategory.LAYOUTS to R.string.backup_category_layouts,
                        BackupCategory.THEME_APPEARANCE to R.string.backup_category_theme,
                        BackupCategory.DICTIONARY_HISTORY to R.string.backup_category_dictionary,
                        BackupCategory.CLIPBOARD to R.string.backup_category_clipboard,
                        BackupCategory.GENERAL_SETTINGS to R.string.backup_category_general
                    )
                    categories.forEach { (category, stringResId) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .toggleable(
                                    value = selectedCategories.contains(category),
                                    onValueChange = { checked ->
                                        selectedCategories = if (checked) {
                                            selectedCategories + category
                                        } else {
                                            selectedCategories - category
                                        }
                                    }
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = selectedCategories.contains(category),
                                onCheckedChange = null
                            )
                            Text(
                                text = stringResource(stringResId),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.backup_restore_message))
                }
            },
            confirmButtonText = stringResource(R.string.button_backup),
            neutralButtonText = stringResource(R.string.button_restore),
            onNeutral = {
                if (selectedCategories.isEmpty()) {
                    Toast.makeText(ctx, "Please select at least one category", Toast.LENGTH_SHORT).show()
                    return@ConfirmationDialog
                }
                showDialog = false
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip")
                restoreLauncher.launch(intent)
            },
            onConfirmed = {
                if (selectedCategories.isEmpty()) {
                    Toast.makeText(ctx, "Please select at least one category", Toast.LENGTH_SHORT).show()
                    return@ConfirmationDialog
                }
                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(
                        Intent.EXTRA_TITLE,
                        ctx.getString(R.string.english_ime_name)
                            .replace(" ", "_") + "_backup_$currentDate.zip"
                    )
                    .setType("application/zip")
                backupLauncher.launch(intent)
            }
        )
    }
    val currentError = error
    if (currentError != null) {
        InfoDialog(
            if (currentError.startsWith("b"))
                stringResource(R.string.backup_error, currentError.drop(1))
            else stringResource(R.string.restore_error, currentError.drop(1))
        ) { error = null }
    }
}

@Composable
private fun backupLauncher(
    selectedCategories: Set<BackupCategory>,
    onError: (String) -> Unit
): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current
    return filePicker { uri ->
        val filesDir = ctx.filesDir ?: return@filePicker
        val filesPath = filesDir.path + File.separator
        val files = mutableListOf<File>()
        filesDir.walk().forEach { file ->
            val path = file.path.replace(filesPath, "")
            if (file.isFile && backupFilePatterns.any { path.matches(it) }) {
                val cat = getCategoryForFilePath(path)
                if (cat == null || selectedCategories.contains(cat)) {
                    files.add(file)
                }
            }
        }
        val protectedFilesDir = DeviceProtectedUtils.getFilesDir(ctx)
        val protectedFilesPath = protectedFilesDir.path + File.separator
        val protectedFiles = mutableListOf<File>()
        protectedFilesDir.walk().forEach { file ->
            val path = file.path.replace(protectedFilesPath, "")
            if (file.isFile && backupFilePatterns.any { path.matches(it) }) {
                val cat = getCategoryForFilePath(path)
                if (cat == null || selectedCategories.contains(cat)) {
                    protectedFiles.add(file)
                }
            }
        }
        val wait = CountDownLatch(1)
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            try {
                ctx.getActivity()?.contentResolver?.openOutputStream(uri)?.use { os ->
                    val zipStream = ZipOutputStream(os)
                    files.forEach {
                        val fileStream = FileInputStream(it).buffered()
                        zipStream.putNextEntry(ZipEntry(it.path.replace(filesPath, "")))
                        fileStream.copyTo(zipStream, 1024)
                        fileStream.close()
                        zipStream.closeEntry()
                    }
                    protectedFiles.forEach {
                        val fileStream = FileInputStream(it).buffered()
                        zipStream.putNextEntry(ZipEntry(it.path.replace(protectedFilesDir.path, "unprotected")))
                        fileStream.copyTo(zipStream, 1024)
                        fileStream.close()
                        zipStream.closeEntry()
                    }
                    if (selectedCategories.contains(BackupCategory.CLIPBOARD)) {
                        val dbFile = ctx.getDatabasePath(Database.NAME)
                        if (dbFile.exists()) {
                            val fileStream = FileInputStream(dbFile).buffered()
                            zipStream.putNextEntry(ZipEntry(Database.NAME))
                            fileStream.copyTo(zipStream, 1024)
                            fileStream.close()
                            zipStream.closeEntry()
                        }
                    }
                    val filteredPrefs = ctx.prefs().all.filter {
                        selectedCategories.contains(getCategoryForPrefKey(it.key))
                    }
                    zipStream.putNextEntry(ZipEntry(PREFS_FILE_NAME))
                    settingsToJsonStream(filteredPrefs, zipStream)
                    zipStream.closeEntry()

                    val filteredProtectedPrefs = ctx.protectedPrefs().all.filter {
                        selectedCategories.contains(getCategoryForPrefKey(it.key))
                    }
                    zipStream.putNextEntry(ZipEntry(PROTECTED_PREFS_FILE_NAME))
                    settingsToJsonStream(filteredProtectedPrefs, zipStream)
                    zipStream.closeEntry()

                    for ((entryName, prefsForBackup) in auxiliaryPrefsToBackUp(ctx)) {
                        val cat = getCategoryForFilePath(entryName)
                        if (cat == null || selectedCategories.contains(cat)) {
                            val filteredAuxPrefs = prefsForBackup.all.filter {
                                selectedCategories.contains(getCategoryForPrefKey(it.key))
                            }
                            zipStream.putNextEntry(ZipEntry(entryName))
                            settingsToJsonStream(filteredAuxPrefs, zipStream)
                            zipStream.closeEntry()
                        }
                    }
                    zipStream.close()
                }
            } catch (t: Throwable) {
                onError("b" + t.message)
                Log.w("AdvancedScreen", "error during backup", t)
            } finally {
                wait.countDown()
            }
        }
        if (!wait.await(30, TimeUnit.SECONDS)) {
            Log.w("AdvancedScreen", "Backup timed out")
        }
    }
}

@Composable
private fun restoreLauncher(
    selectedCategories: Set<BackupCategory>,
    onError: (String) -> Unit
): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current
    return filePicker { uri ->
        val wait = CountDownLatch(1)
        val restoredDb = ctx.getDatabasePath(Database.NAME + "_restored")
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            try {
                ctx.getActivity()?.contentResolver?.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zip ->
                        var entry: ZipEntry? = zip.nextEntry
                        val filesDir = ctx.filesDir ?: return@execute
                        val deviceProtectedFilesDir = DeviceProtectedUtils.getFilesDir(ctx)

                        // Targeted deletion based on selected categories
                        if (selectedCategories.contains(BackupCategory.LAYOUTS)) {
                            File(filesDir, "layouts").deleteRecursively()
                        }
                        if (selectedCategories.contains(BackupCategory.DICTIONARY_HISTORY)) {
                            File(filesDir, "dicts").deleteRecursively()
                            File(filesDir, "blacklists").deleteRecursively()
                            File(deviceProtectedFilesDir, "blacklists").deleteRecursively()
                            filesDir.listFiles()?.forEach {
                                if (it.name.startsWith("UserHistoryDictionary")) it.delete()
                            }
                        }
                        if (selectedCategories.contains(BackupCategory.THEME_APPEARANCE)) {
                            File(filesDir, "custom_font").delete()
                            File(filesDir, "custom_emoji_font").delete()
                            deviceProtectedFilesDir.listFiles()?.forEach {
                                if (it.name.startsWith("custom_background_image")) it.delete()
                            }
                        }
                        if (selectedCategories.contains(BackupCategory.CLIPBOARD)) {
                            ClipboardDao.closeInstance()
                            Database.closeInstance()
                            ctx.deleteDatabase(Database.NAME)
                        }

                        LayoutUtilsCustom.onLayoutFileChanged()
                        Settings.getInstance().stopListener()
                        while (entry != null) {
                            if (entry.name.startsWith("unprotected${File.separator}")) {
                                val adjustedName = entry.name.substringAfter("unprotected${File.separator}")
                                if (backupFilePatterns.any { adjustedName.matches(it) }) {
                                    val cat = getCategoryForFilePath(adjustedName)
                                    if (cat == null || selectedCategories.contains(cat)) {
                                        File(deviceProtectedFilesDir, adjustedName).delete()
                                        if (!restoreEntryToDir(zip, deviceProtectedFilesDir, adjustedName)) {
                                            Log.w("AdvancedScreen", "skipping unsafe backup entry $adjustedName")
                                        }
                                    }
                                }
                            } else if (backupFilePatterns.any { entry.name.matches(it) }) {
                                val cat = getCategoryForFilePath(entry.name)
                                if (cat == null || selectedCategories.contains(cat)) {
                                    File(filesDir, entry.name).delete()
                                    if (!restoreEntryToDir(zip, filesDir, entry.name)) {
                                        Log.w("AdvancedScreen", "skipping unsafe backup entry ${entry.name}")
                                    }
                                }
                            } else if (entry.name == Database.NAME) {
                                if (selectedCategories.contains(BackupCategory.CLIPBOARD)) {
                                    FileUtils.copyStreamToNewFile(zip, restoredDb)
                                }
                            } else if (entry.name == PREFS_FILE_NAME) {
                                val prefLines = String(zip.readBytes()).split("\n")
                                val prefs = ctx.prefs()
                                prefs.edit(commit = true) {
                                    prefs.all.keys.forEach { key ->
                                        if (selectedCategories.contains(getCategoryForPrefKey(key))) {
                                            remove(key)
                                        }
                                    }
                                }
                                readJsonLinesToSettings(prefLines, prefs, selectedCategories)
                            } else if (entry.name == PROTECTED_PREFS_FILE_NAME) {
                                val prefLines = String(zip.readBytes()).split("\n")
                                val protectedPrefs = ctx.protectedPrefs()
                                protectedPrefs.edit(commit = true) {
                                    protectedPrefs.all.keys.forEach { key ->
                                        if (selectedCategories.contains(getCategoryForPrefKey(key))) {
                                            remove(key)
                                        }
                                    }
                                }
                                readJsonLinesToSettings(prefLines, protectedPrefs, selectedCategories)
                            } else {
                                val auxPrefs = auxiliaryPrefsToBackUp(ctx)[entry.name]
                                if (auxPrefs != null) {
                                    val cat = getCategoryForFilePath(entry.name)
                                    if (cat == null || selectedCategories.contains(cat)) {
                                        val prefLines = String(zip.readBytes()).split("\n")
                                        auxPrefs.edit(commit = true) {
                                            auxPrefs.all.keys.forEach { key ->
                                                if (selectedCategories.contains(getCategoryForPrefKey(key))) {
                                                    remove(key)
                                                }
                                            }
                                        }
                                        readJsonLinesToSettings(prefLines, auxPrefs, selectedCategories)
                                    }
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }
                if (selectedCategories.contains(BackupCategory.CLIPBOARD)) {
                    Database.copyFromDb(restoredDb, ctx)
                }
                Handler(Looper.getMainLooper()).post {
                    FeedbackManager.message(ctx, R.string.backup_restored)
                }
            } catch (t: Throwable) {
                onError("r" + t.message)
                Log.w("AdvancedScreen", "error during restore", t)
            } finally {
                wait.countDown()
            }
        }
        if (!wait.await(30, TimeUnit.SECONDS)) {
            Log.w("AdvancedScreen", "Restore timed out")
        }
        AppUpgrade.checkVersionUpgrade(ctx)
        AppUpgrade.transferOldPinnedClips(ctx)
        Settings.getInstance().startListener()
        SubtypeSettings.reloadEnabledSubtypes(ctx)
        val newDictBroadcast = Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)
        ctx.getActivity()?.sendBroadcast(newDictBroadcast)
        LayoutUtilsCustom.onLayoutFileChanged()
        LayoutUtilsCustom.removeMissingLayouts(ctx)
        (ctx.getActivity() as? SettingsActivity)?.prefChanged()
        SupportedEmojis.load(ctx)
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }
}

@Suppress("UNCHECKED_CAST") // it is checked... but whatever (except string set, because can't check for that))
private fun settingsToJsonStream(settings: Map<String?, Any?>, out: OutputStream) {
    val booleans = settings.filter { it.key is String && it.value is Boolean } as Map<String, Boolean>
    val ints = settings.filter { it.key is String && it.value is Int } as Map<String, Int>
    val longs = settings.filter { it.key is String && it.value is Long } as Map<String, Long>
    val floats = settings.filter { it.key is String && it.value is Float } as Map<String, Float>
    val strings = settings.filter { it.key is String && it.value is String } as Map<String, String>
    val stringSets = settings.filter { it.key is String && it.value is Set<*> } as Map<String, Set<String>>
    // now write
    out.write("boolean settings\n".toByteArray())
    out.write(Json.encodeToString(booleans).toByteArray())
    out.write("\nint settings\n".toByteArray())
    out.write(Json.encodeToString(ints).toByteArray())
    out.write("\nlong settings\n".toByteArray())
    out.write(Json.encodeToString(longs).toByteArray())
    out.write("\nfloat settings\n".toByteArray())
    out.write(Json.encodeToString(floats).toByteArray())
    out.write("\nstring settings\n".toByteArray())
    out.write(Json.encodeToString(strings).toByteArray())
    out.write("\nstring set settings\n".toByteArray())
    out.write(Json.encodeToString(stringSets).toByteArray())
}

private fun readJsonLinesToSettings(list: List<String>, prefs: SharedPreferences, selectedCategories: Set<BackupCategory>): Boolean {
    val i = list.iterator()
    val e = prefs.edit()
    try {
        while (i.hasNext()) {
            when (i.next()) {
                "boolean settings" -> Json.decodeFromString<Map<String, Boolean>>(i.next())
                    .filter { selectedCategories.contains(getCategoryForPrefKey(it.key)) }
                    .forEach { e.putBoolean(it.key, it.value) }
                "int settings" -> Json.decodeFromString<Map<String, Int>>(i.next())
                    .filter { selectedCategories.contains(getCategoryForPrefKey(it.key)) }
                    .forEach { e.putInt(it.key, it.value) }
                "long settings" -> Json.decodeFromString<Map<String, Long>>(i.next())
                    .filter { selectedCategories.contains(getCategoryForPrefKey(it.key)) }
                    .forEach { e.putLong(it.key, it.value) }
                "float settings" -> Json.decodeFromString<Map<String, Float>>(i.next())
                    .filter { selectedCategories.contains(getCategoryForPrefKey(it.key)) }
                    .forEach { e.putFloat(it.key, it.value) }
                "string settings" -> Json.decodeFromString<Map<String, String>>(i.next())
                    .filter { selectedCategories.contains(getCategoryForPrefKey(it.key)) }
                    .forEach { e.putString(it.key, it.value) }
                "string set settings" -> Json.decodeFromString<Map<String, Set<String>>>(i.next())
                    .filter { selectedCategories.contains(getCategoryForPrefKey(it.key)) }
                    .forEach { e.putStringSet(it.key, it.value) }
            }
        }
        e.commit()
        return true
    } catch (e: Exception) {
        return false
    }
}

/**
 * Auxiliary SharedPreferences files (other than the main prefs and protectedPrefs) that
 * should be included in backups. The key is the zip entry name to use, and the value
 * is the SharedPreferences instance to read from / write back into on restore.
 *
 * NOTE: This must NOT include EncryptedSharedPreferences (e.g. "gemini_prefs"), because
 * those values are encrypted with a device-bound master key and would be unreadable on
 * any other device. Plus they typically hold credentials, which we don't want in a plain
 * backup zip.
 */
private fun auxiliaryPrefsToBackUp(ctx: android.content.Context): Map<String, SharedPreferences> =
    mapOf(
        FLOATING_KEYBOARD_PREFS_FILE_NAME
            to DeviceProtectedUtils.getSharedPreferences(ctx, "floating_keyboard_prefs"),
    )

private fun restoreEntryToDir(zip: ZipInputStream, baseDir: File, entryName: String): Boolean {
    val file = File(baseDir, entryName)
    val canonicalBase = baseDir.canonicalFile
    val canonicalTarget = file.canonicalFile
    if (canonicalTarget.path != canonicalBase.path
        && !canonicalTarget.path.startsWith(canonicalBase.path + File.separator)
    ) return false
    FileUtils.copyStreamToNewFile(zip, file)
    return true
}

private const val PREFS_FILE_NAME = "preferences.json"
private const val PROTECTED_PREFS_FILE_NAME = "protected_preferences.json"
private const val FLOATING_KEYBOARD_PREFS_FILE_NAME = "floating_keyboard_preferences.json"

private val backupFilePatterns by lazy { listOf(
    "blacklists${File.separator}.*\\.txt".toRegex(),
    "layouts${File.separator}.*${LayoutUtilsCustom.CUSTOM_LAYOUT_PREFIX}+\\..{0,4}".toRegex(), // can't expect a period at the end, as this would break restoring older backups
    "dicts${File.separator}.*${File.separator}.*user\\.dict".toRegex(),
    "UserHistoryDictionary.*${File.separator}UserHistoryDictionary.*\\.(body|header)".toRegex(),
    "custom_background_image.*".toRegex(),
    "custom_font".toRegex(),
    "custom_emoji_font".toRegex(),
) }

enum class BackupCategory {
    LAYOUTS,
    THEME_APPEARANCE,
    DICTIONARY_HISTORY,
    CLIPBOARD,
    GENERAL_SETTINGS
}

private fun getCategoryForPrefKey(key: String): BackupCategory {
    if (key.startsWith("layout_")) return BackupCategory.LAYOUTS
    
    val themeKeys = setOf(
        "theme_style", "icon_style", "theme_colors", "theme_colors_night",
        "theme_key_borders", "theme_auto_day_night", "custom_icon_names",
        "navbar_color", "font_scale", "emoji_font_scale", "narrow_key_gaps",
        "narrow_key_gaps_level", "emoji_key_fit", "emoji_skin_tone", "space_bar_text"
    )
    if (themeKeys.contains(key) 
        || key.startsWith("user_colors_") 
        || key.startsWith("user_all_colors_")
        || key.startsWith("user_more_colors_")
        || key.startsWith("keyboard_height_scale")
        || key.startsWith("bottom_padding_scale")
        || key.startsWith("side_padding_scale")
        || key.startsWith("split_spacer_scale")
    ) {
        return BackupCategory.THEME_APPEARANCE
    }
    
    val dictKeys = setOf(
        "use_personalized_dicts", "block_potentially_offensive", "next_word_prediction", "first_word_prediction",
        "suggest_emojis", "inline_emoji_search", "show_emoji_descriptions",
        "auto_correction", "more_auto_correction", "auto_correct_threshold",
        "autocorrect_shortcuts", "backspace_reverts_autocorrect", "suggest_punctuation",
        "add_to_personal_dictionary"
    )
    if (dictKeys.contains(key) || key.startsWith("pref_text_expander_")) return BackupCategory.DICTIONARY_HISTORY
    
    val clipboardKeys = setOf(
        "enable_clipboard_history", "suggest_screenshots", "compress_screenshots",
        "clipboard_history_retention_time", "clipboard_history_pinned_first",
        "clipboard_fold_pinned", "clear_clipboard_icon"
    )
    if (clipboardKeys.contains(key)) return BackupCategory.CLIPBOARD
    
    return BackupCategory.GENERAL_SETTINGS
}

private fun getCategoryForFilePath(path: String): BackupCategory? {
    if (path.startsWith("layouts${File.separator}") || path.contains("layouts/")) {
        return BackupCategory.LAYOUTS
    }
    if (path.startsWith("custom_background_image") || path == "custom_font" || path == "custom_emoji_font" || path == FLOATING_KEYBOARD_PREFS_FILE_NAME) {
        return BackupCategory.THEME_APPEARANCE
    }
    if (path.startsWith("dicts${File.separator}") || path.startsWith("dicts/")
        || path.startsWith("blacklists${File.separator}") || path.startsWith("blacklists/")
        || path.startsWith("UserHistoryDictionary")
    ) {
        return BackupCategory.DICTIONARY_HISTORY
    }
    if (path == Database.NAME) {
        return BackupCategory.CLIPBOARD
    }
    return null
}
