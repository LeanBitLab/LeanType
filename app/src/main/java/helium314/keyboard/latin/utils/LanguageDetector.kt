// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage
import helium314.keyboard.latin.RichInputMethodManager

/**
 * Intelligent on-device language detector for translation and proofreading.
 * Supports Unicode script analysis, Android TextClassifier (API 29+), and fast heuristic
 * diacritic/vocabulary matching for Latin-script languages.
 */
object LanguageDetector {

    fun detect(context: Context?, text: String, targetLangCode: String? = null): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return "auto"

        // 1. Script-based detection for non-Latin scripts
        val scriptLang = detectByScript(trimmed)
        if (scriptLang != null) return scriptLang

        // 2. Android system TextClassificationManager (API 29+)
        if (context != null) {
            val systemLang = detectBySystemClassifier(context, trimmed)
            if (systemLang != null && systemLang != "und") {
                // If system detected a language that differs from target, return it immediately
                if (targetLangCode == null || !systemLang.equals(targetLangCode, ignoreCase = true)) {
                    return systemLang
                }
            }
        }

        // 3. Heuristic detector based on distinct diacritics and frequent vocabulary
        val heuristicLang = detectByHeuristics(trimmed, targetLangCode)
        if (heuristicLang != null) return heuristicLang

        // 4. Fallback to active keyboard subtype ONLY IF it does not collide with target language
        try {
            val currentSubtype = RichInputMethodManager.getInstance().currentSubtype
            val keyboardLang = currentSubtype.locale.language.lowercase()
            val targetLower = targetLangCode?.lowercase()
            if (keyboardLang.isNotBlank() && keyboardLang != "zz" && keyboardLang != targetLower) {
                return keyboardLang
            }
        } catch (_: Throwable) {}

        return "auto"
    }

    private fun detectByScript(text: String): String? {
        var tamil = 0
        var malayalam = 0
        var telugu = 0
        var kannada = 0
        var gujarati = 0
        var bengali = 0
        var devanagari = 0
        var arabic = 0
        var greek = 0
        var hebrew = 0
        var hangul = 0
        var thai = 0
        var georgian = 0
        var armenian = 0
        var sinhala = 0
        var myanmar = 0
        var khmer = 0
        var lao = 0
        var cyrillic = 0
        var japaneseKana = 0
        var cjkHan = 0

        for (cp in text.codePoints()) {
            when {
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_TAMIL) -> tamil++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_MALAYALAM) -> malayalam++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_TELUGU) -> telugu++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_KANNADA) -> kannada++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_GUJARATI) -> gujarati++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_BENGALI) -> bengali++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_DEVANAGARI) -> devanagari++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_ARABIC) -> arabic++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_GREEK) -> greek++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_HEBREW) -> hebrew++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_HANGUL) || (cp in 0xAC00..0xD7AF) -> hangul++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_THAI) -> thai++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_GEORGIAN) -> georgian++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_ARMENIAN) -> armenian++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_SINHALA) -> sinhala++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_MYANMAR) -> myanmar++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_KHMER) -> khmer++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_LAO) -> lao++
                ScriptUtils.isLetterPartOfScript(cp, ScriptUtils.SCRIPT_CYRILLIC) -> cyrillic++
                (cp in 0x3040..0x309F) || (cp in 0x30A0..0x30FF) -> japaneseKana++
                (cp in 0x4E00..0x9FFF) || (cp in 0x3400..0x4DBF) -> cjkHan++
            }
        }

        // Japanese takes precedence if kana is present with Han ideographs
        if (japaneseKana > 0) return "ja"
        if (cjkHan > 0) return "zh"
        if (cyrillic > 0) return "ru"
        if (devanagari > 0) return "hi"
        if (arabic > 0) return "ar"
        if (bengali > 0) return "bn"
        if (tamil > 0) return "ta"
        if (telugu > 0) return "te"
        if (kannada > 0) return "kn"
        if (malayalam > 0) return "ml"
        if (gujarati > 0) return "gu"
        if (hangul > 0) return "ko"
        if (greek > 0) return "el"
        if (hebrew > 0) return "he"
        if (thai > 0) return "th"
        if (georgian > 0) return "ka"
        if (armenian > 0) return "hy"
        if (sinhala > 0) return "si"
        if (myanmar > 0) return "my"
        if (khmer > 0) return "km"
        if (lao > 0) return "lo"

        return null
    }

    private fun detectBySystemClassifier(context: Context, text: String): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val tcm = context.getSystemService(Context.TEXT_CLASSIFICATION_SERVICE) as? TextClassificationManager
                val classifier = tcm?.textClassifier
                if (classifier != null) {
                    val textLanguage = classifier.detectLanguage(
                        TextLanguage.Request.Builder(text).build()
                    )
                    if (textLanguage.localeHypothesisCount > 0) {
                        val bestLocale = textLanguage.getLocale(0)
                        val confidence = textLanguage.getConfidenceScore(bestLocale)
                        val lang = bestLocale.language.lowercase()
                        if (confidence >= 0.45f && lang.isNotBlank() && lang != "und") {
                            return lang
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun detectByHeuristics(text: String, targetLangCode: String?): String? {
        val scores = mutableMapOf<String, Int>()

        fun addScore(lang: String, points: Int) {
            scores[lang] = (scores[lang] ?: 0) + points
        }

        // Distinct character checks
        for (char in text) {
            when (char) {
                '¿', '¡', 'ñ', 'Ñ' -> addScore("es", 3)
                'œ', 'æ' -> addScore("fr", 3)
                'ç', 'Ç' -> {
                    addScore("fr", 2)
                    addScore("pt", 2)
                }
                'ß' -> addScore("de", 3)
                'ä', 'ö', 'ü', 'Ä', 'Ö', 'Ü' -> addScore("de", 2)
                'ã', 'õ', 'Ã', 'Õ' -> addScore("pt", 3)
                'ą', 'ć', 'ę', 'ł', 'ń', 'ó', 'ś', 'ź', 'ż',
                'Ą', 'Ć', 'Ę', 'Ł', 'Ń', 'Ó', 'Ś', 'Ź', 'Ż' -> addScore("pl", 2)
                'ğ', 'Ğ', 'ı', 'İ', 'ş', 'Ş' -> addScore("tr", 3)
            }
        }

        val words = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 2 }
        for (word in words) {
            if (SPANISH_WORDS.contains(word)) addScore("es", 2)
            if (FRENCH_WORDS.contains(word)) addScore("fr", 2)
            if (GERMAN_WORDS.contains(word)) addScore("de", 2)
            if (ITALIAN_WORDS.contains(word)) addScore("it", 2)
            if (PORTUGUESE_WORDS.contains(word)) addScore("pt", 2)
            if (DUTCH_WORDS.contains(word)) addScore("nl", 2)
            if (TURKISH_WORDS.contains(word)) addScore("tr", 2)
            if (POLISH_WORDS.contains(word)) addScore("pl", 2)
            if (ENGLISH_WORDS.contains(word)) addScore("en", 2)
        }

        if (scores.isEmpty()) return null

        val targetLower = targetLangCode?.lowercase()
        val sorted = scores.entries.sortedByDescending { it.value }
        val top = sorted.firstOrNull() ?: return null

        if (top.value < 2) return null

        // If top scored language is the target language, but a non-target language also has a strong score,
        // prefer the non-target language to avoid source==target self-collision.
        if (targetLower != null && top.key == targetLower) {
            val nonTargetCandidate = sorted.firstOrNull { it.key != targetLower && it.value >= 2 }
            if (nonTargetCandidate != null) {
                return nonTargetCandidate.key
            }
        }

        return top.key
    }

    private val SPANISH_WORDS = setOf(
        "hola", "adios", "adiós", "gracias", "por", "favor", "buenos", "dias", "días", "noches", "tardes",
        "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "al", "en", "y", "que", "qué",
        "para", "con", "sin", "sobre", "entre", "hasta", "desde", "hacia", "no", "si", "sí", "es", "son",
        "fue", "era", "como", "cómo", "pero", "más", "este", "esta", "estos", "estas", "esto", "está",
        "están", "estoy", "estás", "estamos", "su", "sus", "mi", "mis", "mío", "mía", "tu", "tus", "tú",
        "yo", "él", "ella", "ellos", "ellas", "nosotros", "nosotras", "usted", "ustedes", "vosotros",
        "amigo", "amiga", "amigos", "amigas", "bueno", "buena", "bien", "muy", "también", "tampoco",
        "mucho", "mucha", "muchos", "muchas", "donde", "dónde", "cuando", "cuándo", "quién", "quiénes",
        "porque", "porqué", "todos", "todas", "todo", "toda", "nada", "nadie", "nunca", "siempre",
        "hacer", "hace", "hacen", "hice", "hecho", "tener", "tengo", "tiene", "tienen", "tenemos",
        "decir", "digo", "dice", "dicen", "dijo", "ir", "voy", "va", "van", "vamos", "fui",
        "poder", "puedo", "puede", "pueden", "podemos", "saber", "sé", "sabe", "saben",
        "querer", "quiero", "quiere", "quieren", "tiempo", "vida", "mundo", "trabajo", "lugar", "cosa"
    )

    private val FRENCH_WORDS = setOf(
        "bonjour", "merci", "salut", "oui", "non", "le", "la", "les", "un", "une", "des", "du", "de",
        "et", "est", "sont", "en", "que", "qui", "dans", "pour", "avec", "ce", "cette", "ces", "je",
        "tu", "il", "elle", "nous", "vous", "ils", "elles", "au", "aux", "sur", "mais", "comment",
        "très", "bien", "faire", "tout", "tous", "plus", "mon", "ma", "mes", "ton", "ta", "tes",
        "son", "sa", "ses", "notre", "votre", "leur", "leurs"
    )

    private val GERMAN_WORDS = setOf(
        "hallo", "danke", "bitte", "guten", "morgen", "tag", "abend", "der", "die", "das", "den", "dem",
        "des", "ein", "eine", "einer", "eines", "einem", "einen", "und", "ist", "sind", "war", "in",
        "von", "zu", "mit", "nicht", "für", "auf", "sich", "sie", "er", "es", "wir", "ihr", "wie",
        "was", "wo", "wer", "warum", "aber", "auch", "haben", "hat", "werden", "wird", "kann", "können",
        "ja", "nein", "sehr", "gut", "alles", "immer", "wieder"
    )

    private val ITALIAN_WORDS = setOf(
        "ciao", "grazie", "buongiorno", "buonasera", "prego", "il", "lo", "la", "i", "gli", "le",
        "un", "uno", "una", "di", "del", "della", "dei", "delle", "a", "al", "alla", "da", "dal",
        "in", "nel", "nella", "con", "su", "sul", "tra", "fra", "e", "è", "che", "non", "sono",
        "come", "questo", "questa", "questi", "queste", "mio", "mia", "tuo", "tua", "suo", "sua",
        "molto", "molta", "tutto", "tutti", "bene", "ancora", "anche", "cosa", "dove", "quando", "perché", "chi"
    )

    private val PORTUGUESE_WORDS = setOf(
        "olá", "ola", "obrigado", "obrigada", "bom", "boa", "dia", "tarde", "noite", "o", "a", "os",
        "as", "um", "uma", "uns", "umas", "de", "do", "da", "dos", "das", "em", "no", "na", "nos",
        "nas", "por", "pelo", "pela", "pelos", "pelas", "para", "com", "não", "e", "é", "são",
        "que", "se", "como", "mais", "mas", "muito", "muita", "você", "vocês", "tudo", "fazer",
        "tem", "ter", "está", "estão", "onde", "quando", "quem", "porque", "porquê"
    )

    private val DUTCH_WORDS = setOf(
        "hallo", "bedankt", "goedemorgen", "alsjeblieft", "de", "het", "een", "en", "van", "ik", "te",
        "dat", "die", "in", "is", "zijn", "was", "niet", "op", "aan", "met", "er", "maar", "om",
        "ook", "als", "je", "jij", "wij", "we", "ze", "voor", "hebben", "heeft", "had", "naar",
        "wat", "waar", "wanneer", "waarom", "wie"
    )

    private val TURKISH_WORDS = setOf(
        "merhaba", "teşekkürler", "günaydın", "iyi", "bir", "bu", "ve", "için", "ne", "da", "de",
        "o", "ile", "mi", "gibi", "daha", "çok", "en", "kadar", "olan", "var", "yok", "ama",
        "nasıl", "nerede", "zaman", "neden", "kim", "evet", "hayır"
    )

    private val POLISH_WORDS = setOf(
        "cześć", "dzień", "dobry", "dziękuję", "proszę", "tak", "nie", "w", "z", "i", "do", "na",
        "to", "się", "jak", "jest", "co", "ale", "po", "dla", "od", "przez", "o", "za", "ze",
        "że", "jego", "jej", "ich", "nasz", "wasz"
    )

    private val ENGLISH_WORDS = setOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "it", "for", "not", "on", "with",
        "he", "as", "you", "do", "at", "this", "but", "his", "by", "from", "they", "we", "say",
        "her", "she", "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me", "when", "make", "can",
        "like", "time", "no", "just", "him", "know", "take", "people", "into", "year", "your",
        "good", "some", "could", "them", "see", "other", "than", "then", "now", "look", "only",
        "come", "its", "over", "think", "also", "back", "after", "use", "two", "how", "our",
        "work", "first", "well", "way", "even", "new", "want", "because", "any", "these", "give",
        "day", "most", "us", "hello", "thanks", "please", "world"
    )
}
