/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.dictionary

import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.makedict.WordProperty
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * This class provides binary dictionary reading operations with locking. An instance of this class
 * can be used by multiple threads. Note that different session IDs must be used when multiple
 * threads get suggestions using this class.
 */
class ReadOnlyBinaryDictionary(
    filename: String,
    offset: Long,
    length: Long,
    useFullEditDistance: Boolean,
    locale: Locale?,
    dictType: String
) : Dictionary(dictType, locale) {

    /**
     * A lock for accessing binary dictionary. Only closing binary dictionary is the operation
     * that change the state of dictionary.
     */
    private val mLock = ReentrantReadWriteLock()
    private val mIterationLock = Any()

    private val mBinaryDictionary: BinaryDictionary = BinaryDictionary(
        filename, offset, length, useFullEditDistance,
        locale, dictType, false /* isUpdatable */
    )

    val isValidDictionary: Boolean
        get() = mBinaryDictionary.isValidDictionary

    override fun getSuggestions(
        composedData: ComposedData,
        ngramContext: NgramContext,
        proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion,
        sessionId: Int,
        weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?
    ): ArrayList<SuggestedWordInfo>? {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.getSuggestions(
                    composedData, ngramContext, proximityInfoHandle,
                    settingsValuesForSuggestion, sessionId, weightForLocale,
                    inOutWeightOfLangModelVsSpatialModel
                )
            } finally {
                mLock.readLock().unlock()
            }
        }
        return null
    }

    override fun isInDictionary(word: String): Boolean {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.isInDictionary(word)
            } finally {
                mLock.readLock().unlock()
            }
        }
        return false
    }

    override fun shouldAutoCommit(candidate: SuggestedWordInfo): Boolean {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.shouldAutoCommit(candidate)
            } finally {
                mLock.readLock().unlock()
            }
        }
        return false
    }

    override fun getFrequency(word: String): Int {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.getFrequency(word)
            } finally {
                mLock.readLock().unlock()
            }
        }
        return NOT_A_PROBABILITY
    }

    override fun getMaxFrequencyOfExactMatches(word: String): Int {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.getMaxFrequencyOfExactMatches(word)
            } finally {
                mLock.readLock().unlock()
            }
        }
        return NOT_A_PROBABILITY
    }

    override fun getAllWordsWithFrequency(): Map<String, Int> {
        synchronized(mIterationLock) {
            val words = HashMap<String, Int>()
            var token = 0
            var count = 0
            do {
                if (!mLock.readLock().tryLock()) {
                    try {
                        Thread.sleep(5)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                    continue
                }
                try {
                    if (!mBinaryDictionary.isValidDictionary) {
                        break
                    }
                    val result = mBinaryDictionary.getNextWordAndFrequency(token)
                    if (result.mWordAndFrequency == null) break
                    val word = result.mWordAndFrequency.mWord
                    val freq = result.mWordAndFrequency.mFrequency
                    if (!word.isNullOrEmpty() && freq >= 0) {
                        words[word] = freq
                    }
                    token = result.mNextToken
                } finally {
                    mLock.readLock().unlock()
                }

                count++
                if (count % 200 == 0) {
                    Thread.yield()
                }
                if (count % 2000 == 0) {
                    try {
                        Thread.sleep(1)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            } while (token != 0)
            return words
        }
    }

    override fun forEachWord(consumer: java.util.function.BiConsumer<String, Int>) {
        synchronized(mIterationLock) {
            var token = 0
            var count = 0
            do {
                if (!mLock.readLock().tryLock()) {
                    try {
                        Thread.sleep(2)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                    continue
                }
                try {
                    if (!mBinaryDictionary.isValidDictionary) {
                        break
                    }
                    val result = mBinaryDictionary.getNextWordAndFrequency(token)
                    if (result.mWordAndFrequency == null) break
                    val word = result.mWordAndFrequency.mWord
                    val freq = result.mWordAndFrequency.mFrequency
                    if (!word.isNullOrEmpty() && freq >= 0) {
                        consumer.accept(word, freq)
                    }
                    token = result.mNextToken
                } finally {
                    mLock.readLock().unlock()
                }

                count++
                if (count % 200 == 0) {
                    Thread.yield()
                }
            } while (token != 0)
        }
    }

    override fun getWordProperty(word: String, isBeginningOfSentence: Boolean): WordProperty? {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.getWordProperty(word, isBeginningOfSentence)
            } finally {
                mLock.readLock().unlock()
            }
        }
        return null
    }

    override fun close() {
        try {
            if (mLock.writeLock().tryLock(300, TimeUnit.MILLISECONDS)) {
                try {
                    mBinaryDictionary.close()
                } finally {
                    mLock.writeLock().unlock()
                }
            } else {
                mBinaryDictionary.close()
            }
        } catch (e: InterruptedException) {
            mBinaryDictionary.close()
        }
    }
}
