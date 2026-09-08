// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.core.content.FileProvider
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
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private val currentChangelogItems = listOf(
    "• 100% Kotlin Migration: Complete conversion of all 430 application layer files from Java to modern Kotlin (0 Java files remaining)",
    "• Key & Functional Corner Radius: Independent corner radius sliders for normal and functional keys in Appearance settings when key borders are active",
    "• Smart Suggestions: User dictionary authority, contextual bigram gating, and instant session typo eviction on backspace",
    "• Fast OCR Scanning: Defaulted OCR recognition to Latin for faster text extraction, with refined camera lifecycle & back-press handling (#487)",
    "• Input & Auto-Caps Fixes: Fixed selection delete cache desync, restored caps on backspace, and suppressed mid-sentence auto-caps (#448, #482)",
    "• Foldables & Hardware Keys: Fixed layout on folded cover screens (#400) and stabilized Ctrl+Space / Shift+Space shortcuts (#444)",
    "• Compatibility & Stability: Fixed Android <= 8.1 voice input crash, added graceful AI error handling (#492), and instant clipboard sync (#493)"
)

@Composable
fun UpdatesScreen(
    onClickBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.prefs()

    val isOnlineFlavor = BuildConfig.FLAVOR == "standard" || BuildConfig.FLAVOR == "standardfull"
    val isStandardFull = BuildConfig.FLAVOR == "standardfull"

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateCheckStatus by remember { mutableStateOf<String?>(null) }
    var latestVersionTag by remember { mutableStateOf<String?>(null) }
    var downloadApkUrl by remember { mutableStateOf<String?>(null) }
    var isUpdateAvailable by remember { mutableStateOf(false) }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var isAutoCheckEnabled by remember { mutableStateOf(prefs.getBoolean("pref_auto_check_updates", true)) }

    fun installApk(file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(context, "Please allow LeanType to install unknown apps", Toast.LENGTH_LONG).show()
                    val permissionIntent = Intent(
                        AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(permissionIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Error starting installation: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun startDownload(apkUrl: String, versionTag: String) {
        isDownloading = true
        downloadProgress = 0f
        scope.launch(Dispatchers.IO) {
            try {
                val updatesDir = File(context.cacheDir, "updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()
                val targetFile = File(updatesDir, "LeanType_${versionTag}.apk")

                var url = URL(apkUrl)
                var conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "LeanType-Android")
                conn.connect()

                // Follow redirects if any
                var redirectCount = 0
                while ((conn.responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                            conn.responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                            conn.responseCode == 307 || conn.responseCode == 308) && redirectCount < 5) {
                    val location = conn.getHeaderField("Location") ?: break
                    url = URL(location)
                    conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "LeanType-Android")
                    conn.connect()
                    redirectCount++
                }

                val totalBytes = conn.contentLength
                var downloadedBytes = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val prog = downloadedBytes.toFloat() / totalBytes.toFloat()
                                withContext(Dispatchers.Main) {
                                    downloadProgress = prog
                                }
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    downloadedApkFile = targetFile
                    isDownloading = false
                    installApk(targetFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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

                    var targetApkUrl: String? = null
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            val downloadUrl = asset.optString("browser_download_url", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                if (isStandardFull && name.contains("standardfull", ignoreCase = true)) {
                                    targetApkUrl = downloadUrl
                                    break
                                } else if (!isStandardFull && name.contains("standard", ignoreCase = true) && !name.contains("standardfull", ignoreCase = true)) {
                                    targetApkUrl = downloadUrl
                                    break
                                } else if (targetApkUrl == null) {
                                    targetApkUrl = downloadUrl
                                }
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        latestVersionTag = tag
                        downloadApkUrl = targetApkUrl
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

                                if (isDownloading) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    LinearProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Downloading: ${(downloadProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        if (isStandardFull) {
                                            Button(
                                                onClick = {
                                                    val localApk = downloadedApkFile
                                                    val apkUrl = downloadApkUrl
                                                    val versionTag = latestVersionTag
                                                    if (localApk != null && localApk.exists()) {
                                                        installApk(localApk)
                                                    } else if (apkUrl != null && versionTag != null) {
                                                        startDownload(apkUrl, versionTag)
                                                    } else {
                                                        val intent = Intent(Intent.ACTION_VIEW, Links.GITHUB_RELEASES_PAGE.toUri())
                                                        context.startActivity(intent)
                                                    }
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                val btnText = if (downloadedApkFile?.exists() == true) "Install Now" else "Download & Install"
                                                Text(btnText, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
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
