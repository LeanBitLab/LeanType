/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin

import helium314.keyboard.keyboard.internal.KeySpecParser
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.dictionary.Dictionary
import java.util.ArrayList
import java.util.Arrays

/**
 * The extended [SuggestedWords] class to represent punctuation suggestions.
 *
 * Each punctuation specification string is the key specification that can be parsed by
 * [KeySpecParser].
 */
class PunctuationSuggestions private constructor(punctuationsList: ArrayList<SuggestedWordInfo>) : SuggestedWords(
    punctuationsList,
    null,
    null,
    false,
    false,
    false,
    INPUT_STYLE_NONE,
    NOT_A_SEQUENCE_NUMBER
) {

    override fun getWord(index: Int): String {
        val keySpec = super.getWord(index)
        val code = KeySpecParser.getCode(keySpec)
        return if (code == KeyCode.MULTIPLE_CODE_POINTS) {
            KeySpecParser.getOutputText(keySpec, code) ?: keySpec
        } else {
            StringUtils.newSingleCodePointString(code)
        }
    }

    override fun getLabel(index: Int): String {
        val keySpec = super.getWord(index)
        return KeySpecParser.getLabel(keySpec) ?: keySpec
    }

    override fun getInfo(index: Int): SuggestedWordInfo {
        return newHardCodedWordInfo(getWord(index))
    }

    override val isPunctuationSuggestions: Boolean
        get() = true

    override fun toString(): String {
        return "PunctuationSuggestions:  words=" + Arrays.toString(mSuggestedWordInfoList.toTypedArray())
    }

    companion object {
        @JvmStatic
        fun newPunctuationSuggestions(punctuationSpecs: Array<String>?): PunctuationSuggestions {
            if (punctuationSpecs == null || punctuationSpecs.isEmpty()) {
                return PunctuationSuggestions(ArrayList(0))
            }
            val punctuationList = ArrayList<SuggestedWordInfo>(punctuationSpecs.size)
            for (spec in punctuationSpecs) {
                punctuationList.add(newHardCodedWordInfo(spec))
            }
            return PunctuationSuggestions(punctuationList)
        }

        private fun newHardCodedWordInfo(keySpec: String): SuggestedWordInfo {
            return SuggestedWordInfo(
                keySpec,
                "",
                SuggestedWordInfo.MAX_SCORE,
                SuggestedWordInfo.KIND_HARDCODED,
                Dictionary.DICTIONARY_HARDCODED,
                SuggestedWordInfo.NOT_AN_INDEX,
                SuggestedWordInfo.NOT_A_CONFIDENCE
            )
        }
    }
}
