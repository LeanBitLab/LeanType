/*
 * Copyright (C) 2021 The Android Open Source Project
 * parts taken from Material3 AlertDialog.kt
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.settings.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.previewDark

@Composable
fun ThreeButtonAlertDialogContent(
    onDismissRequest: () -> Unit,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
    scrollContent: Boolean = false,
    onNeutral: () -> Unit = { },
    checkOk: () -> Boolean = { true },
    confirmButtonText: String? = stringResource(android.R.string.ok),
    cancelButtonText: String? = null,
    neutralButtonText: String? = null,
) {
    PreferenceDialogContent(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        titleComposable = title,
        icon = icon,
        showCloseButton = true,
        scrollContent = scrollContent,
        content = content,
        buttons = {
            if (neutralButtonText != null || confirmButtonText != null || cancelButtonText != null) {
                if (neutralButtonText == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        if (cancelButtonText != null)
                            TextButton(onClick = onDismissRequest) {
                                Text(cancelButtonText, maxLines = 1, softWrap = false)
                            }
                        if (confirmButtonText != null)
                            TextButton(
                                enabled = checkOk(),
                                onClick = { onConfirmed(); onDismissRequest() },
                            ) {
                                Text(confirmButtonText, maxLines = 1, softWrap = false)
                            }
                    }
                } else {
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(onClick = onNeutral) {
                            Text(neutralButtonText, maxLines = 1, softWrap = false)
                        }
                        Row(
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            if (cancelButtonText != null)
                                TextButton(onClick = onDismissRequest) {
                                    Text(cancelButtonText, maxLines = 1, softWrap = false)
                                }
                            if (confirmButtonText != null)
                                TextButton(
                                    enabled = checkOk(),
                                    onClick = { onConfirmed(); onDismissRequest() },
                                ) {
                                    Text(confirmButtonText, maxLines = 1, softWrap = false)
                                }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun ThreeButtonAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
    scrollContent: Boolean = false,
    onNeutral: () -> Unit = { },
    checkOk: () -> Boolean = { true },
    confirmButtonText: String? = stringResource(android.R.string.ok),
    cancelButtonText: String? = null,
    neutralButtonText: String? = null,
    reducePadding: Boolean = false,
    properties: DialogProperties = DialogProperties()
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        ThreeButtonAlertDialogContent(
            onDismissRequest = onDismissRequest,
            onConfirmed = onConfirmed,
            modifier = modifier,
            icon = icon,
            title = title,
            content = content,
            scrollContent = scrollContent,
            onNeutral = onNeutral,
            checkOk = checkOk,
            confirmButtonText = confirmButtonText,
            cancelButtonText = cancelButtonText,
            neutralButtonText = neutralButtonText
        )
    }
}

@Preview
@Composable
private fun Preview() {
    Theme(previewDark) {
        ThreeButtonAlertDialog(
            onDismissRequest = {},
            onConfirmed = { },
            content = { Text("hello") },
            title = { Text("title") },
            neutralButtonText = "Default"
        )
    }
}
