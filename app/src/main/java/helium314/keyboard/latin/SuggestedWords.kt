/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin

import android.text.TextUtils
import android.view.inputmethod.CompletionInfo
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.common.isEmoji
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.Settings
import java.util.ArrayList
import java.util.Arrays
import java.util.HashSet

open class SuggestedWords(
    @JvmField protected val mSuggestedWordInfoList: ArrayList<SuggestedWordInfo>,
    @JvmField val mRawSuggestions: ArrayList<SuggestedWordInfo>?,
    @JvmField val mTypedWordInfo: SuggestedWordInfo?,
    @JvmField val mTypedWordValid: Boolean,
    @JvmField val mWillAutoCorrect: Boolean,
    @JvmField val mIsObsoleteSuggestions: Boolean,
    @JvmField val mInputStyle: Int,
    @JvmField val mSequenceNumber: Int
) {

    open val isEmpty: Boolean
        get() = mSuggestedWordInfoList.isEmpty()

    open fun size(): Int = mSuggestedWordInfoList.size

    open val size: Int
        get() = mSuggestedWordInfoList.size

    open val isPrediction: Boolean
        get() = isPrediction(mInputStyle)

    open val isPunctuationSuggestions: Boolean
        get() = false

    open fun getWordCountToShow(): Int {
        return if (isPrediction) size() else size() - 1
    }

    open val typedWordInfo: SuggestedWordInfo?
        get() = mTypedWordInfo

    open fun getWord(index: Int): String = mSuggestedWordInfoList[index].mWord
    open fun getLabel(index: Int): String = mSuggestedWordInfoList[index].mWord
    open fun getInfo(index: Int): SuggestedWordInfo = mSuggestedWordInfoList[index]
    open fun indexOf(suggestedWordInfo: SuggestedWordInfo): Int = mSuggestedWordInfoList.indexOf(suggestedWordInfo)

    open fun getDebugString(pos: Int): String? {
        if (!DebugFlags.DEBUG_ENABLED) return null
        val wordInfo = getInfo(pos)
        val debugString = wordInfo.debugString
        return if (TextUtils.isEmpty(debugString)) null else debugString
    }

    override fun toString(): String {
        return "SuggestedWords: mTypedWordValid=$mTypedWordValid mWillAutoCorrect=$mWillAutoCorrect mInputStyle=$mInputStyle words=${Arrays.toString(mSuggestedWordInfoList.toTypedArray())}"
    }

    open fun getAutoCommitCandidate(): SuggestedWordInfo? {
        if (mSuggestedWordInfoList.isEmpty()) return null
        val candidate = mSuggestedWordInfoList[0]
        return if (candidate.isEligibleForAutoCommit) candidate else null
    }

    open fun getTypedWordInfoOrNull(): SuggestedWordInfo? {
        if (INDEX_OF_TYPED_WORD >= size()) return null
        val info = getInfo(INDEX_OF_TYPED_WORD)
        return if (info.kind == SuggestedWordInfo.KIND_TYPED) info else null
    }

    open class SuggestedWordInfo {
        @JvmField val mWord: String
        @JvmField val mPrevWordsContext: String
        @JvmField val mApplicationSpecifiedCompletionInfo: CompletionInfo?
        @JvmField val mScore: Int
        @JvmField val mKindAndFlags: Int
        @JvmField val mCodePointCount: Int
        @JvmField val mSourceDict: Dictionary
        @JvmField val mIndexOfTouchPointOfSecondWord: Int
        @JvmField val mAutoCommitFirstWordConfidence: Int

        private var mDebugString = ""
        private var mIsEmoji: Boolean? = null

        constructor(
            word: String,
            prevWordsContext: String,
            score: Int,
            kindAndFlags: Int,
            sourceDict: Dictionary,
            indexOfTouchPointOfSecondWord: Int,
            autoCommitFirstWordConfidence: Int
        ) {
            mWord = word
            mPrevWordsContext = prevWordsContext
            mApplicationSpecifiedCompletionInfo = null
            mScore = score
            mKindAndFlags = kindAndFlags
            mSourceDict = sourceDict
            mCodePointCount = StringUtils.codePointCount(mWord)
            mIndexOfTouchPointOfSecondWord = indexOfTouchPointOfSecondWord
            mAutoCommitFirstWordConfidence = autoCommitFirstWordConfidence
        }

        constructor(applicationSpecifiedCompletion: CompletionInfo) {
            mWord = applicationSpecifiedCompletion.text.toString()
            mPrevWordsContext = ""
            mApplicationSpecifiedCompletionInfo = applicationSpecifiedCompletion
            mScore = MAX_SCORE
            mKindAndFlags = KIND_APP_DEFINED
            mSourceDict = Dictionary.DICTIONARY_APPLICATION_DEFINED
            mCodePointCount = StringUtils.codePointCount(mWord)
            mIndexOfTouchPointOfSecondWord = NOT_AN_INDEX
            mAutoCommitFirstWordConfidence = NOT_A_CONFIDENCE
        }

        val word: String get() = mWord

        val prevWordsContext: String get() = mPrevWordsContext
        val score: Int get() = mScore

        val kind: Int
            get() = mKindAndFlags and KIND_MASK_KIND

        fun isKindOf(kind: Int): Boolean = this.kind == kind

        val isEligibleForAutoCommit: Boolean
            get() = isKindOf(KIND_CORRECTION) && NOT_AN_INDEX != mIndexOfTouchPointOfSecondWord

        val isPossiblyOffensive: Boolean
            get() = (mKindAndFlags and KIND_FLAG_POSSIBLY_OFFENSIVE) != 0

        val isExactMatch: Boolean
            get() = (mKindAndFlags and KIND_FLAG_EXACT_MATCH) != 0

        val isExactMatchWithIntentionalOmission: Boolean
            get() = (mKindAndFlags and KIND_FLAG_EXACT_MATCH_WITH_INTENTIONAL_OMISSION) != 0

        val isAppropriateForAutoCorrection: Boolean
            get() = (mKindAndFlags and KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION) != 0 || (isKindOf(KIND_SHORTCUT) && Settings.getValues().mAutoCorrectShortcuts)

        var debugString: String
            get() = mDebugString
            set(value) {
                mDebugString = value
            }

        val isEmoji: Boolean
            get() {
                if (mIsEmoji == null) {
                    mIsEmoji = isEmoji(mWord)
                }
                return mIsEmoji!!
            }

        @Deprecated("Use mSourceDict directly")
        fun getSourceDictionary(): Dictionary = mSourceDict

        fun codePointAt(i: Int): Int = mWord.codePointAt(i)

        override fun toString(): String {
            return if (TextUtils.isEmpty(mDebugString)) mWord else "$mWord ($mDebugString)"
        }

        companion object {
            const val NOT_AN_INDEX = -1
            const val NOT_A_CONFIDENCE = -1
            const val MAX_SCORE = Int.MAX_VALUE

            private const val KIND_MASK_KIND = 0xFF
            const val KIND_TYPED = 0
            const val KIND_CORRECTION = 1
            const val KIND_COMPLETION = 2
            const val KIND_WHITELIST = 3
            const val KIND_BLACKLIST = 4
            const val KIND_HARDCODED = 5
            const val KIND_APP_DEFINED = 6
            const val KIND_SHORTCUT = 7
            const val KIND_PREDICTION = 8
            const val KIND_RESUMED = 9
            const val KIND_OOV_CORRECTION = 10

            const val KIND_FLAG_POSSIBLY_OFFENSIVE = -0x80000000
            const val KIND_FLAG_EXACT_MATCH = 0x40000000
            const val KIND_FLAG_EXACT_MATCH_WITH_INTENTIONAL_OMISSION = 0x20000000
            const val KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION = 0x10000000

            @JvmStatic
            fun removeDupsAndTypedWord(typedWord: String?, candidates: ArrayList<SuggestedWordInfo>): Int {
                if (candidates.isEmpty()) return -1
                var firstOccurrenceOfWord = -1
                if (!typedWord.isNullOrEmpty()) {
                    firstOccurrenceOfWord = removeSuggestedWordInfoFromList(typedWord, candidates, -1)
                }
                var i = 0
                while (i < candidates.size) {
                    removeSuggestedWordInfoFromList(candidates[i].mWord, candidates, i)
                    i++
                }
                return firstOccurrenceOfWord
            }

            private fun removeSuggestedWordInfoFromList(
                word: String,
                candidates: ArrayList<SuggestedWordInfo>,
                startIndexExclusive: Int
            ): Int {
                var firstOccurrenceOfWord = -1
                var i = startIndexExclusive + 1
                while (i < candidates.size) {
                    val previous = candidates[i]
                    if (word == previous.mWord) {
                        if (firstOccurrenceOfWord == -1) {
                            firstOccurrenceOfWord = i
                        }
                        candidates.removeAt(i)
                        i--
                    }
                    i++
                }
                return firstOccurrenceOfWord
            }
        }
    }

    companion object {
        const val INDEX_OF_TYPED_WORD = 0
        const val INDEX_OF_AUTO_CORRECTION = 1
        const val NOT_A_SEQUENCE_NUMBER = -1

        const val INPUT_STYLE_NONE = 0
        const val INPUT_STYLE_TYPING = 1
        const val INPUT_STYLE_UPDATE_BATCH = 2
        const val INPUT_STYLE_TAIL_BATCH = 3
        const val INPUT_STYLE_APPLICATION_SPECIFIED = 4
        const val INPUT_STYLE_RECORRECTION = 5
        const val INPUT_STYLE_PREDICTION = 6
        const val INPUT_STYLE_BEGINNING_OF_SENTENCE_PREDICTION = 7

        const val MAX_SUGGESTIONS = 18

        private val EMPTY_WORD_INFO_LIST = ArrayList<SuggestedWordInfo>(0)

        @JvmField
        val EMPTY: SuggestedWords = SuggestedWords(
            EMPTY_WORD_INFO_LIST, null, null, false,
            false, false, INPUT_STYLE_NONE, NOT_A_SEQUENCE_NUMBER
        )

        @JvmField
        val EMPTY_BATCH: SuggestedWords = SuggestedWords(
            EMPTY_WORD_INFO_LIST, null, null, false,
            false, false, INPUT_STYLE_UPDATE_BATCH, NOT_A_SEQUENCE_NUMBER
        )

        @JvmStatic
        fun getFromApplicationSpecifiedCompletions(infos: Array<CompletionInfo>?): ArrayList<SuggestedWordInfo> {
            val result = ArrayList<SuggestedWordInfo>()
            if (infos != null) {
                for (info in infos) {
                    if (info?.text == null) continue
                    result.add(SuggestedWordInfo(info))
                }
            }
            return result
        }

        @JvmStatic
        fun getEmptyInstance(): SuggestedWords = EMPTY

        @JvmStatic
        fun getEmptyBatchInstance(): SuggestedWords = EMPTY_BATCH

        @JvmStatic
        fun getTypedWordAndPreviousSuggestions(
            typedWordInfo: SuggestedWordInfo,
            previousSuggestions: SuggestedWords
        ): ArrayList<SuggestedWordInfo> {
            val suggestionsList = ArrayList<SuggestedWordInfo>()
            val alreadySeen = HashSet<String>()
            suggestionsList.add(typedWordInfo)
            alreadySeen.add(typedWordInfo.mWord)
            val previousSize = previousSuggestions.size()
            for (index in 1 until previousSize) {
                val prevWordInfo = previousSuggestions.getInfo(index)
                val prevWord = prevWordInfo.mWord
                if (!alreadySeen.contains(prevWord)) {
                    suggestionsList.add(prevWordInfo)
                    alreadySeen.add(prevWord)
                }
            }
            return suggestionsList
        }

        private fun isPrediction(inputStyle: Int): Boolean {
            return inputStyle == INPUT_STYLE_PREDICTION || inputStyle == INPUT_STYLE_BEGINNING_OF_SENTENCE_PREDICTION
        }
    }
}
