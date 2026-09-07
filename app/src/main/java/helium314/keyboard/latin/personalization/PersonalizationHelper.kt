/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.personalization

import android.content.Context
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.common.FileUtils
import java.io.File
import java.io.FilenameFilter
import java.lang.ref.SoftReference
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Helps handle and manage personalized dictionaries such as [UserHistoryDictionary].
 */
object PersonalizationHelper {
    private const val TAG = "PersonalizationHelper"
    private const val DEBUG = false

    private val sLangUserHistoryDictCache = ConcurrentHashMap<String, SoftReference<UserHistoryDictionary>>()

    fun getUserHistoryDictionary(context: Context, locale: Locale): UserHistoryDictionary {
        val lookupStr = locale.toString()
        synchronized(sLangUserHistoryDictCache) {
            val ref = sLangUserHistoryDictCache[lookupStr]
            val dict = ref?.get()
            if (dict != null) {
                if (DEBUG) {
                    Log.d(TAG, "Use cached UserHistoryDictionary with lookup: $lookupStr")
                }
                dict.reloadDictionaryIfRequired()
                return dict
            }
            val newDict = UserHistoryDictionary(context, locale)
            sLangUserHistoryDictCache[lookupStr] = SoftReference(newDict)
            return newDict
        }
    }

    fun removeAllUserHistoryDictionaries(context: Context) {
        synchronized(sLangUserHistoryDictCache) {
            for (ref in sLangUserHistoryDictCache.values) {
                ref?.get()?.clear()
            }
            sLangUserHistoryDictCache.clear()
            val filesDir = context.filesDir
            if (filesDir == null) {
                Log.e(TAG, "context.getFilesDir() returned null.")
                return
            }
            val filesDeleted = FileUtils.deleteFilteredFiles(
                filesDir, DictFilter(UserHistoryDictionary.NAME))
            if (!filesDeleted) {
                Log.e(TAG, "Cannot remove dictionary files. filesDir: ${filesDir.absolutePath}" +
                        ", dictNamePrefix: ${UserHistoryDictionary.NAME}")
            }
        }
    }

    private class DictFilter(private val mName: String) : FilenameFilter {
        override fun accept(dir: File, name: String): Boolean {
            return name.startsWith(mName)
        }
    }
}
