// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import helium314.keyboard.latin.voice.VoiceInputManager.Companion.VoiceKeyAction
import helium314.keyboard.latin.voice.VoiceInputManager.VoiceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class VoicePiecewiseTest {
    private val bytesPerMs = 32 // 16 kHz, 16 bit

    @Test fun trailingSilenceIsCutButAPaddingStays() {
        val audio = ByteArray(8000 * bytesPerMs) // 8 s
        val trimmed = VoiceInputManager.trimTrailingSilence(audio, 2000 * bytesPerMs) // speech ends at 2 s
        assertEquals(2500 * bytesPerMs, trimmed.size) // 0.5 s padding
    }

    @Test fun shortAudioAndShortSpeechKeepAtLeastASecond() {
        val audio = ByteArray(5000 * bytesPerMs)
        assertEquals(1000 * bytesPerMs, VoiceInputManager.trimTrailingSilence(audio, 100 * bytesPerMs).size)
        val short = ByteArray(600 * bytesPerMs)
        assertSame(short, VoiceInputManager.trimTrailingSilence(short, 100 * bytesPerMs)) // nothing to cut
    }

    @Test fun trimmedAudioKeepsWholeSamples() {
        val audio = ByteArray(100_000)
        assertEquals(0, VoiceInputManager.trimTrailingSilence(audio, 40_001).size % 2)
    }

    @Test fun voiceKeyIsIgnoredWhileProcessing() {
        assertEquals(VoiceKeyAction.STOP, VoiceInputManager.voiceKeyActionFor(VoiceState.RECORDING, true))
        assertEquals(VoiceKeyAction.IGNORE, VoiceInputManager.voiceKeyActionFor(VoiceState.PROCESSING_FINAL, false))
        assertEquals(VoiceKeyAction.START, VoiceInputManager.voiceKeyActionFor(VoiceState.IDLE, false))
        assertEquals(VoiceKeyAction.START, VoiceInputManager.voiceKeyActionFor(VoiceState.ERROR, false))
    }
}
