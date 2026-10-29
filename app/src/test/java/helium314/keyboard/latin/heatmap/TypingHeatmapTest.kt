// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.heatmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TypingHeatmapTest {
    // simple keyboard: one row "qwertzuiop", keys 100 px wide and 150 px high, row starts at y = 0
    private val row = "qwertzuiop"
    private fun rect(cp: Int): IntArray? {
        val i = row.indexOf(cp.toChar())
        return if (i < 0) null else intArrayOf(i * 100, 0, 100, 150)
    }
    private fun center(c: Char) = row.indexOf(c) * 100 + 50

    private val geometry = object : TypingHeatmap.KeyGeometry {
        override val layoutId = "test"
        override fun keyRect(codePoint: Int) = rect(codePoint)
        override fun snapshot() = row.mapIndexed { i, c -> TypingHeatmap.KeyShape(c.code, c.toString(), i / 10f, 0f, 0.1f, 1f) }
        override val aspectRatio = 0.15f
        override val source: Any = this
    }
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() = TypingHeatmap.resetForTest()

    @Test fun `align pairs matches and substitutions`() {
        fun align(a: String, b: String) = TypingHeatmap.align(a.codePoints().toArray(), b.codePoints().toArray())
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2), align("tor", "tür"))
        // missing letter in typed word: no pair for the inserted letter
        assertEquals(listOf(0 to 0, 1 to 2), align("pt", "pot"))
        // extra letter typed
        assertEquals(listOf(0 to 0, 2 to 1), align("pqo", "po"))
        // case does not matter
        assertEquals(listOf(0 to 0, 1 to 1), align("Wo", "wO"))
    }

    @Test fun `samples are relative to the intended key`() {
        // "tir" typed, "tor" meant: the i touch is a miss for o
        val xs = intArrayOf(center('t') + 10, center('i') + 30, center('r'))
        val ys = intArrayOf(75, 75 + 15, 0)
        val samples = TypingHeatmap.computeSamples("tir", "tor", xs, ys, keyRect = ::rect)
        assertEquals(3, samples.size)
        val (code, miss) = samples[1]
        assertEquals('o'.code, code)
        assertEquals('i'.code, miss.touchedCode)
        assertEquals(-0.7f, miss.dx, 0.001f) // 70 px left of the o center
        assertEquals(0.1f, miss.dy, 0.001f)
        assertEquals(0.1f, samples[0].second.dx, 0.001f)
        assertEquals(-0.5f, samples[2].second.dy, 0.001f)
    }

    @Test fun `unrelated words and far touches are ignored`() {
        val xs = intArrayOf(center('q'), center('w'), center('e'))
        val ys = intArrayOf(75, 75, 75)
        assertTrue(TypingHeatmap.computeSamples("qwe", "pou", xs, ys, keyRect = ::rect).isEmpty())
        // touch 3 keys away from intended key
        val far = TypingHeatmap.computeSamples("ie", "te", intArrayOf(center('i'), center('e')), intArrayOf(75, 75), keyRect = ::rect)
        assertEquals(1, far.size)
        assertEquals('e'.code, far[0].first)
        // no coordinates
        assertTrue(TypingHeatmap.computeSamples("we", "we", intArrayOf(-1, -1), intArrayOf(-1, -1), keyRect = ::rect).isEmpty())
    }

    @Test fun `committed word is recorded when the next input follows`() {
        val xs = intArrayOf(center('w'), center('o') - 80) // second touch hits i
        TypingHeatmap.onWordCommitted(context, geometry, "wi", "wo", xs, intArrayOf(75, 75))
        assertNull(TypingHeatmap.layoutForTest("test"))
        TypingHeatmap.finalizePending()
        val layout = TypingHeatmap.layoutForTest("test")!!
        assertEquals(2, layout.sampleCount)
        assertEquals('i'.code, layout.samples['o'.code]!!.first().touchedCode)
    }

    @Test fun `reverted autocorrection uses the typed word`() {
        val xs = intArrayOf(center('w'), center('i'))
        TypingHeatmap.onWordCommitted(context, geometry, "wi", "wo", xs, intArrayOf(75, 75))
        TypingHeatmap.onCommitReverted()
        val layout = TypingHeatmap.layoutForTest("test")!!
        assertNull(layout.samples['o'.code])
        assertEquals('i'.code, layout.samples['i'.code]!!.first().touchedCode)
    }

    @Test fun `deleting into the committed word discards it`() {
        TypingHeatmap.onWordCommitted(context, geometry, "wi", "wo", intArrayOf(center('w'), center('i')), intArrayOf(75, 75))
        TypingHeatmap.discardPending()
        TypingHeatmap.finalizePending()
        assertNull(TypingHeatmap.layoutForTest("test"))
    }

    private fun samples(n: Int, dx: Float, dy: Float = 0f) = List(n) { TypingHeatmap.Sample(dx, dy, 'o'.code) }

    @Test fun `backspace without revert drops the committed word`() {
        TypingHeatmap.onWordCommitted(context, geometry, "wi", "wo", intArrayOf(center('w'), center('i')), intArrayOf(75, 75))
        TypingHeatmap.onBackspace() // e.g. deleting the space and editing the word
        TypingHeatmap.finalizePending()
        assertNull(TypingHeatmap.layoutForTest("test"))
    }

    @Test fun `backspace that reverts the autocorrection uses the typed word`() {
        TypingHeatmap.onWordCommitted(context, geometry, "wi", "wo", intArrayOf(center('w'), center('i')), intArrayOf(75, 75))
        TypingHeatmap.onBackspace()
        TypingHeatmap.onCommitReverted()
        assertEquals('i'.code, TypingHeatmap.layoutForTest("test")!!.samples['i'.code]!!.first().touchedCode)
    }

    @Test fun `touches and misses are counted per week`() {
        TypingHeatmap.onWordCommitted(context, geometry, "wi", "wo", intArrayOf(center('w'), center('i')), intArrayOf(75, 75))
        TypingHeatmap.finalizePending()
        val weeks = TypingHeatmap.getWeeklyStats(context)
        assertEquals(1, weeks.size)
        assertEquals(2, weeks[0].touches)
        assertEquals(1, weeks[0].misses)
    }

    @Test fun `offsets need enough data and are limited`() {
        assertNull(TypingHeatmap.effectiveOffset(samples(29, 0.4f), 0.33f))
        val full = TypingHeatmap.effectiveOffset(samples(100, 0.4f), 0.33f)!!
        assertEquals(0.33f, full[0], 0.001f) // limited to max shift
        val small = TypingHeatmap.effectiveOffset(samples(100, 0.1f, -0.1f), 0.33f)!!
        assertEquals(0.1f, small[0], 0.001f)
        assertEquals(-0.1f, small[1], 0.001f)
        // less weight with less data
        val half = TypingHeatmap.effectiveOffset(samples(65, 0.2f), 0.33f)!!
        assertTrue(half[0] > 0.05f && half[0] < 0.15f)
    }

    @Test fun `adapted detection picks the key the user means`() {
        val i = TypingHeatmap.Candidate('i'.code, 700f, 0f, 100f, 150f)
        val o = TypingHeatmap.Candidate('o'.code, 800f, 0f, 100f, 150f)
        // user hits o on average 30 % too far left
        val offsets = mapOf('o'.code to floatArrayOf(-0.3f, 0f))
        // touch on i, close to the border: now meant for o
        assertEquals('o'.code, TypingHeatmap.adaptedKeyCode(790f, 75f, listOf(i, o), offsets))
        // touch clearly on i: unchanged
        assertNull(TypingHeatmap.adaptedKeyCode(760f, 75f, listOf(i, o), offsets))
        // no offsets: unchanged
        assertNull(TypingHeatmap.adaptedKeyCode(790f, 75f, listOf(i, o), emptyMap()))
    }

    @Test fun `retyped neighbor letter is a miss for the new letter`() {
        // p deleted, o typed: the p touch was meant for o
        val sample = TypingHeatmap.selfCorrectionSample('p'.code, center('p') - 40, 75, 'o'.code, keyRect = ::rect)!!
        assertEquals('o'.code, sample.first)
        assertEquals('p'.code, sample.second.touchedCode)
        assertEquals(0.6f, sample.second.dx, 0.001f)
        // keys far apart: not a typo
        assertNull(TypingHeatmap.selfCorrectionSample('q'.code, center('q'), 75, 'p'.code, keyRect = ::rect))
        // same letter
        assertNull(TypingHeatmap.selfCorrectionSample('o'.code, center('o'), 75, 'o'.code, keyRect = ::rect))
    }

    @Test fun `learning uses raw touches, not coordinates adjusted for the dictionary`() {
        val raw = center('o') - 30
        val adjusted = TypingHeatmap.adjustForDictionary(raw, 75, 0, 0, 100, 150, floatArrayOf(-0.3f, 0f))
        assertEquals(center('o'), adjusted[0])
        TypingHeatmap.onWordCommitted(context, geometry, "wo", "wo", intArrayOf(center('w'), adjusted[0]), intArrayOf(75, adjusted[1]), true)
        TypingHeatmap.finalizePending()
        val sample = TypingHeatmap.layoutForTest("test")!!.samples['o'.code]!!.first()
        assertEquals(-0.3f, sample.dx, 0.001f)
        assertTrue(sample.adapted)
    }

    @Test fun `raw touches are found with view padding`() {
        // view coordinates are keyboard coordinates + 20 px padding; the word composer has keyboard coordinates
        val padding = 20
        val rawKeyboardX = center('o') - 30
        val adjustedView = TypingHeatmap.adjustForDictionary(rawKeyboardX + padding, 75 + padding, -padding, -padding, 100, 150, floatArrayOf(-0.3f, 0f))
        val adjustedKeyboardX = adjustedView[0] - padding
        val adjustedKeyboardY = adjustedView[1] - padding
        TypingHeatmap.onWordCommitted(context, geometry, "wo", "wo", intArrayOf(center('w'), adjustedKeyboardX), intArrayOf(75, adjustedKeyboardY))
        TypingHeatmap.finalizePending()
        assertEquals(-0.3f, TypingHeatmap.layoutForTest("test")!!.samples['o'.code]!!.first().dx, 0.001f)
    }

    @Test fun `only letters are learned`() {
        // "1" may have been typed on the symbols keyboard, its position says nothing about the letters
        val samples = TypingHeatmap.computeSamples("a1", "a1", intArrayOf(10, 20), intArrayOf(75, 75)) { intArrayOf(0, 0, 100, 150) }
        assertEquals(1, samples.size)
    }

    @Test fun `broken or malicious data is ignored when reading`() {
        val json = JSONObject("""
            {"version": 2, "device": "Test",
             "layouts": [{"id": "qwertz", "aspect": "NaN",
               "keys": [[111, "o", 0.8, 0, 0.1, 0.25], [112, "p", "NaN", 0, 0.1, 0.25], [113, "q", 0, 0, -1, 0.25]],
               "samples": {"111": [0.1, 0.2, 111, 0, "NaN", 0, 105, 0, "Infinity", 0, 105, 0, 99, 0, 105, 0, 0.3, 0.1, -5, 0],
                           "-3": [0.1, 0.1, 111, 0], "abc": [0.1, 0.1, 111, 0]}}]}
        """.trimIndent())
        val parsed = TypingHeatmap.parse(json)
        val layout = parsed.layouts["qwertz"]!!
        assertEquals(0.35f, layout.aspectRatio, 0.001f)
        assertEquals(1, layout.shapes.size) // only the valid key
        assertEquals(setOf('o'.code), layout.samples.keys) // invalid codes ignored
        assertEquals(1, layout.samples['o'.code]!!.size) // NaN, Infinity, too far, invalid code point ignored
        assertEquals("Test", parsed.device)
    }

    @Test fun `too many samples are limited when reading`() {
        val flat = JSONArray()
        repeat(1000) { flat.put(0.1).put(0.1).put('o'.code).put(0) }
        val layouts = JSONArray()
        repeat(30) { layouts.put(JSONObject().put("id", "l$it").put("samples", JSONObject().put("111", flat))) }
        val parsed = TypingHeatmap.parse(JSONObject().put("version", 2).put("layouts", layouts))
        assertTrue(parsed.layouts.size <= 16)
        assertEquals(400, parsed.layouts.values.first().samples['o'.code]!!.size)
    }
}
