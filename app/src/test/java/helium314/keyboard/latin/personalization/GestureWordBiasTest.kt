// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.personalization

import helium314.keyboard.latin.personalization.GestureWordBias.Companion.MAX_ADJUSTMENT
import helium314.keyboard.latin.personalization.GestureWordBias.Companion.adjustScore
import helium314.keyboard.latin.personalization.GestureWordBias.Companion.adjustment
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GestureWordBiasTest {
    private val dir = File.createTempFile("gesture_bias", "").also { it.delete(); it.mkdirs() }
    private val file = File(dir, "gesture_word_bias.dat")

    @AfterTest fun cleanup() { dir.deleteRecursively() }

    // --- scoring rule ---

    @Test fun `no history means no change`() {
        assertEquals(0f, adjustment(0, 0, 0f))
    }

    @Test fun `picks raise and rejects lower the score`() {
        assertTrue(adjustment(2, 0, 0f) > 0f)
        assertTrue(adjustment(0, 2, 0f) < 0f)
    }

    @Test fun `adjustment is capped in both directions, also for corrupt counts`() {
        assertEquals(MAX_ADJUSTMENT, adjustment(1000, 0, 0f), 1e-6f)
        assertEquals(-MAX_ADJUSTMENT, adjustment(0, 1000, 0f), 1e-6f)
        assertEquals(0f, adjustment(-50, 0, 0f), 1e-6f) // negative counts must not invert the sign
        assertEquals(MAX_ADJUSTMENT, adjustment(Int.MAX_VALUE, 0, 0f), 1e-6f)
    }

    @Test fun `picks and rejects of the same word offset each other`() {
        assertEquals(0f, adjustment(2, 1, 0f), 1e-6f)
    }

    @Test fun `adjustment fades with time and ignores a clock that went backwards`() {
        val fresh = adjustment(4, 0, 0f)
        assertEquals(fresh / 4f, adjustment(4, 0, 28f), fresh * 0.02f) // two half-lives
        assertEquals(fresh, adjustment(4, 0, -10f), 1e-6f)
    }

    @Test fun `a close call flips but a clear lead stays`() {
        val bonus = adjustment(2, 0, 0f)
        assertTrue(1.0f * (1f + bonus) > 1.003f) // 0.3% gap flips after two picks
        assertTrue(1.0f * (1f + MAX_ADJUSTMENT) < 1.10f) // a 10% gap never does
    }

    @Test fun `score adjustment does not overflow or touch junk scores`() {
        assertEquals(Int.MAX_VALUE, adjustScore(Int.MAX_VALUE, MAX_ADJUSTMENT))
        assertEquals(-2_000_000_000, adjustScore(-2_000_000_000, MAX_ADJUSTMENT))
        assertEquals(0, adjustScore(0, MAX_ADJUSTMENT))
        assertEquals(1, adjustScore(1, -MAX_ADJUSTMENT))
        assertEquals(103_000_000, adjustScore(100_000_000, MAX_ADJUSTMENT))
        assertEquals(97_000_000, adjustScore(100_000_000, -MAX_ADJUSTMENT))
    }

    // --- store ---

    @Test fun `correction raises picked and lowers replaced, case and punctuation insensitive`() {
        val bias = GestureWordBias(file)
        repeat(3) { bias.recordCorrection("Typing", "ruling") }
        assertTrue(bias.getAdjustment("typing") > 0f)
        assertTrue(bias.getAdjustment("TYPING") > 0f)
        assertTrue(bias.getAdjustment("ruling") < 0f)
        assertEquals(0f, bias.getAdjustment("unknown"))
    }

    @Test fun `same word, short words and file breaking words are ignored`() {
        val bias = GestureWordBias(file)
        bias.recordCorrection("Typing", "typing")
        bias.recordCorrection("a", "to")
        bias.recordCorrection("bad\tword", "other")
        bias.recordCorrection("new\nline", "other")
        bias.recordCorrection("x".repeat(100), "other")
        assertTrue(bias.isEmpty)
    }

    @Test fun `accepting a word undoes one rejection and never goes below zero`() {
        val bias = GestureWordBias(file)
        bias.recordCorrection("typing", "ruling")
        bias.recordAccepted("ruling")
        assertEquals(0f, bias.getAdjustment("ruling"), 1e-6f)
        bias.recordAccepted("ruling")
        bias.recordAccepted("never-seen")
        assertEquals(0f, bias.getAdjustment("ruling"), 1e-6f)
    }

    @Test fun `data survives a restart`() {
        val bias = GestureWordBias(file)
        repeat(2) { bias.recordCorrection("done", "dio") }
        bias.flushIfDirty()
        assertFalse(File(dir, "gesture_word_bias.dat.tmp").exists())
        val reloaded = GestureWordBias(file)
        assertEquals(bias.getAdjustment("done"), reloaded.getAdjustment("done"), 1e-6f)
        assertEquals(bias.getAdjustment("dio"), reloaded.getAdjustment("dio"), 1e-6f)
    }

    @Test fun `malformed, hostile and stale lines in the file are skipped or clamped`() {
        val now = System.currentTimeMillis()
        val old = now - 100L * 86_400_000L
        file.writeText(
            "good\t2\t0\t$now\n" +
            "garbage line\n" +
            "neg\t-5\t0\t$now\n" +
            "huge\t999999999\t0\t$now\n" +
            "nan\tx\t0\t$now\n" +
            "stale\t5\t0\t$old\n" +
            "\t1\t1\t$now\n"
        )
        val bias = GestureWordBias(file)
        assertTrue(bias.getAdjustment("good") > 0f)
        assertEquals(0f, bias.getAdjustment("neg"), 1e-6f) // clamped to zero, not an inverted sign
        assertEquals(MAX_ADJUSTMENT, bias.getAdjustment("huge"), 1e-6f)
        assertEquals(0f, bias.getAdjustment("stale"))
        assertEquals(3, bias.size)
    }

    @Test fun `an unreadable file is ignored`() {
        file.mkdirs() // a directory where the file should be
        val bias = GestureWordBias(file)
        assertTrue(bias.isEmpty)
        bias.recordCorrection("done", "dio") // must not throw
        bias.flushIfDirty()
    }

    @Test fun `store never grows past its limit and drops the oldest`() {
        val bias = GestureWordBias(file)
        repeat(300) { bias.recordCorrection("pick$it", "reject$it") }
        assertTrue(bias.size <= 500)
        assertTrue(bias.getAdjustment("pick299") > 0f) // newest kept
    }

    @Test fun `remove and clear forget the words and the file`() {
        val bias = GestureWordBias(file)
        bias.recordCorrection("done", "dio")
        bias.flushIfDirty()
        bias.removeWord("done")
        assertEquals(0f, bias.getAdjustment("done"))
        bias.clear()
        assertTrue(bias.isEmpty)
        assertFalse(file.exists())
    }

    @Test fun `lookups during corrections on another thread do not fail`() {
        val bias = GestureWordBias(file)
        val writer = Thread { repeat(2000) { bias.recordCorrection("word${it % 50}", "other${it % 50}"); bias.flushIfDirty() } }
        writer.start()
        repeat(5000) { bias.getAdjustment("word${it % 50}") }
        writer.join()
        assertTrue(bias.size > 0)
    }
}
