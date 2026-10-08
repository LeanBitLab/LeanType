package helium314.keyboard.latin.gif

import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
        editorInfo: EditorInfo?
    ): Boolean {
        try {
            if (ic == null || editorInfo == null) {
                return false
            }

            val acceptedMimes = EditorInfoCompat.getContentMimeTypes(editorInfo)
            if (acceptedMimes.isEmpty()) {
                toast(context, "App does not support GIF insertion")
                return false
            }

            val copyResult = withContext(Dispatchers.IO) {
                copyAndValidate(context, pfd)
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
                val flags = if (Build.VERSION.SDK_INT >= 25) {
                    InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                } else {
                    runCatching {
                        context.grantUriPermission(editorInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    0
                }
                val info = InputContentInfoCompat(uri, ClipDescription("GIF", arrayOf(actualMime)), null)
                val ok = runCatching {
                    InputConnectionCompat.commitContent(ic, editorInfo, info, flags, null)
                }.getOrDefault(false)

                if (!ok) {
                    outFile.delete()
                    toast(context, "App does not support GIF insertion")
                }
                ok
            }
        } finally {
            closeQuietly(pfd)
        }
    }

    private suspend fun copyAndValidate(context: Context, pfd: ParcelFileDescriptor): Pair<File, String>? {
        val dir = File(context.cacheDir, "gif_cache").apply { mkdirs() }
        cleanup(dir)

        val tempFile = File(dir, "temp_${UUID.randomUUID()}.tmp")
        val header = ByteArray(16)
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
                        if (headerRead < 16) {
                            val toCopy = (16 - headerRead).coerceAtMost(bytesRead)
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
