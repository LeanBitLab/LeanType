// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceProcessingTimeoutTest {
    @Test
    fun longerRecordingsGetMoreTime() {
        assertEquals(60_000L, VoiceInputManager.processingTimeoutFor(0L)) // at least a minute
        assertEquals(60_000L, VoiceInputManager.processingTimeoutFor(10_000L))
        assertEquals(195_000L, VoiceInputManager.processingTimeoutFor(60_000L)) // 15 s + 3 min
        assertEquals(60_000L, VoiceInputManager.processingTimeoutFor(-5L))
    }
}
