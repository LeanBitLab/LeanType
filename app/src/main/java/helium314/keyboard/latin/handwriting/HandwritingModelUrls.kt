// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.handwriting

object HandwritingModelUrls {
    private val SCRIPT_URLS = mapOf(
        "arabic" to "https://dl.google.com/handwriting/models/scribe.arabic.20221129tfreco.tflite.zip",
        "armenian" to "https://dl.google.com/handwriting/models/scribe.armenian.20221129tfreco.tflite.zip",
        "bengali" to "https://dl.google.com/handwriting/models/scribe.bengali.20221129tfreco.tflite.zip",
        "cyrillic" to "https://dl.google.com/handwriting/models/scribe.cyrillic.20221129tfreco.tflite.zip",
        "devanagari" to "https://dl.google.com/handwriting/models/scribe.devanagari.20221129tfreco.tflite.zip",
        "georgian" to "https://dl.google.com/handwriting/models/scribe.georgian.20221129tfreco.tflite.zip",
        "greek" to "https://dl.google.com/handwriting/models/scribe.greek.20221129tfreco.tflite.zip",
        "gujarati" to "https://dl.google.com/handwriting/models/scribe.gujarati.20221129tfreco.tflite.zip",
        "hebrew" to "https://dl.google.com/handwriting/models/scribe.hebrew.20221129tfreco.tflite.zip",
        "japanese" to "https://dl.google.com/handwriting/models/scribe.japanese.20221129tfreco.tflite.zip",
        "kannada" to "https://dl.google.com/handwriting/models/scribe.kannada.20221129tfreco.tflite.zip",
        "khmer" to "https://dl.google.com/handwriting/models/scribe.khmer.20221129tfreco.tflite.zip",
        "korean" to "https://dl.google.com/handwriting/models/scribe.korean.20221129tfreco.tflite.zip",
        "lao" to "https://dl.google.com/handwriting/models/scribe.lao.20221129tfreco.tflite.zip",
        "latin" to "https://dl.google.com/handwriting/models/scribe.latin.20221129tfreco.tflite.zip",
        "malayalam" to "https://dl.google.com/handwriting/models/scribe.malayalam.20221129tfreco.tflite.zip",
        "myanmar" to "https://dl.google.com/handwriting/models/scribe.myanmar.20221129tfreco.tflite.zip",
        "odia" to "https://dl.google.com/handwriting/models/scribe.odia.20221129tfreco.tflite.zip",
        "punjabi" to "https://dl.google.com/handwriting/models/scribe.punjabi.20221129tfreco.tflite.zip",
        "sinhala" to "https://dl.google.com/handwriting/models/scribe.sinhala.20221129tfreco.tflite.zip",
        "tamil" to "https://dl.google.com/handwriting/models/scribe.tamil.20221129tfreco.tflite.zip",
        "telugu" to "https://dl.google.com/handwriting/models/scribe.telugu.20221129tfreco.tflite.zip",
        "thai" to "https://dl.google.com/handwriting/models/scribe.thai.20221129tfreco.tflite.zip",
        "tibetan" to "https://dl.google.com/handwriting/models/scribe.tibetan.20221129tfreco.tflite.zip",
        "vietnamese" to "https://dl.google.com/handwriting/models/scribe.vietnamese.20221129tfreco.tflite.zip"
    )

    private val LANG_TO_SCRIPT = mapOf(
        "ar" to "arabic", "fa" to "arabic", "ur" to "arabic", "ps" to "arabic",
        "hy" to "armenian",
        "bn" to "bengali", "as" to "bengali",
        "ru" to "cyrillic", "uk" to "cyrillic", "be" to "cyrillic", "bg" to "cyrillic", "mk" to "cyrillic", "sr" to "cyrillic", "kk" to "cyrillic", "ky" to "cyrillic", "tg" to "cyrillic", "mn" to "cyrillic",
        "hi" to "devanagari", "mr" to "devanagari", "ne" to "devanagari", "sa" to "devanagari", "kok" to "devanagari", "mai" to "devanagari", "bho" to "devanagari",
        "ka" to "georgian",
        "el" to "greek",
        "gu" to "gujarati",
        "he" to "hebrew", "iw" to "hebrew", "yi" to "hebrew",
        "ja" to "japanese",
        "kn" to "kannada",
        "km" to "khmer",
        "ko" to "korean",
        "lo" to "lao",
        "ml" to "malayalam",
        "my" to "myanmar",
        "or" to "odia",
        "pa" to "punjabi",
        "si" to "sinhala",
        "ta" to "tamil",
        "te" to "telugu",
        "th" to "thai",
        "bo" to "tibetan",
        "vi" to "vietnamese"
    )

    fun getDownloadUrls(languageTag: String): List<String> {
        val normalizedTag = languageTag.replace('_', '-')
        val baseLang = languageTag.substringBefore('-').lowercase()
        
        HandwritingModelPackData.LANGUAGE_PACKS[normalizedTag]?.let { if (it.isNotEmpty()) return it }
        HandwritingModelPackData.LANGUAGE_PACKS[languageTag]?.let { if (it.isNotEmpty()) return it }
        HandwritingModelPackData.LANGUAGE_PACKS[baseLang]?.let { if (it.isNotEmpty()) return it }
        
        // Fallback to script URL
        val script = LANG_TO_SCRIPT[baseLang] ?: "latin"
        val fallback = SCRIPT_URLS[script] ?: SCRIPT_URLS["latin"] ?: ""
        return listOf(fallback)
    }

    fun getDownloadUrl(languageTag: String): String {
        return getDownloadUrls(languageTag).firstOrNull() ?: SCRIPT_URLS["latin"] ?: ""
    }
}
