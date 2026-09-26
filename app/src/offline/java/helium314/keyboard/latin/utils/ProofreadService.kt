/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.ai.OfflineAiLoader
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Offline proofreading service using modular LeanType-Offline-AI-Plugin with GGUF models.
 */
class ProofreadService(private val context: Context) {

    private val sharedPrefs: SharedPreferences by lazy {
        context.prefs()
    }

    fun getPrefs(): SharedPreferences = sharedPrefs
    
    // Singleton holder for model state to prevent reloading on every request
    object ModelHolder {
        var currentModelPath: String? = null
        var isModelAvailable: Boolean = true
        var isModelLoaded: Boolean = false

        // Smart Unload Logic
        private var unloadJob: Job? = null
        private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
        private const val UNLOAD_DELAY_MS = 10 * 60 * 1000L // 10 minutes
        private val loadMutex = Mutex()

        @Synchronized
        fun scheduleUnload(context: Context) {
            unloadJob?.cancel()
            
            val prefs = context.prefs()
            val keepLoaded = prefs.getBoolean(Settings.PREF_OFFLINE_KEEP_MODEL_LOADED, Defaults.PREF_OFFLINE_KEEP_MODEL_LOADED)
            
            if (keepLoaded) {
                 Log.i(TAG, "Model unload skipped (Keep Model Loaded enabled)")
                 return
            }

            unloadJob = scope.launch {
                delay(UNLOAD_DELAY_MS)
                unloadModel(context)
                Log.i(TAG, "Offline AI model unloaded due to inactivity")
            }
        }

        @Synchronized
        fun cancelUnload() {
            unloadJob?.cancel()
            unloadJob = null
        }

        @Synchronized
        fun unloadModel(context: Context? = null) {
            try {
                if (context != null) {
                    OfflineAiLoader.getProvider(context)?.unloadModel()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error unloading llama model", e)
            }
            currentModelPath = null
            isModelLoaded = false
            isModelAvailable = true
        }

        suspend fun loadModel(
            context: Context,
            modelPath: String
        ): Boolean = loadMutex.withLock {
            cancelUnload()

            val provider = OfflineAiLoader.getProvider(context)
            if (provider == null) {
                Log.w(TAG, "Offline AI Plugin not installed/available")
                isModelAvailable = false
                return false
            }

            // Check if already loaded with same path
            if (isModelLoaded && currentModelPath == modelPath && provider.isModelLoaded()) {
                return true
            }

            unloadModel(context) // Ensure clean slate if path changed

            return try {
                val cores = Runtime.getRuntime().availableProcessors()
                val threads = if (cores <= 4) cores else 4
                
                Log.i(TAG, "Loading GGUF model via AI Plugin: path=$modelPath threads=$threads")
                val success = provider.loadModel(context, modelPath, threads, 2048)
                if (success) {
                    currentModelPath = modelPath
                    isModelLoaded = true
                    isModelAvailable = true
                } else {
                    isModelLoaded = false
                    isModelAvailable = false
                }
                success
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load GGUF model via plugin", e)
                isModelAvailable = false
                false
            }
        }

        private const val TAG = "LlamaProofreadService"
    }

    // AI Provider support (API compatibility)
    enum class AIProvider {
        GEMINI, GROQ, OPENAI
    }
    
    fun getProvider(): AIProvider = AIProvider.GROQ
    fun setProvider(provider: AIProvider) { /* No-op */ }

    suspend fun fetchAvailableModels(provider: AIProvider): List<String> = emptyList()
    suspend fun fetchAvailableVoiceModels(provider: AIProvider): List<String> = emptyList()

    // API-compatible methods
    fun getApiKey(): String? = null
    fun setApiKey(apiKey: String?) { /* No-op */ }
    fun hasApiKey(): Boolean = false
    
    // HuggingFace stubs
    fun getHuggingFaceToken(): String? = null
    fun setHuggingFaceToken(token: String?) { /* No-op */ }
    fun getHuggingFaceModel(): String = "Offline Mode"
    fun setHuggingFaceModel(model: String) { /* No-op */ }
    fun getHuggingFaceEndpoint(): String = "Offline Mode"
    fun setHuggingFaceEndpoint(endpoint: String) { /* No-op */ }

    fun getGroqToken(): String? = null
    fun setGroqToken(token: String?) { /* No-op */ }

    fun getGroqModel(): String = "Offline Mode"
    fun setGroqModel(model: String) { /* No-op */ }

    // Model management - single model path (no encoder/decoder split)
    fun getModelPath(): String? = sharedPrefs.getString(KEY_MODEL_PATH, null)
    
    fun setModelPath(path: String?) {
        sharedPrefs.edit().apply {
            if (path.isNullOrBlank()) {
                remove(KEY_MODEL_PATH)
            } else {
                putString(KEY_MODEL_PATH, path)
            }
            apply()
        }
        ModelHolder.unloadModel()
    }

    // Decoder path (kept for API compatibility, not used with llamacpp)
    fun getDecoderPath(): String? = sharedPrefs.getString(KEY_DECODER_PATH, null)
    
    fun setDecoderPath(path: String?) {
        sharedPrefs.edit().apply {
            if (path.isNullOrBlank()) {
                remove(KEY_DECODER_PATH)
            } else {
                putString(KEY_DECODER_PATH, path)
            }
            apply()
        }
    }

    // Tokenizer path (not needed with GGUF - tokenizer is embedded)
    fun getTokenizerPath(): String? = sharedPrefs.getString(KEY_TOKENIZER_PATH, null)
    
    fun setTokenizerPath(path: String?) {
        sharedPrefs.edit().apply {
            if (path.isNullOrBlank()) {
                remove(KEY_TOKENIZER_PATH)
            } else {
                putString(KEY_TOKENIZER_PATH, path)
            }
            apply()
        }
    }

    fun getSystemPrompt(): String = sharedPrefs.getString(Settings.PREF_OFFLINE_SYSTEM_PROMPT, "") ?: ""

    fun setSystemPrompt(prompt: String) {
        sharedPrefs.edit().putString(Settings.PREF_OFFLINE_SYSTEM_PROMPT, prompt).apply()
    }

    fun getTranslateSystemPrompt(): String = sharedPrefs.getString(Settings.PREF_OFFLINE_TRANSLATE_SYSTEM_PROMPT, "") ?: ""

    fun setTranslateSystemPrompt(prompt: String) {
        sharedPrefs.edit().putString(Settings.PREF_OFFLINE_TRANSLATE_SYSTEM_PROMPT, prompt).apply()
    }

    fun getModelName(): String {
        val path = getModelPath()
        if (path.isNullOrBlank()) return "No Model Selected"
        
        if (path.startsWith("content://")) {
            try {
                val uri = Uri.parse(path)
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            return cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve content URI name", e)
            }
        }
        
        return File(path).name.takeIf { it.isNotEmpty() } ?: "Local Model"
    }

    fun setModelName(name: String) { /* No-op */ }
    
    // Source language
    fun getSourceLanguage(): String {
        val lang = sharedPrefs.getString(
            helium314.keyboard.settings.SettingsWithoutKey.TRANSLATION_SOURCE_LANGUAGE,
            sharedPrefs.getString(Settings.PREF_TRANSLATION_SOURCE_LANGUAGE, "auto")
        ) ?: "auto"
        return if (lang.equals("Auto", ignoreCase = true) || lang.equals("Auto Detect", ignoreCase = true)) "auto" else lang
    }

    fun setSourceLanguage(language: String) {
        sharedPrefs.edit()
            .putString(helium314.keyboard.settings.SettingsWithoutKey.TRANSLATION_SOURCE_LANGUAGE, language)
            .putString(Settings.PREF_TRANSLATION_SOURCE_LANGUAGE, language)
            .apply()
    }

    fun getTargetLanguage(): String {
        val lang = sharedPrefs.getString(
            helium314.keyboard.settings.SettingsWithoutKey.GEMINI_TARGET_LANGUAGE,
            sharedPrefs.getString(Settings.PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE, "keyboard")
        ) ?: "keyboard"
        return if (lang.equals("English", ignoreCase = true)) "en" else lang
    }

    fun setTargetLanguage(language: String) {
        sharedPrefs.edit()
            .putString(helium314.keyboard.settings.SettingsWithoutKey.GEMINI_TARGET_LANGUAGE, language)
            .putString(Settings.PREF_OFFLINE_TRANSLATE_TARGET_LANGUAGE, language)
            .apply()
    }

    fun getResolvedTargetLanguage(): String {
        val lang = getTargetLanguage()
        if (lang.equals("keyboard", ignoreCase = true) || lang.equals("default", ignoreCase = true)) {
            try {
                val currentSubtype = RichInputMethodManager.getInstance().currentSubtype
                val keyboardLang = currentSubtype.locale.language
                if (keyboardLang.isNotBlank() && keyboardLang != "zz") {
                    return keyboardLang.lowercase()
                }
            } catch (_: Throwable) {}
            return "en"
        }
        return lang
    }

    fun getTranslateModelName(): String = ""
    fun setTranslateModelName(modelName: String) { /* No-op */ }

    fun getTranslateHuggingFaceModel(): String = ""
    fun setTranslateHuggingFaceModel(modelName: String) { /* No-op */ }

    fun getTranslateGroqModel(): String = ""
    fun setTranslateGroqModel(modelName: String) { /* No-op */ }

    fun getVoiceGeminiModel(): String = ""
    fun setVoiceGeminiModel(modelName: String) { /* No-op */ }

    fun getVoiceGroqModel(): String = ""
    fun setVoiceGroqModel(modelName: String) { /* No-op */ }

    fun getVoiceHuggingFaceModel(): String = ""
    fun setVoiceHuggingFaceModel(modelName: String) { /* No-op */ }

    suspend fun transcribeAudio(audioBytes: ByteArray, language: String? = null): Result<String> =
        Result.failure(Exception("Online voice transcription is not supported in offline flavor"))

    fun unloadModel() {
        ModelHolder.unloadModel()
    }

    /**
     * Run llamacpp inference for translation.
     */
    suspend fun translate(text: String): Result<String> {
        val target = getResolvedTargetLanguage()
        val systemPromptTemplate = getTranslateSystemPrompt().takeIf { it.isNotBlank() } ?: Defaults.PREF_OFFLINE_TRANSLATE_SYSTEM_PROMPT
        val prompt = systemPromptTemplate.replace("{lang}", target)
        return proofread(text, overridePrompt = prompt, targetLanguage = target)
    }

    /**
     * Run llamacpp inference for proofreading/text correction.
     */
    suspend fun proofread(
        text: String,
        overridePrompt: String? = null,
        showThinking: Boolean? = null,
        targetLanguage: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val modelPath = getModelPath()
        if (modelPath.isNullOrBlank()) {
            return@withContext Result.failure(ProofreadException("Model not loaded. Please select a GGUF model file."))
        }

        val provider = OfflineAiLoader.getProvider(context)
            ?: return@withContext Result.failure(ProofreadException("Offline AI plugin not installed. Please install the plugin in Settings."))

        // Load model (or get cached)
        if (!ModelHolder.loadModel(context, modelPath)) {
             Log.e(TAG, "Model load failed")
             return@withContext Result.failure(ProofreadException("Failed to load model in AI plugin."))
        }

        // Cancel unload timer while working
        ModelHolder.cancelUnload()

        try {
            val maxTokens = sharedPrefs.getInt(Settings.PREF_OFFLINE_MAX_TOKENS, Defaults.PREF_OFFLINE_MAX_TOKENS)
            val temp = sharedPrefs.getFloat(Settings.PREF_OFFLINE_TEMP, Defaults.PREF_OFFLINE_TEMP)
            val topP = sharedPrefs.getFloat(Settings.PREF_OFFLINE_TOP_P, Defaults.PREF_OFFLINE_TOP_P)
            val topK = sharedPrefs.getInt(Settings.PREF_OFFLINE_TOP_K, Defaults.PREF_OFFLINE_TOP_K)
            val minP = sharedPrefs.getFloat(Settings.PREF_OFFLINE_MIN_P, Defaults.PREF_OFFLINE_MIN_P)
            val showThinkingVal = showThinking ?: sharedPrefs.getBoolean(Settings.PREF_OFFLINE_SHOW_THINKING, Defaults.PREF_OFFLINE_SHOW_THINKING)
            
            // Build the prompt
            val systemPrompt = overridePrompt ?: getSystemPrompt().ifBlank { Defaults.PREF_OFFLINE_SYSTEM_PROMPT }
            val fullPrompt = if (systemPrompt.contains("{text}")) {
                systemPrompt.replace("{text}", text)
            } else if (overridePrompt != null) {
                // Translation or specific override
                val examples = targetLanguage?.let { getTranslationFewShot(it) } ?: emptyList()
                if (examples.isNotEmpty()) {
                    var builder = "Instruction: ${systemPrompt.trim()}\n\n"
                    for (ex in examples) {
                        builder += "Input: ${ex.first}\nOutput: ${ex.second}\n\n"
                    }
                    builder += "Input: $text\nOutput:"
                    builder
                } else {
                    "Instruction: ${systemPrompt.trim()}\n\nInput: $text\nOutput:"
                }
            } else {
                // Default proofreading with few-shot examples for better local model guidance
                val instruction = systemPrompt.ifBlank { "Correct the grammar and spelling of the input text. Keep the SAME language as the input. Do NOT translate. Output only the corrected text, nothing else." }
                val currentLocale = try {
                    RichInputMethodManager.getInstance().currentSubtype.locale.toString()
                } catch (_: Exception) { "" }
                val localExamples = getProofreadFewShot(currentLocale)
                val builder = StringBuilder("Instruction: ${instruction.trim()}\n\n")
                if (localExamples.isEmpty() || currentLocale.lowercase().startsWith("en")) {
                    builder.append("Input: heko hw r u\nOutput: Hello, how are you?\n\n")
                }
                for (ex in localExamples) {
                    builder.append("Input: ${ex.first}\nOutput: ${ex.second}\n\n")
                }
                builder.append("Input: $text\nOutput:")
                builder.toString()
            }
            
            // Use provider to generate completion
            val output = provider.generate(fullPrompt, mapOf(
                "temperature" to temp.toDouble(),
                "top_p" to topP.toDouble(),
                "top_k" to topK,
                "min_p" to minP.toDouble(),
                "max_tokens" to maxTokens
            ))

            // Schedule unload after work is done
            ModelHolder.scheduleUnload(context)

            // Robust cleaning of the generated output
            var cleanedOutput = output
            if (cleanedOutput.startsWith(fullPrompt, ignoreCase = true)) {
                cleanedOutput = cleanedOutput.substring(fullPrompt.length).trim()
            } else if (systemPrompt.isNotBlank() && cleanedOutput.startsWith(systemPrompt, ignoreCase = true)) {
                cleanedOutput = cleanedOutput.substring(systemPrompt.length).trim()
                if (cleanedOutput.startsWith(text, ignoreCase = true)) {
                    cleanedOutput = cleanedOutput.substring(text.length).trim()
                }
            }

            // Post-process to strip thinking/reasoning tags first if showThinkingVal is false
            if (!showThinkingVal) {
                cleanedOutput = stripThinkingTags(cleanedOutput)
            }

            // Clean output according to operation type (Translation vs Proofreading)
            val finalOutput = if (targetLanguage != null) {
                cleanTranslationOutput(cleanedOutput)
            } else {
                cleanProofreadOutput(text, cleanedOutput)
            }

            Log.i(TAG, "inference via plugin: input='$text' generated='$output' final='$finalOutput'")
            if (finalOutput.isNotBlank()) {
                Result.success(finalOutput)
            } else {
                Result.failure(ProofreadException("Model produced no output"))
            }

        } catch (e: Throwable) {
            Log.e(TAG, "Proofread failed", e)
            ModelHolder.scheduleUnload(context) // Ensure we still schedule unload on error
            Result.failure(ProofreadException(e.message ?: "Unknown error"))
        }
    }

    private fun stripThinkingTags(text: String): String {
        return text
            .replace(Regex("<think>[\\s\\S]*?(?:</think>|$)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<thinking>[\\s\\S]*?(?:</thinking>|$)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<thought>[\\s\\S]*?(?:</thought>|$)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<reasoning>[\\s\\S]*?(?:</reasoning>|$)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<details>[\\s\\S]*?(?:</details>|$)", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun cleanTranslationOutput(text: String): String {
        var cleaned = text.trim()

        // 1. Cut off reasoning / explanation sections at the end
        val reasoningHeaders = listOf(
            "\nReasoning", "\n\nReasoning",
            "\nExplanation", "\n\nExplanation",
            "\nNotes:", "\n\nNotes:",
            "\nNote:", "\n\nNote:",
            "\nJustification:", "\n\nJustification:",
            "\n- The original", "\n\n- The original",
            "\n* The original", "\n\n* The original"
        )
        for (header in reasoningHeaders) {
            val index = cleaned.indexOf(header, ignoreCase = true)
            if (index > 0) {
                cleaned = cleaned.substring(0, index).trim()
            }
        }

        // 2. Truncate at repetition of prompt template markers (subsequent turns)
        val turnMarkers = listOf("\nInput:", "\nInstruction:", "\nUser:", "\nHuman:", "\nPrompt:")
        for (marker in turnMarkers) {
            val idx = cleaned.indexOf(marker, ignoreCase = true)
            if (idx > 0) {
                cleaned = cleaned.substring(0, idx).trim()
            }
        }

        // 3. Strip conversational preambles and section prefixes (e.g., "Translation:", "Output:")
        val prefixRegex = Regex(
            "^(?i)(?:sure[,!.]?\\s*(?:here(?:'s|\\s+is)\\s+(?:the\\s+)?(?:translated\\s+text|translation)[^:\n]*:?)?|" +
            "(?:here(?:'s|\\s+is)\\s+(?:the\\s+)?(?:translated\\s+text|translation)[^:\n]*:?)|" +
            "(?:translated\\s+text|translation|output|result|response)[^:\n]*:?|" +
            "text\\s+to\\s+translate:?)\\s*",
            RegexOption.MULTILINE
        )
        cleaned = cleaned.replace(prefixRegex, "").trim()

        // 4. Remove markdown code fences if wrapped in ```...```
        if (cleaned.startsWith("```") && cleaned.endsWith("```") && cleaned.length >= 6) {
            val lines = cleaned.lines()
            if (lines.size >= 2) {
                cleaned = lines.subList(1, lines.size - 1).joinToString("\n").trim()
            }
        }

        // 5. Remove outer quotes if wrapped in quotes
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            if (cleaned.length >= 2) {
                cleaned = cleaned.substring(1, cleaned.length - 1).trim()
            }
        }

        return cleaned
    }

    private fun cleanProofreadOutput(originalText: String, text: String): String {
        var cleaned = text.trim()

        // 1. Truncate at repetition of prompt template markers (subsequent turns)
        val turnMarkers = listOf("\nInput:", "\nInstruction:", "\nUser:", "\nHuman:", "\nPrompt:")
        for (marker in turnMarkers) {
            val idx = cleaned.indexOf(marker, ignoreCase = true)
            if (idx > 0) {
                cleaned = cleaned.substring(0, idx).trim()
            }
        }

        // 2. Cut off reasoning / explanation sections at the end
        val reasoningHeaders = listOf(
            "\nReasoning", "\n\nReasoning",
            "\nExplanation", "\n\nExplanation",
            "\nNotes:", "\n\nNotes:",
            "\nNote:", "\n\nNote:",
            "\nJustification:"
        )
        for (header in reasoningHeaders) {
            val index = cleaned.indexOf(header, ignoreCase = true)
            if (index > 0) {
                cleaned = cleaned.substring(0, index).trim()
            }
        }

        // 3. Strip common output prefixes
        val prefixesToStrip = listOf(
            "Output:", "Corrected:", "Correction:", "Response:", "Result:",
            "Output: ", "Corrected: ", "Correction: ", "Response: ", "Result: "
        )
        for (prefix in prefixesToStrip) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length).trim()
                break
            }
        }

        // 4. Remove markdown code fences if wrapped in ```...```
        if (cleaned.startsWith("```") && cleaned.endsWith("```") && cleaned.length >= 6) {
            val lines = cleaned.lines()
            if (lines.size >= 2) {
                cleaned = lines.subList(1, lines.size - 1).joinToString("\n").trim()
            }
        }

        // 5. If wrapped in quotes, strip them
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            if (cleaned.length >= 2) {
                cleaned = cleaned.substring(1, cleaned.length - 1).trim()
            }
        }

        return cleaned
    }

    private fun getTranslationFewShot(targetLanguage: String): List<Pair<String, String>> {
        val lang = targetLanguage.trim().lowercase()
        return when {
            lang.contains("french") || lang.contains("français") -> listOf(
                "Hello, how are you?" to "Bonjour, comment allez-vous?",
                "My name is Alex." to "Je m'appelle Alex."
            )
            lang.contains("spanish") || lang.contains("español") -> listOf(
                "Hello, how are you?" to "Hola, ¿cómo estás?",
                "My name is Alex." to "Mi nombre es Alex."
            )
            lang.contains("german") || lang.contains("deutsch") -> listOf(
                "Hello, how are you?" to "Hallo, wie geht es dir?",
                "My name is Alex." to "Mein Name ist Alex."
            )
            lang.contains("italian") || lang.contains("italiano") -> listOf(
                "Hello, how are you?" to "Ciao, come stai?",
                "My name is Alex." to "Il mio nome è Alex."
            )
            lang.contains("portuguese") || lang.contains("português") -> listOf(
                "Hello, how are you?" to "Olá, como você está?",
                "My name is Alex." to "Meu nome é Alex."
            )
            lang.contains("dutch") || lang.contains("nederlands") -> listOf(
                "Hello, how are you?" to "Hallo, hoe gaat het met je?",
                "My name is Alex." to "Mijn naam is Alex."
            )
            lang.contains("russian") || lang.contains("русский") -> listOf(
                "Hello, how are you?" to "Привет, как дела?",
                "My name is Alex." to "Меня зовут Алекс."
            )
            lang.contains("chinese") || lang.contains("中文") || lang.contains("汉语") -> listOf(
                "Hello, how are you?" to "你好，你好吗？",
                "My name is Alex." to "我的名字是亚历克斯。"
            )
            lang.contains("japanese") || lang.contains("日本語") -> listOf(
                "Hello, how are you?" to "こんにちは、お元気ですか？",
                "My name is Alex." to "私の名前はアレックスです。"
            )
            lang.contains("hindi") || lang.contains("हिन्दी") -> listOf(
                "Hello, how are you?" to "नमस्ते, आप कैसे हैं?",
                "My name is Alex." to "मेरा नाम एलेक्स है।"
            )
            else -> emptyList()
        }
    }

    private fun getProofreadFewShot(languageTag: String): List<Pair<String, String>> {
        val lang = languageTag.lowercase()
        return when {
            lang.startsWith("en") -> emptyList() // English example already included
            lang.startsWith("fr") -> listOf(
                "je sui content de te voire" to "Je suis content de te voir."
            )
            lang.startsWith("es") -> listOf(
                "hola como estas tu vien" to "Hola, ¿cómo estás? Bien."
            )
            lang.startsWith("de") -> listOf(
                "ich habe ein grose Haus" to "Ich habe ein großes Haus."
            )
            lang.startsWith("it") -> listOf(
                "io sono molto contento di vederte" to "Io sono molto contento di vederti."
            )
            lang.startsWith("pt") -> listOf(
                "eu estou muito felis hoje" to "Eu estou muito feliz hoje."
            )
            lang.startsWith("nl") -> listOf(
                "ik ben heel blei om je te zien" to "Ik ben heel blij om je te zien."
            )
            lang.startsWith("ru") -> listOf(
                "привет как дила у тебя" to "Привет, как дела у тебя?"
            )
            lang.startsWith("tr") -> listOf(
                "ben bugün çok mutluyım" to "Ben bugün çok mutluyum."
            )
            lang.startsWith("pl") -> listOf(
                "jestem bardzo szczesliwy dzisiaj" to "Jestem bardzo szczęśliwy dzisiaj."
            )
            lang.startsWith("hi") -> listOf(
                "मैं बहुत खुस हूं आज" to "मैं बहुत खुश हूं आज।"
            )
            lang.startsWith("ar") -> listOf(
                "انا سعيد جدا اليوم" to "أنا سعيد جداً اليوم."
            )
            lang.startsWith("ja") -> listOf(
                "きょう は とても いい てんき です" to "今日はとてもいい天気です。"
            )
            lang.startsWith("zh") -> listOf(
                "我今天很高心" to "我今天很高兴。"
            )
            lang.startsWith("ko") -> listOf(
                "오늘 날씨가 너무 조아요" to "오늘 날씨가 너무 좋아요."
            )
            else -> emptyList()
        }
    }

    class ProofreadException(message: String) : Exception(message)
    class TranslateException(message: String) : Exception(message)

    companion object {
        private const val TAG = "LlamaProofreadService"
        private const val KEY_MODEL_PATH = "offline_model_path"
        private const val KEY_DECODER_PATH = "offline_decoder_path"
        private const val KEY_TOKENIZER_PATH = "offline_tokenizer_path"
        val AVAILABLE_MODELS = listOf("GGUF Model (Local)")
        const val DEFAULT_VOICE_GEMINI_MODEL = "gemini-2.0-flash"
        const val DEFAULT_VOICE_HF_MODEL = "whisper-1"
    }
}
