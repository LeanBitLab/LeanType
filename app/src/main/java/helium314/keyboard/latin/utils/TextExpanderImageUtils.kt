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

    private val thumbnailCache = LruCache<String, Drawable>(CACHE_SIZE)

    fun getImagesDir(context: Context): File {
        return File(context.filesDir, "text_expander_images").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Copies selected image into local internal app storage for offline availability and persistence.
     * Sniffs the file magic header to preserve the exact image format.
     */
    fun saveImageToStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val resolver = context.contentResolver
            val dir = getImagesDir(context)

            val header = ByteArray(16)
            resolver.openInputStream(sourceUri)?.use { input ->
                val read = input.read(header)
                if (read < 3) return null
            } ?: return null

            val ext = sniffExtension(header, resolver.getType(sourceUri))
            val destFile = File(dir, "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}$ext")

            var totalBytes = 0L
            resolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        totalBytes += bytesRead
                        if (totalBytes > MAX_IMAGE_BYTES) {
                            destFile.delete()
                            return null
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            } ?: return null

            destFile.absolutePath
        } catch (_: Exception) {
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

    /**
     * Safely deletes managed image file inside text_expander_images folder.
     */
    fun deleteImageIfManaged(context: Context, imagePath: String?) {
        if (imagePath.isNullOrBlank() || imagePath.startsWith("content://")) return
        try {
            val file = File(imagePath)
            val managedDir = getImagesDir(context)
            if (file.parentFile?.canonicalPath == managedDir.canonicalPath) {
                file.delete()
                thumbnailCache.remove(imagePath)
            }
        } catch (_: Exception) {}
    }

    /**
     * Loads a memory-efficient downsampled thumbnail Bitmap.
     */
    fun loadThumbnailBitmap(context: Context, pathOrUri: String, maxDimPx: Int): Bitmap? {
        return try {
            val file = if (pathOrUri.startsWith("content://")) null else File(pathOrUri)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }

            if (file != null && file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath, options)
            } else {
                val uri = Uri.parse(pathOrUri)
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            }

            val (srcW, srcH) = options.outWidth to options.outHeight
            if (srcW <= 0 || srcH <= 0) return null

            var sampleSize = 1
            while (srcW / (sampleSize * 2) >= maxDimPx && srcH / (sampleSize * 2) >= maxDimPx) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            if (file != null && file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            } else {
                val uri = Uri.parse(pathOrUri)
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOptions)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Gets a cached rounded thumbnail Drawable for display in the suggestion strip or settings.
     * Accurately center-crops to square, scales to exact pixel dimensions, and applies smooth rounded corners.
     */
    fun getThumbnailDrawable(context: Context, pathOrUri: String, sizePx: Int = 18.dpToPx(context.resources)): Drawable? {
        val cacheKey = "${pathOrUri}_$sizePx"
        thumbnailCache.get(cacheKey)?.let { return it }

        val targetDecodeDim = sizePx * 4
        val rawBitmap = loadThumbnailBitmap(context, pathOrUri, targetDecodeDim)
        if (rawBitmap != null) {
            val srcW = rawBitmap.width
            val srcH = rawBitmap.height
            val minEdge = minOf(srcW, srcH)
            val xOffset = (srcW - minEdge) / 2
            val yOffset = (srcH - minEdge) / 2
            val squareBitmap = if (srcW == minEdge && srcH == minEdge) {
                rawBitmap
            } else {
                val sq = Bitmap.createBitmap(rawBitmap, xOffset, yOffset, minEdge, minEdge)
                if (rawBitmap != sq) rawBitmap.recycle()
                sq
            }
            val scaledBitmap = if (squareBitmap.width == sizePx && squareBitmap.height == sizePx) {
                squareBitmap
            } else {
                val sc = Bitmap.createScaledBitmap(squareBitmap, sizePx, sizePx, true)
                if (squareBitmap != sc) squareBitmap.recycle()
                sc
            }

            val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
            val cornerRadius = 5.dpToPx(context.resources).toFloat()

            val shader = BitmapShader(scaledBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            paint.shader = shader
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.dpToPx(context.resources).toFloat().coerceAtLeast(1f)
                color = Color.argb(45, 128, 128, 128)
            }
            val inset = borderPaint.strokeWidth / 2f
            val borderRect = RectF(inset, inset, sizePx.toFloat() - inset, sizePx.toFloat() - inset)
            canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)

            if (scaledBitmap != output) {
                scaledBitmap.recycle()
            }

            val drawable = BitmapDrawable(context.resources, output).apply {
                setBounds(0, 0, sizePx, sizePx)
            }
            thumbnailCache.put(cacheKey, drawable)
            return drawable
        }

        val fallback = ContextCompat.getDrawable(context, R.drawable.ic_image)?.mutate()?.apply {
            setBounds(0, 0, sizePx, sizePx)
        }
        if (fallback != null) {
            thumbnailCache.put(cacheKey, fallback)
        }
        return fallback
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
    }
}
