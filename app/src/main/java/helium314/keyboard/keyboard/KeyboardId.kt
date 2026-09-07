/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard

import android.text.InputType
import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import helium314.keyboard.compat.EditorInfoCompatUtils
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.WordComposer
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET
import helium314.keyboard.latin.utils.InputTypeUtils
import java.util.Locale
import java.util.Objects

/**
 * Unique identifier for each keyboard type.
 */
class KeyboardId(elementId: Int, params: KeyboardLayoutSet.Params) {
    val mSubtype: RichInputMethodSubtype = params.mSubtype
    val mWidth: Int = params.mKeyboardWidth
    val mHeight: Int = params.mKeyboardHeight
    val mMode: Int = params.mMode
    val mElementId: Int = elementId
    val mEditorInfo: EditorInfo = params.mEditorInfo
    val mDeviceLocked: Boolean = params.mDeviceLocked
    val mNumberRowEnabled: Boolean = params.mNumberRowEnabled
    val mNumberRowInSymbols: Boolean = params.mNumberRowInSymbols
    val mCompactNumberRowInSymbols: Boolean = params.mCompactNumberRowInSymbols
    val mLanguageSwitchKeyEnabled: Boolean = params.mLanguageSwitchKeyEnabled
    val mEmojiKeyEnabled: Boolean = params.mEmojiKeyEnabled
    val mCustomActionLabel: String? =
        if (mEditorInfo.actionLabel != null) mEditorInfo.actionLabel.toString() else null
    val mHasShortcutKey: Boolean = params.mVoiceInputKeyEnabled
    val mIsSplitLayout: Boolean = params.mIsSplitLayoutEnabled
    val mOneHandedModeEnabled: Boolean = params.mOneHandedModeEnabled
    val mInternalAction: KeyboardLayoutSet.InternalAction? = params.mInternalAction

    private val mHashCode: Int = computeHashCode(this)

    private fun equals(other: KeyboardId): Boolean {
        if (other === this) return true
        return other.mElementId == mElementId
                && other.mMode == mMode
                && other.mWidth == mWidth
                && other.mHeight == mHeight
                && other.passwordInput() == passwordInput()
                && other.mDeviceLocked == mDeviceLocked
                && other.mHasShortcutKey == mHasShortcutKey
                && other.mNumberRowEnabled == mNumberRowEnabled
                && other.mLanguageSwitchKeyEnabled == mLanguageSwitchKeyEnabled
                && other.mEmojiKeyEnabled == mEmojiKeyEnabled
                && other.isMultiLine == isMultiLine
                && other.imeAction() == imeAction()
                && TextUtils.equals(other.mCustomActionLabel, mCustomActionLabel)
                && other.navigateNext() == navigateNext()
                && other.navigatePrevious() == navigatePrevious()
                && other.mSubtype == mSubtype
                && other.mIsSplitLayout == mIsSplitLayout
                && Objects.equals(other.mInternalAction, mInternalAction)
    }

    val isAlphaOrSymbolKeyboard: Boolean
        get() = mElementId <= ELEMENT_SYMBOLS_SHIFTED

    val isAlphabetKeyboard: Boolean
        get() = isAlphabetKeyboard(mElementId)

    fun navigateNext(): Boolean {
        return (mEditorInfo.imeOptions and EditorInfo.IME_FLAG_NAVIGATE_NEXT) != 0 ||
                imeAction() == EditorInfo.IME_ACTION_NEXT
    }

    fun navigatePrevious(): Boolean {
        return (mEditorInfo.imeOptions and EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS) != 0 ||
                imeAction() == EditorInfo.IME_ACTION_PREVIOUS
    }

    fun passwordInput(): Boolean {
        val inputType = mEditorInfo.inputType
        return InputTypeUtils.isAnyPasswordInputType(inputType)
    }

    val isMultiLine: Boolean
        get() = (mEditorInfo.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0

    val isAlphabetShifted: Boolean
        get() = mElementId == ELEMENT_ALPHABET_SHIFT_LOCKED ||
                mElementId == ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED ||
                mElementId == ELEMENT_ALPHABET_AUTOMATIC_SHIFTED ||
                mElementId == ELEMENT_ALPHABET_MANUAL_SHIFTED

    val isAlphabetShiftedManually: Boolean
        get() = mElementId == ELEMENT_ALPHABET_SHIFT_LOCKED ||
                mElementId == ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED ||
                mElementId == ELEMENT_ALPHABET_MANUAL_SHIFTED

    val isNumberLayout: Boolean
        get() = mElementId == ELEMENT_NUMBER ||
                mElementId == ELEMENT_NUMPAD ||
                mElementId == ELEMENT_PHONE ||
                mElementId == ELEMENT_PHONE_SYMBOLS

    val isEmojiKeyboard: Boolean
        get() = mElementId >= ELEMENT_EMOJI_RECENTS && mElementId <= ELEMENT_EMOJI_CATEGORY16

    val isEmojiClipBottomRow: Boolean
        get() = mElementId == ELEMENT_CLIPBOARD_BOTTOM_ROW ||
                mElementId == ELEMENT_EMOJI_BOTTOM_ROW ||
                mElementId == ELEMENT_HANDWRITING_BOTTOM_ROW

    fun imeAction(): Int {
        return InputTypeUtils.getImeOptionsActionIdFromEditorInfo(mEditorInfo)
    }

    val locale: Locale
        get() = mSubtype.locale

    override fun equals(other: Any?): Boolean {
        return other is KeyboardId && equals(other)
    }

    override fun hashCode(): Int {
        return mHashCode
    }

    override fun toString(): String {
        return String.format(
            Locale.ROOT,
            "[%s %s:%s %dx%d %s %s%s%s%s%s%s%s%s%s%s%s]",
            elementIdToName(mElementId),
            mSubtype.locale,
            mSubtype.getExtraValueOf(KEYBOARD_LAYOUT_SET),
            mWidth,
            mHeight,
            modeName(mMode),
            actionName(imeAction()),
            if (navigateNext()) " navigateNext" else "",
            if (navigatePrevious()) " navigatePrevious" else "",
            if (mDeviceLocked) " deviceLocked" else "",
            if (passwordInput()) " passwordInput" else "",
            if (mHasShortcutKey) " hasShortcutKey" else "",
            if (mNumberRowEnabled) " numberRowEnabled" else "",
            if (mLanguageSwitchKeyEnabled) " languageSwitchKeyEnabled" else "",
            if (mEmojiKeyEnabled) " emojiKeyEnabled" else "",
            if (isMultiLine) " isMultiLine" else "",
            if (mIsSplitLayout) " isSplitLayout" else ""
        )
    }

    val keyboardCapsMode: Int
        get() = when (mElementId) {
            ELEMENT_ALPHABET_SHIFT_LOCKED,
            ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> WordComposer.CAPS_MODE_MANUAL_SHIFT_LOCKED
            ELEMENT_ALPHABET_MANUAL_SHIFTED -> WordComposer.CAPS_MODE_MANUAL_SHIFTED
            ELEMENT_ALPHABET_AUTOMATIC_SHIFTED -> WordComposer.CAPS_MODE_AUTO_SHIFTED
            else -> WordComposer.CAPS_MODE_OFF
        }

    companion object {
        const val MODE_TEXT = 0
        const val MODE_URL = 1
        const val MODE_EMAIL = 2
        const val MODE_IM = 3
        const val MODE_PHONE = 4
        const val MODE_NUMBER = 5
        const val MODE_DATE = 6
        const val MODE_TIME = 7
        const val MODE_DATETIME = 8
        const val MODE_NUMPAD = 9

        const val ELEMENT_ALPHABET = 0
        const val ELEMENT_ALPHABET_MANUAL_SHIFTED = 1
        const val ELEMENT_ALPHABET_AUTOMATIC_SHIFTED = 2
        const val ELEMENT_ALPHABET_SHIFT_LOCKED = 3
        const val ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED = 4
        const val ELEMENT_SYMBOLS = 5
        const val ELEMENT_SYMBOLS_SHIFTED = 6
        const val ELEMENT_PHONE = 7
        const val ELEMENT_PHONE_SYMBOLS = 8
        const val ELEMENT_NUMBER = 9
        const val ELEMENT_EMOJI_RECENTS = 10
        const val ELEMENT_EMOJI_CATEGORY1 = 11
        const val ELEMENT_EMOJI_CATEGORY2 = 12
        const val ELEMENT_EMOJI_CATEGORY3 = 13
        const val ELEMENT_EMOJI_CATEGORY4 = 14
        const val ELEMENT_EMOJI_CATEGORY5 = 15
        const val ELEMENT_EMOJI_CATEGORY6 = 16
        const val ELEMENT_EMOJI_CATEGORY7 = 17
        const val ELEMENT_EMOJI_CATEGORY8 = 18
        const val ELEMENT_EMOJI_CATEGORY9 = 19
        const val ELEMENT_EMOJI_CATEGORY10 = 20
        const val ELEMENT_EMOJI_CATEGORY11 = 21
        const val ELEMENT_EMOJI_CATEGORY12 = 22
        const val ELEMENT_EMOJI_CATEGORY13 = 23
        const val ELEMENT_EMOJI_CATEGORY14 = 24
        const val ELEMENT_EMOJI_CATEGORY15 = 25
        const val ELEMENT_EMOJI_CATEGORY16 = 26
        const val ELEMENT_CLIPBOARD = 27
        const val ELEMENT_NUMPAD = 28
        const val ELEMENT_EMOJI_BOTTOM_ROW = 29
        const val ELEMENT_CLIPBOARD_BOTTOM_ROW = 30
        const val ELEMENT_HANDWRITING_BOTTOM_ROW = 31
        const val ELEMENT_TEXT_EDIT = 32
        const val ELEMENT_CUSTOM1 = 33
        const val ELEMENT_CUSTOM2 = 34
        const val ELEMENT_CUSTOM3 = 35
        const val ELEMENT_CUSTOM4 = 36
        const val ELEMENT_CUSTOM5 = 37

        private fun computeHashCode(id: KeyboardId): Int {
            return arrayOf<Any?>(
                id.mElementId,
                id.mMode,
                id.mWidth,
                id.mHeight,
                id.passwordInput(),
                id.mDeviceLocked,
                id.mHasShortcutKey,
                id.mNumberRowEnabled,
                id.mLanguageSwitchKeyEnabled,
                id.mEmojiKeyEnabled,
                id.isMultiLine,
                id.imeAction(),
                id.mCustomActionLabel,
                id.navigateNext(),
                id.navigatePrevious(),
                id.mSubtype,
                id.mIsSplitLayout,
                id.mInternalAction
            ).contentHashCode()
        }

        private fun isAlphabetKeyboard(elementId: Int): Boolean {
            return elementId < ELEMENT_SYMBOLS
        }

        @JvmStatic
        fun equivalentEditorInfoForKeyboard(a: EditorInfo?, b: EditorInfo?): Boolean {
            if (a == null && b == null) return true
            if (a == null || b == null) return false
            return a.inputType == b.inputType &&
                    a.imeOptions == b.imeOptions &&
                    TextUtils.equals(a.privateImeOptions, b.privateImeOptions)
        }

        @JvmStatic
        fun elementIdToName(elementId: Int): String? {
            return when (elementId) {
                ELEMENT_ALPHABET -> "alphabet"
                ELEMENT_ALPHABET_MANUAL_SHIFTED -> "alphabetManualShifted"
                ELEMENT_ALPHABET_AUTOMATIC_SHIFTED -> "alphabetAutomaticShifted"
                ELEMENT_ALPHABET_SHIFT_LOCKED -> "alphabetShiftLocked"
                ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> "alphabetShiftLockShifted"
                ELEMENT_SYMBOLS -> "symbols"
                ELEMENT_SYMBOLS_SHIFTED -> "symbolsShifted"
                ELEMENT_PHONE -> "phone"
                ELEMENT_PHONE_SYMBOLS -> "phoneSymbols"
                ELEMENT_NUMBER -> "number"
                ELEMENT_EMOJI_RECENTS -> "emojiRecents"
                ELEMENT_EMOJI_CATEGORY1 -> "emojiCategory1"
                ELEMENT_EMOJI_CATEGORY2 -> "emojiCategory2"
                ELEMENT_EMOJI_CATEGORY3 -> "emojiCategory3"
                ELEMENT_EMOJI_CATEGORY4 -> "emojiCategory4"
                ELEMENT_EMOJI_CATEGORY5 -> "emojiCategory5"
                ELEMENT_EMOJI_CATEGORY6 -> "emojiCategory6"
                ELEMENT_EMOJI_CATEGORY7 -> "emojiCategory7"
                ELEMENT_EMOJI_CATEGORY8 -> "emojiCategory8"
                ELEMENT_EMOJI_CATEGORY9 -> "emojiCategory9"
                ELEMENT_EMOJI_CATEGORY10 -> "emojiCategory10"
                ELEMENT_EMOJI_CATEGORY11 -> "emojiCategory11"
                ELEMENT_EMOJI_CATEGORY12 -> "emojiCategory12"
                ELEMENT_EMOJI_CATEGORY13 -> "emojiCategory13"
                ELEMENT_EMOJI_CATEGORY14 -> "emojiCategory14"
                ELEMENT_EMOJI_CATEGORY15 -> "emojiCategory15"
                ELEMENT_EMOJI_CATEGORY16 -> "emojiCategory16"
                ELEMENT_CLIPBOARD -> "clipboard"
                ELEMENT_NUMPAD -> "numpad"
                ELEMENT_EMOJI_BOTTOM_ROW -> "emojiBottomRow"
                ELEMENT_CLIPBOARD_BOTTOM_ROW -> "clipboardBottomRow"
                ELEMENT_HANDWRITING_BOTTOM_ROW -> "handwritingBottomRow"
                ELEMENT_TEXT_EDIT -> "editing"
                ELEMENT_CUSTOM1 -> "custom1"
                ELEMENT_CUSTOM2 -> "custom2"
                ELEMENT_CUSTOM3 -> "custom3"
                ELEMENT_CUSTOM4 -> "custom4"
                ELEMENT_CUSTOM5 -> "custom5"
                else -> null
            }
        }

        @JvmStatic
        fun modeName(mode: Int): String? {
            return when (mode) {
                MODE_TEXT -> "text"
                MODE_URL -> "url"
                MODE_EMAIL -> "email"
                MODE_IM -> "im"
                MODE_PHONE -> "phone"
                MODE_NUMBER -> "number"
                MODE_DATE -> "date"
                MODE_TIME -> "time"
                MODE_DATETIME -> "datetime"
                MODE_NUMPAD -> "numpad"
                else -> null
            }
        }

        @JvmStatic
        fun actionName(actionId: Int): String {
            return if (actionId == InputTypeUtils.IME_ACTION_CUSTOM_LABEL) {
                "actionCustomLabel"
            } else {
                EditorInfoCompatUtils.imeActionName(actionId)
            }
        }
    }
}
