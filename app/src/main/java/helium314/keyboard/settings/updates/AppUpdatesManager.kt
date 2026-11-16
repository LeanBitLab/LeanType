// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.updates

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.ai.OfflineAiLoader
import helium314.keyboard.latin.cjk.CjkPluginLoader
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.gif.GifPluginManager
import helium314.keyboard.latin.handwriting.HandwritingLoader
import helium314.keyboard.latin.ocr.OcrPluginLoader
import helium314.keyboard.latin.translation.TranslationLoader
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.NetworkUtils
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PluginInfo(
    val id: String,
    @StringRes val nameRes: Int,
    val defaultName: String,
    val iconRes: Int,
    val repoReleasesApi: String,
    val releasesPageUrl: String,
    val getInstalledVersion: (Context) -> String?
)

data class PluginUpdateStatus(
    val plugin: PluginInfo,
    val installedVersion: String,
    val latestVersion: String?,
    val isUpdateAvailable: Boolean
)

object AppUpdatesManager {
    private const val TAG = "AppUpdatesManager"
    const val PREF_AUTO_CHECK = "pref_auto_check_updates"
    const val PREF_FREQUENCY = "pref_auto_check_updates_frequency"
    const val PREF_LAST_CHECK = "pref_last_update_check_time"
    const val PREF_APP_LATEST_TAG = "pref_app_latest_tag"
    const val PREF_PLUGIN_LATEST_PREFIX = "pref_plugin_latest_"

    private val _updateState = MutableStateFlow(0)
    val updateState = _updateState.asStateFlow()

    val PLUGINS = listOf(
        PluginInfo(
            id = "gif",
            nameRes = R.string.gif_settings_title,
            defaultName = "GIF Search & Stickers",
            iconRes = R.drawable.ic_gif,
            repoReleasesApi = Links.GIF_PLUGIN_RELEASES_API,
            releasesPageUrl = Links.GIF_PLUGIN_REPO + "/releases",
            getInstalledVersion = { ctx ->
                try {
                    ctx.packageManager.getPackageInfo(GifPluginManager.PLUGIN_PACKAGE, 0).versionName
                } catch (_: Exception) { null }
            }
        ),
        PluginInfo(
            id = "voice",
            nameRes = R.string.voice_input_title,
            defaultName = "Voice Typing Plugin",
            iconRes = R.drawable.sym_keyboard_voice_holo,
            repoReleasesApi = Links.VOICE_PLUGIN_RELEASES_API,
            releasesPageUrl = Links.VOICE_PLUGIN_REPO + "/releases",
            getInstalledVersion = { ctx ->
                try {
                    ctx.packageManager.getPackageInfo("com.leanbitlab.leantype.voice.offline", 0).versionName
                } catch (_: Exception) {
                    try {
                        ctx.packageManager.getPackageInfo("com.leanbitlab.leantype.voice", 0).versionName
                    } catch (_: Exception) { null }
                }
            }
        ),
        PluginInfo(
            id = "ocr",
            nameRes = R.string.ocr_title,
            defaultName = "Text Recognition (OCR) Plugin",
            iconRes = R.drawable.ic_ocr,
            repoReleasesApi = "https://api.github.com/repos/LeanBitLab/LeanType-OCR-Plugin/releases/latest",
            releasesPageUrl = "https://github.com/LeanBitLab/LeanType-OCR-Plugin/releases",
            getInstalledVersion = { ctx ->
                if (OcrPluginLoader.hasPlugin(ctx)) OcrPluginLoader.getPluginVersion(ctx) ?: "1.0" else null
            }
        ),
        PluginInfo(
            id = "cjk",
            nameRes = R.string.cjk_settings_title,
            defaultName = "CJK Companion Plugin",
            iconRes = R.drawable.ic_settings_languages,
            repoReleasesApi = "https://api.github.com/repos/LeanBitLab/LeanType-CJK-Plugin/releases/latest",
            releasesPageUrl = "https://github.com/LeanBitLab/LeanType-CJK-Plugin/releases",
            getInstalledVersion = { ctx ->
                if (CjkPluginLoader.hasPlugin(ctx)) {
                    CjkPluginLoader.getPluginVersion(ctx) ?: "1.0"
                } else {
                    try {
                        ctx.packageManager.getPackageInfo("com.leanbitlab.leantype.cjk.plugin", 0).versionName
                    } catch (_: Exception) { null }
                }
            }
        ),
        PluginInfo(
            id = "handwriting",
            nameRes = R.string.libraries_hub_handwriting_title,
            defaultName = "Handwriting Input Plugin",
            iconRes = R.drawable.ic_edit,
            repoReleasesApi = "https://api.github.com/repos/LeanBitLab/Leantype-Handwriting-Plugin/releases/latest",
            releasesPageUrl = "https://github.com/LeanBitLab/Leantype-Handwriting-Plugin/releases",
            getInstalledVersion = { ctx ->
                if (HandwritingLoader.hasPlugin(ctx)) HandwritingLoader.getPluginVersion(ctx) ?: "1.0" else null
            }
        ),
        PluginInfo(
            id = "translation",
            nameRes = R.string.translation_settings_title,
            defaultName = "Translation Plugin",
            iconRes = R.drawable.ic_translate,
            repoReleasesApi = "https://api.github.com/repos/LeanBitLab/LeanType-Translation-Plugin/releases/latest",
            releasesPageUrl = "https://github.com/LeanBitLab/LeanType-Translation-Plugin/releases",
            getInstalledVersion = { ctx ->
                if (TranslationLoader.hasPlugin(ctx)) TranslationLoader.getPluginVersion(ctx) ?: "1.0" else null
            }
        ),
        PluginInfo(
            id = "offline_ai",
            nameRes = R.string.load_offline_ai_plugin,
            defaultName = "Offline AI Plugin",
            iconRes = R.drawable.ic_proofread,
            repoReleasesApi = "https://api.github.com/repos/LeanBitLab/LeanType-Offline-AI-Plugin/releases/latest",
            releasesPageUrl = "https://github.com/LeanBitLab/LeanType-Offline-AI-Plugin/releases",
            getInstalledVersion = { ctx ->
                if (OfflineAiLoader.hasPlugin(ctx)) OfflineAiLoader.getPluginVersion(ctx) ?: "1.0" else null
            }
        )
    )

    fun hasAppUpdate(context: Context): Boolean {
        if (BuildConfig.FLAVOR != "standard") return false
        // derived from the cached latest tag, so it stays correct after an update without a new check
        val latest = getAppLatestVersion(context) ?: return false
        return isNewerVersion(BuildConfig.VERSION_NAME, latest)
    }

    fun hasAnyPluginUpdate(context: Context): Boolean {
        if (BuildConfig.FLAVOR != "standard") return false
        return PLUGINS.any { isPluginUpdateAvailable(context, it.id) }
    }

    fun hasAnyUpdate(context: Context): Boolean {
        if (BuildConfig.FLAVOR != "standard") return false
        return hasAppUpdate(context) || hasAnyPluginUpdate(context)
    }

    fun isPluginUpdateAvailable(context: Context, pluginId: String): Boolean {
        if (BuildConfig.FLAVOR != "standard") return false
        val plugin = PLUGINS.find { it.id == pluginId } ?: return false
        val installed = plugin.getInstalledVersion(context) ?: return false
        val latest = getPluginLatestVersion(context, pluginId) ?: return false
        return isNewerVersion(installed, latest)
    }

    fun getPluginLatestVersion(context: Context, pluginId: String): String? {
        if (BuildConfig.FLAVOR != "standard") return null
        return context.prefs().getString("$PREF_PLUGIN_LATEST_PREFIX$pluginId", null)
    }

    fun getAppLatestVersion(context: Context): String? {
        if (BuildConfig.FLAVOR != "standard") return null
        return context.prefs().getString(PREF_APP_LATEST_TAG, null)
    }

    fun isNewerVersion(current: String, remote: String): Boolean {
        val cleanCurrent = current.removePrefix("v").substringBefore("-").trim()
        val cleanRemote = remote.removePrefix("v").substringBefore("-").trim()
        val currentParts = cleanCurrent.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        val remoteParts = cleanRemote.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        val maxLen = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val c = currentParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    suspend fun checkUpdatesIfNeeded(context: Context) {
        if (BuildConfig.FLAVOR != "standard") return
        val prefs = context.prefs()
        val isAutoCheckEnabled = prefs.getBoolean(PREF_AUTO_CHECK, true)
        if (!isAutoCheckEnabled) return

        val freqDays = prefs.getString(PREF_FREQUENCY, "7")?.toLongOrNull() ?: 7L
        val lastCheck = prefs.getLong(PREF_LAST_CHECK, 0L)
        val now = System.currentTimeMillis()
        val intervalMs = freqDays * 24 * 60 * 60 * 1000L

        if (now - lastCheck >= intervalMs) {
            if (!NetworkUtils.isNetworkAvailable(context)) return
            checkAllUpdates(context)
        }
    }

    suspend fun checkAllUpdates(context: Context): Pair<Boolean, List<PluginUpdateStatus>> {
        if (BuildConfig.FLAVOR != "standard") return Pair(false, emptyList())
        return withContext(Dispatchers.IO) {
            val appUpdateDeferred = async {
                fetchLatestTag(Links.GITHUB_RELEASES_API)
            }

            // fetch the latest tag of every plugin, not only installed ones, so a plugin installed later
            // shows its update state without waiting for the next check
            val latestTagsDeferred = PLUGINS.map { plugin -> plugin to async { fetchLatestTag(plugin.repoReleasesApi) } }

            val appRemoteTag = appUpdateDeferred.await()
            val latestTags = latestTagsDeferred.map { (plugin, tag) -> plugin to tag.await() }

            context.prefs().edit {
                putLong(PREF_LAST_CHECK, System.currentTimeMillis())
                if (appRemoteTag != null) {
                    putString(PREF_APP_LATEST_TAG, appRemoteTag)
                }
                for ((plugin, tag) in latestTags) {
                    if (tag != null) putString("$PREF_PLUGIN_LATEST_PREFIX${plugin.id}", tag)
                }
            }

            _updateState.value++
            Pair(hasAppUpdate(context), getInstalledPluginStatuses(context))
        }
    }

    fun getInstalledPluginStatuses(context: Context): List<PluginUpdateStatus> {
        val prefs = context.prefs()
        return PLUGINS.mapNotNull { plugin ->
            val installedVer = plugin.getInstalledVersion(context) ?: return@mapNotNull null
            val latestVer = prefs.getString("$PREF_PLUGIN_LATEST_PREFIX${plugin.id}", null)
            val isAvail = latestVer != null && isNewerVersion(installedVer, latestVer)
            PluginUpdateStatus(
                plugin = plugin,
                installedVersion = installedVer,
                latestVersion = latestVer,
                isUpdateAvailable = isAvail
            )
        }
    }

    private fun fetchLatestTag(apiUrl: String): String? {
        return try {
            val url = URL(apiUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("User-Agent", "LeanType-Android")
            conn.connect()
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                json.optString("tag_name", "").trim().ifEmpty { null }
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check update from $apiUrl: ${e.message}")
            null
        }
    }
}
