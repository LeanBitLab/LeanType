/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.spellcheck

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Binder
import android.provider.UserDictionary.Words
import android.service.textservice.SpellCheckerService.Session
import android.text.TextUtils
import android.util.LruCache
import android.view.inputmethod.InputMethodManager
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import com.android.inputmethod.latin.utils.BinaryDictionaryUtils
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.WordComposer
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.ScriptUtils.script
import helium314.keyboard.latin.utils.StatsUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.SuggestionResults
import helium314.keyboard.latin.utils.prefs
import java.util.Locale
import java.util.TreeMap

abstract class AndroidWordLevelSpellCheckerSession(
    private val mService: AndroidSpellCheckerService
) : Session() {

    protected val mSuggestionsCache = SuggestionsCache()
    private var mLocale: Locale? = null
    private var mScript: String = ScriptUtils.SCRIPT_UNKNOWN
    private val mObserver: ContentObserver

    init {
        mObserver = object : ContentObserver(null) {
            override fun onChange(self: Boolean) {
                mSuggestionsCache.clearCache()
            }
        }
        mService.contentResolver.registerContentObserver(Words.CONTENT_URI, true, mObserver)
    }

    private fun updateLocale() {
        val localeString = getLocale()
        if (mLocale?.toString() != localeString) {
            mLocale = localeString?.constructLocale()
            mScript = mLocale?.script() ?: ScriptUtils.SCRIPT_UNKNOWN
            mSuggestionsCache.clearCache()
        }
    }

    override fun onCreate() {
        updateLocale()
    }

    override fun getLocale(): String? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val imm = mService.applicationContext.getSystemService(InputMethodManager::class.java)
            if (imm != null) {
                val currentInputMethodSubtype = imm.currentInputMethodSubtype
                if (currentInputMethodSubtype != null) {
                    val localeString = currentInputMethodSubtype.locale
                    if (!localeString.isNullOrEmpty()) {
                        return localeString
                    }
                    if ("dummy" == currentInputMethodSubtype.extraValue) {
                        val prefs = mService.prefs()
                        return SubtypeSettings.getSelectedSubtype(prefs).locale
                    }
                }
            }
        }
        return super.getLocale()
    }

    override fun onClose() {
        mService.contentResolver.unregisterContentObserver(mObserver)
    }

    private fun getCheckabilityInScript(text: String, script: String): Int {
        if (text.isEmpty() || text.length <= 1) return CHECKABILITY_TOO_SHORT
        val firstCodePoint = text.codePointAt(0)
        if (!ScriptUtils.isLetterPartOfScript(firstCodePoint, script) && '\'' != text[0]) return CHECKABILITY_FIRST_LETTER_UNCHECKABLE

        var letterCount = 0
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            if (Constants.CODE_COMMERCIAL_AT == codePoint || Constants.CODE_SLASH == codePoint) return CHECKABILITY_EMAIL_OR_URL
            if (Constants.CODE_PERIOD == codePoint) return CHECKABILITY_CONTAINS_PERIOD
            if (ScriptUtils.isLetterPartOfScript(codePoint, script)) ++letterCount
            i = text.offsetByCodePoints(i, 1)
        }
        return if (letterCount * 4 < text.length * 3) CHECKABILITY_TOO_MANY_NON_LETTERS else CHECKABILITY_CHECKABLE
    }

    private fun isInDictForAnyCapitalization(text: String, capitalizeType: Int, locale: Locale): Boolean {
        if (mService.isValidWord(locale, text)) return true
        if (StringUtils.CAPITALIZE_NONE == capitalizeType) return false

        val lowerCaseText = text.lowercase(locale)
        if (mService.isValidWord(locale, lowerCaseText)) return true
        if (StringUtils.CAPITALIZE_FIRST == capitalizeType) return false

        return mService.isValidWord(locale, StringUtils.capitalizeFirstAndDowncaseRest(lowerCaseText, locale))
    }

    private fun onGetSuggestionsInternal(textInfo: TextInfo, suggestionsLimit: Int): SuggestionsInfo {
        return onGetSuggestionsInternal(textInfo, null, suggestionsLimit)
    }

    protected fun onGetSuggestionsInternal(textInfo: TextInfo, ngramContext: NgramContext?, suggestionsLimit: Int): SuggestionsInfo {
        try {
            updateLocale()
            val locale = mLocale ?: return AndroidSpellCheckerService.getNotInDictEmptySuggestions(false)
            var text = textInfo.text
                .replace(AndroidSpellCheckerService.APOSTROPHE, AndroidSpellCheckerService.SINGLE_QUOTE)
                .replace(Regex("^$quotesRegexp"), "")
                .replace(Regex("$quotesRegexp$"), "")

            val localeRegex = scriptToPunctuationRegexMap[locale.script()]
            if (localeRegex != null) {
                text = text.replace(Regex(localeRegex), "")
            }

            val cachedSuggestionsParams = mSuggestionsCache.getSuggestionsFromCache(text)
            if (cachedSuggestionsParams != null) {
                return SuggestionsInfo(cachedSuggestionsParams.mFlags, cachedSuggestionsParams.mSuggestions)
            }

            if (!mService.hasMainDictionaryForLocale(locale)) {
                return AndroidSpellCheckerService.getNotInDictEmptySuggestions(false)
            }

            val checkability = getCheckabilityInScript(text, mScript)
            if (CHECKABILITY_CHECKABLE != checkability) {
                val periodOnlyAtLastIndex = text.indexOf(Constants.CODE_PERIOD.toChar()) == (text.length - 1)
                if (CHECKABILITY_CONTAINS_PERIOD == checkability) {
                    val splitText = text.split(Regex(Constants.REGEXP_PERIOD))
                    var allWordsAreValid = true
                    for (word in splitText) {
                        if (word.isNotEmpty() && !mService.isValidWord(locale, word) && !mService.isValidWord(locale, word.lowercase(locale))) {
                            allWordsAreValid = false
                            break
                        }
                    }
                    if (allWordsAreValid && !periodOnlyAtLastIndex) {
                        return SuggestionsInfo(
                            SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO or SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS,
                            arrayOf(TextUtils.join(Constants.STRING_SPACE, splitText))
                        )
                    }
                }
                return if (mService.isValidWord(locale, text))
                    AndroidSpellCheckerService.getInDictEmptySuggestions()
                else
                    AndroidSpellCheckerService.getNotInDictEmptySuggestions(!periodOnlyAtLastIndex)
            }

            val capitalizeType = StringUtils.getCapitalizationType(text)
            if (isInDictForAnyCapitalization(text, capitalizeType, locale)) {
                if (DebugFlags.DEBUG_ENABLED) Log.i(TAG, "onGetSuggestionsInternal() : [$text] is a valid word")
                return AndroidSpellCheckerService.getInDictEmptySuggestions()
            }
            if (DebugFlags.DEBUG_ENABLED) Log.i(TAG, "onGetSuggestionsInternal() : [$text] is NOT a valid word")

            val keyboard = mService.getKeyboardForLocale(locale)
            val composer = WordComposer()
            if (locale.language == "ko") composer.restartCombining("hangul")
            val codePoints = StringUtils.toCodePointArray(text)
            val coordinates = keyboard.getCoordinates(codePoints)
            composer.setComposingWord(codePoints, coordinates)

            val suggestionResults = mService.getSuggestionResults(locale, composer.getComposedDataSnapshot(), ngramContext ?: NgramContext.EMPTY_PREV_WORDS_INFO, keyboard)
                ?: return AndroidSpellCheckerService.getNotInDictEmptySuggestions(false)

            val result = getResult(capitalizeType, locale, suggestionsLimit, mService.recommendedThreshold, text, suggestionResults)

            if (DebugFlags.DEBUG_ENABLED && result.mSuggestions != null && result.mSuggestions.isNotEmpty()) {
                Log.i(TAG, "onGetSuggestionsInternal() : Suggestions = ${result.mSuggestions.joinToString { " [$it]" }}")
            }

            StatsUtils.onInvalidWordIdentification(text)
            val flags = SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO or (if (result.mHasRecommendedSuggestions) SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS else 0)
            val retval = SuggestionsInfo(flags, result.mSuggestions)
            mSuggestionsCache.putSuggestionsToCache(text, result.mSuggestions, flags)
            return retval
        } catch (e: RuntimeException) {
            Log.e(TAG, "Exception while spellchecking", e)
            return AndroidSpellCheckerService.getNotInDictEmptySuggestions(false)
        }
    }

    override fun onGetSuggestions(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
        if (textInfo == null) return AndroidSpellCheckerService.getNotInDictEmptySuggestions(false)
        val ident = Binder.clearCallingIdentity()
        try {
            return onGetSuggestionsInternal(textInfo, suggestionsLimit)
        } finally {
            Binder.restoreCallingIdentity(ident)
        }
    }

    companion object {
        private const val TAG = "AndroidWordLevelSpellCheckerSession"
        @JvmField val EMPTY_STRING_ARRAY = emptyArray<String>()

        private const val quotesRegexp = "([\u0022\u0027\u0060\u00B4\u2018\u2019\u201C\u201D])"
        private val scriptToPunctuationRegexMap = TreeMap<String, String>().apply {
            put(ScriptUtils.SCRIPT_ARMENIAN, "(\u0028|\u0029|\u0027|\u2026|\u055E|\u055C|\u055B|\u055D|\u058A|\u2015|\u00AB|\u00BB|\u002C|\u0589|\u2024)")
        }

        private const val CHECKABILITY_CHECKABLE = 0
        private const val CHECKABILITY_TOO_MANY_NON_LETTERS = 1
        private const val CHECKABILITY_CONTAINS_PERIOD = 2
        private const val CHECKABILITY_EMAIL_OR_URL = 3
        private const val CHECKABILITY_FIRST_LETTER_UNCHECKABLE = 4
        private const val CHECKABILITY_TOO_SHORT = 5

        private fun getResult(
            capitalizeType: Int, locale: Locale, suggestionsLimit: Int,
            recommendedThreshold: Float, originalText: String,
            suggestionResults: SuggestionResults
        ): Result {
            if (suggestionResults.isEmpty() || suggestionsLimit <= 0) {
                return Result(null, false)
            }
            val suggestionsSet = LinkedHashSet<String>()
            for (suggestedWordInfo in suggestionResults) {
                val suggestion = when (capitalizeType) {
                    StringUtils.CAPITALIZE_ALL -> suggestedWordInfo.mWord.uppercase(locale)
                    StringUtils.CAPITALIZE_FIRST -> StringUtils.capitalizeFirstCodePoint(suggestedWordInfo.mWord, locale)
                    else -> suggestedWordInfo.mWord
                }
                suggestionsSet.add(suggestion)
            }
            val suggestions = ArrayList(suggestionsSet)
            val gatheredSuggestionsList = suggestions.subList(0, minOf(suggestions.size, suggestionsLimit))
            val gatheredSuggestions = gatheredSuggestionsList.toTypedArray()

            val bestScore = suggestionResults.first().mScore
            val bestSuggestion = suggestions[0]
            val normalizedScore = BinaryDictionaryUtils.calcNormalizedScore(originalText, bestSuggestion, bestScore)
            val hasRecommendedSuggestions = normalizedScore > recommendedThreshold
            return Result(gatheredSuggestions, hasRecommendedSuggestions)
        }
    }

    protected class SuggestionsParams(
        @JvmField val mSuggestions: Array<String>,
        @JvmField val mFlags: Int
    )

    protected class SuggestionsCache {
        private val mUnigramSuggestionsInfoCache = LruCache<String, SuggestionsParams>(MAX_CACHE_SIZE)

        private fun generateKey(query: String): String = query

        fun getSuggestionsFromCache(query: String): SuggestionsParams? {
            return mUnigramSuggestionsInfoCache.get(query)
        }

        fun putSuggestionsToCache(query: String?, suggestions: Array<String>?, flags: Int) {
            if (suggestions == null || query.isNullOrEmpty()) return
            mUnigramSuggestionsInfoCache.put(generateKey(query), SuggestionsParams(suggestions, flags))
        }

        fun clearCache() {
            mUnigramSuggestionsInfoCache.evictAll()
        }

        companion object {
            private const val MAX_CACHE_SIZE = 50
        }
    }

    private class Result(
        @JvmField val mSuggestions: Array<String>?,
        @JvmField val mHasRecommendedSuggestions: Boolean
    )
}
