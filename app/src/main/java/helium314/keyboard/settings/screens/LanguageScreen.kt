// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.MissingDictionaryDialog
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.initPreview
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import helium314.keyboard.settings.NextScreenIcon
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.settings.previewDark
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.LocaleUtils
import helium314.keyboard.latin.utils.mainLayoutNameOrQwerty
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.ListPreference
import java.util.Locale

@Composable
fun LanguageScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefChanged = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    val enabledSubtypes = remember(prefChanged?.value) {
        SubtypeSettings.getEnabledSubtypes()
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.language_and_layouts_title),
        settings = listOf(Settings.PREF_APP_LANGUAGE)
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            // Card 1: Active Keyboards
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column {
                    PreferenceCategory(stringResource(R.string.active_keyboards_title))
                    for (subtype in enabledSubtypes) {
                        val layoutName = subtype.mainLayoutNameOrQwerty()
                        val layoutDisplayName = SubtypeLocaleUtils.getLayoutDisplayNameInSystemLocale(layoutName, subtype.locale())
                        val secondaryLocales = if (SubtypeSettings.isAdditionalSubtype(subtype)) {
                            subtype.getExtraValueOf(ExtraValue.SECONDARY_LOCALES)?.split(Separators.KV)
                                ?.joinToString(", ") { it.constructLocale().localizedDisplayName(ctx.resources) }
                        } else null
                        val layoutDesc = stringResource(R.string.keyboard_layout_set) + ": " + layoutDisplayName +
                            (secondaryLocales?.let { " • $it" } ?: "")

                        Preference(
                            name = subtype.locale().localizedDisplayName(ctx.resources),
                            description = layoutDesc,
                            icon = R.drawable.ic_ime_switcher,
                            onClick = {
                                SettingsDestination.navigateTo(SettingsDestination.Subtype + subtype.toSettingsSubtype().toPref())
                            }
                        ) { NextScreenIcon() }
                    }

                    Preference(
                        name = stringResource(R.string.add_language_title),
                        description = stringResource(R.string.add_language_summary),
                        icon = R.drawable.ic_plus,
                        onClick = { SettingsDestination.navigateTo(SettingsDestination.LanguagesList) }
                    ) { NextScreenIcon() }
                }
            }

            // Card 2: Configuration
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column {
                    PreferenceCategory(stringResource(R.string.settings_category_configuration))
                    Preference(
                        name = stringResource(R.string.settings_screen_secondary_layouts),
                        onClick = { SettingsDestination.navigateTo(SettingsDestination.Layouts) },
                        icon = R.drawable.ic_ime_switcher
                    ) { NextScreenIcon() }
                    Preference(
                        name = stringResource(R.string.dictionary_settings_category),
                        description = stringResource(R.string.dictionaries_screen_summary),
                        onClick = { SettingsDestination.navigateTo(SettingsDestination.Dictionaries) },
                        icon = R.drawable.ic_dictionary
                    ) { NextScreenIcon() }
                    SettingsActivity.settingsContainer[Settings.PREF_APP_LANGUAGE]?.Preference()
                }
            }
        }
    }
}

fun createLanguageSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_APP_LANGUAGE, R.string.app_language_title, R.string.app_language_summary) {
        ListPreference(
            it,
            LocaleUtils.getAppLanguageItems(context),
            Defaults.PREF_APP_LANGUAGE,
            icon = R.drawable.ic_settings_languages
        )
    }
)

@Composable
fun LanguagesListScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val sortedSubtypes by remember { mutableStateOf(getSortedSubtypes(ctx)) }
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    val enabledSubtypes = remember(b?.value) { SubtypeSettings.getEnabledSubtypes().toSet() }
    SearchScreen(
        onClickBack = onClickBack,
        title = {
            Column {
                Text(stringResource(R.string.languages_title))
                Text(
                    stringResource(R.string.text_tap_languages),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        filteredItems = { term ->
            sortedSubtypes.filter { subtype ->
                subtype.displayName().replace("(", "")
                    .splitOnWhitespace().any { it.startsWith(term, true) }
            }
        },
        itemContent = { SubtypeRow(it, it in enabledSubtypes) }
    )
}

@Composable
private fun SubtypeRow(subtype: InputMethodSubtype, isEnabled: Boolean) {
    val ctx = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                SettingsDestination.navigateTo(SettingsDestination.Subtype + subtype.toSettingsSubtype().toPref())
            }
            .padding(vertical = 6.dp, horizontal = 16.dp)
    ) {
        var showNoDictDialog by remember { mutableStateOf(false) }
        Column(modifier = Modifier.weight(1f)) {
            Text(subtype.displayName(), style = MaterialTheme.typography.bodyLarge)
            val description = if (SubtypeSettings.isAdditionalSubtype(subtype)) {
                val layoutName = subtype.mainLayoutNameOrQwerty()
                val layoutDisplayName = SubtypeLocaleUtils.getLayoutDisplayNameInSystemLocale(layoutName, subtype.locale())
                val secondaryLocales = subtype.getExtraValueOf(ExtraValue.SECONDARY_LOCALES)?.split(Separators.KV)
                    ?.joinToString(", ") { it.constructLocale().localizedDisplayName(ctx.resources) }
                stringResource(R.string.keyboard_layout_set) + ": " + layoutDisplayName + (secondaryLocales?.let { " • $it" } ?: "")
            } else if (isEnabled) {
                val layoutName = subtype.mainLayoutNameOrQwerty()
                val layoutDisplayName = SubtypeLocaleUtils.getLayoutDisplayNameInSystemLocale(layoutName, subtype.locale())
                stringResource(R.string.keyboard_layout_set) + ": " + layoutDisplayName
            } else null
            if (description != null)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        key(subtype) {
            Switch(
                checked = isEnabled,
                onCheckedChange = {
                    if (it && !dictsAvailable(subtype.locale(), ctx))
                        showNoDictDialog = true
                    if (it) SubtypeSettings.addEnabledSubtype(ctx.prefs(), subtype)
                    else SubtypeSettings.removeEnabledSubtype(ctx, subtype)
                    (ctx.getActivity() as? SettingsActivity)?.prefChanged()
                }
            )
        }
        if (showNoDictDialog)
            MissingDictionaryDialog({ showNoDictDialog = false }, subtype.locale())
    }
}

private fun dictsAvailable(locale: Locale, context: Context): Boolean {
    val lang = locale.language.lowercase()
    if (lang == "zh" || lang == "ja") return true
    val (dicts, hasInternal) = getUserAndInternalDictionaries(context, locale)
    return hasInternal || dicts.isNotEmpty()
}

// sorting by display name is still slow, even with the cache... but probably good enough
private fun getSortedSubtypes(context: Context): List<InputMethodSubtype> {
    val systemLocales = SubtypeSettings.getSystemLocales()
    val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(true)
    val localesWithDictionary = DictionaryInfoUtils.getCacheDirectories(context).mapNotNull { dir ->
        if (dir.list()?.any { it.endsWith(DictionaryInfoUtils.USER_DICTIONARY_SUFFIX) } == true)
            dir.name.constructLocale()
        else null
    }

    val defaultAdditionalSubtypes = Defaults.PREF_ADDITIONAL_SUBTYPES.split(Separators.SETS).map {
        it.substringBefore(Separators.SET) to (it.substringAfter(Separators.SET) + ",AsciiCapable,EmojiCapable,isAdditionalSubtype")
    }
    fun isDefaultSubtype(subtype: InputMethodSubtype): Boolean =
        defaultAdditionalSubtypes.any { it.first == subtype.locale().language && it.second == subtype.extraValue }

    val subtypeSortComparator = compareBy<InputMethodSubtype>(
        { it !in enabledSubtypes },
        { it.locale() !in localesWithDictionary },
        { it.locale() !in systemLocales},
        { !(SubtypeSettings.isAdditionalSubtype(it) && !isDefaultSubtype(it) ) },
        {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) it.languageTag == SubtypeLocaleUtils.NO_LANGUAGE
            else it.locale == SubtypeLocaleUtils.NO_LANGUAGE
        },
        { it.displayName() }
    )
    return SubtypeSettings.getAllAvailableSubtypes().sortedWith(subtypeSortComparator)
}

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            LanguageScreen { }
        }
    }
}
