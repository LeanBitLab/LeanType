// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.dictionary

import helium314.keyboard.event.HangulCombiner
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.makedict.WordProperty
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import java.text.Normalizer

class KoreanDictionary(
    private val mDictionary: Dictionary
) : Dictionary(mDictionary.mDictType, mDictionary.mLocale) {

    private val COMPAT_JAMO = HangulCombiner.HangulJamo.COMPAT_CONSONANTS + HangulCombiner.HangulJamo.COMPAT_VOWELS
    private val STANDARD_JAMO = HangulCombiner.HangulJamo.CONVERT_INITIALS + HangulCombiner.HangulJamo.CONVERT_MEDIALS

    private fun processInput(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        val result = StringBuilder()
        for (c in normalized.toCharArray()) {
            val index = COMPAT_JAMO.indexOf(c)
            if (index == -1) result.append(c)
            else result.append(STANDARD_JAMO[index])
        }
        return result.toString()
    }

    private fun processOutput(output: String): String {
        return Normalizer.normalize(output, Normalizer.Form.NFC)
    }

    override fun getSuggestions(
        composedData: ComposedData,
        ngramContext: NgramContext,
        proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion,
        sessionId: Int,
        weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?
    ): ArrayList<SuggestedWords.SuggestedWordInfo>? {
        val processedData = ComposedData(
            composedData.mInputPointers,
            composedData.mIsBatchMode,
            processInput(composedData.mTypedWord)
        )
        val suggestions = mDictionary.getSuggestions(
            processedData,
            ngramContext,
            proximityInfoHandle,
            settingsValuesForSuggestion,
            sessionId,
            weightForLocale,
            inOutWeightOfLangModelVsSpatialModel
        ) ?: return null

        val result = ArrayList<SuggestedWords.SuggestedWordInfo>()
        for (info in suggestions) {
            result.add(
                SuggestedWords.SuggestedWordInfo(
                    processOutput(info.mWord),
                    info.mPrevWordsContext,
                    info.mScore,
                    info.mKindAndFlags,
                    info.mSourceDict,
                    info.mIndexOfTouchPointOfSecondWord,
                    info.mAutoCommitFirstWordConfidence
                )
            )
        }
        return result
    }

    override fun isInDictionary(word: String): Boolean {
        return mDictionary.isInDictionary(processInput(word))
    }

    override fun getFrequency(word: String): Int {
        return mDictionary.getFrequency(processInput(word))
    }

    override fun getMaxFrequencyOfExactMatches(word: String): Int {
        return mDictionary.getMaxFrequencyOfExactMatches(processInput(word))
    }

    override fun getWordProperty(word: String, isBeginningOfSentence: Boolean): WordProperty? {
        return mDictionary.getWordProperty(processInput(word), isBeginningOfSentence)
    }

    override fun same(word: CharArray, length: Int, typedWord: String): Boolean {
        val processedWord = processInput(String(word)).toCharArray()
        val processedTypedWord = processInput(typedWord)
        return mDictionary.same(processedWord, length, processedTypedWord)
    }

    override fun close() {
        mDictionary.close()
    }

    override fun onFinishInput() {
        mDictionary.onFinishInput()
    }

    override val isInitialized: Boolean
        get() = mDictionary.isInitialized

    override fun shouldAutoCommit(candidate: SuggestedWords.SuggestedWordInfo): Boolean {
        return mDictionary.shouldAutoCommit(candidate)
    }

    override fun isUserSpecific(): Boolean {
        return mDictionary.isUserSpecific()
    }
}
