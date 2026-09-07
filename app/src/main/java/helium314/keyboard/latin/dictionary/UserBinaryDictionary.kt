/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.dictionary

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.UserDictionary.Words
import android.text.TextUtils
import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import java.io.File
import java.util.Arrays
import java.util.Locale

/**
 * An expandable dictionary that stores the words in the user dictionary provider into a binary
 * dictionary file to use it from native code.
 */
class UserBinaryDictionary protected constructor(
    context: Context,
    locale: Locale?,
    alsoUseMoreRestrictiveLocales: Boolean,
    dictFile: File?,
    name: String
) : ExpandableBinaryDictionary(
    context,
    getDictName(name, locale, dictFile),
    locale,
    TYPE_USER,
    dictFile
) {
    private var mObserver: ContentObserver?
    private val mLocaleString: String
    private val mAlsoUseMoreRestrictiveLocales: Boolean

    init {
        if (locale == null) throw NullPointerException() // Catch the error earlier
        val localeStr = locale.toString()
        mLocaleString = if (SubtypeLocaleUtils.NO_LANGUAGE == localeStr) {
            USER_DICTIONARY_ALL_LANGUAGES
        } else {
            localeStr
        }
        mAlsoUseMoreRestrictiveLocales = alsoUseMoreRestrictiveLocales

        val observer = object : ContentObserver(null) {
            override fun onChange(self: Boolean, uri: Uri?) {
                setNeedsToRecreate()
            }
        }
        mObserver = observer
        context.contentResolver.registerContentObserver(Words.CONTENT_URI, true, observer)
        reloadDictionaryIfRequired()
    }

    companion object {
        private const val TAG = "ExpandableBinaryDictionary"
        private const val USER_DICTIONARY_ALL_LANGUAGES = ""
        private const val HISTORICAL_DEFAULT_USER_DICTIONARY_FREQUENCY = 250
        private const val LATINIME_DEFAULT_USER_DICTIONARY_FREQUENCY = 160
        private const val USER_DICT_SHORTCUT_FREQUENCY = 14

        private val PROJECTION_QUERY_WITH_SHORTCUT = arrayOf(
            Words.WORD,
            Words.SHORTCUT,
            Words.FREQUENCY
        )
        private val PROJECTION_QUERY_WITHOUT_SHORTCUT = arrayOf(
            Words.WORD,
            Words.FREQUENCY
        )

        private const val NAME = "userunigram"

        fun getDictionary(
            context: Context,
            locale: Locale,
            dictFile: File?,
            dictNamePrefix: String
        ): UserBinaryDictionary {
            return UserBinaryDictionary(context, locale, true, dictFile, dictNamePrefix + NAME)
        }

        private fun scaleFrequencyFromDefaultToLatinIme(defaultFrequency: Int): Int {
            if (defaultFrequency > Int.MAX_VALUE / LATINIME_DEFAULT_USER_DICTIONARY_FREQUENCY) {
                return (defaultFrequency / HISTORICAL_DEFAULT_USER_DICTIONARY_FREQUENCY) *
                        LATINIME_DEFAULT_USER_DICTIONARY_FREQUENCY
            }
            return (defaultFrequency * LATINIME_DEFAULT_USER_DICTIONARY_FREQUENCY) /
                    HISTORICAL_DEFAULT_USER_DICTIONARY_FREQUENCY
        }
    }

    @Synchronized
    override fun close() {
        if (mObserver != null) {
            mContext.contentResolver.unregisterContentObserver(mObserver!!)
            mObserver = null
        }
        super.close()
    }

    override fun loadInitialContentsLocked() {
        val localeElements = if (TextUtils.isEmpty(mLocaleString)) {
            emptyArray()
        } else {
            mLocaleString.split("_", limit = 3).toTypedArray()
        }
        val length = localeElements.size

        val request = StringBuilder("(locale is NULL)")
        var localeSoFar = ""
        for (i in 0 until length) {
            localeElements[i] = localeSoFar + localeElements[i]
            localeSoFar = localeElements[i] + "_"
            request.append(" or (locale=?)")
        }

        val requestArguments: Array<String>
        if (mAlsoUseMoreRestrictiveLocales && length < 3) {
            request.append(" or (locale like ?)")
            val localeElementsWithMoreRestrictiveLocalesIncluded = Arrays.copyOf(localeElements, length + 1)
            localeElementsWithMoreRestrictiveLocalesIncluded[length] = localeElements[length - 1] + "_%"
            requestArguments = localeElementsWithMoreRestrictiveLocalesIncluded
        } else {
            requestArguments = localeElements
        }
        val requestString = request.toString()
        try {
            addWordsFromProjectionLocked(PROJECTION_QUERY_WITH_SHORTCUT, requestString, requestArguments)
        } catch (e: IllegalArgumentException) {
            addWordsFromProjectionLocked(PROJECTION_QUERY_WITHOUT_SHORTCUT, requestString, requestArguments)
        }
    }

    private fun addWordsFromProjectionLocked(
        query: Array<String>,
        request: String,
        requestArguments: Array<String>
    ) {
        var cursor: Cursor? = null
        try {
            cursor = mContext.contentResolver.query(
                Words.CONTENT_URI, query, request, requestArguments, null
            )
            addWordsLocked(cursor)
        } catch (e: SQLiteException) {
            Log.e(TAG, "SQLiteException in the remote User dictionary process.", e)
        } finally {
            try {
                cursor?.close()
            } catch (e: SQLiteException) {
                Log.e(TAG, "SQLiteException in the remote User dictionary process.", e)
            }
        }
    }

    private fun addWordsLocked(cursor: Cursor?) {
        val hasShortcutColumn = true
        if (cursor == null) return
        if (cursor.moveToFirst()) {
            val indexWord = cursor.getColumnIndex(Words.WORD)
            val indexShortcut = if (hasShortcutColumn) cursor.getColumnIndex(Words.SHORTCUT) else 0
            val indexFrequency = cursor.getColumnIndex(Words.FREQUENCY)
            while (!cursor.isAfterLast) {
                val word = cursor.getString(indexWord)
                val shortcut = if (hasShortcutColumn) cursor.getString(indexShortcut) else null
                val frequency = cursor.getInt(indexFrequency)
                val adjustedFrequency = scaleFrequencyFromDefaultToLatinIme(frequency)
                if (word != null && word.length <= MAX_WORD_LENGTH) {
                    runGCIfRequiredLocked(true)
                    addUnigramLocked(
                        word, adjustedFrequency, null,
                        0, false,
                        false,
                        BinaryDictionary.NOT_A_VALID_TIMESTAMP
                    )
                    if (shortcut != null && shortcut.length <= MAX_WORD_LENGTH) {
                        runGCIfRequiredLocked(true)
                        addUnigramLocked(
                            shortcut, adjustedFrequency, word,
                            USER_DICT_SHORTCUT_FREQUENCY, true,
                            false,
                            BinaryDictionary.NOT_A_VALID_TIMESTAMP
                        )
                    }
                    // ponytail: split phrase into unigrams and n-grams for next-word prediction
                    val parts = word.split(Regex("\\s+"))
                    if (parts.size > 1) {
                        for (part in parts) {
                            if (part.length <= MAX_WORD_LENGTH && part.isNotEmpty() && part != word) {
                                runGCIfRequiredLocked(true)
                                addUnigramLocked(part, adjustedFrequency, null, 0, false, false, BinaryDictionary.NOT_A_VALID_TIMESTAMP)
                            }
                        }
                        for (i in 1 until parts.size) {
                            val targetWord = parts[i]
                            if (targetWord.length <= MAX_WORD_LENGTH && targetWord.isNotEmpty()) {
                                val contextSize = minOf(i, BinaryDictionary.MAX_PREV_WORD_COUNT_FOR_N_GRAM)
                                val prevWords = Array(contextSize) { j ->
                                    NgramContext.WordInfo(parts[i - 1 - j])
                                }
                                runGCIfRequiredLocked(true)
                                addNgramEntryLocked(NgramContext(*prevWords), targetWord, adjustedFrequency, BinaryDictionary.NOT_A_VALID_TIMESTAMP)
                            }
                        }
                    }
                }
                cursor.moveToNext()
            }
        }
    }
}
