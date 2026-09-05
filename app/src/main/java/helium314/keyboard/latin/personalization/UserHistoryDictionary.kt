/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.personalization

import android.content.Context
import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.ExpandableBinaryDictionary
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.makedict.DictionaryHeader
import java.io.File
import java.util.Locale

/**
 * Locally gathers statistics about the words user types and various other signals like
 * auto-correction cancellation or manual picks. This allows the keyboard to adapt to the
 * typist over time.
 */
class UserHistoryDictionary internal constructor(
    context: Context,
    locale: Locale
) : ExpandableBinaryDictionary(
    context,
    getUserHistoryDictName(NAME, locale, null),
    locale,
    Dictionary.TYPE_USER_HISTORY,
    null
) {
    init {
        val loc = mLocale
        if (loc != null && loc.toString().length > 1) {
            reloadDictionaryIfRequired()
        }
    }

    companion object {
        const val NAME = "UserHistoryDictionary"

        @JvmStatic
        internal fun getUserHistoryDictName(name: String, locale: Locale, dictFile: File?): String {
            return getDictName(name, locale, dictFile)
        }

        @JvmStatic
        fun getDictionary(
            context: Context,
            locale: Locale,
            dictFile: File?,
            dictNamePrefix: String?
        ): UserHistoryDictionary {
            return PersonalizationHelper.getUserHistoryDictionary(context, locale)
        }

        @JvmStatic
        fun addToDictionary(
            userHistoryDictionary: ExpandableBinaryDictionary,
            ngramContext: NgramContext,
            word: String,
            isValid: Boolean,
            timestamp: Int
        ) {
            if (word.length > BinaryDictionary.DICTIONARY_MAX_WORD_LENGTH) {
                return
            }
            userHistoryDictionary.updateEntriesForWord(ngramContext, word, isValid, 1, timestamp)
        }
    }

    override fun getHeaderAttributeMap(): Map<String, String> {
        val attributeMap = super.getHeaderAttributeMap() as MutableMap<String, String>
        attributeMap[DictionaryHeader.USES_FORGETTING_CURVE_KEY] = DictionaryHeader.ATTRIBUTE_VALUE_TRUE
        attributeMap[DictionaryHeader.HAS_HISTORICAL_INFO_KEY] = DictionaryHeader.ATTRIBUTE_VALUE_TRUE
        return attributeMap
    }

    override fun loadInitialContentsLocked() {
        // No initial contents.
    }

    override fun isValidWord(word: String): Boolean {
        // Strings out of this dictionary should not be considered existing words.
        return false
    }
}
