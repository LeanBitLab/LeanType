/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import android.text.TextUtils
import android.util.SparseIntArray
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.CollectionUtils
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.StringUtils
import java.util.ArrayList
import java.util.HashSet
import java.util.Locale

class PopupKeySpec(popupKeySpec: String, needsToUpperCase: Boolean, locale: Locale) {
    val mCode: Int
    val mLabel: String?
    val mOutputText: String?
    val mIconName: String?

    init {
        if (popupKeySpec.isEmpty()) throw KeySpecParser.KeySpecParserError("Empty popup key spec")
        val label = KeySpecParser.getLabel(popupKeySpec)
        mLabel = if (needsToUpperCase) StringUtils.toTitleCaseOfKeyLabel(label, locale) else label
        val codeInSpec = KeySpecParser.getCode(popupKeySpec)
        val code = if (needsToUpperCase) StringUtils.toTitleCaseOfKeyCode(codeInSpec, locale) else codeInSpec
        if (code == KeyCode.NOT_SPECIFIED) {
            mCode = KeyCode.MULTIPLE_CODE_POINTS
            mOutputText = mLabel
        } else {
            mCode = code
            val outputText = KeySpecParser.getOutputText(popupKeySpec, code)
            mOutputText = if (needsToUpperCase) StringUtils.toTitleCaseOfKeyLabel(outputText, locale) else outputText
        }
        mIconName = KeySpecParser.getIconName(popupKeySpec)
    }

    fun buildKey(x: Int, y: Int, labelFlags: Int, background: Int, params: KeyboardParams): Key {
        return Key(
            mLabel, mIconName, mCode, mOutputText, null, labelFlags, background, x, y,
            params.mDefaultAbsoluteKeyWidth, params.mDefaultAbsoluteRowHeight, params.mHorizontalGap, params.mVerticalGap
        )
    }

    override fun hashCode(): Int {
        var hashCode = 31 + mCode
        hashCode = hashCode * 31 + (mIconName?.hashCode() ?: 0)
        hashCode = hashCode * 31 + (mLabel?.hashCode() ?: 0)
        hashCode = hashCode * 31 + (mOutputText?.hashCode() ?: 0)
        return hashCode
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PopupKeySpec) return false
        return mCode == other.mCode &&
                TextUtils.equals(mIconName, other.mIconName) &&
                TextUtils.equals(mLabel, other.mLabel) &&
                TextUtils.equals(mOutputText, other.mOutputText)
    }

    override fun toString(): String {
        val label = if (mIconName == null) mLabel else KeyboardIconsSet.PREFIX_ICON + mIconName
        val output = if (mCode == KeyCode.MULTIPLE_CODE_POINTS) mOutputText else Constants.printableCode(mCode)
        return if (StringUtils.codePointCount(label) == 1 && label!!.codePointAt(0) == mCode) {
            output!!
        } else {
            "$label|$output"
        }
    }

    class LettersOnBaseLayout {
        private val mCodes = SparseIntArray()
        private val mTexts = HashSet<String?>()

        fun addLetter(key: Key.KeyParams) {
            val code = key.mCode
            if (code > 32) {
                mCodes.put(code, 0)
            } else if (code == KeyCode.MULTIPLE_CODE_POINTS) {
                mTexts.add(key.outputText)
            }
        }

        fun contains(popupKey: PopupKeySpec): Boolean {
            val code = popupKey.mCode
            return if (mCodes.indexOfKey(code) >= 0) {
                true
            } else code == KeyCode.MULTIPLE_CODE_POINTS && mTexts.contains(popupKey.mOutputText)
        }
    }

    companion object {
        private const val COMMA = ','
        private const val BACKSLASH = '\\'
        private val ADDITIONAL_POPUP_KEY_MARKER = StringUtils.newSingleCodePointString(Constants.CODE_PERCENT)
        private val EMPTY_STRING_ARRAY = arrayOf<String>()

        fun removeRedundantPopupKeys(popupKeys: Array<PopupKeySpec>?, lettersOnBaseLayout: LettersOnBaseLayout): Array<PopupKeySpec>? {
            if (popupKeys == null) return null
            val filteredPopupKeys = ArrayList<PopupKeySpec>()
            for (popupKey in popupKeys) {
                if (!lettersOnBaseLayout.contains(popupKey)) {
                    filteredPopupKeys.add(popupKey)
                }
            }
            val size = filteredPopupKeys.size
            if (size == popupKeys.size) return popupKeys
            if (size == 0) return null
            return filteredPopupKeys.toTypedArray()
        }

        fun splitKeySpecs(text: String?): Array<String>? {
            if (TextUtils.isEmpty(text)) return null
            val size = text!!.length
            if (size == 1) {
                return if (text[0] == COMMA) null else arrayOf(text)
            }

            var list: ArrayList<String>? = null
            var start = 0
            var pos = 0
            while (pos < size) {
                val c = text[pos]
                if (c == COMMA) {
                    if (pos - start > 0) {
                        if (list == null) list = ArrayList()
                        list.add(text.substring(start, pos))
                    }
                    start = pos + 1
                } else if (c == BACKSLASH) {
                    pos++
                }
                pos++
            }
            val remain = if (size - start > 0) text.substring(start) else null
            if (list == null) {
                return if (remain != null) arrayOf(remain) else null
            }
            if (remain != null) list.add(remain)
            return list.toTypedArray()
        }

        fun filterOutEmptyString(array: Array<String>?): Array<String> {
            if (array == null) return EMPTY_STRING_ARRAY
            var out: ArrayList<String>? = null
            for (i in array.indices) {
                val entry = array[i]
                if (TextUtils.isEmpty(entry)) {
                    if (out == null) out = CollectionUtils.arrayAsList(array, 0, i)
                } else if (out != null) {
                    out.add(entry)
                }
            }
            return out?.toTypedArray() ?: array
        }

        fun insertAdditionalPopupKeys(popupKeySpecs: Array<String>?, additionalPopupKeySpecs: Array<String>?): Array<String>? {
            val popupKeys = filterOutEmptyString(popupKeySpecs)
            val additionalPopupKeys = filterOutEmptyString(additionalPopupKeySpecs)
            val popupKeysCount = popupKeys.size
            val additionalCount = additionalPopupKeys.size
            var out: ArrayList<String>? = null
            var additionalIndex = 0
            for (popupKeyIndex in 0 until popupKeysCount) {
                val popupKeySpec = popupKeys[popupKeyIndex]
                if (popupKeySpec == ADDITIONAL_POPUP_KEY_MARKER) {
                    if (additionalIndex < additionalCount) {
                        val additionalPopupKey = additionalPopupKeys[additionalIndex]
                        if (out != null) {
                            out.add(additionalPopupKey)
                        } else {
                            popupKeys[popupKeyIndex] = additionalPopupKey
                        }
                        additionalIndex++
                    } else {
                        if (out == null) out = CollectionUtils.arrayAsList(popupKeys, 0, popupKeyIndex)
                    }
                } else {
                    out?.add(popupKeySpec)
                }
            }
            if (additionalCount > 0 && additionalIndex == 0) {
                out = CollectionUtils.arrayAsList(additionalPopupKeys, additionalIndex, additionalCount)
                for (i in 0 until popupKeysCount) out.add(popupKeys[i])
            } else if (additionalIndex < additionalCount) {
                out = CollectionUtils.arrayAsList(popupKeys, 0, popupKeysCount)
                for (i in additionalIndex until additionalCount) out.add(additionalPopupKeys[i])
            }
            return when {
                out == null && popupKeysCount > 0 -> popupKeys
                out != null && out.isNotEmpty() -> out.toTypedArray()
                else -> null
            }
        }

        fun getIntValue(popupKeys: Array<String?>?, key: String, defaultValue: Int): Int {
            if (popupKeys == null) return defaultValue
            val keyLen = key.length
            var foundValue = false
            var value = defaultValue
            for (i in popupKeys.indices) {
                val popupKeySpec = popupKeys[i]
                if (popupKeySpec == null || !popupKeySpec.startsWith(key)) continue
                popupKeys[i] = null
                try {
                    if (!foundValue) {
                        value = popupKeySpec.substring(keyLen).toInt()
                        foundValue = true
                    }
                } catch (e: NumberFormatException) {
                    throw RuntimeException("integer should follow after $key: $popupKeySpec")
                }
            }
            return value
        }

        fun getBooleanValue(popupKeys: Array<String?>?, key: String): Boolean {
            if (popupKeys == null) return false
            var value = false
            for (i in popupKeys.indices) {
                val popupKeySpec = popupKeys[i]
                if (popupKeySpec == null || popupKeySpec != key) continue
                popupKeys[i] = null
                value = true
            }
            return value
        }
    }
}
