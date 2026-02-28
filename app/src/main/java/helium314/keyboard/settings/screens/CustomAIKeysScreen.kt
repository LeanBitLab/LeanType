/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.defaultToolbarPref
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.dialogs.TextInputDialog

@Composable
fun CustomAIKeysScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    
    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(stringResource(R.string.custom_ai_keys_title)) },
        filteredItems = { emptyList<Int>() },
        itemContent = { },
        content = {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.custom_ai_keys_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                (1..10).forEach { index ->
                    CustomAIKeySlot(index, context)
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    )
}

@Composable
private fun CustomAIKeySlot(index: Int, context: Context) {
    val prefs = context.prefs()
    val prefKey = "pref_custom_ai_prompt_$index"
    
    var currentPrompt by remember { mutableStateOf(prefs.getString(prefKey, "") ?: "") }
    val isSet = currentPrompt.isNotBlank()
    var showDialog by remember { mutableStateOf(false) }
    
    val keyEnum = CUSTOM_AI_KEY_ENUMS[index - 1]
    val iconRes = CUSTOM_AI_KEY_ICONS[index - 1]

    val cardModifier = Modifier.fillMaxWidth()
    val cardColors = if (isSet) {
        CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    } else {
        CardDefaults.outlinedCardColors()
    }

    val CardContent = @Composable {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Key $index",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isSet) currentPrompt else "Not configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            if (isSet) {
                androidx.compose.material3.IconButton(
                    onClick = { 
                        prefs.edit { remove(prefKey) }
                        currentPrompt = ""
                        updateToolbarKeyStatus(context, keyEnum, false)
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (isSet) {
        ElevatedCard(
            onClick = { showDialog = true },
            modifier = cardModifier,
            colors = cardColors
        ) { CardContent() }
    } else {
        OutlinedCard(
            onClick = { showDialog = true },
            modifier = cardModifier,
            colors = cardColors
        ) { CardContent() }
    }
    
    if (showDialog) {
        CustomAIKeyDialog(
            initialPrompt = currentPrompt,
            title = "Configure Key $index",
            iconRes = iconRes,
            onDismissRequest = { showDialog = false },
            onConfirmed = { newPrompt ->
                val trimmed = newPrompt.trim()
                prefs.edit { putString(prefKey, trimmed) }
                currentPrompt = trimmed
                updateToolbarKeyStatus(context, keyEnum, trimmed.isNotEmpty())
                showDialog = false
            }
        )
    }
}

@Composable
private fun CustomAIKeyDialog(
    initialPrompt: String,
    title: String,
    iconRes: Int,
    onDismissRequest: () -> Unit,
    onConfirmed: (String) -> Unit
) {
    var prompt by remember { mutableStateOf(initialPrompt) }
    
    val modes = listOf(
        "#editor" to "Edit text",
        "#proofread" to "Fix grammar",
        "#paraphrase" to "Rewrite",
        "#summarize" to "Summarize",
        "#expand" to "Expand",
        "#toneshift" to "Adjust tone",
        "#generate" to "Generate"
    )
    
    val modifiersList = listOf(
        "#outputonly" to "Result only",
        "#append" to "Append result",
        "#showthought" to "Show reasoning"
    )

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                // Modes Section
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Modes",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        modes.forEach { (keyword, label) ->
                            val isSelected = prompt.contains(keyword)
                            androidx.compose.material3.FilterChip(
                                selected = isSelected,
                                onClick = {
                                    prompt = if (isSelected) {
                                        prompt.replace(keyword, "").replace("  ", " ").trim()
                                    } else {
                                        if (prompt.isBlank()) keyword else "$prompt $keyword".trim()
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                // Modifiers Section
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Modifiers",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        modifiersList.forEach { (keyword, label) ->
                            val isSelected = prompt.contains(keyword)
                            androidx.compose.material3.FilterChip(
                                selected = isSelected,
                                onClick = {
                                    prompt = if (isSelected) {
                                        prompt.replace(keyword, "").replace("  ", " ").trim()
                                    } else {
                                        if (prompt.isBlank()) keyword else "$prompt $keyword".trim()
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                androidx.compose.material3.OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Custom Prompt") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirmed(prompt) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = onDismissRequest
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

private val CUSTOM_AI_KEY_ENUMS = arrayOf(
    ToolbarKey.CUSTOM_AI_1, ToolbarKey.CUSTOM_AI_2, ToolbarKey.CUSTOM_AI_3,
    ToolbarKey.CUSTOM_AI_4, ToolbarKey.CUSTOM_AI_5, ToolbarKey.CUSTOM_AI_6,
    ToolbarKey.CUSTOM_AI_7, ToolbarKey.CUSTOM_AI_8, ToolbarKey.CUSTOM_AI_9,
    ToolbarKey.CUSTOM_AI_10
)

private val CUSTOM_AI_KEY_ICONS = intArrayOf(
    R.drawable.ic_custom_ai_1, R.drawable.ic_custom_ai_2, R.drawable.ic_custom_ai_3,
    R.drawable.ic_custom_ai_4, R.drawable.ic_custom_ai_5, R.drawable.ic_custom_ai_6,
    R.drawable.ic_custom_ai_7, R.drawable.ic_custom_ai_8, R.drawable.ic_custom_ai_9,
    R.drawable.ic_custom_ai_10
)

private fun updateToolbarKeyStatus(context: Context, key: ToolbarKey, enable: Boolean) {
    val prefs = context.prefs()
    val toolbarKeys = prefs.getString(Settings.PREF_TOOLBAR_KEYS, defaultToolbarPref) ?: ""
    val entries = toolbarKeys.split(";").toMutableList()
    val keyEntryPrefix = "${key.name},"
    val existingIndex = entries.indexOfFirst { it.startsWith(keyEntryPrefix) }
    if (enable) {
        if (existingIndex != -1) entries[existingIndex] = "${key.name},true"
        else entries.add("${key.name},true")
    } else {
        if (existingIndex != -1) entries[existingIndex] = "${key.name},false"
    }
    prefs.edit { putString(Settings.PREF_TOOLBAR_KEYS, entries.joinToString(";")) }
}
