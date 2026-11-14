// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import com.leanbitlab.leantype.voice.VoiceConstants

data class VoiceModelItem(
    val id: String,
    val displayName: String,
    val engineType: String,
    val language: String,
    val languageCode: String = "",
    val sizeMb: String,
    val downloadUrl: String,
    val browserUrl: String,
    val description: String = ""
)

object VoiceModelRegistry {
    val whisperModels = listOf(
        VoiceModelItem(
            id = "whisper-tiny-q5_1",
            displayName = "Tiny (Multilingual)",
            engineType = VoiceConstants.ENGINE_WHISPER,
            language = "Multilingual (99 languages)",
            languageCode = "mul",
            sizeMb = "31 MB",
            downloadUrl = "https://github.com/LeanBitLab/leantype-whisper-models/releases/download/v1.0.0/leantype-whisper-tiny-q5_1.bin",
            browserUrl = "https://github.com/LeanBitLab/leantype-whisper-models/releases/tag/v1.0.0",
            description = "Fast multilingual speech recognition for 99 languages"
        ),
        VoiceModelItem(
            id = "whisper-base-q5_1",
            displayName = "Base (Multilingual)",
            engineType = VoiceConstants.ENGINE_WHISPER,
            language = "Multilingual (99 languages)",
            languageCode = "mul",
            sizeMb = "57 MB",
            downloadUrl = "https://github.com/LeanBitLab/leantype-whisper-models/releases/download/v1.0.0/leantype-whisper-base-q5_1.bin",
            browserUrl = "https://github.com/LeanBitLab/leantype-whisper-models/releases/tag/v1.0.0",
            description = "Balanced multilingual accuracy and performance"
        ),
        VoiceModelItem(
            id = "whisper-tiny.en-q5_1",
            displayName = "Tiny (English)",
            engineType = VoiceConstants.ENGINE_WHISPER,
            language = "English",
            languageCode = "en",
            sizeMb = "31 MB",
            downloadUrl = "https://github.com/LeanBitLab/leantype-whisper-models/releases/download/v1.0.0/leantype-whisper-tiny.en-q5_1.bin",
            browserUrl = "https://github.com/LeanBitLab/leantype-whisper-models/releases/tag/v1.0.0",
            description = "Ultra-fast English voice dictation"
        )
    )

    fun findById(id: String): VoiceModelItem? {
        return whisperModels.firstOrNull { it.id == id }
    }
}
