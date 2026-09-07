/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin

import android.content.Context
import android.util.LruCache
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.DictionaryStats
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.utils.SuggestionResults
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Interface that facilitates interaction with different kinds of dictionaries. Provides APIs to
 * instantiate and select the correct dictionaries (based on language and settings), update entries
 * and fetch suggestions.
 */
interface DictionaryFacilitator {

    interface DictionaryInitializationListener {
        fun onUpdateMainDictionaryAvailability(isMainDictionaryAvailable: Boolean)
    }

    /** The facilitator will put words into the cache whenever it decodes them. */
    fun setValidSpellingWordReadCache(cache: LruCache<String, Boolean>)

    /** The facilitator will get words from the cache whenever it needs to check their spelling. */
    fun setValidSpellingWordWriteCache(cache: LruCache<String, Boolean>)

    /**
     * Returns whether this facilitator is exactly for this locale.
     *
     * @param locale the locale to test against
     */
    fun isForLocale(locale: Locale?): Boolean

    /**
     * Called every time [LatinIME] starts on a new text field.
     */
    fun onStartInput()

    /**
     * Called every time [LatinIME] finishes with the current text field.
     */
    fun onFinishInput()

    /** whether a dictionary is set */
    fun isActive(): Boolean

    /** the locale provided in resetDictionaries */
    val mainLocale: Locale

    /** the most "trusted" locale, differs from getMainLocale only if multilingual typing is used */
    val currentLocale: Locale

    fun usesSameSettings(
        locales: List<Locale>,
        contacts: Boolean,
        apps: Boolean,
        personalization: Boolean
    ): Boolean

    /** switches to newLocale, gets secondary locales from current settings, and sets secondary dictionaries */
    fun resetDictionaries(
        context: Context,
        newLocale: Locale,
        useContactsDict: Boolean,
        useAppsDict: Boolean,
        usePersonalizedDicts: Boolean,
        forceReloadMainDictionary: Boolean,
        dictNamePrefix: String,
        listener: DictionaryInitializationListener?
    )

    /** removes the word from all editable dictionaries, and adds it to a blacklist in case it's in a read-only dictionary */
    fun removeWord(word: String)

    fun reloadBlacklist()

    fun isBlacklisted(word: String): Boolean

    fun closeDictionaries()

    /** main dictionaries are loaded asynchronously after resetDictionaries */
    fun hasAtLeastOneInitializedMainDictionary(): Boolean

    /** whether main dictionary loading is currently pending/in-progress */
    fun isMainDictionaryLoadPending(): Boolean

    /** main dictionaries are loaded asynchronously after resetDictionaries */
    fun hasAtLeastOneUninitializedMainDictionary(): Boolean

    /** main dictionaries are loaded asynchronously after resetDictionaries */
    @Throws(InterruptedException::class)
    fun waitForLoadingMainDictionaries(timeout: Long, unit: TimeUnit)

    /** adds the word to user history dictionary, calls adjustConfidences, and might add it to personal dictionary if the setting is enabled */
    fun addToUserHistory(
        suggestion: String,
        wasAutoCapitalized: Boolean,
        ngramContext: NgramContext,
        timeStampInSeconds: Long,
        blockPotentiallyOffensive: Boolean
    )

    /** adjust confidences for multilingual typing */
    fun adjustConfidences(word: String, wasAutoCapitalized: Boolean)

    /** a string with all used locales and their current confidences, null if multilingual typing is not used */
    fun localesAndConfidences(): String?

    /** completely removes the word from user history (currently not if event is a backspace event) */
    fun unlearnFromUserHistory(
        word: String,
        ngramContext: NgramContext,
        timeStampInSeconds: Long,
        eventType: Int
    )

    fun getSuggestionResults(
        composedData: ComposedData,
        ngramContext: NgramContext,
        keyboard: Keyboard,
        settingsValuesForSuggestion: SettingsValuesForSuggestion,
        sessionId: Int,
        inputStyle: Int
    ): SuggestionResults

    fun isValidSpellingWord(word: String): Boolean

    fun isValidSuggestionWord(word: String): Boolean

    fun clearUserHistoryDictionary(context: Context)

    fun dump(context: Context): String

    fun dumpDictionaryForDebug(dictName: String)

    fun getDictionaryStats(context: Context): List<DictionaryStats>

    /**
     * Returns all words with frequencies from the primary main dictionary, for gesture typing
     * precomputation. Iterates the binary dictionary directly; can be slow on first call.
     * The default returns an empty map; DictionaryFacilitatorImpl overrides this.
     */
    fun getAllMainDictionaryWordsWithFrequency(): Map<String, Int> = emptyMap()

    fun forEachMainDictionaryWord(consumer: java.util.function.BiConsumer<String, Int>) {}

    companion object {
        @JvmField
        val ALL_DICTIONARY_TYPES = arrayOf(
            Dictionary.TYPE_MAIN,
            Dictionary.TYPE_CONTACTS,
            Dictionary.TYPE_APPS,
            Dictionary.TYPE_USER_HISTORY,
            Dictionary.TYPE_USER
        )

        @JvmField
        val DYNAMIC_DICTIONARY_TYPES = arrayOf(
            Dictionary.TYPE_CONTACTS,
            Dictionary.TYPE_APPS,
            Dictionary.TYPE_USER_HISTORY,
            Dictionary.TYPE_USER
        )
    }
}

