/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.makedict

import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.NgramContext.WordInfo
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.utils.CombinedFormatUtils

/**
 * Utility class for a word with a probability.
 *
 * This is chiefly used to iterate a dictionary.
 */
class WordProperty : Comparable<WordProperty> {
    @JvmField val mWord: String
    @JvmField val mProbabilityInfo: ProbabilityInfo
    @JvmField val mShortcutTargets: ArrayList<WeightedString>?
    @JvmField val mNgrams: ArrayList<NgramProperty>?
    // TODO: Support mIsBeginningOfSentence.
    @JvmField val mIsBeginningOfSentence: Boolean
    @JvmField val mIsNotAWord: Boolean
    @JvmField val mIsPossiblyOffensive: Boolean
    @JvmField val mHasShortcuts: Boolean
    @JvmField val mHasNgrams: Boolean

    private var mHashCode = 0

    // TODO: Support n-gram.
    constructor(
        word: String,
        probabilityInfo: ProbabilityInfo,
        shortcutTargets: ArrayList<WeightedString>?,
        bigrams: ArrayList<WeightedString>?,
        isNotAWord: Boolean,
        isPossiblyOffensive: Boolean
    ) {
        mWord = word
        mProbabilityInfo = probabilityInfo
        mShortcutTargets = shortcutTargets
        if (bigrams == null) {
            mNgrams = null
        } else {
            mNgrams = ArrayList()
            val ngramContext = NgramContext(WordInfo(mWord))
            for (bigramTarget in bigrams) {
                mNgrams.add(NgramProperty(bigramTarget, ngramContext))
            }
        }
        mIsBeginningOfSentence = false
        mIsNotAWord = isNotAWord
        mIsPossiblyOffensive = isPossiblyOffensive
        mHasNgrams = bigrams != null && bigrams.isNotEmpty()
        mHasShortcuts = shortcutTargets != null && shortcutTargets.isNotEmpty()
    }

    // Construct word property using information from native code.
    // This represents invalid word when the probability is BinaryDictionary.NOT_A_PROBABILITY.
    constructor(
        codePoints: IntArray,
        isNotAWord: Boolean,
        isPossiblyOffensive: Boolean,
        hasBigram: Boolean,
        hasShortcuts: Boolean,
        isBeginningOfSentence: Boolean,
        probabilityInfo: IntArray,
        ngramPrevWordsArray: ArrayList<Array<IntArray>>,
        ngramPrevWordIsBeginningOfSentenceArray: ArrayList<BooleanArray>,
        ngramTargets: ArrayList<IntArray>,
        ngramProbabilityInfo: ArrayList<IntArray>,
        shortcutTargets: ArrayList<IntArray>,
        shortcutProbabilities: ArrayList<Int>
    ) {
        mWord = StringUtils.getStringFromNullTerminatedCodePointArray(codePoints)
        mProbabilityInfo = createProbabilityInfoFromArray(probabilityInfo)
        mShortcutTargets = ArrayList()
        val ngrams = ArrayList<NgramProperty>()
        mIsBeginningOfSentence = isBeginningOfSentence
        mIsNotAWord = isNotAWord
        mIsPossiblyOffensive = isPossiblyOffensive
        mHasShortcuts = hasShortcuts
        mHasNgrams = hasBigram

        val relatedNgramCount = ngramTargets.size
        for (i in 0 until relatedNgramCount) {
            val ngramTargetString = StringUtils.getStringFromNullTerminatedCodePointArray(ngramTargets[i])
            val ngramTarget = WeightedString(ngramTargetString, createProbabilityInfoFromArray(ngramProbabilityInfo[i]))
            val prevWords = ngramPrevWordsArray[i]
            val isBeginningOfSentenceArray = ngramPrevWordIsBeginningOfSentenceArray[i]
            val wordInfoArray = Array(prevWords.size) { j ->
                if (isBeginningOfSentenceArray[j]) {
                    WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO
                } else {
                    WordInfo(StringUtils.getStringFromNullTerminatedCodePointArray(prevWords[j]))
                }
            }
            val ngramContext = NgramContext(*wordInfoArray)
            ngrams.add(NgramProperty(ngramTarget, ngramContext))
        }
        mNgrams = if (ngrams.isEmpty()) null else ngrams

        val shortcutTargetCount = shortcutTargets.size
        for (i in 0 until shortcutTargetCount) {
            val shortcutTargetString = StringUtils.getStringFromNullTerminatedCodePointArray(shortcutTargets[i])
            mShortcutTargets.add(WeightedString(shortcutTargetString, shortcutProbabilities[i]))
        }
    }

    // TODO: Remove
    fun getBigrams(): ArrayList<WeightedString>? {
        val ngrams = mNgrams ?: return null
        val bigrams = ArrayList<WeightedString>()
        for (ngram in ngrams) {
            if (ngram.mNgramContext.prevWordCount == 1) {
                bigrams.add(ngram.mTargetWord)
            }
        }
        return bigrams
    }

    fun getProbability(): Int {
        return mProbabilityInfo.mProbability
    }

    /**
     * Three-way comparison.
     *
     * A Word x is greater than a word y if x has a higher frequency. If they have the same
     * frequency, they are sorted in lexicographic order.
     */
    override fun compareTo(other: WordProperty): Int {
        if (getProbability() < other.getProbability()) return 1
        if (getProbability() > other.getProbability()) return -1
        return mWord.compareTo(other.mWord)
    }

    /**
     * Equality test.
     *
     * Words are equal if they have the same frequency, the same spellings, and the same
     * attributes.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WordProperty) return false
        return mProbabilityInfo == other.mProbabilityInfo && mWord == other.mWord
                && mShortcutTargets == other.mShortcutTargets && listEquals(mNgrams, other.mNgrams)
                && mIsNotAWord == other.mIsNotAWord && mIsPossiblyOffensive == other.mIsPossiblyOffensive
                && mHasNgrams == other.mHasNgrams && mHasShortcuts && other.mHasNgrams
    }

    // TODO: Have a utility method like java.util.Objects.equals.
    private fun <T> listEquals(a: ArrayList<T>?, b: ArrayList<T>?): Boolean {
        if (a == null) return b == null
        return a == b
    }

    override fun hashCode(): Int {
        if (mHashCode == 0) {
            mHashCode = arrayOf<Any?>(
                mWord,
                mProbabilityInfo,
                mShortcutTargets,
                mNgrams,
                mIsNotAWord,
                mIsPossiblyOffensive
            ).contentHashCode()
        }
        return mHashCode
    }

    fun isValid(): Boolean {
        return getProbability() != Dictionary.NOT_A_PROBABILITY
    }

    override fun toString(): String {
        return CombinedFormatUtils.formatWordProperty(this)
    }

    companion object {
        private fun createProbabilityInfoFromArray(probabilityInfo: IntArray): ProbabilityInfo {
            return ProbabilityInfo(
                probabilityInfo[BinaryDictionary.FORMAT_WORD_PROPERTY_PROBABILITY_INDEX],
                probabilityInfo[BinaryDictionary.FORMAT_WORD_PROPERTY_TIMESTAMP_INDEX],
                probabilityInfo[BinaryDictionary.FORMAT_WORD_PROPERTY_LEVEL_INDEX],
                probabilityInfo[BinaryDictionary.FORMAT_WORD_PROPERTY_COUNT_INDEX]
            )
        }
    }
}
