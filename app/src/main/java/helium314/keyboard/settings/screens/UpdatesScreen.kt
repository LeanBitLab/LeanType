// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import helium314.keyboard.settings.preferences.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val currentChangelogItems = listOf(
    "• Input Desync & Duplication Fix: Resolved text duplication during word replacement and backspace desync thrashing in RichInputConnection",
    "• Backup & Restore Freeze Elimination: Removed blocking main-thread latches during backup and restore to prevent UI freezes and ANRs",
    "• Mode Switch Touch Protection: Fixed PointerTracker layout transitions to eliminate accidental space and 'x' clicks when switching ?123 and ABC",
    "• Hexagonal Honeycomb Layout: Native Typewise and hex QWERTY layouts with axial hit detection, twin spacebars, and custom vector rendering",
    "• Landscape Hex Ergonomic Split: Positioned hex keys at screen edges with a central gap in landscape mode, maintaining comfortable key sizing and thumb reachability",
    "• Popup Key Vertical Offset: Added an adjustable popup position slider (0–10 dp) in Appearance settings with refined preview animations",
    "• Engine & Locale Polish: Decoupled startup settings listeners, fixed voice status NPE, synchronized emoji dictionary reloads, and preserved Catalan dictionaries"
)

@Composable
fun UpdatesScreen(
    onClickBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.prefs()

    val isOnlineFlavor = BuildConfig.FLAVOR == "standard"

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateCheckStatus by remember { mutableStateOf<String?>(null) }
    var latestVersionTag by remember { mutableStateOf<String?>(null) }
    var isUpdateAvailable by remember { mutableStateOf(false) }
    var isAutoCheckEnabled by remember { mutableStateOf(prefs.getBoolean("pref_auto_check_updates", true)) }

    fun checkForUpdates() {
        if (!isOnlineFlavor) return

        isCheckingUpdates = true
        updateCheckStatus = null
        scope.launch(Dispatchers.IO) {
            try {
                val url = URL(Links.GITHUB_RELEASES_API)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "LeanType-Android")
                conn.connect()

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val tag = json.optString("tag_name", "").trim()

                    withContext(Dispatchers.Main) {
                        latestVersionTag = tag
                        val cleanCurrent = BuildConfig.VERSION_NAME.removePrefix("v").trim()
                        val cleanRemote = tag.removePrefix("v").trim()

                        if (cleanRemote.isNotBlank() && isNewerVersion(cleanCurrent, cleanRemote)) {
                            isUpdateAvailable = true
                            updateCheckStatus = "Update available: $tag"
                        } else {
                            isUpdateAvailable = false
                            updateCheckStatus = context.getString(R.string.updates_up_to_date)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        updateCheckStatus = "Check failed (HTTP ${conn.responseCode})"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateCheckStatus = "Network error checking updates"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isCheckingUpdates = false
                }
            }
        }
    }

    val autoCheckSetting = remember {
        Setting(
            key = "pref_auto_check_updates",
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
            key = "pref_auto_check_updates_frequency",
            title = context.getString(R.string.updates_frequency_title)
        ) {
            ListPreference(
                setting = it,
                items = listOf(
                    "Daily" to "1",
                    "Weekly" to "7",
                    "Monthly" to "30"
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
                    if (isUpdateAvailable && latestVersionTag != null) {
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
                                            text = "🎉 New Update Available",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Version $latestVersionTag is ready to install",
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
                                        Text("View Release", fontWeight = FontWeight.Bold)
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
                            val currentVersionText = "Installed: v${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})"
                            val status = updateCheckStatus
                            val checkDescription = when {
                                isCheckingUpdates -> stringResource(R.string.updates_checking)
                                status != null -> status
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
                                    } else if (isUpdateAvailable) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "Update",
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else if (updateCheckStatus != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = "Latest",
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
                                text = "What's New in v${BuildConfig.VERSION_NAME}",
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
                                        text = "Current",
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

private fun isNewerVersion(current: String, remote: String): Boolean {
    val currentParts = current.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
    val remoteParts = remote.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
    val maxLen = maxOf(currentParts.size, remoteParts.size)
    for (i in 0 until maxLen) {
        val c = currentParts.getOrElse(i) { 0 }
        val r = remoteParts.getOrElse(i) { 0 }
        if (r > c) return true
        if (r < c) return false
    }
    return false
}
