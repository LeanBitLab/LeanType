/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import java.util.Locale

/**
 * The status of the current recapitalize process.
 */
class RecapitalizeStatus {
    // We store the location of the cursor and the string that was there before the recapitalize
    // action was done, and the location of the cursor and the string that was there after.
    private var mText: String = ""
    private lateinit var mTextPlacement: TextPlacement
    private var mCurrentMode: RecapitalizeMode = RecapitalizeMode.ORIGINAL_MIXED_CASE
    private var mSkipOriginalMixedCaseMode: Boolean = false
    private var mLocale: Locale = Locale.getDefault()
    private var mSortedSeparators: IntArray = intArrayOf()
    private var mIsStarted: Boolean = false
    private var mIsEnabled: Boolean = true

    init {
        // By default, initialize with dummy values that won't match any real recapitalize.
        start("", -1, Locale.getDefault(), intArrayOf())
        stop()
    }

    fun start(text: String, cursorStart: Int, locale: Locale, sortedSeparators: IntArray) {
        if (!mIsEnabled) {
            return
        }
        mText = text
        mTextPlacement = TextPlacement(text, cursorStart)
        mCurrentMode = RecapitalizeMode.of(mText, sortedSeparators)
        mSkipOriginalMixedCaseMode = RecapitalizeMode.ORIGINAL_MIXED_CASE != mCurrentMode
        mLocale = locale
        mSortedSeparators = sortedSeparators
        mIsStarted = true
    }

    fun stop() {
        mIsStarted = false
    }

    fun isStarted(): Boolean {
        return mIsStarted
    }

    fun enable() {
        mIsEnabled = true
    }

    fun disable() {
        mIsEnabled = false
    }

    fun isEnabled(): Boolean {
        return mIsEnabled
    }

    fun isSetAt(cursorStart: Int, cursorEnd: Int): Boolean {
        return cursorStart == mTextPlacement.selectionStart && cursorEnd == mTextPlacement.selectionEnd()
    }

    /**
     * Rotate through the different possible capitalization modes.
     */
    fun rotate() {
        val modeCount = RecapitalizeMode.count()
        var replacement: String
        var i = 0 // Protection against infinite loop.
        do {
            mCurrentMode = mCurrentMode.rotate(mSkipOriginalMixedCaseMode)
            replacement = mCurrentMode.apply(mText, mSortedSeparators, mLocale)
            ++i
        } while (replacement == mTextPlacement.text && i <= modeCount)
        mTextPlacement.text = replacement
    }

    fun textReplacement(): TextPlacement {
        return mTextPlacement
    }

    fun getCurrentMode(): RecapitalizeMode {
        return mCurrentMode
    }
}
