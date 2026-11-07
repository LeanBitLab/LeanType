// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.style.StyleCategory
import helium314.keyboard.latin.style.StyleDefinition
import helium314.keyboard.latin.style.UnicodeStyleEngine
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory

@Composable
fun StyleSettingsScreen(
    onClickBack: () -> Unit
) {
    val context = LocalContext.current
    var previewText by remember { mutableStateOf("LeanType Stylish Font") }
    var activeLiveStyleId by remember { mutableStateOf(UnicodeStyleEngine.activeLiveStyleId) }

    val styles = remember { UnicodeStyleEngine.getAllStyles() }
    val activeStyle = styles.firstOrNull { it.id == activeLiveStyleId }

    fun updateLiveStyle(styleId: String?) {
        if (styleId == null) {
            UnicodeStyleEngine.clearActiveLiveStyle()
            activeLiveStyleId = null
            Toast.makeText(context, context.getString(R.string.style_active_none), Toast.LENGTH_SHORT).show()
        } else {
            UnicodeStyleEngine.activeLiveStyleId = styleId
            activeLiveStyleId = styleId
            val name = UnicodeStyleEngine.getStyle(styleId)?.name ?: styleId
            Toast.makeText(context, context.getString(R.string.style_active_indicator, name), Toast.LENGTH_SHORT).show()
        }
        KeyboardSwitcher.getInstance().suggestionStripView?.updateToolbarButtonsActivatedState()
    }

    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.style_hub_title),
        settings = emptyList(),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            // Section 1: Live Typing State
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    PreferenceCategory(stringResource(R.string.style_live_typing))
                    Text(
                        text = stringResource(R.string.style_live_typing_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (activeStyle != null) {
                                    stringResource(R.string.style_active_indicator, activeStyle.name)
                                } else {
                                    stringResource(R.string.style_active_none)
                                },
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (activeStyle != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (activeStyle != null) {
                                Text(
                                    text = UnicodeStyleEngine.transformText("Sample live typing preview", activeStyle.id),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            }
                        }
                        if (activeStyle != null) {
                            Button(
                                onClick = { updateLiveStyle(null) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Text(stringResource(R.string.style_revert_plain))
                            }
                        }
                    }
                }
            }

            // Section 2: Accessibility Notice
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_about),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.style_accessibility_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Section 3: Interactive Style Tester
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    PreferenceCategory("Interactive Style Tester")

                    OutlinedTextField(
                        value = previewText,
                        onValueChange = { previewText = it },
                        label = { Text(stringResource(R.string.style_preview_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(16.dp))

                    val effectiveText = if (previewText.isBlank()) "Sample" else previewText

                    styles.forEach { style ->
                        StylePreviewItem(
                            style = style,
                            targetText = effectiveText,
                            isActiveLive = activeLiveStyleId == style.id,
                            onToggleLive = {
                                if (activeLiveStyleId == style.id) {
                                    updateLiveStyle(null)
                                } else {
                                    updateLiveStyle(style.id)
                                }
                            },
                            onCopy = { transformed ->
                                copyToClipboard(transformed, style.name)
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // Section 4: Toolbar Shortcut Guidance
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    PreferenceCategory("Quick Toolbar Shortcuts")
                    Text(
                        text = "• Tap the Font Style toolbar key to style current word or selection via candidate strip.\n" +
                               "• Long press the Font Style key to open categorized Unicode symbols (Math, Greek, Arrows, Box drawing).\n" +
                               "• Use the Plain chip in the candidate strip anytime to convert styled text back to normal ASCII.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StylePreviewItem(
    style: StyleDefinition,
    targetText: String,
    isActiveLive: Boolean,
    onToggleLive: () -> Unit,
    onCopy: (String) -> Unit
) {
    val transformed = remember(style, targetText) {
        UnicodeStyleEngine.transformText(targetText, style.id)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isActiveLive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = style.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = style.category.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = transformed,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = { onCopy(transformed) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.sym_keyboard_copy),
                    contentDescription = "Copy styled text",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (style.supportsLiveTyping) {
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = onToggleLive,
                    colors = if (isActiveLive) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    }
                ) {
                    Text(
                        text = if (isActiveLive) "Active" else "Live",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
