/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.settings

import android.content.res.Resources
import helium314.keyboard.compat.locale
import helium314.keyboard.keyboard.internal.PopupKeySpec
import helium314.keyboard.latin.PunctuationSuggestions
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.common.getSecondInSymbolPair
import helium314.keyboard.latin.common.toSortedCodepointArrays
import java.util.Arrays
import java.util.Locale

class SpacingAndPunctuations(res: Resources, urlDetection: Boolean) {
    private val mSortedSymbolsPrecededBySpace: IntArray
    private val mSortedSymbolsFollowedBySpace: IntArray
    private val mSortedSymbolsClusteringTogether: IntArray
    private val mSortedWordConnectors: IntArray
    private val mSortedSometimesWordConnectors: IntArray

    @JvmField val mSortedWordSeparators: IntArray
    @JvmField val mPairSymbols: List<IntArray>
    @JvmField val mSuggestPuncList: PunctuationSuggestions
    private val mSentenceSeparator: Int
    private val mAbbreviationMarker: Int
    private val mSortedSentenceTerminators: IntArray
    @JvmField val mSentenceSeparatorAndSpace: String
    @JvmField val mCurrentLanguageHasSpaces: Boolean
    @JvmField val mUsesAmericanTypography: Boolean
    @JvmField val mUsesGermanRules: Boolean

    init {
        mSortedSymbolsPrecededBySpace = StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_preceded_by_space))
        mSortedSymbolsFollowedBySpace = StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_followed_by_space))
        mSortedSymbolsClusteringTogether = StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_clustering_together))
        mSortedWordConnectors = StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_word_connectors))
        mSortedWordSeparators = StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_word_separators))
        mPairSymbols = toSortedCodepointArrays(res.getString(R.string.pair_symbols))
        mSortedSentenceTerminators = StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_sentence_terminators))
        mSentenceSeparator = res.getInteger(R.integer.sentence_separator)
        mAbbreviationMarker = res.getInteger(R.integer.abbreviation_marker)
        mSentenceSeparatorAndSpace = String(intArrayOf(mSentenceSeparator, Constants.CODE_SPACE), 0, 2)
        mCurrentLanguageHasSpaces = res.getBoolean(R.bool.current_language_has_spaces)

        mSortedSometimesWordConnectors = if (urlDetection && mCurrentLanguageHasSpaces) {
            StringUtils.toSortedCodePointArray(res.getString(R.string.symbols_sometimes_word_connectors))
        } else {
            IntArray(0)
        }

        val locale = res.configuration.locale()
        mUsesAmericanTypography = Locale.ENGLISH.language == locale.language
        mUsesGermanRules = Locale.GERMAN.language == locale.language

        val suggestPuncsSpec = PopupKeySpec.splitKeySpecs(res.getString(R.string.suggested_punctuations))
        mSuggestPuncList = PunctuationSuggestions.newPunctuationSuggestions(suggestPuncsSpec)
    }

    fun isWordSeparator(code: Int): Boolean {
        return Arrays.binarySearch(mSortedWordSeparators, code) >= 0
    }

    fun getSecondInSymbolPair(code: Int): Int {
        return helium314.keyboard.latin.common.getSecondInSymbolPair(mPairSymbols, code)
    }

    fun isWordConnector(code: Int): Boolean {
        return Arrays.binarySearch(mSortedWordConnectors, code) >= 0
    }

    fun isSometimesWordConnector(code: Int): Boolean {
        return Arrays.binarySearch(mSortedSometimesWordConnectors, code) >= 0
    }

    fun containsSometimesWordConnector(word: CharSequence): Boolean {
        val s = if (word is String) word else word.toString()
        val length = s.length
        var offset = 0
        while (offset < length) {
            val cp = Character.codePointAt(s, offset)
            if (isSometimesWordConnector(cp)) return true
            offset += Character.charCount(cp)
        }
        return false
    }

    fun isWordCodePoint(code: Int): Boolean {
        return Character.isLetter(code) || isWordConnector(code) || Character.getType(code) == Character.COMBINING_SPACING_MARK.toInt()
    }

    fun isUsuallyPrecededBySpace(code: Int): Boolean {
        return Arrays.binarySearch(mSortedSymbolsPrecededBySpace, code) >= 0
    }

    fun isUsuallyFollowedBySpace(code: Int): Boolean {
        return Arrays.binarySearch(mSortedSymbolsFollowedBySpace, code) >= 0
    }

    fun isClusteringSymbol(code: Int): Boolean {
        return Arrays.binarySearch(mSortedSymbolsClusteringTogether, code) >= 0
    }

    fun isSentenceTerminator(code: Int): Boolean {
        return Arrays.binarySearch(mSortedSentenceTerminators, code) >= 0
    }

    fun isAbbreviationMarker(code: Int): Boolean {
        return code == mAbbreviationMarker
    }

    fun isSentenceSeparator(code: Int): Boolean {
        return code == mSentenceSeparator
    }

    fun dump(): String {
        return "mSortedSymbolsPrecededBySpace = " +
                Arrays.toString(mSortedSymbolsPrecededBySpace) +
                "\n   mSortedSymbolsFollowedBySpace = " +
                Arrays.toString(mSortedSymbolsFollowedBySpace) +
                "\n   mSortedWordConnectors = " +
                Arrays.toString(mSortedWordConnectors) +
                "\n   mSortedWordSeparators = " +
                Arrays.toString(mSortedWordSeparators) +
                "\n   mSuggestPuncList = " +
                mSuggestPuncList +
                "\n   mSentenceSeparator = " +
                mSentenceSeparator +
                "\n   mSentenceSeparatorAndSpace = " +
                mSentenceSeparatorAndSpace +
                "\n   mCurrentLanguageHasSpaces = " +
                mCurrentLanguageHasSpaces +
                "\n   mUsesAmericanTypography = " +
                mUsesAmericanTypography +
                "\n   mUsesGermanRules = " +
                mUsesGermanRules
    }
}
