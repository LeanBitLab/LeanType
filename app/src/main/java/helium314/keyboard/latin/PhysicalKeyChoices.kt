// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.dictionary.Dictionary
import java.util.Locale

/**
 * The popup characters of a held physical key, shown in the suggestion strip. Picking one replaces the
 * character typed by the key press. They are not words: nothing is auto-corrected, spaced or learned.
 */
class PhysicalKeyChoices(choices: List<String>) : SuggestedWords(
    ArrayList(choices.map {
        SuggestedWordInfo(it, "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_HARDCODED,
            Dictionary.DICTIONARY_HARDCODED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
    }),
    null, null, false, false, false,
    // not INPUT_STYLE_NONE, which would not replace a clipboard or calculator suggestion in the strip
    INPUT_STYLE_APPLICATION_SPECIFIED, NOT_A_SEQUENCE_NUMBER
) {
    override fun getWordCountToShow() = size()

    companion object {
        /**
         * What the popup of the on-screen [typed] key can insert, in popup order (so following the user's popup
         * settings and layouts), in the case of [typed]. Functional popup keys are left out.
         * [keys] may come from a shifted or unshifted keyboard.
         */
        fun find(keys: List<Key>, typed: Int, locale: Locale): List<String> {
            if (typed <= ' '.code) return emptyList()
            val base = lower(StringUtils.newSingleCodePointString(typed), locale)
            val key = keys.firstOrNull { it.code > 0 && lower(StringUtils.newSingleCodePointString(it.code), locale) == base }
                ?: return emptyList()
            val upperCase = Character.isUpperCase(typed)
            return key.popupKeys.orEmpty().mapNotNull {
                val text = when {
                    it.mCode > ' '.code -> StringUtils.newSingleCodePointString(it.mCode)
                    it.mCode == KeyCode.MULTIPLE_CODE_POINTS -> it.mOutputText
                    else -> null
                }
                // only single letters follow the typed case, longer popups (like ".com") stay as they are
                if (text.isNullOrBlank() || text.codePointCount(0, text.length) != 1 || !Character.isLetter(text.codePointAt(0))) text
                else if (!upperCase) lower(text, locale)
                else StringUtils.toTitleCaseOfKeyCode(lower(text, locale).codePointAt(0), locale)
                    .takeIf { code -> code > 0 }?.let { code -> StringUtils.newSingleCodePointString(code) } ?: text
            }.filter { !it.isNullOrBlank() && it != StringUtils.newSingleCodePointString(typed) }.distinct()
        }

        private fun lower(text: String, locale: Locale): String {
            val lower = text.lowercase(locale)
            return if (lower.codePointCount(0, lower.length) == 1) lower else text
        }
    }
}
