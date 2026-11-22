// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Language packs: resource-only APKs that hold the translated UI strings of one app build.
 * They are loaded on Android 11+ with [ResourcesLoader], which overlays the app's own resources
 * for every lookup path (Compose, XML, getString, plurals, arrays).
 *
 * A pack reuses the resource IDs of exactly one build (tools/build_language_pack.py), so its
 * versionName carries that build's resource table checksum and its locale:
 * `<versionName>;res=<crc32 of resources.arsc, hex>;locale=<values folder suffix>`.
 * Packs whose package name or checksum differ from the running app are never loaded.
 */
object LanguagePacks {
    private const val TAG = "LanguagePacks"
    private const val DIR = "language_packs"
    const val RELEASES_URL = "https://github.com/LeanBitLab/LeanType-Language-Packs/releases"

    enum class Result { OK, INVALID, WRONG_BUILD, DOWNLOAD_FAILED }

    private var loader: Any? = null // ResourcesLoader, typed as Any to keep this class loadable below API 30
    private var resChecksum: String? = null

    val isSupported get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** Device-protected, so packs also load before the first unlock (direct boot). */
    private fun packsDir(context: Context): File {
        val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            context.createDeviceProtectedStorageContext() else context
        return File(storageContext.filesDir, DIR)
    }

    /** Adds the pack loader to the application resources and every activity created later. */
    fun init(app: Application) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val packLoader = ResourcesLoader()
        loader = packLoader
        reload(app)
        app.resources.addLoaders(packLoader)
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                apply(activity.resources)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun apply(resources: Resources) {
        val packLoader = loader as? ResourcesLoader ?: return
        resources.addLoaders(packLoader) // no-op if the loader is already there
    }

    /** Values folder suffixes that can translate the current app language, best match first. */
    fun wantedLocales(context: Context): List<String> {
        val tag = context.prefs().getString(Settings.PREF_APP_LANGUAGE, Defaults.PREF_APP_LANGUAGE) ?: ""
        val locale = if (tag.isEmpty() || tag == "system") Resources.getSystem().configuration.locales[0]
            else LocaleUtils.parseLocale(tag)
        if (locale.language == "en") return emptyList()
        return listOfNotNull(locale.country.takeIf { it.isNotEmpty() }?.let { "${locale.language}-r$it" }, locale.language)
    }

    fun displayName(context: Context): String {
        val locale = wantedLocales(context).lastOrNull()?.let { Locale(it) } ?: return ""
        return locale.getDisplayName(locale).replaceFirstChar { it.uppercase(locale) }
    }

    /** The installed pack that translates the current app language, if any. */
    fun installedPack(context: Context): File? =
        wantedLocales(context).map { File(packsDir(context), "$it.apk") }.firstOrNull { it.isFile }

    fun remove(context: Context) {
        installedPack(context)?.delete()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) reload(context)
    }

    fun importFromUri(context: Context, uri: Uri): Result {
        val temp = File(context.cacheDir, "language_pack_import.apk")
        context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
            ?: return Result.INVALID
        return install(context, temp)
    }

    /** Downloads the pack for the current app language that was built for this exact app build. */
    fun download(context: Context, onProgress: (Float) -> Unit): Result {
        val tag = "${appVersionName(context)}-${appChecksum(context)}"
        val temp = File(context.cacheDir, "language_pack_download.apk")
        for (locale in wantedLocales(context)) {
            val url = "$RELEASES_URL/download/$tag/$locale.apk"
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "LeanType")
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.i(TAG, "no pack at $url (${conn.responseCode})")
                    conn.disconnect()
                    continue
                }
                val length = conn.contentLengthLong.toFloat()
                conn.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(16384)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (length > 0) onProgress(copied / length)
                        }
                    }
                }
                conn.disconnect()
                return install(context, temp)
            } catch (e: Exception) {
                Log.w(TAG, "download of $url failed", e)
            }
        }
        return Result.DOWNLOAD_FAILED
    }

    private fun install(context: Context, file: File): Result {
        val result = check(context, file)
        if (result.first != Result.OK) {
            file.delete()
            return result.first
        }
        val dir = packsDir(context).apply { mkdirs() }
        file.copyTo(File(dir, "${result.second}.apk"), overwrite = true)
        file.delete()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) reload(context)
        return Result.OK
    }

    /** Returns the check result and, if OK, the pack's locale. */
    private fun check(context: Context, pack: File): Pair<Result, String?> {
        val info = context.packageManager.getPackageArchiveInfo(pack.absolutePath, 0)
            ?: return Result.INVALID to null
        val fields = info.versionName.orEmpty().split(";").drop(1)
            .mapNotNull { it.split("=", limit = 2).takeIf { kv -> kv.size == 2 } }.associate { it[0] to it[1] }
        val locale = fields["locale"]
        if (locale == null || !locale.matches(Regex("[a-z]{2,3}(-r[A-Z]{2})?"))) return Result.INVALID to null
        if (info.packageName != context.packageName || fields["res"] != appChecksum(context)) {
            Log.w(TAG, "${pack.name} is for ${info.packageName} ${info.versionName}, app is ${context.packageName} ${appChecksum(context)}")
            return Result.WRONG_BUILD to null
        }
        return Result.OK to locale
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun reload(context: Context) {
        val packLoader = loader as? ResourcesLoader ?: return
        val providers = packsDir(context).listFiles { f -> f.isFile && f.name.endsWith(".apk") }.orEmpty()
            .sortedBy { it.name }
            .mapNotNull { pack ->
                if (check(context, pack).first != Result.OK) {
                    Log.w(TAG, "skipping ${pack.name}")
                    return@mapNotNull null
                }
                Log.i(TAG, "loading ${pack.name}")
                ParcelFileDescriptor.open(pack, ParcelFileDescriptor.MODE_READ_ONLY).use { ResourcesProvider.loadFromApk(it) }
            }
        // replaced providers are not closed: resources may still reference them until they are rebuilt
        packLoader.setProviders(providers)
    }

    private fun appVersionName(context: Context) =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** CRC32 of the app's resources.arsc, read from the zip directory (no decompression). */
    private fun appChecksum(context: Context): String = resChecksum ?: ZipFile(context.applicationInfo.sourceDir).use {
        "%08x".format(it.getEntry("resources.arsc").crc)
    }.also { resChecksum = it }
}
