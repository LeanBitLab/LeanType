/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard

import android.util.SparseArray
import com.android.inputmethod.keyboard.ProximityInfo
import helium314.keyboard.keyboard.internal.KeyVisualAttributes
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.CoordinateUtils
import java.util.Collections

/**
 * Loads an XML description of a keyboard and stores the attributes of the keys. A keyboard
 * consists of rows of keys.
 */
open class Keyboard {
    @JvmField val mId: KeyboardId
    @JvmField val mThemeId: Int

    /** Total height of the keyboard, including the padding and keys */
    @JvmField val mOccupiedHeight: Int
    /** Total width of the keyboard, including the padding and keys */
    @JvmField var mOccupiedWidth: Int

    /** Base height of the keyboard, used to calculate rows' height */
    @JvmField val mBaseHeight: Int
    /** Base width of the keyboard, used to calculate keys' width */
    @JvmField var mBaseWidth: Int

    /** The padding above the keyboard */
    @JvmField val mTopPadding: Int
    /** Default gap between rows */
    @JvmField val mVerticalGap: Int

    /** Per keyboard key visual parameters */
    @JvmField val mKeyVisualAttributes: KeyVisualAttributes?

    @JvmField val mMostCommonKeyHeight: Int
    @JvmField val mMostCommonKeyWidth: Int

    /** Popup keys keyboard template */
    @JvmField val mPopupKeysTemplate: Int

    /** Maximum column for popup keys keyboard */
    @JvmField val mMaxPopupKeysKeyboardColumn: Int

    /** List of keys in this keyboard */
    private val mSortedKeys: List<Key>
    @JvmField val mShiftKeys: List<Key>
    @JvmField val mAltCodeKeysWhileTyping: List<Key>
    @JvmField val mIconsSet: KeyboardIconsSet

    private val mKeyCache = SparseArray<Key?>()

    private val mProximityInfo: ProximityInfo

    private val mProximityCharsCorrectionEnabled: Boolean

    constructor(params: KeyboardParams) {
        mId = params.mId
        mThemeId = params.mThemeId
        mOccupiedHeight = params.mOccupiedHeight
        mOccupiedWidth = params.mOccupiedWidth
        mBaseHeight = params.mBaseHeight
        mBaseWidth = params.mBaseWidth
        mMostCommonKeyHeight = params.mMostCommonKeyHeight
        mMostCommonKeyWidth = params.mMostCommonKeyWidth
        mPopupKeysTemplate = params.mPopupKeysTemplate
        mMaxPopupKeysKeyboardColumn = params.mMaxPopupKeysKeyboardColumn
        mKeyVisualAttributes = params.mKeyVisualAttributes
        mTopPadding = params.mTopPadding
        mVerticalGap = params.mVerticalGap

        mSortedKeys = Collections.unmodifiableList(ArrayList(params.mSortedKeys))
        mShiftKeys = Collections.unmodifiableList(params.mShiftKeys)
        mAltCodeKeysWhileTyping = Collections.unmodifiableList(params.mAltCodeKeysWhileTyping)
        mIconsSet = params.mIconsSet

        mProximityInfo = ProximityInfo(
            params.GRID_WIDTH, params.GRID_HEIGHT,
            mOccupiedWidth, mOccupiedHeight, mMostCommonKeyWidth, mMostCommonKeyHeight,
            mSortedKeys, params.mTouchPositionCorrection
        )
        mProximityCharsCorrectionEnabled = params.mProximityCharsCorrectionEnabled
    }

    protected constructor(keyboard: Keyboard) {
        mId = keyboard.mId
        mThemeId = keyboard.mThemeId
        mOccupiedHeight = keyboard.mOccupiedHeight
        mOccupiedWidth = keyboard.mOccupiedWidth
        mBaseHeight = keyboard.mBaseHeight
        mBaseWidth = keyboard.mBaseWidth
        mMostCommonKeyHeight = keyboard.mMostCommonKeyHeight
        mMostCommonKeyWidth = keyboard.mMostCommonKeyWidth
        mPopupKeysTemplate = keyboard.mPopupKeysTemplate
        mMaxPopupKeysKeyboardColumn = keyboard.mMaxPopupKeysKeyboardColumn
        mKeyVisualAttributes = keyboard.mKeyVisualAttributes
        mTopPadding = keyboard.mTopPadding
        mVerticalGap = keyboard.mVerticalGap

        mSortedKeys = keyboard.mSortedKeys
        mShiftKeys = keyboard.mShiftKeys
        mAltCodeKeysWhileTyping = keyboard.mAltCodeKeysWhileTyping
        mIconsSet = keyboard.mIconsSet

        mProximityInfo = keyboard.mProximityInfo
        mProximityCharsCorrectionEnabled = keyboard.mProximityCharsCorrectionEnabled
    }

    fun hasProximityCharsCorrection(code: Int): Boolean {
        if (!mProximityCharsCorrectionEnabled) {
            return false
        }
        val canAssumeNativeHasProximityCharsInfoOfAllKeys = (
                mId.mElementId == KeyboardId.ELEMENT_ALPHABET ||
                mId.mElementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED
        )
        return canAssumeNativeHasProximityCharsInfoOfAllKeys || Character.isLetter(code)
    }

    val proximityInfo: ProximityInfo
        get() = mProximityInfo

    open val sortedKeys: List<Key>
        get() = mSortedKeys

    fun getKey(code: Int): Key? {
        if (code == KeyCode.NOT_SPECIFIED) {
            return null
        }
        synchronized(mKeyCache) {
            val index = mKeyCache.indexOfKey(code)
            if (index >= 0) {
                return mKeyCache.valueAt(index)
            }
            for (key in sortedKeys) {
                if (key.code == code) {
                    mKeyCache.put(code, key)
                    return key
                }
            }
            mKeyCache.put(code, null)
            return null
        }
    }

    fun hasKey(aKey: Key): Boolean {
        synchronized(mKeyCache) {
            if (mKeyCache.indexOfValue(aKey) >= 0) {
                return true
            }
            for (key in sortedKeys) {
                if (key == aKey) {
                    mKeyCache.put(key.code, key)
                    return true
                }
            }
            return false
        }
    }

    override fun toString(): String {
        return mId.toString()
    }

    open fun getNearestKeys(x: Int, y: Int): List<Key> {
        val adjustedX = x.coerceIn(0, mOccupiedWidth - 1)
        val adjustedY = y.coerceIn(0, mOccupiedHeight - 1)
        return mProximityInfo.getNearestKeys(adjustedX, adjustedY)
    }

    fun getCoordinates(codePoints: IntArray): IntArray {
        val length = codePoints.size
        val coordinates = CoordinateUtils.newCoordinateArray(length)
        for (i in 0 until length) {
            val key = getKey(codePoints[i])
            if (key != null) {
                CoordinateUtils.setXYInArray(
                    coordinates, i,
                    key.x + key.width / 2, key.y + key.height / 2
                )
            } else {
                CoordinateUtils.setXYInArray(
                    coordinates, i,
                    Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE
                )
            }
        }
        return coordinates
    }
}
