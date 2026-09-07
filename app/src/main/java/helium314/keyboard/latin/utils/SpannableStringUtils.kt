/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import android.os.Build
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.SpannedString
import android.text.TextUtils
import android.text.style.SuggestionSpan
import android.text.style.URLSpan
import java.util.regex.Pattern

object SpannableStringUtils {

    fun copyNonParagraphSuggestionSpansFrom(
        source: Spanned,
        start: Int,
        end: Int,
        dest: Spannable,
        destoff: Int
    ) {
        val spans = source.getSpans(start, end, SuggestionSpan::class.java)

        for (span in spans) {
            var fl = source.getSpanFlags(span)
            fl = fl and Spanned.SPAN_PARAGRAPH.inv()

            var st = source.getSpanStart(span)
            var en = source.getSpanEnd(span)

            if (st < start) st = start
            if (en > end) en = end

            dest.setSpan(span, st - start + destoff, en - start + destoff, fl)
        }
    }

    fun concatWithNonParagraphSuggestionSpansOnly(vararg text: CharSequence): CharSequence {
        if (text.isEmpty()) {
            return ""
        }

        if (text.size == 1) {
            return text[0]
        }

        var spanned = false
        for (value in text) {
            if (value is Spanned) {
                spanned = true
                break
            }
        }

        val sb = StringBuilder()
        for (sequence in text) {
            sb.append(sequence)
        }

        if (!spanned) {
            return sb.toString()
        }

        val ss = SpannableString(sb)
        var off = 0
        for (charSequence in text) {
            val len = charSequence.length
            if (charSequence is Spanned) {
                copyNonParagraphSuggestionSpansFrom(charSequence, 0, len, ss, off)
            }
            off += len
        }

        return SpannedString(ss)
    }

    fun fromHtml(text: String): Spanned {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("deprecation")
            Html.fromHtml(text)
        }
    }

    fun hasUrlSpans(
        text: CharSequence?,
        startIndex: Int,
        endIndex: Int
    ): Boolean {
        if (text !is Spanned) {
            return false
        }
        val spans = text.getSpans(startIndex - 1, endIndex + 1, URLSpan::class.java)
        return !spans.isNullOrEmpty()
    }

    fun split(
        charSequence: CharSequence,
        regex: String,
        preserveTrailingEmptySegments: Boolean
    ): Array<CharSequence> {
        if (charSequence !is Spanned) {
            val limit = if (preserveTrailingEmptySegments) -1 else 0
            val pattern = Pattern.compile(regex)
            val parts = pattern.split(charSequence.toString(), limit)
            return parts.map { it as CharSequence }.toTypedArray()
        }

        val sequences = ArrayList<CharSequence>()
        val matcher = Pattern.compile(regex).matcher(charSequence)
        var nextStart = 0
        var matched = false
        while (matcher.find()) {
            sequences.add(charSequence.subSequence(nextStart, matcher.start()))
            nextStart = matcher.end()
            matched = true
        }
        if (!matched) {
            return arrayOf(charSequence)
        }
        sequences.add(charSequence.subSequence(nextStart, charSequence.length))
        if (!preserveTrailingEmptySegments) {
            for (i in sequences.size - 1 downTo 0) {
                if (!TextUtils.isEmpty(sequences[i])) {
                    break
                }
                sequences.removeAt(i)
            }
        }
        return sequences.toTypedArray()
    }
}
