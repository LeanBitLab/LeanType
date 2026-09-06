/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.spellcheck

import android.content.res.Resources
import android.os.Binder
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SpannableStringUtils

class AndroidSpellCheckerSession(service: AndroidSpellCheckerService) : AndroidWordLevelSpellCheckerSession(service) {

    private val mResources: Resources = service.resources
    private var mSentenceLevelAdapter: SentenceLevelAdapter? = null

    private fun fixWronglyInvalidatedWordWithSingleQuote(ti: TextInfo, ssi: SentenceSuggestionsInfo): SentenceSuggestionsInfo? {
        val typedText = ti.charSequence
        if (!typedText.toString().contains(AndroidSpellCheckerService.SINGLE_QUOTE)) {
            return null
        }
        val N = ssi.suggestionsCount
        val additionalOffsets = ArrayList<Int>()
        val additionalLengths = ArrayList<Int>()
        val additionalSuggestionsInfos = ArrayList<SuggestionsInfo>()

        for (i in 0 until N) {
            val si = ssi.getSuggestionsInfoAt(i)
            val flags = si.suggestionsAttributes
            if ((flags and SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) == 0) continue

            val offset = ssi.getOffsetAt(i)
            val length = ssi.getLengthAt(i)
            val subText = typedText.subSequence(offset, offset + length)
            if (!subText.toString().contains(AndroidSpellCheckerService.SINGLE_QUOTE)) continue

            val splitTexts = SpannableStringUtils.split(subText, AndroidSpellCheckerService.SINGLE_QUOTE, true)
            if (splitTexts.size <= 1) continue

            for (splitText in splitTexts) {
                if (splitText.isNullOrEmpty()) continue
                if (mSuggestionsCache.getSuggestionsFromCache(splitText.toString()) == null) continue

                val newLength = splitText.length
                val newFlags = 0
                val newSi = SuggestionsInfo(newFlags, EMPTY_STRING_ARRAY)
                newSi.setCookieAndSequence(si.cookie, si.sequence)

                if (DBG) Log.d(TAG, "Override and remove old span over: $splitText, $offset,$newLength")

                additionalOffsets.add(offset)
                additionalLengths.add(newLength)
                additionalSuggestionsInfos.add(newSi)
            }
        }

        val additionalSize = additionalOffsets.size
        if (additionalSize == 0) return null

        val suggestionsSize = N + additionalSize
        val newOffsets = IntArray(suggestionsSize)
        val newLengths = IntArray(suggestionsSize)
        val newSuggestionsInfos = arrayOfNulls<SuggestionsInfo>(suggestionsSize)

        var i = 0
        while (i < N) {
            newOffsets[i] = ssi.getOffsetAt(i)
            newLengths[i] = ssi.getLengthAt(i)
            newSuggestionsInfos[i] = ssi.getSuggestionsInfoAt(i)
            i++
        }
        while (i < suggestionsSize) {
            newOffsets[i] = additionalOffsets[i - N]
            newLengths[i] = additionalLengths[i - N]
            newSuggestionsInfos[i] = additionalSuggestionsInfos[i - N]
            i++
        }
        return SentenceSuggestionsInfo(newSuggestionsInfos, newOffsets, newLengths)
    }

    override fun onGetSentenceSuggestionsMultiple(textInfos: Array<TextInfo?>?, suggestionsLimit: Int): Array<SentenceSuggestionsInfo?>? {
        val retval = splitAndSuggest(textInfos, suggestionsLimit) ?: return null
        if (retval.size != textInfos?.size) return retval

        for (i in retval.indices) {
            val tempSsi = fixWronglyInvalidatedWordWithSingleQuote(textInfos[i]!!, retval[i]!!)
            if (tempSsi != null) {
                retval[i] = tempSsi
            }
        }
        return retval
    }

    private fun splitAndSuggest(textInfos: Array<TextInfo?>?, suggestionsLimit: Int): Array<SentenceSuggestionsInfo?>? {
        if (textInfos == null || textInfos.isEmpty()) {
            @Suppress("UNCHECKED_CAST")
            return SentenceLevelAdapter.getEmptySentenceSuggestionsInfo() as Array<SentenceSuggestionsInfo?>
        }
        var sentenceLevelAdapter: SentenceLevelAdapter?
        synchronized(this) {
            sentenceLevelAdapter = mSentenceLevelAdapter
            if (sentenceLevelAdapter == null) {
                val localeString = getLocale()
                if (!localeString.isNullOrEmpty()) {
                    sentenceLevelAdapter = SentenceLevelAdapter(mResources, localeString.constructLocale())
                    mSentenceLevelAdapter = sentenceLevelAdapter
                }
            }
        }
        if (sentenceLevelAdapter == null) {
            @Suppress("UNCHECKED_CAST")
            return SentenceLevelAdapter.getEmptySentenceSuggestionsInfo() as Array<SentenceSuggestionsInfo?>
        }

        val infosSize = textInfos.size
        val retval = arrayOfNulls<SentenceSuggestionsInfo>(infosSize)
        for (i in 0 until infosSize) {
            val textInfo = textInfos[i] ?: continue
            val textInfoParams = sentenceLevelAdapter!!.getSplitWords(textInfo)
            val mItems = textInfoParams.mItems
            val itemsSize = mItems.size
            val splitTextInfos = arrayOfNulls<TextInfo>(itemsSize)
            for (j in 0 until itemsSize) {
                splitTextInfos[j] = mItems[j].mTextInfo
            }
            @Suppress("UNCHECKED_CAST")
            retval[i] = SentenceLevelAdapter.reconstructSuggestions(
                textInfoParams, onGetSuggestionsMultiple(splitTextInfos as Array<TextInfo?>, suggestionsLimit, true)
            )
        }
        return retval
    }

    override fun onGetSuggestionsMultiple(textInfos: Array<TextInfo?>?, suggestionsLimit: Int, sequentialWords: Boolean): Array<SuggestionsInfo?> {
        val ident = Binder.clearCallingIdentity()
        try {
            if (textInfos == null) return emptyArray()
            val length = textInfos.size
            val retval = arrayOfNulls<SuggestionsInfo>(length)
            for (i in 0 until length) {
                val textInfo = textInfos[i]
                if (textInfo == null) {
                    retval[i] = AndroidSpellCheckerService.getNotInDictEmptySuggestions(false)
                    continue
                }
                val prevWord: CharSequence? = if (sequentialWords && i > 0) {
                    val prevTextInfo = textInfos[i - 1]
                    val prevWordCandidate = prevTextInfo?.charSequence
                    if (prevWordCandidate.isNullOrEmpty()) null else prevWordCandidate
                } else {
                    null
                }
                val ngramContext = NgramContext(NgramContext.WordInfo(prevWord?.toString()))
                val info = onGetSuggestionsInternal(textInfo, ngramContext, suggestionsLimit)
                info.setCookieAndSequence(textInfo.cookie, textInfo.sequence)
                retval[i] = info
            }
            return retval
        } finally {
            Binder.restoreCallingIdentity(ident)
        }
    }

    companion object {
        private const val TAG = "AndroidSpellCheckerSession"
        private const val DBG = false
    }
}
