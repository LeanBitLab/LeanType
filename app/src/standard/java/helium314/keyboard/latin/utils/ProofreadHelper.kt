/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputConnection
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.settings.SettingsWithoutKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Helper class to handle proofreading async operations from Java code.
 * This avoids the complexity of Java-Kotlin coroutine interop.
 */
object ProofreadHelper {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Track current operation for cancellation
    private var currentJob: Job? = null
    
    // Check if an operation is in progress
    val isOperationInProgress: Boolean
        get() = currentJob?.isActive == true
    
    // Store original text for potential undo
    var lastOriginalText: String? = null
        private set
    
    /**
     * Preload the model in the background to avoid initial latency.
     */
    fun preloadModel(context: Context) {
        // No-op for standard flavor (runs API based proofreader)
    }

    /**
     * Cancel the current proofreading/translation operation if one is in progress.
     */
    fun cancelCurrentOperation() {
        if (currentJob?.isActive == true) {
            currentJob?.cancel()
            currentJob = null
            mainHandler.post {
                KeyboardSwitcher.getInstance().hideLoadingAnimation()
                // Toast removed as visual feedback (stopping animation) is sufficient
            }
        }
    }
    
    private fun performAsyncOperation(
        context: Context,
        text: String,
        noTextErrorResId: Int,
        errorResId: Int,
        apiCall: suspend (ProofreadService) -> Result<String>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
        allowEmptyInput: Boolean = false,
        skipApiKeyCheck: Boolean = false
    ) {
        val service = ProofreadService(context)

        // Check if API key/token is configured based on provider (unless plugin handles operation)
        if (!skipApiKeyCheck) {
            val provider = service.getProvider()
            when (provider) {
                ProofreadService.AIProvider.GEMINI -> {
                    if (!service.hasApiKey()) {
                        mainHandler.post {
                            KeyboardSwitcher.getInstance().showToast(
                                context.getString(R.string.proofread_no_api_key),
                                true
                            )
                        }
                        return
                    }
                }
                ProofreadService.AIProvider.GROQ -> {
                    if (service.getGroqToken() == null) {
                        mainHandler.post {
                            KeyboardSwitcher.getInstance().showToast(
                                context.getString(R.string.huggingface_no_token),
                                true
                            )
                        }
                        return
                    }
                }
                ProofreadService.AIProvider.OPENAI -> {
                    if (service.getHuggingFaceToken() == null) {
                        mainHandler.post {
                            KeyboardSwitcher.getInstance().showToast(
                                context.getString(R.string.huggingface_no_token),
                                true
                            )
                        }
                        return
                    }
                }
            }
        }

        if (!allowEmptyInput && text.isBlank()) {
            mainHandler.post {
                KeyboardSwitcher.getInstance().showToast(
                    context.getString(noTextErrorResId),
                    true
                )
            }
            return
        }

        // Store original text for undo
        lastOriginalText = text

        // Show loading animation on suggestion strip
        mainHandler.post {
            KeyboardSwitcher.getInstance().showLoadingAnimation()
        }

        // Launch coroutine for API call and track it for cancellation
        currentJob = scope.launch {
            val result = apiCall(service)

            mainHandler.post {
                currentJob = null
                // Hide loading animation
                KeyboardSwitcher.getInstance().hideLoadingAnimation()

                result.fold(
                    onSuccess = { resultText ->
                        onSuccess(resultText)
                    },
                    onFailure = { error ->
                        onError(error.message ?: "Unknown error")
                        KeyboardSwitcher.getInstance().showToast(
                            context.getString(errorResId, error.message ?: "Unknown error"),
                            false
                        )
                    }
                )
            }
        }
    }

    /**
     * Proofread text asynchronously and call the callback with the result.
     * 
     * @param context Application context
     * @param text Text to proofread
     * @param hasSelection Whether text was selected (false = entire field)
     * @param onSuccess Callback with proofread text
     * @param onError Callback with error message
     */
    fun proofreadAsync(
        context: Context,
        text: String,
        hasSelection: Boolean,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        performAsyncOperation(
            context = context,
            text = text,
            noTextErrorResId = R.string.proofread_no_text,
            errorResId = R.string.proofread_error,
            apiCall = { service -> service.proofread(text) },
            onSuccess = onSuccess,
            onError = onError
        )
    }
    
    /**
     * Simple Java-friendly interface for proofreading.
     */
    interface ProofreadCallback {
        fun onSuccess(proofreadText: String)
        fun onError(errorMessage: String)
    }
    
    /**
     * Java-friendly version using callback interface.
     */
    fun proofreadAsync(
        context: Context,
        text: String,
        hasSelection: Boolean,
        callback: ProofreadCallback
    ) {
        proofreadAsync(
            context = context,
            text = text,
            hasSelection = hasSelection,
            onSuccess = { callback.onSuccess(it) },
            onError = { callback.onError(it) }
        )
    }

    private fun getLangCode(targetLang: String): String {
        val trimmed = targetLang.trim()
        if (trimmed.isEmpty()) return "en"
        if (trimmed.length in 2..3 && trimmed.all { it.isLetter() }) return trimmed.lowercase()
        if (trimmed.contains("-") || trimmed.contains("_")) {
            val prefix = trimmed.split('-', '_')[0].trim().lowercase()
            if (prefix.length in 2..3 && prefix.all { it.isLetter() }) return prefix
        }
        val lower = trimmed.lowercase()
        return when (lower) {
            "english", "anglais", "englisch", "inglés", "inglese", "inglês", "английский", "انگریزی", "الإنجليزية", "ഇംഗ്ലീഷ്", "αγγλικά", "İngilizce" -> "en"
            "spanish", "espagnol", "spanisch", "español", "spagnolo", "espanhol", "испанский", "ہسپانوی", "الإسبانية", "സ്പാനിഷ്", "ισπανικά", "İspanyolca" -> "es"
            "french", "français", "französisch", "francés", "francese", "francês", "французский", "فرانسیسی", "الفرنسية", "ഫ്രഞ്ച്", "γαλλικά", "Fransızca" -> "fr"
            "german", "allemand", "deutsch", "alemán", "tedesco", "alemão", "немецкий", "جرمن", "الألمانية", "ജർമ്മൻ", "γερμανικά", "Almanca" -> "de"
            "italian", "italien", "italienisch", "italiano", "итальянский", "اطالوی", "الإيطالية", "ഇറ്റാലിയൻ", "ιταλικά", "İtalyanca" -> "it"
            "portuguese", "portugais", "portugiesisch", "portugués", "portoghese", "português", "португальский", "پرتگالی", "البرتغالية", "പോർച്ചുഗീസ്", "πορτογαλικά", "Portekizce" -> "pt"
            "chinese", "chinese (simplified)", "chinese (traditional)", "chinois", "chinois (simplifié)", "chinois (traditionnel)", "chinesisch", "chino", "cinese", "chinês", "китайский", "چینی", "الصينية", "ചൈനീസ്", "κινεζικά", "Çince" -> "zh"
            "japanese", "japonais", "japanisch", "japonés", "giapponese", "japonês", "японский", "جاپانی", "اليابانية", "ജാപ്പനീസ്", "ιαπωνικά", "Japonca" -> "ja"
            "korean", "coréen", "koreanisch", "coreano", "корейский", "کوریائی", "الكورية", "കൊറിയൻ", "κορεατικά", "Korece" -> "ko"
            "arabic", "arabe", "arabisch", "árabe", "arabo", "арабский", "عربی", "العربية", "അറബിക്", "αραβικά", "Arapça" -> "ar"
            "russian", "russe", "russisch", "ruso", "russo", "русский", "روسی", "الروسية", "റഷ്യൻ", "ρωσικά", "Rusça" -> "ru"
            "hindi", "indien", "индийский", "хинди", "ہندی", "الهندية", "ഹിന്ദി", "χίντι", "Hintçe" -> "hi"
            "bengali", "bengalí", "бенгальский", "بنگالی", "البنغالية", "ബംഗാളി", "μπενγκάλι", "Bengalce" -> "bn"
            "indonesian", "indonésien", "indonesisch", "indonesio", "indonesiano", "индонезийский", "انڈونیشیائی", "الإندونيسية", "ഇന്തോനേഷ്യൻ", "ινδονησιακά", "Endonezce" -> "id"
            "dutch", "néerlandais", "niederländisch", "holandés", "olandese", "holandês", "нидерландский", "голландский", "ولندیزی", "الهولندية", "ഡച്ച്", "ολλανδικά", "Felemenkçe" -> "nl"
            "turkish", "turc", "türkisch", "turco", "турецкий", "ترکی", "التركية", "ടർക്കിഷ്", "τουρκικά", "Türkçe" -> "tr"
            "polish", "polonais", "polnisch", "polaco", "polacco", "польский", "پولش", "البولندية", "പോളിഷ്", "πολωνικά", "Lehçe" -> "pl"
            "ukrainian", "ukrainien", "ukrainisch", "ucraniano", "ucraino", "украинский", "یوکرائنی", "الأوكرانية", "ഉക്രേനിയൻ", "ουκρανικά", "Ukraynaca" -> "uk"
            "swedish", "suédois", "schwedisch", "sueco", "svedese", "шведский", "سویڈش", "السويدية", "സ്വീഡിഷ്", "σουηδικά", "İsveççe" -> "sv"
            "danish", "danois", "dänisch", "danés", "danese", "dinamarquês", "датский", "ڈینش", "الدنماركية", "ഡാനിഷ്", "δανικά", "Danca" -> "da"
            "norwegian", "norvégien", "norwegisch", "noruego", "norvegese", "norueguês", "норвежский", "نارویجن", "النرويجية", "നോർവീജിയൻ", "νορβηγικά", "Norveççe" -> "no"
            "finnish", "finnois", "finnisch", "finlandés", "finlandese", "finlandês", "финский", "فنش", "الفنلندية", "ഫിന്നിഷ്", "φινλανδικά", "Fince" -> "fi"
            "greek", "grec", "griechisch", "griego", "greco", "grego", "греческий", "یونانی", "اليونانية", "ഗ്രീക്ക്", "ελληνικά", "Yunanca" -> "el"
            "hebrew", "hébreu", "hebräisch", "hebreo", "ebraico", "hebraico", "иврит", "عبرانی", "العبرية", "ഹീബ്രു", "εβραϊκά", "İbranice" -> "he"
            "thai", "thaï", "thailändisch", "tailandés", "thailandese", "tailandês", "тайский", "تھائی", "التايلاندية", "തായ്", "ταϊλανδικά", "Tayca" -> "th"
            "vietnamese", "vietnamien", "vietnamesisch", "vietnamita", "вьетнамский", "ویتنامی", "الفيتنامية", "വിയറ്റ്നാമീസ്", "βιετναμέζικα", "Vietnamca" -> "vi"
            "tamil", "tamoul", "тамильский", "تامل", "التاميلية", "തമിഴ്", "ταμίλ", "Tamilce" -> "ta"
            "telugu", "télougou", "телугу", "تیلگو", "التيلوغوية", "തെലുങ്ക്", "τελούγκου", "Teluguca" -> "te"
            "marathi", "marathe", "маратхи", "مراٹھی", "الماراثية", "മറാത്തി", "μαράθι", "Marathice" -> "mr"
            "gujarati", "goudjarati", "гуджарати", "گجراتی", "الغوجاراتية", "ഗുജറാത്തി", "γκουτζαράτι", "Guceratça" -> "gu"
            "kannada", "каннада", "کنڑ", "الكانادية", "കന്നഡ", "κανάντα", "Kannadaca" -> "kn"
            "malayalam", "малаялам", "ملیالم", "المالايالامية", "മലയാളം", "μαλαγιαλάμ", "Malayalamca" -> "ml"
            "urdu", "ourdou", "урду", "اردو", "الأردية", "ഉർദു", "ούρντου", "Urduca" -> "ur"
            "persian (farsi)", "persian", "farsi", "persan (farsi)", "persan", "персидский", "فارسی", "الفارسية", "പേർഷ്യൻ", "περσικά", "Farsça" -> "fa"
            "swahili", "souahéli", "суахили", "سواحلی", "السواحيلية", "സ്വാഹിലി", "σουαχίλι", "Svahilice" -> "sw"
            "romanian", "roumain", "rumänisch", "rumano", "rumeno", "romeno", "румынский", "رومانیہ", "الرومانية", "റൊമാനിയൻ", "ρουμανικά", "Romence" -> "ro"
            "czech", "tchèque", "tschechisch", "checo", "ceco", "чешский", "چیک", "التشيكية", "ചെക്ക്", "τσέχικα", "Çekçe" -> "cs"
            "hungarian", "hongrois", "ungarisch", "húngaro", "ungherese", "венгерский", "ہنگری", "المجرية", "ഹംഗേറിയൻ", "ουγγρικά", "Macarca" -> "hu"
            "filipino (tagalog)", "tagalog", "filipino", "philippin (tagalog)", "тагальский", "فلپائنی", "الفلبينية", "ഫിലിപ്പിനോ", "φιλιππινέζικα", "Filipince" -> "tl"
            "malay", "malais", "malaiisch", "malayo", "malese", "малайский", "ملائی", "الملايوية", "മലായ്", "μαλαισιανά", "Malayca" -> "ms"
            "serbian", "serbe", "serbisch", "serbio", "сербский", "سربین", "الصربية", "സെർബിയൻ", "σερβικά", "Sırpça" -> "sr"
            "croatian", "croate", "kroatisch", "croata", "хорватский", "کروشین", "الكرواتية", "ക്രൊയേഷ്യൻ", "κροατικά", "Hırvatça" -> "hr"
            "bulgarian", "bulgare", "bulgarisch", "búlgaro", "болгарский", "بلغاریائی", "البلغارية", "ബൾഗേറിയൻ", "βουλγαρικά", "Bulgarca" -> "bg"
            "slovak", "slovaque", "slowakisch", "eslovaco", "словацкий", "سلوواک", "السلوفاكية", "സ്ലോവാക്", "σλοβακικά", "Slovakça" -> "sk"
            "slovenian", "slovène", "slowenisch", "esloveno", "словенский", "سلووین", "السلوفينية", "സ്ലൊവേനിയൻ", "σλοβενικά", "Slovence" -> "sl"
            "lithuanian", "lituanien", "litauisch", "lituano", "литовский", "لتھواینین", "الليتوانية", "ലിത്വാനിയൻ", "λιθουανικά", "Litvanca" -> "lt"
            "latvian", "letton", "lettisch", "letón", "латышский", "لاطویائی", "اللاتفية", "ലാത്വിയൻ", "λετονικά", "Letonca" -> "lv"
            "estonian", "estonien", "estnisch", "estonio", "эстонский", "اسٹونین", "الإستونية", "എസ്റ്റോണിയൻ", "εσθονικά", "Estonca" -> "et"
            "catalan", "catalán", "katalanisch", "каталанский", "کیٹالان", "الكتالانية", "കറ്റാലൻ", "καταλανικά", "Katalanca" -> "ca"
            "basque", "baskisch", "vasco", "euskera", "баскский", "باسکی", "الباسكية", "ബാസ്ക്", "βασκικά", "Baskça" -> "eu"
            "afrikaans" -> "af"
            "albanian", "albanais", "albanisch", "albanés", "албанский" -> "sq"
            "belarusian", "biélorusse", "belarussisch", "bielorruso", "белорусский" -> "be"
            "esperanto" -> "eo"
            "galician", "galicien", "galizisch", "gallego", "галисийский" -> "gl"
            "georgian", "géorgien", "georgisch", "georgiano", "грузинский" -> "ka"
            "haitian creole", "haitian", "haïtien" -> "ht"
            "icelandic", "islandais", "isländisch", "islandés", "исландский" -> "is"
            "irish", "irlandais", "irisch", "irlandés", "ирландский" -> "ga"
            "macedonian", "macédonien", "mazedonisch", "macedonio", "македонский" -> "mk"
            "maltese", "maltais", "maltesisch", "maltés", "мальтийский" -> "mt"
            "welsh", "gallois", "walisisch", "galés", "валлийский" -> "cy"
            else -> {
                try {
                    val matched = java.util.Locale.getAvailableLocales().firstOrNull {
                        it.getDisplayLanguage(it).equals(lower, ignoreCase = true) ||
                        it.getDisplayLanguage(java.util.Locale.ENGLISH).equals(lower, ignoreCase = true) ||
                        it.getDisplayLanguage(java.util.Locale.getDefault()).equals(lower, ignoreCase = true)
                    }
                    if (matched != null && matched.language.isNotBlank()) {
                        matched.language.lowercase()
                    } else {
                        val parsed = java.util.Locale.forLanguageTag(lower).language
                        if (parsed.isNotBlank() && parsed.length in 2..3) parsed.lowercase() else "en"
                    }
                } catch (_: Throwable) {
                    "en"
                }
            }
        }
    }

    private fun detectSourceLanguage(text: String): String {
        for (cp in text.codePoints()) {
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_TAMIL)) return "ta"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_MALAYALAM)) return "ml"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_TELUGU)) return "te"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_KANNADA)) return "kn"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_GUJARATI)) return "gu"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_BENGALI)) return "bn"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_DEVANAGARI)) return "hi"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_ARABIC)) return "ar"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_GREEK)) return "el"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_HEBREW)) return "he"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_HANGUL)) return "ko"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_THAI)) return "th"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_GEORGIAN)) return "ka"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_ARMENIAN)) return "hy"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_SINHALA)) return "si"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_MYANMAR)) return "my"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_KHMER)) return "km"
            if (ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_LAO)) return "lo"
        }
        try {
            val currentSubtype = RichInputMethodManager.getInstance().currentSubtype
            val lang = currentSubtype.locale.language
            if (lang.isNotBlank() && lang != "zz") {
                return lang.lowercase()
            }
        } catch (_: Throwable) {}
        return "auto"
    }

    private fun getLanguageDisplayName(context: Context, code: String): String {
        val names = context.resources.getStringArray(R.array.translate_language_names)
        val codes = context.resources.getStringArray(R.array.translate_language_codes)
        val index = codes.indexOfFirst { it.equals(code, ignoreCase = true) }
        if (index != -1 && index < names.size) {
            return names[index]
        }
        val localeName = java.util.Locale(code).getDisplayLanguage(java.util.Locale.ENGLISH)
        return if (localeName.isNotBlank()) localeName else code.uppercase()
    }

    /**
     * Translate text asynchronously and call the callback with the result.
     * 
     * @param context Application context
     * @param text Text to translate
     * @param hasSelection Whether text was selected (true) or extracted (false)
     * @param onSuccess Callback for successful translation
     * @param onError Callback for error
     */
    fun translateAsync(
        context: Context,
        text: String,
        hasSelection: Boolean,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val prefs = context.prefs()
        val translationEngine = prefs.getString("pref_translation_engine", prefs.getString("pref_translation_method", "plugin") ?: "plugin") ?: "plugin"
        val isOfflineOnly = translationEngine == "plugin"
        val isOnlineOnly = translationEngine == "ai"

        val hasPlugin = helium314.keyboard.latin.translation.TranslationLoader.hasPlugin(context)
        val usePlugin = !isOnlineOnly && hasPlugin

        performAsyncOperation(
            context = context,
            text = text,
            noTextErrorResId = R.string.translate_no_text,
            errorResId = R.string.translate_error,
            skipApiKeyCheck = usePlugin || isOfflineOnly,
            apiCall = { service ->
                val pluginProvider = if (usePlugin) helium314.keyboard.latin.translation.TranslationLoader.getProvider(context) else null
                val targetLang = service.getTargetLanguage()
                val targetLangCode = getLangCode(targetLang)
                val sourceLangCode = detectSourceLanguage(text)

                // Check the token of the *active* provider, not just the Gemini key.
                // ProofreadService.getApiKey() reads only KEY_API_KEY ("gemini_api_key"),
                // which made translation short-circuit for OPENAI/GROQ providers
                // (https://github.com/LeanBitLab/LeanType/issues/459).
                val hasAiConfigured = when (service.getProvider()) {
                    ProofreadService.AIProvider.GEMINI -> !service.getApiKey().isNullOrBlank()
                    ProofreadService.AIProvider.GROQ -> !service.getGroqToken().isNullOrBlank()
                    ProofreadService.AIProvider.OPENAI -> !service.getHuggingFaceToken().isNullOrBlank()
                }

                if (pluginProvider != null && pluginProvider.isAvailable()) {
                    val missingModels = mutableListOf<String>()
                    if (sourceLangCode != "auto" && sourceLangCode != "en") {
                        val isDownloaded = try {
                            pluginProvider.isModelDownloaded(sourceLangCode)
                        } catch (_: Throwable) {
                            false
                        } || helium314.keyboard.latin.translation.TranslationModelImporter.isModelInstalled(context, sourceLangCode)
                        if (!isDownloaded) {
                            missingModels.add(sourceLangCode)
                        }
                    }
                    if (targetLangCode != "en" && !missingModels.contains(targetLangCode)) {
                        val isDownloaded = try {
                            pluginProvider.isModelDownloaded(targetLangCode)
                        } catch (_: Throwable) {
                            false
                        } || helium314.keyboard.latin.translation.TranslationModelImporter.isModelInstalled(context, targetLangCode)
                        if (!isDownloaded) {
                            missingModels.add(targetLangCode)
                        }
                    }

                    if (missingModels.isNotEmpty()) {
                        val missingNames = missingModels.joinToString(", ") { getLanguageDisplayName(context, it) }
                        val errorMsg = context.getString(R.string.translation_specific_model_not_downloaded, missingNames)
                        if (isOfflineOnly || !hasAiConfigured) {
                            mainHandler.post {
                                KeyboardSwitcher.getInstance().showToast(errorMsg, true)
                            }
                            return@performAsyncOperation Result.failure(Exception(errorMsg))
                        } else {
                            mainHandler.post {
                                KeyboardSwitcher.getInstance().showToast(
                                    context.getString(R.string.translation_switching_to_ai, missingNames),
                                    false
                                )
                            }
                            Log.i("ProofreadHelper", "Plugin model for $missingNames not downloaded, falling back to built-in AI")
                            return@performAsyncOperation service.translate(text)
                        }
                    }

                    try {
                        Log.i("ProofreadHelper", "Translating via Translation Plugin (source: $sourceLangCode, target: $targetLangCode)")
                        val result = pluginProvider.translate(text, targetLangCode, sourceLangCode)
                        if (result.isNotBlank()) {
                            Result.success(result)
                        } else if (isOfflineOnly || !hasAiConfigured) {
                            Result.failure(Exception("Plugin translation returned empty result"))
                        } else {
                            mainHandler.post {
                                KeyboardSwitcher.getInstance().showToast(
                                    context.getString(R.string.translation_plugin_fallback_to_ai),
                                    false
                                )
                            }
                            Log.w("ProofreadHelper", "Plugin returned blank text, falling back to built-in AI")
                            service.translate(text)
                        }
                    } catch (e: Throwable) {
                        if (isOfflineOnly || !hasAiConfigured) {
                            Result.failure(e)
                        } else {
                            mainHandler.post {
                                KeyboardSwitcher.getInstance().showToast(
                                    context.getString(R.string.translation_plugin_fallback_to_ai),
                                    false
                                )
                            }
                            Log.e("ProofreadHelper", "Plugin translation failed, falling back to built-in AI", e)
                            service.translate(text)
                        }
                    }
                } else if (isOfflineOnly || !hasAiConfigured) {
                    mainHandler.post {
                        KeyboardSwitcher.getInstance().showToast(
                            context.getString(R.string.translation_model_not_downloaded),
                            true
                        )
                    }
                    Result.failure(Exception("Translation plugin not available"))
                } else {
                    if (!isOnlineOnly) {
                        mainHandler.post {
                            KeyboardSwitcher.getInstance().showToast(
                                context.getString(R.string.translation_plugin_fallback_to_ai),
                                false
                            )
                        }
                    }
                    Log.i("ProofreadHelper", if (isOnlineOnly) "Translating via built-in AI service" else "Plugin unavailable, translating via built-in AI service")
                    service.translate(text)
                }
            },
            onSuccess = onSuccess,
            onError = onError
        )
    }
    
    /**
     * Simple Java-friendly interface for translation (reuses ProofreadCallback).
     */
    fun translateAsync(
        context: Context,
        text: String,
        hasSelection: Boolean,
        callback: ProofreadCallback
    ) {
        translateAsync(
            context = context,
            text = text,
            hasSelection = hasSelection,
            onSuccess = { callback.onSuccess(it) },
            onError = { callback.onError(it) }
        )
    }
    /**
     * Perform custom AI action asynchronously.
     */
    fun customAsync(
        context: Context,
        text: String,
        prompt: String,
        hasSelection: Boolean,
        showThinking: Boolean,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        performAsyncOperation(
            context = context,
            text = text,
            noTextErrorResId = R.string.proofread_no_text,
            errorResId = R.string.proofread_error,
            apiCall = { service -> service.proofread(text, overridePrompt = prompt, showThinking = showThinking) },
            onSuccess = onSuccess,
            onError = onError,
            allowEmptyInput = true
        )
    }

    /**
     * Java-friendly interface for custom action.
     */
    fun customAsync(
        context: Context,
        text: String,
        prompt: String,
        hasSelection: Boolean,
        showThinking: Boolean = false,
        callback: ProofreadCallback
    ) {
        customAsync(
            context = context,
            text = text,
            prompt = prompt,
            hasSelection = hasSelection,
            showThinking = showThinking,
            onSuccess = { callback.onSuccess(it) },
            onError = { callback.onError(it) }
        )
    }
}
