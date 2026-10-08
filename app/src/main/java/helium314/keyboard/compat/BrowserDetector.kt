// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.compat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import helium314.keyboard.latin.utils.Log
import java.util.Collections

object BrowserDetector {
    private const val TAG = "BrowserDetector"

    // Comprehensive fallback list for instant cold-starts and Robolectric unit tests
    private val KNOWN_BROWSER_PACKAGES = setOf(
        // Standard Browsers
        "com.android.chrome", "org.chromium.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "org.mozilla.fennec_fdroid", "org.mozilla.fenix", "org.mozilla.firefox_beta", "org.mozilla.focus",
        "org.mozilla.klar", "org.mozilla.firefox", "org.ironfoxoss.ironfox", "net.waterfox.android.release",
        "io.github.forkmaintainers.iceraven", "com.zen.web.tools.browser",
        "com.brave.browser", "com.vivaldi.browser", "com.kiwibrowser.browser", "com.opera.browser",
        "mark.via.gp", "com.microsoft.emmx", "org.bromite.bromite", "com.duckduckgo.mobile.android",
        "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
        // Privacy / Hardened / Forks
        "app.vanadium.browser", "org.cromite.cromite", "us.spotco.fennec_dos",
        "org.torproject.torbrowser", "org.torproject.torbrowser_alpha",
        "org.mozilla.fenix.nightly", "com.opera.mini.native", "com.opera.touch", "com.opera.gx",
        "com.vivaldi.browser.snapshot", "company.thebrowser.arc", "jp.ablaze.floorp",
        "net.mullvad.mullvadbrowser", "com.mycompany.app.soulbrowser", "com.ecosia.android",
        "com.aloha.browser", "com.aloha.browser.beta", "com.android.browser"
    )

    private val discoveredBrowsers: MutableSet<String> = Collections.synchronizedSet(HashSet(KNOWN_BROWSER_PACKAGES))
    @Volatile private var isInitialized = false

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PackageManager.MATCH_ALL
            } else {
                0
            }
            val resolveInfos = context.packageManager.queryIntentActivities(intent, flags)
            for (info in resolveInfos) {
                val pkg = info.activityInfo?.packageName
                if (!pkg.isNullOrEmpty()) {
                    discoveredBrowsers.add(pkg)
                }
            }
            isInitialized = true
            Log.i(TAG, "BrowserDetector initialized with ${discoveredBrowsers.size} known/discovered browsers")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query browser packages via PackageManager, falling back to defaults", e)
        }
    }

    private val GECKO_BROWSER_PACKAGES = setOf(
        "org.mozilla.fennec_fdroid", "org.mozilla.fenix", "org.mozilla.firefox_beta", "org.mozilla.focus",
        "org.mozilla.klar", "org.mozilla.firefox", "org.ironfoxoss.ironfox", "net.waterfox.android.release",
        "io.github.forkmaintainers.iceraven", "com.zen.web.tools.browser",
        "us.spotco.fennec_dos", "org.torproject.torbrowser", "org.torproject.torbrowser_alpha",
        "org.mozilla.fenix.nightly", "jp.ablaze.floorp", "net.mullvad.mullvadbrowser"
    )

    fun isWebBrowser(packageName: String?): Boolean {
        return packageName != null && discoveredBrowsers.contains(packageName)
    }

    fun isGeckoBrowser(packageName: String?): Boolean {
        if (packageName == null) return false
        return GECKO_BROWSER_PACKAGES.contains(packageName) || packageName.startsWith("org.mozilla.")
    }

    fun isChromiumBrowser(packageName: String?): Boolean {
        if (packageName == null) return false
        if (isGeckoBrowser(packageName)) return false
        return isWebBrowser(packageName) ||
                packageName.contains("chrome") ||
                packageName.contains("chromium") ||
                packageName.contains("brave") ||
                packageName.contains("edge") ||
                packageName.contains("opera") ||
                packageName.contains("vivaldi")
    }

    internal fun resetForTesting() {
        discoveredBrowsers.clear()
        discoveredBrowsers.addAll(KNOWN_BROWSER_PACKAGES)
        isInitialized = false
    }
}
