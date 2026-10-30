package helium314.keyboard.settings.preferences

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object PluginUpdateHelper {
    private val tagNameRegex = "\"tag_name\"\\s*:\\s*\"([^\"]+)\"".toRegex()

    fun isUpdateAvailable(local: String, remote: String): Boolean {
        val cleanLocal = local.removePrefix("v").trim()
        val cleanRemote = remote.removePrefix("v").trim()
        if (cleanLocal == cleanRemote) return false

        val localParts = cleanLocal.split(".").mapNotNull { it.toIntOrNull() }
        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(localParts.size, remoteParts.size)
        for (i in 0 until maxLength) {
            val localPart = localParts.getOrElse(i) { 0 }
            val remotePart = remoteParts.getOrElse(i) { 0 }
            if (remotePart > localPart) return true
            if (localPart > remotePart) return false
        }
        return false
    }

    suspend fun fetchLatestReleaseTag(repoName: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$repoName/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "HeliboardL")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.connect()
            try {
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    tagNameRegex.find(response)?.groupValues?.getOrNull(1)
                } else {
                    null
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
