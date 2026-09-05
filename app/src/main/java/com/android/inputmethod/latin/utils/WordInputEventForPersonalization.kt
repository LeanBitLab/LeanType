/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.android.inputmethod.latin.utils

import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.define.DecoderSpecificConstants
import helium314.keyboard.latin.settings.SpacingAndPunctuations
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import java.util.ArrayList
import java.util.Locale

// Note: this class is used as a parameter type of a native method. You should be careful when you
// rename this class or field name. See BinaryDictionary#addMultipleDictionaryEntriesNative().
class WordInputEventForPersonalization(
    targetWord: CharSequence,
    ngramContext: NgramContext,
    timestamp: Int
) {
    @JvmField val mTargetWord: IntArray = StringUtils.toCodePointArray(targetWord)
    @JvmField val mPrevWordsCount: Int = ngramContext.prevWordCount
    @JvmField val mPrevWordArray: Array<IntArray?> = arrayOfNulls(DecoderSpecificConstants.MAX_PREV_WORD_COUNT_FOR_N_GRAM)
    @JvmField val mIsPrevWordBeginningOfSentenceArray: BooleanArray = BooleanArray(DecoderSpecificConstants.MAX_PREV_WORD_COUNT_FOR_N_GRAM)
    // Time stamp in seconds.
    @JvmField val mTimestamp: Int = timestamp

    init {
        ngramContext.outputToArray(mPrevWordArray, mIsPrevWordBeginningOfSentenceArray)
    }

    companion object {
        private const val TAG = "WordInputEventForPersonalization"
        private const val DEBUG_TOKEN = false

        // Process a list of words and return a list of [WordInputEventForPersonalization] objects.
        @JvmStatic
        fun createInputEventFrom(
            tokens: List<String>, timestamp: Int,
            spacingAndPunctuations: SpacingAndPunctuations, locale: Locale
        ): ArrayList<WordInputEventForPersonalization> {
            val inputEvents = ArrayList<WordInputEventForPersonalization>()
            val N = tokens.size
            var ngramContext = NgramContext.EMPTY_PREV_WORDS_INFO
            for (i in 0 until N) {
                val tempWord = tokens[i]
                if (StringUtils.isEmptyStringOrWhiteSpaces(tempWord)) {
                    // just skip this token
                    if (DEBUG_TOKEN) {
                        Log.d(TAG, "--- isEmptyStringOrWhiteSpaces: \"$tempWord\"")
                    }
                    continue
                }
                if (!DictionaryInfoUtils.looksValidForDictionaryInsertion(
                        tempWord, spacingAndPunctuations)
                ) {
                    if (DEBUG_TOKEN) {
                        Log.d(TAG, "--- not looksValidForDictionaryInsertion: \"$tempWord\"")
                    }
                    // Sentence terminator found. Split.
                    // TODO: Detect whether the context is beginning-of-sentence.
                    ngramContext = NgramContext.EMPTY_PREV_WORDS_INFO
                    continue
                }
                if (DEBUG_TOKEN) {
                    Log.d(TAG, "--- word: \"$tempWord\"")
                }
                val inputEvent = detectWhetherVaildWordOrNotAndGetInputEvent(
                    ngramContext, tempWord, timestamp, locale
                )
                if (inputEvent == null) {
                    continue
                }
                inputEvents.add(inputEvent)
                ngramContext = ngramContext.getNextNgramContext(NgramContext.WordInfo(tempWord))
            }
            return inputEvents
        }

        private fun detectWhetherVaildWordOrNotAndGetInputEvent(
            ngramContext: NgramContext, targetWord: String, timestamp: Int,
            locale: Locale?
        ): WordInputEventForPersonalization? {
            if (locale == null) {
                return null
            }
            return WordInputEventForPersonalization(targetWord, ngramContext, timestamp)
        }
    }
}
