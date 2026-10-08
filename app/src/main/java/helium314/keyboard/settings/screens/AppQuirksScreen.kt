// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import helium314.keyboard.compat.AppQuirk
import helium314.keyboard.compat.AppQuirksManager
import helium314.keyboard.compat.BrowserDetector
import helium314.keyboard.latin.R
import helium314.keyboard.settings.DropDownField
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap? = null
)

private data class BadgeInfo(
    val text: String,
    val containerColor: Color,
    val contentColor: Color
)

private data class ActionOption(
    val label: String,
    val action: Int?
)

@Composable
fun AppQuirksScreen(
    onClickBack: () -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var filterConfiguredOnly by remember { mutableStateOf(false) }
    var editingApp by remember { mutableStateOf<AppEntry?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PackageManager.MATCH_ALL
            } else {
                0
            }
            val resolveInfos = pm.queryIntentActivities(intent, flags)
            val appList = mutableListOf<AppEntry>()
            val visited = mutableSetOf<String>()
            val iconSizePx = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)

            for (info in resolveInfos) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (!visited.add(pkg)) continue
                val label = try {
                    info.loadLabel(pm).toString()
                } catch (_: Exception) {
                    pkg
                }
                val icon = try {
                    info.loadIcon(pm)?.toBitmap(iconSizePx, iconSizePx)?.asImageBitmap()
                } catch (_: Exception) {
                    null
                }
                appList.add(AppEntry(packageName = pkg, label = if (label.isNotBlank()) label else pkg, icon = icon))
            }

            // Include configured packages or defaults that might not have launcher intents
            val extraPackages = AppQuirksManager.getAllUserQuirks().keys +
                    listOf("com.google.android.apps.nexuslauncher", "md.obsidian")
            for (pkg in extraPackages) {
                if (visited.add(pkg)) {
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        val label = pm.getApplicationLabel(appInfo).toString()
                        val icon = appInfo.loadIcon(pm)?.toBitmap(iconSizePx, iconSizePx)?.asImageBitmap()
                        appList.add(AppEntry(packageName = pkg, label = if (label.isNotBlank()) label else pkg, icon = icon))
                    } catch (_: Exception) {
                        appList.add(AppEntry(packageName = pkg, label = pkg, icon = null))
                    }
                }
            }

            appList.sortBy { it.label.lowercase() }
            apps = appList
            isLoading = false
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SearchScreen(
            onClickBack = onClickBack,
            title = {
                Text(stringResource(R.string.app_quirks_title))
            },
            menu = listOf(
                (if (filterConfiguredOnly) "Show All Apps" else stringResource(R.string.app_quirks_filter_configured)) to {
                    filterConfiguredOnly = !filterConfiguredOnly
                }
            ),
            filteredItems = { query ->
                val q = query.trim().lowercase()
                apps.filter { app ->
                    val matchesQuery = q.isEmpty() ||
                            app.label.lowercase().contains(q) ||
                            app.packageName.lowercase().contains(q)

                    if (!matchesQuery) return@filter false

                    if (filterConfiguredOnly) {
                        val effective = AppQuirksManager.getEffectiveQuirk(app.packageName)
                        val isBrowser = BrowserDetector.isWebBrowser(app.packageName)
                        (effective != null && effective.hasCustomSettings()) || isBrowser
                    } else {
                        true
                    }
                }
            },
            itemContent = { app ->
                // Read refreshTrigger to ensure recomposition on dialog save
                if (refreshTrigger < 0) return@SearchScreen
                val effective = AppQuirksManager.getEffectiveQuirk(app.packageName)
                val userQuirk = AppQuirksManager.getUserQuirk(app.packageName)
                val isBrowser = BrowserDetector.isWebBrowser(app.packageName)

                val badges = mutableListOf<BadgeInfo>()
                if (effective?.forceWebEditor == true || isBrowser) {
                    badges.add(BadgeInfo(
                        stringResource(R.string.app_quirks_badge_web),
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer
                    ))
                }
                if (effective?.forceIncognito == true) {
                    badges.add(BadgeInfo(
                        stringResource(R.string.app_quirks_badge_incognito),
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer
                    ))
                }
                if (effective?.forceNonIncognito == true) {
                    badges.add(BadgeInfo(
                        stringResource(R.string.app_quirks_badge_non_incognito),
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer
                    ))
                }
                if (effective?.forceDirectCommit == true) {
                    badges.add(BadgeInfo(
                        stringResource(R.string.app_quirks_badge_direct_commit),
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer
                    ))
                }
                if (effective?.allowSymbolComposing == true) {
                    badges.add(BadgeInfo(
                        stringResource(R.string.app_quirks_badge_symbol_composing),
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer
                    ))
                }
                if (effective?.disableAutoSpace == true) {
                    badges.add(BadgeInfo(
                        stringResource(R.string.app_quirks_badge_no_auto_space),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                }
                if (effective?.allowTypeNullKeyboard == true) {
                    badges.add(BadgeInfo(
                        stringResource(R.string.app_quirks_badge_allow_type_null),
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer
                    ))
                }
                if (effective?.autoCorrectionMode == AppQuirksManager.AUTOCORRECT_FORCE_ENABLE) {
                    badges.add(BadgeInfo(
                        stringResource(R.string.app_quirks_badge_autocorrect_on),
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer
                    ))
                } else if (effective?.autoCorrectionMode == AppQuirksManager.AUTOCORRECT_FORCE_DISABLE) {
                    badges.add(BadgeInfo(
                        stringResource(R.string.app_quirks_badge_autocorrect_off),
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer
                    ))
                }
                if (effective?.stripNoEnterAction == true) {
                    badges.add(BadgeInfo(
                        stringResource(R.string.app_quirks_badge_no_enter),
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer
                    ))
                }
                if (effective?.alwaysShowSuggestions == true) {
                    badges.add(BadgeInfo(
                        stringResource(R.string.app_quirks_badge_always_suggestions),
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer
                    ))
                }
                if (effective?.hideSuggestionStrip == true) {
                    badges.add(BadgeInfo(
                        stringResource(R.string.app_quirks_badge_no_suggestions),
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer
                    ))
                }
                if (effective?.hideToolbar == true) {
                    badges.add(BadgeInfo(
                        stringResource(R.string.app_quirks_badge_no_toolbar),
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer
                    ))
                }
                if (effective?.useSelectionForWordReplacement == true) {
                    badges.add(BadgeInfo(
                        stringResource(R.string.app_quirks_badge_selection_replacement),
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer
                    ))
                }
                if (effective?.forceEnterAction != null) {
                    val actionLabel = when (effective.forceEnterAction) {
                        EditorInfo.IME_ACTION_NONE -> stringResource(R.string.app_quirks_enter_action_none)
                        EditorInfo.IME_ACTION_SEND -> stringResource(R.string.app_quirks_enter_action_send)
                        EditorInfo.IME_ACTION_SEARCH -> stringResource(R.string.app_quirks_enter_action_search)
                        EditorInfo.IME_ACTION_GO -> stringResource(R.string.app_quirks_enter_action_go)
                        EditorInfo.IME_ACTION_NEXT -> stringResource(R.string.app_quirks_enter_action_next)
                        EditorInfo.IME_ACTION_DONE -> stringResource(R.string.app_quirks_enter_action_done)
                        else -> stringResource(R.string.app_quirks_badge_action)
                    }
                    badges.add(BadgeInfo(
                        actionLabel,
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer
                    ))
                } else if (userQuirk != null && userQuirk.hasCustomSettings()) {
                    badges.add(BadgeInfo(
                        "Custom",
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                } else if (AppQuirksManager.defaultQuirk(app.packageName) != null) {
                    badges.add(BadgeInfo(
                        stringResource(R.string.app_quirks_badge_default),
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingApp = app }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        if (app.icon != null) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = app.label,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = app.label.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (badges.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    badges.forEach { badge ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = badge.containerColor,
                                            contentColor = badge.contentColor,
                                        ) {
                                            Text(
                                                text = badge.text,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    val currentEditing = editingApp
    if (currentEditing != null) {
        AppQuirkDialog(
            app = currentEditing,
            onDismissRequest = { editingApp = null },
            onSaved = {
                editingApp = null
                refreshTrigger++
            }
        )
    }
}

@Composable
private fun AppQuirkDialog(
    app: AppEntry,
    onDismissRequest: () -> Unit,
    onSaved: () -> Unit
) {
    val initialEffective = remember(app.packageName) {
        AppQuirksManager.getEffectiveQuirk(app.packageName) ?: AppQuirk(app.packageName)
    }
    val hasUserQuirk = remember(app.packageName) {
        AppQuirksManager.getUserQuirk(app.packageName) != null
    }

    var forceWebEditor by remember { mutableStateOf(initialEffective.forceWebEditor) }
    var forceIncognito by remember { mutableStateOf(initialEffective.forceIncognito) }
    var forceNonIncognito by remember { mutableStateOf(initialEffective.forceNonIncognito) }
    var stripNoEnterAction by remember { mutableStateOf(initialEffective.stripNoEnterAction) }
    var forceDirectCommit by remember { mutableStateOf(initialEffective.forceDirectCommit) }
    var allowSymbolComposing by remember { mutableStateOf(initialEffective.allowSymbolComposing) }
    var disableAutoSpace by remember { mutableStateOf(initialEffective.disableAutoSpace) }
    var allowTypeNullKeyboard by remember { mutableStateOf(initialEffective.allowTypeNullKeyboard) }
    var autoCorrectionMode by remember { mutableStateOf(initialEffective.autoCorrectionMode) }
    var hideSuggestionStrip by remember { mutableStateOf(initialEffective.hideSuggestionStrip) }
    var alwaysShowSuggestions by remember { mutableStateOf(initialEffective.alwaysShowSuggestions) }
    var useSelectionForWordReplacement by remember { mutableStateOf(initialEffective.useSelectionForWordReplacement) }
    var hideToolbar by remember { mutableStateOf(initialEffective.hideToolbar) }
    var selectedAction by remember { mutableStateOf(initialEffective.forceEnterAction) }

    val options = listOf(
        ActionOption(stringResource(R.string.app_quirks_enter_action_default), null),
        ActionOption(stringResource(R.string.app_quirks_enter_action_none), EditorInfo.IME_ACTION_NONE),
        ActionOption(stringResource(R.string.app_quirks_enter_action_send), EditorInfo.IME_ACTION_SEND),
        ActionOption(stringResource(R.string.app_quirks_enter_action_search), EditorInfo.IME_ACTION_SEARCH),
        ActionOption(stringResource(R.string.app_quirks_enter_action_go), EditorInfo.IME_ACTION_GO),
        ActionOption(stringResource(R.string.app_quirks_enter_action_next), EditorInfo.IME_ACTION_NEXT),
        ActionOption(stringResource(R.string.app_quirks_enter_action_done), EditorInfo.IME_ACTION_DONE),
    )
    val currentSelectedOption = options.find { it.action == selectedAction } ?: options.first()

    val autoCorrectionOptions = listOf(
        ActionOption(stringResource(R.string.app_quirks_autocorrect_default), null),
        ActionOption(stringResource(R.string.app_quirks_autocorrect_enable), AppQuirksManager.AUTOCORRECT_FORCE_ENABLE),
        ActionOption(stringResource(R.string.app_quirks_autocorrect_disable), AppQuirksManager.AUTOCORRECT_FORCE_DISABLE),
    )
    val currentAutoCorrectionOption = autoCorrectionOptions.find { it.action == autoCorrectionMode } ?: autoCorrectionOptions.first()

    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = {
            val newQuirk = AppQuirk(
                packageName = app.packageName,
                forceWebEditor = forceWebEditor,
                stripNoEnterAction = stripNoEnterAction,
                forceEnterAction = selectedAction,
                forceIncognito = forceIncognito,
                forceNonIncognito = forceNonIncognito,
                forceDirectCommit = forceDirectCommit,
                allowSymbolComposing = allowSymbolComposing,
                disableAutoSpace = disableAutoSpace,
                allowTypeNullKeyboard = allowTypeNullKeyboard,
                autoCorrectionMode = autoCorrectionMode,
                hideSuggestionStrip = hideSuggestionStrip,
                hideToolbar = hideToolbar,
                alwaysShowSuggestions = alwaysShowSuggestions,
                useSelectionForWordReplacement = useSelectionForWordReplacement,
            )
            AppQuirksManager.saveQuirk(newQuirk)
            onSaved()
        },
        confirmButtonText = stringResource(android.R.string.ok),
        cancelButtonText = stringResource(android.R.string.cancel),
        neutralButtonText = if (hasUserQuirk) stringResource(R.string.app_quirks_reset_default) else null,
        onNeutral = {
            AppQuirksManager.removeQuirk(app.packageName)
            onSaved()
        },
        scrollContent = true,
        title = {
            Column {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                QuirkToggleRow(
                    title = stringResource(R.string.app_quirks_always_show_suggestions),
                    summary = stringResource(R.string.app_quirks_always_show_suggestions_summary),
                    checked = alwaysShowSuggestions,
                    onCheckedChange = {
                        alwaysShowSuggestions = it
                        if (it) hideSuggestionStrip = false
                    }
                )
                QuirkToggleRow(
                    title = stringResource(R.string.app_quirks_hide_suggestion_strip),
                    summary = stringResource(R.string.app_quirks_hide_suggestion_strip_summary),
                    checked = hideSuggestionStrip,
                    onCheckedChange = {
                        hideSuggestionStrip = it
                        if (it) alwaysShowSuggestions = false
                    }
                )
                QuirkToggleRow(
                    title = stringResource(R.string.app_quirks_hide_toolbar),
                    summary = stringResource(R.string.app_quirks_hide_toolbar_summary),
                    checked = hideToolbar,
                    onCheckedChange = { hideToolbar = it }
                )
                QuirkToggleRow(
                    title = stringResource(R.string.app_quirks_use_selection_replacement),
                    summary = stringResource(R.string.app_quirks_use_selection_replacement_summary),
                    checked = useSelectionForWordReplacement,
                    onCheckedChange = { useSelectionForWordReplacement = it }
                )
                QuirkToggleRow(
                    title = stringResource(R.string.app_quirks_force_web_editor),
                    summary = stringResource(R.string.app_quirks_force_web_editor_summary),
                    checked = forceWebEditor,
                    onCheckedChange = { forceWebEditor = it }
                )
                QuirkToggleRow(
                    title = stringResource(R.string.app_quirks_force_incognito),
                    summary = stringResource(R.string.app_quirks_force_incognito_summary),
                    checked = forceIncognito,
                    onCheckedChange = {
                        forceIncognito = it
                        if (it) forceNonIncognito = false
                    }
                )
                QuirkToggleRow(
                    title = stringResource(R.string.app_quirks_force_non_incognito),
                    summary = stringResource(R.string.app_quirks_force_non_incognito_summary),
                    checked = forceNonIncognito,
                    onCheckedChange = {
                        forceNonIncognito = it
                        if (it) forceIncognito = false
                    }
                )
                QuirkToggleRow(
                    title = stringResource(R.string.app_quirks_strip_no_enter),
                    summary = stringResource(R.string.app_quirks_strip_no_enter_summary),
                    checked = stripNoEnterAction,
                    onCheckedChange = { stripNoEnterAction = it }
                )
                QuirkToggleRow(
                    title = stringResource(R.string.app_quirks_force_direct_commit),
                    summary = stringResource(R.string.app_quirks_force_direct_commit_summary),
                    checked = forceDirectCommit,
                    onCheckedChange = {
                        forceDirectCommit = it
                        if (it) allowSymbolComposing = false
                    }
                )
                QuirkToggleRow(
                    title = stringResource(R.string.app_quirks_allow_symbol_composing),
                    summary = stringResource(R.string.app_quirks_allow_symbol_composing_summary),
                    checked = allowSymbolComposing,
                    onCheckedChange = {
                        allowSymbolComposing = it
                        if (it) forceDirectCommit = false
                    }
                )
                QuirkToggleRow(
                    title = stringResource(R.string.app_quirks_disable_auto_space),
                    summary = stringResource(R.string.app_quirks_disable_auto_space_summary),
                    checked = disableAutoSpace,
                    onCheckedChange = { disableAutoSpace = it }
                )
                QuirkToggleRow(
                    title = stringResource(R.string.app_quirks_allow_type_null),
                    summary = stringResource(R.string.app_quirks_allow_type_null_summary),
                    checked = allowTypeNullKeyboard,
                    onCheckedChange = { allowTypeNullKeyboard = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.app_quirks_autocorrect_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                DropDownField(
                    items = autoCorrectionOptions,
                    selectedItem = currentAutoCorrectionOption,
                    onSelected = { autoCorrectionMode = it.action }
                ) {
                    Text(it.label)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.app_quirks_enter_action),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                DropDownField(
                    items = options,
                    selectedItem = currentSelectedOption,
                    onSelected = { selectedAction = it.action }
                ) {
                    Text(it.label)
                }
            }
        }
    )
}

@Composable
private fun QuirkToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
