// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import helium314.keyboard.latin.R

@Composable
fun PreferenceDialogContent(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleComposable: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    showCloseButton: Boolean = true,
    scrollContent: Boolean = false,
    buttons: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    Box(
        modifier = modifier.widthIn(min = 280.dp, max = 560.dp),
        propagateMinConstraints = true
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            contentColor = contentColorFor(MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                if (title != null || titleComposable != null || showCloseButton || icon != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            icon?.let {
                                Box(modifier = Modifier.padding(end = 12.dp)) {
                                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                                        it()
                                    }
                                }
                            }
                            if (titleComposable != null) {
                                CompositionLocalProvider(
                                    LocalTextStyle provides MaterialTheme.typography.titleLarge,
                                    LocalContentColor provides MaterialTheme.colorScheme.onSurface
                                ) {
                                    titleComposable()
                                }
                            } else if (title != null) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (showCloseButton) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = onDismissRequest,
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close_rounded),
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                content?.let {
                    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                        if (scrollContent) {
                            val scrollState = rememberScrollState()
                            Box(
                                Modifier
                                    .weight(weight = 1f, fill = false)
                                    .padding(bottom = if (buttons != null) 16.dp else 0.dp)
                                    .verticalScroll(scrollState)
                            ) {
                                it()
                            }
                        } else {
                            Box(
                                Modifier
                                    .weight(weight = 1f, fill = false)
                                    .padding(bottom = if (buttons != null) 16.dp else 0.dp)
                            ) {
                                it()
                            }
                        }
                    }
                }

                buttons?.let {
                    it()
                }
            }
        }
    }
}

@Composable
fun PreferenceDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleComposable: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    showCloseButton: Boolean = true,
    scrollContent: Boolean = false,
    properties: DialogProperties = DialogProperties(),
    buttons: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        PreferenceDialogContent(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            title = title,
            titleComposable = titleComposable,
            icon = icon,
            showCloseButton = showCloseButton,
            scrollContent = scrollContent,
            buttons = buttons,
            content = content
        )
    }
}
