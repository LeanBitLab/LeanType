/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.android.inputmethod.latin.utils

import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.makedict.DictionaryHeader
import helium314.keyboard.latin.makedict.UnsupportedFormatException
import helium314.keyboard.latin.utils.JniUtils
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

object BinaryDictionaryUtils {
    private const val TAG = "BinaryDictionaryUtils"

    init {
        JniUtils.loadNativeLibrary()
    }

    @JvmStatic
    private external fun createEmptyDictFileNative(
        filePath: String, dictVersion: Long,
        locale: String, attributeKeyStringArray: Array<String>, attributeValueStringArray: Array<String>
    ): Boolean

    @JvmStatic
    private external fun calcNormalizedScoreNative(before: IntArray, after: IntArray, score: Int): Float

    @JvmStatic
    private external fun setCurrentTimeForTestNative(currentTime: Int): Int

    @JvmStatic
    @Throws(IOException::class, UnsupportedFormatException::class)
    fun getHeader(dictFile: File): DictionaryHeader {
        return getHeaderWithOffsetAndLength(dictFile, 0 /* offset */, dictFile.length())
    }

    @JvmStatic
    @Throws(IOException::class, UnsupportedFormatException::class)
    fun getHeaderWithOffsetAndLength(dictFile: File, offset: Long, length: Long): DictionaryHeader {
        // dictType is never used for reading the header. Passing an empty string.
        val binaryDictionary = BinaryDictionary(
            dictFile.absolutePath, offset, length,
            true /* useFullEditDistance */, null /* locale */, "" /* dictType */,
            false /* isUpdatable */
        )
        val header = binaryDictionary.getHeader()
        binaryDictionary.close()
        if (header == null) {
            throw IOException()
        }
        return header
    }

    @JvmStatic
    fun renameDict(dictFile: File, newDictFile: File): Boolean {
        if (dictFile.isFile) {
            return dictFile.renameTo(newDictFile)
        } else if (dictFile.isDirectory) {
            val dictName = dictFile.name
            val newDictName = newDictFile.name
            if (newDictFile.exists()) {
                return false
            }
            val files = dictFile.listFiles() ?: return false
            for (file in files) {
                if (!file.isFile) {
                    continue
                }
                val fileName = file.name
                val newFileName = fileName.replaceFirst(
                    Pattern.quote(dictName).toRegex(), Matcher.quoteReplacement(newDictName)
                )
                if (!file.renameTo(File(dictFile, newFileName))) {
                    return false
                }
            }
            return dictFile.renameTo(newDictFile)
        }
        return false
    }

    @JvmStatic
    fun createEmptyDictFile(
        filePath: String, dictVersion: Long,
        locale: Locale, attributeMap: Map<String, String?>
    ): Boolean {
        val keyArray = Array(attributeMap.size) { "" }
        val valueArray = Array(attributeMap.size) { "" }
        var index = 0
        for ((key, value) in attributeMap) {
            keyArray[index] = key
            valueArray[index] = value ?: ""
            index++
        }
        return createEmptyDictFileNative(filePath, dictVersion, locale.toString(), keyArray, valueArray)
    }

    /** normalized score is >= 0, with 0 being a bad match, ~0.1 ok for autocorrect, and ~1.5 a very good match */
    @JvmStatic
    fun calcNormalizedScore(before: String, after: String, score: Int): Float {
        return calcNormalizedScoreNative(
            StringUtils.toCodePointArray(before),
            StringUtils.toCodePointArray(after), score
        )
    }

    /**
     * Control the current time to be used in the native code. If currentTime >= 0, this method sets
     * the current time and gets into test mode.
     * In test mode, set timestamp is used as the current time in the native code.
     * If currentTime < 0, quit the test mode and returns to using time() to get the current time.
     *
     * @param currentTime seconds since the unix epoch
     * @return current time got in the native code.
     */
    @JvmStatic
    fun setCurrentTimeForTest(currentTime: Int): Int {
        return setCurrentTimeForTestNative(currentTime)
    }
}
