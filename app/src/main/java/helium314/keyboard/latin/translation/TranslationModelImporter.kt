// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translation

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object TranslationModelImporter {
    private const val TAG = "TranslationModelImporter"

    fun isModelInstalled(context: Context, langCode: String): Boolean {
        if (langCode == "en") return true
        val modelName = TranslationModelUrls.getModelName(langCode) ?: langCode
        val baseDirs = listOfNotNull(context.noBackupFilesDir, context.filesDir).distinct()
        val normalized = if (langCode == "he") "iw" else if (langCode == "iw") "he" else langCode
        val possibleNames = listOf(
            modelName,
            "${langCode}_en", "en_${langCode}",
            "${normalized}_en", "en_${normalized}",
            langCode, normalized
        ).distinct()

        val isValidModelFile = { f: File ->
            f.isFile && (f.name.startsWith("merged_dict") || f.name.startsWith("dict.") || f.length() > 100_000)
        }

        for (baseDir in baseDirs) {
            for (name in possibleNames) {
                val dir = File(baseDir, "com.google.mlkit.translate.models/$name")
                if (dir.exists() && dir.isDirectory) {
                    val hasRootFiles = dir.listFiles()?.any(isValidModelFile) == true
                    val dirZero = File(dir, "0")
                    val hasZeroFiles = dirZero.exists() && dirZero.isDirectory &&
                        dirZero.listFiles()?.any(isValidModelFile) == true
                    if (hasRootFiles || hasZeroFiles) return true
                }
            }
        }
        return false
    }

    fun deleteModel(context: Context, langCode: String): Boolean {
        if (langCode == "en") return false
        val modelName = TranslationModelUrls.getModelName(langCode) ?: langCode
        val baseDirs = listOfNotNull(context.noBackupFilesDir, context.filesDir).distinct()
        val normalized = if (langCode == "he") "iw" else if (langCode == "iw") "he" else langCode
        val possibleNames = listOf(
            modelName,
            "${langCode}_en", "en_${langCode}",
            "${normalized}_en", "en_${normalized}",
            langCode, normalized
        ).distinct()

        var anyDeleted = false
        for (baseDir in baseDirs) {
            for (name in possibleNames) {
                val dir = File(baseDir, "com.google.mlkit.translate.models/$name")
                if (dir.exists()) {
                    if (dir.deleteRecursively()) anyDeleted = true
                }
            }
        }
        Log.i(TAG, "Deleted translation model for $langCode (deleted=$anyDeleted)")
        if (anyDeleted) {
            TranslationLoader.unloadPlugin()
        }
        return anyDeleted
    }

    fun migrateLegacyModels(context: Context) {
        try {
            // 1. Delete redundant duplicate models under filesDir (ML Kit exclusively uses noBackupFilesDir)
            val filesModelsDir = File(context.filesDir, "com.google.mlkit.translate.models")
            if (filesModelsDir.exists()) {
                filesModelsDir.deleteRecursively()
                Log.i(TAG, "Removed redundant translation models from filesDir")
            }

            val noBackupDir = context.noBackupFilesDir ?: return
            val modelsDir = File(noBackupDir, "com.google.mlkit.translate.models")
            if (!modelsDir.exists() || !modelsDir.isDirectory) return

            // 2. Remove corrupted self-referential / non-pair directories
            File(modelsDir, "en_en").deleteRecursively()
            File(modelsDir, "en").deleteRecursively()

            // 3. Consolidate non-canonical alias folders (e.g. es_en, es -> en_es)
            val subDirs = modelsDir.listFiles()?.filter { it.isDirectory && it.name != "0" } ?: emptyList()
            for (dir in subDirs) {
                val name = dir.name
                val canonical = TranslationModelUrls.getModelName(name)
                    ?: if (name.contains("_")) {
                        val parts = name.split("_")
                        if (parts.size == 2) {
                            TranslationModelUrls.getModelName(parts[0])
                                ?: TranslationModelUrls.getModelName(parts[1])
                                ?: "${parts[1]}_${parts[0]}"
                        } else null
                    } else null

                if (canonical != null && canonical != name) {
                    val targetDir = File(modelsDir, canonical)
                    val targetHasFiles = targetDir.exists() && targetDir.walkTopDown().any { it.isFile }
                    if (targetHasFiles) {
                        // Canonical directory already contains the model; delete the duplicate alias
                        dir.deleteRecursively()
                    } else {
                        // Target doesn't exist or is empty; remove empty target if needed and rename alias to canonical
                        if (targetDir.exists()) targetDir.deleteRecursively()
                        dir.renameTo(targetDir)
                    }
                }
            }

            // 4. In canonical model directories, eliminate duplicate root files that also exist in 0/
            // and ensure all model files reside in 0/
            modelsDir.listFiles()?.filter { it.isDirectory }?.forEach { modelDir ->
                val zeroDir = File(modelDir, "0")
                if (!zeroDir.exists()) {
                    zeroDir.mkdirs()
                }
                val rootFiles = modelDir.listFiles()?.filter { it.isFile } ?: emptyList()
                for (rFile in rootFiles) {
                    val zFile = File(zeroDir, rFile.name)
                    if (zFile.exists()) {
                        rFile.delete()
                    } else {
                        rFile.renameTo(zFile)
                    }
                }
            }

            val summary = modelsDir.listFiles()?.filter { it.isDirectory }?.joinToString(", ") { dir ->
                val sizeMb = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / (1024 * 1024)
                "${dir.name}: ${sizeMb}MB"
            } ?: "none"
            Log.i(TAG, "Storage cleanup complete. Remaining models in noBackup: $summary")
        } catch (e: Throwable) {
            Log.w(TAG, "Error cleaning up translation model storage bloat", e)
        }
    }

    fun getFilename(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        return cursor.getString(nameIndex)
                    }
                }
            } catch (_: Exception) {}
        }
        return uri.lastPathSegment
    }

    private val FN_MATCH_REGEX = Regex("""(?:dict\.|merged_dict_)?([a-z]{2,3})[_-]([a-z]{2,3})""")
    private val SINGLE_MATCH_REGEX = Regex("""^([a-z]{2,3})(?:[._-]model)?\.zip$""")
    private val INNER_MATCH_REGEX = Regex("""(?:dict\.|merged_dict_)([a-z]{2,3})_([a-z]{2,3})""")
    private val SINGLE_INNER_MATCH_REGEX = Regex("""(?:dict\.|merged_dict_)([a-z]{2,3}_[a-z]{2,3})""")

    fun detectLanguageCode(context: Context, uri: Uri): String? {
        val filename = getFilename(context, uri) ?: uri.lastPathSegment ?: ""
        val fnLower = filename.lowercase()
        val fnMatch = FN_MATCH_REGEX.find(fnLower)
        if (fnMatch != null) {
            val code1 = fnMatch.groupValues[1]
            val code2 = fnMatch.groupValues[2]
            if (code1 == "en") return code2
            if (code2 == "en") return code1
            return code1
        }
        val singleMatch = SINGLE_MATCH_REGEX.find(fnLower)
        if (singleMatch != null) {
            return singleMatch.groupValues[1]
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream.buffered()).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val name = entry.name.lowercase()
                        val match = INNER_MATCH_REGEX.find(name)
                        if (match != null) {
                            val code1 = match.groupValues[1]
                            val code2 = match.groupValues[2]
                            return if (code1 == "en") code2 else code1
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
        } catch (_: Throwable) {}
        return null
    }

    fun importForLanguageFromUri(context: Context, uri: Uri, targetLangCode: String): String? {
        migrateLegacyModels(context)
        return try {
            val filename = getFilename(context, uri) ?: ""
            context.contentResolver.openInputStream(uri)?.use { stream ->
                importForLanguageFromStream(context, stream, targetLangCode, filename)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to import translation model for $targetLangCode from URI: $uri", e)
            null
        }
    }

    fun importForLanguageFromStream(
        context: Context,
        inputStream: InputStream,
        targetLangCode: String,
        filenameHint: String = ""
    ): String? {
        val tempZip = File(context.cacheDir, "import_translation_model_${System.currentTimeMillis()}.zip")
        return try {
            FileOutputStream(tempZip).use { out ->
                inputStream.copyTo(out)
            }

            val modelName = TranslationModelUrls.getModelName(targetLangCode) ?: "${targetLangCode}_en"
            val baseDir = context.noBackupFilesDir ?: context.filesDir
            val targetDir = File(baseDir, "com.google.mlkit.translate.models/$modelName")
            val targetDirZero = File(targetDir, "0")
            targetDir.mkdirs()
            targetDirZero.mkdirs()

            var extractedAny = false
            try {
                ZipInputStream(tempZip.inputStream().buffered()).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val entryName = entry.name
                        val relPath = if (entryName.contains("/")) entryName.substringAfterLast("/") else entryName
                        if (relPath.isNotEmpty() && !entry.isDirectory) {
                            val outFileZero = File(targetDirZero, relPath)
                            outFileZero.parentFile?.mkdirs()
                            FileOutputStream(outFileZero).use { out ->
                                zipIn.copyTo(out)
                            }
                            extractedAny = true
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            } catch (_: Throwable) {
                extractedAny = false
            }

            if (!extractedAny) {
                val filename = if (filenameHint.isNotBlank()) filenameHint.substringAfterLast("/") else "model"
                val outFileZero = File(targetDirZero, filename)
                tempZip.copyTo(outFileZero, overwrite = true)
            }

            Log.i(TAG, "Successfully imported translation model $modelName into $targetDir and $targetDirZero")
            migrateLegacyModels(context)
            TranslationLoader.unloadPlugin()
            modelName
        } catch (e: Throwable) {
            Log.e(TAG, "Error extracting translation model for $targetLangCode", e)
            null
        } finally {
            tempZip.delete()
        }
    }

    fun importFromUri(context: Context, uri: Uri): String? {
        migrateLegacyModels(context)
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                importFromStream(context, stream)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to import translation model from URI: $uri", e)
            null
        }
    }

    fun importFromStream(context: Context, inputStream: InputStream): String? {
        val tempZip = File(context.cacheDir, "import_translation_model_${System.currentTimeMillis()}.zip")
        return try {
            FileOutputStream(tempZip).use { out ->
                inputStream.copyTo(out)
            }

            var detectedModelName: String? = null

            // Inspect zip entries to detect model name (e.g. dict.en_es_25 or merged_dict_en_es_25...)
            java.util.zip.ZipFile(tempZip).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    val match = SINGLE_INNER_MATCH_REGEX.find(name)
                    if (match != null) {
                        detectedModelName = match.groupValues[1]
                        break
                    }
                }
            }

            val modelName = detectedModelName
            if (modelName == null) {
                Log.e(TAG, "Could not detect translation model language pair from zip contents")
                return null
            }
            val baseDir = context.noBackupFilesDir ?: context.filesDir
            val targetDir = File(baseDir, "com.google.mlkit.translate.models/$modelName")
            val targetDirZero = File(targetDir, "0")
            targetDir.mkdirs()
            targetDirZero.mkdirs()

            ZipInputStream(tempZip.inputStream().buffered()).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    val relPath = if (entryName.contains("/")) entryName.substringAfterLast("/") else entryName
                    if (relPath.isNotEmpty() && !entry.isDirectory) {
                        val outFileZero = File(targetDirZero, relPath)
                        outFileZero.parentFile?.mkdirs()
                        FileOutputStream(outFileZero).use { out ->
                            zipIn.copyTo(out)
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            Log.i(TAG, "Successfully imported translation model $modelName into $targetDirZero")
            migrateLegacyModels(context)
            TranslationLoader.unloadPlugin()
            modelName
        } catch (e: Throwable) {
            Log.e(TAG, "Error extracting translation model zip", e)
            null
        } finally {
            tempZip.delete()
        }
    }
}
