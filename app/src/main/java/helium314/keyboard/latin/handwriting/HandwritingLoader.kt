// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.handwriting

import android.content.Context
import android.net.Uri
import dalvik.system.DexClassLoader
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import java.io.File

object HandwritingLoader {
    private const val PLUGIN_FILENAME = "handwriting_plugin.apk"
    private const val PLUGIN_CLASS_NAME = "helium314.keyboard.handwriting.plugin.HandwritingRecognizerImpl"
    private const val PREF_HAS_PLUGIN = "pref_handwriting_has_plugin"

    const val PREF_HANDWRITING_LANGUAGE = "pref_handwriting_language"
    const val LANG_FOLLOW_KEYBOARD = "default"

    private var activeRecognizer: HandwritingRecognizer? = null

    fun resetRecognizer() {
        activeRecognizer = null
        displayNameCache = null
    }

    fun getHandwritingLanguagePref(context: Context): String {
        return context.prefs().getString(PREF_HANDWRITING_LANGUAGE, LANG_FOLLOW_KEYBOARD) ?: LANG_FOLLOW_KEYBOARD
    }

    fun setHandwritingLanguage(context: Context, language: String) {
        context.prefs().edit().putString(PREF_HANDWRITING_LANGUAGE, language).apply()
    }

    fun findInstalledModelForLanguage(context: Context, languageTag: String): String? {
        val target = languageTag.trim()
        if (target.isBlank()) return null
        val targetCanonical = HandwritingModelImporter.canonicalTagKey(target)
        val targetBase = targetCanonical.substringBefore('-')

        // 1. Direct / Canonical match for target (e.g. "fr-CA", "en-US", "ml")
        if (HandwritingModelImporter.hasModelDirectly(context, target)) {
            return target
        }
        if (HandwritingModelImporter.hasModelDirectly(context, targetCanonical)) {
            return targetCanonical
        }

        // 2. Base language match (e.g. "fr" for "fr-CA", "en" for "en-IN", "ml" for "ml-IN")
        if (HandwritingModelImporter.hasModelDirectly(context, targetBase)) {
            return targetBase
        }

        // 3. Sibling regional variant of same base language (e.g. "fr-FR" for "fr-CA", "en-US" for "en-IN")
        val installedMap = HandwritingModelImporter.getInstalledLanguageStatuses(context)
        for ((installedTag, status) in installedMap) {
            if (status.isReady) {
                val installedCanonical = HandwritingModelImporter.canonicalTagKey(installedTag)
                val installedBase = installedCanonical.substringBefore('-')
                if (installedBase == targetBase) {
                    return installedTag
                }
            }
        }

        return null
    }

    fun getEffectiveLanguage(context: Context, subtypeLanguage: String): String {
        val pref = getHandwritingLanguagePref(context)
        val target = if (pref == LANG_FOLLOW_KEYBOARD || pref.isBlank()) {
            subtypeLanguage
        } else {
            pref
        }
        return findInstalledModelForLanguage(context, target) ?: target
    }

    private class DisplayNameCache(val tag: String, val name: String)

    @Volatile
    private var displayNameCache: DisplayNameCache? = null

    fun getEffectiveDisplayName(context: Context, subtypeLanguage: String): String {
        val tag = getEffectiveLanguage(context, subtypeLanguage)
        val currentCache = displayNameCache
        if (currentCache != null && currentCache.tag == tag) {
            return currentCache.name
        }
        val displayName = try {
            val locale = java.util.Locale.forLanguageTag(tag)
            val sysLocale = context.resources.configuration.locales[0]
            val name = locale.getDisplayName(sysLocale)
            if (name.isNullOrBlank()) tag else name
        } catch (_: Exception) {
            tag
        }
        displayNameCache = DisplayNameCache(tag, displayName)
        return displayName
    }

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
        val filename = "handwriting_plugin-$abi.apk"
        return if (tag == null || tag == "latest") {
            "https://github.com/LeanBitLab/Leantype-Handwriting-Plugin/releases/latest/download/$filename"
        } else {
            "https://github.com/LeanBitLab/Leantype-Handwriting-Plugin/releases/download/$tag/$filename"
        }
    }

    fun downloadPluginApk(context: Context, tag: String? = null, tempFile: File): Boolean {
        val urlsToTry = listOf(
            getPluginDownloadUrl(tag),
            if (tag == null || tag == "latest") {
                "https://github.com/LeanBitLab/Leantype-Handwriting-Plugin/releases/latest/download/handwriting_plugin.apk"
            } else {
                "https://github.com/LeanBitLab/Leantype-Handwriting-Plugin/releases/download/$tag/handwriting_plugin.apk"
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
                Log.w("HandwritingLoader", "Failed to download from $urlStr", e)
            }
        }
        return false
    }

    private const val NATIVE_LOADER_DEX_BASE64 = "ZGV4CjAzNQAkiCvTdFX0r/3RrbselneGBCvx+cvJKtkwAwAAcAAAAHhWNBIAAAAAAAAAAJACAAAKAAAAcAAAAAUAAACYAAAAAgAAAKwAAAAAAAAAAAAAAAQAAADEAAAAAQAAAOQAAAAsAgAABAEAAEYBAABOAQAAhAEAAJgBAACsAQAAwAEAANMBAADWAQAA2gEAAOABAAABAAAAAgAAAAMAAAAEAAAABgAAAAYAAAAEAAAAAAAAAAcAAAAEAAAAQAEAAAAAAAAAAAAAAAABAAgAAAABAAAAAAAAAAMAAQAIAAAAAAAAAAEAAAABAAAAAAAAAAUAAAAAAAAAfgIAAAAAAAABAAEAAQAAADQBAAAEAAAAcBACAAAADgABAAEAAQAAADgBAAAEAAAAcRADAAAADgACAA4ABAEADjwAAAABAAAAAgAGPGluaXQ+ADRMaGVsaXVtMzE0L2tleWJvYXJkL2hhbmR3cml0aW5nL3BsdWdpbi9OYXRpdmVMb2FkZXI7ABJMamF2YS9sYW5nL09iamVjdDsAEkxqYXZhL2xhbmcvU3RyaW5nOwASTGphdmEvbGFuZy9TeXN0ZW07ABFOYXRpdmVMb2FkZXIuamF2YQABVgACVkwABGxvYWQAmwF+fkQ4eyJiYWNrZW5kIjoiZGV4IiwiY29tcGlsYXRpb24tbW9kZSI6ImRlYnVnIiwiaGFzLWNoZWNrc3VtcyI6ZmFsc2UsIm1pbi1hcGkiOjEsInNoYS0xIjoiNzUwYTIxYjRmNDI4MWIxZjQ1M2I2NDllMGI4NGYxYmE5YzA0ZjRmYyIsInZlcnNpb24iOiI5LjAuMy1kZXYifQAAAAIAAIGABIQCAQmcAgAAAAANAAAAAAAAAAEAAAAAAAAAAQAAAAoAAABwAAAAAgAAAAUAAACYAAAAAwAAAAIAAACsAAAABQAAAAQAAADEAAAABgAAAAEAAADkAAAAASAAAAIAAAAEAQAAAyAAAAIAAAA0AQAAARAAAAEAAABAAQAAAiAAAAoAAABGAQAAACAAAAEAAAB+AgAAAxAAAAEAAACMAgAAABAAAAEAAACQAgAA"

    private fun getNativeLoaderDex(context: Context): File {
        val dexFile = File(context.codeCacheDir, "native_loader.dex")
        if (!dexFile.exists() || dexFile.length() == 0L) {
            try {
                if (dexFile.exists()) {
                    dexFile.setWritable(true)
                    dexFile.delete()
                }
                val bytes = android.util.Base64.decode(NATIVE_LOADER_DEX_BASE64, android.util.Base64.DEFAULT)
                dexFile.outputStream().use { it.write(bytes) }
            } catch (e: Exception) {
                Log.e("HandwritingLoader", "Failed to write native loader dex", e)
            }
        }
        dexFile.setReadOnly()
        return dexFile
    }

    private fun loadNativeLibrariesInPlugin(classLoader: ClassLoader, libFile: File) {
        if (!libFile.exists()) return
        var loadedInPlugin = false
        try {
            val loaderClass = classLoader.loadClass("helium314.keyboard.handwriting.plugin.NativeLoader")
            val loadMethod = loaderClass.getMethod("load", String::class.java)
            loadMethod.invoke(null, libFile.absolutePath)
            loadedInPlugin = true
            Log.i("HandwritingLoader", "Successfully loaded native digitalink library into PluginClassLoader")
        } catch (e: Throwable) {
            Log.e("HandwritingLoader", "Failed to load digitalink library via NativeLoader in PluginClassLoader", e)
        }
        if (!loadedInPlugin) {
            try {
                System.load(libFile.absolutePath)
            } catch (e: Throwable) {
                Log.e("HandwritingLoader", "Failed to System.load libdigitalink.so", e)
            }
        }
    }

    private fun getNativeLibDir(context: Context, apkFile: File): File {
        val baseDir = File(context.filesDir, "plugin_libs")
        if (!baseDir.exists()) baseDir.mkdirs()
        val targetName = "handwriting_${apkFile.lastModified()}"
        val targetDir = File(baseDir, targetName)
        baseDir.listFiles()?.forEach { f ->
            if (f.isDirectory && (f.name.startsWith("handwriting_") || f.name == "handwriting") && f.name != targetName) {
                try {
                    f.deleteRecursively()
                } catch (_: Exception) {}
            }
        }
        return targetDir
    }

    fun getRecognizer(context: Context): HandwritingRecognizer? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return null
        if (activeRecognizer != null) return activeRecognizer
        if (!hasPlugin(context)) return null

        val apkFile = File(context.filesDir, PLUGIN_FILENAME)
        if (!apkFile.exists()) {
            context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
            return null
        }
        apkFile.setReadOnly()

        try {
            Log.i("HandwritingLoader", "Loaded plugin APK path: ${apkFile.absolutePath}, size: ${apkFile.length()}")
        } catch (e: Exception) {
            Log.e("HandwritingLoader", "Failed to log plugin info", e)
        }

        try {
            ensureWorkManagerInitialized(context)
            val nativeLibDir = getNativeLibDir(context, apkFile)
            extractNativeLibs(apkFile, nativeLibDir)
            val libFile = File(nativeLibDir, "libdigitalink.so")
            val nativeLoaderDex = getNativeLoaderDex(context)
            val dexPaths = "${apkFile.absolutePath}${File.pathSeparator}${nativeLoaderDex.absolutePath}"
            val classLoader = PluginClassLoader(
                dexPaths,
                context.codeCacheDir.absolutePath,
                nativeLibDir.absolutePath,
                context.classLoader
            )
            loadNativeLibrariesInPlugin(classLoader, libFile)
            val clazz = classLoader.loadClass(PLUGIN_CLASS_NAME)
            val recognizer = clazz.getDeclaredConstructor().newInstance() as HandwritingRecognizer
            val pluginContext = PluginContext(context.applicationContext, apkFile.absolutePath)
            recognizer.init(pluginContext)
            activeRecognizer = recognizer
            return recognizer
        } catch (e: Exception) {
            Log.e("HandwritingLoader", "Failed to load handwriting plugin", e)
        } catch (e: LinkageError) {
            Log.e("HandwritingLoader", "Failed to link handwriting plugin (ML Kit incompatible on this OS)", e)
        }
        return null
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
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e("HandwritingLoader", "Failed to extract native libraries", e)
        }
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
            val libFile = File(nativeLibDir, "libdigitalink.so")
            val nativeLoaderDex = getNativeLoaderDex(context)
            val dexPaths = "${apkFile.absolutePath}${File.pathSeparator}${nativeLoaderDex.absolutePath}"
            val classLoader = PluginClassLoader(
                dexPaths,
                context.codeCacheDir.absolutePath,
                nativeLibDir.absolutePath,
                context.classLoader
            )
            loadNativeLibrariesInPlugin(classLoader, libFile)
            val clazz = classLoader.loadClass(PLUGIN_CLASS_NAME)
            val recognizer = clazz.getDeclaredConstructor().newInstance() as HandwritingRecognizer
            val pluginContext = PluginContext(context.applicationContext, apkFile.absolutePath)
            recognizer.init(pluginContext)
            
            context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, true).apply()
            activeRecognizer = recognizer
            return true
        } catch (e: Throwable) {
            Log.e("HandwritingLoader", "Failed to import plugin APK", e)
            // Cleanup on failure
            try {
                File(context.filesDir, PLUGIN_FILENAME).delete()
            } catch (_: Exception) {}
            try {
                context.codeCacheDir.deleteRecursively()
            } catch (_: Exception) {}
            try {
                val baseDir = File(context.filesDir, "plugin_libs")
                baseDir.listFiles()?.forEach { f ->
                    if (f.isDirectory && (f.name.startsWith("handwriting_") || f.name == "handwriting")) {
                        f.deleteRecursively()
                    }
                }
            } catch (_: Exception) {}
            context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
            activeRecognizer = null
        }
        return false
    }

    fun removePlugin(context: Context) {
        try {
            File(context.filesDir, PLUGIN_FILENAME).delete()
        } catch (_: Exception) {}
        try {
            File(context.cacheDir, "temp_handwriting_plugin.apk").delete()
        } catch (_: Exception) {}
        try {
            context.cacheDir.listFiles()?.forEach { f ->
                if (f.name.contains("handwriting_plugin")) {
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
                if (f.isDirectory && (f.name.startsWith("handwriting_") || f.name == "handwriting")) {
                    f.deleteRecursively()
                }
            }
        } catch (_: Exception) {}
        context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
        activeRecognizer = null
    }

    private class PluginContext(base: Context, private val apkPath: String) : android.content.ContextWrapper(base), androidx.work.Configuration.Provider {
        private val pluginResources: android.content.res.Resources by lazy {
            try {
                val assetManager = android.content.res.AssetManager::class.java.getDeclaredConstructor().newInstance()
                val addAssetPathMethod = android.content.res.AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
                addAssetPathMethod.invoke(assetManager, apkPath)
                android.content.res.Resources(assetManager, base.resources.displayMetrics, base.resources.configuration)
            } catch (e: Throwable) {
                Log.e("HandwritingLoader", "Failed to create plugin resources", e)
                base.resources
            }
        }

        override fun getResources(): android.content.res.Resources = pluginResources

        override fun getAssets(): android.content.res.AssetManager = pluginResources.assets

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
            if (name.startsWith("helium314.keyboard.handwriting.plugin.") ||
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
