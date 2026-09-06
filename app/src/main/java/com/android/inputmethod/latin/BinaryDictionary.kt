/*
 * Copyright (C) 2008 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.android.inputmethod.latin

import android.text.TextUtils
import android.util.SparseArray
import com.android.inputmethod.latin.utils.BinaryDictionaryUtils
import com.android.inputmethod.latin.utils.WordInputEventForPersonalization
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.makedict.DictionaryHeader
import helium314.keyboard.latin.makedict.FormatSpec.DictionaryOptions
import helium314.keyboard.latin.makedict.UnsupportedFormatException
import helium314.keyboard.latin.makedict.WordProperty
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.Locale

/**
 * Implements a static, compacted, binary dictionary of standard words.
 */
class BinaryDictionary : Dictionary {

    companion object {
        private const val TAG = "BinaryDictionary"
        private const val CONFIDENCE_TO_AUTO_COMMIT = 1000000

        const val DICTIONARY_MAX_WORD_LENGTH = 48
        const val MAX_PREV_WORD_COUNT_FOR_N_GRAM = 3

        const val UNIGRAM_COUNT_QUERY = "UNIGRAM_COUNT"
        const val BIGRAM_COUNT_QUERY = "BIGRAM_COUNT"
        const val MAX_UNIGRAM_COUNT_QUERY = "MAX_UNIGRAM_COUNT"
        const val MAX_BIGRAM_COUNT_QUERY = "MAX_BIGRAM_COUNT"

        const val NOT_A_VALID_TIMESTAMP = -1

        private const val FORMAT_WORD_PROPERTY_OUTPUT_FLAG_COUNT = 5
        private const val FORMAT_WORD_PROPERTY_IS_NOT_A_WORD_INDEX = 0
        private const val FORMAT_WORD_PROPERTY_IS_POSSIBLY_OFFENSIVE_INDEX = 1
        private const val FORMAT_WORD_PROPERTY_HAS_NGRAMS_INDEX = 2
        private const val FORMAT_WORD_PROPERTY_HAS_SHORTCUTS_INDEX = 3
        private const val FORMAT_WORD_PROPERTY_IS_BEGINNING_OF_SENTENCE_INDEX = 4

        const val FORMAT_WORD_PROPERTY_OUTPUT_PROBABILITY_INFO_COUNT = 4
        const val FORMAT_WORD_PROPERTY_PROBABILITY_INDEX = 0
        const val FORMAT_WORD_PROPERTY_TIMESTAMP_INDEX = 1
        const val FORMAT_WORD_PROPERTY_LEVEL_INDEX = 2
        const val FORMAT_WORD_PROPERTY_COUNT_INDEX = 3

        const val DICT_FILE_NAME_SUFFIX_FOR_MIGRATION = ".migrate"
        const val DIR_NAME_SUFFIX_FOR_RECORD_MIGRATION = ".migrating"

        init {
            JniUtils.loadNativeLibrary()
        }

        @JvmStatic
        private external fun openNative(sourceDir: String, dictOffset: Long, dictSize: Long, isUpdatable: Boolean): Long
        @JvmStatic
        private external fun createOnMemoryNative(formatVersion: Long, locale: String, attributeKeyStringArray: Array<String>, attributeValueStringArray: Array<String>): Long
        @JvmStatic
        private external fun getHeaderInfoNative(dict: Long, outHeaderSize: IntArray, outFormatVersion: IntArray, outAttributeKeys: ArrayList<IntArray>, outAttributeValues: ArrayList<IntArray>)
        @JvmStatic
        private external fun flushNative(dict: Long, filePath: String): Boolean
        @JvmStatic
        private external fun needsToRunGCNative(dict: Long, mindsBlockByGC: Boolean): Boolean
        @JvmStatic
        private external fun flushWithGCNative(dict: Long, filePath: String): Boolean
        @JvmStatic
        private external fun closeNative(dict: Long)
        @JvmStatic
        private external fun getFormatVersionNative(dict: Long): Int
        @JvmStatic
        private external fun getProbabilityNative(dict: Long, word: IntArray): Int
        @JvmStatic
        private external fun getMaxProbabilityOfExactMatchesNative(dict: Long, word: IntArray): Int
        @JvmStatic
        private external fun getNgramProbabilityNative(dict: Long, prevWordCodePointArrays: Array<IntArray?>, isBeginningOfSentenceArray: BooleanArray, word: IntArray): Int
        @JvmStatic
        private external fun getWordPropertyNative(dict: Long, word: IntArray, isBeginningOfSentence: Boolean, outCodePoints: IntArray, outFlags: BooleanArray, outProbabilityInfo: IntArray, outNgramPrevWordsArray: ArrayList<Array<IntArray>>, outNgramPrevWordIsBeginningOfSentenceArray: ArrayList<BooleanArray>, outNgramTargets: ArrayList<IntArray>, outNgramProbabilityInfo: ArrayList<IntArray>, outShortcutTargets: ArrayList<IntArray>, outShortcutProbabilities: ArrayList<Int>)
        @JvmStatic
        private external fun getNextWordNative(dict: Long, token: Int, outCodePoints: IntArray, outIsBeginningOfSentence: BooleanArray): Int
        @JvmStatic
        private external fun getSuggestionsNative(dict: Long, proximityInfo: Long, traverseSession: Long, xCoordinates: IntArray, yCoordinates: IntArray, times: IntArray, pointerIds: IntArray, inputCodePoints: IntArray, inputSize: Int, suggestOptions: IntArray, prevWordCodePointArrays: Array<IntArray?>, isBeginningOfSentenceArray: BooleanArray, prevWordCount: Int, outputSuggestionCount: IntArray, outputCodePoints: IntArray, outputScores: IntArray, outputIndices: IntArray, outputTypes: IntArray, outputAutoCommitFirstWordConfidence: IntArray, inOutWeightOfLangModelVsSpatialModel: FloatArray)
        @JvmStatic
        private external fun addUnigramEntryNative(dict: Long, word: IntArray, probability: Int, shortcutTarget: IntArray?, shortcutProbability: Int, isBeginningOfSentence: Boolean, isNotAWord: Boolean, isPossiblyOffensive: Boolean, timestamp: Int): Boolean
        @JvmStatic
        private external fun removeUnigramEntryNative(dict: Long, word: IntArray): Boolean
        @JvmStatic
        private external fun addNgramEntryNative(dict: Long, prevWordCodePointArrays: Array<IntArray?>, isBeginningOfSentenceArray: BooleanArray, word: IntArray, probability: Int, timestamp: Int): Boolean
        @JvmStatic
        private external fun removeNgramEntryNative(dict: Long, prevWordCodePointArrays: Array<IntArray?>, isBeginningOfSentenceArray: BooleanArray, word: IntArray): Boolean
        @JvmStatic
        private external fun updateEntriesForWordWithNgramContextNative(dict: Long, prevWordCodePointArrays: Array<IntArray?>, isBeginningOfSentenceArray: BooleanArray, word: IntArray, isValidWord: Boolean, count: Int, timestamp: Int): Boolean
        @JvmStatic
        private external fun updateEntriesForInputEventsNative(dict: Long, inputEvents: Array<WordInputEventForPersonalization>, startIndex: Int): Int
        @JvmStatic
        private external fun getPropertyNative(dict: Long, query: String): String?
        @JvmStatic
        private external fun isCorruptedNative(dict: Long): Boolean
        @JvmStatic
        private external fun migrateNative(dict: Long, dictFilePath: String, newFormatVersion: Long): Boolean
    }

    class GetNextWordPropertyResult(@JvmField val mWordProperty: WordProperty?, @JvmField val mNextToken: Int)

    class WordAndFrequency(@JvmField val mWord: String, @JvmField val mFrequency: Int)

    class GetNextWordAndFrequencyResult(@JvmField val mWordAndFrequency: WordAndFrequency, @JvmField val mNextToken: Int)

    private var mNativeDict: Long = 0
    private val mDictSize: Long
    private val mDictFilePath: String
    private val mUseFullEditDistance: Boolean
    private val mIsUpdatable: Boolean
    private var mHasUpdated: Boolean = false

    private val mDicTraverseSessions = SparseArray<DicTraverseSession>()

    private fun getTraverseSession(traverseSessionId: Int): DicTraverseSession {
        synchronized(mDicTraverseSessions) {
            return getTraverseSessionLocked(traverseSessionId)
        }
    }

    private fun getTraverseSessionLocked(traverseSessionId: Int): DicTraverseSession {
        var traverseSession = mDicTraverseSessions.get(traverseSessionId)
        if (traverseSession == null) {
            traverseSession = DicTraverseSession(mLocale, mNativeDict, mDictSize)
            mDicTraverseSessions.put(traverseSessionId, traverseSession)
        }
        return traverseSession
    }

    constructor(
        filename: String, offset: Long, length: Long, useFullEditDistance: Boolean,
        locale: Locale?, dictType: String, isUpdatable: Boolean
    ) : super(dictType, locale) {
        mDictSize = length
        mDictFilePath = filename
        mIsUpdatable = isUpdatable
        mUseFullEditDistance = useFullEditDistance
        loadDictionary(filename, offset, length, isUpdatable)
    }

    constructor(
        filename: String, useFullEditDistance: Boolean, locale: Locale?, dictType: String,
        formatVersion: Long, attributeMap: Map<String, String>
    ) : super(dictType, locale) {
        mDictSize = 0
        mDictFilePath = filename
        mIsUpdatable = true
        mUseFullEditDistance = useFullEditDistance
        val keyArray = Array(attributeMap.size) { "" }
        val valueArray = Array(attributeMap.size) { "" }
        var index = 0
        for ((key, value) in attributeMap) {
            keyArray[index] = key
            valueArray[index] = value ?: ""
            index++
        }
        mNativeDict = createOnMemoryNative(formatVersion, locale?.toString() ?: "", keyArray, valueArray)
    }

    private fun loadDictionary(path: String, startOffset: Long, length: Long, isUpdatable: Boolean) {
        mHasUpdated = false
        mNativeDict = openNative(path, startOffset, length, isUpdatable)
    }

    val isCorrupted: Boolean
        get() {
            if (!isValidDictionary) return false
            if (!isCorruptedNative(mNativeDict)) return false
            Log.e(TAG, "BinaryDictionary ($mDictFilePath) is corrupted.")
            Log.e(TAG, "locale: $mLocale")
            Log.e(TAG, "dict size: $mDictSize")
            Log.e(TAG, "updatable: $mIsUpdatable")
            return true
        }

    val header: DictionaryHeader?
        @Throws(UnsupportedFormatException::class)
        get() {
            if (mNativeDict == 0L) return null
            val outHeaderSize = IntArray(1)
            val outFormatVersion = IntArray(1)
            val outAttributeKeys = ArrayList<IntArray>()
            val outAttributeValues = ArrayList<IntArray>()
            getHeaderInfoNative(mNativeDict, outHeaderSize, outFormatVersion, outAttributeKeys, outAttributeValues)
            val attributes = HashMap<String, String>()
            for (i in outAttributeKeys.indices) {
                val attributeKey = StringUtils.getStringFromNullTerminatedCodePointArray(outAttributeKeys[i])
                val attributeValue = StringUtils.getStringFromNullTerminatedCodePointArray(outAttributeValues[i])
                attributes[attributeKey] = attributeValue
            }
            return DictionaryHeader(DictionaryOptions(attributes))
        }

    override fun getSuggestions(
        composedData: ComposedData, ngramContext: NgramContext, proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion, sessionId: Int,
        weightForLocale: Float, inOutWeightOfLangModelVsSpatialModel: FloatArray?
    ): ArrayList<SuggestedWordInfo>? {
        if (!isValidDictionary) return null
        val session = getTraverseSession(sessionId)
        session.mInputCodePoints.fill(Constants.NOT_A_CODE)
        ngramContext.outputToArray(session.mPrevWordCodePointArrays, session.mIsBeginningOfSentenceArray)
        val inputPointers = composedData.mInputPointers
        val isGesture = composedData.mIsBatchMode
        val inputSize: Int = if (!isGesture) {
            val size = composedData.copyCodePointsExceptTrailingSingleQuotesAndReturnCodePointCount(session.mInputCodePoints)
            if (size < 0) return null
            size
        } else {
            inputPointers.pointerSize
        }
        session.mNativeSuggestOptions.setUseFullEditDistance(mUseFullEditDistance)
        session.mNativeSuggestOptions.setIsGesture(isGesture)
        if (isGesture) session.mNativeSuggestOptions.setIsSpaceAwareGesture(settingsValuesForSuggestion.mSpaceAwareGesture)
        session.mNativeSuggestOptions.setBlockOffensiveWords(settingsValuesForSuggestion.mBlockPotentiallyOffensive)
        session.mNativeSuggestOptions.setWeightForLocale(weightForLocale)
        
        session.mInputOutputWeightOfLangModelVsSpatialModel[0] = inOutWeightOfLangModelVsSpatialModel?.get(0) 
            ?: Dictionary.NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL

        getSuggestionsNative(
            mNativeDict, proximityInfoHandle, getTraverseSession(sessionId).getSession(),
            inputPointers.xCoordinates, inputPointers.yCoordinates, inputPointers.times,
            inputPointers.pointerIds, session.mInputCodePoints, inputSize,
            session.mNativeSuggestOptions.options, session.mPrevWordCodePointArrays,
            session.mIsBeginningOfSentenceArray, ngramContext.prevWordCount,
            session.mOutputSuggestionCount, session.mOutputCodePoints, session.mOutputScores,
            session.mSpaceIndices, session.mOutputTypes, session.mOutputAutoCommitFirstWordConfidence,
            session.mInputOutputWeightOfLangModelVsSpatialModel
        )

        inOutWeightOfLangModelVsSpatialModel?.let { it[0] = session.mInputOutputWeightOfLangModelVsSpatialModel[0] }

        val count = session.mOutputSuggestionCount[0]
        if (DebugFlags.DEBUG_ENABLED && composedData.mTypedWord.isEmpty()) {
            Log.i("ScoreAudit", "BinaryDict.getSuggestions type=$mDictType outputCount=$count prevWordCount=${ngramContext.prevWordCount} isBOS=${ngramContext.isBeginningOfSentenceContext}")
        }
        val suggestions = ArrayList<SuggestedWordInfo>()
        for (j in 0 until count) {
            val start = j * DICTIONARY_MAX_WORD_LENGTH
            var len = 0
            while (len < DICTIONARY_MAX_WORD_LENGTH && session.mOutputCodePoints[start + len] != 0) {
                ++len
            }
            if (len > 0) {
                suggestions.add(SuggestedWordInfo(
                    String(session.mOutputCodePoints, start, len),
                    "",
                    (session.mOutputScores[j] * weightForLocale).toInt(),
                    session.mOutputTypes[j],
                    this,
                    session.mSpaceIndices[j],
                    session.mOutputAutoCommitFirstWordConfidence[0]
                ))
            }
        }
        return suggestions
    }

    val isValidDictionary: Boolean
        get() = mNativeDict != 0L

    val formatVersion: Int
        get() {
            if (!isValidDictionary) return 0
            return try {
                getFormatVersionNative(mNativeDict)
            } catch (e: Throwable) {
                Log.e(TAG, "getFormatVersion failed", e)
                0
            }
        }

    override fun isInDictionary(word: String): Boolean {
        return getFrequency(word) != NOT_A_PROBABILITY
    }

    override fun getFrequency(word: String): Int {
        if (TextUtils.isEmpty(word) || !isValidDictionary) return NOT_A_PROBABILITY
        return try {
            getProbabilityNative(mNativeDict, StringUtils.toCodePointArray(word))
        } catch (e: Throwable) {
            Log.e(TAG, "getFrequency failed", e)
            NOT_A_PROBABILITY
        }
    }

    override fun getMaxFrequencyOfExactMatches(word: String): Int {
        if (TextUtils.isEmpty(word) || !isValidDictionary) return NOT_A_PROBABILITY
        return try {
            getMaxProbabilityOfExactMatchesNative(mNativeDict, StringUtils.toCodePointArray(word))
        } catch (e: Throwable) {
            Log.e(TAG, "getMaxFrequencyOfExactMatches failed", e)
            NOT_A_PROBABILITY
        }
    }

    fun isValidNgram(ngramContext: NgramContext, word: String): Boolean {
        return getNgramProbability(ngramContext, word) != NOT_A_PROBABILITY
    }

    fun getNgramProbability(ngramContext: NgramContext, word: String): Int {
        if (!ngramContext.isValid || TextUtils.isEmpty(word) || !isValidDictionary) return NOT_A_PROBABILITY
        return try {
            val prevWordCodePointArrays = arrayOfNulls<IntArray>(ngramContext.prevWordCount)
            val isBeginningOfSentenceArray = BooleanArray(ngramContext.prevWordCount)
            ngramContext.outputToArray(prevWordCodePointArrays, isBeginningOfSentenceArray)
            getNgramProbabilityNative(mNativeDict, prevWordCodePointArrays, isBeginningOfSentenceArray, StringUtils.toCodePointArray(word))
        } catch (e: Throwable) {
            Log.e(TAG, "getNgramProbability failed", e)
            NOT_A_PROBABILITY
        }
    }

    override fun getWordProperty(word: String, isBeginningOfSentence: Boolean): WordProperty? {
        if (!isValidDictionary) return null
        return try {
            val codePoints = StringUtils.toCodePointArray(word)
            val outCodePoints = IntArray(DICTIONARY_MAX_WORD_LENGTH)
            val outFlags = BooleanArray(FORMAT_WORD_PROPERTY_OUTPUT_FLAG_COUNT)
            val outProbabilityInfo = IntArray(FORMAT_WORD_PROPERTY_OUTPUT_PROBABILITY_INFO_COUNT)
            val outNgramPrevWordsArray = ArrayList<Array<IntArray>>()
            val outNgramPrevWordIsBeginningOfSentenceArray = ArrayList<BooleanArray>()
            val outNgramTargets = ArrayList<IntArray>()
            val outNgramProbabilityInfo = ArrayList<IntArray>()
            val outShortcutTargets = ArrayList<IntArray>()
            val outShortcutProbabilities = ArrayList<Int>()
            getWordPropertyNative(mNativeDict, codePoints, isBeginningOfSentence, outCodePoints, outFlags, outProbabilityInfo, outNgramPrevWordsArray, outNgramPrevWordIsBeginningOfSentenceArray, outNgramTargets, outNgramProbabilityInfo, outShortcutTargets, outShortcutProbabilities)
            WordProperty(codePoints, outFlags[FORMAT_WORD_PROPERTY_IS_NOT_A_WORD_INDEX], outFlags[FORMAT_WORD_PROPERTY_IS_POSSIBLY_OFFENSIVE_INDEX], outFlags[FORMAT_WORD_PROPERTY_HAS_NGRAMS_INDEX], outFlags[FORMAT_WORD_PROPERTY_HAS_SHORTCUTS_INDEX], outFlags[FORMAT_WORD_PROPERTY_IS_BEGINNING_OF_SENTENCE_INDEX], outProbabilityInfo, outNgramPrevWordsArray, outNgramPrevWordIsBeginningOfSentenceArray, outNgramTargets, outNgramProbabilityInfo, outShortcutTargets, outShortcutProbabilities)
        } catch (e: Throwable) {
            Log.e(TAG, "getWordProperty failed", e)
            null
        }
    }

    fun getNextWordProperty(token: Int): GetNextWordPropertyResult {
        val codePoints = IntArray(DICTIONARY_MAX_WORD_LENGTH)
        val isBeginningOfSentence = BooleanArray(1)
        val nextToken = getNextWordNative(mNativeDict, token, codePoints, isBeginningOfSentence)
        val word = StringUtils.getStringFromNullTerminatedCodePointArray(codePoints)
        return GetNextWordPropertyResult(getWordProperty(word, isBeginningOfSentence[0]), nextToken)
    }

    fun getNextWordAndFrequency(token: Int): GetNextWordAndFrequencyResult {
        val codePoints = IntArray(DICTIONARY_MAX_WORD_LENGTH)
        val isBeginningOfSentence = BooleanArray(1)
        val nextToken = getNextWordNative(mNativeDict, token, codePoints, isBeginningOfSentence)
        val word = StringUtils.getStringFromNullTerminatedCodePointArray(codePoints)
        val probability = getFrequency(word)
        return GetNextWordAndFrequencyResult(WordAndFrequency(word, probability), nextToken)
    }

    fun addUnigramEntry(word: String?, probability: Int, shortcutTarget: String?, shortcutProbability: Int, isBeginningOfSentence: Boolean, isNotAWord: Boolean, isPossiblyOffensive: Boolean, timestamp: Int): Boolean {
        if (word == null || (word.isEmpty() && !isBeginningOfSentence)) return false
        val codePoints = StringUtils.toCodePointArray(word)
        val shortcutTargetCodePoints = shortcutTarget?.let { StringUtils.toCodePointArray(it) }
        if (!addUnigramEntryNative(mNativeDict, codePoints, probability, shortcutTargetCodePoints, shortcutProbability, isBeginningOfSentence, isNotAWord, isPossiblyOffensive, timestamp)) return false
        mHasUpdated = true
        return true
    }

    fun removeUnigramEntry(word: String): Boolean {
        if (TextUtils.isEmpty(word)) return false
        if (!removeUnigramEntryNative(mNativeDict, StringUtils.toCodePointArray(word))) return false
        mHasUpdated = true
        return true
    }

    fun addNgramEntry(ngramContext: NgramContext, word: String, probability: Int, timestamp: Int): Boolean {
        if (!ngramContext.isValid || TextUtils.isEmpty(word)) return false
        val prevWordCodePointArrays = arrayOfNulls<IntArray>(ngramContext.prevWordCount)
        val isBeginningOfSentenceArray = BooleanArray(ngramContext.prevWordCount)
        ngramContext.outputToArray(prevWordCodePointArrays, isBeginningOfSentenceArray)
        if (!addNgramEntryNative(mNativeDict, prevWordCodePointArrays, isBeginningOfSentenceArray, StringUtils.toCodePointArray(word), probability, timestamp)) return false
        mHasUpdated = true
        return true
    }

    fun updateEntriesForWordWithNgramContext(ngramContext: NgramContext, word: String, isValidWord: Boolean, count: Int, timestamp: Int): Boolean {
        if (TextUtils.isEmpty(word)) return false
        val prevWordCodePointArrays = arrayOfNulls<IntArray>(ngramContext.prevWordCount)
        val isBeginningOfSentenceArray = BooleanArray(ngramContext.prevWordCount)
        ngramContext.outputToArray(prevWordCodePointArrays, isBeginningOfSentenceArray)
        if (!updateEntriesForWordWithNgramContextNative(mNativeDict, prevWordCodePointArrays, isBeginningOfSentenceArray, StringUtils.toCodePointArray(word), isValidWord, count, timestamp)) return false
        mHasUpdated = true
        return true
    }

    fun updateEntriesForInputEvents(inputEvents: Array<WordInputEventForPersonalization>) {
        if (!isValidDictionary) return
        var processedEventCount = 0
        while (processedEventCount < inputEvents.size) {
            if (needsToRunGC(true)) flushWithGC()
            processedEventCount = updateEntriesForInputEventsNative(mNativeDict, inputEvents, processedEventCount)
            mHasUpdated = true
            if (processedEventCount <= 0) return
        }
    }

    private fun reopen() {
        close()
        val dictFile = File(mDictFilePath)
        loadDictionary(dictFile.absolutePath, 0, dictFile.length(), mIsUpdatable)
    }

    fun flush(): Boolean {
        if (!isValidDictionary) return false
        if (mHasUpdated) {
            if (!flushNative(mNativeDict, mDictFilePath)) return false
            reopen()
        }
        return true
    }

    fun flushWithGCIfHasUpdated(): Boolean {
        if (mHasUpdated) return flushWithGC()
        return true
    }

    fun flushWithGC(): Boolean {
        if (!isValidDictionary) return false
        if (!flushWithGCNative(mNativeDict, mDictFilePath)) return false
        reopen()
        return true
    }

    fun needsToRunGC(mindsBlockByGC: Boolean): Boolean {
        if (!isValidDictionary) return false
        return needsToRunGCNative(mNativeDict, mindsBlockByGC)
    }

    fun migrateTo(newFormatVersion: Int): Boolean {
        if (!isValidDictionary) return false
        val isMigratingDir = File(mDictFilePath + DIR_NAME_SUFFIX_FOR_RECORD_MIGRATION)
        if (isMigratingDir.exists()) {
            isMigratingDir.delete()
            Log.e(TAG, "Previous migration attempt failed probably due to a crash. Giving up using the old dictionary ($mDictFilePath).")
            return false
        }
        if (!isMigratingDir.mkdir()) {
            Log.e(TAG, "Cannot create a dir (${isMigratingDir.absolutePath}) to record migration.")
            return false
        }
        return try {
            val tmpDictFilePath = mDictFilePath + DICT_FILE_NAME_SUFFIX_FOR_MIGRATION
            if (!migrateNative(mNativeDict, tmpDictFilePath, newFormatVersion.toLong())) return false
            close()
            val dictFile = File(mDictFilePath)
            val tmpDictFile = File(tmpDictFilePath)
            if (!FileUtils.deleteRecursively(dictFile)) return false
            if (!BinaryDictionaryUtils.renameDict(tmpDictFile, dictFile)) return false
            loadDictionary(dictFile.absolutePath, 0, dictFile.length(), mIsUpdatable)
            true
        } finally {
            isMigratingDir.delete()
        }
    }

    fun getPropertyForGettingStats(query: String): String {
        if (!isValidDictionary) return ""
        return getPropertyNative(mNativeDict, query) ?: ""
    }

    override fun shouldAutoCommit(candidate: SuggestedWordInfo): Boolean {
        return candidate.mAutoCommitFirstWordConfidence > CONFIDENCE_TO_AUTO_COMMIT
    }

    override fun close() {
        synchronized(mDicTraverseSessions) {
            closeDicTraverseSessionsLocked()
        }
        synchronized(this) {
            closeInternalLocked()
        }
    }

    private fun closeDicTraverseSessionsLocked() {
        val sessionsSize = mDicTraverseSessions.size()
        for (index in 0 until sessionsSize) {
            mDicTraverseSessions.valueAt(index)?.close()
        }
        mDicTraverseSessions.clear()
    }

    private fun closeInternalLocked() {
        if (mNativeDict != 0L) {
            closeNative(mNativeDict)
            mNativeDict = 0L
        }
    }

    @Throws(Throwable::class)
    protected fun finalize() {
        synchronized(this) {
            closeInternalLocked()
        }
    }
}
