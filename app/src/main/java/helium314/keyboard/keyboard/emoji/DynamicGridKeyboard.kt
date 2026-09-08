/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.emoji

import android.content.SharedPreferences
import android.text.TextUtils
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.internal.PopupKeySpec
import helium314.keyboard.keyboard.internal.keyboard_parser.EMOJI_HINT_LABEL
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.JsonUtils
import helium314.keyboard.latin.utils.Log
import java.util.ArrayDeque
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

class DynamicGridKeyboard(
    private val mPrefs: SharedPreferences,
    templateKeyboard: Keyboard,
    private val mMaxKeyCount: Int,
    categoryId: Int,
    width: Int
) : Keyboard(templateKeyboard) {

    private val mLock = Any()
    private val mHorizontalStep: Int
    private val mHorizontalGap: Int
    private val mVerticalStep: Int
    private val mColumnsNum: Int
    private val mIsRecents: Boolean = categoryId == EmojiCategory.ID_RECENTS
    private val mGridKeys = ArrayDeque<GridKey>()
    private val mPendingKeys = ArrayDeque<Key>()
    private var mCachedGridKeys: List<Key>? = null
    private val mEmptyColumnIndices = ArrayList<Int>(4)

    init {
        val paddingWidth = mOccupiedWidth - mBaseWidth
        mBaseWidth = width - paddingWidth
        mOccupiedWidth = width
        val spacerWidth = Settings.getValues().mSplitKeyboardSpacerRelativeWidth * mBaseWidth
        val key0 = getTemplateKey(Constants.RECENTS_TEMPLATE_KEY_CODE_0)
        val key1 = getTemplateKey(Constants.RECENTS_TEMPLATE_KEY_CODE_1)
        val horizontalGap = abs(key1.x - key0.x) - key0.width
        val widthScale = determineWidthScale((key0.width + horizontalGap).toFloat())

        var columnsNumVal: Int
        var horizontalStepVal: Int
        var horizontalGapVal: Int

        if (categoryId == EmojiCategory.ID_EMOTICONS) {
            val standardColumns = mBaseWidth / ((key0.width + horizontalGap) * widthScale).toInt()
            columnsNumVal = max(1, standardColumns / 2)
            horizontalStepVal = mBaseWidth / columnsNumVal
            horizontalGapVal = (horizontalGap * widthScale * 2).toInt()
        } else {
            horizontalGapVal = (horizontalGap * widthScale).toInt()
            horizontalStepVal = ((key0.width + horizontalGap) * widthScale).toInt()
            columnsNumVal = mBaseWidth / horizontalStepVal
        }

        var verticalStepVal = ((key0.height + mVerticalGap) / sqrt(Settings.getValues().mKeyboardHeightScale.toDouble())).toInt()

        if (Settings.getValues().mEmojiKeyFit) {
            val scale = Settings.getValues().mFontSizeMultiplierEmoji
            horizontalGapVal = (horizontalGapVal * scale).toInt()
            horizontalStepVal = (horizontalStepVal * scale).toInt()
            columnsNumVal = max(1, mBaseWidth / horizontalStepVal)
            verticalStepVal = (verticalStepVal * scale).toInt()
        }

        mHorizontalGap = horizontalGapVal
        mHorizontalStep = horizontalStepVal
        mColumnsNum = columnsNumVal
        mVerticalStep = verticalStepVal

        if (spacerWidth > 0) {
            setSpacerColumns(spacerWidth)
        }
    }

    private fun setSpacerColumns(spacerWidth: Float) {
        var spacerColumnsWidth = (spacerWidth / mHorizontalStep).toInt()
        if (spacerColumnsWidth == 0) return
        if (mColumnsNum % 2 != spacerColumnsWidth % 2) spacerColumnsWidth++

        val leftmost: Int
        val rightmost: Int
        if (spacerColumnsWidth % 2 == 0) {
            val center = mColumnsNum / 2
            leftmost = center - (spacerColumnsWidth / 2 - 1)
            rightmost = center + spacerColumnsWidth / 2
        } else {
            val center = mColumnsNum / 2 + 1
            leftmost = center - spacerColumnsWidth / 2
            rightmost = center + spacerColumnsWidth / 2
        }
        for (i in leftmost..rightmost) {
            mEmptyColumnIndices.add(i - 1)
        }
    }

    private fun determineWidthScale(horizontalStep: Float): Float {
        val columnsNumRaw = mBaseWidth / horizontalStep
        val columnsNum = columnsNumRaw.roundToInt().toFloat()
        return columnsNumRaw / columnsNum
    }

    private fun getTemplateKey(code: Int): Key {
        for (key in super.sortedKeys) {
            if (key.code == code) {
                return key
            }
        }
        throw RuntimeException("Can't find template key: code=$code")
    }

    fun getDynamicOccupiedHeight(): Int {
        val row = (mGridKeys.size - 1) / occupiedColumnCount + 1
        return row * mVerticalStep
    }

    val occupiedColumnCount: Int
        get() = mColumnsNum - mEmptyColumnIndices.size

    fun addPendingKey(usedKey: Key) {
        synchronized(mLock) {
            mPendingKeys.addLast(usedKey)
        }
    }

    fun flushPendingRecentKeys() {
        synchronized(mLock) {
            while (mPendingKeys.isNotEmpty()) {
                addKey(mPendingKeys.pollFirst(), true)
            }
            saveRecentKeys()
        }
    }

    fun addKeyFirst(usedKey: Key) {
        addKey(usedKey, true)
        if (mIsRecents) {
            saveRecentKeys()
        }
    }

    fun addStringKeyFirst(outputText: String) {
        val key = Key(getTemplateKey(Constants.RECENTS_TEMPLATE_KEY_CODE_0), null, null, Key.BACKGROUND_TYPE_EMPTY, 0, outputText)
        addKeyFirst(key)
    }

    fun addPendingStringKey(outputText: String) {
        val key = Key(getTemplateKey(Constants.RECENTS_TEMPLATE_KEY_CODE_0), null, null, Key.BACKGROUND_TYPE_EMPTY, 0, outputText)
        addPendingKey(key)
    }

    fun addKeyLast(usedKey: Key) {
        addKey(usedKey, false)
    }

    private fun addKey(usedKey: Key?, addFirst: Boolean) {
        if (usedKey == null) return
        synchronized(mLock) {
            mCachedGridKeys = null
            val dropPopupKeys = mIsRecents
            val dropHintLabel = dropPopupKeys && EMOJI_HINT_LABEL == usedKey.hintLabel
            val key = GridKey(
                usedKey,
                if (dropPopupKeys) null else usedKey.popupKeys,
                if (dropHintLabel) null else usedKey.hintLabel,
                if (mIsRecents) Key.BACKGROUND_TYPE_EMPTY else usedKey.backgroundType
            )
            while (mGridKeys.remove(key)) {
                // Remove duplicate keys.
            }
            if (addFirst) {
                mGridKeys.addFirst(key)
            } else {
                mGridKeys.addLast(key)
            }
            while (mGridKeys.size > mMaxKeyCount) {
                mGridKeys.removeLast()
            }
            var index = 0
            for (gridKey in mGridKeys) {
                while (mEmptyColumnIndices.contains(index % mColumnsNum)) {
                    index++
                }
                val keyX0 = getKeyX0(index)
                val keyY0 = getKeyY0(index)
                val keyX1 = getKeyX1(index)
                val keyY1 = getKeyY1(index)
                gridKey.updateCoordinates(keyX0, keyY0, keyX1, keyY1)
                index++
            }
        }
    }

    fun clearRecentKeys() {
        synchronized(mLock) {
            mGridKeys.clear()
            mPendingKeys.clear()
            mEmptyColumnIndices.clear()
            mCachedGridKeys = null
            saveRecentKeys()
        }
    }

    private fun saveRecentKeys() {
        val keys = ArrayList<Any>()
        for (key in mGridKeys) {
            if (key.outputText != null) {
                keys.add(key.outputText as Any)
            } else {
                keys.add(key.code)
            }
        }
        val jsonStr = JsonUtils.listToJsonStr(keys)
        mPrefs.edit().putString(Settings.PREF_EMOJI_RECENT_KEYS, jsonStr).apply()
    }

    private fun getKeyByCode(keyboards: Collection<DynamicGridKeyboard>, code: Int): Key {
        for (keyboard in keyboards) {
            for (key in keyboard.sortedKeys) {
                if (key.code == code) {
                    return key
                }
            }
        }
        return Key(getTemplateKey(Constants.RECENTS_TEMPLATE_KEY_CODE_0), null, null, Key.BACKGROUND_TYPE_EMPTY, code, null)
    }

    private fun getKeyByOutputText(keyboards: Collection<DynamicGridKeyboard>, outputText: String): Key {
        for (keyboard in keyboards) {
            for (key in keyboard.sortedKeys) {
                if (outputText == key.outputText) {
                    return key
                }
            }
        }
        return Key(getTemplateKey(Constants.RECENTS_TEMPLATE_KEY_CODE_0), null, null, Key.BACKGROUND_TYPE_EMPTY, 0, outputText)
    }

    fun loadRecentKeys(keyboards: Collection<DynamicGridKeyboard>) {
        val str = mPrefs.getString(Settings.PREF_EMOJI_RECENT_KEYS, Defaults.PREF_EMOJI_RECENT_KEYS) ?: Defaults.PREF_EMOJI_RECENT_KEYS
        val keys = JsonUtils.jsonStrToList(str)
        for (o in keys) {
            val key: Key = when (o) {
                is Int -> getKeyByCode(keyboards, o)
                is String -> getKeyByOutputText(keyboards, o)
                else -> {
                    Log.w(TAG, "Invalid object: $o")
                    continue
                }
            }
            addKeyLast(key)
        }
    }

    private fun getKeyX0(index: Int): Int {
        val column = index % mColumnsNum
        return column * mHorizontalStep + mHorizontalGap / 2
    }

    private fun getKeyX1(index: Int): Int {
        val column = index % mColumnsNum + 1
        return column * mHorizontalStep + mHorizontalGap / 2
    }

    private fun getKeyY0(index: Int): Int {
        val row = index / mColumnsNum
        return row * mVerticalStep + mVerticalGap / 2
    }

    private fun getKeyY1(index: Int): Int {
        val row = index / mColumnsNum + 1
        return row * mVerticalStep + mVerticalGap / 2
    }

    override val sortedKeys: List<Key>
        get() {
            synchronized(mLock) {
                val cached = mCachedGridKeys
                if (cached != null) return cached
                val cachedKeys = ArrayList<Key>(mGridKeys)
                val unmodifiable = Collections.unmodifiableList(cachedKeys)
                mCachedGridKeys = unmodifiable
                return unmodifiable
            }
        }

    override fun getNearestKeys(x: Int, y: Int): List<Key> {
        return sortedKeys
    }

    class GridKey(
        originalKey: Key,
        popupKeys: Array<PopupKeySpec>?,
        labelHint: String?,
        backgroundType: Int
    ) : Key(originalKey, popupKeys, labelHint, backgroundType) {

        private var mCurrentX = 0
        private var mCurrentY = 0

        fun updateCoordinates(x0: Int, y0: Int, x1: Int, y1: Int) {
            mCurrentX = x0
            mCurrentY = y0
            hitBox.set(x0, y0, x1, y1)
        }

        override val horizontalGap: Int
            get() = if (Settings.getValues().mEmojiKeyFit) {
                (super.horizontalGap * Settings.getValues().mFontSizeMultiplierEmoji).toInt()
            } else super.horizontalGap

        override val verticalGap: Int
            get() = if (Settings.getValues().mEmojiKeyFit) {
                (super.verticalGap * Settings.getValues().mFontSizeMultiplierEmoji).toInt()
            } else super.verticalGap

        override val width: Int get() = hitBox.width() - horizontalGap

        override val height: Int get() = hitBox.height() - verticalGap

        override val x: Int get() = hitBox.left + horizontalGap / 2

        override val y: Int get() = hitBox.top + verticalGap / 2

        override val drawWidth: Int get() = width

        override fun equals(other: Any?): Boolean {
            if (other !is Key) return false
            if (code != other.code) return false
            if (!TextUtils.equals(label, other.label)) return false
            return TextUtils.equals(outputText, other.outputText)
        }

        override fun hashCode(): Int {
            var result = code
            result = 31 * result + (label?.hashCode() ?: 0)
            result = 31 * result + (outputText?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String = "GridKey: ${super.toString()}"
    }

    companion object {
        private const val TAG = "DynamicGridKeyboard"
    }
}
