// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.dictionary

import android.content.Context
import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.latin.AppsManager
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.Locale

class AppsBinaryDictionary private constructor(
    ctx: Context,
    locale: Locale?,
    dictFile: File?,
    name: String
) : ExpandableBinaryDictionary(
    ctx,
    getDictName(name, locale, dictFile),
    locale,
    Dictionary.TYPE_APPS,
    dictFile
), AppsManager.AppsChangedListener {

    private val mAppsManager = AppsManager(ctx)

    init {
        mAppsManager.registerForUpdates(this)
        reloadDictionaryIfRequired()
    }

    companion object {
        private const val TAG = "AppsBinaryDictionary"
        private const val NAME = "apps"

        private const val FREQUENCY_FOR_APPS = 100
        private const val FREQUENCY_FOR_APPS_BIGRAM = 200

        private const val DEBUG = false
        private const val DEBUG_DUMP = false

        fun getDictionary(
            context: Context,
            locale: Locale?,
            dictFile: File?,
            dictNamePrefix: String
        ): AppsBinaryDictionary {
            return AppsBinaryDictionary(context, locale, dictFile, dictNamePrefix + NAME)
        }
    }

    @Synchronized
    override fun close() {
        mAppsManager.close()
        super.close()
    }

    override fun onAppsChanged() {
        setNeedsToRecreate()
    }

    override fun loadInitialContentsLocked() {
        loadDictionaryLocked()
    }

    private fun loadDictionaryLocked() {
        for (name in mAppsManager.getNames()) {
            addNameLocked(name)
        }
    }

    private fun addNameLocked(appLabel: String) {
        var ngramContext = NgramContext.getEmptyPrevWordsContext(
            BinaryDictionary.MAX_PREV_WORD_COUNT_FOR_N_GRAM
        )
        for (word in appLabel.split(Regex("\\s+"))) {
            if (word.isEmpty()) continue
            if (DEBUG_DUMP) {
                Log.d(TAG, "addName word = $word")
            }
            val wordLen = StringUtils.codePointCount(word)
            if (1 < wordLen && wordLen <= MAX_WORD_LENGTH) {
                if (DEBUG) {
                    Log.d(TAG, "addName $appLabel, $word, $ngramContext")
                }
                runGCIfRequiredLocked(true)
                addUnigramLocked(
                    word, FREQUENCY_FOR_APPS,
                    null, 0, false,
                    false,
                    BinaryDictionary.NOT_A_VALID_TIMESTAMP
                )
                if (ngramContext.isValid) {
                    runGCIfRequiredLocked(true)
                    addNgramEntryLocked(
                        ngramContext,
                        word,
                        FREQUENCY_FOR_APPS_BIGRAM,
                        BinaryDictionary.NOT_A_VALID_TIMESTAMP
                    )
                }
                ngramContext = ngramContext.getNextNgramContext(
                    NgramContext.WordInfo(word)
                )
            }
        }
    }
}
