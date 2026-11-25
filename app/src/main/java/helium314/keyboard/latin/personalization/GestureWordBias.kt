/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.personalization

import android.content.Context
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.WordTokenizer
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.min

/**
 * Learns from the corrections a user makes after a swipe: when the first suggestion is replaced by another one,
 * the picked word gets a small relative score bonus and the replaced word a small malus in later swipes.
 *
 * Swipe scores are in the tens of millions, so the additive boost of [SessionWordBoost] cannot change a
 * ranking. Here the adjustment is a fraction of the score, capped at [MAX_ADJUSTMENT] in both directions:
 * it only decides close calls and never beats a clearly better geometric match. It fades over time.
 *
 * Thread safety: corrections come from the UI thread, lookups from the suggestion thread. The map is concurrent,
 * entries are only changed inside [ConcurrentHashMap.compute], and saving is serialized by [saveLock].
 */
class GestureWordBias internal constructor(private val file: File) {
    private val entries = ConcurrentHashMap<String, Entry>()
    private val saveLock = Any()
    @Volatile private var dirty = false

    data class Entry(var picks: Int, var rejects: Int, var lastMs: Long)

    init {
        load()
    }

    val isEmpty get() = entries.isEmpty()
    val size get() = entries.size

    /** The user replaced the swiped [replaced] by the suggestion [picked]. */
    fun recordCorrection(picked: String, replaced: String) {
        val pickedKey = key(picked) ?: return
        val replacedKey = key(replaced) ?: return
        if (pickedKey == replacedKey) return
        val now = System.currentTimeMillis()
        entries.compute(pickedKey) { _, e -> (e ?: Entry(0, 0, now)).also { it.picks = min(it.picks + 1, MAX_COUNT); it.lastMs = now } }
        entries.compute(replacedKey) { _, e -> (e ?: Entry(0, 0, now)).also { it.rejects = min(it.rejects + 1, MAX_COUNT); it.lastMs = now } }
        evictIfNeeded()
        dirty = true
    }

    /** The user kept the swiped [word]: undoes part of earlier rejections of it. */
    fun recordAccepted(word: String) {
        val k = key(word) ?: return
        entries.computeIfPresent(k) { _, e ->
            if (e.rejects > 0) {
                e.rejects--
                dirty = true
            }
            e
        }
    }

    /** Relative score change for [word], between -[MAX_ADJUSTMENT] and +[MAX_ADJUSTMENT], 0 for unknown words. */
    fun getAdjustment(word: String): Float {
        if (entries.isEmpty()) return 0f
        val e = entries[key(word) ?: return 0f] ?: return 0f
        val days = (System.currentTimeMillis() - e.lastMs) / MILLIS_PER_DAY.toFloat()
        return adjustment(e.picks, e.rejects, days)
    }

    fun removeWord(word: String) {
        val k = key(word) ?: return
        if (entries.remove(k) != null) dirty = true
    }

    fun clear() {
        entries.clear()
        dirty = false
        synchronized(saveLock) { file.delete() }
    }

    fun flushIfDirty() {
        if (!dirty) return
        dirty = false
        if (!save()) dirty = true // try again at the next flush
    }

    /** Normalized lookup key, null for words that cannot be learned (too short, or break the file format). */
    private fun key(word: String): String? {
        if (word.any { it == '\t' || it == '\n' || it == '\r' }) return null
        val k = WordTokenizer.normalizeForLookup(word).lowercase(Locale.ROOT)
        return if (k.length < 2 || k.length > MAX_WORD_LENGTH) null else k
    }

    private fun evictIfNeeded() {
        while (entries.size > MAX_ENTRIES) {
            val oldest = entries.entries.minByOrNull { it.value.lastMs } ?: return
            entries.remove(oldest.key)
        }
    }

    private fun load() {
        if (!file.isFile) return
        try {
            file.forEachLine { line ->
                val parts = line.split('\t')
                if (parts.size != 4 || parts[0].isEmpty()) return@forEachLine
                val picks = parts[1].toIntOrNull()?.coerceIn(0, MAX_COUNT) ?: return@forEachLine
                val rejects = parts[2].toIntOrNull()?.coerceIn(0, MAX_COUNT) ?: return@forEachLine
                val last = parts[3].toLongOrNull() ?: return@forEachLine
                entries[parts[0]] = Entry(picks, rejects, last)
            }
            val cutoff = System.currentTimeMillis() - MAX_AGE_DAYS * MILLIS_PER_DAY
            if (entries.values.removeAll { it.lastMs < cutoff }) dirty = true
            while (entries.size > MAX_ENTRIES) evictIfNeeded()
        } catch (e: Exception) { // unreadable or malformed file: start over, this data is only a hint
            Log.w(TAG, "Failed to load gesture bias, ignoring it", e)
            entries.clear()
        }
    }

    /** Writes to a temporary file and renames it, so a crash cannot leave a half written file. */
    private fun save(): Boolean = synchronized(saveLock) {
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.bufferedWriter().use { writer ->
                for ((word, e) in entries) {
                    writer.write("$word\t${e.picks}\t${e.rejects}\t${e.lastMs}")
                    writer.newLine()
                }
            }
            if (!temp.renameTo(file)) {
                file.delete()
                if (!temp.renameTo(file)) throw IOException("cannot replace ${file.name}")
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save gesture bias", e)
            false
        }
    }

    companion object {
        private const val TAG = "GestureWordBias"
        private const val FILENAME = "gesture_word_bias.dat"
        private const val MAX_ENTRIES = 500
        private const val MAX_COUNT = 1000
        private const val MAX_WORD_LENGTH = 64
        private const val MILLIS_PER_DAY = 86_400_000L
        private const val MAX_AGE_DAYS = 60L
        private const val DECAY_LAMBDA = 0.0495 // half-life of 14 days
        const val PICK_STEP = 0.005f
        const val REJECT_STEP = 0.01f
        const val MAX_ADJUSTMENT = 0.03f

        /** Pure scoring rule, public for testing. */
        fun adjustment(picks: Int, rejects: Int, daysSinceLastUse: Float): Float {
            val bonus = min(picks.coerceAtLeast(0) * PICK_STEP, MAX_ADJUSTMENT)
            val malus = min(rejects.coerceAtLeast(0) * REJECT_STEP, MAX_ADJUSTMENT)
            val decay = exp(-DECAY_LAMBDA * daysSinceLastUse.coerceAtLeast(0f)).toFloat()
            return ((bonus - malus) * decay).coerceIn(-MAX_ADJUSTMENT, MAX_ADJUSTMENT)
        }

        /** Applies a relative [adjustment] to a score without overflowing, positive scores only (negative ones are junk). */
        fun adjustScore(score: Int, adjustment: Float): Int {
            if (score <= 0 || adjustment == 0f) return score
            val delta = (score.toLong() * adjustment).toLong()
            return (score + delta).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        }

        @Volatile
        private var instance: GestureWordBias? = null

        fun getInstance(context: Context): GestureWordBias = instance ?: synchronized(this) {
            instance ?: GestureWordBias(File(context.filesDir, FILENAME)).also { instance = it }
        }
    }
}
