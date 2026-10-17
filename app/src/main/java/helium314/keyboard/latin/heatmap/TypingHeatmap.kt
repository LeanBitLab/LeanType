// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.heatmap

import android.content.Context
import android.os.Build
import helium314.keyboard.compat.isUserLocked
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToLong

/**
 * Learns where the user actually touches keys, and adapts key detection to it.
 *
 * The intended key is taken from the text that was finally used: the committed word (after
 * autocorrection or a picked suggestion), the typed word if the autocorrection was reverted,
 * or the retyped letter if the user deleted a letter and typed a neighboring one instead.
 * Touches are stored per key, relative to the key center and in units of key width / height.
 *
 * From the touches, each key gets an average offset, which is used to decide which key a touch
 * was meant for ([adaptedKeyCode]) and to move the touch coordinates passed to the dictionary
 * ([adjustForDictionary]). Learning always uses the raw touch positions, so adaptation does not
 * feed back into what is learned. All coordinates are keyboard coordinates (view coordinates
 * corrected by the key detector, like the coordinates in the word composer).
 *
 * Privacy and security:
 *  - data stays on the device: no network access, stored in credential encrypted storage in the
 *    no-backup folder (excluded from Android cloud backup and device-to-device transfer), only
 *    available after the user unlocked the device
 *  - samples are stored grouped by key without any order across keys, so the typed text cannot
 *    be reconstructed from the stored data
 *  - nothing is recorded in password fields and incognito mode, and adaptation is not used in
 *    password fields
 *  - stored data and restored backups are validated, broken data is ignored
 */
object TypingHeatmap {
    private const val TAG = "TypingHeatmap"
    const val FILE_NAME = "typing_heatmap.json"
    private const val MAX_SAMPLES_PER_KEY = 400
    /** Touches further away from the intended key center than this (in key sizes) are ignored */
    private const val MAX_OFFSET = 1.6f
    private const val SAVE_EVERY_SAMPLES = 40
    /** Limits for reading stored or restored data */
    /** must be larger than the largest file we write: 16 layouts * ~40 keys * 400 samples * ~25 bytes */
    private const val MAX_FILE_SIZE = 10L * 1024 * 1024
    private const val MAX_LAYOUTS = 16
    private const val MAX_KEY_SHAPES = 300
    private const val MAX_SAMPLE_KEYS = 150
    private const val MAX_LABEL_LENGTH = 8
    /** a key is adapted only with at least this many samples ... */
    const val MIN_SAMPLES_FOR_ADAPTATION = 30
    /** ... and fully from this number of samples on */
    const val FULL_SAMPLES_FOR_ADAPTATION = 100
    /** maximum shift of a key center for strength 1..5, in key sizes */
    private val MAX_SHIFT_FOR_STRENGTH = floatArrayOf(0.2f, 0.27f, 0.33f, 0.42f, 0.5f)
    const val DEFAULT_STRENGTH = 3

    /** Position of a key, normalized to the keyboard size (0..1) */
    data class KeyShape(val code: Int, val label: String, val x: Float, val y: Float, val width: Float, val height: Float) {
        val centerX get() = x + width / 2
        val centerY get() = y + height / 2
    }

    /**
     * Touch relative to the intended key center, in units of key width / height.
     * [adapted]: whether adaptation was active when the touch was made.
     */
    data class Sample(val dx: Float, val dy: Float, val touchedCode: Int, val adapted: Boolean = false)

    class LayoutData(val id: String) {
        var shapes: List<KeyShape> = emptyList()
        /** keyboard height / width, for drawing */
        var aspectRatio: Float = 0.35f
        val samples = HashMap<Int, ArrayDeque<Sample>>()
        val sampleCount get() = samples.values.sumOf { it.size }
        /** keyboard the shapes were taken from (not persisted) */
        internal var shapesSource: Any? = null
    }

    /** Geometry of the currently shown keyboard, in keyboard pixels */
    interface KeyGeometry {
        val layoutId: String
        /** key that produces this (lowercase) code point, as x, y, width, height in keyboard pixels */
        fun keyRect(codePoint: Int): IntArray?
        fun snapshot(): List<KeyShape>
        val aspectRatio: Float
        /** identity of the keyboard, to avoid taking a new snapshot for every word */
        val source: Any
    }

    /** A key considered for adapted detection: code and x, y, width, height in any unit */
    class Candidate(val code: Int, val x: Float, val y: Float, val width: Float, val height: Float)

    private class PendingWord(
        val geometry: KeyGeometry,
        val typedWord: String,
        val finalWord: String,
        val xs: IntArray,
        val ys: IntArray,
        val adapted: Boolean,
    )

    /** Result of reading stored data */
    class ParsedData(val layouts: Map<String, LayoutData>, val device: String?, val weeks: Map<Int, IntArray> = emptyMap())

    /** touches and misses of one week, for showing whether typing gets better */
    class WeekStats(val week: Int, val touches: Int, val misses: Int)

    private val layouts = LinkedHashMap<String, LayoutData>()
    /** week (days since 1970 / 7) -> [touches, misses]; only counts, nothing about the text */
    private val weeks = java.util.TreeMap<Int, IntArray>()
    private const val MAX_WEEKS = 26
    @Volatile private var loaded = false
    private var pending: PendingWord? = null
    /** committed word at the time of a backspace press, only kept for reverting the autocorrection */
    private var revertCandidate: PendingWord? = null
    private var unsavedSamples = 0
    private var appContext: Context? = null
    private val ioExecutor = Executors.newSingleThreadExecutor()
    /** device the data was learned on, null if unknown (no data yet) */
    private var learnedOnDevice: String? = null
    val currentDevice: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    /** cache: layout id + strength -> offsets per code as [dx, dy]; invalidated when data is saved or replaced */
    @Volatile private var offsetCache = HashMap<String, Map<Int, FloatArray>>()

    /** adjusted dictionary coordinates -> raw touch coordinates (keyboard coordinates), for the last touches */
    private val rawTouches = object : LinkedHashMap<Long, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Long>?) = size > 128
    }

    /** Make sure stored data can be loaded, e.g. for adapting key detection. Loads in background. */
    @Synchronized
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        if (!loaded) ioExecutor.execute { synchronized(this) { ensureLoaded() } }
    }

    // ---------------- recording ----------------

    /** A word was committed. It is only used once we know it was not reverted. */
    @Synchronized
    fun onWordCommitted(context: Context, geometry: KeyGeometry, typedWord: String, finalWord: String,
                        xs: IntArray, ys: IntArray, adapted: Boolean = false) {
        appContext = context.applicationContext
        finalizePending()
        revertCandidate = null
        if (typedWord.isEmpty() || finalWord.isEmpty()) return
        val rawXs = xs.copyOf()
        val rawYs = ys.copyOf()
        for (i in rawXs.indices) {
            if (i >= rawYs.size) break
            val raw = rawTouches[pack(rawXs[i], rawYs[i])] ?: continue
            rawXs[i] = unpackX(raw)
            rawYs[i] = unpackY(raw)
        }
        pending = PendingWord(geometry, typedWord, finalWord, rawXs, rawYs, adapted)
    }

    /**
     * Backspace was pressed. Unless it reverts the autocorrection ([onCommitReverted]), the user
     * edits the committed word, so we don't know which word was finally used: it is dropped.
     */
    @Synchronized
    fun onBackspace() {
        revertCandidate = pending
        pending = null
    }

    /** The user reverted the autocorrection: the typed word was what they wanted */
    @Synchronized
    fun onCommitReverted() {
        val p = revertCandidate ?: pending ?: return
        revertCandidate = null
        pending = PendingWord(p.geometry, p.typedWord, p.typedWord, p.xs, p.ys, p.adapted)
        finalizePending()
    }

    /** The user deletes into the committed word, so we don't know the final word */
    @Synchronized
    fun discardPending() {
        pending = null
    }

    /** Called when other input follows, i.e. the last committed word is final */
    @Synchronized
    fun finalizePending() {
        revertCandidate = null
        val p = pending ?: return
        pending = null
        val newSamples = computeSamples(p.typedWord, p.finalWord, p.xs, p.ys, p.adapted) { p.geometry.keyRect(it) }
        addSamples(p.geometry, newSamples)
    }

    /**
     * The user deleted [deletedCodePoint] (touched at [x], [y]) and typed [newCodePoint] instead.
     * If the keys are neighbors, the touch was a miss for the new letter.
     */
    @Synchronized
    fun onLetterRetyped(context: Context, geometry: KeyGeometry, deletedCodePoint: Int, x: Int, y: Int,
                        newCodePoint: Int, adapted: Boolean = false) {
        appContext = context.applicationContext
        val raw = rawTouches[pack(x, y)]
        val rawX = if (raw == null) x else unpackX(raw)
        val rawY = if (raw == null) y else unpackY(raw)
        val sample = selfCorrectionSample(deletedCodePoint, rawX, rawY, newCodePoint, adapted) { geometry.keyRect(it) } ?: return
        addSamples(geometry, listOf(sample))
    }

    private fun addSamples(geometry: KeyGeometry, newSamples: List<Pair<Int, Sample>>) {
        if (newSamples.isEmpty()) return
        ensureLoaded()
        if (!loaded) return // user storage not available (device not unlocked yet)
        if (learnedOnDevice == null) learnedOnDevice = currentDevice
        val layout = layouts.getOrPut(geometry.layoutId) { LayoutData(geometry.layoutId) }
        // same limit as when reading, so nothing is lost when reading again: drop the layout with least data
        while (layouts.size > MAX_LAYOUTS) {
            val smallest = layouts.values.filter { it !== layout }.minByOrNull { it.sampleCount } ?: break
            layouts.remove(smallest.id)
        }
        if (layout.shapesSource !== geometry.source) {
            layout.shapes = geometry.snapshot()
            layout.aspectRatio = geometry.aspectRatio
            layout.shapesSource = geometry.source
        }
        for ((code, sample) in newSamples) {
            val list = layout.samples.getOrPut(code) { ArrayDeque() }
            list.addLast(sample)
            while (list.size > MAX_SAMPLES_PER_KEY) list.removeFirst()
        }
        val week = currentWeek()
        val stats = weeks.getOrPut(week) { IntArray(2) }
        stats[0] += newSamples.size
        stats[1] += newSamples.count { (code, sample) -> sample.touchedCode != code }
        while (weeks.size > MAX_WEEKS) weeks.remove(weeks.firstKey())
        unsavedSamples += newSamples.size
        if (unsavedSamples >= SAVE_EVERY_SAMPLES) save()
    }

    /** Input finished (keyboard closed or other field): finalize and write to disk */
    @Synchronized
    fun onFinishInput() {
        finalizePending()
        if (unsavedSamples > 0) save()
    }

    // ---------------- adaptation ----------------

    /** Effective offsets per key code for a layout, empty if there is not enough data */
    fun getOffsets(context: Context?, layoutId: String, strength: Int): Map<Int, FloatArray> {
        val cacheKey = cacheKey(layoutId, strength)
        offsetCache[cacheKey]?.let { return it }
        if (context == null && !loaded) return emptyMap() // touch events: don't wait for loading (started by init)
        synchronized(this) {
            if (context != null && appContext == null) appContext = context.applicationContext
            ensureLoaded()
            if (!loaded) return emptyMap() // not available yet
            val maxShift = maxShift(strength)
            val result = HashMap<Int, FloatArray>()
            layouts[layoutId]?.samples?.forEach { (code, list) ->
                effectiveOffset(list, maxShift)?.let { result[code] = it }
            }
            val newCache = HashMap(offsetCache)
            newCache[cacheKey] = result
            offsetCache = newCache
            return result
        }
    }

    // avoids building a new string for every touch event (layout ids are cached per keyboard)
    private class CacheKey(val layoutId: String, val strength: Int, val key: String)
    @Volatile private var lastCacheKey = CacheKey("", 0, "")
    private fun cacheKey(layoutId: String, strength: Int): String {
        val last = lastCacheKey
        if (last.layoutId == layoutId && last.strength == strength) return last.key
        val key = CacheKey(layoutId, strength, "$layoutId|$strength")
        lastCacheKey = key
        return key.key
    }

    fun maxShift(strength: Int) = MAX_SHIFT_FOR_STRENGTH[(strength - 1).coerceIn(0, MAX_SHIFT_FOR_STRENGTH.size - 1)]

    /**
     * Average offset of the touches meant for a key, weighted by the amount of data and limited
     * to [maxShift]. Null if there is not enough data.
     */
    fun effectiveOffset(samples: Collection<Sample>, maxShift: Float): FloatArray? {
        val n = samples.size
        if (n < MIN_SAMPLES_FOR_ADAPTATION) return null
        val weight = ((n - MIN_SAMPLES_FOR_ADAPTATION + 1).toFloat() /
                (FULL_SAMPLES_FOR_ADAPTATION - MIN_SAMPLES_FOR_ADAPTATION + 1)).coerceIn(0f, 1f)
        // limit single outliers
        var dx = samples.sumOf { it.dx.coerceIn(-1f, 1f).toDouble() }.toFloat() / n * weight
        var dy = samples.sumOf { it.dy.coerceIn(-1f, 1f).toDouble() }.toFloat() / n * weight
        if (!dx.isFinite() || !dy.isFinite()) return null
        val length = hypot(dx, dy)
        if (length > maxShift) {
            dx *= maxShift / length
            dy *= maxShift / length
        }
        return floatArrayOf(dx, dy)
    }

    /**
     * Code of the key a touch at [x], [y] is meant for, if adaptation decides differently than
     * plain key centers would. Null means: keep the normal detection.
     * Only the candidates are considered, distances are relative to the key size.
     */
    fun adaptedKeyCode(x: Float, y: Float, candidates: List<Candidate>, offsets: Map<Int, FloatArray>): Int? {
        if (offsets.isEmpty() || candidates.size < 2) return null
        var bestPlain: Candidate? = null
        var bestPlainDistance = Float.MAX_VALUE
        var bestAdapted: Candidate? = null
        var bestAdaptedDistance = Float.MAX_VALUE
        for (c in candidates) {
            val cx = c.x + c.width / 2
            val cy = c.y + c.height / 2
            val plain = sq((x - cx) / c.width) + sq((y - cy) / c.height)
            val offset = offsets[c.code]
            val adapted = if (offset == null) plain
                else sq((x - cx - offset[0] * c.width) / c.width) + sq((y - cy - offset[1] * c.height) / c.height)
            if (plain < bestPlainDistance) { bestPlainDistance = plain; bestPlain = c }
            if (adapted < bestAdaptedDistance) { bestAdaptedDistance = adapted; bestAdapted = c }
        }
        if (bestAdapted == null || bestAdapted === bestPlain) return null
        // don't take touches that are far away from the adapted key
        if (bestAdaptedDistance > 1f) return null
        return bestAdapted.code
    }

    /**
     * Touch coordinates for the dictionary: moved by the learned offset of the detected key, so
     * the spatial model of the dictionary sees the touch where an average touch for the key is.
     * [x] and [y] are view coordinates, [correctionX] and [correctionY] convert them to keyboard
     * coordinates. Remembers the raw position (keyboard coordinates), so learning is not affected.
     */
    @Synchronized
    fun adjustForDictionary(x: Int, y: Int, correctionX: Int, correctionY: Int,
                            keyWidth: Int, keyHeight: Int, offset: FloatArray?): IntArray {
        if (offset == null || !offset[0].isFinite() || !offset[1].isFinite()) return intArrayOf(x, y)
        val ax = (x - offset[0] * keyWidth).toInt().coerceAtLeast(0)
        val ay = (y - offset[1] * keyHeight).toInt().coerceAtLeast(0)
        if (ax != x || ay != y) rawTouches[pack(ax + correctionX, ay + correctionY)] = pack(x + correctionX, y + correctionY)
        return intArrayOf(ax, ay)
    }

    private fun sq(f: Float) = f * f
    private fun pack(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
    private fun unpackX(p: Long) = (p shr 32).toInt()
    private fun unpackY(p: Long) = p.toInt()

    // ---------------- reading (settings screen) ----------------

    @Synchronized
    fun getLayouts(context: Context): List<LayoutData> {
        appContext = context.applicationContext
        ensureLoaded()
        return layouts.values.filter { it.sampleCount > 0 }.sortedByDescending { it.sampleCount }
    }

    /** Touches and misses per week, oldest first */
    @Synchronized
    fun getWeeklyStats(context: Context): List<WeekStats> {
        appContext = context.applicationContext
        ensureLoaded()
        return weeks.map { (week, stats) -> WeekStats(week, stats[0], stats[1]) }
    }

    private fun currentWeek() = (System.currentTimeMillis() / 86_400_000L / 7).toInt()

    /** Device the data was learned on, if it is not this device */
    @Synchronized
    fun getOtherLearnedOnDevice(context: Context): String? {
        appContext = context.applicationContext
        ensureLoaded()
        val device = learnedOnDevice ?: return null
        return if (device == currentDevice || layouts.isEmpty()) null else device
    }

    /** Keep data that was learned on another device, and continue learning on this one */
    @Synchronized
    fun acceptDataFromOtherDevice(context: Context) {
        appContext = context.applicationContext
        learnedOnDevice = currentDevice
        save()
    }

    @Synchronized
    fun clear(context: Context) {
        appContext = context.applicationContext
        layouts.clear()
        weeks.clear()
        pending = null
        revertCandidate = null
        unsavedSamples = 0
        loaded = !isUserLocked(context)
        learnedOnDevice = null
        offsetCache = HashMap()
        rawTouches.clear()
        val file = getFile(context)
        val legacyFile = getLegacyFile(context)
        ioExecutor.execute {
            file.delete()
            legacyFile.delete()
        }
    }

    /** Data file was replaced, e.g. by restoring a backup */
    @Synchronized
    fun reload() {
        layouts.clear()
        weeks.clear()
        pending = null
        revertCandidate = null
        unsavedSamples = 0
        loaded = false
        learnedOnDevice = null
        offsetCache = HashMap()
    }

    // ---------------- backup ----------------

    /** File to put into a backup, null if there is no data. Must not be called on the main thread. */
    fun getFileForBackup(context: Context): File? {
        synchronized(this) {
            appContext = context.applicationContext
            if (isUserLocked(context)) return null
            ensureLoaded()
            onFinishInput() // write unsaved data...
        }
        // ... and wait until it is written (without holding the lock, background tasks may need it)
        try { ioExecutor.submit(Runnable { }).get() } catch (_: Exception) { }
        return getFile(context).takeIf { it.isFile }
    }

    /**
     * Restore data from a backup. The data is validated and written in a clean form, invalid data
     * is rejected. Returns whether data was restored.
     */
    fun restore(context: Context, input: InputStream): Boolean {
        val text = readLimited(input) ?: return false
        val parsed = try {
            parse(JSONObject(text))
        } catch (t: Throwable) { // also catch errors like StackOverflowError from malicious input
            Log.w(TAG, "could not restore typing heatmap: ${t.javaClass.simpleName}")
            return false
        }
        val json = toJson(parsed.layouts.values.map { it.copyForSaving() }, parsed.device ?: currentDevice, parsed.weeks).toString()
        val file = getFile(context)
        // forget the current data first, so it can't be saved over the restored data
        synchronized(this) { reload() }
        try {
            // written in the same queue as normal saves, so earlier saves can't overwrite it
            ioExecutor.submit(Runnable { writeAtomically(file, json) }).get()
        } catch (e: Exception) {
            Log.w(TAG, "could not restore typing heatmap: ${e.javaClass.simpleName}")
            return false
        }
        synchronized(this) {
            getLegacyFile(context).delete()
            reload()
        }
        return true
    }

    private fun readLimited(input: InputStream): String? {
        val bytes = input.readNBytesCompat(MAX_FILE_SIZE + 1)
        if (bytes.size > MAX_FILE_SIZE) {
            Log.w(TAG, "typing heatmap too large, ignoring it")
            return null
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun InputStream.readNBytesCompat(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (total < limit) {
            val read = read(buffer, 0, minOf(buffer.size.toLong(), limit - total).toInt())
            if (read < 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    // ---------------- pure logic (tested) ----------------

    /**
     * Pairs of (index in typed, index in final) for code points that correspond to each other,
     * i.e. matches and substitutions of a minimal edit alignment (case insensitive).
     */
    fun align(typed: IntArray, final: IntArray): List<Pair<Int, Int>> {
        val n = typed.size
        val m = final.size
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            val cost = if (sameLetter(typed[i - 1], final[j - 1])) 0 else 1
            d[i][j] = minOf(d[i - 1][j - 1] + cost, d[i - 1][j] + 1, d[i][j - 1] + 1)
        }
        val pairs = ArrayList<Pair<Int, Int>>()
        var i = n
        var j = m
        while (i > 0 && j > 0) {
            val cost = if (sameLetter(typed[i - 1], final[j - 1])) 0 else 1
            when {
                d[i][j] == d[i - 1][j - 1] + cost -> { pairs.add(i - 1 to j - 1); i--; j-- }
                d[i][j] == d[i - 1][j] + 1 -> i--
                else -> j--
            }
        }
        pairs.reverse()
        return pairs
    }

    /**
     * Samples (intended code to sample) from the touches of a typed word, using the final word
     * as ground truth. [keyRect] gives x, y, width, height for a lowercase code point.
     */
    fun computeSamples(typedWord: String, finalWord: String, xs: IntArray, ys: IntArray,
                       adapted: Boolean = false, keyRect: (Int) -> IntArray?): List<Pair<Int, Sample>> {
        val typed = StringUtils.toCodePointArray(typedWord)
        val final = StringUtils.toCodePointArray(finalWord)
        val pairs = align(typed, final)
        val substitutions = pairs.count { !sameLetter(typed[it.first], final[it.second]) }
        // unrelated word (e.g. a picked prediction): don't learn from it
        if (pairs.isEmpty() || substitutions > maxOf(1, pairs.size / 3)) return emptyList()
        val result = ArrayList<Pair<Int, Sample>>()
        for ((ti, fi) in pairs) {
            if (ti >= xs.size || ti >= ys.size) continue
            // only letters: digits and symbols may have been typed on another keyboard (symbols)
            if (!Character.isLetter(typed[ti])) continue
            val sample = sampleFor(lower(final[fi]), lower(typed[ti]), xs[ti], ys[ti], adapted, keyRect) ?: continue
            result.add(sample)
        }
        return result
    }

    /** Sample for a touch at [x], [y] that was meant for [newCodePoint], if the keys are neighbors */
    fun selfCorrectionSample(deletedCodePoint: Int, x: Int, y: Int, newCodePoint: Int, adapted: Boolean = false,
                             keyRect: (Int) -> IntArray?): Pair<Int, Sample>? {
        val deleted = lower(deletedCodePoint)
        val intended = lower(newCodePoint)
        if (deleted == intended || !Character.isLetter(deleted)) return null
        val deletedRect = keyRect(deleted) ?: return null
        val intendedRect = keyRect(intended) ?: return null
        // neighbor keys: centers at most about one key apart (also diagonally)
        val distX = abs((deletedRect[0] + deletedRect[2] / 2f) - (intendedRect[0] + intendedRect[2] / 2f)) / intendedRect[2]
        val distY = abs((deletedRect[1] + deletedRect[3] / 2f) - (intendedRect[1] + intendedRect[3] / 2f)) / intendedRect[3]
        if (distX > 1.1f || distY > 1.1f) return null
        return sampleFor(intended, deleted, x, y, adapted, keyRect)
    }

    private fun sampleFor(intended: Int, touched: Int, x: Int, y: Int, adapted: Boolean, keyRect: (Int) -> IntArray?): Pair<Int, Sample>? {
        if (x < 0 || y < 0) return null // no coordinates
        if (!Character.isLetter(intended)) return null
        val rect = keyRect(intended) ?: return null
        if (rect[2] <= 0 || rect[3] <= 0) return null
        val dx = (x - (rect[0] + rect[2] / 2f)) / rect[2]
        val dy = (y - (rect[1] + rect[3] / 2f)) / rect[3]
        if (abs(dx) > MAX_OFFSET || abs(dy) > MAX_OFFSET) return null
        return intended to Sample(dx, dy, touched, adapted)
    }

    private fun lower(cp: Int) = Character.toLowerCase(cp)
    private fun sameLetter(a: Int, b: Int) = lower(a) == lower(b)

    // ---------------- persistence ----------------

    /** credential encrypted storage, excluded from Android backup and device-to-device transfer */
    private fun getFile(context: Context) = File(context.applicationContext.noBackupFilesDir, FILE_NAME)

    /** location used by earlier versions (device protected storage) */
    private fun getLegacyFile(context: Context) = File(DeviceProtectedUtils.getFilesDir(context), FILE_NAME)

    private fun ensureLoaded() {
        if (loaded) return
        val context = appContext ?: return
        if (isUserLocked(context)) return // credential encrypted storage is not available yet
        loaded = true
        val file = getFile(context)
        migrateLegacyFile(context, file)
        if (!file.isFile) return
        try {
            if (file.length() > MAX_FILE_SIZE) throw IllegalStateException("file too large")
            val parsed = parse(JSONObject(file.readText()))
            layouts.clear()
            layouts.putAll(parsed.layouts)
            weeks.clear()
            weeks.putAll(parsed.weeks)
            learnedOnDevice = parsed.device
            offsetCache = HashMap()
        } catch (t: Throwable) { // also catch errors like StackOverflowError from broken data
            // log only the type: the message may contain the file content
            Log.w(TAG, "could not read typing heatmap: ${t.javaClass.simpleName}")
            layouts.clear()
            weeks.clear()
            learnedOnDevice = null
            // keep the unreadable file instead of overwriting it with new data
            file.renameTo(File(file.parentFile, "$FILE_NAME.broken"))
        }
    }

    /** Move the file from the location of earlier versions, without risking to lose it */
    private fun migrateLegacyFile(context: Context, file: File) {
        val legacyFile = getLegacyFile(context)
        if (!legacyFile.isFile) return
        try {
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, "$FILE_NAME.tmp")
                legacyFile.copyTo(tmp, overwrite = true)
                if (!tmp.renameTo(file)) return // keep the old file, try again next time
            }
            legacyFile.delete()
        } catch (e: Exception) {
            Log.w(TAG, "could not move typing heatmap: ${e.javaClass.simpleName}")
        }
    }

    private fun save() {
        val context = appContext ?: return
        if (!loaded) return // never overwrite data that was not loaded
        unsavedSamples = 0
        offsetCache = HashMap() // new data is used for adaptation from now on
        // copy the data, and do the expensive conversion in background
        val copy = layouts.values.map { it.copyForSaving() }
        val weeksCopy = weeks.mapValues { it.value.copyOf() }
        val device = learnedOnDevice ?: currentDevice
        val file = getFile(context)
        ioExecutor.execute {
            try {
                writeAtomically(file, toJson(copy, device, weeksCopy).toString())
            } catch (t: Throwable) {
                Log.w(TAG, "could not write typing heatmap: ${t.javaClass.simpleName}")
            }
        }
    }

    private fun writeAtomically(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IllegalStateException("rename failed")
        }
    }

    /** Copy whose lists are not changed while writing in background */
    private class LayoutCopy(val id: String, val aspectRatio: Float, val shapes: List<KeyShape>, val samples: Map<Int, List<Sample>>)

    private fun LayoutData.copyForSaving() = LayoutCopy(id, aspectRatio, shapes, samples.mapValues { it.value.toList() })

    private fun toJson(layouts: List<LayoutCopy>, device: String, weeks: Map<Int, IntArray>): JSONObject {
        val root = JSONObject()
        root.put("version", 2)
        root.put("device", device)
        val layoutArray = JSONArray()
        for (layout in layouts) {
            val l = JSONObject()
            l.put("id", layout.id)
            l.put("aspect", r(if (layout.aspectRatio.isFinite()) layout.aspectRatio else 0.35f))
            val shapes = JSONArray()
            layout.shapes.forEach {
                if (!it.x.isFinite() || !it.y.isFinite() || !it.width.isFinite() || !it.height.isFinite()) return@forEach
                shapes.put(JSONArray().put(it.code).put(it.label).put(r(it.x)).put(r(it.y)).put(r(it.width)).put(r(it.height)))
            }
            l.put("keys", shapes)
            val samples = JSONObject()
            for ((code, list) in layout.samples) {
                val flat = JSONArray()
                list.forEach {
                    if (!it.dx.isFinite() || !it.dy.isFinite()) return@forEach
                    flat.put(r(it.dx)).put(r(it.dy)).put(it.touchedCode).put(if (it.adapted) 1 else 0)
                }
                samples.put(code.toString(), flat)
            }
            l.put("samples", samples)
            layoutArray.put(l)
        }
        root.put("layouts", layoutArray)
        val weeksJson = JSONObject()
        weeks.forEach { (week, stats) -> weeksJson.put(week.toString(), JSONArray().put(stats[0]).put(stats[1])) }
        root.put("weeks", weeksJson)
        return root
    }

    /** Read stored data, ignoring anything invalid or out of range */
    fun parse(root: JSONObject): ParsedData {
        val version = root.optInt("version", 1)
        val valuesPerSample = if (version >= 2) 4 else 3
        val device = root.optString("device", "").take(100).ifEmpty { null }
        val result = LinkedHashMap<String, LayoutData>()
        val weeks = java.util.TreeMap<Int, IntArray>()
        root.optJSONObject("weeks")?.let { w ->
            for (key in w.keys()) {
                val week = key.toIntOrNull() ?: continue
                val a = w.optJSONArray(key) ?: continue
                val touches = a.optInt(0, -1)
                val misses = a.optInt(1, -1)
                if (week < 0 || touches < 0 || misses < 0 || misses > touches) continue
                weeks[week] = intArrayOf(touches, misses)
            }
            while (weeks.size > MAX_WEEKS) weeks.remove(weeks.firstKey())
        }
        val layoutArray = root.optJSONArray("layouts") ?: return ParsedData(result, device, weeks)
        for (i in 0 until minOf(layoutArray.length(), MAX_LAYOUTS)) {
            val l = layoutArray.optJSONObject(i) ?: continue
            val id = l.optString("id", "").take(100)
            if (id.isEmpty()) continue
            val layout = LayoutData(id)
            val aspect = l.optDouble("aspect", 0.35).toFloat()
            layout.aspectRatio = if (aspect.isFinite() && aspect in 0.05f..3f) aspect else 0.35f
            val shapes = l.optJSONArray("keys") ?: JSONArray()
            layout.shapes = (0 until minOf(shapes.length(), MAX_KEY_SHAPES)).mapNotNull {
                val s = shapes.optJSONArray(it) ?: return@mapNotNull null
                if (s.length() < 6) return@mapNotNull null
                val values = (2..5).map { index -> s.optDouble(index, Double.NaN).toFloat() }
                if (values.any { v -> !v.isFinite() || v < -1f || v > 2f } || values[2] <= 0f || values[3] <= 0f)
                    return@mapNotNull null
                KeyShape(s.optInt(0), s.optString(1, "").take(MAX_LABEL_LENGTH), values[0], values[1], values[2], values[3])
            }
            val samples = l.optJSONObject("samples") ?: JSONObject()
            var keyCount = 0
            for (key in samples.keys()) {
                if (keyCount >= MAX_SAMPLE_KEYS) break
                val code = key.toIntOrNull() ?: continue
                if (!Character.isValidCodePoint(code) || !Character.isLetter(code)) continue
                val flat = samples.optJSONArray(key) ?: continue
                val count = flat.length() / valuesPerSample
                val list = ArrayDeque<Sample>()
                // keep only the newest samples
                for (index in maxOf(0, count - MAX_SAMPLES_PER_KEY) until count) {
                    val j = index * valuesPerSample
                    val dx = flat.optDouble(j, Double.NaN).toFloat()
                    val dy = flat.optDouble(j + 1, Double.NaN).toFloat()
                    val touched = flat.optInt(j + 2, -1)
                    if (!dx.isFinite() || !dy.isFinite() || abs(dx) > MAX_OFFSET || abs(dy) > MAX_OFFSET) continue
                    if (!Character.isValidCodePoint(touched) || touched == 0) continue
                    val adapted = valuesPerSample == 4 && flat.optInt(j + 3, 0) == 1
                    list.addLast(Sample(dx, dy, touched, adapted))
                }
                if (list.isNotEmpty()) {
                    layout.samples[code] = list
                    keyCount++
                }
            }
            result[layout.id] = layout
        }
        return ParsedData(result, device, weeks)
    }

    private fun r(f: Float): Double = (f * 1000.0).roundToLong() / 1000.0

    /** for tests */
    @Synchronized
    internal fun resetForTest() {
        layouts.clear()
        weeks.clear()
        pending = null
        revertCandidate = null
        unsavedSamples = 0
        loaded = true
        appContext = null
        learnedOnDevice = null
        offsetCache = HashMap()
        rawTouches.clear()
    }

    @Synchronized
    internal fun layoutForTest(id: String): LayoutData? = layouts[id]

    /** for tests: invalidate cached offsets without saving */
    @Synchronized
    internal fun clearOffsetCacheForTest() {
        offsetCache = HashMap()
    }
}
