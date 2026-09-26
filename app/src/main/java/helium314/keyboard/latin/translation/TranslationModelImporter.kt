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
            val baseDirs = listOfNotNull(context.noBackupFilesDir, context.filesDir).distinct()

            // 1. Gather all directories containing model files across both baseDirs
            val foundModelNames = mutableSetOf<String>()
            for (baseDir in baseDirs) {
                val modelsDir = File(baseDir, "com.google.mlkit.translate.models")
                if (!modelsDir.exists() || !modelsDir.isDirectory) continue
                modelsDir.listFiles()?.forEach { modelDir ->
                    if (modelDir.isDirectory && modelDir.name != "0") {
                        val hasRootFiles = modelDir.listFiles()?.any { it.isFile && it.length() > 0 } == true
                        val zeroDir = File(modelDir, "0")
                        val hasZeroFiles = zeroDir.exists() && zeroDir.isDirectory &&
                            zeroDir.listFiles()?.any { it.isFile && it.length() > 0 } == true
                        if (hasRootFiles || hasZeroFiles) {
                            foundModelNames.add(modelDir.name)
                        }
                    }
                }
            }

            // 2. Synchronize all aliases and directories
            for (modelName in foundModelNames) {
                val aliases = mutableSetOf(modelName)
                if (modelName.contains("_")) {
                    val parts = modelName.split("_")
                    if (parts.size == 2) {
                        aliases.add("${parts[1]}_${parts[0]}")
                        aliases.add(parts[0])
                        aliases.add(parts[1])
                    }
                } else {
                    val mapped = TranslationModelUrls.getModelName(modelName)
                    if (mapped != null) {
                        aliases.add(mapped)
                        val parts = mapped.split("_")
                        if (parts.size == 2) aliases.add("${parts[1]}_${parts[0]}")
                    }
                    aliases.add("${modelName}_en")
                    aliases.add("en_${modelName}")
                }

                var sourceDir: File? = null
                for (baseDir in baseDirs) {
                    val dir = File(baseDir, "com.google.mlkit.translate.models/$modelName")
                    if (dir.exists() && (dir.listFiles()?.any { it.isFile } == true || File(dir, "0").listFiles()?.any { it.isFile } == true)) {
                        sourceDir = dir
                        break
                    }
                }
                if (sourceDir == null) continue

                val sourceFiles = (sourceDir.listFiles()?.filter { it.isFile } ?: emptyList()) +
                    (File(sourceDir, "0").listFiles()?.filter { it.isFile } ?: emptyList())
                val distinctFiles = sourceFiles.distinctBy { it.name }

                for (baseDir in baseDirs) {
                    for (alias in aliases) {
                        val aliasDir = File(baseDir, "com.google.mlkit.translate.models/$alias")
                        val aliasZero = File(aliasDir, "0")
                        if (!aliasDir.exists()) aliasDir.mkdirs()
                        if (!aliasZero.exists()) aliasZero.mkdirs()

                        for (srcFile in distinctFiles) {
                            val destRoot = File(aliasDir, srcFile.name)
                            val destZero = File(aliasZero, srcFile.name)
                            syncModelFile(srcFile, destRoot)
                            syncModelFile(srcFile, destZero)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error synchronizing translation model folders", e)
        }
    }

    private fun syncModelFile(src: File, dest: File) {
        if (!dest.exists() || dest.length() != src.length()) {
            try {
                if (dest.exists()) dest.delete()
                android.system.Os.link(src.absolutePath, dest.absolutePath)
            } catch (_: Throwable) {
                try {
                    src.copyTo(dest, overwrite = true)
                } catch (_: Throwable) {}
            }
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

    fun detectLanguageCode(context: Context, uri: Uri): String? {
        val filename = getFilename(context, uri) ?: uri.lastPathSegment ?: ""
        val fnLower = filename.lowercase()
        val fnMatch = Regex("""(?:dict\.|merged_dict_)?([a-z]{2,3})[_-]([a-z]{2,3})""").find(fnLower)
        if (fnMatch != null) {
            val code1 = fnMatch.groupValues[1]
            val code2 = fnMatch.groupValues[2]
            if (code1 == "en") return code2
            if (code2 == "en") return code1
            return code1
        }
        val singleMatch = Regex("""^([a-z]{2,3})(?:[._-]model)?\.zip$""").find(fnLower)
        if (singleMatch != null) {
            return singleMatch.groupValues[1]
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream.buffered()).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val name = entry.name.lowercase()
                        val match = Regex("""(?:dict\.|merged_dict_)([a-z]{2,3})_([a-z]{2,3})""").find(name)
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
                            val outFile = File(targetDir, relPath)
                            val outFileZero = File(targetDirZero, relPath)
                            outFile.parentFile?.mkdirs()
                            outFileZero.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                zipIn.copyTo(out)
                            }
                            outFile.copyTo(outFileZero, overwrite = true)
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
                val outFile = File(targetDir, filename)
                val outFileZero = File(targetDirZero, filename)
                tempZip.copyTo(outFile, overwrite = true)
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
                    val match = Regex("""(?:dict\.|merged_dict_)([a-z]{2,3}_[a-z]{2,3})""").find(name)
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
                        val outFile = File(targetDir, relPath)
                        val outFileZero = File(targetDirZero, relPath)
                        outFile.parentFile?.mkdirs()
                        outFileZero.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            zipIn.copyTo(out)
                        }
                        outFile.copyTo(outFileZero, overwrite = true)
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            Log.i(TAG, "Successfully imported translation model $modelName into $targetDir and $targetDirZero")
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
