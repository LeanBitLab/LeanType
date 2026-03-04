/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.ScrollState
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.defaultToolbarPref
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.dialogs.TextInputDialog

@Composable
fun CustomAIKeysScreen(onClickBack: () -> Unit, onNavigateToConfig: (Int) -> Unit) {
    val context = LocalContext.current
    
    SearchScreen(
        onClickBack = onClickBack,
        title = { 
            Text(
                text = stringResource(R.string.custom_ai_keys_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            ) 
        },
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

                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "What are Keywords?",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Keywords (like #proofread or #summarize) act as quick instructions for the AI. Select them when configuring a key to define its behavior. You must also add your own custom prompt to create the key.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                (1..10).forEach { index ->
                    CustomAIKeySlot(index, context, onNavigateToConfig)
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    )
}

@Composable
private fun CustomAIKeySlot(index: Int, context: Context, onNavigateToConfig: (Int) -> Unit) {
    val prefs = context.prefs()
    val prefKey = "pref_custom_ai_prompt_$index"
    
    var currentPrompt by remember { mutableStateOf(prefs.getString(prefKey, "") ?: "") }
    val isSet = currentPrompt.isNotBlank()
    
    val keyEnum = CUSTOM_AI_KEY_ENUMS[index - 1]
    val iconRes = CUSTOM_AI_KEY_ICONS[index - 1]

    val cardModifier = Modifier.fillMaxWidth()
    val cardColors = if (isSet) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    }

    val CardContent = @Composable {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = if (isSet) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (isSet) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Key $index",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val displayPrompt = if (isSet) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    buildAnnotatedString {
                        val words = currentPrompt.split(" ")
                        words.forEachIndexed { i, word ->
                            if (word.startsWith("#") && word.length > 1) {
                                pushStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold))
                                append(word)
                                pop()
                            } else {
                                append(word)
                            }
                            if (i < words.size - 1) append(" ")
                        }
                    }
                } else {
                    androidx.compose.ui.text.AnnotatedString("Not configured")
                }

                Text(
                    text = displayPrompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSet) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    androidx.compose.material3.Card(
        onClick = { onNavigateToConfig(index) },
        modifier = cardModifier,
        colors = cardColors
    ) { CardContent() }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ConfigCustomAIKeyScreen(
    index: Int,
    onClickBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.prefs()
    val prefKey = "pref_custom_ai_prompt_$index"
    val initialPrompt = prefs.getString(prefKey, "") ?: ""
    val keyEnum = CUSTOM_AI_KEY_ENUMS[index - 1]
    
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

    val allKeywords = (modes.map { it.first } + modifiersList.map { it.first })
    
    // Split initial prompt into custom text and keywords
    val initialKeywords = initialPrompt.split(" ").filter { it in allKeywords }.toSet()
    val initialCustomText = initialPrompt.split(" ").filter { it !in allKeywords }.joinToString(" ")
    
    var customText by remember { mutableStateOf(initialCustomText) }
    var selectedKeywords by remember { mutableStateOf(initialKeywords) }


    val keywordDescriptions = mapOf(
        "#editor" to "Free-form editing and formatting of text according to prompt.",
        "#proofread" to "Corrects grammar, spelling, and punctuation.",
        "#paraphrase" to "Rewrites text while maintaining original meaning.",
        "#summarize" to "Condenses text to its most important points.",
        "#expand" to "Elaborates on the text by adding more details.",
        "#toneshift" to "Changes the tone (e.g., professional, casual).",
        "#generate" to "Generates new text completely ignoring selected text.",
        "#outputonly" to "Returns only the modified text, without conversational fillers.",
        "#append" to "Appends the result to the end of the original text.",
        "#showthought" to "Includes AI's reasoning process along with result."
    )

    // Prevents nested scrollable areas from bubbling leftover scroll events to the parent screen
    val consumeUnconsumedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return available // Consume remaining scroll delta so parent doesn't scroll
            }
        }
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Configure Key $index") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(
                        onClick = {
                            val finalPrompt = buildString {
                                if (customText.isNotBlank()) append(customText.trim())
                                if (customText.isNotBlank() && selectedKeywords.isNotEmpty()) append(" ")
                                append(selectedKeywords.joinToString(" "))
                            }
                            prefs.edit { putString(prefKey, finalPrompt) }
                            updateToolbarKeyStatus(context, keyEnum, finalPrompt.isNotEmpty())
                            onClickBack()
                        }
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(android.R.string.cancel))
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            androidx.compose.material3.Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (initialPrompt.isNotBlank()) {
                        Button(
                            onClick = {
                                prefs.edit { remove(prefKey) }
                                updateToolbarKeyStatus(context, keyEnum, false)
                                onClickBack()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text("Delete")
                        }
                    }
                    Button(
                        onClick = {
                            val finalPrompt = buildString {
                                if (customText.isNotBlank()) append(customText.trim())
                                if (customText.isNotBlank() && selectedKeywords.isNotEmpty()) append(" ")
                                append(selectedKeywords.joinToString(" "))
                            }
                            prefs.edit { putString(prefKey, finalPrompt) }
                            updateToolbarKeyStatus(context, keyEnum, finalPrompt.isNotEmpty())
                            onClickBack()
                        },
                        enabled = customText.isNotBlank() && selectedKeywords.isNotEmpty(),
                        modifier = if (initialPrompt.isNotBlank()) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Select keywords to define behavior, and combine them with your custom instructions.\n\n" +
                       "Example: Choose #editor and type \"Translate to French\" to create a specialized translation key.\n\n" +
                       "Note: You must provide both a custom prompt and select at least one keyword to save.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Custom prompt input
            androidx.compose.material3.OutlinedTextField(
                value = customText,
                onValueChange = { customText = it },
                label = { Text("Custom Prompt for Key $index") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .nestedScroll(consumeUnconsumedScrollConnection),
                shape = RoundedCornerShape(16.dp)
            )

            // All keywords in a single FlowRow
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                (modes + modifiersList).forEach { (keyword, label) ->
                    val isSelected = selectedKeywords.contains(keyword)
                    androidx.compose.material3.FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedKeywords = if (isSelected) {
                                selectedKeywords - keyword
                            } else {
                                selectedKeywords + keyword
                            }
                        },
                        label = { Text(label) },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }

            // Keyword descriptions
            if (selectedKeywords.isNotEmpty()) {
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    val keywordScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .nestedScroll(consumeUnconsumedScrollConnection)
                            .verticalScroll(keywordScrollState)
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Selected Keyword Functions:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        selectedKeywords.forEach { keyword ->
                            val descriptionText = buildAnnotatedString {
                                pushStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))
                                append(keyword)
                                pop()
                                append(": ${keywordDescriptions[keyword]}")
                            }
                            Text(
                                text = descriptionText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

        }
    }
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

private class KeywordVisualTransformation(private val color: androidx.compose.ui.graphics.Color) : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val annotatedString = buildAnnotatedString {
            val textStr = text.text
            val regex = Regex("(#[a-zA-Z0-9_]+)")
            var lastIndex = 0

            regex.findAll(textStr).forEach { matchResult ->
                // Append text before hashtag
                append(textStr.substring(lastIndex, matchResult.range.first))
                // Apply style to hashtag
                pushStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold))
                append(matchResult.value)
                pop()
                lastIndex = matchResult.range.last + 1
            }
            // Append remaining text
            if (lastIndex < textStr.length) {
                append(textStr.substring(lastIndex))
            }
        }
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}


