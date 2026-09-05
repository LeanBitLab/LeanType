/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.makedict

/**
 * A string with a probability.
 *
 * This represents an "attribute", that is either a bigram or a shortcut.
 */
class WeightedString(
    @JvmField val mWord: String,
    @JvmField var mProbabilityInfo: ProbabilityInfo
) {
    constructor(word: String, probability: Int) : this(word, ProbabilityInfo(probability))

    fun getProbability(): Int {
        return mProbabilityInfo.mProbability
    }

    fun setProbability(probability: Int) {
        mProbabilityInfo = ProbabilityInfo(probability)
    }

    override fun hashCode(): Int {
        return arrayOf<Any>(mWord, mProbabilityInfo).contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WeightedString) return false
        return mWord == other.mWord && mProbabilityInfo == other.mProbabilityInfo
    }
}
