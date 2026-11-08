// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.updates.AppUpdatesManager
import kotlinx.coroutines.launch

private val currentChangelogItems = listOf(
    "• Unicode Font Styles: Added 26+ typographic font styles with interactive preview strip and live typing",
    "• Unicode Symbol Palette: Added topic-categorized Unicode symbols and scrollable emoji category bar (#776)",
    "• Foldable Split Keyboard: Fixed unfolded split keyboard detection and decoupled preferences on foldables (#778)",
    "• Shift & Auto-Caps Sync: Prevented shift state desynchronization during reload and unshift (#598)",
    "• Fast Typing Capitalization: Prevented duplicate caps and auto-caps desync during rapid typing (#598, #743)",
    "• Layout Popup Key Priority: Prioritized layout-defined popup keys over auto-generated symbols (#772)",
    "• Automation Broadcasts: Added broadcast receiver for external subtype, layout, and mode switching (#501)",
    "• Bracket & Quote Auto-Pairing: Added auto-closing brackets and CJK quotes with step-over (#754)",
    "• Plain Text Dictionary Export: Added export option for user dictionaries to plain text format (#431)",
    "• Smart Correction Suppression: Prevented repeating auto-corrections reverted by backspace (#415)"
)

@Composable
fun UpdatesScreen(
    onClickBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.prefs()

    val isOnlineFlavor = BuildConfig.FLAVOR == "standard"

    val updateTrigger by AppUpdatesManager.updateState.collectAsState()
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateCheckStatus by remember { mutableStateOf<String?>(null) }
    val isAppUpdateAvailable = remember(updateTrigger) { AppUpdatesManager.hasAppUpdate(context) }
    val latestVersionTag = remember(updateTrigger) { AppUpdatesManager.getAppLatestVersion(context) }
    val pluginStatuses = remember(updateTrigger) { AppUpdatesManager.getInstalledPluginStatuses(context) }
    var isAutoCheckEnabled by remember { mutableStateOf(prefs.getBoolean(AppUpdatesManager.PREF_AUTO_CHECK, true)) }

    fun checkForUpdates() {
        if (!isOnlineFlavor) return

        isCheckingUpdates = true
        updateCheckStatus = null
        scope.launch {
            try {
                val (appUpdate, _) = AppUpdatesManager.checkAllUpdates(context)
                if (appUpdate) {
                    val tag = AppUpdatesManager.getAppLatestVersion(context)
                    updateCheckStatus = context.getString(R.string.updates_update_available, tag ?: "")
                } else {
                    updateCheckStatus = context.getString(R.string.updates_up_to_date)
                }
            } catch (e: Exception) {
                updateCheckStatus = context.getString(R.string.updates_network_error)
            } finally {
                isCheckingUpdates = false
            }
        }
    }

    val autoCheckSetting = remember {
        Setting(
            key = AppUpdatesManager.PREF_AUTO_CHECK,
            title = context.getString(R.string.updates_auto_check_title),
            description = context.getString(R.string.updates_auto_check_summary)
        ) {
            SwitchPreference(
                setting = it,
                default = true,
                onCheckedChange = { isAutoCheckEnabled = it }
            )
        }
    }

    val frequencySetting = remember {
        Setting(
            key = AppUpdatesManager.PREF_FREQUENCY,
            title = context.getString(R.string.updates_frequency_title)
        ) {
            ListPreference(
                setting = it,
                items = listOf(
                    context.getString(R.string.updates_frequency_daily) to "1",
                    context.getString(R.string.updates_frequency_weekly) to "7",
                    context.getString(R.string.updates_frequency_monthly) to "30"
                ),
                default = "7"
            )
        }
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_updates),
        settings = emptyList()
    ) {
        Scaffold(contentWindowInsets = WindowInsets(0)) { innerPadding ->
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(vertical = 8.dp)
            ) {
                // Section 1: App Updates (OMITTED entirely on offline flavor)
                if (isOnlineFlavor) {
                    // Minimal Update Indicator Banner if update is available
                    if (isAppUpdateAvailable && latestVersionTag != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "🎉 " + stringResource(R.string.updates_new_update_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = stringResource(R.string.updates_new_update_desc, latestVersionTag ?: ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Links.GITHUB_RELEASES_PAGE.toUri())
                                            context.startActivity(intent)
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(stringResource(R.string.updates_view_release), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            val currentVersionText = stringResource(R.string.updates_installed_version, BuildConfig.VERSION_NAME, BuildConfig.FLAVOR)
                            val status = updateCheckStatus
                            val checkDescription = when {
                                isCheckingUpdates -> stringResource(R.string.updates_checking)
                                status != null -> status
                                isAppUpdateAvailable && latestVersionTag != null -> stringResource(R.string.updates_update_available, latestVersionTag)
                                else -> currentVersionText
                            }

                            Preference(
                                name = stringResource(R.string.updates_check_title),
                                description = checkDescription,
                                icon = R.drawable.ic_settings_updates,
                                onClick = { checkForUpdates() },
                                value = {
                                    if (isCheckingUpdates) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else if (isAppUpdateAvailable) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(Color(0xFFE53935), CircleShape)
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.updates_badge_update),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    } else if (updateCheckStatus != null || AppUpdatesManager.getAppLatestVersion(context) != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = stringResource(R.string.updates_badge_latest),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            )

                            autoCheckSetting.Preference()

                            if (isAutoCheckEnabled) {
                                frequencySetting.Preference()
                            }
                        }
                    }

                    // Section 2: Companion Plugins Updates
                    if (pluginStatuses.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                PreferenceCategory(stringResource(R.string.updates_plugins_title))

                                for (pluginStatus in pluginStatuses) {
                                    val pluginName = stringResource(pluginStatus.plugin.nameRes)
                                    val desc = if (pluginStatus.isUpdateAvailable && pluginStatus.latestVersion != null) {
                                        stringResource(R.string.updates_plugin_update_available, pluginStatus.latestVersion)
                                    } else {
                                        "v${pluginStatus.installedVersion}"
                                    }

                                    Preference(
                                        name = pluginName,
                                        description = desc,
                                        icon = pluginStatus.plugin.iconRes,
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, pluginStatus.plugin.releasesPageUrl.toUri())
                                            context.startActivity(intent)
                                        },
                                        value = {
                                            if (pluginStatus.isUpdateAvailable) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .background(Color(0xFFE53935), CircleShape)
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        modifier = Modifier.clickable {
                                                            val intent = Intent(Intent.ACTION_VIEW, pluginStatus.plugin.releasesPageUrl.toUri())
                                                            context.startActivity(intent)
                                                        }
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.updates_badge_update),
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            } else if (pluginStatus.latestVersion != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.updates_badge_latest),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.updates_offline_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Section 2: Support & Sponsorship
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = stringResource(R.string.updates_support_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.updates_support_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Preference(
                            name = stringResource(R.string.updates_github_sponsor_title),
                            description = stringResource(R.string.updates_github_sponsor_desc),
                            icon = R.drawable.ic_settings_about_github,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Links.SPONSOR.toUri())
                                context.startActivity(intent)
                            }
                        )

                        Preference(
                            name = stringResource(R.string.updates_opencollective_title),
                            description = stringResource(R.string.updates_opencollective_desc),
                            icon = R.drawable.ic_opencollective,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Links.OPEN_COLLECTIVE.toUri())
                                context.startActivity(intent)
                            }
                        )
                    }
                }

                // Section 3: Changelog (Current Version Only)
                var isChangelogExpanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isChangelogExpanded = !isChangelogExpanded }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.updates_whats_new_title, BuildConfig.VERSION_NAME),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = stringResource(R.string.updates_badge_current),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Icon(
                                    painter = painterResource(R.drawable.ic_arrow_left),
                                    contentDescription = if (isChangelogExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(if (isChangelogExpanded) 90f else -90f)
                                )
                            }
                        }

                        AnimatedVisibility(visible = isChangelogExpanded) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                for (item in currentChangelogItems) {
                                    Text(
                                        text = item,
                                        style = MaterialTheme.typography.bodySmall,
                                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3f,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 4: Community & Official Links
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = stringResource(R.string.updates_community_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.updates_community_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val socialLinks = listOf(
                                Triple(R.drawable.ic_social_web, stringResource(R.string.social_website), Links.OFFICIAL_SITE),
                                Triple(R.drawable.ic_social_telegram, stringResource(R.string.social_telegram), Links.TELEGRAM),
                                Triple(R.drawable.ic_social_reddit, stringResource(R.string.social_reddit), Links.REDDIT),
                                Triple(R.drawable.ic_social_x, stringResource(R.string.social_x), Links.X_TWITTER),
                                Triple(R.drawable.ic_social_youtube, stringResource(R.string.social_youtube), Links.YOUTUBE)
                            )
                            for ((iconRes, label, url) in socialLinks) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                            context.startActivity(intent)
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                painter = painterResource(iconRes),
                                                contentDescription = label,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
