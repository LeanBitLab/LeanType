/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.dictionary

import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.utils.Log
import java.util.Locale

/**
 * Class for a collection of dictionaries that behave like one dictionary.
 */
class DictionaryCollection(
    dictType: String,
    locale: Locale?,
    dictionaries: Collection<Dictionary>,
    weights: FloatArray
) : Dictionary(dictType, locale) {
    private val TAG = "DictionaryCollection"
    private val mDictionaries: ArrayList<Dictionary>
    private val mWeights: FloatArray

    init {
        mDictionaries = ArrayList(dictionaries.filterNotNull())
        mWeights = if (mDictionaries.size > weights.size) {
            Log.w(TAG, "got weights array of length ${weights.size}, expected ${mDictionaries.size}")
            FloatArray(mDictionaries.size) { 1f }
        } else {
            weights
        }
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
        val dictionaries = mDictionaries
        if (dictionaries.isEmpty()) return null
        
        // To avoid creating unnecessary objects, we get the list out of the first
        // dictionary and add the rest to it if not null, hence the get(0)
        var suggestions = dictionaries[0].getSuggestions(
            composedData, ngramContext, proximityInfoHandle, settingsValuesForSuggestion,
            sessionId, weightForLocale * mWeights[0], inOutWeightOfLangModelVsSpatialModel
        )
        if (suggestions == null) suggestions = ArrayList()
        
        val length = dictionaries.size
        for (i in 1 until length) {
            val sugg = dictionaries[i].getSuggestions(
                composedData, ngramContext, proximityInfoHandle, settingsValuesForSuggestion,
                sessionId, weightForLocale * mWeights[i], inOutWeightOfLangModelVsSpatialModel
            )
            if (sugg != null) suggestions.addAll(sugg)
        }
        return suggestions
    }

    override fun isInDictionary(word: String): Boolean {
        for (i in mDictionaries.indices.reversed()) {
            if (mDictionaries[i].isInDictionary(word)) return true
        }
        return false
    }

    override fun getFrequency(word: String): Int {
        var maxFreq = -1
        for (i in mDictionaries.indices.reversed()) {
            val tempFreq = mDictionaries[i].getFrequency(word)
            maxFreq = maxOf(tempFreq, maxFreq)
        }
        return maxFreq
    }

    override fun getMaxFrequencyOfExactMatches(word: String): Int {
        var maxFreq = -1
        for (i in mDictionaries.indices.reversed()) {
            val tempFreq = mDictionaries[i].getMaxFrequencyOfExactMatches(word)
            maxFreq = maxOf(tempFreq, maxFreq)
        }
        return maxFreq
    }

    override fun getAllWordsWithFrequency(): Map<String, Int> {
        val result = HashMap<String, Int>()
        for (dict in mDictionaries) result.putAll(dict.getAllWordsWithFrequency())
        return result
    }

    override fun forEachWord(consumer: java.util.function.BiConsumer<String, Int>) {
        for (dict in mDictionaries) {
            dict.forEachWord(consumer)
        }
    }

    override val isInitialized: Boolean
        get() = mDictionaries.isNotEmpty()

    override fun close() {
        for (dict in mDictionaries) dict.close()
    }
}
