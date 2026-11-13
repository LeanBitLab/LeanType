// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.leanbitlab.leantype.voice.IVoiceCallback
import com.leanbitlab.leantype.voice.VoiceConstants
import com.leanbitlab.leantype.voice.VoiceSessionConfig
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.NetworkUtils
import helium314.keyboard.latin.utils.ProofreadService
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class VoiceInputManager(
    private val ims: LatinIME,
    private val pluginManager: VoicePluginManager
) {

    enum class VoiceState {
        IDLE,
        CONNECTING_PLUGIN,
        STARTING_SESSION,
        RECORDING,
        PROCESSING_FINAL,
        ERROR
    }

    interface VoiceInputListener {
        fun onStateChanged(state: VoiceState)
        fun onError(message: String, canRetry: Boolean = false)
        fun onScreenKeepAliveChanged(keepAlive: Boolean) {}
    }

    data class CachedVoiceAudio(
        val wavBytes: ByteArray,
        val languageTag: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    var lastFailedOnlineAudio: CachedVoiceAudio? = null
        private set

    @Volatile
    var currentAudioLevel: Float = 0f
        private set

    @Volatile
    var isKeepScreenOnLeaseActive: Boolean = false
        private set

    fun clearFailedAudio() {
        lastFailedOnlineAudio = null
    }

    private fun isNetworkAvailable(): Boolean = NetworkUtils.isNetworkAvailable(ims)

    private fun isNetworkException(t: Throwable): Boolean {
        var cur: Throwable? = t
        while (cur != null) {
            if (cur is java.net.UnknownHostException ||
                cur is java.net.ConnectException ||
                cur is java.net.SocketTimeoutException ||
                cur is java.net.SocketException ||
                cur is java.net.NoRouteToHostException
            ) {
                return true
            }
            val msg = cur.message.orEmpty()
            if (msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("Network is unreachable", ignoreCase = true) ||
                msg.contains("Failed to connect", ignoreCase = true) ||
                msg.contains("No address associated with hostname", ignoreCase = true) ||
                msg.contains("Connection refused", ignoreCase = true) ||
                msg.contains("route to host", ignoreCase = true)
            ) {
                return true
            }
            cur = cur.cause
        }
        return !isNetworkAvailable()
    }

    private var state = VoiceState.IDLE
    private var activeSessionId: String? = null

    private var audioRecord: AudioRecord? = null
    private var audioPipeWriteSide: ParcelFileDescriptor? = null
    private var audioPipeReadSide: ParcelFileDescriptor? = null

    private val isRecording = AtomicBoolean(false)
    private var audioThread: Thread? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastPartialText: String? = null
    private var connectingTimeoutRunnable: Runnable? = null
    private var handshakeTimeoutRunnable: Runnable? = null
    private var processingTimeoutRunnable: Runnable? = null
    // start of the current recording, the time for processing depends on its length
    private var recordingStartedAt = 0L
    private var needsCapitalStart = true
    private var sessionEmittedText = ""

    private var isCurrentSessionOnline = false
    private val onlineAudioBuffer = ByteArrayOutputStream()
    private var onlineTranscriptionJob: Job? = null
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var listener: VoiceInputListener? = null
    var onOfflineSessionIdle: (() -> Unit)? = null

    fun setListener(listener: VoiceInputListener?) {
        this.listener = listener
    }

    fun getState(): VoiceState = state

    fun isRecording(): Boolean = state == VoiceState.RECORDING || state == VoiceState.STARTING_SESSION

    fun canStartVoice(): Boolean {
        val provider = RichInputMethodManager.getInstance().currentVoiceProvider
        val offlineEnabled = provider == VoiceConstants.VOICE_PROVIDER_OFFLINE
        val onlineEnabled = provider == VoiceConstants.VOICE_PROVIDER_ONLINE
        if (!offlineEnabled && !onlineEnabled) {
            Log.w(TAG, "canStartVoice: Voice input not enabled in preferences")
            return false
        }
        if (ContextCompat.checkSelfPermission(ims, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "canStartVoice: Missing RECORD_AUDIO permission")
            return false
        }
        if (isBlockedEditor(ims.currentInputEditorInfo)) {
            Log.w(TAG, "canStartVoice: Blocked editor (password)")
            return false
        }
        return true
    }

    fun startVoice() {
        if (state == VoiceState.RECORDING) {
            stopVoice()
            return
        }

        if (state != VoiceState.IDLE && state != VoiceState.ERROR) {
            Log.w(TAG, "Resetting previous state $state for new voice session")
            cancelVoice(notifyIdle = false)
        }

        if (!canStartVoice()) {
            notifyError("Voice input not available or permission missing")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ims.requestShowSelf(0)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to requestShowSelf", e)
            }
        }

        val onlineEnabled = RichInputMethodManager.getInstance().currentVoiceProvider == VoiceConstants.VOICE_PROVIDER_ONLINE
        if (onlineEnabled) {
            val service = ProofreadService(ims)
            val provider = service.getProvider()
            val hasKey = when (provider) {
                ProofreadService.AIProvider.GEMINI -> service.hasApiKey()
                ProofreadService.AIProvider.GROQ -> !service.getGroqToken().isNullOrBlank()
                ProofreadService.AIProvider.OPENAI -> !service.getHuggingFaceToken().isNullOrBlank()
            }
            if (!hasKey) {
                notifyError("API key for ${provider.name} not configured. Set it in Settings → AI Integration")
                return
            }
            startOnlineVoice()
            return
        }

        pluginManager.cancelScheduledUnbind()
        val isConnected = pluginManager.isPluginConnected()
        Log.i(TAG, "startVoice: isConnected=$isConnected")

        pluginManager.cancelSession()
        val sessionId = UUID.randomUUID().toString()
        activeSessionId = sessionId
        needsCapitalStart = true
        sessionEmittedText = ""
        isCurrentSessionOnline = false
        isKeepScreenOnLeaseActive = true

        if (!isConnected) {
            updateState(VoiceState.CONNECTING_PLUGIN)
            scheduleConnectingTimeout(sessionId)
            pluginManager.setConnectionListener(object : VoicePluginManager.PluginConnectionListener {
                override fun onPluginConnected(info: com.leanbitlab.leantype.voice.VoiceEngineInfo?) {
                    cancelConnectingTimeout()
                    mainHandler.post {
                        if (activeSessionId == sessionId && state == VoiceState.CONNECTING_PLUGIN) {
                            initiateSessionHandshake(sessionId)
                        }
                    }
                }

                override fun onPluginDisconnected() {
                    cancelConnectingTimeout()
                    mainHandler.post {
                        if (activeSessionId == sessionId) {
                            notifyError("Plugin disconnected unexpectedly")
                            cleanupSession()
                            updateState(VoiceState.ERROR)
                        }
                    }
                }
            })

            val bound = pluginManager.bindIfNeeded()
            if (!bound) {
                cancelConnectingTimeout()
                notifyError("Failed to bind to voice plugin")
                updateState(VoiceState.ERROR)
                return
            }
        } else {
            initiateSessionHandshake(sessionId)
        }
    }

    private fun startOnlineVoice() {
        if (!isNetworkAvailable()) {
            val warningMsg = ims.getString(R.string.voice_no_internet_warning)
            mainHandler.post {
                Toast.makeText(ims, warningMsg, Toast.LENGTH_SHORT).show()
            }
            notifyError(warningMsg, canRetry = true)
            updateState(VoiceState.ERROR)
            return
        }
        val sessionId = UUID.randomUUID().toString()
        activeSessionId = sessionId
        needsCapitalStart = true
        sessionEmittedText = ""
        isCurrentSessionOnline = true
        isKeepScreenOnLeaseActive = true
        synchronized(onlineAudioBuffer) {
            onlineAudioBuffer.reset()
        }

        val started = startAudioRecordingThread()
        if (started) {
            updateState(VoiceState.RECORDING)
        } else {
            notifyError("Failed to start audio recording")
            cleanupSession()
            updateState(VoiceState.ERROR)
        }
    }

    private fun userVoicePrompt(): String? =
        ims.prefs().getString(VoiceConstants.PREF_VOICE_CUSTOM_PROMPT, "")?.trim()?.takeIf { it.isNotEmpty() }

    private fun buildSessionConfig(sessionId: String, prompt: String?): VoiceSessionConfig {
        val prefLang = ims.prefs().getString(VoiceConstants.PREF_VOICE_LANGUAGE, VoiceConstants.VOICE_LANG_FOLLOW_KEYBOARD)
            ?: VoiceConstants.VOICE_LANG_FOLLOW_KEYBOARD
        val languageTag = when (prefLang) {
            VoiceConstants.VOICE_LANG_AUTO -> "auto"
            VoiceConstants.VOICE_LANG_FOLLOW_KEYBOARD, "" -> {
                try {
                    RichInputMethodManager.getInstance().currentSubtypeLocale.toLanguageTag()
                } catch (_: Exception) {
                    java.util.Locale.getDefault().toLanguageTag()
                }
            }
            else -> prefLang
        }

        val threads = ims.prefs().getString(VoiceConstants.PREF_VOICE_CPU_THREADS, "4")?.toIntOrNull() ?: 4

        return VoiceSessionConfig(
            sessionId = sessionId,
            mode = VoiceConstants.MODE_ACCURATE,
            languageTag = languageTag,
            sampleRate = SAMPLE_RATE,
            enablePartial = true,
            maxSegmentMs = 5000,
            hybridTimeoutMs = 0,
            hybridFallbackToVosk = false,
            cpuThreads = threads,
            customPrompt = prompt
        )
    }

    private fun initiateSessionHandshake(sessionId: String) {
        // transcribe the recording in pieces while still speaking
        if (ims.prefs().getBoolean(VoiceConstants.PREF_VOICE_TRANSCRIBE_WHILE_SPEAKING, false)) {
            startPiecewiseRecording()
            return
        }
        if (state == VoiceState.CONNECTING_PLUGIN) {
            updateState(VoiceState.STARTING_SESSION)
        }

        val pipe: Array<ParcelFileDescriptor>
        try {
            pipe = ParcelFileDescriptor.createPipe()
            audioPipeReadSide = pipe[0]
            audioPipeWriteSide = pipe[1]
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create audio pipe", e)
            notifyError("Failed to create audio pipe")
            updateState(VoiceState.ERROR)
            return
        }

        val config = buildSessionConfig(sessionId, userVoicePrompt())

        val callback = object : IVoiceCallback.Stub() {
            override fun onSessionStarted() {
                mainHandler.post {
                    if (activeSessionId == sessionId) {
                        cancelHandshakeTimeout()
                        updateState(VoiceState.RECORDING)
                    }
                }
            }

            override fun onPartial(text: String?) {
                Log.i(TAG, "Received onPartial from plugin: ${text?.length ?: 0} chars")
                mainHandler.post {
                    if (activeSessionId == sessionId && isRecording.get()) {
                        syncRecognizedText(text.orEmpty(), isFinal = false)
                    }
                }
            }

            override fun onFinal(text: String?) {
                Log.i(TAG, "Received onFinal: ${text?.length ?: 0} chars (isRecording=${isRecording.get()})")
                mainHandler.post {
                    if (activeSessionId == sessionId) {
                        syncRecognizedText(text.orEmpty(), isFinal = true)
                        lastPartialText = null

                        if (!isRecording.get()) {
                            Log.i(TAG, "Final session commit complete, transitioning to IDLE")
                            cleanupSession()
                            updateState(VoiceState.IDLE)
                            onOfflineSessionIdle?.invoke()
                        } else {
                            Log.i(TAG, "Segment refined & committed. Continuing continuous recording.")
                        }
                    }
                }
            }

            override fun onError(code: Int, message: String?) {
                Log.e(TAG, "Received onError: code=$code, message='$message'")
                mainHandler.post {
                    if (activeSessionId == sessionId) {
                        clearComposingText()
                        notifyError(message ?: "Voice error ($code)")
                        cleanupSession()
                        updateState(VoiceState.ERROR)
                        onOfflineSessionIdle?.invoke()
                    }
                }
            }

            override fun onSessionEnded() {
                Log.i(TAG, "Received onSessionEnded, state=$state")
                mainHandler.post {
                    if (activeSessionId == sessionId) {
                        cleanupSession()
                        if (state != VoiceState.ERROR) {
                            updateState(VoiceState.IDLE)
                        }
                        onOfflineSessionIdle?.invoke()
                    }
                }
            }
        }

        // Set handshake timeout guard (8000 ms)
        handshakeTimeoutRunnable = Runnable {
            if (activeSessionId == sessionId && state == VoiceState.STARTING_SESSION) {
                Log.e(TAG, "Session handshake timed out")
                notifyError("Voice session handshake timed out")
                pluginManager.cancelSession()
                cleanupSession()
                updateState(VoiceState.ERROR)
                onOfflineSessionIdle?.invoke()
            }
        }
        handshakeTimeoutRunnable?.let {
            mainHandler.postDelayed(it, HANDSHAKE_TIMEOUT_MS)
        }

        // Start hardware audio capture IMMEDIATELY so the green mic privacy dot appears without IPC delay
        startAudioRecordingThread()

        try {
            val pfdForPlugin = audioPipeReadSide
            if (pfdForPlugin != null) {
                audioPipeReadSide = null
                try {
                    pluginManager.startSession(config, pfdForPlugin, callback)
                } finally {
                    closeQuietly(pfdForPlugin)
                }
            } else {
                throw IllegalStateException("Read-side pipe descriptor is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remote exception starting voice session", e)
            cancelHandshakeTimeout()
            notifyError("Failed to start voice session with plugin")
            cleanupSession()
            updateState(VoiceState.ERROR)
        }
    }

    private fun startAudioRecordingThread(): Boolean {
        stopAudioLoop() // Prevent zombie thread overlap on rapid re-entry
        if (ContextCompat.checkSelfPermission(ims, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "startAudioRecordingThread: Missing RECORD_AUDIO permission")
            return false
        }

        val audioManager = ims.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        try {
            if (audioManager?.isMicrophoneMute == true) {
                Log.w(TAG, "Microphone was muted in AudioManager, unmuting...")
                audioManager.isMicrophoneMute = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unmute via AudioManager", e)
        }

        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufSize <= 0) {
            Log.e(TAG, "startAudioRecordingThread: Invalid min buffer size: $minBufSize")
            return false
        }

        // Multiply by 4 (at least 8192) to prevent hardware buffer overruns during Whisper inference blocks
        val bufferSize = maxOf(minBufSize * 4, FRAME_SIZE_BYTES * 8, 8192)

        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT
        )
        var record: AudioRecord? = null
        for (source in sources) {
            try {
                val candidate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val audioFormat = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                    val builder = AudioRecord.Builder()
                        .setAudioSource(source)
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(bufferSize)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        builder.setContext(ims)
                    }
                    builder.build()
                } else {
                    AudioRecord(
                        source,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                }
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    Log.i(TAG, "AudioRecord initialized successfully with source: $source")
                    record = candidate
                    break
                } else {
                    candidate.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create AudioRecord with source $source", e)
            }
        }

        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize (state=${record?.state})")
            record?.release()
            return false
        }

        audioRecord = record
        try {
            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Exception in AudioRecord.startRecording", e)
            audioRecord?.release()
            audioRecord = null
            return false
        }

        isRecording.set(true)
        val isOnline = isCurrentSessionOnline
        val piecewise = piecewiseActive
        val writePfd = if (!isOnline && !piecewise) {
            audioPipeWriteSide ?: return false
        } else null

        val silenceTimeoutSec = ims.prefs().getString(VoiceConstants.PREF_VOICE_SILENCE_TIMEOUT_SECONDS, "3")?.toIntOrNull() ?: 3
        val silenceTimeoutMs = if (silenceTimeoutSec > 0) silenceTimeoutSec * 1000L else 0L
        val initialTimeoutMs = if (silenceTimeoutSec > 0) maxOf(silenceTimeoutSec * 2000L, 6000L) else 0L

        val maxDurationSec = resolveMaxDurationSec(ims.prefs().getString(VoiceConstants.PREF_VOICE_MAX_DURATION_SECONDS, null))
        val maxDurationMs = maxDurationSec * 1000L

        val sensitivity = ims.prefs().getString(VoiceConstants.PREF_VOICE_MIC_SENSITIVITY, "normal")
        val speechRmsThreshold = when (sensitivity) {
            "high" -> 60.0
            "low" -> 250.0
            else -> 120.0
        }

        audioThread = Thread({
            val buffer = ByteArray(FRAME_SIZE_BYTES)
            var outputStream: FileOutputStream? = null
            var totalBytesWritten = 0L
            val sessionStartTime = System.currentTimeMillis()
            recordingStartedAt = sessionStartTime
            var lastSpeechTime = sessionStartTime
            var hasSpoken = false
            var ambientNoiseRms = speechRmsThreshold * 0.5
            // transcribe while speaking: the audio since the last cut, given to Whisper at a pause
            val piece = if (piecewise) ByteArrayOutputStream() else null
            var pieceHasSpeech = false
            var pieceLastSpeech = 0L

            try {
                if (!isOnline && writePfd != null) {
                    outputStream = FileOutputStream(writePfd.fileDescriptor)
                }
                while (isRecording.get()) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        if (isOnline) {
                            synchronized(onlineAudioBuffer) {
                                onlineAudioBuffer.write(buffer, 0, read)
                            }
                        } else if (piece != null) {
                            piece.write(buffer, 0, read)
                        } else {
                            outputStream?.write(buffer, 0, read)
                            outputStream?.flush()
                        }
                        totalBytesWritten += read

                        val now = System.currentTimeMillis()
                        if (maxDurationMs > 0L && (now - sessionStartTime >= maxDurationMs)) {
                            Log.i(TAG, "Max recording duration (${maxDurationMs}ms) reached. Stopping voice input.")
                            mainHandler.post { stopVoice() }
                            break
                        }

                        var sum = 0.0
                        var sampleCount = 0
                        var i = 0
                        while (i < read - 1) {
                            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
                            sum += sample.toDouble() * sample.toDouble()
                            sampleCount++
                            i += 2
                        }
                        val rms = if (sampleCount > 0) kotlin.math.sqrt(sum / sampleCount) else 0.0

                        // Normalize RMS for live audio visualizer (typical speech RMS: 100 - 3000)
                        val rawNormalized = if (rms > 40.0) {
                            ((rms - 40.0) / 1400.0).coerceIn(0.0, 1.0)
                        } else {
                            0.0
                        }
                        currentAudioLevel = kotlin.math.sqrt(rawNormalized).toFloat()

                        // Adaptively track noise floor to avoid background noise holding silence or screen-on leases
                        val effectiveSpeechThreshold = maxOf(speechRmsThreshold, ambientNoiseRms * 1.75 + 30.0)
                        val isSpeech = rms > effectiveSpeechThreshold
                        if (isSpeech) {
                            lastSpeechTime = now
                            hasSpoken = true
                        } else {
                            ambientNoiseRms = 0.95 * ambientNoiseRms + 0.05 * rms
                        }

                        if (piece != null) {
                            // same speech detection as the silence timeout, so pauses are found in noisy places too
                            if (isSpeech) {
                                pieceHasSpeech = true
                                pieceLastSpeech = now
                            }
                            val pieceMs = piece.size() / BYTES_PER_MS
                            val atPause = pieceHasSpeech && now - pieceLastSpeech >= PIECE_PAUSE_MS
                            // a piece needs some seconds of speech: Whisper invents text for very short ones
                            if ((atPause && pieceMs >= PIECE_MIN_MS) || pieceMs >= PIECE_MAX_MS) {
                                val bytes = piece.toByteArray()
                                piece.reset()
                                val hadSpeech = pieceHasSpeech
                                pieceHasSpeech = false
                                if (hadSpeech) mainHandler.post { onPieceRecorded(bytes) }
                            }
                        }

                        val keepScreenOnActive = shouldKeepRecordingScreenOn(now - lastSpeechTime)
                        if (keepScreenOnActive != isKeepScreenOnLeaseActive) {
                            isKeepScreenOnLeaseActive = keepScreenOnActive
                            mainHandler.post {
                                if (state == VoiceState.RECORDING) {
                                    listener?.onScreenKeepAliveChanged(keepScreenOnActive)
                                }
                            }
                        }

                        if (silenceTimeoutMs > 0L) {
                            if (hasSpoken && (now - lastSpeechTime > silenceTimeoutMs)) {
                                Log.i(TAG, "Silence timeout (${silenceTimeoutMs}ms) detected after speech. Stopping voice input.")
                                mainHandler.post { stopVoice() }
                                break
                            } else if (!hasSpoken && (now - sessionStartTime > initialTimeoutMs)) {
                                Log.i(TAG, "Initial silence timeout (${initialTimeoutMs}ms) detected. Cancelling voice input.")
                                mainHandler.post { cancelVoice() }
                                break
                            }
                        }
                    } else if (read < 0) {
                        Log.e(TAG, "AudioRecord read error: $read")
                        break
                    } else {
                        // Prevent 100% CPU busy-spin when AudioRecord.read returns 0
                        try {
                            Thread.sleep(10)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRecording.get()) {
                    Log.e(TAG, "Exception in audio write loop", e)
                }
            } finally {
                currentAudioLevel = 0f
                if (piece != null) {
                    val rest = if (pieceHasSpeech) piece.toByteArray() else null
                    mainHandler.post { onPiecewiseRecordingEnded(rest) }
                }
                try { outputStream?.close() } catch (_: Exception) {}
                Log.i(TAG, "Audio loop ended. Total wrote: $totalBytesWritten bytes")
                if (isRecording.get()) {
                    Log.i(TAG, "Audio loop finished while isRecording=true. Dispatching stopVoice()")
                    mainHandler.post { stopVoice() }
                }
            }
        }, "VoiceAudioThread").apply {
            isDaemon = true
            start()
        }

        return true
    }

    private fun scheduleConnectingTimeout(sessionId: String) {
        cancelConnectingTimeout()
        val runnable = Runnable {
            if (activeSessionId == sessionId && state == VoiceState.CONNECTING_PLUGIN) {
                Log.w(TAG, "Voice plugin connection timed out (8s)")
                notifyError("Voice plugin connection timed out")
                cleanupSession()
                updateState(VoiceState.ERROR)
                onOfflineSessionIdle?.invoke()
            }
        }
        connectingTimeoutRunnable = runnable
        mainHandler.postDelayed(runnable, HANDSHAKE_TIMEOUT_MS)
    }

    private fun cancelConnectingTimeout() {
        connectingTimeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            connectingTimeoutRunnable = null
        }
    }

    private fun scheduleProcessingTimeout() {
        cancelProcessingTimeout()
        // offline recognition of a longer message can take much longer than a fixed 15 s on a phone
        val recordedMs = if (recordingStartedAt > 0L) System.currentTimeMillis() - recordingStartedAt else 0L
        val timeoutMs = processingTimeoutFor(recordedMs)
        val runnable = Runnable {
            if (state == VoiceState.PROCESSING_FINAL) {
                Log.w(TAG, "Voice processing timed out (${timeoutMs / 1000}s)")
                notifyError("Voice processing timed out")
                val wasOffline = !isCurrentSessionOnline
                cleanupSession()
                updateState(VoiceState.ERROR)
                if (wasOffline) {
                    onOfflineSessionIdle?.invoke()
                }
            }
        }
        processingTimeoutRunnable = runnable
        mainHandler.postDelayed(runnable, timeoutMs)
    }

    private fun cancelProcessingTimeout() {
        processingTimeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            processingTimeoutRunnable = null
        }
    }

    private fun cancelHandshakeTimeout() {
        handshakeTimeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            handshakeTimeoutRunnable = null
        }
    }

    fun stopVoice() {
        Log.i(TAG, "stopVoice() called, state=$state, isOnline=$isCurrentSessionOnline")
        if (state == VoiceState.RECORDING || state == VoiceState.STARTING_SESSION || state == VoiceState.CONNECTING_PLUGIN) {
            updateState(VoiceState.PROCESSING_FINAL)
            scheduleProcessingTimeout()
            stopAudioLoop()
            if (isCurrentSessionOnline) {
                processOnlineTranscription()
            } else if (!piecewiseActive) {
                pluginManager.stopSession()
            }
            // transcribing while speaking: the end of the recording is reported by the audio thread
        }
    }

    private fun processOnlineTranscription() {
        val pcmBytes = synchronized(onlineAudioBuffer) {
            onlineAudioBuffer.toByteArray().also { onlineAudioBuffer.reset() }
        }

        if (pcmBytes.size < 3200) { // Less than 100ms of audio
            Log.i(TAG, "Online voice audio too short (${pcmBytes.size} bytes), skipping")
            cleanupSession()
            updateState(VoiceState.IDLE)
            return
        }

        val wavBytes = AudioUtils.pcmToWav(pcmBytes, SAMPLE_RATE, 1, 16)
        val prefLang = ims.prefs().getString(VoiceConstants.PREF_VOICE_LANGUAGE, VoiceConstants.VOICE_LANG_FOLLOW_KEYBOARD)
            ?: VoiceConstants.VOICE_LANG_FOLLOW_KEYBOARD
        val languageTag = when (prefLang) {
            VoiceConstants.VOICE_LANG_AUTO -> "auto"
            VoiceConstants.VOICE_LANG_FOLLOW_KEYBOARD, "" -> {
                try {
                    RichInputMethodManager.getInstance().currentSubtypeLocale.toLanguageTag()
                } catch (_: Exception) {
                    java.util.Locale.getDefault().toLanguageTag()
                }
            }
            else -> prefLang
        }

        transcribeOnlineWav(wavBytes, languageTag, retryCount = 0)
    }

    private fun transcribeOnlineWav(wavBytes: ByteArray, languageTag: String, retryCount: Int) {
        val sessionId = activeSessionId ?: UUID.randomUUID().toString().also { activeSessionId = it }
        val service = ProofreadService(ims)
        val cachedAudio = CachedVoiceAudio(wavBytes, languageTag)
        val autoRetryEnabled = ims.prefs().getBoolean(VoiceConstants.PREF_VOICE_AUTO_RETRY, true)

        if (!isNetworkAvailable()) {
            Log.w(TAG, "No internet connection before voice AI API access")
            lastFailedOnlineAudio = cachedAudio
            val warningMsg = ims.getString(R.string.voice_no_internet_warning)
            mainHandler.post {
                Toast.makeText(ims, warningMsg, Toast.LENGTH_SHORT).show()
            }
            notifyError(warningMsg, canRetry = true)
            cleanupSession()
            updateState(VoiceState.ERROR)
            return
        }

        onlineTranscriptionJob?.cancel()
        onlineTranscriptionJob = coroutineScope.launch(Dispatchers.IO) {
            val result = try {
                kotlinx.coroutines.withTimeout(25_000L) {
                    service.transcribeAudio(wavBytes, languageTag)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Result.failure(Exception("Transcription timed out (25s)"))
            } catch (e: Exception) {
                Result.failure(e)
            }
            mainHandler.post {
                if (activeSessionId == sessionId) {
                    result.onSuccess { transcribedText ->
                        Log.i(TAG, "Online transcription success: ${transcribedText.length} chars")
                        lastFailedOnlineAudio = null
                        syncRecognizedText(transcribedText, isFinal = true)
                        lastPartialText = null
                        cleanupSession()
                        updateState(VoiceState.IDLE)
                    }.onFailure { ex ->
                        val isNetworkErr = isNetworkException(ex)
                        val err = if (isNetworkErr) {
                            ims.getString(R.string.voice_no_internet_warning)
                        } else {
                            ex.message ?: "Transcription failed"
                        }
                        Log.e(TAG, "Online transcription error (retryCount=$retryCount): $err", ex)
                        if (isNetworkErr) {
                            mainHandler.post {
                                Toast.makeText(ims, err, Toast.LENGTH_SHORT).show()
                            }
                        }
                        if (autoRetryEnabled && retryCount < 1 && !isNetworkErr) {
                            Log.i(TAG, "Auto-retrying online transcription in 1500ms...")
                            mainHandler.postDelayed({
                                if (state == VoiceState.PROCESSING_FINAL) {
                                    transcribeOnlineWav(wavBytes, languageTag, retryCount + 1)
                                }
                            }, 1500L)
                        } else {
                            lastFailedOnlineAudio = cachedAudio
                            notifyError(err, canRetry = true)
                            cleanupSession()
                            updateState(VoiceState.ERROR)
                        }
                    }
                }
            }
        }
    }

    fun retryLastFailedOnlineVoice() {
        val cached = lastFailedOnlineAudio ?: return
        if (!isNetworkAvailable()) {
            val warningMsg = ims.getString(R.string.voice_no_internet_warning)
            mainHandler.post {
                Toast.makeText(ims, warningMsg, Toast.LENGTH_SHORT).show()
            }
            notifyError(warningMsg, canRetry = true)
            return
        }
        Log.i(TAG, "Retrying last failed online voice audio")
        updateState(VoiceState.PROCESSING_FINAL)
        scheduleProcessingTimeout()
        activeSessionId = UUID.randomUUID().toString()
        transcribeOnlineWav(cached.wavBytes, cached.languageTag, retryCount = 0)
    }

    @JvmOverloads
    fun cancelVoice(notifyIdle: Boolean = true) {
        Log.i(TAG, "cancelVoice() called, state=$state, isOnline=$isCurrentSessionOnline")
        if (state != VoiceState.IDLE || isRecording.get() || audioThread != null || audioRecord != null) {
            val wasOffline = !isCurrentSessionOnline
            cleanupSession()
            pluginManager.cancelSession()
            clearComposingText()
            updateState(VoiceState.IDLE)
            if (notifyIdle && wasOffline) {
                onOfflineSessionIdle?.invoke()
            }
        }
    }

    private fun stopAudioLoop() {
        currentAudioLevel = 0f
        if (!isRecording.getAndSet(false)) return
        Log.i(TAG, "stopAudioLoop() executing")

        // 1. Unblock the blocking native read() call by stopping AudioRecord
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }

        // 2. Wait for background VoiceAudioThread to fully exit native read() and terminate
        audioThread?.let { thread ->
            try {
                thread.join(1000)
                // 3. Edge-case guard: if driver hung, skip release to avoid native SIGABRT proxy crash
                if (thread.isAlive) {
                    Log.w(TAG, "VoiceAudioThread hung. Skipping release() to avoid native proxy crash.")
                    audioThread = null
                    audioRecord = null
                    closeQuietly(audioPipeWriteSide)
                    audioPipeWriteSide = null
                    return
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.w(TAG, "Interrupted while joining audioThread", e)
            }
        }
        audioThread = null

        // 4. Safe to destroy native proxy ONLY after thread is confirmed dead
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null

        closeQuietly(audioPipeWriteSide)
        audioPipeWriteSide = null
    }

    // ---- transcribe while speaking ----
    // The recording is cut at speech pauses and each piece is given to the plugin as its own session while
    // recording goes on, one piece at a time. After stopping, only the last piece is left to transcribe.

    private var piecewiseActive = false
    private val pendingPieces = java.util.ArrayDeque<ByteArray>()
    /** id of the plugin session transcribing a piece, null if none */
    private var pieceInFlight: String? = null
    private var piecewiseRecordingEnded = false
    /** the text recognized so far, given to Whisper as context for the next piece */
    private var piecewiseContext = ""

    /** the field the dictation was started in: text of later pieces goes only there */
    private var piecewiseEditor: String? = null

    private fun editorKey(info: EditorInfo?): String? = info?.let { "${it.packageName}|${it.fieldId}|${it.inputType}" }

    private fun startPiecewiseRecording() {
        piecewiseActive = true
        piecewiseEditor = editorKey(ims.currentInputEditorInfo)
        pendingPieces.clear()
        pieceInFlight = null
        piecewiseRecordingEnded = false
        piecewiseContext = ""
        cancelHandshakeTimeout()
        if (startAudioRecordingThread()) {
            updateState(VoiceState.RECORDING)
        } else {
            notifyError("Failed to start audio recording")
            cleanupSession()
            updateState(VoiceState.ERROR)
        }
    }

    private fun onPieceRecorded(bytes: ByteArray) {
        if (!piecewiseActive) return
        pendingPieces.addLast(bytes)
        transcribeNextPiece()
    }

    private fun onPiecewiseRecordingEnded(rest: ByteArray?) {
        if (!piecewiseActive) return
        if (rest != null) pendingPieces.addLast(rest)
        piecewiseRecordingEnded = true
        transcribeNextPiece()
    }

    private fun transcribeNextPiece() {
        if (!piecewiseActive || pieceInFlight != null) return
        val bytes = pendingPieces.pollFirst()
        if (bytes == null) {
            if (piecewiseRecordingEnded) {
                Log.i(TAG, "Transcribing while speaking: all pieces done")
                cleanupSession()
                updateState(VoiceState.IDLE)
            }
            return
        }
        val pieceId = UUID.randomUUID().toString()
        pieceInFlight = pieceId
        val context = piecewiseContext.takeLast(PIECE_CONTEXT_CHARS).trim()
        val prompt = listOfNotNull(userVoicePrompt(), context.takeIf { it.isNotEmpty() }).joinToString(" ").ifBlank { null }
        val pipe = try {
            ParcelFileDescriptor.createPipe()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create audio pipe for a piece", e)
            failPiecewise("Failed to create audio pipe")
            return
        }
        val callback = object : IVoiceCallback.Stub() {
            override fun onSessionStarted() {}
            override fun onPartial(text: String?) {}
            override fun onFinal(text: String?) {
                Log.i(TAG, "Piece recognized: ${text?.length ?: 0} chars")
                mainHandler.post {
                    if (pieceInFlight != pieceId) return@post
                    val editor = ims.currentInputEditorInfo
                    if (editorKey(editor) != piecewiseEditor || isBlockedEditor(editor)) {
                        // another app or field meanwhile: the rest of the dictation must not end up there
                        Log.w(TAG, "Transcribing while speaking: field changed, dropping the rest")
                        // say so instead of silently dropping it
                        android.widget.Toast.makeText(ims, ims.getString(R.string.voice_piecewise_field_changed), android.widget.Toast.LENGTH_LONG).show()
                        pendingPieces.clear()
                        pluginManager.cancelSession()
                        cleanupSession()
                        updateState(VoiceState.IDLE)
                        return@post
                    }
                    val recognized = text.orEmpty().trim()
                    if (recognized.isNotEmpty()) {
                        syncRecognizedText(recognized, isFinal = true)
                        piecewiseContext = (piecewiseContext + " " + recognized).takeLast(PIECE_CONTEXT_CHARS * 2)
                    }
                }
            }
            override fun onError(code: Int, message: String?) {
                Log.e(TAG, "Piece error: code=$code, message='$message'")
                mainHandler.post { if (pieceInFlight == pieceId) failPiecewise(message ?: "Voice error ($code)") }
            }
            override fun onSessionEnded() {
                // the next piece only after the plugin is done, a new session would cancel this one
                mainHandler.post {
                    if (pieceInFlight != pieceId) return@post
                    pieceInFlight = null
                    transcribeNextPiece()
                }
            }
        }
        val started = try {
            pluginManager.startSession(buildSessionConfig(pieceId, prompt), pipe[0], callback)
        } finally {
            closeQuietly(pipe[0])
        }
        if (!started) {
            closeQuietly(pipe[1])
            failPiecewise("Failed to start voice session with plugin")
            return
        }
        Log.i(TAG, "Transcribing piece of ${bytes.size / BYTES_PER_MS} ms, ${pendingPieces.size} waiting")
        // the pipe holds only a few seconds of audio: write while the plugin reads, not on this thread
        Thread({
            try {
                FileOutputStream(pipe[1].fileDescriptor).use { it.write(bytes) }
            } catch (e: Exception) {
                Log.w(TAG, "Writing a piece to the plugin failed", e)
            } finally {
                closeQuietly(pipe[1])
            }
        }, "VoicePieceWriter").apply { isDaemon = true; start() }
    }

    private fun failPiecewise(message: String) {
        notifyError(message)
        pluginManager.cancelSession()
        cleanupSession()
        updateState(VoiceState.ERROR)
    }

    private fun syncRecognizedText(rawText: String, isFinal: Boolean) {
        val ic = ims.currentInputConnection
        if (ic == null) {
            Log.e(TAG, "syncRecognizedText: InputConnection lost! (ic is null, isFinal=$isFinal)")
            return
        }
        if (!isRecording.get() && !isFinal) return

        val isSmartPunctuationEnabled = ims.prefs().getBoolean(VoiceConstants.PREF_VOICE_SMART_PUNCTUATION, true)
        val processedRaw = if (!isSmartPunctuationEnabled) {
            rawText.replace(PUNCTUATION_REGEX, "")
        } else {
            rawText
        }
        val trimmed = processedRaw.trim()

        // If onFinal has empty text (e.g. silence timeout fired after audio stream closed),
        // lock whatever text was already emitted during partials and commit a trailing space.
        if (isFinal && trimmed.isEmpty()) {
            if (sessionEmittedText.isNotEmpty()) {
                ic.beginBatchEdit()
                try {
                    ic.finishComposingText()
                    ic.commitText(" ", 1)
                    val lastChar = sessionEmittedText.lastOrNull()
                    needsCapitalStart = lastChar != null && lastChar in ".!?"
                } finally {
                    ic.endBatchEdit()
                    sessionEmittedText = ""
                }
            }
            return
        }

        if (trimmed.isEmpty()) return

        // Sentence capitalization for the current utterance
        val fullTargetText = if (needsCapitalStart && trimmed.isNotEmpty()) {
            trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
        } else {
            trimmed
        }

        val current = sessionEmittedText

        // Find longest common prefix between what's in the editor from this utterance and the new target
        var commonPrefixLen = 0
        val minLen = minOf(current.length, fullTargetText.length)
        while (commonPrefixLen < minLen && current[commonPrefixLen] == fullTargetText[commonPrefixLen]) {
            commonPrefixLen++
        }

        val charsToDelete = current.length - commonPrefixLen
        val textToAppend = fullTargetText.substring(commonPrefixLen)

        if (charsToDelete == 0 && textToAppend.isEmpty() && !isFinal) {
            return
        }

        Log.i(TAG, "syncRecognizedText: current=${current.length} chars, target=${fullTargetText.length} chars, commonPrefixLen=$commonPrefixLen, delete=$charsToDelete, append=${textToAppend.length} chars, isFinal=$isFinal")

        ic.beginBatchEdit()
        try {
            if (charsToDelete > 0) {
                ic.deleteSurroundingText(charsToDelete, 0)
            }
            if (textToAppend.isNotEmpty()) {
                ic.commitText(textToAppend, 1)
            }
            if (isFinal && fullTargetText.isNotEmpty()) {
                ic.finishComposingText()
                ic.commitText(" ", 1)
                val lastChar = fullTargetText.lastOrNull()
                needsCapitalStart = lastChar != null && lastChar in ".!?"
                sessionEmittedText = ""
            } else {
                sessionEmittedText = fullTargetText
            }
        } finally {
            ic.endBatchEdit()
        }
    }

    fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        // Delta algorithm tracks emitted text directly
    }

    private fun executeVoiceCommand(action: VoiceTextProcessor.Action, ic: InputConnection) {
        ic.finishComposingText()
        when (action) {
            VoiceTextProcessor.Action.NEW_LINE -> {
                ic.commitText("\n", 1)
            }
            VoiceTextProcessor.Action.NEW_PARAGRAPH -> {
                ic.commitText("\n\n", 1)
            }
            VoiceTextProcessor.Action.DELETE_LAST_WORD -> {
                val before = ic.getTextBeforeCursor(100, 0)?.toString() ?: return
                val trimmed = before.trimEnd()
                val lastSpace = trimmed.lastIndexOf(' ')
                val wordLen = if (lastSpace == -1) trimmed.length else trimmed.length - lastSpace - 1
                val totalDelete = wordLen + (before.length - trimmed.length)
                if (totalDelete > 0) {
                    ic.deleteSurroundingText(totalDelete, 0)
                }
            }
            VoiceTextProcessor.Action.CLEAR_ALL -> {
                ic.beginBatchEdit()
                try {
                    ic.performContextMenuAction(android.R.id.selectAll)
                    ic.commitText("", 1)
                } finally {
                    ic.endBatchEdit()
                }
            }
            VoiceTextProcessor.Action.SEND -> {
                val editorInfo = ims.currentInputEditorInfo
                if (editorInfo != null) {
                    val actionId = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
                    if (actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
                        ic.performEditorAction(actionId)
                    } else {
                        ic.performEditorAction(EditorInfo.IME_ACTION_SEND)
                    }
                }
            }
        }
        Toast.makeText(ims, R.string.voice_command_executed, Toast.LENGTH_SHORT).show()
    }

    private fun clearComposingText() {
        if (sessionEmittedText.isNotEmpty()) {
            val ic = ims.currentInputConnection
            if (ic != null) {
                ic.deleteSurroundingText(sessionEmittedText.length, 0)
            }
            sessionEmittedText = ""
        }
        lastPartialText = null
    }

    private fun cleanupSession() {
        cancelConnectingTimeout()
        cancelHandshakeTimeout()
        cancelProcessingTimeout()
        piecewiseActive = false
        pendingPieces.clear()
        pieceInFlight = null
        onlineTranscriptionJob?.cancel()
        onlineTranscriptionJob = null
        synchronized(onlineAudioBuffer) {
            onlineAudioBuffer.reset()
        }
        isCurrentSessionOnline = false
        isKeepScreenOnLeaseActive = false
        pluginManager.setConnectionListener(null)
        stopAudioLoop()
        closeQuietly(audioPipeReadSide)
        audioPipeReadSide = null
        closeQuietly(audioPipeWriteSide)
        audioPipeWriteSide = null
        activeSessionId = null
        sessionEmittedText = ""
    }

    @Synchronized
    private fun updateState(newState: VoiceState) {
        val oldState = this.state
        if (oldState == newState) {
            Log.d(TAG, "State dedup: $oldState -> $newState (ignored)")
            return
        }
        Log.i(TAG, "State transition: $oldState -> $newState")
        this.state = newState
        // full speed for the plugin only while dictating
        val dictating = newState == VoiceState.RECORDING || newState == VoiceState.PROCESSING_FINAL
        mainHandler.post { pluginManager.setDictationPriority(dictating) }
        mainHandler.post {
            listener?.onStateChanged(newState)
        }
    }

    private fun notifyError(message: String, canRetry: Boolean = false) {
        mainHandler.post {
            listener?.onError(message, canRetry)
        }
    }

    fun release() {
        cancelVoice()
        coroutineScope.cancel()
        pluginManager.release()
    }

    companion object {
        private const val TAG = "VoiceInputManager"
        private val PUNCTUATION_REGEX = Regex("[,.?!;:]")
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE_MS = 30
        private const val FRAME_SIZE_SHORTS = SAMPLE_RATE * FRAME_SIZE_MS / 1000 // 480 shorts
        private const val FRAME_SIZE_BYTES = FRAME_SIZE_SHORTS * 2 // 960 bytes
        private const val HANDSHAKE_TIMEOUT_MS = 8000L
        private const val PROCESSING_TIMEOUT_MS = 15000L

        /**
         * Time allowed for the final recognition: offline recognition on a phone can take longer than the
         * recording itself, so at least 60 s and three times the recording; it only ends a hung session.
         */
        internal fun processingTimeoutFor(recordedMs: Long): Long =
            maxOf(60_000L, PROCESSING_TIMEOUT_MS + 3 * recordedMs.coerceAtLeast(0L))
        private const val DEFAULT_MAX_DURATION_SEC = 30
        internal const val KEEP_SCREEN_ON_SILENCE_GRACE_MS = 60_000L

        /** Resolves configured recording duration (0 = unlimited when chosen explicitly, < 0 or invalid -> default 30s). */
        internal fun resolveMaxDurationSec(rawValue: String?): Int {
            val parsed = rawValue?.toIntOrNull() ?: return DEFAULT_MAX_DURATION_SEC
            return if (parsed < 0) DEFAULT_MAX_DURATION_SEC else parsed
        }

        internal fun shouldKeepRecordingScreenOn(elapsedSinceSpeechMs: Long): Boolean {
            return elapsedSinceSpeechMs < KEEP_SCREEN_ON_SILENCE_GRACE_MS
        }

        /** Keeps screen awake only while voice UI is visible and active (releasing after 60s of silence while recording). */
        internal fun shouldKeepScreenOnForVoice(
            state: VoiceState,
            prefEnabled: Boolean,
            isImeWindowVisible: Boolean,
            isRecordingKeepAliveActive: Boolean
        ): Boolean {
            if (!prefEnabled || !isImeWindowVisible) return false
            return when (state) {
                VoiceState.CONNECTING_PLUGIN,
                VoiceState.STARTING_SESSION,
                VoiceState.PROCESSING_FINAL -> true
                VoiceState.RECORDING -> isRecordingKeepAliveActive
                VoiceState.IDLE,
                VoiceState.ERROR -> false
            }
        }
        private const val BYTES_PER_MS = SAMPLE_RATE * 2 / 1000
        /** silence that ends a piece when transcribing while speaking */
        private const val PIECE_PAUSE_MS = 700L
        /** shorter pieces wait for the next pause: Whisper invents text for very short audio */
        private const val PIECE_MIN_MS = 4000
        /** Whisper works on 30 s windows, cut before even without a pause */
        private const val PIECE_MAX_MS = 25000
        private const val PIECE_CONTEXT_CHARS = 200

        fun isBlockedEditor(info: EditorInfo?): Boolean {
            if (info == null) return false
            return InputTypeUtils.isAnyPasswordInputType(info.inputType)
        }
    }

    private fun closeQuietly(pfd: ParcelFileDescriptor?) {
        try {
            pfd?.close()
        } catch (_: Exception) {}
    }
}
