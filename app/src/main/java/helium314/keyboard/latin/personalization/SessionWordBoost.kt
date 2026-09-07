/*
 * Copyright (C) 2026
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin.personalization

import android.content.Context
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.WordTokenizer
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.min

/**
 * Lightweight persistent session word tracker that boosts recently typed words.
 *
 * Words committed by the user are recorded with a use count and timestamp.
 * The boost for a word decays exponentially over time, so words typed recently
 * have a stronger boost than words typed days ago.
 *
 * Data is persisted to a simple text file and survives app restarts.
 * Size-limited via LRU eviction at [maxEntries].
 */
class SessionWordBoost private constructor(
    private val boostFile: File,
    private val maxEntries: Int = MAX_ENTRIES_DEFAULT
) {
    private val entries = ConcurrentHashMap<String, WordEntry>()
    @Volatile private var dirty = false

    data class WordEntry(
        val word: String,
        var count: Int,
        var firstSeenMs: Long,
        var lastSeenMs: Long
    )

    init {
        loadFromDisk()
    }

    /**
     * Record that a word was committed. Increments count and updates timestamp.
     */
    fun recordWord(word: String, wasAutoCapitalized: Boolean = false) {
        val rawNormalized = WordTokenizer.normalizeForLookup(word)
        if (rawNormalized.length <= 1) return // skip single chars
        val normalized = if (wasAutoCapitalized) rawNormalized.lowercase() else rawNormalized
        val now = System.currentTimeMillis()
        val existing = entries[normalized]
            ?: entries[normalized.lowercase()]
            ?: entries.entries.firstOrNull { it.key.equals(normalized, ignoreCase = true) }?.value

        if (existing != null) {
            existing.count++
            existing.lastSeenMs = now
            if (wasAutoCapitalized && existing.word != normalized) {
                entries.remove(existing.word)
                entries[normalized] = existing
            }
        } else {
            entries[normalized] = WordEntry(normalized, 1, now, now)
            evictIfNeeded()
        }
        dirty = true
    }

    /**
     * Remove a word from session boost (memory and disk).
     */
    fun removeWord(word: String) {
        val normalized = WordTokenizer.normalizeForLookup(word)
        val lower = normalized.lowercase()
        val toRemove = entries.keys.filter { it.equals(normalized, ignoreCase = true) || it.equals(lower, ignoreCase = true) }
        for (key in toRemove) {
            entries.remove(key)
        }
        if (toRemove.isNotEmpty()) {
            dirty = true
            flushIfDirty()
        }
    }

    /**
     * Get the committed use count for a word.
     */
    fun getCount(word: String): Int {
        val normalized = WordTokenizer.normalizeForLookup(word)
        return entries[normalized]?.count
            ?: entries[normalized.lowercase()]?.count
            ?: entries.entries.firstOrNull { it.key.equals(normalized, ignoreCase = true) }?.value?.count
            ?: 0
    }

    /**
     * Get the boost score for a candidate word.
     * Returns 0 if the word has never been recorded.
     *
     * Boost formula: count * exp(-DECAY_LAMBDA * daysSinceLastUse)
     * Capped at [MAX_BOOST].
     */
    fun getBoost(word: String): Float {
        val normalized = WordTokenizer.normalizeForLookup(word)
        val entry = entries[normalized]
            ?: entries[normalized.lowercase()]
            ?: entries.entries.firstOrNull { it.key.equals(normalized, ignoreCase = true) }?.value
            ?: return 0f
        val daysSinceLastUse = (System.currentTimeMillis() - entry.lastSeenMs) /
            MILLIS_PER_DAY.toFloat()
        val decay = exp(-DECAY_LAMBDA * daysSinceLastUse).toFloat()
        val rawBoost = entry.count * BASE_BOOST_PER_COUNT * decay
        return min(rawBoost, MAX_BOOST)
    }

    /**
     * Apply session boost to a suggestion score.
     * The boost is additive: finalScore = originalScore + boost * SCORE_MULTIPLIER
     */
    fun applyBoostToScore(word: String, originalScore: Int): Int {
        val boost = getBoost(word)
        if (boost <= 0f) return originalScore
        return originalScore + (boost * SCORE_MULTIPLIER).toInt()
    }

    /**
     * Flush to disk if dirty. Call this on input finish or periodically.
     */
    fun flushIfDirty() {
        if (!dirty) return
        saveToDisk()
        dirty = false
    }

    /**
     * Clear all entries (in-memory and on disk).
     */
    fun clear() {
        entries.clear()
        dirty = false
        try {
            boostFile.delete()
        } catch (e: IOException) {
            Log.w(TAG, "Failed to delete boost file", e)
        }
    }

    val size: Int get() = entries.size

    // --- persistence ---

    private fun loadFromDisk() {
        if (!boostFile.exists()) return
        try {
            BufferedReader(FileReader(boostFile)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val parts = line.split('\t')
                    if (parts.size == 4) {
                        try {
                            val word = parts[0]
                            val count = parts[1].toInt()
                            val firstSeen = parts[2].toLong()
                            val lastSeen = parts[3].toLong()
                            entries[word] = WordEntry(word, count, firstSeen, lastSeen)
                        } catch (e: NumberFormatException) {
                            // skip malformed line
                        }
                    }
                    line = reader.readLine()
                }
            }
            // Evict stale entries (older than MAX_AGE_DAYS)
            evictStale()
        } catch (e: IOException) {
            Log.w(TAG, "Failed to load session boost file", e)
        }
    }

    private fun saveToDisk() {
        try {
            boostFile.parentFile?.mkdirs()
            BufferedWriter(FileWriter(boostFile)).use { writer ->
                for (entry in entries.values) {
                    writer.write("${entry.word}\t${entry.count}\t${entry.firstSeenMs}\t${entry.lastSeenMs}")
                    writer.newLine()
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to save session boost file", e)
        }
    }

    private fun evictIfNeeded() {
        if (entries.size <= maxEntries) return
        // Remove the entry with the oldest lastSeenMs
        val oldest = entries.values.minByOrNull { it.lastSeenMs } ?: return
        entries.remove(oldest.word)
    }

    private fun evictStale() {
        val cutoff = System.currentTimeMillis() - (MAX_AGE_DAYS * MILLIS_PER_DAY)
        val stale = entries.values.filter { it.lastSeenMs < cutoff }
        for (entry in stale) {
            entries.remove(entry.word)
        }
        if (stale.isNotEmpty()) dirty = true
    }

    companion object {
        private const val TAG = "SessionWordBoost"
        private const val BOOST_FILENAME = "session_word_boost.dat"
        private const val MAX_ENTRIES_DEFAULT = 200
        private const val MILLIS_PER_DAY = 86_400_000L
        private const val MAX_AGE_DAYS = 30L

        // Decay constant: half-life ≈ 3 days (ln(2)/3 ≈ 0.231)
        private const val DECAY_LAMBDA = 0.231

        // Each use contributes this much base boost (before decay)
        private const val BASE_BOOST_PER_COUNT = 5f

        // Maximum boost value (prevents runaway scores for very frequent words)
        private const val MAX_BOOST = 50f

        // Multiplier to convert boost into score-space (native scores are ~1_000_000)
        private const val SCORE_MULTIPLIER = 1000f

        @Volatile
        private var instance: SessionWordBoost? = null

        fun getInstance(context: Context): SessionWordBoost {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val file = File(context.filesDir, BOOST_FILENAME)
                    SessionWordBoost(file).also { instance = it }
                }
            }
        }
    }
}
