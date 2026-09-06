/*
 * Copyright (C) 2008 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.dictionary

import java.util.Collections
import java.util.Locale
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.makedict.WordProperty
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion

/**
 * Abstract base class for a dictionary that can do a fuzzy search for words based on a set of key
 * strokes.
 */
abstract class Dictionary(
    @JvmField val mDictType: String,
    @JvmField val mLocale: Locale?
) {
    /**
     * Searches for suggestions for a given context.
     * @param composedData the key sequence to match with coordinate info
     * @param ngramContext the context for n-gram.
     * @param proximityInfoHandle the handle for key proximity. Is ignored by some implementations.
     * @param settingsValuesForSuggestion the settings values used for the suggestion.
     * @param sessionId the session id.
     * @param weightForLocale the weight given to this locale, to multiply the output scores for
     * multilingual input.
     * @param inOutWeightOfLangModelVsSpatialModel the weight of the language model as a ratio of
     * the spatial model, used for generating suggestions. inOutWeightOfLangModelVsSpatialModel is
     * a float array that has only one element. This can be updated when a different value is used.
     * @return the list of suggestions (possibly null if none)
     */
    abstract fun getSuggestions(
        composedData: ComposedData,
        ngramContext: NgramContext,
        proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion,
        sessionId: Int,
        weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?
    ): ArrayList<SuggestedWordInfo>?

    /**
     * Checks if the given word has to be treated as a valid word. Please note that some
     * dictionaries have entries that should be treated as invalid words.
     * @param word the word to search for. The search should be case-insensitive.
     * @return true if the word is valid, false otherwise
     */
    open fun isValidWord(word: String): Boolean {
        return isInDictionary(word)
    }

    /**
     * Checks if the given word is in the dictionary regardless of it being valid or not.
     */
    abstract fun isInDictionary(word: String): Boolean

    /**
     * Returns all words stored in this dictionary.
     * The default implementation returns an empty list; override in concrete dictionaries
     * that support full enumeration (e.g. ReadOnlyBinaryDictionary).
     */
    open fun getAllWordsWithFrequency(): Map<String, Int> {
        return Collections.emptyMap()
    }

    open fun forEachWord(consumer: java.util.function.BiConsumer<String, Int>) {
        for (entry in getAllWordsWithFrequency().entries) {
            consumer.accept(entry.key, entry.value)
        }
    }

    /**
     * Get the frequency of the word.
     * @param word the word to get the frequency of.
     */
    open fun getFrequency(word: String): Int {
        return NOT_A_PROBABILITY
    }

    /**
     * Get the maximum frequency of the word.
     * @param word the word to get the maximum frequency of.
     */
    open fun getMaxFrequencyOfExactMatches(word: String): Int {
        return NOT_A_PROBABILITY
    }

    /**
     * Compares the contents of the character array with the typed word and returns true if they
     * are the same.
     * @param word the array of characters that make up the word
     * @param length the number of valid characters in the character array
     * @param typedWord the word to compare with
     * @return true if they are the same, false otherwise.
     */
    protected open fun same(word: CharArray, length: Int, typedWord: String): Boolean {
        if (typedWord.length != length) {
            return false
        }
        for (i in 0 until length) {
            if (word[i] != typedWord[i]) {
                return false
            }
        }
        return true
    }

    /**
     * Override to clean up any resources.
     */
    open fun close() {
        // empty base implementation
    }

    open fun onFinishInput() {
        //empty base implementation
    }

    /**
     * Subclasses may override to indicate that this Dictionary is not yet properly initialized.
     */
    open val isInitialized: Boolean
        get() = true

    /**
     * Whether we think this suggestion should trigger an auto-commit. prevWord is the word
     * before the suggestion, so that we can use n-gram frequencies.
     * @param candidate The candidate suggestion, in whole (not only the first part).
     * @return whether we should auto-commit or not.
     */
    open fun shouldAutoCommit(candidate: SuggestedWordInfo): Boolean {
        // If we don't have support for auto-commit, or if we don't know, we return false to
        // avoid auto-committing stuff. Implementations of the Dictionary class that know to
        // determine whether we should auto-commit will override this.
        return false
    }

    /**
     * Whether this dictionary is based on data specific to the user, e.g., the user's contacts.
     * @return Whether this dictionary is specific to the user.
     */
    open fun isUserSpecific(): Boolean {
        return when (mDictType) {
            TYPE_USER_TYPED,
            TYPE_USER,
            TYPE_CONTACTS,
            TYPE_APPS,
            TYPE_USER_HISTORY -> true
            else -> false
        }
    }

    open fun getWordProperty(word: String, isBeginningOfSentence: Boolean): WordProperty? {
        return null
    }

    /**
     * Not a true dictionary. A placeholder used to indicate suggestions that don't come from any
     * real dictionary.
     */
    open class PhonyDictionary(type: String) : Dictionary(type, null) {
        override fun getSuggestions(
            composedData: ComposedData,
            ngramContext: NgramContext,
            proximityInfoHandle: Long,
            settingsValuesForSuggestion: SettingsValuesForSuggestion,
            sessionId: Int,
            weightForLocale: Float,
            inOutWeightOfLangModelVsSpatialModel: FloatArray?
        ): ArrayList<SuggestedWordInfo>? {
            return null
        }

        override fun isInDictionary(word: String): Boolean {
            return false
        }
    }

    companion object {
        const val NOT_A_PROBABILITY = -1
        const val NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL = -1.0f

        // The following types do not actually come from real dictionary instances, so we create
        // corresponding instances.
        const val TYPE_USER_TYPED = "user_typed"
        @JvmField val DICTIONARY_USER_TYPED: PhonyDictionary = PhonyDictionary(TYPE_USER_TYPED)

        const val TYPE_USER_SHORTCUT = "user_shortcut"
        @JvmField val DICTIONARY_USER_SHORTCUT: PhonyDictionary = PhonyDictionary(TYPE_USER_SHORTCUT)

        const val TYPE_APPLICATION_DEFINED = "application_defined"
        @JvmField val DICTIONARY_APPLICATION_DEFINED: PhonyDictionary = PhonyDictionary(TYPE_APPLICATION_DEFINED)

        const val TYPE_HARDCODED = "hardcoded" // punctuation signs and such
        @JvmField val DICTIONARY_HARDCODED: PhonyDictionary = PhonyDictionary(TYPE_HARDCODED)

        // Spawned by resuming suggestions. Comes from a span that was in the TextView.
        const val TYPE_RESUMED = "resumed"
        @JvmField val DICTIONARY_RESUMED: PhonyDictionary = PhonyDictionary(TYPE_RESUMED)

        // The following types of dictionary have actual functional instances. We don't need final
        // phony dictionary instances for them.
        const val TYPE_MAIN = "main"
        const val TYPE_CONTACTS = "contacts"
        const val TYPE_APPS = "apps"
        // User dictionary, the system-managed one.
        const val TYPE_USER = "user"
        // User history dictionary internal to LatinIME.
        const val TYPE_USER_HISTORY = "history"
        const val TYPE_EMOJI = "emoji"
    }
}
