/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.android.inputmethod.latin

import helium314.keyboard.latin.common.NativeSuggestOptions
import helium314.keyboard.latin.define.DecoderSpecificConstants
import helium314.keyboard.latin.utils.JniUtils
import java.util.Locale

class DicTraverseSession(locale: Locale?, dictionary: Long, dictSize: Long) {

    companion object {
        private const val MAX_RESULTS = 18

        init {
            JniUtils.loadNativeLibrary()
        }

        @JvmStatic
        private external fun setDicTraverseSessionNative(locale: String, dictSize: Long): Long
        @JvmStatic
        private external fun initDicTraverseSessionNative(nativeDicTraverseSession: Long, dictionary: Long, previousWord: IntArray?, previousWordLength: Int)
        @JvmStatic
        private external fun releaseDicTraverseSessionNative(nativeDicTraverseSession: Long)
    }

    @JvmField val mInputCodePoints = IntArray(DecoderSpecificConstants.DICTIONARY_MAX_WORD_LENGTH)
    @JvmField val mPrevWordCodePointArrays: Array<IntArray?> = arrayOfNulls(DecoderSpecificConstants.MAX_PREV_WORD_COUNT_FOR_N_GRAM)
    @JvmField val mIsBeginningOfSentenceArray = BooleanArray(DecoderSpecificConstants.MAX_PREV_WORD_COUNT_FOR_N_GRAM)
    @JvmField val mOutputSuggestionCount = IntArray(1)
    @JvmField val mOutputCodePoints = IntArray(DecoderSpecificConstants.DICTIONARY_MAX_WORD_LENGTH * MAX_RESULTS)
    @JvmField val mSpaceIndices = IntArray(MAX_RESULTS)
    @JvmField val mOutputScores = IntArray(MAX_RESULTS)
    @JvmField val mOutputTypes = IntArray(MAX_RESULTS)
    @JvmField val mOutputAutoCommitFirstWordConfidence = IntArray(1)
    @JvmField val mInputOutputWeightOfLangModelVsSpatialModel = FloatArray(1)
    @JvmField val mNativeSuggestOptions = NativeSuggestOptions()

    private var mNativeDicTraverseSession: Long = setDicTraverseSessionNative(locale?.toString() ?: "", dictSize)

    init {
        initSession(dictionary)
    }

    fun getSession(): Long = mNativeDicTraverseSession

    fun initSession(dictionary: Long) {
        initSession(dictionary, null, 0)
    }

    fun initSession(dictionary: Long, previousWord: IntArray?, previousWordLength: Int) {
        initDicTraverseSessionNative(mNativeDicTraverseSession, dictionary, previousWord, previousWordLength)
    }

    private fun closeInternal() {
        if (mNativeDicTraverseSession != 0L) {
            releaseDicTraverseSessionNative(mNativeDicTraverseSession)
            mNativeDicTraverseSession = 0L
        }
    }

    fun close() {
        closeInternal()
    }

    @Throws(Throwable::class)
    protected fun finalize() {
        closeInternal()
    }
}
