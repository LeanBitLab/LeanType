/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.dictionary

import android.content.Context
import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.define.DecoderSpecificConstants
import helium314.keyboard.latin.makedict.DictionaryHeader
import helium314.keyboard.latin.makedict.FormatSpec
import helium314.keyboard.latin.makedict.UnsupportedFormatException
import helium314.keyboard.latin.makedict.WordProperty
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.utils.AsyncResultHolder
import helium314.keyboard.latin.utils.CombinedFormatUtils
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Abstract base class for an expandable dictionary that can be created and
 * updated dynamically during runtime. When updated it automatically generates a new binary
 * dictionary to handle future queries in native code. This binary dictionary is written to internal
 * storage.
 *
 * A class that extends this abstract class must have a static factory method named
 * getDictionary(Context context, Locale locale, File dictFile, String dictNamePrefix)
 */
abstract class ExpandableBinaryDictionary(
    val mContext: Context,
    dictName: String,
    locale: Locale?,
    dictType: String,
    dictFile: File?
) : Dictionary(dictType, locale) {

    private val mDictName: String = dictName
    private val mDictFile: File = getDictFile(mContext, dictName, dictFile)
    private var mBinaryDictionary: BinaryDictionary? = null
    private val mIsReloading = AtomicBoolean()
    private var mNeedsToRecreate = false
    private val mLock = ReentrantReadWriteLock()
    private val mIterationLock = Any()

    protected abstract fun loadInitialContentsLocked()

    internal fun isValidDictionaryLocked(): Boolean {
        return mBinaryDictionary?.isValidDictionary ?: false
    }

    internal fun getBinaryDictionary(): BinaryDictionary? {
        return mBinaryDictionary
    }

    override fun getFrequency(word: String): Int {
        if (mLock.readLock().tryLock()) {
            try {
                val dict = mBinaryDictionary ?: return NOT_A_PROBABILITY
                return dict.getFrequency(word)
            } finally {
                mLock.readLock().unlock()
            }
        }
        return NOT_A_PROBABILITY
    }

    internal fun closeBinaryDictionary() {
        mBinaryDictionary?.close()
        mBinaryDictionary = null
    }

    override fun close() {
        asyncExecuteTaskWithWriteLock(Runnable { closeBinaryDictionary() })
    }

    protected open fun getHeaderAttributeMap(): Map<String, String> {
        val attributeMap = HashMap<String, String>()
        attributeMap[DictionaryHeader.DICTIONARY_ID_KEY] = mDictName
        attributeMap[DictionaryHeader.DICTIONARY_LOCALE_KEY] = mLocale?.toString() ?: ""
        attributeMap[DictionaryHeader.DICTIONARY_VERSION_KEY] =
            TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toString()
        return attributeMap
    }

    private fun removeBinaryDictionary() {
        asyncExecuteTaskWithWriteLock(Runnable { removeBinaryDictionaryLocked() })
    }

    internal fun removeBinaryDictionaryLocked() {
        closeBinaryDictionary()
        if (mDictFile.exists() && !FileUtils.deleteRecursively(mDictFile)) {
            Log.e(TAG, "Can't remove a file: " + mDictFile.name)
        }
    }

    private fun openBinaryDictionaryLocked() {
        mBinaryDictionary = BinaryDictionary(
            mDictFile.absolutePath, 0L /* offset */, mDictFile.length(),
            true /* useFullEditDistance */, mLocale, mDictType, true /* isUpdatable */
        )
    }

    internal fun createOnMemoryBinaryDictionaryLocked() {
        mBinaryDictionary = BinaryDictionary(
            mDictFile.absolutePath, true /* useFullEditDistance */, mLocale, mDictType,
            DICTIONARY_FORMAT_VERSION.toLong(), getHeaderAttributeMap()
        )
    }

    fun clear() {
        asyncExecuteTaskWithWriteLock(Runnable {
            removeBinaryDictionaryLocked()
            createOnMemoryBinaryDictionaryLocked()
        })
    }

    fun runGCIfRequired(mindsBlockByGC: Boolean) {
        asyncExecuteTaskWithWriteLock(Runnable {
            if (getBinaryDictionary() == null) return@Runnable
            runGCIfRequiredLocked(mindsBlockByGC)
        })
    }

    protected open fun runGCIfRequiredLocked(mindsBlockByGC: Boolean) {
        val dict = mBinaryDictionary ?: return
        if (dict.needsToRunGC(mindsBlockByGC)) {
            dict.flushWithGC()
        }
    }

    private fun updateDictionaryWithWriteLock(updateTask: Runnable) {
        reloadDictionaryIfRequired()
        asyncExecuteTaskWithWriteLock(Runnable {
            if (getBinaryDictionary() == null) return@Runnable
            runGCIfRequiredLocked(true)
            updateTask.run()
        })
    }

    fun addUnigramEntry(
        word: String, frequency: Int,
        shortcutTarget: String?, shortcutFreq: Int, isNotAWord: Boolean,
        isPossiblyOffensive: Boolean, timestamp: Int
    ) {
        updateDictionaryWithWriteLock(Runnable {
            addUnigramLocked(word, frequency, shortcutTarget, shortcutFreq, isNotAWord, isPossiblyOffensive, timestamp)
        })
    }

    protected open fun addUnigramLocked(
        word: String, frequency: Int,
        shortcutTarget: String?, shortcutFreq: Int, isNotAWord: Boolean,
        isPossiblyOffensive: Boolean, timestamp: Int
    ) {
        val dict = mBinaryDictionary ?: return
        if (!dict.addUnigramEntry(
                word, frequency, shortcutTarget, shortcutFreq,
                false /* isBeginningOfSentence */, isNotAWord, isPossiblyOffensive, timestamp
            )
        ) {
            Log.e(TAG, "Cannot add unigram entry. word: $word")
        }
    }

    fun removeUnigramEntryDynamically(word: String) {
        reloadDictionaryIfRequired()
        asyncExecuteTaskWithWriteLock(Runnable {
            val binaryDictionary = getBinaryDictionary() ?: return@Runnable
            runGCIfRequiredLocked(true)
            if (!binaryDictionary.removeUnigramEntry(word)) {
                if (DEBUG) {
                    Log.i(TAG, "Cannot remove unigram entry: $word")
                }
            }
        })
    }

    fun addNgramEntry(ngramContext: NgramContext, word: String, frequency: Int, timestamp: Int) {
        reloadDictionaryIfRequired()
        asyncExecuteTaskWithWriteLock(Runnable {
            if (getBinaryDictionary() == null) return@Runnable
            runGCIfRequiredLocked(true)
            addNgramEntryLocked(ngramContext, word, frequency, timestamp)
        })
    }

    protected open fun addNgramEntryLocked(ngramContext: NgramContext, word: String, frequency: Int, timestamp: Int) {
        val dict = mBinaryDictionary ?: return
        if (!dict.addNgramEntry(ngramContext, word, frequency, timestamp)) {
            if (DEBUG) {
                Log.i(TAG, "Cannot add n-gram entry.")
                Log.i(TAG, "  NgramContext: $ngramContext, word: $word")
            }
        }
    }

    fun updateEntriesForWord(
        ngramContext: NgramContext,
        word: String, isValidWord: Boolean, count: Int, timestamp: Int
    ) {
        updateDictionaryWithWriteLock(Runnable {
            val binaryDictionary = getBinaryDictionary() ?: return@Runnable
            if (!binaryDictionary.updateEntriesForWordWithNgramContext(ngramContext, word, isValidWord, count, timestamp)) {
                if (DEBUG) {
                    Log.e(TAG, "Cannot update counter. word: $word context: $ngramContext")
                }
            }
        })
    }

    override fun getSuggestions(
        composedData: ComposedData,
        ngramContext: NgramContext,
        proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion,
        sessionId: Int,
        weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?
    ): ArrayList<SuggestedWordInfo>? {
        reloadDictionaryIfRequired()
        var lockAcquired = false
        try {
            lockAcquired = mLock.readLock().tryLock(
                TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS.toLong(), TimeUnit.MILLISECONDS
            )
            if (lockAcquired) {
                val dict = mBinaryDictionary ?: return null
                val suggestions = dict.getSuggestions(
                    composedData, ngramContext, proximityInfoHandle,
                    settingsValuesForSuggestion, sessionId, weightForLocale,
                    inOutWeightOfLangModelVsSpatialModel
                )
                if (dict.isCorrupted) {
                    Log.i(TAG, "Dictionary ($mDictName) is corrupted. Remove and regenerate it.")
                    removeBinaryDictionary()
                }
                return suggestions
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted tryLock() in getSuggestionsWithSessionId().", e)
        } finally {
            if (lockAcquired) {
                mLock.readLock().unlock()
            }
        }
        return null
    }

    override fun isInDictionary(word: String): Boolean {
        reloadDictionaryIfRequired()
        var lockAcquired = false
        try {
            lockAcquired = mLock.readLock().tryLock(
                TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS.toLong(), TimeUnit.MILLISECONDS
            )
            if (lockAcquired) {
                if (mBinaryDictionary == null) return false
                return isInDictionaryLocked(word)
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted tryLock() in isInDictionary().", e)
        } finally {
            if (lockAcquired) {
                mLock.readLock().unlock()
            }
        }
        return false
    }

    protected open fun isInDictionaryLocked(word: String): Boolean {
        return mBinaryDictionary?.isInDictionary(word) ?: false
    }

    override fun getAllWordsWithFrequency(): Map<String, Int> {
        synchronized(mIterationLock) {
            val words = HashMap<String, Int>()
            var lockAcquired = false
            try {
                lockAcquired = mLock.readLock().tryLock(
                    TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS.toLong(), TimeUnit.MILLISECONDS
                )
                if (lockAcquired) {
                    val dict = mBinaryDictionary
                    if (dict == null || !dict.isValidDictionary) return words
                    var token = 0
                    do {
                        val result = dict.getNextWordAndFrequency(token)
                        val wordAndFreq = result.mWordAndFrequency ?: break
                        val word = wordAndFreq.mWord
                        val freq = wordAndFreq.mFrequency
                        if (!word.isNullOrEmpty() && freq >= 0) {
                            words[word] = freq
                        }
                        token = result.mNextToken
                    } while (token != 0)
                }
            } catch (e: InterruptedException) {
                Log.e(TAG, "Interrupted tryLock() in getAllWordsWithFrequency().", e)
            } finally {
                if (lockAcquired) {
                    mLock.readLock().unlock()
                }
            }
            return words
        }
    }

    override fun forEachWord(consumer: java.util.function.BiConsumer<String, Int>) {
        synchronized(mIterationLock) {
            var lockAcquired = false
            try {
                lockAcquired = mLock.readLock().tryLock(
                    TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS.toLong(), TimeUnit.MILLISECONDS
                )
                if (lockAcquired) {
                    val dict = mBinaryDictionary
                    if (dict == null || !dict.isValidDictionary) return
                    var token = 0
                    do {
                        val result = dict.getNextWordAndFrequency(token)
                        val wordAndFreq = result.mWordAndFrequency ?: break
                        val word = wordAndFreq.mWord
                        val freq = wordAndFreq.mFrequency
                        if (!word.isNullOrEmpty() && freq >= 0) {
                            consumer.accept(word, freq)
                        }
                        token = result.mNextToken
                    } while (token != 0)
                }
            } catch (e: InterruptedException) {
                Log.e(TAG, "Interrupted tryLock() in forEachWord().", e)
            } finally {
                if (lockAcquired) {
                    mLock.readLock().unlock()
                }
            }
        }
    }

    override fun getMaxFrequencyOfExactMatches(word: String): Int {
        reloadDictionaryIfRequired()
        var lockAcquired = false
        try {
            lockAcquired = mLock.readLock().tryLock(
                TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS.toLong(), TimeUnit.MILLISECONDS
            )
            if (lockAcquired) {
                val dict = mBinaryDictionary ?: return NOT_A_PROBABILITY
                return dict.getMaxFrequencyOfExactMatches(word)
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted tryLock() in getMaxFrequencyOfExactMatches().", e)
        } finally {
            if (lockAcquired) {
                mLock.readLock().unlock()
            }
        }
        return NOT_A_PROBABILITY
    }

    internal fun loadBinaryDictionaryLocked() {
        if (DBG_STRESS_TEST) {
            try {
                Log.w(TAG, "Start stress in loading: $mDictName")
                Thread.sleep(15000)
                Log.w(TAG, "End stress in loading")
            } catch (e: InterruptedException) {
                Log.w(TAG, "Interrupted while loading: $mDictName", e)
            }
        }
        val oldBinaryDictionary = mBinaryDictionary
        openBinaryDictionaryLocked()
        oldBinaryDictionary?.close()
        
        val dict = mBinaryDictionary
        if (dict != null && dict.isValidDictionary && needsToMigrateDictionary(dict.formatVersion)) {
            if (!dict.migrateTo(DICTIONARY_FORMAT_VERSION)) {
                Log.e(TAG, "Dictionary migration failed: $mDictName")
                removeBinaryDictionaryLocked()
            }
        }
    }

    internal fun createNewDictionaryLocked() {
        removeBinaryDictionaryLocked()
        createOnMemoryBinaryDictionaryLocked()
        loadInitialContentsLocked()
        mBinaryDictionary?.flushWithGCIfHasUpdated()
    }

    protected fun setNeedsToRecreate() {
        mNeedsToRecreate = true
    }

    internal fun clearNeedsToRecreate() {
        mNeedsToRecreate = false
    }

    internal fun isNeededToRecreate(): Boolean {
        return mNeedsToRecreate
    }

    fun reloadDictionaryIfRequired() {
        if (!isReloadRequired()) return
        asyncReloadDictionary()
    }

    private fun isReloadRequired(): Boolean {
        return mBinaryDictionary == null || mNeedsToRecreate
    }

    private fun asyncReloadDictionary() {
        val isReloading = mIsReloading
        if (!isReloading.compareAndSet(false, true)) return
        
        val dictFile = mDictFile
        asyncExecuteTaskWithWriteLock(Runnable {
            try {
                if (!dictFile.exists() || isNeededToRecreate()) {
                    createNewDictionaryLocked()
                } else if (getBinaryDictionary() == null) {
                    loadBinaryDictionaryLocked()
                    val binaryDictionary = getBinaryDictionary()
                    if (binaryDictionary != null && !(isValidDictionaryLocked()
                                && matchesExpectedBinaryDictFormatVersionForThisType(binaryDictionary.formatVersion))
                    ) {
                        createNewDictionaryLocked()
                    }
                }
                clearNeedsToRecreate()
            } finally {
                isReloading.set(false)
            }
        })
    }

    override fun onFinishInput() {
        asyncExecuteTaskWithWriteLock(Runnable {
            val binaryDictionary = getBinaryDictionary() ?: return@Runnable
            if (binaryDictionary.needsToRunGC(false)) {
                binaryDictionary.flushWithGCIfHasUpdated()
            } else {
                binaryDictionary.flush()
            }
        })
    }

    val dictionaryStats: DictionaryStats?
        get() {
            reloadDictionaryIfRequired()
            val dictName = mDictName
            val dictFile = mDictFile
            val result = AsyncResultHolder<DictionaryStats>("DictionaryStats")
            asyncExecuteTaskWithLock(mLock.readLock(), Runnable {
                val loc = mLocale ?: Locale.ROOT
                result.set(DictionaryStats(loc, dictName, dictName, dictFile, 0))
            })
            return result.get(null, TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS.toLong())
        }

    fun dumpAllWordsForDebug() {
        reloadDictionaryIfRequired()
        val tag = TAG
        val dictName = mDictName
        asyncExecuteTaskWithLock(mLock.readLock(), Runnable {
            synchronized(mIterationLock) {
                Log.d(tag, "Dump dictionary: $dictName for $mLocale")
                val binaryDictionary = getBinaryDictionary() ?: return@Runnable
                try {
                    val header = binaryDictionary.header
                    Log.d(tag, "Format version: ${binaryDictionary.formatVersion}")
                    if (header != null) {
                        Log.d(tag, CombinedFormatUtils.formatAttributeMap(header.mDictionaryOptions.mAttributes))
                    }
                } catch (e: UnsupportedFormatException) {
                    Log.d(tag, "Cannot fetch header information.", e)
                }
                var token = 0
                do {
                    val result = binaryDictionary.getNextWordProperty(token)
                    val wordProperty = result.mWordProperty
                    if (wordProperty == null) {
                        Log.d(tag, " dictionary is empty.")
                        break
                    }
                    Log.d(tag, wordProperty.toString())
                    token = result.mNextToken
                } while (token != 0)
            }
        })
    }

    fun getWordPropertiesForSyncing(): Array<WordProperty> {
        reloadDictionaryIfRequired()
        val result = AsyncResultHolder<Array<WordProperty>>("WordPropertiesForSync")
        asyncExecuteTaskWithLock(mLock.readLock(), Runnable {
            synchronized(mIterationLock) {
                val wordPropertyList = ArrayList<WordProperty>()
                val binaryDictionary = getBinaryDictionary() ?: return@Runnable
                var token = 0
                do {
                    val nextWordPropertyResult = binaryDictionary.getNextWordProperty(token)
                    val wordProperty = nextWordPropertyResult.mWordProperty ?: break
                    wordPropertyList.add(wordProperty)
                    token = nextWordPropertyResult.mNextToken
                } while (token != 0)
                result.set(wordPropertyList.toTypedArray())
            }
        })
        return result.get(DEFAULT_WORD_PROPERTIES_FOR_SYNC, TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS.toLong()) ?: DEFAULT_WORD_PROPERTIES_FOR_SYNC
    }

    companion object {
        private const val DEBUG = false
        private const val TAG = "ExpandableBinaryDictionary"
        private const val DBG_STRESS_TEST = false
        private const val TIMEOUT_FOR_READ_OPS_IN_MILLISECONDS = 100
        private const val DICTIONARY_FORMAT_VERSION = FormatSpec.VERSION4
        private val DEFAULT_WORD_PROPERTIES_FOR_SYNC = emptyArray<WordProperty>()

        const val MAX_WORD_LENGTH = DecoderSpecificConstants.DICTIONARY_MAX_WORD_LENGTH
        const val DICT_FILE_EXTENSION = ".dict"

        fun matchesExpectedBinaryDictFormatVersionForThisType(formatVersion: Int): Boolean {
            return formatVersion == FormatSpec.VERSION4
        }

        private fun needsToMigrateDictionary(formatVersion: Int): Boolean {
            return formatVersion == FormatSpec.VERSION402
        }

        fun getDictFile(context: Context, dictName: String, dictFile: File?): File {
            return dictFile ?: File(context.filesDir, dictName + DICT_FILE_EXTENSION)
        }

        fun getDictName(name: String, locale: Locale?, dictFile: File?): String {
            return dictFile?.name ?: (name + "." + (locale?.toLanguageTag() ?: ""))
        }

        private fun asyncExecuteTaskWithLock(lock: Lock, task: Runnable) {
            ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
                lock.lock()
                try {
                    task.run()
                } finally {
                    lock.unlock()
                }
            }
        }
    }

    private fun asyncExecuteTaskWithWriteLock(task: Runnable) {
        asyncExecuteTaskWithLock(mLock.writeLock(), task)
    }
}
