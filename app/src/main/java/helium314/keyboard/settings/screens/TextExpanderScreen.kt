/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.utils.TextExpanderUtils
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.settings.preferences.SwitchPreference

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextExpanderScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.prefs()

    var isExpanderEnabled by remember {
        mutableStateOf(TextExpanderUtils.isEnabled(context))
    }

    var isImmediateEnabled by remember {
        mutableStateOf(TextExpanderUtils.isImmediateEnabled(context))
    }

    var isBackspaceRevertsEnabled by remember {
        mutableStateOf(TextExpanderUtils.isBackspaceRevertsEnabled(context))
    }

    var isSuggestionsEnabled by remember {
        mutableStateOf(TextExpanderUtils.isSuggestionsEnabled(context))
    }

    var isSuggestionsOnly by remember {
        mutableStateOf(TextExpanderUtils.isSuggestionsOnly(context))
    }

    var shortcutsMap by remember {
        mutableStateOf(TextExpanderUtils.getShortcuts(context))
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingPrefix by remember { mutableStateOf("") }
    var editingShortcut by remember { mutableStateOf("") }
    var editingTemplate by remember { mutableStateOf(TextFieldValue("")) }
    var originalShortcutToEdit by remember { mutableStateOf<String?>(null) }
    var editingIsRegex by remember { mutableStateOf(false) }
    var showTextEditor by remember { mutableStateOf(false) }

    val openGuide = {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Links.TEXT_EXPANDER_GUIDE_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SearchScreen(
            onClickBack = onClickBack,
            title = {
                Text(
                    text = stringResource(R.string.text_expander_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            },
            filteredItems = { term ->
                shortcutsMap.entries
                    .filter { (shortcut, entry) ->
                        val isRegex = shortcut.startsWith(TextExpanderUtils.REGEX_PREFIX)
                        val cleanKey = if (isRegex) shortcut.substring(TextExpanderUtils.REGEX_PREFIX.length) else shortcut
                        val rawShortcut = cleanKey.substring(entry.prefix.length)
                        val displayShortcut = entry.prefix + rawShortcut
                        displayShortcut.contains(term, ignoreCase = true) ||
                        entry.template.contains(term, ignoreCase = true)
                    }
                    .map { Pair(it.key, it.value) }
            },
            itemContent = { (shortcut, entry) ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    ShortcutItem(
                        shortcut = shortcut,
                        entry = entry,
                        onEdit = {
                            val isRegex = shortcut.startsWith(TextExpanderUtils.REGEX_PREFIX)
                            val cleanKey = if (isRegex) shortcut.substring(TextExpanderUtils.REGEX_PREFIX.length) else shortcut
                            editingPrefix = entry.prefix
                            editingShortcut = cleanKey.substring(entry.prefix.length)
                            editingTemplate = TextFieldValue(entry.template)
                            originalShortcutToEdit = shortcut
                            editingIsRegex = isRegex
                            showAddDialog = true
                        },
                        onDelete = {
                            val updated = shortcutsMap.toMutableMap()
                            updated.remove(shortcut)
                            shortcutsMap = updated
                            TextExpanderUtils.saveShortcuts(context, updated)
                        }
                    )
                }
            },
            content = {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Behavior & Options
                    PreferenceCategory(stringResource(R.string.text_expander_options_category))

                    SwitchPreference(
                        name = stringResource(R.string.text_expander_enable_title),
                        key = TextExpanderUtils.PREF_ENABLED,
                        default = false,
                        description = stringResource(R.string.text_expander_enable_summary),
                        onCheckedChange = { isExpanderEnabled = it }
                    )

                    SwitchPreference(
                        name = stringResource(R.string.text_expander_immediate_title),
                        key = TextExpanderUtils.PREF_IMMEDIATE,
                        default = false,
                        description = stringResource(R.string.text_expander_immediate_summary),
                        enabled = isExpanderEnabled,
                        onCheckedChange = { isImmediateEnabled = it }
                    )

                    SwitchPreference(
                        name = stringResource(R.string.text_expander_backspace_undo_title),
                        key = TextExpanderUtils.PREF_BACKSPACE_REVERTS,
                        default = false,
                        description = stringResource(R.string.text_expander_backspace_undo_summary),
                        enabled = isExpanderEnabled,
                        onCheckedChange = { isBackspaceRevertsEnabled = it }
                    )

                    SwitchPreference(
                        name = stringResource(R.string.text_expander_suggestions_title),
                        key = TextExpanderUtils.PREF_SUGGESTIONS,
                        default = true,
                        description = stringResource(R.string.text_expander_suggestions_summary),
                        enabled = isExpanderEnabled,
                        onCheckedChange = { isSuggestionsEnabled = it }
                    )

                    SwitchPreference(
                        name = stringResource(R.string.text_expander_suggestions_only_title),
                        key = TextExpanderUtils.PREF_SUGGESTIONS_ONLY,
                        default = false,
                        description = stringResource(R.string.text_expander_suggestions_only_summary),
                        enabled = isExpanderEnabled && isSuggestionsEnabled,
                        onCheckedChange = { isSuggestionsOnly = it }
                    )

                    // 2. Tools & Documentation
                    PreferenceCategory(stringResource(R.string.text_expander_tools_category))

                    Preference(
                        name = stringResource(R.string.text_expander_all_as_text),
                        description = stringResource(R.string.text_expander_all_as_text_summary),
                        icon = R.drawable.ic_open_in_full,
                        onClick = { showTextEditor = true }
                    )

                    Preference(
                        name = stringResource(R.string.text_expander_guide_title),
                        description = stringResource(R.string.text_expander_guide_summary),
                        icon = R.drawable.ic_settings_about_wiki,
                        onClick = { openGuide() }
                    )

                    // 3. Shortcuts List
                    PreferenceCategory("${stringResource(R.string.text_expander_custom_shortcuts_title)} (${shortcutsMap.size})")

                    if (shortcutsMap.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp, horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.text_expander_no_shortcuts_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = stringResource(R.string.text_expander_no_shortcuts_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        shortcutsMap.entries.forEach { (shortcut, entry) ->
                            ShortcutItem(
                                shortcut = shortcut,
                                entry = entry,
                                onEdit = {
                                    val isRegex = shortcut.startsWith(TextExpanderUtils.REGEX_PREFIX)
                                    val cleanKey = if (isRegex) shortcut.substring(TextExpanderUtils.REGEX_PREFIX.length) else shortcut
                                    editingPrefix = entry.prefix
                                    editingShortcut = cleanKey.substring(entry.prefix.length)
                                    editingTemplate = TextFieldValue(entry.template)
                                    originalShortcutToEdit = shortcut
                                    editingIsRegex = isRegex
                                    showAddDialog = true
                                },
                                onDelete = {
                                    val updated = shortcutsMap.toMutableMap()
                                    updated.remove(shortcut)
                                    shortcutsMap = updated
                                    TextExpanderUtils.saveShortcuts(context, updated)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(88.dp))
                }
            }
        )

        // Floating Action Button to Add New Shortcut
        if (isExpanderEnabled && !WindowInsets.isImeVisible) {
            ExtendedFloatingActionButton(
                onClick = {
                    editingShortcut = ""
                    editingTemplate = TextFieldValue("")
                    originalShortcutToEdit = null
                    editingIsRegex = false
                    showAddDialog = true
                },
                text = { Text(stringResource(R.string.text_expander_add_shortcut)) },
                icon = { Icon(painter = painterResource(R.drawable.ic_edit), stringResource(R.string.text_expander_add_shortcut)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(all = 16.dp)
                    .then(Modifier.safeDrawingPadding())
            )
        }
    }

    if (showTextEditor) {
        TextExpanderTextEditor { changed ->
            showTextEditor = false
            if (changed) shortcutsMap = TextExpanderUtils.getShortcuts(context)
        }
    }

    // Add / Edit Shortcut Dialog
    if (showAddDialog) {
        val focusRequester = remember { FocusRequester() }
        val isEditMode = originalShortcutToEdit != null
        val isRegexValid = remember(editingShortcut, editingIsRegex) {
            !editingIsRegex || runCatching { Regex(editingShortcut.trim()) }.isSuccess
        }
        
        ThreeButtonAlertDialog(
            onDismissRequest = { showAddDialog = false },
            onConfirmed = {
                val updated = shortcutsMap.toMutableMap()
                if (isEditMode) {
                    updated.remove(originalShortcutToEdit)
                }
                val key = if (editingIsRegex) {
                    TextExpanderUtils.REGEX_PREFIX + editingPrefix.trim() + editingShortcut.trim()
                } else {
                    editingPrefix.trim() + editingShortcut.trim()
                }
                updated[key] = TextExpanderUtils.ShortcutEntry(editingTemplate.text, editingPrefix.trim())
                shortcutsMap = updated
                TextExpanderUtils.saveShortcuts(context, updated)
                showAddDialog = false
            },
            checkOk = { editingShortcut.trim().isNotEmpty() && editingTemplate.text.isNotEmpty() && isRegexValid },
            confirmButtonText = if (isEditMode) stringResource(R.string.text_expander_save) else stringResource(R.string.text_expander_add),
            neutralButtonText = if (isEditMode) stringResource(R.string.text_expander_delete) else null,
            onNeutral = {
                if (isEditMode) {
                    val updated = shortcutsMap.toMutableMap()
                    updated.remove(originalShortcutToEdit)
                    shortcutsMap = updated
                    TextExpanderUtils.saveShortcuts(context, updated)
                }
                showAddDialog = false
            },
            title = {
                Text(text = if (isEditMode) stringResource(R.string.text_expander_edit_shortcut) else stringResource(R.string.text_expander_add_shortcut))
            },
            content = {
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = editingPrefix,
                            onValueChange = { editingPrefix = it.replace(" ", "") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.text_expander_prefix_title)) }
                        )
                        TextField(
                            value = editingShortcut,
                            onValueChange = { editingShortcut = if (editingIsRegex) it else it.replace(" ", "") },
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            singleLine = true,
                            label = { Text(if (editingIsRegex) stringResource(R.string.text_expander_regex_pattern_title) else stringResource(R.string.text_expander_shortcut_title)) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.text_expander_regex_title),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        androidx.compose.material3.Switch(
                            checked = editingIsRegex,
                            onCheckedChange = { checked ->
                                editingIsRegex = checked
                                if (!checked) {
                                    editingShortcut = editingShortcut.replace(" ", "")
                                }
                            }
                        )
                    }

                    if (editingIsRegex && !isRegexValid) {
                        Text(
                            text = stringResource(R.string.text_expander_regex_invalid),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    OutlinedTextField(
                        value = editingTemplate,
                        onValueChange = { editingTemplate = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        label = { Text(stringResource(R.string.text_expander_template_title)) },
                        placeholder = { Text(stringResource(R.string.text_expander_template_hint)) }
                    )

                    Text(
                        text = stringResource(R.string.text_expander_quick_placeholders_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    PlaceholderTagsRow(editingTemplate) { editingTemplate = it }
                }
            }
        )
    }
}

/** Placeholder tags, a tap inserts the tag at the cursor */
@Composable
internal fun PlaceholderTagsRow(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    val editingTemplate = value
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val tags = listOf(
            "%clipboard%", "%clipboard:clean%", "%clipboard:singleline%",
            "%clipboard:title%", "%clipboard:slug%", "%clipboard:upper%",
            "%date%", "%date:yyyy/MM/dd%", "%date:dd-MM-yyyy%", "%time%", "%time12%",
            "%day%", "%month%", "%year%", "%week%",
            "%battery%", "%language%", "%cursor%", "%greeting%",
            "%tomorrow%", "%bullets%", "%list%",
            "%tab%"
        )
        tags.forEach { tag ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    .clickable {
                        val text = editingTemplate.text
                        val selection = editingTemplate.selection
                        val start = selection.start
                        val end = selection.end
                        val newText = text.substring(0, start) + tag + text.substring(end)
                        val newSelectionRange = androidx.compose.ui.text.TextRange(start + tag.length)
                        onValueChange(TextFieldValue(text = newText, selection = newSelectionRange))
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ShortcutItem(
    shortcut: String,
    entry: TextExpanderUtils.ShortcutEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isRegex = shortcut.startsWith(TextExpanderUtils.REGEX_PREFIX)
    val cleanKey = if (isRegex) shortcut.substring(TextExpanderUtils.REGEX_PREFIX.length) else shortcut
    val rawShortcut = cleanKey.substring(entry.prefix.length)
    val displayShortcut = entry.prefix + rawShortcut

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = displayShortcut,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                    if (isRegex) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Regex",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = entry.template,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = if (entry.template.contains("%") || entry.template.contains("\t")) androidx.compose.ui.text.font.FontFamily.Monospace else null
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_bin),
                    contentDescription = stringResource(R.string.text_expander_delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}

fun createTextExpanderSettings(context: Context): List<helium314.keyboard.settings.Setting> = listOf(
    helium314.keyboard.settings.Setting(
        key = TextExpanderUtils.PREF_ENABLED,
        title = context.getString(R.string.text_expander_enable_title),
        description = context.getString(R.string.text_expander_enable_summary)
    ) { setting ->
        var isEnabled by remember { mutableStateOf(TextExpanderUtils.isEnabled(context)) }
        SwitchPreference(
            name = setting.title,
            key = setting.key,
            default = false,
            description = setting.description,
            onCheckedChange = {
                isEnabled = it
                context.prefs().edit { putBoolean(TextExpanderUtils.PREF_ENABLED, it) }
                TextExpanderUtils.clearCache()
            }
        )
    },
    helium314.keyboard.settings.Setting(
        key = TextExpanderUtils.PREF_IMMEDIATE,
        title = context.getString(R.string.text_expander_immediate_title),
        description = context.getString(R.string.text_expander_immediate_summary)
    ) { setting ->
        var isImmediate by remember { mutableStateOf(TextExpanderUtils.isImmediateEnabled(context)) }
        SwitchPreference(
            name = setting.title,
            key = setting.key,
            default = false,
            description = setting.description,
            onCheckedChange = {
                isImmediate = it
                context.prefs().edit { putBoolean(TextExpanderUtils.PREF_IMMEDIATE, it) }
            }
        )
    },
    helium314.keyboard.settings.Setting(
        key = TextExpanderUtils.PREF_BACKSPACE_REVERTS,
        title = context.getString(R.string.text_expander_backspace_undo_title),
        description = context.getString(R.string.text_expander_backspace_undo_summary)
    ) { setting ->
        var isReverts by remember { mutableStateOf(TextExpanderUtils.isBackspaceRevertsEnabled(context)) }
        SwitchPreference(
            name = setting.title,
            key = setting.key,
            default = false,
            description = setting.description,
            onCheckedChange = {
                isReverts = it
                context.prefs().edit { putBoolean(TextExpanderUtils.PREF_BACKSPACE_REVERTS, it) }
            }
        )
    },
    helium314.keyboard.settings.Setting(
        key = TextExpanderUtils.PREF_SUGGESTIONS,
        title = context.getString(R.string.text_expander_suggestions_title),
        description = context.getString(R.string.text_expander_suggestions_summary)
    ) { setting ->
        var isSuggestions by remember { mutableStateOf(TextExpanderUtils.isSuggestionsEnabled(context)) }
        SwitchPreference(
            name = setting.title,
            key = setting.key,
            default = true,
            description = setting.description,
            onCheckedChange = {
                isSuggestions = it
                context.prefs().edit { putBoolean(TextExpanderUtils.PREF_SUGGESTIONS, it) }
            }
        )
    },
    helium314.keyboard.settings.Setting(
        key = TextExpanderUtils.PREF_SUGGESTIONS_ONLY,
        title = context.getString(R.string.text_expander_suggestions_only_title),
        description = context.getString(R.string.text_expander_suggestions_only_summary)
    ) { setting ->
        var isSuggestionsOnly by remember { mutableStateOf(TextExpanderUtils.isSuggestionsOnly(context)) }
        SwitchPreference(
            name = setting.title,
            key = setting.key,
            default = false,
            description = setting.description,
            onCheckedChange = {
                isSuggestionsOnly = it
                context.prefs().edit { putBoolean(TextExpanderUtils.PREF_SUGGESTIONS_ONLY, it) }
            }
        )
    }
)
