/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin

import android.content.Context
import helium314.keyboard.latin.utils.Log
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Cache for dictionary facilitators of multiple locales.
 * This class automatically creates and releases up to 3 facilitator instances using LRU policy.
 */
class DictionaryFacilitatorLruCache(
    private val mContext: Context,
    private val mDictionaryNamePrefix: String
) {
    private val mLock = Any()
    private val mDictionaryFacilitator: DictionaryFacilitator =
        DictionaryFacilitatorProvider.getDictionaryFacilitator(true /* isNeededForSpellChecking */)
    private var mUseContactsDictionary = false
    private var mUseAppsDictionary = false
    private var mLocale: Locale? = null

    private fun resetDictionariesForLocaleLocked() {
        val locale = mLocale ?: return
        mDictionaryFacilitator.resetDictionaries(
            mContext, locale, mUseContactsDictionary, mUseAppsDictionary,
            false, false, mDictionaryNamePrefix, null
        )
    }

    fun setUseContactsDictionary(useContactsDictionary: Boolean) {
        synchronized(mLock) {
            if (mUseContactsDictionary == useContactsDictionary) return
            mUseContactsDictionary = useContactsDictionary
            resetDictionariesForLocaleLocked()
            waitForLoadingMainDictionary(mDictionaryFacilitator)
        }
    }

    fun setUseAppsDictionary(useAppsDictionary: Boolean) {
        synchronized(mLock) {
            if (mUseAppsDictionary == useAppsDictionary) return
            mUseAppsDictionary = useAppsDictionary
            resetDictionariesForLocaleLocked()
            waitForLoadingMainDictionary(mDictionaryFacilitator)
        }
    }

    fun get(locale: Locale): DictionaryFacilitator {
        synchronized(mLock) {
            if (!mDictionaryFacilitator.isForLocale(locale)) {
                mLocale = locale
                resetDictionariesForLocaleLocked()
            }
            waitForLoadingMainDictionary(mDictionaryFacilitator)
            return mDictionaryFacilitator
        }
    }

    fun closeDictionaries() {
        synchronized(mLock) {
            mDictionaryFacilitator.closeDictionaries()
        }
    }

    companion object {
        private const val TAG = "DictFacilitatorLruCache"
        private const val WAIT_FOR_LOADING_MAIN_DICT_IN_MILLISECONDS = 1000L
        private const val MAX_RETRY_COUNT_FOR_WAITING_FOR_LOADING_DICT = 5

        private fun waitForLoadingMainDictionary(dictionaryFacilitator: DictionaryFacilitator) {
            for (i in 0 until MAX_RETRY_COUNT_FOR_WAITING_FOR_LOADING_DICT) {
                try {
                    dictionaryFacilitator.waitForLoadingMainDictionaries(
                        WAIT_FOR_LOADING_MAIN_DICT_IN_MILLISECONDS, TimeUnit.MILLISECONDS
                    )
                    return
                } catch (e: InterruptedException) {
                    Log.i(TAG, "Interrupted during waiting for loading main dictionary.", e)
                    if (i < MAX_RETRY_COUNT_FOR_WAITING_FOR_LOADING_DICT - 1) {
                        Log.i(TAG, "Retry", e)
                    } else {
                        Log.w(TAG, "Give up retrying. Retried $MAX_RETRY_COUNT_FOR_WAITING_FOR_LOADING_DICT times.", e)
                    }
                }
            }
        }
    }
}
