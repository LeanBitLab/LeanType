/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.define.ProductionFlags
import java.util.TreeSet

/**
 * A TreeSet of SuggestedWordInfo that is bounded in size and throws everything that's smaller
 * than its limit
 */
class SuggestionResults private constructor(
    comparator: Comparator<in SuggestedWordInfo>?,
    private val mCapacity: Int,
    val mIsBeginningOfSentence: Boolean,
    val mFirstSuggestionExceedsConfidenceThreshold: Boolean
) : TreeSet<SuggestedWordInfo>(comparator) {

    val mRawSuggestions: ArrayList<SuggestedWordInfo>? =
        if (ProductionFlags.INCLUDE_RAW_SUGGESTIONS) ArrayList() else null

    constructor(
        capacity: Int,
        isBeginningOfSentence: Boolean,
        firstSuggestionExceedsConfidenceThreshold: Boolean
    ) : this(
        sSuggestedWordInfoComparator,
        capacity,
        isBeginningOfSentence,
        firstSuggestionExceedsConfidenceThreshold
    )

    constructor(other: SuggestionResults) : this(
        @Suppress("UNCHECKED_CAST")
        (other.comparator() as? Comparator<SuggestedWordInfo>) ?: sSuggestedWordInfoComparator,
        other.mCapacity,
        other.mIsBeginningOfSentence,
        other.mFirstSuggestionExceedsConfidenceThreshold
    ) {
        addAll(other)
        if (this.mRawSuggestions != null && other.mRawSuggestions != null) {
            this.mRawSuggestions.addAll(other.mRawSuggestions)
        }
    }

    fun copy(): SuggestionResults {
        return SuggestionResults(this)
    }

    override fun add(element: SuggestedWordInfo): Boolean {
        if (size < mCapacity) return super.add(element)
        if (comparator()!!.compare(element, last()) > 0) return false
        super.add(element)
        pollLast()
        return true
    }

    override fun addAll(elements: Collection<SuggestedWordInfo>): Boolean {
        return super.addAll(elements)
    }

    @JvmName("addAllNullable")
    fun addAll(elements: Collection<SuggestedWordInfo>?): Boolean {
        if (elements == null) return false
        return super.addAll(elements)
    }

    private class SuggestedWordInfoComparator : Comparator<SuggestedWordInfo> {
        override fun compare(o1: SuggestedWordInfo, o2: SuggestedWordInfo): Int {
            if (o1.mScore > o2.mScore) return -1
            if (o1.mScore < o2.mScore) return 1
            if (o1.mCodePointCount < o2.mCodePointCount) return -1
            if (o1.mCodePointCount > o2.mCodePointCount) return 1
            return o1.mWord.compareTo(o2.mWord)
        }
    }

    companion object {
        private val sSuggestedWordInfoComparator = SuggestedWordInfoComparator()
    }
}
