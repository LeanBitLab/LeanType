/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.define.DebugFlags

object KeySpecParser {
    private const val BACKSLASH = '\\'
    private const val VERTICAL_BAR = '|'
    private const val PREFIX_HEX = "0x"

    private fun hasIcon(keySpec: String): Boolean = keySpec.startsWith(KeyboardIconsSet.PREFIX_ICON)

    private fun hasCode(keySpec: String, labelEnd: Int): Boolean {
        if (labelEnd <= 0 || labelEnd + 1 >= keySpec.length) return false
        if (keySpec.startsWith(KeyboardCodesSet.PREFIX_CODE, labelEnd + 1)) return true
        return keySpec.startsWith(PREFIX_HEX, labelEnd + 1)
    }

    private fun parseEscape(text: String): String {
        if (text.indexOf(BACKSLASH) < 0) return text
        val length = text.length
        val sb = StringBuilder()
        var pos = 0
        while (pos < length) {
            val c = text[pos]
            if (c == BACKSLASH && pos + 1 < length) {
                pos++
                sb.append(text[pos])
            } else {
                sb.append(c)
            }
            pos++
        }
        return sb.toString()
    }

    private fun indexOfLabelEnd(keySpec: String): Int {
        val length = keySpec.length
        if (keySpec.indexOf(BACKSLASH) < 0) {
            val labelEnd = keySpec.lastIndexOf(VERTICAL_BAR)
            if (labelEnd == 0) {
                if (length == 1) return -1
                if (DebugFlags.DEBUG_ENABLED) throw KeySpecParserError("Empty label")
                return -1
            }
            return labelEnd
        }
        for (pos in length - 1 downTo 0) {
            val c = keySpec[pos]
            if (c != VERTICAL_BAR) continue
            if (pos > 0 && keySpec[pos - 1] == BACKSLASH) {
                continue // Skip escape char
            } else {
                return pos
            }
        }
        return -1
    }

    private fun getBeforeLabelEnd(keySpec: String, labelEnd: Int): String =
        if (labelEnd < 0) keySpec else keySpec.substring(0, labelEnd)

    private fun getAfterLabelEnd(keySpec: String, labelEnd: Int): String =
        keySpec.substring(labelEnd + 1)

    private fun checkDoubleLabelEnd(keySpec: String, labelEnd: Int) {
        if (indexOfLabelEnd(getAfterLabelEnd(keySpec, labelEnd)) < 0) return
        if (DebugFlags.DEBUG_ENABLED) throw KeySpecParserError("Multiple $VERTICAL_BAR: $keySpec")
    }

    fun getLabel(keySpec: String?): String? {
        if (keySpec == null) return null
        if (hasIcon(keySpec)) return null
        val labelEnd = indexOfLabelEnd(keySpec)
        val label = parseEscape(getBeforeLabelEnd(keySpec, labelEnd))
        if (label.isEmpty() && DebugFlags.DEBUG_ENABLED) {
            throw KeySpecParserError("Empty label: $keySpec")
        }
        return label
    }

    private fun getOutputTextInternal(keySpec: String, labelEnd: Int): String? {
        if (labelEnd <= 0) return null
        checkDoubleLabelEnd(keySpec, labelEnd)
        return parseEscape(getAfterLabelEnd(keySpec, labelEnd))
    }

    fun getOutputText(keySpec: String?, code: Int): String? {
        if (keySpec == null) return null
        val labelEnd = indexOfLabelEnd(keySpec)
        if (hasCode(keySpec, labelEnd)) return null
        val outputText = getOutputTextInternal(keySpec, labelEnd)
        if (outputText != null) {
            if (StringUtils.codePointCount(outputText) == 1) return null
            return outputText
        }
        val label = getLabel(keySpec)
        if (label == null) {
            if (keySpec.startsWith(KeyboardIconsSet.PREFIX_ICON) && code != KeyCode.UNSPECIFIED && code != KeyCode.MULTIPLE_CODE_POINTS)
                return null
            throw KeySpecParserError("Empty label: $keySpec")
        }
        return if (StringUtils.codePointCount(label) == 1) null else label
    }

    fun getCode(keySpec: String?): Int {
        if (keySpec == null) return KeyCode.NOT_SPECIFIED
        val labelEnd = indexOfLabelEnd(keySpec)
        if (hasCode(keySpec, labelEnd)) {
            checkDoubleLabelEnd(keySpec, labelEnd)
            return parseCode(getAfterLabelEnd(keySpec, labelEnd), KeyCode.NOT_SPECIFIED)
        }
        val outputText = getOutputTextInternal(keySpec, labelEnd)
        if (outputText != null) {
            if (StringUtils.codePointCount(outputText) == 1) {
                return outputText.codePointAt(0)
            }
            return KeyCode.MULTIPLE_CODE_POINTS
        }
        val label = getLabel(keySpec)
        if (label == null) {
            if (DebugFlags.DEBUG_ENABLED) throw KeySpecParserError("Empty label: $keySpec")
            return KeyCode.MULTIPLE_CODE_POINTS
        }
        return if (StringUtils.codePointCount(label) == 1) label.codePointAt(0) else KeyCode.MULTIPLE_CODE_POINTS
    }

    fun parseCode(text: String?, defaultCode: Int): Int {
        if (text == null) return defaultCode
        if (text.startsWith(KeyboardCodesSet.PREFIX_CODE)) {
            return KeyboardCodesSet.getCode(text.substring(KeyboardCodesSet.PREFIX_CODE.length))
        }
        if (text.startsWith(PREFIX_HEX)) {
            return text.substring(PREFIX_HEX.length).toInt(16)
        }
        return defaultCode
    }

    fun getIconName(keySpec: String?): String? {
        if (keySpec == null) return null
        if (!hasIcon(keySpec)) return null
        val labelEnd = indexOfLabelEnd(keySpec)
        return getBeforeLabelEnd(keySpec, labelEnd).substring(KeyboardIconsSet.PREFIX_ICON.length).intern()
    }

    class KeySpecParserError(message: String) : RuntimeException(message)
}
