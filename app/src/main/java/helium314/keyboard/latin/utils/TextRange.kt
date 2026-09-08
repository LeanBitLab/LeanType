/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import android.text.Spanned
import android.text.TextUtils
import android.text.style.SuggestionSpan
import java.util.Objects

/**
 * Represents a range of text, relative to the current cursor position.
 */
class TextRange(
    private val mTextAtCursor: CharSequence,
    private val mWordAtCursorStartIndex: Int,
    private val mWordAtCursorEndIndex: Int,
    private val mCursorIndex: Int,
    val mHasUrlSpans: Boolean
) {
    val mWord: CharSequence

    init {
        if (mWordAtCursorStartIndex < 0 || mCursorIndex < mWordAtCursorStartIndex
            || mCursorIndex > mWordAtCursorEndIndex
            || mWordAtCursorEndIndex > mTextAtCursor.length) {
            throw IndexOutOfBoundsException()
        }
        mWord = mTextAtCursor.subSequence(mWordAtCursorStartIndex, mWordAtCursorEndIndex)
    }

    fun getNumberOfCharsInWordBeforeCursor(): Int {
        return mCursorIndex - mWordAtCursorStartIndex
    }

    fun getNumberOfCharsInWordAfterCursor(): Int {
        return mWordAtCursorEndIndex - mCursorIndex
    }

    fun length(): Int {
        return mWord.length
    }

    /**
     * Gets the suggestion spans that are put squarely on the word, with the exact start
     * and end of the span matching the boundaries of the word.
     * @return the array of spans.
     */
    fun getSuggestionSpansAtWord(): Array<SuggestionSpan> {
        if (mTextAtCursor !is Spanned || mWord !is Spanned) {
            return arrayOf()
        }
        val text = mTextAtCursor
        // Note: it's fine to pass indices negative or greater than the length of the string
        // to the #getSpans() method. The reason we need to get from -1 to +1 is that, the
        // spans were cut at the cursor position, and #getSpans(start, end) does not return
        // spans that end at `start' or begin at `end'. Consider the following case:
        //              this| is          (The | symbolizes the cursor position
        //              ---- ---
        // In this case, the cursor is in position 4, so the 0~7 span has been split into
        // a 0~4 part and a 4~7 part.
        // If we called #getSpans(0, 4) in this case, we would only get the part from 0 to 4
        // of the span, and not the part from 4 to 7, so we would not realize the span actually
        // extends from 0 to 7. But if we call #getSpans(-1, 5) we'll get both the 0~4 and
        // the 4~7 spans and we can merge them accordingly.
        // Any span starting more than 1 char away from the word boundaries in any direction
        // does not touch the word, so we don't need to consider it. That's why requesting
        // -1 ~ +1 is enough.
        // Of course this is only relevant if the cursor is at one end of the word. If it's
        // in the middle, the -1 and +1 are not necessary, but they are harmless.
        @Suppress("UNCHECKED_CAST")
        val spans = text.getSpans(
            mWordAtCursorStartIndex - 1,
            mWordAtCursorEndIndex + 1,
            SuggestionSpan::class.java
        ) as Array<SuggestionSpan?>

        var readIndex = 0
        var writeIndex = 0
        while (readIndex < spans.size) {
            val span = spans[readIndex]
            if (span != null) {
                // Tentative span start and end. This may be modified later if we realize the
                // same span is also applied to other parts of the string.
                var spanStart = text.getSpanStart(span)
                var spanEnd = text.getSpanEnd(span)
                for (i in readIndex + 1 until spans.size) {
                    val otherSpan = spans[i]
                    if (span == otherSpan) {
                        // We found the same span somewhere else. Read the new extent of this
                        // span, and adjust our values accordingly.
                        spanStart = minOf(spanStart, text.getSpanStart(otherSpan))
                        spanEnd = maxOf(spanEnd, text.getSpanEnd(otherSpan))
                        // ...and mark the span as processed.
                        spans[i] = null
                    }
                }
                if (spanStart == mWordAtCursorStartIndex && spanEnd == mWordAtCursorEndIndex) {
                    // If the span does not start and stop here, ignore it. It probably extends
                    // past the start or end of the word, as happens in missing space correction
                    // or EasyEditSpans put by voice input.
                    spans[writeIndex++] = span
                }
            }
            readIndex++
        }
        @Suppress("UNCHECKED_CAST")
        return if (writeIndex == readIndex) spans as Array<SuggestionSpan> else spans.copyOfRange(0, writeIndex) as Array<SuggestionSpan>
    }

    override fun equals(other: Any?): Boolean {
        if (other !is TextRange) return false
        return mWordAtCursorStartIndex == other.mWordAtCursorStartIndex
            && mWordAtCursorEndIndex == other.mWordAtCursorEndIndex
            && mCursorIndex == other.mCursorIndex
            && mHasUrlSpans == other.mHasUrlSpans
            && TextUtils.equals(mTextAtCursor, other.mTextAtCursor)
            && TextUtils.equals(mWord, other.mWord)
    }

    override fun hashCode(): Int {
        return Objects.hash(mTextAtCursor, mWordAtCursorStartIndex, mWordAtCursorEndIndex, mCursorIndex, mWord, mHasUrlSpans)
    }

    override fun toString(): String {
        return "$mTextAtCursor, $mWord, $mCursorIndex"
    }
}
