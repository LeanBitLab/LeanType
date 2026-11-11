// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.sound.CustomSoundManager
import helium314.keyboard.latin.sound.RemoteSoundPack
import helium314.keyboard.latin.sound.SoundPackImporter
import helium314.keyboard.latin.sound.SoundPackUrls
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SoundPackDownloadDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.prefs() }
    val isOffline = BuildConfig.FLAVOR == "offline"

    var currentSelectedStyle by remember {
        mutableStateOf(prefs.getString(Settings.PREF_KEYPRESS_SOUND_STYLE, Defaults.PREF_KEYPRESS_SOUND_STYLE) ?: Defaults.PREF_KEYPRESS_SOUND_STYLE)
    }

    var searchQuery by remember { mutableStateOf("") }
    var customPacks by remember { mutableStateOf(SoundPackImporter.getInstalledCustomPacks(context)) }
    var remotePacks by remember { mutableStateOf<List<RemoteSoundPack>>(SoundPackUrls.FALLBACK_CATALOG) }
    var isLoadingRemote by remember { mutableStateOf(false) }
    val downloadingMap = remember { mutableStateMapOf<String, Boolean>() }
    val downloadProgressMap = remember { mutableStateMapOf<String, Float>() }

    fun refreshCustomPacks() {
        customPacks = SoundPackImporter.getInstalledCustomPacks(context)
    }

    LaunchedEffect(Unit) {
        if (!isOffline) {
            isLoadingRemote = true
            withContext(Dispatchers.IO) {
                val fetched = SoundPackUrls.fetchRemoteIndex()
                withContext(Dispatchers.Main) {
                    remotePacks = fetched
                    isLoadingRemote = false
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val importedId = SoundPackImporter.importFromUri(context, uri)
                withContext(Dispatchers.Main) {
                    if (importedId != null) {
                        refreshCustomPacks()
                        currentSelectedStyle = importedId
                        prefs.edit().putString(Settings.PREF_KEYPRESS_SOUND_STYLE, importedId).apply()
                        CustomSoundManager.getInstance(context).setSoundPack(importedId)
                        CustomSoundManager.getInstance(context).previewSound(importedId)
                        Toast.makeText(context, context.getString(R.string.sound_pack_imported_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.sound_pack_import_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun selectPack(packId: String) {
        currentSelectedStyle = packId
        prefs.edit().putString(Settings.PREF_KEYPRESS_SOUND_STYLE, packId).apply()
        CustomSoundManager.getInstance(context).setSoundPack(packId)
        CustomSoundManager.getInstance(context).previewSound(packId)
    }

    PreferenceDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.prefs_keypress_sound_style_dialog_title),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isOffline) stringResource(R.string.sound_pack_browser_note) else stringResource(R.string.sound_pack_app_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Button(
                        onClick = { importLauncher.launch("*/*") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(stringResource(R.string.sound_pack_import_zip), style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_sound_pack_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                if (isLoadingRemote && remotePacks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // System Default Item
                        if (searchQuery.isBlank() || "System default".contains(searchQuery, ignoreCase = true)) {
                            item(key = SoundPackUrls.SYSTEM_DEFAULT_ID) {
                                val isSelected = currentSelectedStyle == SoundPackUrls.SYSTEM_DEFAULT_ID
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { selectPack(SoundPackUrls.SYSTEM_DEFAULT_ID) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectPack(SoundPackUrls.SYSTEM_DEFAULT_ID) }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.prefs_keypress_sound_style_system),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = stringResource(R.string.sound_pack_default_system_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { CustomSoundManager.getInstance(context).previewSound(SoundPackUrls.SYSTEM_DEFAULT_ID) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_play_arrow),
                                                contentDescription = stringResource(R.string.sound_pack_preview),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Installed Custom Packs Section
                        val filteredCustom = customPacks.filter {
                            searchQuery.isBlank() || it.displayName.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true)
                        }

                        if (filteredCustom.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.sound_pack_installed_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
                                )
                            }

                            items(filteredCustom, key = { it.id }) { pack ->
                                val isSelected = currentSelectedStyle == pack.id

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { selectPack(pack.id) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectPack(pack.id) }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(
                                                text = pack.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            val subtitle = buildString {
                                                pack.author?.let { append("$it • ") }
                                                pack.versionName?.let { append("v$it • ") }
                                                append(pack.description)
                                            }
                                            Text(
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { CustomSoundManager.getInstance(context).previewSound(pack.id) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_play_arrow),
                                                    contentDescription = stringResource(R.string.sound_pack_preview),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Button(
                                                onClick = {
                                                    SoundPackImporter.deletePack(context, pack.id)
                                                    refreshCustomPacks()
                                                    if (currentSelectedStyle == pack.id) {
                                                        selectPack(SoundPackUrls.SYSTEM_DEFAULT_ID)
                                                    }
                                                    Toast.makeText(context, context.getString(R.string.sound_pack_deleted_format, pack.displayName), Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text(stringResource(R.string.delete), style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Available Online Sound Packs Section
                        val availableRemote = remotePacks.filter { rp ->
                            customPacks.none { it.id == rp.id } &&
                                (searchQuery.isBlank() || rp.name.contains(searchQuery, ignoreCase = true) || (rp.summary?.contains(searchQuery, ignoreCase = true) == true))
                        }

                        if (availableRemote.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.sound_pack_available_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
                                )
                            }

                            items(availableRemote, key = { it.id }) { rPack ->
                                val isDownloading = downloadingMap[rPack.id] == true
                                val downloadProgress = downloadProgressMap[rPack.id] ?: 0f
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text(
                                                    text = rPack.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                val details = buildString {
                                                    rPack.author?.let { append("$it • ") }
                                                    append("v${rPack.versionName} • ")
                                                    if (rPack.sizeBytes > 0) {
                                                        val kb = rPack.sizeBytes / 1024
                                                        if (kb >= 1024) append(String.format("%.1f MB", kb / 1024f))
                                                        else append("$kb KB")
                                                    }
                                                }
                                                Text(
                                                    text = if (isDownloading) {
                                                        if (downloadProgress > 0f) "${stringResource(R.string.downloading)} ${(downloadProgress * 100).toInt()}%" else stringResource(R.string.downloading)
                                                    } else details,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isDownloading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (!isDownloading && !rPack.summary.isNullOrBlank()) {
                                                    Text(
                                                        text = rPack.summary,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            if (isDownloading) {
                                                Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                                }
                                            } else {
                                                Button(
                                                    onClick = {
                                                        if (isOffline) {
                                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rPack.downloadUrl)).apply {
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                            context.startActivity(intent)
                                                            Toast.makeText(context, context.getString(R.string.sound_pack_downloading_in_browser_toast), Toast.LENGTH_LONG).show()
                                                        } else {
                                                            downloadingMap[rPack.id] = true
                                                            downloadProgressMap[rPack.id] = 0f
                                                            scope.launch(Dispatchers.IO) {
                                                                val ok = SoundPackImporter.downloadAndInstall(context, rPack) { prog ->
                                                                    scope.launch(Dispatchers.Main) {
                                                                        downloadProgressMap[rPack.id] = prog
                                                                    }
                                                                }
                                                                withContext(Dispatchers.Main) {
                                                                    downloadingMap[rPack.id] = false
                                                                    downloadProgressMap.remove(rPack.id)
                                                                    if (ok) {
                                                                        refreshCustomPacks()
                                                                        selectPack(rPack.id)
                                                                        Toast.makeText(context, context.getString(R.string.sound_pack_downloaded_activated, rPack.name), Toast.LENGTH_SHORT).show()
                                                                    } else {
                                                                        Toast.makeText(context, context.getString(R.string.sound_pack_download_failed, rPack.name), Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text(stringResource(R.string.download), style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }

                                        if (isDownloading) {
                                            if (downloadProgress > 0f) {
                                                LinearProgressIndicator(
                                                    progress = { downloadProgress },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 6.dp)
                                                )
                                            } else {
                                                LinearProgressIndicator(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 6.dp)
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
        }
    )
}
