/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import android.text.InputType
import android.text.TextUtils
import helium314.keyboard.latin.WordComposer
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.settings.SpacingAndPunctuations
import java.util.Locale

object CapsModeUtils {
    /**
     * Apply an auto-caps mode to a string.
     * <p>
     * This intentionally does NOT apply manual caps mode. It only changes the capitalization if
     * the mode is one of the auto-caps modes.
     * @param s The string to capitalize.
     * @param capitalizeMode The mode in which to capitalize.
     * @param locale The locale for capitalizing.
     * @return The capitalized string.
     */
    @JvmStatic
    fun applyAutoCapsMode(s: String, capitalizeMode: Int, locale: Locale): String {
        return if (WordComposer.CAPS_MODE_AUTO_SHIFT_LOCKED == capitalizeMode) {
            s.uppercase(locale)
        } else if (WordComposer.CAPS_MODE_AUTO_SHIFTED == capitalizeMode) {
            StringUtils.capitalizeFirstCodePoint(s, locale)
        } else {
            s
        }
    }

    /**
     * Return whether a constant represents an auto-caps mode (either auto-shift or auto-shift-lock)
     * @param mode The mode to test for
     * @return true if this represents an auto-caps mode, false otherwise
     */
    @JvmStatic
    fun isAutoCapsMode(mode: Int): Boolean {
        return (WordComposer.CAPS_MODE_AUTO_SHIFTED == mode
                || WordComposer.CAPS_MODE_AUTO_SHIFT_LOCKED == mode)
    }

    /**
     * Helper method to find out if a code point is starting punctuation.
     * <p>
     * This include the Unicode START_PUNCTUATION category, but also some other symbols that are
     * starting, like the inverted question mark or the double quote.
     *
     * @param codePoint the code point
     * @return true if it's starting punctuation, false otherwise.
     */
    private fun isStartPunctuation(codePoint: Int): Boolean {
        return (codePoint == Constants.CODE_DOUBLE_QUOTE || codePoint == Constants.CODE_SINGLE_QUOTE
                || codePoint == Constants.CODE_INVERTED_QUESTION_MARK
                || codePoint == Constants.CODE_INVERTED_EXCLAMATION_MARK
                || Character.getType(codePoint).toInt() == Character.START_PUNCTUATION.toInt())
    }

    /**
     * Determine what caps mode should be in effect at the current offset in
     * the text. Only the mode bits set in <var>reqModes</var> will be
     * checked. Note that the caps mode flags here are explicitly defined
     * to match those in {@link InputType}.
     *
     * @param cs The text that should be checked for caps modes.
     * @param reqModes The modes to be checked: may be any combination of
     * {@link TextUtils#CAP_MODE_CHARACTERS}, {@link TextUtils#CAP_MODE_WORDS}, and
     * {@link TextUtils#CAP_MODE_SENTENCES}.
     * @param spacingAndPunctuations The current spacing and punctuations settings.
     * @param hasSpaceBefore Whether we should consider there is a space inserted at the end of cs
     *
     * @return Returns the actual capitalization modes that can be in effect
     * at the current position, which is any combination of
     * {@link TextUtils#CAP_MODE_CHARACTERS}, {@link TextUtils#CAP_MODE_WORDS}, and
     * {@link TextUtils#CAP_MODE_SENTENCES}.
     */
    @JvmStatic
    fun getCapsMode(
        cs: CharSequence,
        reqModes: Int,
        spacingAndPunctuations: SpacingAndPunctuations,
        hasSpaceBefore: Boolean
    ): Int {
        if ((reqModes and (TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES)) == 0) {
            return TextUtils.CAP_MODE_CHARACTERS and reqModes
        }

        var i: Int
        if (hasSpaceBefore) {
            i = cs.length + 1
        } else {
            i = cs.length
            while (i > 0) {
                val c = cs[i - 1]
                if (!isStartPunctuation(c.code)) {
                    break
                }
                i--
            }
        }

        var j = i
        var prevChar = Constants.CODE_SPACE.toChar()
        if (hasSpaceBefore) --j
        while (j > 0) {
            prevChar = cs[j - 1]
            if (!Character.isSpaceChar(prevChar) && prevChar.code != Constants.CODE_TAB) break
            j--
        }
        if (j <= 0 || Character.isWhitespace(prevChar)) {
            if (spacingAndPunctuations.mUsesGermanRules) {
                var hasNewLine = false
                while (--j >= 0 && Character.isWhitespace(prevChar)) {
                    if (Constants.CODE_ENTER == prevChar.code) {
                        hasNewLine = true
                    }
                    prevChar = cs[j]
                }
                if (Constants.CODE_COMMA == prevChar.code && hasNewLine) {
                    return (TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS) and reqModes
                }
            }
            return (TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS
                    or TextUtils.CAP_MODE_SENTENCES) and reqModes
        }
        if (i == j) {
            return TextUtils.CAP_MODE_CHARACTERS and reqModes
        }
        if ((reqModes and TextUtils.CAP_MODE_SENTENCES) == 0) {
            return (TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS) and reqModes
        }

        if (spacingAndPunctuations.mUsesAmericanTypography) {
            while (j > 0) {
                val c = cs[j - 1]
                if (c.code != Constants.CODE_DOUBLE_QUOTE && c.code != Constants.CODE_SINGLE_QUOTE
                        && Character.getType(c.code).toInt() != Character.END_PUNCTUATION.toInt()) {
                    break
                }
                j--
            }
        }

        if (j <= 0) return TextUtils.CAP_MODE_CHARACTERS and reqModes
        var c = cs[--j]

        if (spacingAndPunctuations.isSentenceTerminator(c.code)
            && !spacingAndPunctuations.isAbbreviationMarker(c.code)) {
            return (TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS
                    or TextUtils.CAP_MODE_SENTENCES) and reqModes
        }
        if (!spacingAndPunctuations.isSentenceSeparator(c.code) || j <= 0) {
            return (TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS) and reqModes
        }

        val start = 0
        val word = 1
        val period = 2
        val letter = 3
        val number = 4
        val caps = (TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS
                or TextUtils.CAP_MODE_SENTENCES) and reqModes
        val noCaps = (TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS) and reqModes
        var state = start
        while (j > 0) {
            c = cs[--j]
            when (state) {
                start -> {
                    if (Character.isLetter(c)) {
                        state = word
                    } else if (Character.isWhitespace(c)) {
                        return noCaps
                    } else if (Character.isDigit(c) && spacingAndPunctuations.mUsesGermanRules) {
                        state = number
                    } else {
                        return caps
                    }
                }
                word -> {
                    if (Character.isLetter(c)) {
                        state = word
                    } else if (spacingAndPunctuations.isSentenceSeparator(c.code)) {
                        state = period
                    } else {
                        return caps
                    }
                }
                period -> {
                    if (Character.isLetter(c)) {
                        state = letter
                    } else {
                        return caps
                    }
                }
                letter -> {
                    if (Character.isLetter(c)) {
                        state = letter
                    } else if (spacingAndPunctuations.isSentenceSeparator(c.code)) {
                        state = period
                    } else {
                        return noCaps
                    }
                }
                number -> {
                    if (Character.isLetter(c)) {
                        state = word
                    } else if (Character.isDigit(c)) {
                        state = number
                    } else {
                        return noCaps
                    }
                }
            }
        }
        return if (start == state || letter == state) noCaps else caps
    }

    /**
     * Convert capitalize mode flags into human readable text.
     *
     * @param capsFlags The modes flags to be converted.
     * @return the text that describes the capsMode.
     */
    @JvmStatic
    fun flagsToString(capsFlags: Int): String {
        val capsFlagsMask = (TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS
                or TextUtils.CAP_MODE_SENTENCES)
        if ((capsFlags and capsFlagsMask.inv()) != 0) {
            return "unknown<0x${Integer.toHexString(capsFlags)}>"
        }
        val builder = ArrayList<String>()
        if ((capsFlags and TextUtils.CAP_MODE_CHARACTERS) != 0) {
            builder.add("characters")
        }
        if ((capsFlags and TextUtils.CAP_MODE_WORDS) != 0) {
            builder.add("words")
        }
        if ((capsFlags and TextUtils.CAP_MODE_SENTENCES) != 0) {
            builder.add("sentences")
        }
        return if (builder.isEmpty()) "none" else TextUtils.join("|", builder)
    }
}
