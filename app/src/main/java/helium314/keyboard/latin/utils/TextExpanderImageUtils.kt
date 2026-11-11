/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.R
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object TextExpanderImageUtils {
    private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024 // 20 MB limit
    private const val CACHE_SIZE = 40
    internal const val IMAGES_DIR_NAME = "text_expander_images"
    internal const val ORPHAN_GRACE_PERIOD_MS = 5 * 60 * 1000L

    private val thumbnailCache = LruCache<String, Drawable>(CACHE_SIZE)
    private val bitmapCache = LruCache<String, Bitmap>(CACHE_SIZE)

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
     * Streams once into a temp file while sniffing magic header bytes to preserve the exact image format.
     */
    fun saveImageToStorage(context: Context, sourceUri: Uri): String? {
        var tempFile: File? = null
        return try {
            val resolver = context.contentResolver
            val dir = getImagesDir(context)
            tempFile = File.createTempFile("pending_", ".tmp", dir)

            val header = ByteArray(16)
            var headerLen = 0
            var totalBytes = 0L

            val copied = resolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
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
                            return@use false
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                    true
                }
            } ?: false

            if (!copied || headerLen < 3) {
                tempFile.delete()
                return null
            }

            val ext = sniffExtension(header, resolver.getType(sourceUri))
            val destFile = File(dir, "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}$ext")
            if (!tempFile.renameTo(destFile)) {
                tempFile.delete()
                return null
            }
            destFile.absolutePath
        } catch (_: Exception) {
            tempFile?.delete()
            null
        }
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
        return when (mimeType) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/gif" -> ".gif"
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
            val referencedPaths = HashSet<String>()
            val referencedNames = HashSet<String>()

            for (entry in shortcuts.values) {
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

            if (file != null) {
                BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            } else {
                context.contentResolver.openInputStream(contentUri!!)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOptions)
                }
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
        val cacheKey = "${pathOrUri}_${widthPx}x${heightPx}"
        thumbnailCache.get(cacheKey)?.let { cached ->
            val bmp = (cached as? BitmapDrawable)?.bitmap
            if (bmp == null || !bmp.isRecycled) return cached
            thumbnailCache.remove(cacheKey)
        }

        val targetDecodeW = widthPx * 2
        val targetDecodeH = heightPx * 2
        // Decode uncached so intermediate crop/scale recycling never mutates bitmaps in bitmapCache.
        val rawBitmap = decodeSampledBitmap(context, pathOrUri, targetDecodeW, targetDecodeH)
        if (rawBitmap != null) {
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

        return ContextCompat.getDrawable(context, R.drawable.ic_image)?.mutate()?.apply {
            setBounds(0, 0, widthPx, heightPx)
        }
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
