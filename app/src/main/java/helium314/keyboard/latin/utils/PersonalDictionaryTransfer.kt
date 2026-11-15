// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.ContentValues
import android.content.Context
import android.provider.UserDictionary
import java.io.BufferedReader
import java.io.Writer
import java.util.Locale

/**
 * Reads and writes the personal dictionary, i.e. the words in Android's user dictionary, as a text file
 * with one word per line (word, shortcut and locale separated by tabs, like the Gboard dictionary export).
 * Used by the export and import of the personal dictionary screen and by the backup.
 */
object PersonalDictionaryTransfer {
    /** Entry name of the personal dictionary in a backup */
    const val BACKUP_ENTRY_NAME = "personal_dictionary.txt"

    private const val HEADER_PREFIX = "# Gboard Dictionary format:"

    /** [locale] is the locale as stored in the user dictionary (e.g. "en_US"), null for all languages */
    data class Entry(val word: String, val shortcut: String?, val locale: String?)

    /** Writes the header and the [entries], returns the number of entries */
    fun write(entries: Sequence<Entry>, writer: Writer): Int {
        writer.write("$HEADER_PREFIX word\tshortcut\tlocale\n")
        var count = 0
        for (entry in entries) {
            writer.write("${entry.word}\t${entry.shortcut.orEmpty()}\t${entry.locale.orEmpty()}\n")
            count++
        }
        return count
    }

    fun parse(reader: BufferedReader): List<Entry> {
        val result = mutableListOf<Entry>()
        var wordIndex = 0
        var shortcutIndex = 1
        var localeIndex = 2
        while (true) {
            val line = reader.readLine() ?: break
            if (line.startsWith("#")) {
                if (line.startsWith(HEADER_PREFIX)) {
                    val columnsStr = line.substring(HEADER_PREFIX.length)
                    val columns = (if (columnsStr.contains("\t")) columnsStr.split("\t") else columnsStr.split(Regex("\\s+")))
                        .map { it.trim() }
                    val word = columns.indexOf("word")
                    if (word != -1) {
                        wordIndex = word
                        shortcutIndex = columns.indexOf("shortcut")
                        localeIndex = columns.indexOf("language_tag").let { if (it == -1) columns.indexOf("locale") else it }
                    }
                }
                continue
            }
            val parts = line.split("\t")
            val word = parts.getOrNull(wordIndex).orEmpty()
            if (word.isBlank()) continue
            val shortcut = if (shortcutIndex != -1) parts.getOrNull(shortcutIndex)?.ifBlank { null } else null
            val locale = if (localeIndex != -1) normalizeLocale(parts.getOrNull(localeIndex)) else null
            result.add(Entry(word, shortcut, locale))
        }
        return result
    }

    /**
     * Locale as stored in the user dictionary: "en_US" is kept, a language tag like "en-US" is converted,
     * and blank or "all" means all languages.
     */
    fun normalizeLocale(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value == "all") return null
        if (value.contains('_')) return value // already in the form of Locale.toString
        return try { Locale.forLanguageTag(value).toString().ifEmpty { null } } catch (_: Exception) { null }
    }

    /** All words of the user dictionary; the cursor can be null if there is no user dictionary */
    fun readAll(context: Context): List<Entry> {
        val cursor = context.contentResolver.query(
            UserDictionary.Words.CONTENT_URI,
            arrayOf(UserDictionary.Words.WORD, UserDictionary.Words.SHORTCUT, UserDictionary.Words.LOCALE),
            null, null, UserDictionary.Words.DEFAULT_SORT_ORDER
        ) ?: return emptyList()
        return cursor.use {
            val wordIndex = it.getColumnIndexOrThrow(UserDictionary.Words.WORD)
            val shortcutIndex = it.getColumnIndexOrThrow(UserDictionary.Words.SHORTCUT)
            val localeIndex = it.getColumnIndexOrThrow(UserDictionary.Words.LOCALE)
            buildList {
                while (it.moveToNext()) {
                    val word = it.getString(wordIndex) ?: continue
                    add(Entry(word, it.getString(shortcutIndex)?.ifEmpty { null }, it.getString(localeIndex)?.ifEmpty { null }))
                }
            }
        }
    }

    /** Adds [entries] to the user dictionary, without the ones that are in there already if [skipExisting]; returns the number added */
    fun insert(context: Context, entries: List<Entry>, skipExisting: Boolean): Int {
        val toAdd = if (skipExisting) {
            val existing = readAll(context).toSet()
            entries.filter { it !in existing }.distinct()
        } else entries
        if (toAdd.isEmpty()) return 0
        val values = toAdd.map {
            ContentValues(5).apply {
                put(UserDictionary.Words.WORD, it.word)
                put(UserDictionary.Words.FREQUENCY, 250)
                put(UserDictionary.Words.LOCALE, it.locale)
                put(UserDictionary.Words.APP_ID, 0)
                put(UserDictionary.Words.SHORTCUT, it.shortcut)
            }
        }
        return try {
            context.contentResolver.bulkInsert(UserDictionary.Words.CONTENT_URI, values.toTypedArray())
        } catch (e: Exception) {
            Log.e("PersonalDictionary", "Bulk insert failed, falling back to one-by-one insert", e)
            var added = 0
            for (v in values) {
                try {
                    context.contentResolver.insert(UserDictionary.Words.CONTENT_URI, v)
                    added++
                } catch (ex: Exception) {
                    Log.w("PersonalDictionary", "Failed to add a word", ex)
                }
            }
            added
        }
    }
}
