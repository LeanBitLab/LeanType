/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin

import android.text.TextUtils
import helium314.keyboard.event.Event
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.define.DecoderSpecificConstants
import java.util.ArrayList

/**
 * This class encapsulates data about a word previously composed, but that has been
 * committed already. This is used for resuming suggestion, and cancel auto-correction.
 */
class LastComposedWord(
    events: ArrayList<Event>,
    inputPointers: InputPointers?,
    @JvmField val mTypedWord: String,
    @JvmField val mCommittedWord: CharSequence,
    @JvmField val mSeparatorString: String,
    @JvmField val mNgramContext: NgramContext?,
    @JvmField val mCapitalizedMode: Int
) {
    @JvmField val mEvents: ArrayList<Event> = ArrayList(events)
    @JvmField val mInputPointers: InputPointers = InputPointers(DecoderSpecificConstants.DICTIONARY_MAX_WORD_LENGTH)

    private var mActive: Boolean = true

    init {
        if (inputPointers != null) {
            mInputPointers.copy(inputPointers)
        }
    }

    fun deactivate() {
        mActive = false
    }

    fun canRevertCommit(): Boolean {
        return mActive && !TextUtils.isEmpty(mCommittedWord) && !didCommitTypedWord()
    }

    private fun didCommitTypedWord(): Boolean {
        return TextUtils.equals(mTypedWord, mCommittedWord)
    }

    companion object {
        const val COMMIT_TYPE_USER_TYPED_WORD = 0
        const val COMMIT_TYPE_MANUAL_PICK = 1
        const val COMMIT_TYPE_DECIDED_WORD = 2
        const val COMMIT_TYPE_CANCEL_AUTO_CORRECT = 3

        const val NOT_A_SEPARATOR = ""

        @JvmField
        val NOT_A_COMPOSED_WORD = LastComposedWord(
            ArrayList(),
            null,
            "",
            "",
            NOT_A_SEPARATOR,
            null,
            WordComposer.CAPS_MODE_OFF
        )
    }
}
