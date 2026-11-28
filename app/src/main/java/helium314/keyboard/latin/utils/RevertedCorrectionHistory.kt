// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.util.LruCache
import java.util.Locale

/**
 * In-memory short-term cache of recently reverted auto-corrections.
 * When a user reverts an auto-correction via Backspace, this cache remembers
 * the pair (typedWord -> revertedCandidate) to suppress repeating the same
 * auto-correction immediately upon subsequent spaces or separators.
 */
object RevertedCorrectionHistory {
    private const val MAX_SIZE = 30
    private val cache = LruCache<String, Set<String>>(MAX_SIZE)

    private fun normalize(word: CharSequence?) = word?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()

    @Synchronized
    fun record(typedWord: CharSequence?, revertedCandidate: CharSequence?) {
        val key = normalize(typedWord)
        val candidate = normalize(revertedCandidate)
        if (key.isEmpty() || candidate.isEmpty() || key == candidate) return
        cache.put(key, cache.get(key).orEmpty() + candidate)
    }

    @Synchronized
    fun isRecentlyReverted(typedWord: CharSequence?, candidateWord: CharSequence?): Boolean {
        val key = normalize(typedWord)
        if (key.isEmpty()) return false
        return cache.get(key)?.contains(normalize(candidateWord)) == true
    }

    /** Allows [candidateWord] again for [typedWord], other rejected corrections stay */
    @Synchronized
    fun remove(typedWord: CharSequence?, candidateWord: CharSequence?) {
        val key = normalize(typedWord)
        if (key.isEmpty()) return
        val rejected = cache.get(key) ?: return
        val remaining = rejected - normalize(candidateWord)
        if (remaining.isEmpty()) cache.remove(key) else cache.put(key, remaining)
    }

    @Synchronized
    fun clear() {
        cache.evictAll()
    }
}
