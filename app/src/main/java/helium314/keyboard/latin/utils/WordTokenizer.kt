/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin.utils



/**
 * Utility for normalizing words before dictionary lookups and session boost recording.
 *
 * Handles edge cases:
 * - Trailing punctuation: "hello," → "hello"
 * - Leading punctuation: "(hello" → "hello"
 * - Abbreviation dots: "Dr." → "Dr"
 * - Preserves contractions: "don't" → "don't" (apostrophe is a word connector)
 * - Preserves hyphenated words: "well-known" → "well-known"
 * - Preserves internal dots for URLs/emails (if they contain @ or multiple dots)
 */
object WordTokenizer {

    // Characters that should be stripped from word boundaries for suggestion lookup
    private val LEADING_STRIP_CHARS = charArrayOf(
        '(', '[', '{', '"', '\'', '`',
        '\u00AB',  // «
        '\u00BF',  // ¿
        '\u00A1',  // ¡
        '\u201C',  // "
        '\u2018',  // '
        '\u2039'   // ‹
    )
    private val TRAILING_STRIP_CHARS = charArrayOf(
        '.', ',', '!', '?', ':', ';', ')', ']', '}', '"',
        '\u00BB',  // »
        '\u201D',  // "
        '\u2019',  // '
        '\u203A',  // ›
        '\u2026'   // …
    )

    /**
     * Normalize a word for dictionary/session lookup by stripping boundary punctuation.
     * Returns the cleaned word, or the original if no stripping is needed.
     *
     * Does NOT strip if the word looks like a URL or email (contains @ or multiple dots).
     */
    fun normalizeForLookup(word: String): String {
        if (word.length <= 1) return word

        // Don't touch URLs or email addresses
        if (word.contains('@') || word.count { it == '.' } > 1) return word

        var start = 0
        var end = word.length

        // Strip leading punctuation
        while (start < end && word[start] in LEADING_STRIP_CHARS) {
            start++
        }

        // Strip trailing punctuation
        while (end > start && word[end - 1] in TRAILING_STRIP_CHARS) {
            end--
        }

        if (start == 0 && end == word.length) return word
        if (start >= end) return word // all punctuation, return as-is

        return word.substring(start, end)
    }

    /**
     * Check if a word is a contraction (contains an apostrophe between letters).
     * e.g., "don't", "I'm", "they're"
     */
    fun isContraction(word: String): Boolean {
        if (word.length < 3) return false
        val apostropheIdx = word.indexOf('\'')
        if (apostropheIdx <= 0 || apostropheIdx >= word.length - 1) return false
        return Character.isLetter(word[apostropheIdx - 1]) &&
               Character.isLetter(word[apostropheIdx + 1])
    }

    /**
     * Split a contraction into its component parts.
     * "don't" → ["don", "t"]
     * "they're" → ["they", "re"]
     * Returns null if not a contraction.
     */
    fun splitContraction(word: String): Pair<String, String>? {
        if (!isContraction(word)) return null
        val idx = word.indexOf('\'')
        return word.substring(0, idx) to word.substring(idx + 1)
    }

    /**
     * Normalize a word for recording in session boost / user history.
     * Strips boundary punctuation and lowercases.
     */
    fun normalizeForHistory(word: String): String {
        return normalizeForLookup(word).lowercase()
    }
}
