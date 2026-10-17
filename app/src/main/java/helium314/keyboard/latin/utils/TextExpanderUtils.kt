/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.utils

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import helium314.keyboard.latin.R

object TextExpanderUtils {
    const val PREF_ENABLED = "pref_text_expander_enabled"
    const val PREF_PREFIX = "pref_text_expander_prefix"
    const val PREF_IMMEDIATE = "pref_text_expander_immediate"
    const val PREF_BACKSPACE_REVERTS = "pref_text_expander_backspace_reverts"
    const val PREF_DATA = "pref_text_expander_data"
    const val REGEX_PREFIX = "__regex__:"

    // Pre-compiled regexes for performance
    private val CITATION_REGEX = Regex(
        """\[(?:\s*\d+(?:\s*[,;\-–—]\s*\d+)*\s*|\s*note\s*\d+\s*|\s*citation\s+needed\s*|\s*edit\s*|\s*source\s*)\]""",
        RegexOption.IGNORE_CASE
    )
    private val SPACE_PUNCT_REGEX = Regex("""\s+([.,;:!?])""")
    private val MULTI_SPACE_REGEX = Regex("""[^\S\r\n]{2,}""")
    private val NEWLINE_REGEX = Regex("""[\r\n]+""")
    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val NON_ALPHANUM_DASH_REGEX = Regex("""[^\w\s-]""")
    private val SPACE_UNDERSCORE_REGEX = Regex("""[\s_]+""")
    private val MULTI_DASH_REGEX = Regex("""-+""")
    private val NON_ALPHANUM_REGEX = Regex("""[^\w\s]""")
    private val SPACE_DASH_REGEX = Regex("""[\s-]+""")
    private val MULTI_UNDERSCORE_REGEX = Regex("""_+""")
    private val URL_REGEX = Regex("""https?://\S+""")
    private val TWO_OR_MORE_SPACES_REGEX = Regex("""\s{2,}""")
    private val DATE_REGEX = Regex("%date(?::([a-zA-Z0-9_():, ./-]+))?%")
    private val TOMORROW_REGEX = Regex("%tomorrow(?::([a-zA-Z0-9_():, ./-]+))?%")
    private val CLIPBOARD_REGEX = Regex("%clipboard(?::([a-zA-Z0-9_():, -]+))?%")
    private val BULLETS_REGEX = Regex("%bullets(?:_(\\d+))?%")
    private val LIST_REGEX = Regex("%list(?:_(\\d+))?%")
    private val SPACE_UNDERSCORE_DASH_REGEX = Regex("""[\s_-]+""")

    @Volatile private var cachedEnabled: Boolean? = null
    @Volatile private var cachedImmediateEnabled: Boolean? = null
    @Volatile private var cachedRawJson: String? = null
    @Volatile private var cachedShortcuts: Map<String, ShortcutEntry>? = null
    @Volatile private var cachedCompiledList: List<CompiledShortcut>? = null
    @Volatile private var cachedPrefs: SharedPreferences? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == null) {
            cachedEnabled = prefs.getBoolean(PREF_ENABLED, false)
            cachedImmediateEnabled = prefs.getBoolean(PREF_IMMEDIATE, false)
            cachedRawJson = null
            cachedShortcuts = null
            cachedCompiledList = null
            return@OnSharedPreferenceChangeListener
        }
        when (key) {
            PREF_ENABLED -> cachedEnabled = prefs.getBoolean(PREF_ENABLED, false)
            PREF_IMMEDIATE -> cachedImmediateEnabled = prefs.getBoolean(PREF_IMMEDIATE, false)
            PREF_DATA -> {
                cachedRawJson = null
                cachedShortcuts = null
                cachedCompiledList = null
            }
        }
    }

    private fun ensureListener(prefs: SharedPreferences) {
        if (cachedPrefs !== prefs) {
            synchronized(this) {
                if (cachedPrefs !== prefs) {
                    prefs.registerOnSharedPreferenceChangeListener(prefListener)
                    cachedPrefs = prefs
                    cachedEnabled = prefs.getBoolean(PREF_ENABLED, false)
                    cachedImmediateEnabled = prefs.getBoolean(PREF_IMMEDIATE, false)
                    cachedRawJson = null
                    cachedShortcuts = null
                    cachedCompiledList = null
                }
            }
        }
    }

    fun isEnabled(context: Context): Boolean {
        val prefs = context.prefs()
        ensureListener(prefs)
        return cachedEnabled ?: prefs.getBoolean(PREF_ENABLED, false).also { cachedEnabled = it }
    }

    fun isImmediateEnabled(context: Context): Boolean {
        val prefs = context.prefs()
        ensureListener(prefs)
        return cachedImmediateEnabled ?: prefs.getBoolean(PREF_IMMEDIATE, false).also { cachedImmediateEnabled = it }
    }

    fun isBackspaceRevertsEnabled(context: Context): Boolean {
        return context.prefs().getBoolean(PREF_BACKSPACE_REVERTS, false)
    }

    data class ShortcutEntry(
        val template: String,
        val prefix: String = ""
    )

    data class ExpandedResult(
        val expandedText: String,
        val prefixLength: Int,
        val matchedString: String
    )

    private data class CompiledShortcut(
        val key: String,
        val cleanKey: String,
        val isRegex: Boolean,
        val regex: Regex?,
        val entry: ShortcutEntry
    )

    fun clearCache() {
        cachedEnabled = null
        cachedImmediateEnabled = null
        cachedRawJson = null
        cachedShortcuts = null
        cachedCompiledList = null
        cachedPrefs = null
    }

    fun getShortcuts(context: Context): Map<String, ShortcutEntry> {
        val prefs = context.prefs()
        ensureListener(prefs)
        val currentShortcuts = cachedShortcuts
        if (currentShortcuts != null) {
            return currentShortcuts
        }
        val jsonStr = prefs.getString(PREF_DATA, "{}") ?: "{}"
        if (cachedRawJson != null && cachedRawJson == jsonStr && cachedShortcuts != null) {
            return cachedShortcuts!!
        }
        val map = mutableMapOf<String, ShortcutEntry>()
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val valueObj = json.get(key)
                if (valueObj is JSONObject) {
                    val template = valueObj.optString("template", "")
                    val prefix = valueObj.optString("prefix", "")
                    map[key] = ShortcutEntry(template, prefix)
                } else {
                    map[key] = ShortcutEntry(valueObj.toString(), "")
                }
            }
        } catch (e: java.lang.Exception) {
            // fallback
        }
        val compiled = map.map { (key, entry) ->
            val isRegex = key.startsWith(REGEX_PREFIX)
            val cleanKey = if (isRegex) key.substring(REGEX_PREFIX.length) else key
            val regex = if (isRegex) {
                try {
                    Regex(cleanKey, RegexOption.IGNORE_CASE)
                } catch (e: Exception) {
                    null
                }
            } else null
            CompiledShortcut(key, cleanKey, isRegex, regex, entry)
        }
        cachedRawJson = jsonStr
        cachedShortcuts = map
        cachedCompiledList = compiled
        return map
    }

    fun saveShortcuts(context: Context, map: Map<String, ShortcutEntry>) {
        try {
            val json = JSONObject()
            for ((key, entry) in map) {
                val obj = JSONObject()
                obj.put("template", entry.template)
                obj.put("prefix", entry.prefix)
                json.put(key, obj)
            }
            val jsonStr = json.toString()
            val prefs = context.prefs()
            ensureListener(prefs)
            prefs.edit().putString(PREF_DATA, jsonStr).commit()
            clearCache()
        } catch (e: java.lang.Exception) {
            // fail silently
        }
    }

    fun cleanCitations(text: String): String {
        var cleaned = CITATION_REGEX.replace(text, "")
        cleaned = cleaned.replace(SPACE_PUNCT_REGEX, "$1")
        cleaned = cleaned.replace(MULTI_SPACE_REGEX, " ")
        return cleaned
    }

    fun parseModifierTokens(modifiersString: String): List<String> {
        if (modifiersString.isBlank()) return emptyList()
        val modifierTokens = mutableListOf<String>()
        var currentToken = StringBuilder()
        var parenDepth = 0
        for (ch in modifiersString) {
            if (ch == '(') parenDepth++
            else if (ch == ')') parenDepth--
            if (ch == ':' && parenDepth == 0) {
                if (currentToken.isNotBlank()) modifierTokens.add(currentToken.toString().trim())
                currentToken = StringBuilder()
            } else {
                currentToken.append(ch)
            }
        }
        if (currentToken.isNotBlank()) modifierTokens.add(currentToken.toString().trim())
        return modifierTokens
    }

    fun resolveDateWithModifiers(date: Date, mods: String?): String {
        if (mods.isNullOrBlank()) {
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        }
        val tokens = parseModifierTokens(mods)
        var formatPattern: String? = null
        var isNamedFormat = false
        var namedFormatType = ""
        val textModifiers = mutableListOf<String>()

        val stringModifiers = setOf(
            "clean", "nocite", "singleline", "oneline", "trim",
            "lower", "upper", "title", "slug", "kebab", "snake",
            "camel", "unquote", "nourl"
        )

        for (token in tokens) {
            when {
                token.startsWith("format(", ignoreCase = true) && token.endsWith(")") -> {
                    formatPattern = token.substring(7, token.length - 1).trim()
                }
                token.equals("short", ignoreCase = true) ||
                token.equals("medium", ignoreCase = true) ||
                token.equals("long", ignoreCase = true) ||
                token.equals("full", ignoreCase = true) ||
                token.equals("iso", ignoreCase = true) -> {
                    isNamedFormat = true
                    namedFormatType = token.lowercase(Locale.US)
                }
                stringModifiers.contains(token.lowercase(Locale.US)) ||
                token.startsWith("replace(", ignoreCase = true) -> {
                    textModifiers.add(token)
                }
                else -> {
                    val isValid = runCatching { SimpleDateFormat(token, Locale.getDefault()) }.isSuccess
                    if (isValid && formatPattern == null) {
                        formatPattern = token
                    } else {
                        textModifiers.add(token)
                    }
                }
            }
        }

        var dateString = when {
            formatPattern != null -> {
                runCatching { SimpleDateFormat(formatPattern, Locale.getDefault()).format(date) }
                    .getOrElse { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date) }
            }
            isNamedFormat -> {
                when (namedFormatType) {
                    "short" -> java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, Locale.getDefault()).format(date)
                    "medium" -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, Locale.getDefault()).format(date)
                    "long" -> java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG, Locale.getDefault()).format(date)
                    "full" -> java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL, Locale.getDefault()).format(date)
                    "iso" -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
                    else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
                }
            }
            else -> {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
            }
        }

        if (textModifiers.isNotEmpty()) {
            dateString = applyModifiers(dateString, textModifiers.joinToString(":"))
        }
        return dateString
    }

    fun applyModifiers(input: String, modifiersString: String): String {
        if (modifiersString.isBlank()) return input
        var text = input
        val modifierTokens = parseModifierTokens(modifiersString)

        for (mod in modifierTokens) {
            text = when {
                mod.equals("clean", ignoreCase = true) || mod.equals("nocite", ignoreCase = true) -> cleanCitations(text)
                mod.equals("singleline", ignoreCase = true) || mod.equals("oneline", ignoreCase = true) -> text.replace(NEWLINE_REGEX, " ")
                mod.equals("trim", ignoreCase = true) -> text.trim()
                mod.equals("lower", ignoreCase = true) -> text.lowercase(Locale.getDefault())
                mod.equals("upper", ignoreCase = true) -> text.uppercase(Locale.getDefault())
                mod.equals("title", ignoreCase = true) -> text.split(WHITESPACE_REGEX).joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
                mod.equals("slug", ignoreCase = true) || mod.equals("kebab", ignoreCase = true) -> {
                    text.trim().lowercase(Locale.getDefault())
                        .replace(NON_ALPHANUM_DASH_REGEX, "")
                        .replace(SPACE_UNDERSCORE_REGEX, "-")
                        .replace(MULTI_DASH_REGEX, "-")
                }
                mod.equals("snake", ignoreCase = true) -> {
                    text.trim().lowercase(Locale.getDefault())
                        .replace(NON_ALPHANUM_REGEX, "")
                        .replace(SPACE_DASH_REGEX, "_")
                        .replace(MULTI_UNDERSCORE_REGEX, "_")
                }
                mod.equals("camel", ignoreCase = true) -> {
                    val words = text.trim().split(SPACE_UNDERSCORE_DASH_REGEX)
                    words.mapIndexed { index, w ->
                        if (index == 0) w.lowercase(Locale.getDefault())
                        else w.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }.joinToString("")
                }
                mod.equals("unquote", ignoreCase = true) -> {
                    var trimmed = text.trim()
                    if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
                        trimmed = trimmed.substring(1, trimmed.length - 1)
                    }
                    trimmed
                }
                mod.equals("nourl", ignoreCase = true) -> text.replace(URL_REGEX, "").replace(TWO_OR_MORE_SPACES_REGEX, " ")
                mod.startsWith("replace(", ignoreCase = true) && mod.endsWith(")") -> {
                    val inner = mod.substring(8, mod.length - 1)
                    val parts = inner.split(",", limit = 2)
                    if (parts.isNotEmpty()) {
                        val pattern = parts[0].trim()
                        val replacement = if (parts.size > 1) parts[1] else ""
                        try {
                            text.replace(Regex(pattern), replacement)
                        } catch (_: Exception) {
                            text
                        }
                    } else text
                }
                else -> text
            }
        }
        return text
    }

    private fun getClipboardText(context: Context): String {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard?.hasPrimaryClip() == true) {
                val rawText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                if (rawText.length > 5000) rawText.substring(0, 5000) else rawText
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    fun expand(template: String, context: Context): String {
        var result = template

        // tab character; %tabkey% (key press) and %enter% are handled when committing
        if (result.contains("%tab%")) result = result.replace("%tab%", "\t")

        // Resolve %date[:modifiers]%
        if (result.contains("%date")) {
            result = DATE_REGEX.replace(result) { match ->
                val mods = match.groups[1]?.value
                resolveDateWithModifiers(Date(), mods)
            }
        }

        // Resolve %time%
        if (result.contains("%time%")) {
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            result = result.replace("%time%", timeStr)
        }

        // Resolve %clipboard_clean% / %clipboard_nocite% aliases
        if (result.contains("%clipboard_clean%") || result.contains("%clipboard_nocite%")) {
            val cleanClip = cleanCitations(getClipboardText(context))
            result = result.replace("%clipboard_clean%", cleanClip).replace("%clipboard_nocite%", cleanClip)
        }

        // Resolve %clipboard[:modifiers]%
        if (result.contains("%clipboard")) {
            result = CLIPBOARD_REGEX.replace(result) { match ->
                val mods = match.groups[1]?.value
                val rawClip = getClipboardText(context)
                if (mods != null) applyModifiers(rawClip, mods) else rawClip
            }
        }

        // Resolve %day%
        if (result.contains("%day%")) {
            val dayStr = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
            result = result.replace("%day%", dayStr)
        }

        // Resolve %time12%
        if (result.contains("%time12%")) {
            val time12Str = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            result = result.replace("%time12%", time12Str)
        }

        // Resolve %month%
        if (result.contains("%month%")) {
            val monthStr = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())
            result = result.replace("%month%", monthStr)
        }

        // Resolve %year%
        if (result.contains("%year%")) {
            val yearStr = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
            result = result.replace("%year%", yearStr)
        }

        // Resolve %week%
        if (result.contains("%week%")) {
            val weekStr = SimpleDateFormat("w", Locale.getDefault()).format(Date())
            result = result.replace("%week%", weekStr)
        }

        // Resolve %battery%
        if (result.contains("%battery%")) {
            val batteryStr = try {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
                val level = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                if (level != -1) "$level%" else ""
            } catch (e: Exception) {
                ""
            }
            result = result.replace("%battery%", batteryStr)
        }

        // Resolve %language%
        if (result.contains("%language%")) {
            val imeManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            val activeSubtype = imeManager?.currentInputMethodSubtype
            val languageStr = activeSubtype?.getDisplayName(context, context.packageName, context.applicationInfo)?.toString()
                ?: Locale.getDefault().getDisplayName(Locale.getDefault())
            result = result.replace("%language%", languageStr)
        }

        // Resolve %greeting%
        if (result.contains("%greeting%")) {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val greeting = when (hour) {
                in 5..11 -> context.getString(R.string.text_expander_greeting_morning)
                in 12..16 -> context.getString(R.string.text_expander_greeting_afternoon)
                in 17..21 -> context.getString(R.string.text_expander_greeting_evening)
                else -> context.getString(R.string.text_expander_greeting_night)
            }
            result = result.replace("%greeting%", greeting)
        }

        // Resolve %tomorrow[:modifiers]%
        if (result.contains("%tomorrow")) {
            val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
            result = TOMORROW_REGEX.replace(result) { match ->
                val mods = match.groups[1]?.value
                resolveDateWithModifiers(cal.time, mods)
            }
        }

        // Resolve %bullets% with optional count
        if (result.contains("%bullets")) {
            result = BULLETS_REGEX.replace(result) { match ->
                val count = match.groups[1]?.value?.toIntOrNull() ?: 3
                if (count <= 0) ""
                else {
                    val sb = java.lang.StringBuilder()
                    sb.append("• %cursor%")
                    for (i in 2..count) {
                        sb.append("\n• ")
                    }
                    sb.toString()
                }
            }
        }

        // Resolve %list% with optional count
        if (result.contains("%list")) {
            result = LIST_REGEX.replace(result) { match ->
                val count = match.groups[1]?.value?.toIntOrNull() ?: 3
                if (count <= 0) ""
                else {
                    val sb = java.lang.StringBuilder()
                    sb.append("1. %cursor%")
                    for (i in 2..count) {
                        sb.append("\n$i. ")
                    }
                    sb.toString()
                }
            }
        }

        return result
    }

    fun isPrefixOfNonRegexShortcut(
        word: String,
        textBeforeCursor: String,
        context: Context,
    ): Boolean =
        getShortcuts(context).any { (key, entry) ->
            if (key.startsWith(REGEX_PREFIX) || key.length < entry.prefix.length) {
                false
            } else {
                val shortcut = key.substring(entry.prefix.length)
                !shortcut.equals(word, ignoreCase = true) &&
                    shortcut.startsWith(word, ignoreCase = true) &&
                    textBeforeCursor.endsWith(entry.prefix + word, ignoreCase = true)
            }
        }

    fun getExpandedWordForTyped(word: String?, textBeforeCursor: String?, context: Context): ExpandedResult? {
        if (textBeforeCursor == null || !isEnabled(context)) return null
        getShortcuts(context)
        val compiledList = cachedCompiledList ?: return null
        val fullText = if (word != null && !textBeforeCursor.endsWith(word, ignoreCase = true)) {
            textBeforeCursor + word
        } else {
            textBeforeCursor
        }
        
        for (item in compiledList) {
            val entry = item.entry
            if (item.isRegex) {
                val regex = item.regex ?: continue
                val prefix = entry.prefix
                if (word != null) {
                    if (regex.matches(fullText)) {
                        try {
                            val replaced = regex.replace(fullText, entry.template)
                            val prefixLength = if (fullText.length > word.length) fullText.length - word.length else prefix.length
                            return ExpandedResult(expand(replaced, context), prefixLength, fullText)
                        } catch (e: java.lang.Exception) {
                            // ignore
                        }
                    }
                }
                try {
                    val match = regex.findAll(fullText).lastOrNull { it.range.last == fullText.length - 1 }
                    if (match != null && match.value.isNotEmpty()) {
                        val matchedString = match.value
                        val replaced = regex.replace(matchedString, entry.template)
                        val extraPrefix = if (word != null && matchedString.endsWith(word, ignoreCase = true)) {
                            matchedString.length - word.length
                        } else 0
                        val effectivePrefixLength = maxOf(prefix.length, extraPrefix)
                        return ExpandedResult(expand(replaced, context), effectivePrefixLength, matchedString)
                    }
                } catch (e: java.lang.Exception) {
                    // ignore
                }
            } else {
                val prefix = entry.prefix
                val expectedSuffix = item.cleanKey
                val matchesWord = word == null
                    || expectedSuffix.equals(prefix + word, ignoreCase = true)
                    || (expectedSuffix.endsWith(word, ignoreCase = true) && textBeforeCursor.endsWith(expectedSuffix, ignoreCase = true))
                if (matchesWord && textBeforeCursor.endsWith(expectedSuffix, ignoreCase = true)) {
                    if (word == null && prefix.isEmpty() && textBeforeCursor.length > expectedSuffix.length) {
                        val prevChar = textBeforeCursor[textBeforeCursor.length - expectedSuffix.length - 1]
                        if (Character.isLetterOrDigit(prevChar)) {
                            continue
                        }
                    }
                    val effectivePrefixLength = if (word != null && expectedSuffix.endsWith(word, ignoreCase = true)) {
                        expectedSuffix.length - word.length
                    } else {
                        prefix.length
                    }
                    return ExpandedResult(expand(entry.template, context), effectivePrefixLength, expectedSuffix)
                }
            }
        }
        return null
    }

    fun getExpandedWord(word: String?, context: Context): String? {
        if (word == null || !isEnabled(context)) return null
        val shortcuts = getShortcuts(context)
        val entry = shortcuts[word] ?: shortcuts[word.lowercase(Locale.getDefault())]
        if (entry != null && entry.prefix.isEmpty()) {
            return expand(entry.template, context)
        }
        return null
    }
}
