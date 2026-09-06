/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.dictionary

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.Contacts
import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.latin.ContactsDictionaryConstants
import helium314.keyboard.latin.ContactsDictionaryUtils
import helium314.keyboard.latin.ContactsManager
import helium314.keyboard.latin.ContactsManager.ContactsChangedListener
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.permissions.PermissionsUtil
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.Locale

class ContactsBinaryDictionary protected constructor(
    context: Context,
    locale: Locale,
    dictFile: File?,
    name: String
) : ExpandableBinaryDictionary(
    context,
    getDictName(name, locale, dictFile),
    locale,
    Dictionary.TYPE_CONTACTS,
    dictFile
), ContactsChangedListener {

    private val mUseFirstLastBigrams: Boolean = ContactsDictionaryUtils.useFirstLastBigramsForLocale(locale)
    private val mContactsManager = ContactsManager(context)

    init {
        mContactsManager.registerForUpdates(this)
        reloadDictionaryIfRequired()
    }

    companion object {
        private const val TAG = "ContactsBinaryDictionary"
        private const val NAME = "contacts"
        private const val DEBUG = false
        private const val DEBUG_DUMP = false

        @JvmStatic
        fun getDictionary(
            context: Context,
            locale: Locale,
            dictFile: File?,
            dictNamePrefix: String
        ): ContactsBinaryDictionary {
            return ContactsBinaryDictionary(context, locale, dictFile, dictNamePrefix + NAME)
        }
    }

    @Synchronized
    override fun close() {
        mContactsManager.close()
        super.close()
    }

    override fun loadInitialContentsLocked() {
        loadDictionaryForUriLocked(ContactsContract.Profile.CONTENT_URI)
        loadDictionaryForUriLocked(Contacts.CONTENT_URI)
    }

    private fun loadDictionaryForUriLocked(uri: Uri) {
        if (!PermissionsUtil.checkAllPermissionsGranted(
                mContext, Manifest.permission.READ_CONTACTS)
        ) {
            Log.i(TAG, "No permission to read contacts. Not loading the Dictionary.")
        }

        val validNames = mContactsManager.getValidNames(uri)
        for (name in validNames) {
            addNameLocked(name)
        }
        if (uri == Contacts.CONTENT_URI) {
            mContactsManager.updateLocalState(validNames)
        }
    }

    private fun addNameLocked(name: String) {
        val len = StringUtils.codePointCount(name)
        var ngramContext = NgramContext.getEmptyPrevWordsContext(
            BinaryDictionary.MAX_PREV_WORD_COUNT_FOR_N_GRAM
        )
        var i = 0
        while (i < len) {
            if (Character.isLetter(name.codePointAt(i))) {
                val end = ContactsDictionaryUtils.getWordEndPosition(name, len, i)
                val word = name.substring(i, end)
                if (DEBUG_DUMP) {
                    Log.d(TAG, "addName word = " + word)
                }
                i = end - 1
                val wordLen = StringUtils.codePointCount(word)
                if (wordLen <= MAX_WORD_LENGTH && wordLen > 1) {
                    if (DEBUG) {
                        Log.d(TAG, "addName " + name + ", " + word + ", " + ngramContext)
                    }
                    runGCIfRequiredLocked(true)
                    addUnigramLocked(
                        word, ContactsDictionaryConstants.FREQUENCY_FOR_CONTACTS,
                        null, 0, false,
                        false,
                        BinaryDictionary.NOT_A_VALID_TIMESTAMP
                    )
                    if (ngramContext.isValid && mUseFirstLastBigrams) {
                        runGCIfRequiredLocked(true)
                        addNgramEntryLocked(
                            ngramContext,
                            word,
                            ContactsDictionaryConstants.FREQUENCY_FOR_CONTACTS_BIGRAM,
                            BinaryDictionary.NOT_A_VALID_TIMESTAMP
                        )
                    }
                    ngramContext = ngramContext.getNextNgramContext(
                        NgramContext.WordInfo(word)
                    )
                }
            }
            i++
        }
    }

    override fun onContactsChange() {
        setNeedsToRecreate()
    }
}
