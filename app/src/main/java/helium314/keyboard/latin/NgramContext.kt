/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin

import android.text.TextUtils
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.define.DecoderSpecificConstants
import java.util.ArrayList
import java.util.Arrays

/**
 * Class to represent information of previous words. This class is used to add n-gram entries
 * into binary dictionaries, to get predictions, and to get suggestions.
 */
class NgramContext {
    private val mPrevWordsInfo: Array<WordInfo>
    private val mPrevWordsCount: Int
    private val mMaxPrevWordCount: Int

    constructor(vararg prevWordsInfo: WordInfo) : this(
        DecoderSpecificConstants.MAX_PREV_WORD_COUNT_FOR_N_GRAM,
        *prevWordsInfo
    )

    constructor(maxPrevWordCount: Int, vararg prevWordsInfo: WordInfo) {
        mPrevWordsInfo = Array(prevWordsInfo.size) { prevWordsInfo[it] }
        mPrevWordsCount = prevWordsInfo.size
        mMaxPrevWordCount = maxPrevWordCount
    }

    fun changeWordIfAfterBeginningOfSentence(from: String, to: String): Boolean {
        var beginning = false
        for (i in mPrevWordsCount - 1 downTo 0) {
            val info = mPrevWordsInfo[i]
            if (beginning && TextUtils.equals(info.mWord, from)) {
                mPrevWordsInfo[i] = WordInfo(to)
                return true
            }
            beginning = info.mIsBeginningOfSentence
        }
        return false
    }

    /**
     * Create next prevWordsInfo using current prevWordsInfo.
     */
    fun getNextNgramContext(wordInfo: WordInfo): NgramContext {
        val nextPrevWordCount = minOf(mMaxPrevWordCount, mPrevWordsCount + 1)
        val prevWordsInfo = Array(nextPrevWordCount) { i ->
            if (i == 0) wordInfo else mPrevWordsInfo[i - 1]
        }
        return NgramContext(mMaxPrevWordCount, *prevWordsInfo)
    }

    /**
     * Returns a 1-gram context containing only the immediately preceding word.
     */
    val singlePrevWordContext: NgramContext
        get() {
            if (mPrevWordsCount <= 1) return this
            return NgramContext(mMaxPrevWordCount, mPrevWordsInfo[0])
        }

    /**
     * Extracts the previous words context.
     *
     * @return a String with the previous words separated by white space.
     */
    fun extractPrevWordsContext(): String {
        val terms = ArrayList<String>()
        for (i in mPrevWordsInfo.indices.reversed()) {
            val wordInfo = mPrevWordsInfo[i]
            if (wordInfo.isValid) {
                if (wordInfo.mIsBeginningOfSentence) {
                    terms.add(BEGINNING_OF_SENTENCE_TAG)
                } else {
                    val term = wordInfo.mWord.toString()
                    if (term.isNotEmpty()) {
                        terms.add(term)
                    }
                }
            }
        }
        return TextUtils.join(CONTEXT_SEPARATOR, terms)
    }

    /**
     * Extracts the previous words context.
     *
     * @return a String array with the previous words.
     */
    fun extractPrevWordsContextArray(): Array<String> {
        val prevTermList = ArrayList<String>()
        for (i in mPrevWordsInfo.indices.reversed()) {
            val wordInfo = mPrevWordsInfo[i]
            if (wordInfo.isValid) {
                if (wordInfo.mIsBeginningOfSentence) {
                    prevTermList.add(BEGINNING_OF_SENTENCE_TAG)
                } else {
                    val term = wordInfo.mWord.toString()
                    if (term.isNotEmpty()) {
                        prevTermList.add(term)
                    }
                }
            }
        }
        return prevTermList.toTypedArray()
    }

    val isValid: Boolean
        get() = mPrevWordsCount > 0 && mPrevWordsInfo[0].isValid

    val isBeginningOfSentenceContext: Boolean
        get() = mPrevWordsCount > 0 && mPrevWordsInfo[0].mIsBeginningOfSentence

    fun getNthPrevWord(n: Int): CharSequence? {
        if (n <= 0 || n > mPrevWordsCount) return null
        return mPrevWordsInfo[n - 1].mWord
    }

    fun isNthPrevWordBeginningOfSentence(n: Int): Boolean {
        if (n <= 0 || n > mPrevWordsCount) return false
        return mPrevWordsInfo[n - 1].mIsBeginningOfSentence
    }

    fun outputToArray(codePointArrays: Array<IntArray?>, isBeginningOfSentenceArray: BooleanArray) {
        for (i in codePointArrays.indices) {
            if (i < mPrevWordsCount) {
                val wordInfo = mPrevWordsInfo[i]
                val word = wordInfo.mWord
                if (!wordInfo.isValid || word == null) {
                    codePointArrays[i] = IntArray(0)
                    isBeginningOfSentenceArray[i] = false
                    continue
                }
                codePointArrays[i] = StringUtils.toCodePointArray(word)
                isBeginningOfSentenceArray[i] = wordInfo.mIsBeginningOfSentence
            } else {
                codePointArrays[i] = IntArray(0)
                isBeginningOfSentenceArray[i] = false
            }
        }
    }

    val prevWordCount: Int
        get() = mPrevWordsCount

    override fun hashCode(): Int {
        var hashValue = 0
        for (wordInfo in mPrevWordsInfo) {
            if (WordInfo.EMPTY_WORD_INFO != wordInfo) {
                break
            }
            hashValue = hashValue xor wordInfo.hashCode()
        }
        return hashValue
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NgramContext) return false

        val minLength = minOf(mPrevWordsCount, other.mPrevWordsCount)
        for (i in 0 until minLength) {
            if (mPrevWordsInfo[i] != other.mPrevWordsInfo[i]) {
                return false
            }
        }
        val longerWordsInfo: Array<WordInfo>
        val longerWordsInfoCount: Int
        if (mPrevWordsCount > other.mPrevWordsCount) {
            longerWordsInfo = mPrevWordsInfo
            longerWordsInfoCount = mPrevWordsCount
        } else {
            longerWordsInfo = other.mPrevWordsInfo
            longerWordsInfoCount = other.mPrevWordsCount
        }
        for (i in minLength until longerWordsInfoCount) {
            if (WordInfo.EMPTY_WORD_INFO != longerWordsInfo[i]) {
                return false
            }
        }
        return true
    }

    override fun toString(): String {
        val builder = StringBuilder()
        for (i in 0 until mPrevWordsCount) {
            val wordInfo = mPrevWordsInfo[i]
            builder.append("PrevWord[").append(i).append("]: ")
            if (!wordInfo.isValid) {
                builder.append(if (wordInfo.mWord == null) "null. " else "Empty. ")
                continue
            }
            builder.append(wordInfo.mWord).append(", isBeginningOfSentence: ").append(wordInfo.mIsBeginningOfSentence).append(". ")
        }
        return builder.toString()
    }

    class WordInfo {
        val mWord: CharSequence?
        val mIsBeginningOfSentence: Boolean

        // Beginning of sentence.
        private constructor() {
            mWord = ""
            mIsBeginningOfSentence = true
        }

        constructor(word: CharSequence?) {
            mWord = word
            mIsBeginningOfSentence = false
        }

        val isValid: Boolean
            get() = mWord != null

        override fun hashCode(): Int = Arrays.hashCode(arrayOf<Any?>(mWord, mIsBeginningOfSentence))

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WordInfo) return false
            if (mWord == null || other.mWord == null) {
                return mWord === other.mWord && mIsBeginningOfSentence == other.mIsBeginningOfSentence
            }
            return TextUtils.equals(mWord, other.mWord) && mIsBeginningOfSentence == other.mIsBeginningOfSentence
        }

        companion object {
            val EMPTY_WORD_INFO = WordInfo(null)
            val BEGINNING_OF_SENTENCE_WORD_INFO = WordInfo()
        }
    }

    companion object {
        val EMPTY_PREV_WORDS_INFO = NgramContext(WordInfo.EMPTY_WORD_INFO)
        val BEGINNING_OF_SENTENCE = NgramContext(WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO)

        const val BEGINNING_OF_SENTENCE_TAG = "<S>"
        const val CONTEXT_SEPARATOR = " "

        fun getEmptyPrevWordsContext(maxPrevWordCount: Int): NgramContext {
            return NgramContext(maxPrevWordCount, WordInfo.EMPTY_WORD_INFO)
        }
    }
}
