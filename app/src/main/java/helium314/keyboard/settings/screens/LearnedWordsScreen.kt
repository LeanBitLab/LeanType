// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.LearnedWord
import helium314.keyboard.latin.R
import helium314.keyboard.latin.personalization.PersonalizationHelper
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** most used first, then alphabetical */
internal fun sortLearnedWords(words: List<LearnedWord>): List<LearnedWord> =
    words.sortedWith(compareByDescending<LearnedWord> { it.count }.thenBy { it.word.lowercase(it.locale) })

/** The learning history of the keyboard's current languages: view, search and delete single words (#948). */
@Composable
fun LearnedWordsScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    var words: List<LearnedWord>? by remember { mutableStateOf(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // the history is held by the running keyboard, reading it waits for its dictionary thread
        words = withContext(Dispatchers.IO) {
            sortLearnedWords(LatinIME.getInstance()?.getDictionaryFacilitator()?.getLearnedWords().orEmpty())
        }
    }
    val list = words
    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(if (list == null) stringResource(R.string.learned_words) else "${stringResource(R.string.learned_words)} (${list.size})") },
        filteredItems = { term -> list.orEmpty().filter { it.word.startsWith(term, ignoreCase = true) } },
        actions = {
            IconButton(onClick = { showClearDialog = true }) {
                Icon(painterResource(R.drawable.ic_clear_all), stringResource(R.string.clear_learned_words_title))
            }
        },
        content = if (list != null && list.isEmpty()) { {
            Text(
                stringResource(R.string.learned_words_empty),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } } else null,
        itemContent = { item ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.word, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            item.locale.getLocaleDisplayNameForUserDictSettings(ctx),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        LatinIME.getInstance()?.getDictionaryFacilitator()?.removeLearnedWord(item.word, item.locale)
                        words = words?.minus(item)
                    }) {
                        Icon(painterResource(R.drawable.ic_bin), stringResource(R.string.delete))
                    }
                }
            }
        }
    )
    if (showClearDialog) {
        ConfirmationDialog(
            onDismissRequest = { showClearDialog = false },
            onConfirmed = {
                showClearDialog = false
                PersonalizationHelper.clearUserHistory(ctx)
                words = emptyList()
                Toast.makeText(ctx, ctx.getString(R.string.clear_learned_words_success), Toast.LENGTH_SHORT).show()
            },
            title = { Text(stringResource(R.string.clear_learned_words_title)) },
            content = { Text(stringResource(R.string.clear_learned_words_details)) },
            confirmButtonText = stringResource(R.string.clear_all),
            cancelButtonText = stringResource(android.R.string.cancel),
        )
    }
}
