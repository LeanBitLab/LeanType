// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translation

import android.content.Context
import android.net.Uri
import dalvik.system.DexClassLoader
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import java.io.File
import java.lang.ref.WeakReference

object TranslationLoader {
    private const val CURRENT_INTERFACE_VERSION = 2
    private const val PLUGIN_FILENAME = "translation_plugin.apk"
    private const val PLUGIN_CLASS_NAME = "helium314.keyboard.translation.plugin.TranslationProviderImpl"
    private const val PREF_HAS_PLUGIN = "pref_translation_has_plugin"
    private const val TAG = "TranslationLoader"

    private var activeProvider: ITranslationProvider? = null
    private var cachedClassLoader: PluginClassLoader? = null
    private var cachedApkModified: Long = 0L

    fun getTargetAbi(): String {
        for (abi in android.os.Build.SUPPORTED_ABIS) {
            when (abi) {
                "arm64-v8a" -> return "arm64-v8a"
                "armeabi-v7a" -> return "armeabi-v7a"
                "x86_64" -> return "x86_64"
                "x86" -> return "x86"
            }
        }
        return "arm64-v8a"
    }

    fun getPluginDownloadUrl(tag: String? = null): String {
        val abi = getTargetAbi()
        val filename = "translation_plugin-$abi.apk"
        return if (tag == null || tag == "latest") {
            "https://github.com/LeanBitLab/LeanType-Translation-Plugin/releases/latest/download/$filename"
        } else {
            "https://github.com/LeanBitLab/LeanType-Translation-Plugin/releases/download/$tag/$filename"
        }
    }

    fun downloadPluginApk(context: Context, tag: String? = null, tempFile: File): Boolean {
        val urlsToTry = listOf(
            getPluginDownloadUrl(tag),
            if (tag == null || tag == "latest") {
                "https://github.com/LeanBitLab/LeanType-Translation-Plugin/releases/latest/download/translation_plugin.apk"
            } else {
                "https://github.com/LeanBitLab/LeanType-Translation-Plugin/releases/download/$tag/translation_plugin.apk"
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
        val targetName = "translation_${apkFile.lastModified()}"
        val targetDir = File(baseDir, targetName)
        baseDir.listFiles()?.forEach { f ->
            if (f.isDirectory && (f.name.startsWith("translation_") || f.name == "translation") && f.name != targetName) {
                try {
                    f.deleteRecursively()
                } catch (_: Exception) {}
            }
        }
        return targetDir
    }

    fun getProvider(context: Context): ITranslationProvider? {
        val cached = activeProvider
        if (cached != null) return cached
        if (!hasPlugin(context)) return null

        val apkFile = File(context.filesDir, PLUGIN_FILENAME)
        if (!apkFile.exists()) {
            context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
            return null
        }
        apkFile.setReadOnly()

        return try {
            TranslationModelImporter.migrateLegacyModels(context)
            ensureWorkManagerInitialized(context)
            val nativeLibDir = getNativeLibDir(context, apkFile)
            extractNativeLibs(apkFile, nativeLibDir)
            val cachedLoader = cachedClassLoader
            val classLoader = if (cachedLoader != null && cachedApkModified == apkFile.lastModified()) {
                cachedLoader
            } else {
                val cl = PluginClassLoader(
                    apkFile.absolutePath,
                    context.codeCacheDir.absolutePath,
                    nativeLibDir.absolutePath,
                    context.classLoader
                )
                cachedClassLoader = cl
                cachedApkModified = apkFile.lastModified()
                cl
            }
            val clazz = classLoader.loadClass(PLUGIN_CLASS_NAME)
            val provider = clazz.getDeclaredConstructor().newInstance() as ITranslationProvider
            
            if (provider.getInterfaceVersion() > CURRENT_INTERFACE_VERSION) {
                Log.w(TAG, "Plugin version newer than supported interface!")
                return null
            }

            val mergedContext = createMergedContext(context.applicationContext, apkFile)
            val pluginRuntime = helium314.keyboard.latin.work.PluginRuntime(
                classLoader = classLoader,
                workerContext = mergedContext
            )
            helium314.keyboard.latin.App.pluginWorkerFactory.pluginRuntime = pluginRuntime

            provider.init(mergedContext)
            activeProvider = provider
            provider
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load translation plugin", e)
            null
        }
    }

    private fun extractNativeLibs(apkFile: File, outputDir: File) {
        if (!outputDir.exists()) outputDir.mkdirs()
        try {
            java.util.zip.ZipFile(apkFile).use { zip ->
                val abis = android.os.Build.SUPPORTED_ABIS
                var targetAbi: String? = null
                for (abi in abis) {
                    if (zip.entries().asSequence().any { it.name.startsWith("lib/$abi/") && it.name.endsWith(".so") }) {
                        targetAbi = abi
                        break
                    }
                }
                if (targetAbi != null) {
                    val prefix = "lib/$targetAbi/"
                    for (entry in zip.entries().asSequence()) {
                        if (entry.name.startsWith(prefix) && entry.name.endsWith(".so")) {
                            val fileName = entry.name.substring(prefix.length)
                            val outFile = File(outputDir, fileName)
                            if (!outFile.exists() || outFile.length() != entry.size) {
                                zip.getInputStream(entry).use { input ->
                                    outFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                outFile.setReadable(true, false)
                                outFile.setExecutable(true, false)
                                outFile.setReadOnly()
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to extract native libraries", e)
        }
    }

    fun hasPlugin(context: Context): Boolean {
        return context.prefs().getBoolean(PREF_HAS_PLUGIN, false)
    }

    fun getPluginVersion(context: Context): String? {
        val apkFile = File(context.filesDir, PLUGIN_FILENAME)
        if (!apkFile.exists()) return null
        return try {
            val info = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            info?.versionName
        } catch (e: Exception) {
            null
        }
    }

    fun importPlugin(context: Context, uri: Uri): Boolean {
        try {
            try {
                context.codeCacheDir.deleteRecursively()
            } catch (_: Exception) {}

            val apkFile = File(context.filesDir, PLUGIN_FILENAME)
            if (apkFile.exists()) {
                apkFile.delete()
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                apkFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            apkFile.setReadOnly()

            // Verify the plugin loads successfully
            ensureWorkManagerInitialized(context)
            val nativeLibDir = getNativeLibDir(context, apkFile)
            extractNativeLibs(apkFile, nativeLibDir)
            val classLoader = PluginClassLoader(
                apkFile.absolutePath,
                context.codeCacheDir.absolutePath,
                nativeLibDir.absolutePath,
                context.classLoader
            )
            val clazz = classLoader.loadClass(PLUGIN_CLASS_NAME)
            val provider = clazz.getDeclaredConstructor().newInstance() as ITranslationProvider
            
            if (provider.getInterfaceVersion() > CURRENT_INTERFACE_VERSION) {
                Log.w(TAG, "Incompatible plugin interface version")
                return false
            }

            val mergedContext = createMergedContext(context.applicationContext, apkFile)
            val pluginRuntime = helium314.keyboard.latin.work.PluginRuntime(
                classLoader = classLoader,
                workerContext = mergedContext
            )
            helium314.keyboard.latin.App.pluginWorkerFactory.pluginRuntime = pluginRuntime

            provider.init(mergedContext)
            context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, true).apply()
            activeProvider = provider
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to import translation plugin APK", e)
            try {
                File(context.filesDir, PLUGIN_FILENAME).delete()
            } catch (_: Exception) {}
            try {
                context.codeCacheDir.deleteRecursively()
            } catch (_: Exception) {}
            try {
                val baseDir = File(context.filesDir, "plugin_libs")
                baseDir.listFiles()?.forEach { f ->
                    if (f.isDirectory && (f.name.startsWith("translation_") || f.name == "translation")) {
                        f.deleteRecursively()
                    }
                }
            } catch (_: Exception) {}
            context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
            activeProvider = null
        }
        return false
    }

    private fun ensureWorkManagerInitialized(context: Context) {
        try {
            androidx.work.WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            try {
                androidx.work.WorkManager.initialize(
                    context.applicationContext,
                    (context.applicationContext as? androidx.work.Configuration.Provider)?.workManagerConfiguration
                        ?: androidx.work.Configuration.Builder().build()
                )
            } catch (_: Throwable) {}
        }
    }

    fun unloadPlugin() {
        try {
            activeProvider?.cleanup()
        } catch (e: Throwable) {
            Log.e(TAG, "Error during plugin cleanup", e)
        }
        activeProvider = null
    }

    fun removePlugin(context: Context) {
        unloadPlugin()
        cachedClassLoader = null
        cachedApkModified = 0L
        helium314.keyboard.latin.App.pluginWorkerFactory.pluginRuntime = null
        try {
            File(context.filesDir, PLUGIN_FILENAME).delete()
        } catch (_: Exception) {}
        try {
            File(context.cacheDir, "temp_translation_plugin.apk").delete()
        } catch (_: Exception) {}
        try {
            context.cacheDir.listFiles()?.forEach { f ->
                if (f.name.contains("translation_plugin")) {
                    f.delete()
                }
            }
        } catch (_: Exception) {}
        try {
            context.codeCacheDir.deleteRecursively()
        } catch (_: Exception) {}
        try {
            val baseDir = File(context.filesDir, "plugin_libs")
            baseDir.listFiles()?.forEach { f ->
                if (f.isDirectory && (f.name.startsWith("translation_") || f.name == "translation")) {
                    f.deleteRecursively()
                }
            }
        } catch (_: Exception) {}
        context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
    }

    private fun createMergedContext(host: Context, pluginApk: File): Context {
        val hostRes = host.resources
        val assetManager = try {
            val am = android.content.res.AssetManager::class.java.getDeclaredConstructor().newInstance()
            val addAssetPathMethod = android.content.res.AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
            addAssetPathMethod.invoke(am, pluginApk.absolutePath)
            am
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create merged AssetManager", e)
            host.assets
        }
        val mergedResources = try {
            android.content.res.Resources(
                assetManager,
                hostRes.displayMetrics,
                hostRes.configuration
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create merged Resources", e)
            hostRes
        }
        return MergedPluginContext(
            host.applicationContext,
            assetManager,
            mergedResources
        )
    }

    private class MergedPluginContext(
        base: Context,
        private val mergedAssets: android.content.res.AssetManager,
        private val mergedResources: android.content.res.Resources
    ) : android.content.ContextWrapper(base), androidx.work.Configuration.Provider {

        override fun getResources(): android.content.res.Resources = mergedResources

        override fun getAssets(): android.content.res.AssetManager = mergedAssets

        override fun getApplicationContext(): Context = this

        override val workManagerConfiguration: androidx.work.Configuration
            get() = (baseContext.applicationContext as? androidx.work.Configuration.Provider)?.workManagerConfiguration
                ?: androidx.work.Configuration.Builder().build()
    }

    private class PluginClassLoader(
        dexPath: String,
        optimizedDirectory: String?,
        private val librarySearchPath: String?,
        parent: ClassLoader
    ) : DexClassLoader(dexPath, optimizedDirectory, librarySearchPath, parent) {
        override fun findLibrary(name: String): String? {
            if (librarySearchPath != null) {
                val filename = System.mapLibraryName(name)
                val file = java.io.File(librarySearchPath, filename)
                if (file.exists()) {
                    return file.absolutePath
                }
            }
            return super.findLibrary(name)
        }

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name.startsWith("helium314.keyboard.translation.plugin.") ||
                name.startsWith("com.google.mlkit.") ||
                name.startsWith("com.google.android.datatransport.") ||
                name.startsWith("com.google.android.gms.") ||
                name.startsWith("com.google.firebase.")
            ) {
                val loaded = findLoadedClass(name)
                if (loaded != null) return loaded
                try {
                    return findClass(name)
                } catch (_: ClassNotFoundException) {
                    // fallback to parent
                }
            }
            return super.loadClass(name, resolve)
        }
    }
}
