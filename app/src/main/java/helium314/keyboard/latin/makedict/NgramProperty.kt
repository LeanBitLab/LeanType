/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.makedict

import helium314.keyboard.latin.NgramContext

class NgramProperty(
    @JvmField val mTargetWord: WeightedString,
    @JvmField val mNgramContext: NgramContext
) {
    override fun hashCode(): Int {
        return mTargetWord.hashCode() xor mNgramContext.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NgramProperty) return false
        return mTargetWord == other.mTargetWord && mNgramContext == other.mNgramContext
    }
}
