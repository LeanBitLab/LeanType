// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai

import android.content.Context
import android.net.Uri
import dalvik.system.DexClassLoader
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import java.io.File

object OfflineAiLoader {
    private const val CURRENT_INTERFACE_VERSION = 1
    private const val PLUGIN_FILENAME = "offline_ai_plugin.apk"
    private const val PLUGIN_CLASS_NAME = "helium314.keyboard.ai.plugin.OfflineAiProviderImpl"
    private const val PREF_HAS_PLUGIN = "pref_offline_ai_has_plugin"
    private const val TAG = "OfflineAiLoader"

    private var activeProvider: IOfflineAiProvider? = null

    fun getTargetAbi(): String {
        for (abi in android.os.Build.SUPPORTED_ABIS) {
            when (abi) {
                "arm64-v8a" -> return "arm64-v8a"
                "x86_64" -> return "x86_64"
            }
        }
        return "arm64-v8a"
    }

    fun getPluginDownloadUrl(tag: String? = null): String {
        val abi = getTargetAbi()
        val filename = "ai_plugin-$abi.apk"
        return if (tag == null || tag == "latest") {
            "https://github.com/LeanBitLab/LeanType-Offline-AI-Plugin/releases/latest/download/$filename"
        } else {
            "https://github.com/LeanBitLab/LeanType-Offline-AI-Plugin/releases/download/$tag/$filename"
        }
    }

    fun downloadPluginApk(context: Context, tag: String? = null, tempFile: File): Boolean {
        val urlsToTry = listOf(
            getPluginDownloadUrl(tag),
            if (tag == null || tag == "latest") {
                "https://github.com/LeanBitLab/LeanType-Offline-AI-Plugin/releases/latest/download/ai_plugin.apk"
            } else {
                "https://github.com/LeanBitLab/LeanType-Offline-AI-Plugin/releases/download/$tag/ai_plugin.apk"
            }
        ).distinct()

        for (urlStr in urlsToTry) {
            try {
                val url = java.net.URL(urlStr)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "HeliboardL")
                conn.connect()

                var redirectConn = conn
                var status = redirectConn.responseCode
                var redirectCount = 0
                while ((status == java.net.HttpURLConnection.HTTP_MOVED_TEMP || status == java.net.HttpURLConnection.HTTP_MOVED_PERM || status == java.net.HttpURLConnection.HTTP_SEE_OTHER) && redirectCount < 5) {
                    val newUrl = redirectConn.getHeaderField("Location")
                    redirectConn.disconnect()
                    val nextUrl = java.net.URL(newUrl)
                    redirectConn = nextUrl.openConnection() as java.net.HttpURLConnection
                    redirectConn.setRequestProperty("User-Agent", "HeliboardL")
                    redirectConn.connect()
                    status = redirectConn.responseCode
                    redirectCount++
                }

                if (status == java.net.HttpURLConnection.HTTP_OK) {
                    redirectConn.inputStream.use { input ->
                        java.io.FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    redirectConn.disconnect()
                    return true
                }
                redirectConn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to download from $urlStr", e)
            }
        }
        return false
    }

    private fun getNativeLibDir(context: Context, apkFile: File): File {
        val baseDir = File(context.filesDir, "plugin_libs")
        if (!baseDir.exists()) baseDir.mkdirs()
        val targetName = "offline_ai_${apkFile.lastModified()}"
        val targetDir = File(baseDir, targetName)
        baseDir.listFiles()?.forEach { f ->
            if (f.isDirectory && (f.name.startsWith("offline_ai_") || f.name == "offline_ai") && f.name != targetName) {
                try {
                    f.deleteRecursively()
                } catch (_: Exception) {}
            }
        }
        return targetDir
    }

    fun getProvider(context: Context): IOfflineAiProvider? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return null
        val cached = activeProvider
        if (cached != null) return cached
        if (!hasPlugin(context)) return null

        val apkFile = File(context.filesDir, PLUGIN_FILENAME)
        if (!apkFile.exists()) {
            context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
            return null
        }
        val provider = loadProviderInternal(context, apkFile)
        if (provider != null) {
            activeProvider = provider
        }
        return provider
    }

    private fun loadProviderInternal(context: Context, apkFile: File): IOfflineAiProvider? {
        return try {
            apkFile.setReadOnly()
            val nativeLibDir = getNativeLibDir(context, apkFile)
            extractNativeLibs(apkFile, nativeLibDir)
            val classLoader = PluginClassLoader(
                apkFile.absolutePath,
                context.codeCacheDir.absolutePath,
                nativeLibDir.absolutePath,
                context.classLoader
            )
            val clazz = classLoader.loadClass(PLUGIN_CLASS_NAME)
            val provider = clazz.getDeclaredConstructor().newInstance() as IOfflineAiProvider
            
            if (provider.getInterfaceVersion() > CURRENT_INTERFACE_VERSION) {
                Log.w(TAG, "Plugin version newer than supported interface!")
                return null
            }

            provider.init(context)
            Log.i(TAG, "Offline AI provider loaded successfully")
            provider
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load offline AI plugin", e)
            null
        }
    }

    private fun extractNativeLibs(apkFile: File, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()
        try {
            val zip = java.util.zip.ZipFile(apkFile)
            val targetAbi = getTargetAbi()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.startsWith("lib/$targetAbi/") && entry.name.endsWith(".so")) {
                    val libName = entry.name.substringAfterLast("/")
                    val outFile = File(destDir, libName)
                    if (!outFile.exists() || outFile.length() != entry.size) {
                        zip.getInputStream(entry).use { input ->
                            java.io.FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    outFile.setReadable(true, false)
                    outFile.setExecutable(true, false)
                    outFile.setReadOnly()
                }
            }
            zip.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting native libs from plugin APK", e)
        }
    }

    fun hasPlugin(context: Context): Boolean {
        val has = context.prefs().getBoolean(PREF_HAS_PLUGIN, false)
        if (!has) return false
        val apkFile = File(context.filesDir, PLUGIN_FILENAME)
        return apkFile.exists() && apkFile.length() > 0
    }

    fun getPluginVersion(context: Context): String? {
        val apkFile = File(context.filesDir, PLUGIN_FILENAME)
        if (!apkFile.exists()) return null
        return try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            info?.versionName
        } catch (_: Exception) {
            null
        }
    }

    fun loadPlugin(context: Context, sourceUri: Uri): Boolean {
        return try {
            try {
                context.codeCacheDir.deleteRecursively()
            } catch (_: Exception) {}

            val targetFile = File(context.filesDir, PLUGIN_FILENAME)
            if (targetFile.exists()) targetFile.delete()

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                java.io.FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return false

            targetFile.setReadOnly()
            activeProvider?.cleanup()
            activeProvider = null

            val provider = loadProviderInternal(context, targetFile)
            val success = provider != null && provider.isAvailable()
            if (success) {
                activeProvider = provider
                context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, true).apply()
                Log.i(TAG, "Plugin imported and registered successfully")
            } else {
                targetFile.delete()
                context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
                Log.w(TAG, "Plugin import verification failed")
            }
            success
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load plugin from URI", e)
            false
        }
    }

    fun loadPluginFromTempFile(context: Context, tempFile: File): Boolean {
        return try {
            try {
                context.codeCacheDir.deleteRecursively()
            } catch (_: Exception) {}

            val targetFile = File(context.filesDir, PLUGIN_FILENAME)
            if (targetFile.exists()) targetFile.delete()

            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
            targetFile.setReadOnly()

            activeProvider?.cleanup()
            activeProvider = null

            val provider = loadProviderInternal(context, targetFile)
            val success = provider != null && provider.isAvailable()
            if (success) {
                activeProvider = provider
                context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, true).apply()
                Log.i(TAG, "Plugin imported from temp file successfully")
            } else {
                targetFile.delete()
                context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
                Log.w(TAG, "Plugin temp file verification failed")
            }
            success
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load plugin from temp file", e)
            false
        }
    }

    fun removePlugin(context: Context) {
        try {
            activeProvider?.cleanup()
            activeProvider = null
            val apkFile = File(context.filesDir, PLUGIN_FILENAME)
            if (apkFile.exists()) apkFile.delete()
            val nativeLibBase = File(context.filesDir, "plugin_libs")
            nativeLibBase.listFiles()?.forEach { f ->
                if (f.isDirectory && (f.name.startsWith("offline_ai_") || f.name == "offline_ai")) {
                    try { f.deleteRecursively() } catch (_: Exception) {}
                }
            }
            context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
            Log.i(TAG, "Plugin removed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing plugin", e)
        }
    }

    class PluginClassLoader(
        dexPath: String,
        optimizedDirectory: String?,
        librarySearchPath: String?,
        parent: ClassLoader
    ) : DexClassLoader(dexPath, optimizedDirectory, librarySearchPath, parent)
}
