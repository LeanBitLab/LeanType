// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.compat

import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.view.inputmethod.EditorInfo
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

object AppQuirksManager {
    private const val TAG = "AppQuirksManager"

    const val AUTOCORRECT_DEFAULT = 0
    const val AUTOCORRECT_FORCE_ENABLE = 1
    const val AUTOCORRECT_FORCE_DISABLE = 2

    private val userQuirks = ConcurrentHashMap<String, AppQuirk>()
    private var prefs: SharedPreferences? = null
    @Volatile private var isInitialized = false

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        BrowserDetector.init(context)
        try {
            val p = context.prefs()
            prefs = p
            loadFromPrefs(p)
            isInitialized = true
            Log.i(TAG, "AppQuirksManager initialized with ${userQuirks.size} user profile overrides")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize AppQuirksManager prefs", e)
        }
    }

    fun defaultQuirk(packageName: String): AppQuirk? {
        return when {
            // Google decided to set inputType multiline and imeOptions no_enter_action
            // on their search bar in Pixel launcher, and keyboards ignore the flag to perform search.
            packageName == "com.google.android.apps.nexuslauncher" -> AppQuirk(
                packageName = packageName,
                stripNoEnterAction = true
            )
            // Obsidian uses a Chromium WebView editor where synchronous cursor IPC causes backspace lag;
            // treat it as a web editor for direct, non-blocking input and deletion.
            packageName == "md.obsidian" || packageName.startsWith("md.obsidian.") -> AppQuirk(
                packageName = packageName,
                forceWebEditor = true
            )
            else -> null
        }
    }

    /**
     * Returns the effective quirk for a package, prioritizing user overrides over built-in defaults.
     */
    fun getEffectiveQuirk(packageName: String?): AppQuirk? {
        if (packageName == null) return null
        return userQuirks[packageName] ?: defaultQuirk(packageName)
    }

    /**
     * Returns whether an application is treated as a web editor (WebView / Browser).
     */
    fun isWebEditor(packageName: String?): Boolean {
        if (packageName == null) return false
        val quirk = getEffectiveQuirk(packageName)
        if (quirk?.forceWebEditor == true) return true
        return BrowserDetector.isWebBrowser(packageName)
    }

    /**
     * Adjusts inputType for web fields or apps configured with web editor quirks.
     */
    fun adjustInputType(inputType: Int, packageName: String?): Int {
        if (isWebEditor(packageName)) {
            // Firefox and forks (and any app configured for web compatibility) don't set these flags,
            // so we want to force them for most text fields on websites
            if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return inputType
            if (inputType and InputType.TYPE_MASK_VARIATION != 0) return inputType // if any variation is specified we leave it (URL, email, password, ...)
            // looks like most non-password text fields on websites are either IME_MULTI_LINE or IME_MULTI_LINE + AUTO_CORRECT + CAP_SENTENCES
            if (inputType and InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE == 0) return inputType
            // for the AUTO_CORRECT flag we assume suggestions are safe and only add WEB_EDIT_TEXT
            if (inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT != 0) return inputType or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
            // for all others we also add NO_SUGGESTIONS to avoid JS messing with the composing text
            return inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
        }
        return inputType
    }

    /**
     * Adjusts imeOptions according to user overrides or built-in quirks.
     */
    fun adjustImeOptions(imeOptions: Int, packageName: String?): Int {
        val quirk = getEffectiveQuirk(packageName) ?: return imeOptions
        var result = imeOptions
        if (quirk.forceEnterAction != null) {
            val action = quirk.forceEnterAction
            result = if (action == EditorInfo.IME_ACTION_NONE) {
                (result and EditorInfo.IME_MASK_ACTION.inv()) or EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
            } else {
                (result and EditorInfo.IME_MASK_ACTION.inv() and EditorInfo.IME_FLAG_NO_ENTER_ACTION.inv()) or action
            }
        } else if (quirk.stripNoEnterAction) {
            if (result and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) {
                result -= EditorInfo.IME_FLAG_NO_ENTER_ACTION
            }
        }
        return result
    }

    /**
     * Returns true if user configured this app to force incognito mode.
     */
    fun isIncognitoApp(packageName: String?): Boolean {
        if (packageName == null) return false
        return getEffectiveQuirk(packageName)?.forceIncognito == true
    }

    /**
     * Returns true if user configured this app to force non-incognito mode.
     */
    fun isNonIncognitoApp(packageName: String?): Boolean {
        if (packageName == null) return false
        return getEffectiveQuirk(packageName)?.forceNonIncognito == true
    }

    /**
     * Returns whether direct text commit (without composing spans) should be forced.
     */
    fun isDirectCommitApp(packageName: String?): Boolean {
        if (packageName == null) return false
        return getEffectiveQuirk(packageName)?.forceDirectCommit == true
    }

    /**
     * Returns whether symbols like underscore should be composed as word connectors.
     */
    fun isSymbolComposingApp(packageName: String?): Boolean {
        if (packageName == null) return false
        return getEffectiveQuirk(packageName)?.allowSymbolComposing == true
    }

    /**
     * Returns whether automatic spacing is disabled for this application.
     */
    fun isAutoSpaceDisabled(packageName: String?): Boolean {
        if (packageName == null) return false
        return getEffectiveQuirk(packageName)?.disableAutoSpace == true
    }

    /**
     * Returns whether keyboard display is allowed for TYPE_NULL fields in this package.
     */
    fun isTypeNullKeyboardAllowed(packageName: String?): Boolean {
        if (packageName == null) return false
        val pkg = packageName.lowercase()
        if (pkg == "com.termux" || pkg.startsWith("com.termux.") || pkg.endsWith(".termux") || pkg.contains("terminal")) return true
        if (pkg.contains("dialer") || pkg.contains("contacts") || pkg.contains("phone")) return true
        if (isWebEditor(packageName)) return true
        return getEffectiveQuirk(packageName)?.allowTypeNullKeyboard == true
    }

    /**
     * Returns the auto-correction override mode for this package:
     * AUTOCORRECT_DEFAULT (0), AUTOCORRECT_FORCE_ENABLE (1), or AUTOCORRECT_FORCE_DISABLE (2).
     */
    fun getAutoCorrectionOverride(packageName: String?): Int {
        if (packageName == null) return AUTOCORRECT_DEFAULT
        return getEffectiveQuirk(packageName)?.autoCorrectionMode ?: AUTOCORRECT_DEFAULT
    }

    /**
     * Returns whether suggestion strip is hidden for this application.
     */
    fun isSuggestionStripHidden(packageName: String?): Boolean {
        if (packageName == null) return false
        return getEffectiveQuirk(packageName)?.hideSuggestionStrip == true
    }

    /**
     * Returns whether suggestions are always forced on for this application.
     */
    fun isAlwaysShowSuggestions(packageName: String?): Boolean {
        if (packageName == null) return false
        return getEffectiveQuirk(packageName)?.alwaysShowSuggestions == true
    }

    /**
     * Returns whether toolbar is hidden for this application.
     */
    fun isToolbarHidden(packageName: String?): Boolean {
        if (packageName == null) return false
        return getEffectiveQuirk(packageName)?.hideToolbar == true
    }

    /**
     * Returns whether word replacement should use selection and commit instead of composition.
     */
    fun shouldUseSelectionForWordReplacement(packageName: String?): Boolean {
        if (packageName == null) return false
        val quirk = getEffectiveQuirk(packageName)
        if (quirk?.useSelectionForWordReplacement == true) return true
        return BrowserDetector.isChromiumBrowser(packageName)
    }

    fun getUserQuirk(packageName: String): AppQuirk? = userQuirks[packageName]

    fun getAllUserQuirks(): Map<String, AppQuirk> = HashMap(userQuirks)

    fun saveQuirk(quirk: AppQuirk) {
        if (quirk.hasCustomSettings()) {
            userQuirks[quirk.packageName] = quirk
        } else {
            userQuirks.remove(quirk.packageName)
        }
        persist()
    }

    fun removeQuirk(packageName: String) {
        userQuirks.remove(packageName)
        persist()
    }

    private fun loadFromPrefs(sp: SharedPreferences) {
        userQuirks.clear()
        val raw = sp.getString(Settings.PREF_APP_QUIRKS, null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val quirk = AppQuirk.fromJson(obj)
                if (quirk.packageName.isNotEmpty() && quirk.hasCustomSettings()) {
                    userQuirks[quirk.packageName] = quirk
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load app quirks from prefs", e)
        }
    }

    private fun persist() {
        val sp = prefs ?: return
        try {
            val array = JSONArray()
            for (quirk in userQuirks.values) {
                if (quirk.hasCustomSettings()) {
                    array.put(quirk.toJson())
                }
            }
            sp.edit().putString(Settings.PREF_APP_QUIRKS, array.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist app quirks", e)
        }
    }

    internal fun resetForTesting(context: Context? = null) {
        userQuirks.clear()
        isInitialized = false
        if (context != null) {
            init(context)
        }
    }
}
