package helium314.keyboard.latin.gif

import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.leanbitlab.leantype.gif.Contract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object GifInserter {
    private const val MAX_INSERT_BYTES = 15L * 1024 * 1024 // 15 MB
    private const val MAX_CACHE_BYTES = 20L * 1024 * 1024  // 20 MB
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000L    // 24 hours
    private const val GRACE_PERIOD_MS = 5L * 60 * 1000L    // 5 minutes immunity
    private val cleanupMutex = Mutex()

    /**
     * Takes ownership of [pfd] (always closed). Must be initiated from coroutine on Main thread.
     */
    suspend fun commit(
        context: Context,
        pfd: ParcelFileDescriptor,
        declaredMime: String,
        ic: InputConnection?,
        editorInfo: EditorInfo?,
        kind: Int = 0
    ): Boolean {
        try {
            if (ic == null || editorInfo == null) {
                return false
            }

            val acceptedMimes = EditorInfoCompat.getContentMimeTypes(editorInfo)
            if (acceptedMimes.isEmpty()) {
                val copyResult = withContext(Dispatchers.IO) {
                    copyAndValidate(context, pfd, false, false, kind)
                }
                if (copyResult != null) {
                    val (outFile, actualMime) = copyResult
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
                    runCatching {
                        context.grantUriPermission(editorInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val label = if (actualMime.contains("sticker") || actualMime == "image/webp") "Sticker" else "GIF"
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(android.content.ClipData.newUri(context.contentResolver, label, uri))
                    withContext(Dispatchers.Main) {
                        toast(context, "App does not support direct insertion: copied to clipboard")
                    }
                    return true
                }
                toast(context, "App does not support GIF insertion")
                return false
            }

            val supportsWaSticker = acceptedMimes.any { it.equals("image/webp.wasticker", ignoreCase = true) }
            val supportsWebp = acceptedMimes.any { ClipDescription.compareMimeTypes("image/webp", it) }

            val copyResult = withContext(Dispatchers.IO) {
                copyAndValidate(context, pfd, supportsWaSticker, supportsWebp, kind)
            } ?: return false

            val (outFile, actualMime) = copyResult

            val isAccepted = acceptedMimes.any { ClipDescription.compareMimeTypes(actualMime, it) }
            if (!isAccepted) {
                outFile.delete()
                toast(context, "App does not accept this media format")
                return false
            }

            return withContext(Dispatchers.Main) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
                runCatching {
                    context.grantUriPermission(editorInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val flags = if (Build.VERSION.SDK_INT >= 25) {
                    InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                } else {
                    0
                }
                val label = if (actualMime.contains("sticker") || actualMime == "image/webp") "Sticker" else "GIF"
                val info = InputContentInfoCompat(uri, ClipDescription(label, arrayOf(actualMime)), null)
                val ok = runCatching {
                    InputConnectionCompat.commitContent(ic, editorInfo, info, flags, null)
                }.getOrDefault(false)

                if (!ok) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData.newUri(context.contentResolver, label, uri))
                        toast(context, "App does not support direct insertion: copied to clipboard")
                    } else {
                        outFile.delete()
                        toast(context, "App does not support GIF insertion")
                    }
                }
                ok
            }
        } finally {
            closeQuietly(pfd)
        }
    }

    private suspend fun copyAndValidate(
        context: Context,
        pfd: ParcelFileDescriptor,
        supportsWaSticker: Boolean,
        supportsWebp: Boolean,
        kind: Int
    ): Pair<File, String>? {
        val dir = File(context.cacheDir, "gif_cache").apply { mkdirs() }
        cleanup(dir)

        val tempFile = File(dir, "temp_${UUID.randomUUID()}.tmp")
        val header = ByteArray(32)
        var headerRead = 0
        var totalBytes = 0L

        try {
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        totalBytes += bytesRead
                        if (totalBytes > MAX_INSERT_BYTES) {
                            tempFile.delete()
                            withContext(Dispatchers.Main) {
                                toast(context, "File exceeds size limit (15 MB)")
                            }
                            return null
                        }
                        if (headerRead < 32) {
                            val toCopy = (32 - headerRead).coerceAtMost(bytesRead)
                            System.arraycopy(buffer, 0, header, headerRead, toCopy)
                            headerRead += toCopy
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            val sniffedMime = sniffMime(header)
            if (sniffedMime == null) {
                tempFile.delete()
                withContext(Dispatchers.Main) {
                    toast(context, "Invalid media format")
                }
                return null
            }

            // WhatsApp sticker insertion
            if (supportsWaSticker && sniffedMime != "image/gif") {
                val waFile = File(dir, "sticker_${UUID.randomUUID()}.wasticker")
                val isAnim = isAnimatedWebp(header)
                val success = if (isAnim) {
                    tempFile.renameTo(waFile)
                } else {
                    normalizeToWebpSticker(tempFile, waFile).also { tempFile.delete() }
                }
                if (success && waFile.exists() && waFile.length() > 0) {
                    return waFile to "image/webp.wasticker"
                }
                tempFile.delete()
                waFile.delete()
                return null
            }

            // Telegram or other sticker-capable apps: normalize sticker images (JPEG/PNG) to WebP
            if (supportsWebp && (kind == Contract.KIND_STICKER || sniffedMime == "image/webp") && sniffedMime in listOf("image/png", "image/jpeg")) {
                val webpFile = File(dir, "sticker_${UUID.randomUUID()}.webp")
                if (normalizeToWebpSticker(tempFile, webpFile)) {
                    tempFile.delete()
                    return webpFile to "image/webp"
                }
            }

            val ext = when (sniffedMime) {
                "image/webp" -> "webp"
                "image/png" -> "png"
                "image/jpeg" -> "jpg"
                else -> "gif"
            }
            val targetFile = File(dir, "gif_${UUID.randomUUID()}.$ext")
            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete()
                return null
            }
            return targetFile to sniffedMime
        } catch (_: Exception) {
            tempFile.delete()
            return null
        }
    }

    private fun isAnimatedWebp(header: ByteArray): Boolean {
        if (header.size >= 21 &&
            header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
            header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() &&
            header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte() &&
            header[12] == 'V'.code.toByte() && header[13] == 'P'.code.toByte() &&
            header[14] == '8'.code.toByte() && header[15] == 'X'.code.toByte()
        ) {
            val flags = header[20].toInt()
            return (flags and 0x02) != 0
        }
        return false
    }

    private fun normalizeToWebpSticker(inputFile: File, outputFile: File): Boolean {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(inputFile.absolutePath, options)
            val w = options.outWidth
            val h = options.outHeight
            if (w <= 0 || h <= 0) return false

            if (w == 512 && h == 512 && inputFile.length() <= 100 * 1024) {
                inputFile.copyTo(outputFile, overwrite = true)
                return true
            }

            val maxDim = maxOf(w, h)
            var sampleSize = 1
            while (maxDim / sampleSize > 1024) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, decodeOptions) ?: return false

            val currMax = maxOf(bitmap.width, bitmap.height)
            val scale = 512f / currMax
            val targetW = (bitmap.width * scale).toInt().coerceIn(1, 512)
            val targetH = (bitmap.height * scale).toInt().coerceIn(1, 512)

            val scaled = if (targetW != bitmap.width || targetH != bitmap.height) {
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }

            val canvasBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(canvasBitmap)
            val left = (512 - targetW) / 2f
            val top = (512 - targetH) / 2f
            canvas.drawBitmap(scaled, left, top, null)

            val baos = ByteArrayOutputStream()
            var quality = 90
            var compressedBytes: ByteArray
            while (true) {
                baos.reset()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val format = if (quality == 90 && canvasBitmap.hasAlpha()) {
                        Bitmap.CompressFormat.WEBP_LOSSLESS
                    } else {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    }
                    canvasBitmap.compress(format, quality, baos)
                } else {
                    @Suppress("DEPRECATION")
                    canvasBitmap.compress(Bitmap.CompressFormat.WEBP, quality, baos)
                }
                compressedBytes = baos.toByteArray()
                if (compressedBytes.size <= 100 * 1024 || quality <= 30) {
                    break
                }
                quality -= 15
            }

            outputFile.writeBytes(compressedBytes)

            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()
            canvasBitmap.recycle()
            outputFile.exists() && outputFile.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun sniffMime(bytes: ByteArray): String? {
        if (bytes.size >= 6 && (bytes.startsWith("GIF87a".toByteArray()) || bytes.startsWith("GIF89a".toByteArray()))) {
            return "image/gif"
        }
        if (bytes.size >= 12 && bytes.startsWith("RIFF".toByteArray()) &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()) {
            return "image/webp"
        }
        if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()) {
            return "image/png"
        }
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return "image/jpeg"
        }
        return null
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    private suspend fun cleanup(dir: File) = cleanupMutex.withLock {
        val now = System.currentTimeMillis()
        val files = dir.listFiles() ?: return@withLock

        val eligible = files.filter { file ->
            val modified = file.lastModified()
            if (modified > now) {
                file.setLastModified(now)
            }
            // Protect files created in the last 5 minutes from eviction
            (now - modified) > GRACE_PERIOD_MS
        }.sortedBy { it.lastModified() }

        var total = files.sumOf { it.length() }
        for (f in eligible) {
            if (now - f.lastModified() > MAX_AGE_MS || total > MAX_CACHE_BYTES) {
                val len = f.length()
                if (f.delete()) {
                    total -= len
                }
            }
        }
    }

    private fun closeQuietly(pfd: ParcelFileDescriptor) {
        try { pfd.close() } catch (_: Exception) {}
    }

    private fun toast(context: Context, msg: String) {
        Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show()
    }
}
