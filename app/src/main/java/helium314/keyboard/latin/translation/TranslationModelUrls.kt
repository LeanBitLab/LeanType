// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translation

object TranslationModelUrls {
    private val MODEL_MAP = mapOf(
        "af" to "af_en",
        "ar" to "ar_en",
        "be" to "be_en",
        "bg" to "bg_en",
        "bn" to "bn_en",
        "ca" to "ca_en",
        "cs" to "cs_en",
        "cy" to "cy_en",
        "da" to "da_en",
        "de" to "de_en",
        "el" to "el_en",
        "eo" to "en_eo",
        "es" to "en_es",
        "et" to "en_et",
        "fa" to "en_fa",
        "fi" to "en_fi",
        "fr" to "en_fr",
        "ga" to "en_ga",
        "gl" to "en_gl",
        "gu" to "en_gu",
        "he" to "en_iw",
        "hi" to "en_hi",
        "hr" to "en_hr",
        "ht" to "en_ht",
        "hu" to "en_hu",
        "id" to "en_id",
        "is" to "en_is",
        "it" to "en_it",
        "ja" to "en_ja",
        "ka" to "en_ka",
        "kn" to "en_kn",
        "ko" to "en_ko",
        "lt" to "en_lt",
        "lv" to "en_lv",
        "mk" to "en_mk",
        "mr" to "en_mr",
        "ms" to "en_ms",
        "mt" to "en_mt",
        "nl" to "en_nl",
        "no" to "en_no",
        "pl" to "en_pl",
        "pt" to "en_pt",
        "ro" to "en_ro",
        "ru" to "en_ru",
        "sk" to "en_sk",
        "sl" to "en_sl",
        "sq" to "en_sq",
        "sv" to "en_sv",
        "sw" to "en_sw",
        "ta" to "en_ta",
        "te" to "en_te",
        "th" to "en_th",
        "tl" to "en_tl",
        "tr" to "en_tr",
        "uk" to "en_uk",
        "ur" to "en_ur",
        "vi" to "en_vi",
        "zh" to "en_zh"
    )

    fun getModelName(langCode: String): String? {
        val code = if (langCode == "iw") "he" else langCode
        return MODEL_MAP[code]
    }

    fun getDownloadUrl(langCode: String): String? {
        val model = getModelName(langCode) ?: return null
        return "https://dl.google.com/translate/offline/v5/high/r29/$model.zip"
    }
}
