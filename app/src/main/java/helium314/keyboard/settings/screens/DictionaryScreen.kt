// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import helium314.keyboard.settings.dialogs.PreferenceDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getDictionaryLocales
import helium314.keyboard.latin.utils.htmlToAnnotated
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.withHtmlLink
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.DictionaryDialog
import helium314.keyboard.settings.dictionaryFilePicker
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.previewDark
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.NextScreenIcon
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.utils.prefs
import java.io.File
import java.util.Locale

@Composable
fun DictionaryScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val enabledLanguages = SubtypeSettings.getEnabledSubtypes(true).map { it.locale().language }
    val cachedDictFolders = DictionaryInfoUtils.getCacheDirectories(ctx).map { it.name }
    val comparer = compareBy<Locale>({ it.language !in enabledLanguages }, { it.toLanguageTag() !in cachedDictFolders }, { it.displayName })
    val dictionaryLocales = listOf(Locale.forLanguageTag(SubtypeLocaleUtils.NO_LANGUAGE)) + getDictionaryLocales(ctx)
        .filter { it.language != SubtypeLocaleUtils.NO_LANGUAGE }
        .sortedWith(comparer)
    var selectedLocale: Locale? by remember { mutableStateOf(null) }
    var showAddDictDialog by remember { mutableStateOf(false) }
    val dictPicker = dictionaryFilePicker(selectedLocale)

    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(stringResource(R.string.dictionary_settings_category)) },
        filteredItems = { term ->
            if (term.isBlank()) dictionaryLocales
            else dictionaryLocales.filter { loc ->
                    loc.language != SubtypeLocaleUtils.NO_LANGUAGE
                            && loc.localizedDisplayName(ctx.resources).replace("(", "")
                                .splitOnWhitespace().any { it.startsWith(term, true) }
                }
        },
        itemContent = { locale ->
            if (locale.language == SubtypeLocaleUtils.NO_LANGUAGE) {
                // Card 1: Dictionaries Management
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory(stringResource(R.string.dictionary_settings_category))

                        // Add Dictionary Entry
                        Preference(
                            name = stringResource(R.string.add_new_dictionary_title),
                            icon = R.drawable.ic_plus,
                            onClick = { showAddDictDialog = true },
                            value = { NextScreenIcon() }
                        )

                        // Personal Dictionary Entry
                        Preference(
                            name = stringResource(R.string.edit_personal_dictionary),
                            icon = R.drawable.ic_dictionary,
                            onClick = { SettingsDestination.navigateTo(SettingsDestination.PersonalDictionaries) },
                            value = { NextScreenIcon() }
                        )

                        // Blocked Words Entry
                        Preference(
                            name = stringResource(R.string.edit_blocked_words),
                            icon = R.drawable.ic_bin,
                            onClick = { SettingsDestination.navigateTo(SettingsDestination.BlockedWords) },
                            value = { NextScreenIcon() }
                        )

                        // Dictionary Source Entry
                        Preference(
                            name = stringResource(R.string.dictionary_source_title),
                            description = stringResource(R.string.dictionary_source_summary),
                            icon = R.drawable.ic_settings_about_github,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(helium314.keyboard.latin.common.Links.DICTIONARY_URL))
                                ctx.startActivity(intent)
                            },
                            value = { NextScreenIcon() }
                        )
                    }
                }

                // Card 2: Personal Dictionary Learning & Threshold
                val prefs = ctx.prefs()
                var personalDictEnabled by remember { mutableStateOf(prefs.getBoolean(Settings.PREF_ADD_TO_PERSONAL_DICTIONARY, Defaults.PREF_ADD_TO_PERSONAL_DICTIONARY)) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column {
                        PreferenceCategory("Word learning")

                        SwitchPreference(
                            name = stringResource(R.string.add_to_personal_dictionary),
                            description = "Add typed words to personal dictionary",
                            key = Settings.PREF_ADD_TO_PERSONAL_DICTIONARY,
                            default = Defaults.PREF_ADD_TO_PERSONAL_DICTIONARY,
                            icon = R.drawable.ic_settings_correction,
                            onCheckedChange = { personalDictEnabled = it }
                        )

                        if (personalDictEnabled) {
                            SliderPreference(
                                name = stringResource(R.string.add_to_personal_dict_threshold),
                                key = Settings.PREF_ADD_TO_PERSONAL_DICT_THRESHOLD,
                                default = Defaults.PREF_ADD_TO_PERSONAL_DICT_THRESHOLD,
                                icon = R.drawable.ic_settings_preferences,
                                range = 1f..5f,
                                stepSize = 1,
                                description = {
                                    if (it == 1) {
                                        stringResource(R.string.add_to_personal_dict_threshold_times_1)
                                    } else {
                                        stringResource(R.string.add_to_personal_dict_threshold_times_many, it)
                                    }
                                }
                            )
                        }
                    }
                }

                // Languages Section Header
                PreferenceCategory(stringResource(R.string.language_and_layouts_title))
            } else {
                // Language Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { selectedLocale = locale },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = locale.localizedDisplayName(LocalResources.current),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val (dicts, hasInternal) = getUserAndInternalDictionaries(ctx, locale)
                            val mainDictLabel = stringResource(R.string.main_dictionary)
                            val internalDictLabel = stringResource(R.string.internal_dictionary_summary)
                            val types = dicts.mapTo(mutableListOf()) { file ->
                                if (file.name == DictionaryInfoUtils.MAIN_DICT_FILE_NAME) {
                                    mainDictLabel
                                } else {
                                    file.name.substringBefore("_${DictionaryInfoUtils.USER_DICTIONARY_SUFFIX}")
                                }
                            }
                            if (hasInternal && !types.contains(Dictionary.TYPE_MAIN) && !types.contains(mainDictLabel))
                                types.add(0, internalDictLabel)
                            
                            // Render active dictionaries as stylized badges
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (types.isEmpty()) {
                                    Text(
                                        text = "No active dictionaries",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                } else {
                                    types.forEach { type ->
                                        val badgeColor = when (type.lowercase()) {
                                            "main", internalDictLabel.lowercase(), mainDictLabel.lowercase() -> MaterialTheme.colorScheme.primaryContainer
                                            "user" -> MaterialTheme.colorScheme.secondaryContainer
                                            else -> MaterialTheme.colorScheme.tertiaryContainer
                                        }
                                        val badgeTextColor = when (type.lowercase()) {
                                            "main", internalDictLabel.lowercase(), mainDictLabel.lowercase() -> MaterialTheme.colorScheme.onPrimaryContainer
                                            "user" -> MaterialTheme.colorScheme.onSecondaryContainer
                                            else -> MaterialTheme.colorScheme.onTertiaryContainer
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(badgeColor)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = type,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = badgeTextColor,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        NextScreenIcon()
                    }
                }
            }
        }
    )
    if (showAddDictDialog) {
        PreferenceDialog(
            onDismissRequest = { showAddDictDialog = false },
            title = stringResource(R.string.add_new_dictionary_title),
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val link = stringResource(R.string.dictionary_link_text).withHtmlLink(Links.DICTIONARY_URL)
                    val addDictString = stringResource(R.string.add_dictionary, link)
                    Text(addDictString.htmlToAnnotated(), style = MaterialTheme.typography.bodyMedium)
                }
            },
            buttons = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showAddDictDialog = false
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("application/octet-stream")
                            dictPicker.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.load_gesture_library_button_load))
                    }
                    OutlinedButton(
                        onClick = {
                            showAddDictDialog = false
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Links.DICTIONARY_URL)).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try {
                                ctx.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Download from GitHub")
                    }
                }
            }
        )
    }
    val currentSelectedLocale = selectedLocale
    if (currentSelectedLocale != null) {
        DictionaryDialog(
            onDismissRequest = { selectedLocale = null },
            locale = currentSelectedLocale
        )
    }
}

fun getUserAndInternalDictionaries(context: Context, locale: Locale): Pair<List<File>, Boolean> {
    val userDicts = mutableListOf<File>()
    var hasInternalDict = false

    val candidateDirs = mutableListOf<File>()
    DictionaryInfoUtils.getCacheDirectoryForLocale(locale, context)?.let { candidateDirs.add(File(it)) }
    if (locale.country.isNotEmpty() || locale.variant.isNotEmpty()) {
        val fallbackLocale = Locale.forLanguageTag(locale.language)
        DictionaryInfoUtils.getCacheDirectoryForLocale(fallbackLocale, context)?.let { candidateDirs.add(File(it)) }
    }
    DictionaryInfoUtils.getFallbackVariantDirectory(locale, context)?.let { candidateDirs.add(it) }

    val internalDicts = DictionaryInfoUtils.getAssetsDictionaryList(context)
    val best = internalDicts?.let {
        LocaleUtils.getBestMatch(locale, it.toList()) { dict ->
            DictionaryInfoUtils.extractLocaleFromAssetsDictionaryFile(dict)
        }
    }
    val hasAsset = best != null

    val seenFiles = mutableSetOf<String>()
    candidateDirs.filter { it.exists() && it.isDirectory }.forEach { dir ->
        dir.listFiles()?.forEach { file ->
            if (seenFiles.add(file.name)) {
                if (file.name.endsWith(DictionaryInfoUtils.USER_DICTIONARY_SUFFIX)) {
                    userDicts.add(file)
                } else if (file.name.startsWith(DictionaryInfoUtils.MAIN_DICT_PREFIX)) {
                    hasInternalDict = true
                } else if (file.name.endsWith(".dict")) {
                    if (file.name == DictionaryInfoUtils.MAIN_DICT_FILE_NAME) {
                        if (!hasAsset) {
                            userDicts.add(file)
                        } else {
                            hasInternalDict = true
                        }
                    } else if (file.name == "emoji.dict") {
                        val hasEmojiAsset = internalDicts?.any { asset -> asset.startsWith("emoji") } == true
                        if (!hasEmojiAsset) {
                            userDicts.add(file)
                        } else {
                            hasInternalDict = true
                        }
                    } else {
                        userDicts.add(file)
                    }
                }
            }
        }
    }

    return userDicts to (hasInternalDict || hasAsset)
}

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            DictionaryScreen { }
        }
    }
}
