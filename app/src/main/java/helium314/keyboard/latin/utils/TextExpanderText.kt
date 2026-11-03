// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import helium314.keyboard.latin.utils.TextExpanderUtils.ShortcutEntry

/**
 * All text expander shortcuts as one plain text, for editing them together, copying and sharing.
 *
 * Each shortcut starts with a header line `=== shortcut ===`, followed by its text:
 * ```
 * === ;cic ===
 * long prompt …
 *
 * === regex: \d+eur ===
 * …
 * ```
 * Characters before the first letter or digit of a shortcut are its prefix (`;` above).
 * Empty lines around a text are only for readability and are removed.
 */
object TextExpanderText {
    private const val REGEX_LABEL = "regex:"
    private val HEADER = Regex("""^===\s*(.*?)\s*===\s*$""")

    enum class ErrorKind { TEXT_BEFORE_FIRST, EMPTY_SHORTCUT, SPACE_IN_SHORTCUT, EMPTY_TEXT, DUPLICATE, INVALID_REGEX }

    /** [line] is 1-based */
    data class Error(val line: Int, val kind: ErrorKind, val name: String = "")

    class Result(val entries: LinkedHashMap<String, ShortcutEntry>, val errors: List<Error>)

    data class Diff(val added: List<String>, val changed: List<String>, val removed: List<String>) {
        val isEmpty get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty()
    }

    /** Header name of a stored key, e.g. ";cic" or "regex: \d+eur" */
    fun displayName(key: String): String =
        if (key.startsWith(TextExpanderUtils.REGEX_PREFIX)) "$REGEX_LABEL ${key.removePrefix(TextExpanderUtils.REGEX_PREFIX)}"
        else key

    fun format(shortcuts: Map<String, ShortcutEntry>): String =
        shortcuts.entries.joinToString("") { (key, entry) -> "=== ${displayName(key)} ===\n${entry.template.trimEnd('\n')}\n\n" }
            .trimEnd('\n').let { if (it.isEmpty()) it else it + "\n" }

    /** Header names with the offset of their line in [text], for jumping to a shortcut */
    fun headers(text: String): List<Pair<String, Int>> {
        val result = ArrayList<Pair<String, Int>>()
        var offset = 0
        for (line in text.split('\n')) {
            HEADER.matchEntire(line)?.let { result.add(it.groupValues[1] to offset) }
            offset += line.length + 1
        }
        return result
    }

    /**
     * Shortcuts in [text]. [existing] shortcuts keep their prefix split and their exact text
     * (e.g. a trailing line break) if the text is otherwise unchanged.
     */
    fun parse(text: String, existing: Map<String, ShortcutEntry>): Result {
        val entries = LinkedHashMap<String, ShortcutEntry>()
        val errors = ArrayList<Error>()
        var headerLine = 0
        var header: String? = null
        val body = ArrayList<String>()

        fun finishBlock() {
            val name = header ?: return
            val template = body.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }.joinToString("\n")
            if (name.isEmpty()) { errors.add(Error(headerLine, ErrorKind.EMPTY_SHORTCUT)); return }
            val isRegex = name.startsWith(REGEX_LABEL, ignoreCase = true)
            val key: String
            val prefix: String
            if (isRegex) {
                val pattern = name.substring(REGEX_LABEL.length).trim()
                if (pattern.isEmpty()) { errors.add(Error(headerLine, ErrorKind.EMPTY_SHORTCUT, name)); return }
                key = TextExpanderUtils.REGEX_PREFIX + pattern
                prefix = existing[key]?.prefix ?: ""
                if (runCatching { Regex(pattern.removePrefix(prefix)) }.isFailure) {
                    errors.add(Error(headerLine, ErrorKind.INVALID_REGEX, pattern)); return
                }
            } else {
                if (name.any { it.isWhitespace() }) { errors.add(Error(headerLine, ErrorKind.SPACE_IN_SHORTCUT, name)); return }
                key = name
                val leading = name.takeWhile { !it.isLetterOrDigit() }
                prefix = existing[key]?.prefix ?: if (leading.length < name.length) leading else ""
            }
            if (template.isEmpty()) { errors.add(Error(headerLine, ErrorKind.EMPTY_TEXT, name)); return }
            if (entries.containsKey(key)) { errors.add(Error(headerLine, ErrorKind.DUPLICATE, name)); return }
            val old = existing[key]
            entries[key] = if (old != null && old.prefix == prefix && old.template.trimEnd('\n') == template) old
                else ShortcutEntry(template, prefix)
        }

        text.replace("\r\n", "\n").split('\n').forEachIndexed { index, line ->
            val match = HEADER.matchEntire(line)
            if (match != null) {
                finishBlock()
                header = match.groupValues[1]
                headerLine = index + 1
                body.clear()
            } else if (header == null) {
                if (line.isNotBlank() && errors.none { it.kind == ErrorKind.TEXT_BEFORE_FIRST })
                    errors.add(Error(index + 1, ErrorKind.TEXT_BEFORE_FIRST))
            } else {
                body.add(line)
            }
        }
        finishBlock()
        return Result(entries, errors)
    }

    fun diff(old: Map<String, ShortcutEntry>, new: Map<String, ShortcutEntry>) = Diff(
        added = new.keys.filter { it !in old }.map(::displayName),
        changed = new.filter { (key, entry) -> old[key].let { it != null && it != entry } }.keys.map(::displayName),
        removed = old.keys.filter { it !in new }.map(::displayName)
    )
}
