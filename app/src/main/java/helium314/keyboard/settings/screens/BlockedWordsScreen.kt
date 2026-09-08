// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.settings.DropDownField
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import java.io.File
import java.io.IOException
import java.util.Locale

private data class BlockedWord(val word: String, val locale: Locale)

private fun getBlacklistFile(context: Context, locale: Locale): File {
    val dir = File(DeviceProtectedUtils.getFilesDir(context), "blacklists")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "${locale.toLanguageTag()}.txt")
}

private fun loadBlockedWords(context: Context): List<BlockedWord> {
    val dir = File(DeviceProtectedUtils.getFilesDir(context), "blacklists")
    if (!dir.exists() || !dir.isDirectory) return emptyList()
    val list = mutableListOf<BlockedWord>()
    dir.listFiles()?.forEach { file ->
        if (file.isFile && file.name.endsWith(".txt")) {
            val localeTag = file.name.substringBefore(".txt")
            val locale = Locale.forLanguageTag(localeTag)
            try {
                file.readLines().forEach { line ->
                    val trimmed = line.trim().lowercase(locale)
                    if (trimmed.isNotEmpty()) {
                        list.add(BlockedWord(trimmed, locale))
                    }
                }
            } catch (e: Exception) {
                Log.e("BlockedWords", "Error reading blacklist file $file", e)
            }
        }
    }
    val uniqueList = list.distinct()
    return uniqueList.sortedWith(compareBy({ it.word.lowercase(it.locale) }, { it.locale.toLanguageTag() }))
}

private fun addBlockedWord(context: Context, word: String, locale: Locale) {
    val file = getBlacklistFile(context, locale)
    val lowercaseWord = word.trim().lowercase(locale)
    try {
        val existing = if (file.exists()) file.readLines().map { it.trim().lowercase(locale) }.filter { it.isNotEmpty() }.toMutableSet() else mutableSetOf()
        if (existing.add(lowercaseWord)) {
            file.writeText(existing.joinToString("\n") + "\n")
        }
    } catch (e: Exception) {
        Log.e("BlockedWords", "Error adding word to blacklist", e)
    }
}

private fun removeBlockedWord(context: Context, word: String, locale: Locale) {
    val file = getBlacklistFile(context, locale)
    val lowercaseWord = word.trim().lowercase(locale)
    try {
        if (file.exists()) {
            val existing = file.readLines().map { it.trim().lowercase(locale) }.filter { it.isNotEmpty() }.toMutableSet()
            if (existing.remove(lowercaseWord)) {
                if (existing.isEmpty()) {
                    file.delete()
                } else {
                    file.writeText(existing.joinToString("\n") + "\n")
                }
            }
        }
    } catch (e: Exception) {
        Log.e("BlockedWords", "Error removing word from blacklist", e)
    }
}

private fun notifyKeyboardToReload() {
    KeyboardSwitcher.getInstance().latinIME?.reloadBlacklist()
}

@Composable
fun BlockedWordsScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    var refreshTrigger by remember { mutableStateOf(0) }
    val blockedWords = remember(refreshTrigger) { loadBlockedWords(ctx) }
    var selectedWord: BlockedWord? by remember { mutableStateOf(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        SearchScreen(
            onClickBack = onClickBack,
            title = {
                // ponytail: show total count of blocked words in title
                Text("${stringResource(R.string.edit_blocked_words)} (${blockedWords.size})")
            },
            menu = listOf(
                stringResource(R.string.clear_all) to { showClearAllDialog = true }
            ),
            filteredItems = { term ->
                blockedWords.filter {
                    val termLower = term.lowercase(it.locale)
                    val wordLower = it.word.lowercase(it.locale)
                    wordLower.startsWith(termLower)
                }
            },
            itemContent = { item ->
                androidx.compose.material3.Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedWord = item }
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.word, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                item.locale.getLocaleDisplayNameForUserDictSettings(ctx),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.IconButton(
                            onClick = {
                                removeBlockedWord(ctx, item.word, item.locale)
                                notifyKeyboardToReload()
                                refreshTrigger++
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_bin),
                                contentDescription = stringResource(R.string.delete)
                            )
                        }
                    }
                }
            }
        )
        ExtendedFloatingActionButton(
            onClick = { selectedWord = BlockedWord("", getSortedDictionaryLocales().firstOrNull() ?: Locale.getDefault()) },
            text = { Text(stringResource(R.string.add_blocked_word)) },
            icon = { Icon(painter = painterResource(R.drawable.ic_plus), stringResource(R.string.add_blocked_word)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(all = 12.dp)
                .then(Modifier.safeDrawingPadding())
        )
    }

    val currentSelectedWord = selectedWord
    if (currentSelectedWord != null) {
        EditBlockedWordDialog(currentSelectedWord, onDismissRequest = {
            selectedWord = null
            refreshTrigger++
        })
    }

    if (showClearAllDialog) {
        ConfirmationDialog(
            onDismissRequest = { showClearAllDialog = false },
            onConfirmed = {
                showClearAllDialog = false
                val dir = File(DeviceProtectedUtils.getFilesDir(ctx), "blacklists")
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { it.delete() }
                }
                notifyKeyboardToReload()
                refreshTrigger++
            },
            content = { Text(stringResource(R.string.clear_all_blocked_words_confirmation)) }
        )
    }
}

@Composable
private fun EditBlockedWordDialog(
    blockedWord: BlockedWord,
    onDismissRequest: () -> Unit
) {
    val ctx = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var wordText by remember { mutableStateOf(blockedWord.word) }
    var wordLocale by remember { mutableStateOf(blockedWord.locale) }

    val localesList = remember { getSortedDictionaryLocales().toList() }

    val alreadyExists = remember(wordText, wordLocale) {
        if (wordText.isBlank()) false
        else {
            val file = File(DeviceProtectedUtils.getFilesDir(ctx), "blacklists/${wordLocale.toLanguageTag()}.txt")
            if (file.exists()) {
                val cleanLower = wordText.trim().lowercase(wordLocale)
                file.readLines().map { it.trim().lowercase(wordLocale) }.contains(cleanLower)
            } else false
        }
    }

    val isNew = blockedWord.word.isEmpty()
    val isSaveEnabled = wordText.isNotBlank() && (!alreadyExists || (!isNew && wordText.trim().lowercase(wordLocale) == blockedWord.word.lowercase(blockedWord.locale) && wordLocale == blockedWord.locale))

    fun save() {
        if (wordText.isNotBlank()) {
            val cleanWord = wordText.trim()
            if (!isNew && (blockedWord.word.lowercase(blockedWord.locale) != cleanWord.lowercase(wordLocale) || blockedWord.locale != wordLocale)) {
                removeBlockedWord(ctx, blockedWord.word, blockedWord.locale)
            }
            addBlockedWord(ctx, cleanWord, wordLocale)
            notifyKeyboardToReload()
        }
    }

    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = {
            save()
            onDismissRequest()
        },
        checkOk = { isSaveEnabled },
        confirmButtonText = stringResource(R.string.save),
        neutralButtonText = if (isNew) null else stringResource(R.string.delete),
        onNeutral = {
            removeBlockedWord(ctx, blockedWord.word, blockedWord.locale)
            notifyKeyboardToReload()
            onDismissRequest()
        },
        title = {
            Text(if (isNew) stringResource(R.string.add_blocked_word) else stringResource(R.string.edit_blocked_words))
        },
        content = {
            LaunchedEffect(blockedWord) {
                if (isNew) {
                    focusRequester.requestFocus()
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = wordText,
                    onValueChange = { wordText = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    label = { Text("Word") },
                    keyboardActions = KeyboardActions {
                        if (isSaveEnabled) {
                            save()
                            onDismissRequest()
                        }
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.user_dict_settings_add_locale_option_name), Modifier.fillMaxWidth(0.3f))
                    DropDownField(
                        items = localesList,
                        selectedItem = wordLocale,
                        onSelected = { wordLocale = it },
                    ) {
                        Text(it.getLocaleDisplayNameForUserDictSettings(ctx))
                    }
                }
                if (alreadyExists && (isNew || wordText != blockedWord.word || wordLocale != blockedWord.locale)) {
                    Text(
                        stringResource(R.string.blocked_word_already_present),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}
