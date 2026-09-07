/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.makedict

import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.latin.utils.CombinedFormatUtils

class ProbabilityInfo(
    val mProbability: Int,
    val mTimestamp: Int,
    val mLevel: Int,
    val mCount: Int
) {
    constructor(probability: Int) : this(probability, BinaryDictionary.NOT_A_VALID_TIMESTAMP, 0, 0)

    fun hasHistoricalInfo(): Boolean {
        return mTimestamp != BinaryDictionary.NOT_A_VALID_TIMESTAMP
    }

    override fun hashCode(): Int {
        return if (hasHistoricalInfo()) {
            arrayOf<Any>(mProbability, mTimestamp, mLevel, mCount).contentHashCode()
        } else {
            arrayOf<Any>(mProbability).contentHashCode()
        }
    }

    override fun toString(): String {
        return CombinedFormatUtils.formatProbabilityInfo(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProbabilityInfo) return false
        if (!hasHistoricalInfo() && !other.hasHistoricalInfo()) {
            return mProbability == other.mProbability
        }
        return mProbability == other.mProbability && mTimestamp == other.mTimestamp && mLevel == other.mLevel && mCount == other.mCount
    }

    companion object {
        @JvmStatic
        fun max(probabilityInfo1: ProbabilityInfo?, probabilityInfo2: ProbabilityInfo?): ProbabilityInfo? {
            if (probabilityInfo1 == null) {
                return probabilityInfo2
            }
            if (probabilityInfo2 == null) {
                return probabilityInfo1
            }
            return if (probabilityInfo1.mProbability > probabilityInfo2.mProbability) probabilityInfo1 else probabilityInfo2
        }
    }
}
