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
    private val cache = LruCache<String, String>(MAX_SIZE)

    @Synchronized
    fun record(typedWord: CharSequence?, revertedCandidate: CharSequence?) {
        if (typedWord.isNullOrEmpty() || revertedCandidate.isNullOrEmpty()) return
        val key = typedWord.toString().trim().lowercase(Locale.ROOT)
        val value = revertedCandidate.toString().trim().lowercase(Locale.ROOT)
        if (key.isNotEmpty() && value.isNotEmpty() && key != value) {
            cache.put(key, value)
        }
    }

    @Synchronized
    fun isRecentlyReverted(typedWord: CharSequence?, candidateWord: CharSequence?): Boolean {
        if (typedWord.isNullOrEmpty() || candidateWord.isNullOrEmpty()) return false
        val key = typedWord.toString().trim().lowercase(Locale.ROOT)
        val candidate = candidateWord.toString().trim().lowercase(Locale.ROOT)
        val reverted = cache.get(key) ?: return false
        return reverted == candidate
    }

    @Synchronized
    fun remove(typedWord: CharSequence?) {
        if (typedWord.isNullOrEmpty()) return
        val key = typedWord.toString().trim().lowercase(Locale.ROOT)
        cache.remove(key)
    }

    @Synchronized
    fun clear() {
        cache.evictAll()
    }
}
