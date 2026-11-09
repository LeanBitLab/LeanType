// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import com.leanbitlab.leantype.voice.ModelImportRequest
import helium314.keyboard.compat.contentLengthLongCompat
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.NetworkUtils
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object VoiceDownloadDispatcher {
    private const val TAG = "VoiceDownloadDispatcher"

    // Observable download state for Compose UI
    val downloadingModelId = mutableStateOf<String?>(null)
    val downloadProgress = mutableFloatStateOf(0f)

    fun hasInternetPermission(context: Context): Boolean {
        return context.packageManager.checkPermission(
            "android.permission.INTERNET",
            context.packageName
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun downloadAndInstall(
        context: Context,
        model: VoiceModelItem,
        pluginManager: VoicePluginManager,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!pluginManager.isPluginInstalled()) {
            withContext(Dispatchers.Main) {
                onError(context.getString(R.string.voice_plugin_not_installed))
            }
            return@withContext
        }

        if (!hasInternetPermission(context)) {
            withContext(Dispatchers.Main) {
                fallbackToBrowser(context, model)
            }
            return@withContext
        }

        if (!NetworkUtils.isNetworkAvailable(context)) {
            withContext(Dispatchers.Main) {
                onError(context.getString(R.string.voice_no_internet_warning))
            }
            return@withContext
        }

        withContext(Dispatchers.Main) {
            downloadingModelId.value = model.id
            downloadProgress.floatValue = 0f
        }

        val cacheDir = File(context.cacheDir, "models")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val tempFile = File(cacheDir, "download_${model.id}.bin")
        if (tempFile.exists()) tempFile.delete()

        try {
            Log.i(TAG, "Starting in-app download for ${model.displayName} from ${model.downloadUrl}")
            var currentUrl = URL(model.downloadUrl)
            var conn = currentUrl.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "LeanType-Android")
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.connect()

            var redirectCount = 0
            while ((conn.responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        conn.responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        conn.responseCode == 307 || conn.responseCode == 308) && redirectCount < 8) {
                val location = conn.getHeaderField("Location") ?: break
                currentUrl = URL(location)
                conn = currentUrl.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "LeanType-Android")
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                conn.connect()
                redirectCount++
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${conn.responseCode}")
            }

            val totalBytes = conn.contentLengthLongCompat()
            var downloadedBytes = 0L

            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(32768)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0L) {
                            val prog = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            withContext(Dispatchers.Main) {
                                downloadProgress.floatValue = prog
                            }
                        }
                    }
                }
            }

            Log.i(TAG, "Download complete (${tempFile.length()} bytes). Dispatching import to voice plugin...")

            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val imported = try {
                val request = ModelImportRequest(
                    engineType = model.engineType,
                    language = model.language,
                    sha256 = null,
                    sizeBytes = tempFile.length(),
                    file = pfd
                )
                withTimeoutOrNull(15000L) {
                    pluginManager.bindAndImport(request)
                } ?: false
            } finally {
                try { pfd.close() } catch (_: Exception) {}
                try { tempFile.delete() } catch (_: Exception) {}
            }

            withContext(Dispatchers.Main) {
                downloadingModelId.value = null
                downloadProgress.floatValue = 0f
                if (imported) {
                    context.prefs().edit().putString("installed_model_${model.engineType}", model.id).apply()
                    Toast.makeText(context, context.getString(R.string.voice_model_installed_success, model.displayName), Toast.LENGTH_SHORT).show()
                    onSuccess()
                } else {
                    val msg = if (!pluginManager.isPluginInstalled()) {
                        context.getString(R.string.voice_plugin_not_installed)
                    } else {
                        context.getString(R.string.voice_plugin_import_failed)
                    }
                    onError(msg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            try { tempFile.delete() } catch (_: Exception) {}
            withContext(Dispatchers.Main) {
                downloadingModelId.value = null
                downloadProgress.floatValue = 0f
                onError(e.localizedMessage ?: context.getString(R.string.download_failed))
            }
        }
    }

    fun fallbackToBrowser(context: Context, model: VoiceModelItem) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(model.browserUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, context.getString(R.string.voice_opening_browser_for, model.displayName), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch browser", e)
        }
    }
}

// Retained for backward-compatibility if any pending system downloads exist
class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No-op in modern in-app download architecture
    }
}

