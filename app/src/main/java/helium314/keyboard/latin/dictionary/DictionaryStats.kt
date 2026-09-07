/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.dictionary

import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

class DictionaryStats {
    val mLocale: Locale
    val mDictType: String
    val mDictFileName: String?
    val mDictFileSize: Long
    val mContentVersion: Int
    val mWordCount: Int

    constructor(
        locale: Locale,
        dictType: String,
        dictFileName: String?,
        dictFile: File?,
        contentVersion: Int
    ) {
        mLocale = locale
        mDictType = dictType
        mDictFileSize = if (dictFile == null || !dictFile.exists()) 0 else dictFile.length()
        mDictFileName = dictFileName
        mContentVersion = contentVersion
        mWordCount = -1
    }

    constructor(
        locale: Locale,
        dictType: String,
        wordCount: Int
    ) {
        mLocale = locale
        mDictType = dictType
        mDictFileSize = wordCount.toLong()
        mDictFileName = null
        mContentVersion = 0
        mWordCount = wordCount
    }

    fun getFileSizeString(): String {
        val bytes = BigDecimal(mDictFileSize)
        val kb = bytes.divide(BigDecimal(1024), 2, RoundingMode.HALF_UP)
        if (kb.toLong() == 0L) {
            return "$bytes bytes"
        }
        val mb = kb.divide(BigDecimal(1024), 2, RoundingMode.HALF_UP)
        if (mb.toLong() == 0L) {
            return "$kb kb"
        }
        return "$mb Mb"
    }

    override fun toString(): String {
        val builder = StringBuilder(mDictType)
        if (mDictType == Dictionary.TYPE_MAIN) {
            builder.append(" (")
            builder.append(mContentVersion)
            builder.append(")")
        }
        builder.append(": ")
        if (mWordCount > -1) {
            builder.append(mWordCount)
            builder.append(" words")
        } else {
            builder.append(mDictFileName)
            builder.append(" / ")
            builder.append(getFileSizeString())
        }
        return builder.toString()
    }

    companion object {
        fun toString(stats: Iterable<DictionaryStats>): String {
            val builder = StringBuilder("LM Stats")
            for (stat in stats) {
                builder.append("\n    ")
                builder.append(stat.toString())
            }
            return builder.toString()
        }
    }
}
