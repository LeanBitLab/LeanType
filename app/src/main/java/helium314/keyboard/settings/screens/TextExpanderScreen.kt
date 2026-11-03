/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.TextExpanderUtils
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
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

    var shortcutsMap by remember {
        mutableStateOf(TextExpanderUtils.getShortcuts(context))
    }

    var isGuideExpanded by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingPrefix by remember { mutableStateOf("") }
    var editingShortcut by remember { mutableStateOf("") }
    var editingTemplate by remember { mutableStateOf(TextFieldValue("")) }
    var originalShortcutToEdit by remember { mutableStateOf<String?>(null) }
    var editingIsRegex by remember { mutableStateOf(false) }
    var showTextEditor by remember { mutableStateOf(false) }

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
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
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
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Premium Collapsible Feature Guide Card
                    androidx.compose.material3.Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Clickable Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isGuideExpanded = !isGuideExpanded }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.text_expander_quick_guide),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_left),
                                    contentDescription = if (isGuideExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.rotate(if (isGuideExpanded) -90f else 180f)
                                )
                            }
                            
                            AnimatedVisibility(visible = isGuideExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.text_expander_how_it_works_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    
                                    // Step 1
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        StepBadge(num = "1")
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.text_expander_step1_title),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = stringResource(R.string.text_expander_step1_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Step 2
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        StepBadge(num = "2")
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.text_expander_step2_title),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = stringResource(R.string.text_expander_step2_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Step 3
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        StepBadge(num = "3")
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.text_expander_step3_title),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = stringResource(R.string.text_expander_step3_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = stringResource(R.string.text_expander_template_placeholders_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                PlaceholderChip(tag = "%date%", desc = stringResource(R.string.text_expander_tag_desc_date))
                                                PlaceholderChip(tag = "%date:yyyy/MM/dd%", desc = stringResource(R.string.text_expander_tag_desc_date_format))
                                                PlaceholderChip(tag = "%time%", desc = stringResource(R.string.text_expander_tag_desc_time))
                                                PlaceholderChip(tag = "%time12%", desc = stringResource(R.string.text_expander_tag_desc_time12))
                                                PlaceholderChip(tag = "%year%", desc = stringResource(R.string.text_expander_tag_desc_year))
                                                PlaceholderChip(tag = "%week%", desc = stringResource(R.string.text_expander_tag_desc_week))
                                                PlaceholderChip(tag = "%battery%", desc = stringResource(R.string.text_expander_tag_desc_battery))
                                                PlaceholderChip(tag = "%greeting%", desc = stringResource(R.string.text_expander_tag_desc_greeting))
                                                PlaceholderChip(tag = "%tomorrow%", desc = stringResource(R.string.text_expander_tag_desc_tomorrow))
                                            }
                                            Column(modifier = Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                PlaceholderChip(tag = "%clipboard%", desc = stringResource(R.string.text_expander_tag_desc_clipboard))
                                                PlaceholderChip(tag = "%clipboard:clean%", desc = stringResource(R.string.text_expander_tag_desc_clipboard_clean))
                                                PlaceholderChip(tag = "%clipboard:singleline%", desc = stringResource(R.string.text_expander_tag_desc_clipboard_singleline))
                                                PlaceholderChip(tag = "%clipboard:title%", desc = stringResource(R.string.text_expander_tag_desc_clipboard_title))
                                                PlaceholderChip(tag = "%clipboard:slug%", desc = stringResource(R.string.text_expander_tag_desc_clipboard_slug))
                                                PlaceholderChip(tag = "%day%", desc = stringResource(R.string.text_expander_tag_desc_day))
                                                PlaceholderChip(tag = "%month%", desc = stringResource(R.string.text_expander_tag_desc_month))
                                                PlaceholderChip(tag = "%language%", desc = stringResource(R.string.text_expander_tag_desc_language))
                                                PlaceholderChip(tag = "%cursor%", desc = stringResource(R.string.text_expander_tag_desc_cursor))
                                                PlaceholderChip(tag = "%bullets%", desc = stringResource(R.string.text_expander_tag_desc_bullets))
                                                PlaceholderChip(tag = "%list%", desc = stringResource(R.string.text_expander_tag_desc_list))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 1. Master Switch Toggle
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

                    // global prefix config removed

                    // 3. Section Title / Header for shortcuts
                    Text(
                        text = stringResource(R.string.text_expander_custom_shortcuts_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    androidx.compose.material3.OutlinedButton(
                        onClick = { showTextEditor = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(painterResource(R.drawable.ic_open_in_full), null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.text_expander_all_as_text))
                    }

                    // 4. List of saved shortcuts
                    if (shortcutsMap.isEmpty()) {
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 32.dp, horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_edit),
                                        contentDescription = "Edit",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
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
                                    textAlign = TextAlign.Center,
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.25f
                                )
                            }
                        }
                    } else {
                        shortcutsMap.forEach { (shortcut, entry) ->
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
        "%tab%", "%tabkey%", "%enter%"
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

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                    if (isRegex) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = entry.template,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    fontFamily = if (entry.template.contains("%")) androidx.compose.ui.text.font.FontFamily.Monospace else null
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bin),
                        contentDescription = "Delete shortcut",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_left),
                    contentDescription = "Edit shortcut",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.rotate(180f)
                )
            }
        }
    }
}

@Composable
private fun StepBadge(num: String) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(top = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = num,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PlaceholderChip(tag: String, desc: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                text = tag,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.9f
            )
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
    }
)
