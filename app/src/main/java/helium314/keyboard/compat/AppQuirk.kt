// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.compat

import org.json.JSONObject

/**
 * Data model representing per-application compatibility quirks and overrides.
 */
data class AppQuirk(
    val packageName: String,
    val forceWebEditor: Boolean = false,
    val stripNoEnterAction: Boolean = false,
    val forceEnterAction: Int? = null,
    val forceIncognito: Boolean = false,
    val forceNonIncognito: Boolean = false,
    val forceDirectCommit: Boolean = false,
    val allowSymbolComposing: Boolean = false,
    val disableAutoSpace: Boolean = false,
    val allowTypeNullKeyboard: Boolean = false,
    val autoCorrectionMode: Int? = null,
    val hideSuggestionStrip: Boolean = false,
    val hideToolbar: Boolean = false,
    val alwaysShowSuggestions: Boolean = false,
    val useSelectionForWordReplacement: Boolean = false,
) {
    fun hasCustomSettings(): Boolean =
        forceWebEditor || stripNoEnterAction || (forceEnterAction != null) || forceIncognito ||
                forceNonIncognito || forceDirectCommit || allowSymbolComposing || disableAutoSpace ||
                allowTypeNullKeyboard || (autoCorrectionMode != null) || hideSuggestionStrip || hideToolbar ||
                alwaysShowSuggestions || useSelectionForWordReplacement

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("packageName", packageName)
        if (forceWebEditor) json.put("forceWebEditor", true)
        if (stripNoEnterAction) json.put("stripNoEnterAction", true)
        if (forceEnterAction != null) json.put("forceEnterAction", forceEnterAction)
        if (forceIncognito) json.put("forceIncognito", true)
        if (forceNonIncognito) json.put("forceNonIncognito", true)
        if (forceDirectCommit) json.put("forceDirectCommit", true)
        if (allowSymbolComposing) json.put("allowSymbolComposing", true)
        if (disableAutoSpace) json.put("disableAutoSpace", true)
        if (allowTypeNullKeyboard) json.put("allowTypeNullKeyboard", true)
        if (autoCorrectionMode != null) json.put("autoCorrectionMode", autoCorrectionMode)
        if (hideSuggestionStrip) json.put("hideSuggestionStrip", true)
        if (hideToolbar) json.put("hideToolbar", true)
        if (alwaysShowSuggestions) json.put("alwaysShowSuggestions", true)
        if (useSelectionForWordReplacement) json.put("useSelectionForWordReplacement", true)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): AppQuirk {
            val packageName = json.optString("packageName", "")
            val forceWebEditor = json.optBoolean("forceWebEditor", false)
            val stripNoEnterAction = json.optBoolean("stripNoEnterAction", false)
            val forceEnterAction = if (json.has("forceEnterAction")) json.getInt("forceEnterAction") else null
            val forceIncognito = json.optBoolean("forceIncognito", false)
            val forceNonIncognito = json.optBoolean("forceNonIncognito", false)
            val forceDirectCommit = json.optBoolean("forceDirectCommit", false)
            val allowSymbolComposing = json.optBoolean("allowSymbolComposing", false)
            val disableAutoSpace = json.optBoolean("disableAutoSpace", false)
            val allowTypeNullKeyboard = json.optBoolean("allowTypeNullKeyboard", false)
            val autoCorrectionMode = if (json.has("autoCorrectionMode")) json.getInt("autoCorrectionMode") else null
            val hideSuggestionStrip = json.optBoolean("hideSuggestionStrip", false)
            val hideToolbar = json.optBoolean("hideToolbar", false)
            val alwaysShowSuggestions = json.optBoolean("alwaysShowSuggestions", false)
            val useSelectionForWordReplacement = json.optBoolean("useSelectionForWordReplacement", false)
            return AppQuirk(
                packageName = packageName,
                forceWebEditor = forceWebEditor,
                stripNoEnterAction = stripNoEnterAction,
                forceEnterAction = forceEnterAction,
                forceIncognito = forceIncognito,
                forceNonIncognito = forceNonIncognito,
                forceDirectCommit = forceDirectCommit,
                allowSymbolComposing = allowSymbolComposing,
                disableAutoSpace = disableAutoSpace,
                allowTypeNullKeyboard = allowTypeNullKeyboard,
                autoCorrectionMode = autoCorrectionMode,
                hideSuggestionStrip = hideSuggestionStrip,
                hideToolbar = hideToolbar,
                alwaysShowSuggestions = alwaysShowSuggestions,
                useSelectionForWordReplacement = useSelectionForWordReplacement,
            )
        }
    }
}
