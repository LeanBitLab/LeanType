// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.translation.ITranslationProvider
import helium314.keyboard.latin.translation.TranslationModelDownloadListener
import helium314.keyboard.latin.translation.TranslationModelImporter
import helium314.keyboard.latin.translation.TranslationModelUrls
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.settings.DropDownField
import helium314.keyboard.settings.WithSmallTitle

data class TranslationLanguageItem(
    val code: String,
    val displayName: String
)

@Composable
fun TranslationModelDownloadDialog(
    provider: ITranslationProvider,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isOffline = BuildConfig.FLAVOR == "offline"
    var searchQuery by remember { mutableStateOf("") }
    
    val downloadedMap = remember { mutableStateMapOf<String, Boolean>() }
    val downloadingMap = remember { mutableStateMapOf<String, Boolean>() }
    var allLanguages by remember { mutableStateOf<List<TranslationLanguageItem>>(emptyList()) }
    var isLoadingList by remember { mutableStateOf(true) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            pendingImportUri = uri
        }
    }

    val currentImportUri = pendingImportUri
    if (currentImportUri != null) {
        val uri = currentImportUri
        val fileName = remember(uri) { TranslationModelImporter.getFilename(context, uri) ?: uri.lastPathSegment ?: "model.zip" }
        val detectedLangCode = remember(uri) { TranslationModelImporter.detectLanguageCode(context, uri) }
        val enabledLanguages = remember { SubtypeSettings.getEnabledSubtypes(true).map { it.locale().language } }
        val sortedLanguages = remember(allLanguages, detectedLangCode) {
            allLanguages.sortedWith(
                compareBy<TranslationLanguageItem>(
                    { it.code != detectedLangCode },
                    { it.code !in enabledLanguages },
                    { it.displayName }
                )
            )
        }
        var selectedLanguage by remember(uri) {
            mutableStateOf(
                sortedLanguages.firstOrNull { it.code == detectedLangCode }
                    ?: sortedLanguages.firstOrNull { it.code in enabledLanguages }
                    ?: sortedLanguages.firstOrNull()
            )
        }

        ThreeButtonAlertDialog(
            onDismissRequest = { pendingImportUri = null },
            onConfirmed = {
                val lang = selectedLanguage
                if (lang != null) {
                    scope.launch(Dispatchers.IO) {
                        val importedModel = TranslationModelImporter.importForLanguageFromUri(context, uri, lang.code)
                        withContext(Dispatchers.Main) {
                            if (importedModel != null) {
                                downloadedMap[lang.code] = true
                                Toast.makeText(context, "Imported translation model for ${lang.displayName}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to import translation model", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                pendingImportUri = null
            },
            confirmButtonText = stringResource(R.string.load_gesture_library_button_load),
            cancelButtonText = stringResource(android.R.string.cancel),
            title = { Text("Import Translation Model") },
            content = {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                    Text(
                        text = "File: $fileName",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    val lang = selectedLanguage
                    if (sortedLanguages.isNotEmpty() && lang != null) {
                        WithSmallTitle(stringResource(R.string.button_select_language)) {
                            DropDownField(
                                items = sortedLanguages,
                                selectedItem = lang,
                                onSelected = { selectedLanguage = it }
                            ) { item ->
                                Text(item.displayName)
                            }
                        }
                    }
                }
            },
            scrollContent = true
        )
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            TranslationModelImporter.migrateLegacyModels(context)
            val codes = try {
                provider.getSupportedLanguages()
            } catch (_: Throwable) {
                emptyList()
            }.ifEmpty {
                // Fallback standard ML Kit 59 language tags
                listOf(
                    "af", "sq", "ar", "be", "bg", "bn", "ca", "zh", "hr", "cs", "da", "nl",
                    "en", "eo", "et", "fi", "fr", "gl", "ka", "de", "el", "gu", "ht", "he",
                    "hi", "hu", "is", "id", "ga", "it", "ja", "kn", "ko", "lv", "lt", "mk",
                    "ms", "mt", "mr", "no", "fa", "pl", "pt", "ro", "ru", "sk", "sl", "es",
                    "sw", "sv", "tl", "ta", "te", "th", "tr", "uk", "ur", "vi", "cy"
                )
            }
            val sysLocale = context.resources.configuration.locales[0] ?: Locale.getDefault()
            val list = codes.map { code ->
                val locale = Locale.forLanguageTag(code)
                val name = locale.getDisplayName(sysLocale).ifBlank {
                    locale.getDisplayName(Locale.ENGLISH).ifBlank { code }
                }.replaceFirstChar { it.uppercase(sysLocale) }
                TranslationLanguageItem(code, "$name ($code)")
            }.sortedBy { it.displayName }

            withContext(Dispatchers.Main) {
                allLanguages = list
                isLoadingList = false
            }

            // Check download status for all languages
            codes.forEach { code ->
                val downloaded = if (code == "en") true else {
                    (try {
                        provider.isModelDownloaded(code)
                    } catch (_: Throwable) {
                        false
                    }) || TranslationModelImporter.isModelInstalled(context, code)
                }
                withContext(Dispatchers.Main) { downloadedMap[code] = downloaded }
            }
        }
    }

    PreferenceDialog(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.offline_translation_models_title),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isOffline) "Download model in browser, then import .zip" else "Download in app or import .zip",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Button(
                        onClick = { importLauncher.launch("application/zip") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Import .zip", style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search language…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                if (isLoadingList) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val filtered = remember(searchQuery, allLanguages, downloadedMap.toMap(), downloadingMap.toMap()) {
                        val baseList = if (searchQuery.isBlank()) allLanguages
                        else allLanguages.filter {
                            it.displayName.contains(searchQuery, ignoreCase = true) ||
                                it.code.contains(searchQuery, ignoreCase = true)
                        }
                        baseList.sortedWith(
                            compareByDescending<TranslationLanguageItem> {
                                if (it.code == "en") 2 else if (downloadedMap[it.code] == true) 1 else 0
                            }.thenBy { it.displayName.lowercase() }
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filtered, key = { it.code }) { item ->
                            val isEnglish = item.code == "en"
                            val isDownloaded = isEnglish || downloadedMap[item.code] == true
                            val isDownloading = !isEnglish && downloadingMap[item.code] == true

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = item.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isDownloaded) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = if (isEnglish) "Built-in" else if (isDownloaded) "Downloaded (Offline ready)" else if (isDownloading) "Downloading…" else "Not downloaded",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDownloaded)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isEnglish) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                } else if (isDownloading) {
                                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    }
                                } else if (isDownloaded) {
                                    Button(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                val deleted = (try {
                                                    provider.deleteModel(item.code)
                                                } catch (_: Throwable) {
                                                    false
                                                }) || TranslationModelImporter.deleteModel(context, item.code)
                                                withContext(Dispatchers.Main) {
                                                    if (deleted) {
                                                        downloadedMap[item.code] = false
                                                        Toast.makeText(context, "${item.displayName} model removed", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Failed to remove model", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Delete", style = MaterialTheme.typography.labelSmall)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (isOffline) {
                                                val url = TranslationModelUrls.getDownloadUrl(item.code)
                                                if (url != null) {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                    Toast.makeText(context, "Downloading in browser… import .zip once finished", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "Download URL not available", Toast.LENGTH_SHORT).show()
                                                }
                                             } else {
                                                downloadModelWithFallback(
                                                    provider = provider,
                                                    item = item,
                                                    context = context,
                                                    scope = scope,
                                                    downloadingMap = downloadingMap,
                                                    downloadedMap = downloadedMap
                                                )
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Download", style = MaterialTheme.typography.labelSmall)
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

private fun downloadModelWithFallback(
    provider: ITranslationProvider,
    item: TranslationLanguageItem,
    context: android.content.Context,
    scope: CoroutineScope,
    downloadingMap: MutableMap<String, Boolean>,
    downloadedMap: MutableMap<String, Boolean>
) {
    downloadingMap[item.code] = true

    fun tryFallbackToBrowser() {
        downloadingMap[item.code] = false
        val url = TranslationModelUrls.getDownloadUrl(item.code)
        if (url != null) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Plugin incompatible with in-app download. Downloading in browser… import .zip once finished.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to launch browser: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Download not supported by this plugin version. Please update the plugin.", Toast.LENGTH_LONG).show()
        }
    }

    val ifaceVersion = try {
        provider.getInterfaceVersion()
    } catch (_: Throwable) {
        1
    }
    if (ifaceVersion < 2) {
        Log.w("TranslationDownload", "Translation plugin interface version ($ifaceVersion) does not support in-app download. Falling back to browser.")
        tryFallbackToBrowser()
        return
    }

    fun tryLegacyDownload(): Boolean {
        return try {
            val legacyMethod = provider.javaClass.methods.firstOrNull { method ->
                method.name == "downloadModel" &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == String::class.java &&
                    method.parameterTypes[1] != TranslationModelDownloadListener::class.java
            } ?: return false

            val paramType = legacyMethod.parameterTypes[1]
            val callback = if (paramType.isInterface) {
                java.lang.reflect.Proxy.newProxyInstance(
                    provider.javaClass.classLoader ?: paramType.classLoader,
                    arrayOf(paramType)
                ) { _, method, args ->
                    if (method.name == "invoke" || method.name == "onComplete") {
                        val success = (args?.firstOrNull() as? Boolean) ?: false
                        scope.launch(Dispatchers.Main) {
                            downloadingMap[item.code] = false
                            if (success) {
                                downloadedMap[item.code] = true
                                Toast.makeText(context, "Downloaded ${item.displayName}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    null
                }
            } else {
                val f: (Boolean) -> Unit = { success ->
                    scope.launch(Dispatchers.Main) {
                        downloadingMap[item.code] = false
                        if (success) {
                            downloadedMap[item.code] = true
                            Toast.makeText(context, "Downloaded ${item.displayName}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                f
            }
            legacyMethod.invoke(provider, item.code, callback)
            true
        } catch (t: Throwable) {
            Log.w("TranslationDownload", "Legacy reflection download failed", t)
            false
        }
    }

    val modernListener = object : TranslationModelDownloadListener {
        override fun onComplete(success: Boolean, errorMessage: String?) {
            scope.launch(Dispatchers.Main) {
                if (success) {
                    downloadingMap[item.code] = false
                    downloadedMap[item.code] = true
                    Toast.makeText(context, "Downloaded ${item.displayName}", Toast.LENGTH_SHORT).show()
                } else if (errorMessage == "Unsupported" || errorMessage?.contains("abstract method", ignoreCase = true) == true) {
                    if (!tryLegacyDownload()) {
                        tryFallbackToBrowser()
                    }
                } else {
                    downloadingMap[item.code] = false
                    val err = if (!errorMessage.isNullOrBlank()) ": $errorMessage" else ""
                    Toast.makeText(context, "Download failed$err", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onComplete(success: Boolean) {
            onComplete(success, null)
        }
    }

    var invokedModern = false
    try {
        provider.downloadModel(item.code, modernListener)
        invokedModern = true
    } catch (_: AbstractMethodError) {
    } catch (_: NoSuchMethodError) {
    } catch (_: IncompatibleClassChangeError) {
    } catch (e: Throwable) {
        Log.w("TranslationDownload", "Modern downloadModel error", e)
    }

    if (!invokedModern) {
        if (!tryLegacyDownload()) {
            tryFallbackToBrowser()
        }
    }
}
