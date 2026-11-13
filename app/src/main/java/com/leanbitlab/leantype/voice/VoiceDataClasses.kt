// SPDX-License-Identifier: GPL-3.0-only
package com.leanbitlab.leantype.voice

import android.os.ParcelFileDescriptor
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class VoiceEngineInfo(
    val contractVersion: Int,
    val pluginId: String,
    val displayName: String,
    val supportsVosk: Boolean,
    val supportsWhisper: Boolean,
    val supportsHybrid: Boolean
) : Parcelable

@Parcelize
data class ModelState(
    val engineType: String,
    val state: Int,
    val message: String?
) : Parcelable {
    companion object {
        const val STATE_MISSING = 0
        const val STATE_LOADING = 1
        const val STATE_READY = 2
        const val STATE_ERROR = 3
    }
}

@Parcelize
data class ModelImportRequest(
    val engineType: String,
    val language: String?,
    val sha256: String?,
    val sizeBytes: Long,
    val file: ParcelFileDescriptor
) : Parcelable

@Parcelize
data class VoiceSessionConfig(
    val sessionId: String,
    val mode: String,
    val languageTag: String?,
    val sampleRate: Int,
    val enablePartial: Boolean,
    val maxSegmentMs: Int,
    val hybridTimeoutMs: Int,
    val hybridFallbackToVosk: Boolean,
    val cpuThreads: Int = 4,
    val customPrompt: String? = null
) : Parcelable

object VoiceConstants {
    const val VOICE_CONTRACT_VERSION = 1

    const val ENGINE_VOSK = "vosk"
    const val ENGINE_WHISPER = "whisper"

    const val MODE_FAST = "FAST"
    const val MODE_ACCURATE = "ACCURATE"
    const val MODE_HYBRID = "HYBRID"

    const val VOICE_ERROR_MODEL_MISSING = 1001
    const val VOICE_ERROR_MODEL_LOADING = 1002
    const val VOICE_ERROR_MODEL_INVALID = 1003
    const val VOICE_ERROR_AUDIO_START_FAILED = 1004
    const val VOICE_ERROR_PLUGIN_CRASHED = 1005
    const val VOICE_ERROR_TIMEOUT = 1006
    const val VOICE_ERROR_CANCELLED = 1007
    const val VOICE_ERROR_UNKNOWN = 1008

    const val PREF_VOICE_OFFLINE_ENABLED = "voice_offline_enabled"
    const val PREF_VOICE_ONLINE_ENABLED = "voice_online_enabled"
    const val PREF_VOICE_PROVIDER = "voice_provider"
    const val VOICE_PROVIDER_OFFLINE = "offline"
    const val VOICE_PROVIDER_ONLINE = "online"
    const val VOICE_PROVIDER_THIRD_PARTY = "third_party"
    const val VOICE_PROVIDER_NONE = "none"

    const val PREF_VOICE_THIRD_PARTY_APP = "voice_third_party_app"
    const val VOICE_APP_SYSTEM_DEFAULT = "system_default"

    const val PREF_VOICE_MODE = "voice_mode"
    const val PREF_VOICE_HYBRID_TIMEOUT_MS = "voice_hybrid_timeout_ms"
    const val PREF_VOICE_HYBRID_FALLBACK = "voice_hybrid_fallback"
    const val PREF_VOICE_WHISPER_KEEP_LOADED_SECONDS = "voice_whisper_keep_loaded_seconds"
    const val PREF_VOICE_ADVANCED_VAD = "voice_advanced_vad"
    const val PREF_VOICE_COMMANDS_ENABLED = "voice_commands_enabled"
    const val PREF_VOICE_SMART_PUNCTUATION = "voice_smart_punctuation"
    const val PREF_VOICE_KEEP_SCREEN_ON = "voice_keep_screen_on"
    const val PREF_VOICE_SILENCE_TIMEOUT_SECONDS = "voice_silence_timeout_seconds"
    const val PREF_VOICE_LANGUAGE = "voice_language"
    const val VOICE_LANG_FOLLOW_KEYBOARD = "follow_keyboard"
    const val VOICE_LANG_AUTO = "auto"
    const val PREF_VOICE_CPU_THREADS = "voice_cpu_threads"
    const val PREF_VOICE_CUSTOM_PROMPT = "voice_custom_prompt"
    /** Transcribe the recording in pieces at speech pauses while still recording */
    const val PREF_VOICE_TRANSCRIBE_WHILE_SPEAKING = "voice_transcribe_while_speaking"
    const val PREF_VOICE_MIC_SENSITIVITY = "voice_mic_sensitivity"
    const val PREF_VOICE_MAX_DURATION_SECONDS = "voice_max_duration_seconds"
    const val PREF_VOICE_AUTO_RETRY = "pref_voice_auto_retry"
    const val PREF_USE_DEBUG_VOICE_STUB = "use_debug_voice_stub"
    const val VOICE_PLUGIN_PACKAGE = "com.leanbitlab.leantype.voice.offline"
}
