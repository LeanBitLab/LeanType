/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.NgramContext.WordInfo
import helium314.keyboard.latin.define.DecoderSpecificConstants
import helium314.keyboard.latin.settings.SpacingAndPunctuations
import java.util.regex.Pattern

object NgramContextUtils {
    private val NEWLINE_REGEX = Pattern.compile("[\\r\\n]+")
    private val SPACE_REGEX = Pattern.compile("\\s+")

    fun getNgramContextFromNthPreviousWord(
        prev: CharSequence?,
        spacingAndPunctuations: SpacingAndPunctuations,
        n: Int
    ): NgramContext {
        if (prev == null) return NgramContext.EMPTY_PREV_WORDS_INFO

        var lastNewlineIdx = -1
        for (i in prev.length - 1 downTo 0) {
            val c = prev[i]
            if (c == '\n' || c == '\r') {
                lastNewlineIdx = i
                break
            }
        }

        if (lastNewlineIdx != -1) {
            var hasNonWhitespaceAfter = false
            for (i in lastNewlineIdx + 1 until prev.length) {
                if (!Character.isWhitespace(prev[i])) {
                    hasNonWhitespaceAfter = true
                    break
                }
            }
            if (!hasNonWhitespaceAfter) {
                return NgramContext(WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO)
            }
        }

        val lines = NEWLINE_REGEX.split(prev)
        if (lines.isEmpty()) {
            return NgramContext(WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO)
        }

        val w = SPACE_REGEX.split(lines[lines.size - 1])
        val list = ArrayList<WordInfo>()

        for (i in 0 until DecoderSpecificConstants.MAX_PREV_WORD_COUNT_FOR_N_GRAM) {
            val focusedWordIndex = w.size - n - i

            // Referring to the word after the focused word.
            if ((focusedWordIndex + 1) >= 0 && (focusedWordIndex + 1) < w.size) {
                val wordFollowingTheNthPrevWord = w[focusedWordIndex + 1]
                if (wordFollowingTheNthPrevWord.isNotEmpty()) {
                    val firstChar = wordFollowingTheNthPrevWord[0]
                    if (spacingAndPunctuations.isWordConnector(firstChar.code)) {
                        // The word following the focused word is starting with a word connector.
                        break
                    }
                }
            }

            // If we can't find (n + i) words, the context is beginning-of-sentence.
            if (focusedWordIndex < 0) {
                list.add(WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO)
                break
            }

            val focusedWord = w[focusedWordIndex]

            // If the word is empty, the context is beginning-of-sentence.
            val length = focusedWord.length
            if (length <= 0) {
                list.add(WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO)
                break
            }

            // If the word ends in a sentence terminator, the context is beginning-of-sentence.
            val lastChar = focusedWord[length - 1]
            if (spacingAndPunctuations.isSentenceTerminator(lastChar.code)) {
                list.add(WordInfo.BEGINNING_OF_SENTENCE_WORD_INFO)
                break
            }

            // If ends in a word separator or connector, the context is unclear.
            if (spacingAndPunctuations.isWordSeparator(lastChar.code)
                || spacingAndPunctuations.isWordConnector(lastChar.code)
            ) {
                break
            }

            list.add(WordInfo(focusedWord))
        }

        return if (list.isEmpty()) {
            NgramContext.EMPTY_PREV_WORDS_INFO
        } else {
            NgramContext(*list.toTypedArray())
        }
    }
}
