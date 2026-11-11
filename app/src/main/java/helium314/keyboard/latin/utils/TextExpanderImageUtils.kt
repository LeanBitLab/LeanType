/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.utils

import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.R
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object TextExpanderImageUtils {
    sealed class SaveImageResult {
        data class Success(val reference: String) : SaveImageResult()
        data object TooLarge : SaveImageResult()
        data object InvalidOrUnreadable : SaveImageResult()
    }

    private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024 // 20 MB raw import limit
    private const val MAX_NORMALIZED_BYTES = 3 * 1024 * 1024 // 3 MB target cap for reliable commitContent
    private const val MAX_NORMALIZED_DIMENSION = 2048
    private const val COMPAT_MAX_AGE_MS = 24L * 60 * 60 * 1000L
    private const val CACHE_SIZE = 40
    internal const val IMAGES_DIR_NAME = "text_expander_images"
    internal const val COMPAT_DIR_NAME = "text_expander_compat"
    internal const val ORPHAN_GRACE_PERIOD_MS = 5 * 60 * 1000L

    private val thumbnailCache = LruCache<String, Drawable>(CACHE_SIZE)
    private val bitmapCache = LruCache<String, Bitmap>(CACHE_SIZE)
    private val inFlightThumbnails = ConcurrentHashMap<String, MutableList<(Drawable?) -> Unit>>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getImagesDir(context: Context): File {
        return File(context.filesDir, IMAGES_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Resolves an image reference to an existing local File when possible.
     * Supports direct paths, file:// URIs, and managed filenames migrated across package flavors or backups.
     */
    fun resolveImageFile(context: Context, pathOrUri: String?): File? {
        if (pathOrUri.isNullOrBlank() || pathOrUri.startsWith("content://")) return null
        return try {
            val rawPath = if (pathOrUri.startsWith("file://")) {
                Uri.parse(pathOrUri).path ?: return null
            } else {
                pathOrUri
            }
            val direct = File(rawPath)
            if (direct.exists() && direct.isFile) return direct

            val normalized = rawPath.replace('\\', '/')
            val name = direct.name
            val looksManaged = normalized.contains("/$IMAGES_DIR_NAME/") || !normalized.contains('/')
            if (looksManaged && name.isNotEmpty() && name != "." && name != "..") {
                val managedDir = getImagesDir(context)
                val fallback = File(managedDir, name)
                if (fallback.exists() && fallback.isFile && fallback.parentFile?.canonicalPath == managedDir.canonicalPath) {
                    return fallback
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns true if the referenced local file or content URI currently exists and can be opened.
     */
    fun imageExists(context: Context, pathOrUri: String?): Boolean {
        if (pathOrUri.isNullOrBlank()) return false
        if (resolveImageFile(context, pathOrUri) != null) return true
        if (!pathOrUri.startsWith("content://")) return false
        return try {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(pathOrUri), "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Copies selected image into local internal app storage for offline availability and persistence.
     * Normalizes orientation/format (e.g., EXIF rotation, HEIC/AVIF, oversized camera photos) into
     * portable managed filenames suitable for cross-app commitContent.
     */
    fun saveImageWithResult(context: Context, sourceUri: Uri): SaveImageResult {
        var tempRawFile: File? = null
        try {
            val resolver = context.contentResolver
            val dir = getImagesDir(context)
            tempRawFile = File.createTempFile("pending_", ".tmp", dir)

            val header = ByteArray(32)
            var headerLen = 0
            var totalBytes = 0L
            var exceedsLimit = false

            val opened = resolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempRawFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (headerLen < header.size) {
                            val toCopy = minOf(bytesRead, header.size - headerLen)
                            System.arraycopy(buffer, 0, header, headerLen, toCopy)
                            headerLen += toCopy
                        }
                        totalBytes += bytesRead
                        if (totalBytes > MAX_IMAGE_BYTES) {
                            exceedsLimit = true
                            return@use false
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                    true
                }
            } ?: false

            if (exceedsLimit) {
                return SaveImageResult.TooLarge
            }
            if (!opened || headerLen < 3) {
                return SaveImageResult.InvalidOrUnreadable
            }

            val sniffedExt = sniffExtension(header.copyOf(headerLen), resolver.getType(sourceUri))
            if (sniffedExt == ".gif") {
                val destFile = newManagedFile(dir, ".gif")
                if (tempRawFile.renameTo(destFile)) {
                    tempRawFile = null
                    return SaveImageResult.Success(destFile.name)
                }
                return SaveImageResult.InvalidOrUnreadable
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tempRawFile.absolutePath, bounds)
            val orientation = readExifOrientation(tempRawFile.absolutePath)
            val hasGps = hasExifGpsTags(tempRawFile.absolutePath)
            val needsRotation = orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            val isStandardExt = sniffedExt in setOf(".jpg", ".png", ".webp")
            val needsNormalize = !isStandardExt
                || needsRotation
                || hasGps
                || bounds.outWidth <= 0
                || bounds.outHeight <= 0
                || maxDim > MAX_NORMALIZED_DIMENSION
                || tempRawFile.length() > MAX_NORMALIZED_BYTES

            if (!needsNormalize) {
                val destFile = newManagedFile(dir, sniffedExt)
                if (tempRawFile.renameTo(destFile)) {
                    tempRawFile = null
                    return SaveImageResult.Success(destFile.name)
                }
                return SaveImageResult.InvalidOrUnreadable
            }

            val decoded = decodeUprightBitmapForExport(tempRawFile, MAX_NORMALIZED_DIMENSION, orientation)
            if (decoded == null) {
                if (isStandardExt && bounds.outWidth > 0 && bounds.outHeight > 0 && tempRawFile.length() <= MAX_IMAGE_BYTES) {
                    val fallbackFile = newManagedFile(dir, sniffedExt)
                    if (tempRawFile.renameTo(fallbackFile)) {
                        tempRawFile = null
                        return SaveImageResult.Success(fallbackFile.name)
                    }
                }
                return SaveImageResult.InvalidOrUnreadable
            }

            try {
                val outExt = if (decoded.hasAlpha()) ".png" else ".jpg"
                val destFile = newManagedFile(dir, outExt)
                val wrote = encodeBitmapToFile(decoded, outExt, destFile)
                if (!wrote) {
                    destFile.delete()
                    return SaveImageResult.InvalidOrUnreadable
                }
                if (destFile.length() > MAX_IMAGE_BYTES) {
                    destFile.delete()
                    return SaveImageResult.TooLarge
                }
                return SaveImageResult.Success(destFile.name)
            } finally {
                decoded.recycle()
            }
        } catch (_: Exception) {
            return SaveImageResult.InvalidOrUnreadable
        } finally {
            tempRawFile?.delete()
        }
    }

    /**
     * Legacy convenience wrapper returning the managed image reference or null on failure.
     */
    fun saveImageToStorage(context: Context, sourceUri: Uri): String? {
        return (saveImageWithResult(context, sourceUri) as? SaveImageResult.Success)?.reference
    }

    private fun newManagedFile(dir: File, ext: String): File {
        return File(dir, "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}$ext")
    }

    private fun encodeBitmapToFile(bitmap: Bitmap, ext: String, destFile: File): Boolean {
        if (ext == ".png") {
            FileOutputStream(destFile).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return false
                out.flush()
            }
            return true
        }
        FileOutputStream(destFile).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) return false
            out.flush()
        }
        if (destFile.length() > MAX_NORMALIZED_BYTES) {
            FileOutputStream(destFile).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)) return false
                out.flush()
            }
        }
        return true
    }

    /**
     * Determines the file extension from file header magic bytes or fallback MIME type.
     */
    internal fun sniffExtension(header: ByteArray, mimeType: String?): String {
        if (header.size >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
            return ".jpg"
        }
        if (header.size >= 8 && header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()) {
            return ".png"
        }
        if (header.size >= 12 && header[0] == 0x52.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte() && header[3] == 0x46.toByte()
            && header[8] == 0x57.toByte() && header[9] == 0x45.toByte() && header[10] == 0x42.toByte() && header[11] == 0x50.toByte()) {
            return ".webp"
        }
        if (header.size >= 3 && header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte()) {
            return ".gif"
        }
        if (header.size >= 2 && header[0] == 0x42.toByte() && header[1] == 0x4D.toByte()) {
            return ".bmp"
        }
        if (header.size >= 12 && header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte()
            && header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()) {
            val brand = String(header, 8, 4, Charsets.US_ASCII)
            when (brand) {
                "avif", "avis" -> return ".avif"
                "heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1" -> return ".heic"
            }
        }
        return when (mimeType?.lowercase()) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/gif" -> ".gif"
            "image/heic", "image/heif" -> ".heic"
            "image/avif" -> ".avif"
            "image/bmp", "image/x-ms-bmp" -> ".bmp"
            else -> ".jpg"
        }
    }

    private fun evictCachesFor(path: String, canonicalPath: String? = null, fileName: String? = null) {
        val matchesKey: (String) -> Boolean = { key ->
            val keyPath = key.substringBeforeLast('_').replace('\\', '/')
            val normalizedPath = path.replace('\\', '/')
            val normalizedCanonical = canonicalPath?.replace('\\', '/')
            keyPath == normalizedPath
                || (normalizedCanonical != null && keyPath == normalizedCanonical)
                || (fileName != null && (keyPath == fileName || keyPath.endsWith("/$fileName")))
        }
        thumbnailCache.snapshot().keys.filter(matchesKey).forEach { thumbnailCache.remove(it) }
        bitmapCache.snapshot().keys.filter(matchesKey).forEach { bitmapCache.remove(it) }
    }

    private fun readExifOrientation(filePath: String): Int {
        return runCatching {
            ExifInterface(filePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private fun hasExifGpsTags(filePath: String): Boolean {
        return runCatching {
            val exif = ExifInterface(filePath)
            exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null ||
                exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE) != null
        }.getOrDefault(false)
    }

    private fun transformBitmapForExif(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed != bitmap) {
            bitmap.recycle()
        }
        return transformed
    }

    private fun decodeUprightBitmapForExport(file: File, maxDimension: Int, orientation: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decoded = runCatching {
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width
                    val h = info.size.height
                    if (w > maxDimension || h > maxDimension) {
                        val scale = maxDimension.toFloat() / maxOf(w, h).toFloat()
                        decoder.setTargetSize(
                            (w * scale).toInt().coerceAtLeast(1),
                            (h * scale).toInt().coerceAtLeast(1)
                        )
                    }
                }
            }.getOrNull()
            if (decoded != null) return decoded
        }

        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val srcMax = maxOf(bounds.outWidth, bounds.outHeight)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (srcMax / (sampleSize * 2) >= maxDimension) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
            val upright = transformBitmapForExif(raw, orientation)
            val curMax = maxOf(upright.width, upright.height)
            if (curMax <= maxDimension) {
                upright
            } else {
                val scale = maxDimension.toFloat() / curMax.toFloat()
                val scaled = Bitmap.createScaledBitmap(
                    upright,
                    (upright.width * scale).toInt().coerceAtLeast(1),
                    (upright.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled != upright) upright.recycle()
                scaled
            }
        }.getOrNull()
    }

    /**
     * Converts a local static image into a format accepted by the target editor when the editor
     * declares explicit MIME restrictions (e.g., WebP shortcut into a PNG/JPEG-only editor).
     */
    fun transcodeForEditorCompat(
        context: Context,
        sourceFile: File,
        currentMime: String,
        acceptedMimes: Array<String>
    ): Pair<File, String>? {
        if (acceptedMimes.isEmpty() || acceptedMimes.any { ClipDescription.compareMimeTypes(currentMime, it) }) {
            return null
        }
        val acceptsPng = acceptedMimes.any { ClipDescription.compareMimeTypes("image/png", it) }
        val acceptsJpg = acceptedMimes.any { ClipDescription.compareMimeTypes("image/jpeg", it) }
        if (!acceptsPng && !acceptsJpg) return null

        val orientation = readExifOrientation(sourceFile.absolutePath)
        val decoded = decodeUprightBitmapForExport(sourceFile, MAX_NORMALIZED_DIMENSION, orientation) ?: return null
        return try {
            val targetExt = if (acceptsPng && (decoded.hasAlpha() || !acceptsJpg)) ".png" else ".jpg"
            val targetMime = if (targetExt == ".png") "image/png" else "image/jpeg"
            val compatDir = File(context.cacheDir, COMPAT_DIR_NAME).apply { if (!exists()) mkdirs() }
            val now = System.currentTimeMillis()
            compatDir.listFiles()?.forEach { old ->
                if (old.isFile && now - old.lastModified() > COMPAT_MAX_AGE_MS) old.delete()
            }
            val compatFile = File(compatDir, "compat_${sourceFile.nameWithoutExtension}_${sourceFile.lastModified()}$targetExt")
            if (compatFile.exists() && compatFile.length() > 0L) {
                return compatFile to targetMime
            }
            val tempCompat = File.createTempFile("compat_", targetExt, compatDir)
            if (encodeBitmapToFile(decoded, targetExt, tempCompat) && tempCompat.renameTo(compatFile)) {
                compatFile to targetMime
            } else {
                tempCompat.delete()
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            decoded.recycle()
        }
    }

    private fun collectReferencedEntries(
        context: Context,
        shortcuts: Map<String, TextExpanderUtils.ShortcutEntry>,
        includePreviousBackup: Boolean
    ): Pair<Set<String>, Set<String>> {
        val referencedPaths = HashSet<String>()
        val referencedNames = HashSet<String>()

        fun addFromMap(map: Map<String, TextExpanderUtils.ShortcutEntry>) {
            for (entry in map.values) {
                val ref = entry.imageUri?.takeIf { it.isNotBlank() } ?: continue
                referencedPaths.add(ref)
                resolveImageFile(context, ref)?.let { resolved ->
                    referencedPaths.add(resolved.absolutePath)
                    runCatching { resolved.canonicalPath }.getOrNull()?.let { referencedPaths.add(it) }
                    referencedNames.add(resolved.name)
                }
                if (!ref.startsWith("content://")) {
                    val rawPath = if (ref.startsWith("file://")) Uri.parse(ref).path else ref
                    if (!rawPath.isNullOrBlank()) {
                        referencedPaths.add(rawPath)
                        val refFile = File(rawPath)
                        runCatching { refFile.canonicalPath }.getOrNull()?.let { referencedPaths.add(it) }
                        if (refFile.name.isNotEmpty()) {
                            referencedNames.add(refFile.name)
                        }
                    }
                }
            }
        }

        addFromMap(shortcuts)
        if (includePreviousBackup) {
            val prevJson = context.prefs().getString(TextExpanderUtils.PREF_DATA_PREVIOUS, null)
            if (!prevJson.isNullOrBlank()) {
                addFromMap(TextExpanderUtils.parseShortcutsJson(prevJson))
            }
        }
        return referencedPaths to referencedNames
    }

    /**
     * Returns true if any active shortcut (or bulk editor backup snapshot) still references this image.
     */
    fun isImageReferenced(
        context: Context,
        imagePath: String?,
        activeShortcuts: Map<String, TextExpanderUtils.ShortcutEntry> = TextExpanderUtils.getShortcuts(context),
        includePreviousBackup: Boolean = true
    ): Boolean {
        if (imagePath.isNullOrBlank()) return false
        val (referencedPaths, referencedNames) = collectReferencedEntries(context, activeShortcuts, includePreviousBackup)
        val file = resolveImageFile(context, imagePath)
        if (referencedPaths.contains(imagePath)) return true
        if (file != null) {
            if (referencedPaths.contains(file.absolutePath) || referencedNames.contains(file.name)) return true
            val canonical = runCatching { file.canonicalPath }.getOrNull()
            if (canonical != null && referencedPaths.contains(canonical)) return true
        }
        val name = File(imagePath).name
        return name.isNotEmpty() && referencedNames.contains(name)
    }

    /**
     * Deletes a managed image only if no active shortcut (or undo snapshot) still references it.
     */
    fun deleteImageIfUnreferenced(
        context: Context,
        imagePath: String?,
        activeShortcuts: Map<String, TextExpanderUtils.ShortcutEntry> = TextExpanderUtils.getShortcuts(context)
    ) {
        if (isImageReferenced(context, imagePath, activeShortcuts, includePreviousBackup = true)) return
        deleteImageIfManaged(context, imagePath)
    }

    /**
     * Safely deletes managed image file inside text_expander_images folder.
     */
    fun deleteImageIfManaged(context: Context, imagePath: String?) {
        if (imagePath.isNullOrBlank() || imagePath.startsWith("content://")) return
        try {
            val file = resolveImageFile(context, imagePath) ?: File(imagePath)
            val managedDir = getImagesDir(context)
            val fileCanonical = file.canonicalPath
            if (file.parentFile?.canonicalPath == managedDir.canonicalPath) {
                file.delete()
                evictCachesFor(imagePath, fileCanonical, file.name)
            }
        } catch (_: Exception) {}
    }

    /**
     * Cleans up unreferenced image files inside text_expander_images folder after a grace period
     * so pending attachments in an open editor dialog are not deleted if the IME restarts before Save.
     */
    fun cleanUpOrphanedImages(context: Context, nowMs: Long = System.currentTimeMillis()) {
        try {
            val dir = getImagesDir(context)
            if (!dir.exists()) return
            val shortcuts = TextExpanderUtils.getShortcuts(context)
            val (referencedPaths, referencedNames) = collectReferencedEntries(
                context, shortcuts, includePreviousBackup = true
            )

            dir.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                val canonical = runCatching { file.canonicalPath }.getOrNull()
                val isReferenced = referencedPaths.contains(file.absolutePath)
                    || (canonical != null && referencedPaths.contains(canonical))
                    || referencedNames.contains(file.name)
                if (isReferenced) return@forEach

                // Respect grace period so newly picked unsaved images survive service restarts.
                if (nowMs - file.lastModified() < ORPHAN_GRACE_PERIOD_MS) return@forEach

                if (file.delete()) {
                    evictCachesFor(file.absolutePath, canonical, file.name)
                }
            }
        } catch (_: Exception) {}
    }

    private fun decodeSampledBitmap(context: Context, pathOrUri: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val isContentUri = pathOrUri.startsWith("content://")
            val file = if (isContentUri) null else resolveImageFile(context, pathOrUri) ?: return null
            val contentUri = if (isContentUri) Uri.parse(pathOrUri) else null

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (file != null) {
                BitmapFactory.decodeFile(file.absolutePath, options)
            } else {
                context.contentResolver.openInputStream(contentUri!!)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            }

            val (srcW, srcH) = options.outWidth to options.outHeight
            if (srcW <= 0 || srcH <= 0) return null

            var sampleSize = 1
            while (srcW / (sampleSize * 2) >= reqWidth && srcH / (sampleSize * 2) >= reqHeight) {
                sampleSize *= 2
            }

            val isJpeg = options.outMimeType == "image/jpeg"
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = if (isJpeg) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            }

            val decoded = if (file != null) {
                BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            } else {
                context.contentResolver.openInputStream(contentUri!!)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOptions)
                }
            } ?: return null

            if (file != null && options.outMimeType != "image/gif") {
                val orientation = readExifOrientation(file.absolutePath)
                transformBitmapForExif(decoded, orientation)
            } else {
                decoded
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Loads a memory-efficient downsampled thumbnail Bitmap.
     */
    fun loadThumbnailBitmap(context: Context, pathOrUri: String, reqWidth: Int, reqHeight: Int = reqWidth): Bitmap? {
        val cacheKey = "${pathOrUri}_${reqWidth}x${reqHeight}"
        bitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return cached
            bitmapCache.remove(cacheKey)
        }
        val bitmap = decodeSampledBitmap(context, pathOrUri, reqWidth, reqHeight)
        if (bitmap != null) {
            bitmapCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    fun getCachedThumbnailDrawable(
        context: Context,
        pathOrUri: String,
        widthPx: Int = 40.dpToPx(context.resources),
        heightPx: Int = 26.dpToPx(context.resources)
    ): Drawable? {
        val cacheKey = "${pathOrUri}_${widthPx}x${heightPx}"
        thumbnailCache.get(cacheKey)?.let { cached ->
            val bmp = (cached as? BitmapDrawable)?.bitmap
            if (bmp == null || !bmp.isRecycled) return cached
            thumbnailCache.remove(cacheKey)
        }
        return null
    }

    fun getPlaceholderDrawable(
        context: Context,
        widthPx: Int = 40.dpToPx(context.resources),
        heightPx: Int = 26.dpToPx(context.resources)
    ): Drawable? {
        return ContextCompat.getDrawable(context, R.drawable.ic_image)?.mutate()?.apply {
            setBounds(0, 0, widthPx, heightPx)
        }
    }

    /**
     * Decodes a rounded thumbnail asynchronously when missing from memory cache to keep typing smooth.
     */
    fun preloadThumbnailAsync(
        context: Context,
        pathOrUri: String,
        widthPx: Int = 40.dpToPx(context.resources),
        heightPx: Int = 26.dpToPx(context.resources),
        onLoaded: ((Drawable?) -> Unit)? = null
    ) {
        getCachedThumbnailDrawable(context, pathOrUri, widthPx, heightPx)?.let { cached ->
            onLoaded?.invoke(cached)
            return
        }
        val cacheKey = "${pathOrUri}_${widthPx}x${heightPx}"
        val shouldStart: Boolean
        synchronized(inFlightThumbnails) {
            val existing = inFlightThumbnails[cacheKey]
            if (existing != null) {
                if (onLoaded != null) existing.add(onLoaded)
                shouldStart = false
            } else {
                val list = mutableListOf<(Drawable?) -> Unit>()
                if (onLoaded != null) list.add(onLoaded)
                inFlightThumbnails[cacheKey] = list
                shouldStart = true
            }
        }
        if (!shouldStart) return

        val appContext = context.applicationContext ?: context
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            var decoded: Drawable? = null
            try {
                decoded = renderRoundedThumbnailDrawable(appContext, pathOrUri, widthPx, heightPx)
            } finally {
                val listeners = synchronized(inFlightThumbnails) {
                    inFlightThumbnails.remove(cacheKey)
                }
                if (!listeners.isNullOrEmpty()) {
                    mainHandler.post {
                        for (listener in listeners) {
                            listener(decoded)
                        }
                    }
                }
            }
        }
    }

    private fun renderRoundedThumbnailDrawable(
        context: Context,
        pathOrUri: String,
        widthPx: Int,
        heightPx: Int
    ): Drawable? {
        val cacheKey = "${pathOrUri}_${widthPx}x${heightPx}"
        getCachedThumbnailDrawable(context, pathOrUri, widthPx, heightPx)?.let { return it }

        val targetDecodeW = widthPx * 2
        val targetDecodeH = heightPx * 2
        // Decode uncached so intermediate crop/scale recycling never mutates bitmaps in bitmapCache.
        val rawBitmap = decodeSampledBitmap(context, pathOrUri, targetDecodeW, targetDecodeH) ?: return null

        val srcW = rawBitmap.width
        val srcH = rawBitmap.height
        val targetRatio = widthPx.toFloat() / heightPx.toFloat()
        val srcRatio = srcW.toFloat() / srcH.toFloat()

        val cropW = if (srcRatio > targetRatio) (srcH * targetRatio).toInt().coerceAtMost(srcW) else srcW
        val cropH = if (srcRatio > targetRatio) srcH else (srcW / targetRatio).toInt().coerceAtMost(srcH)
        val xOffset = ((srcW - cropW) / 2).coerceAtLeast(0)
        val yOffset = ((srcH - cropH) / 2).coerceAtLeast(0)

        val croppedBitmap = if (cropW == srcW && cropH == srcH) {
            rawBitmap
        } else {
            val sq = Bitmap.createBitmap(rawBitmap, xOffset, yOffset, cropW, cropH)
            if (rawBitmap != sq) rawBitmap.recycle()
            sq
        }
        val scaledBitmap = if (croppedBitmap.width == widthPx && croppedBitmap.height == heightPx) {
            croppedBitmap
        } else {
            val sc = Bitmap.createScaledBitmap(croppedBitmap, widthPx, heightPx, true)
            if (croppedBitmap != sc) croppedBitmap.recycle()
            sc
        }

        val output = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val rect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        val cornerRadius = 8.dpToPx(context.resources).toFloat()

        val shader = BitmapShader(scaledBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        paint.shader = shader
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.dpToPx(context.resources).toFloat().coerceAtLeast(1f)
            color = Color.argb(45, 128, 128, 128)
        }
        val inset = borderPaint.strokeWidth / 2f
        val borderRect = RectF(inset, inset, widthPx.toFloat() - inset, heightPx.toFloat() - inset)
        canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)

        if (scaledBitmap != output) {
            scaledBitmap.recycle()
        }

        val drawable = BitmapDrawable(context.resources, output).apply {
            setBounds(0, 0, widthPx, heightPx)
        }
        thumbnailCache.put(cacheKey, drawable)
        return drawable
    }

    /**
     * Gets a cached rounded thumbnail Drawable for display in the suggestion strip or settings.
     * Accurately center-crops to square, scales to exact pixel dimensions, and applies smooth rounded corners.
     */
    fun getThumbnailDrawable(
        context: Context,
        pathOrUri: String,
        widthPx: Int = 40.dpToPx(context.resources),
        heightPx: Int = 26.dpToPx(context.resources)
    ): Drawable? {
        return renderRoundedThumbnailDrawable(context, pathOrUri, widthPx, heightPx)
            ?: getPlaceholderDrawable(context, widthPx, heightPx)
    }

    /**
     * Resolves the MIME type for an image path or URI.
     */
    fun getMimeType(filePathOrUri: String): String {
        val lower = filePathOrUri.lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            else -> "image/jpeg"
        }
    }

    fun isImageShortcut(entry: TextExpanderUtils.ShortcutEntry): Boolean {
        return !entry.imageUri.isNullOrBlank()
    }

    fun clearCache() {
        thumbnailCache.evictAll()
        bitmapCache.evictAll()
    }
}
