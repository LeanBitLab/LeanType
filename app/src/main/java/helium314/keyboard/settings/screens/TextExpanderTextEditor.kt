// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.TextExpanderText
import helium314.keyboard.latin.utils.TextExpanderUtils
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.launch

/** previous state of the text expander data, kept when saving in the text editor, for restoring it */
private const val PREF_DATA_PREVIOUS = "pref_text_expander_data_previous"
private const val MAX_FILE_SIZE = 2 * 1024 * 1024

/**
 * All text expander shortcuts as one text, see [TextExpanderText].
 * [onClose] gets whether the shortcuts were changed.
 */
@Composable
fun TextExpanderTextEditor(onClose: (Boolean) -> Unit) {
    val context = LocalContext.current
    val prefs = context.prefs()
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    var current by remember { mutableStateOf(TextExpanderUtils.getShortcuts(context)) }
    var original by remember { mutableStateOf(TextExpanderText.format(current)) }
    var value by remember { mutableStateOf(TextFieldValue(original)) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var changedSomething by remember { mutableStateOf(false) }
    var hasPrevious by remember { mutableStateOf(prefs.contains(PREF_DATA_PREVIOUS)) }

    var errors by remember { mutableStateOf<List<TextExpanderText.Error>?>(null) }
    var pending by remember { mutableStateOf<Pair<TextExpanderText.Result, TextExpanderText.Diff>?>(null) }
    var askDiscard by remember { mutableStateOf(false) }
    var askRestore by remember { mutableStateOf(false) }

    fun close() { if (value.text != original) askDiscard = true else onClose(changedSomething) }
    fun replaceText(text: String) { value = TextFieldValue(text.replace("\r\n", "\n")) }
    fun reload() {
        current = TextExpanderUtils.getShortcuts(context)
        original = TextExpanderText.format(current)
        value = TextFieldValue(original)
        hasPrevious = prefs.contains(PREF_DATA_PREVIOUS)
    }

    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(value.text.toByteArray()) } }
            .onFailure { toast(context, R.string.text_expander_file_error) }
    }
    val loadFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readNBytesCompat(MAX_FILE_SIZE + 1)
                require(bytes.size <= MAX_FILE_SIZE)
                replaceText(String(bytes))
            }
        }.onFailure { toast(context, R.string.text_expander_file_error) }
    }

    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                // title bar
                Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::close) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(android.R.string.cancel))
                    }
                    Text(
                        stringResource(R.string.text_expander_all_title), style = MaterialTheme.typography.titleLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        val result = TextExpanderText.parse(value.text, current)
                        if (result.errors.isNotEmpty()) errors = result.errors
                        else {
                            val diff = TextExpanderText.diff(current, result.entries)
                            if (diff.isEmpty) { toast(context, R.string.text_expander_no_changes); original = value.text }
                            else pending = result to diff
                        }
                    }) { Text(stringResource(R.string.text_expander_save)) }
                }
                // actions
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("LeanType", value.text))
                        toast(context, R.string.text_expander_copied)
                    }) { Text(stringResource(R.string.text_expander_copy_all)) }
                    OutlinedButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                        if (text.isNullOrEmpty()) toast(context, R.string.text_expander_clipboard_empty) else replaceText(text)
                    }) { Text(stringResource(R.string.text_expander_paste_all)) }
                    OutlinedButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, value.text)
                            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.text_expander_all_title))
                        context.startActivity(Intent.createChooser(intent, null))
                    }) { Text(stringResource(R.string.text_expander_share)) }
                    OutlinedButton(onClick = { saveFile.launch(context.getString(R.string.text_expander_file_name)) }) {
                        Text(stringResource(R.string.text_expander_save_file))
                    }
                    OutlinedButton(onClick = { loadFile.launch(arrayOf("text/*")) }) { Text(stringResource(R.string.text_expander_load_file)) }
                    OutlinedButton(onClick = { askRestore = true }, enabled = hasPrevious) {
                        Text(stringResource(R.string.text_expander_restore_previous))
                    }
                }
                // jump bar
                val headers = remember(value.text) { TextExpanderText.headers(value.text) }
                if (headers.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        headers.forEach { (name, offset) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable {
                                        val l = layout ?: return@clickable
                                        val line = l.getLineForOffset(offset.coerceAtMost(value.text.length))
                                        val lineEnd = value.text.indexOf('\n', offset).let { if (it < 0) value.text.length else it + 1 }
                                        value = value.copy(selection = TextRange(lineEnd))
                                        scope.launch { scroll.animateScrollTo(l.getLineTop(line).toInt()) }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(name.ifEmpty { "?" }, style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
                // the text
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp)) {
                        val headerStyle = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        BasicTextField(
                            value = value,
                            onValueChange = { value = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            visualTransformation = remember(headerStyle) { HeaderHighlight(headerStyle) },
                            onTextLayout = { layout = it },
                        )
                    }
                }
                Text(
                    stringResource(R.string.text_expander_all_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                    PlaceholderTagsRow(value) { value = it }
                }
            }
        }
    }

    errors?.let { list ->
        AlertDialog(
            onDismissRequest = { errors = null },
            confirmButton = { TextButton(onClick = { errors = null }) { Text(stringResource(android.R.string.ok)) } },
            title = { Text(stringResource(R.string.text_expander_errors_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.forEach { Text(errorText(it)) }
                }
            }
        )
    }
    pending?.let { (result, diff) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit { putString(PREF_DATA_PREVIOUS, prefs.getString(TextExpanderUtils.PREF_DATA, null) ?: "{}") }
                    TextExpanderUtils.saveShortcuts(context, result.entries)
                    changedSomething = true
                    pending = null
                    reload()
                    toast(context, R.string.text_expander_saved)
                }) { Text(stringResource(R.string.text_expander_save)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(android.R.string.cancel)) } },
            title = { Text(stringResource(R.string.text_expander_save_question)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.text_expander_changes, diff.added.size, diff.changed.size, diff.removed.size))
                    if (diff.removed.isNotEmpty())
                        Text(stringResource(R.string.text_expander_changes_deleted, diff.removed.joinToString(", ")),
                            color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
    if (askRestore) {
        AlertDialog(
            onDismissRequest = { askRestore = false },
            confirmButton = {
                TextButton(onClick = {
                    val now = prefs.getString(TextExpanderUtils.PREF_DATA, null) ?: "{}"
                    val previous = prefs.getString(PREF_DATA_PREVIOUS, null) ?: "{}"
                    prefs.edit { putString(TextExpanderUtils.PREF_DATA, previous); putString(PREF_DATA_PREVIOUS, now) }
                    changedSomething = true
                    askRestore = false
                    reload()
                }) { Text(stringResource(R.string.text_expander_restore_previous)) }
            },
            dismissButton = { TextButton(onClick = { askRestore = false }) { Text(stringResource(android.R.string.cancel)) } },
            text = { Text(stringResource(R.string.text_expander_restore_previous_confirm)) }
        )
    }
    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            confirmButton = {
                TextButton(onClick = { askDiscard = false; onClose(changedSomething) }) { Text(stringResource(R.string.text_expander_discard)) }
            },
            dismissButton = { TextButton(onClick = { askDiscard = false }) { Text(stringResource(android.R.string.cancel)) } },
            text = { Text(stringResource(R.string.text_expander_discard_title)) }
        )
    }
}

@Composable
private fun errorText(error: TextExpanderText.Error): String = when (error.kind) {
    TextExpanderText.ErrorKind.TEXT_BEFORE_FIRST -> stringResource(R.string.text_expander_error_text_before, error.line)
    TextExpanderText.ErrorKind.EMPTY_SHORTCUT -> stringResource(R.string.text_expander_error_empty_shortcut, error.line)
    TextExpanderText.ErrorKind.SPACE_IN_SHORTCUT -> stringResource(R.string.text_expander_error_space, error.line, error.name)
    TextExpanderText.ErrorKind.EMPTY_TEXT -> stringResource(R.string.text_expander_error_empty_text, error.line, error.name)
    TextExpanderText.ErrorKind.DUPLICATE -> stringResource(R.string.text_expander_error_duplicate, error.line, error.name)
    TextExpanderText.ErrorKind.INVALID_REGEX -> stringResource(R.string.text_expander_error_regex, error.line, error.name)
}

/** Shows the "=== shortcut ===" lines highlighted, the text itself is unchanged */
private class HeaderHighlight(private val style: SpanStyle) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text)
            TextExpanderText.headers(text.text).forEach { (_, offset) ->
                val end = text.text.indexOf('\n', offset).let { if (it < 0) text.length else it }
                addStyle(style, offset, end)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

private fun toast(context: Context, res: Int) = Toast.makeText(context, res, Toast.LENGTH_SHORT).show()

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
