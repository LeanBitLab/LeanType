/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import helium314.keyboard.compat.isDeviceLocked
import helium314.keyboard.keyboard.internal.KeyboardBuilder
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.UniqueKeysCache
import helium314.keyboard.keyboard.internal.keyboard_parser.LayoutParser
import helium314.keyboard.keyboard.internal.keyboard_parser.LocaleKeyboardInfos
import helium314.keyboard.keyboard.internal.keyboard_parser.clearCache
import helium314.keyboard.keyboard.internal.keyboard_parser.getOrCreate
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.ScriptUtils.script
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import java.lang.ref.SoftReference
import java.util.HashMap

/**
 * This class represents a set of keyboard layouts. Each of them represents a different keyboard
 * specific to a keyboard state, such as alphabet, symbols, and so on. Layouts in the same
 * [KeyboardLayoutSet] are related to each other.
 * A [KeyboardLayoutSet] needs to be created for each [android.view.inputmethod.EditorInfo].
 */
class KeyboardLayoutSet internal constructor(
    private val mContext: Context,
    private val mParams: Params
) {
    val mLocaleKeyboardInfos: LocaleKeyboardInfos =
        getOrCreate(mContext, mParams.mSubtype.locale)

    class KeyboardLayoutSetException(cause: Throwable, val mKeyboardId: KeyboardId) :
        RuntimeException(cause)

    /**
     * Represents an internal action that overrides the action provided by the input field.
     * @param code to send on action key press
     * @param label to display on action key
     */
    data class InternalAction(val code: Int, val label: String) {
        fun code(): Int = code
        fun label(): String = label
    }

    class Params {
        var mMode: Int = 0
        var mDisableTouchPositionCorrectionDataForTest: Boolean = false
        var mEditorInfo: EditorInfo = EditorInfo()
        var mVoiceInputKeyEnabled: Boolean = false
        var mDeviceLocked: Boolean = false
        var mNumberRowEnabled: Boolean = false
        var mNumberRowInSymbols: Boolean = false
        var mCompactNumberRowInSymbols: Boolean = false
        var mLanguageSwitchKeyEnabled: Boolean = false
        var mEmojiKeyEnabled: Boolean = false
        var mOneHandedModeEnabled: Boolean = false
        var mSubtype: RichInputMethodSubtype = RichInputMethodSubtype.noLanguageSubtype
        var mIsSpellChecker: Boolean = false
        var mKeyboardWidth: Int = 0
        var mKeyboardHeight: Int = 0
        var mScript: String = ScriptUtils.SCRIPT_LATIN
        var mIsSplitLayoutEnabled: Boolean = false
        var mInternalAction: InternalAction? = null
    }

    fun getKeyboard(baseKeyboardLayoutSetElementId: Int): Keyboard {
        val keyboardLayoutSetElementId = when (mParams.mMode) {
            KeyboardId.MODE_PHONE -> {
                if (baseKeyboardLayoutSetElementId == KeyboardId.ELEMENT_SYMBOLS) {
                    KeyboardId.ELEMENT_PHONE_SYMBOLS
                } else {
                    KeyboardId.ELEMENT_PHONE
                }
            }
            KeyboardId.MODE_NUMPAD -> KeyboardId.ELEMENT_NUMPAD
            KeyboardId.MODE_NUMBER, KeyboardId.MODE_DATE, KeyboardId.MODE_TIME, KeyboardId.MODE_DATETIME ->
                KeyboardId.ELEMENT_NUMBER
            else -> baseKeyboardLayoutSetElementId
        }

        val id = KeyboardId(keyboardLayoutSetElementId, mParams)
        try {
            return getKeyboard(id)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Can't create keyboard: $id", e)
            throw KeyboardLayoutSetException(e, id)
        }
    }

    private fun getKeyboard(id: KeyboardId): Keyboard {
        val ref = sKeyboardCache[id]
        val cachedKeyboard = ref?.get()
        if (cachedKeyboard != null) {
            if (DEBUG_CACHE) {
                Log.d(TAG, "keyboard cache size=${sKeyboardCache.size}: HIT  id=$id")
            }
            return cachedKeyboard
        }

        val builder = KeyboardBuilder(mContext, KeyboardParams(sUniqueKeysCache))
        sUniqueKeysCache.setEnabled(id.isAlphabetKeyboard)
        builder.load(id)
        if (mParams.mDisableTouchPositionCorrectionDataForTest) {
            builder.disableTouchPositionCorrectionDataForTest()
        }
        val keyboard = builder.build()
        sKeyboardCache[id] = SoftReference(keyboard)
        if ((id.mElementId == KeyboardId.ELEMENT_ALPHABET ||
                    id.mElementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED) &&
            !mParams.mIsSpellChecker
        ) {
            for (i in sForcibleKeyboardCache.size - 1 downTo 1) {
                sForcibleKeyboardCache[i] = sForcibleKeyboardCache[i - 1]
            }
            sForcibleKeyboardCache[0] = keyboard
            if (DEBUG_CACHE) {
                Log.d(TAG, "forcing caching of keyboard with id=$id")
            }
        }
        if (DEBUG_CACHE) {
            Log.d(TAG, "keyboard cache size=${sKeyboardCache.size}: ${if (ref == null) "LOAD" else "GCed"} id=$id")
        }
        return keyboard
    }

    fun getScript(): String = mParams.mScript

    class Builder(context: Context, ei: EditorInfo?) {
        private val mContext: Context = context
        private val mParams: Params = Params()
        private var mSubtypeSpecified: Boolean = false

        init {
            val editorInfo = ei ?: EMPTY_EDITOR_INFO
            mParams.mMode = getKeyboardMode(editorInfo)
            mParams.mEditorInfo = editorInfo
            mParams.mDeviceLocked = isDeviceLocked(context)
        }

        fun setKeyboardGeometry(keyboardWidth: Int, keyboardHeight: Int): Builder {
            mParams.mKeyboardWidth = keyboardWidth
            mParams.mKeyboardHeight = keyboardHeight
            return this
        }

        fun setSubtype(subtype: RichInputMethodSubtype): Builder {
            val asciiCapable = subtype.rawSubtype.isAsciiCapable
            val forceAscii = (mParams.mEditorInfo.imeOptions and EditorInfo.IME_FLAG_FORCE_ASCII) != 0
            mParams.mSubtype = if (forceAscii && !asciiCapable) {
                RichInputMethodSubtype.noLanguageSubtype
            } else {
                subtype
            }
            mSubtypeSpecified = true
            return this
        }

        fun setIsSpellChecker(isSpellChecker: Boolean): Builder {
            mParams.mIsSpellChecker = isSpellChecker
            return this
        }

        fun setVoiceInputKeyEnabled(enabled: Boolean): Builder {
            mParams.mVoiceInputKeyEnabled = enabled
            return this
        }

        fun setNumberRowEnabled(enabled: Boolean): Builder {
            mParams.mNumberRowEnabled = enabled
            return this
        }

        fun setNumberRowInSymbolsEnabled(enabled: Boolean): Builder {
            mParams.mNumberRowInSymbols = enabled
            return this
        }

        fun setCompactNumberRowInSymbolsEnabled(enabled: Boolean): Builder {
            mParams.mCompactNumberRowInSymbols = enabled
            return this
        }

        fun setLanguageSwitchKeyEnabled(enabled: Boolean): Builder {
            mParams.mLanguageSwitchKeyEnabled = enabled
            return this
        }

        fun setEmojiKeyEnabled(enabled: Boolean): Builder {
            mParams.mEmojiKeyEnabled = enabled
            return this
        }

        fun disableTouchPositionCorrectionData(): Builder {
            mParams.mDisableTouchPositionCorrectionDataForTest = true
            return this
        }

        fun setSplitLayoutEnabled(enabled: Boolean): Builder {
            mParams.mIsSplitLayoutEnabled = enabled
            return this
        }

        fun setOneHandedModeEnabled(enabled: Boolean): Builder {
            mParams.mOneHandedModeEnabled = enabled
            return this
        }

        fun setInternalAction(internalAction: InternalAction?): Builder {
            mParams.mInternalAction = internalAction
            return this
        }

        fun build(): KeyboardLayoutSet {
            if (!mSubtypeSpecified) {
                throw RuntimeException("KeyboardLayoutSet subtype is not specified")
            }
            mParams.mScript = mParams.mSubtype.locale.script()
            return KeyboardLayoutSet(mContext, mParams)
        }

        companion object {
            private val EMPTY_EDITOR_INFO = EditorInfo()

            fun buildEmojiClipBottomRow(context: Context, ei: EditorInfo?): KeyboardLayoutSet {
                val builder = Builder(context, ei)
                builder.mParams.mMode = KeyboardId.MODE_TEXT
                val width = ResourceUtils.getKeyboardWidth(context, Settings.getValues())
                val height = ResourceUtils.getKeyboardHeight(context.resources, Settings.getValues())
                builder.setKeyboardGeometry(width, height)
                builder.setSubtype(RichInputMethodManager.getInstance().currentSubtype)
                builder.setSplitLayoutEnabled(Settings.getValues().mIsSplitKeyboardEnabled)
                return builder.build()
            }

            fun getKeyboardMode(editorInfo: EditorInfo): Int {
                val inputType = editorInfo.inputType
                val variation = inputType and InputType.TYPE_MASK_VARIATION
                return when (inputType and InputType.TYPE_MASK_CLASS) {
                    InputType.TYPE_CLASS_NUMBER -> KeyboardId.MODE_NUMBER
                    InputType.TYPE_CLASS_DATETIME -> when (variation) {
                        InputType.TYPE_DATETIME_VARIATION_DATE -> KeyboardId.MODE_DATE
                        InputType.TYPE_DATETIME_VARIATION_TIME -> KeyboardId.MODE_TIME
                        else -> KeyboardId.MODE_DATETIME
                    }
                    InputType.TYPE_CLASS_PHONE -> KeyboardId.MODE_PHONE
                    InputType.TYPE_CLASS_TEXT -> {
                        if (InputTypeUtils.isEmailVariation(variation)) {
                            KeyboardId.MODE_EMAIL
                        } else if (variation == InputType.TYPE_TEXT_VARIATION_URI) {
                            KeyboardId.MODE_URL
                        } else {
                            KeyboardId.MODE_TEXT
                        }
                    }
                    else -> KeyboardId.MODE_TEXT
                }
            }
        }
    }

    companion object {
        private val TAG = KeyboardLayoutSet::class.simpleName
        private const val DEBUG_CACHE = false

        private const val FORCIBLE_CACHE_SIZE = 4
        private val sForcibleKeyboardCache = arrayOfNulls<Keyboard>(FORCIBLE_CACHE_SIZE)
        private val sKeyboardCache = HashMap<KeyboardId, SoftReference<Keyboard>>()
        private val sUniqueKeysCache = UniqueKeysCache.newInstance()

        fun onSystemLocaleChanged() {
            clearKeyboardCache()
            clearCache()
            SubtypeLocaleUtils.clearSubtypeDisplayNameCache()
        }

        fun onKeyboardThemeChanged() {
            clearKeyboardCache()
        }

        private fun clearKeyboardCache() {
            sKeyboardCache.clear()
            sUniqueKeysCache.clear()
            LayoutParser.clearCache()
            KeyboardIconsSet.needsReload = true
        }

        // used for testing keyboard layout files without actually creating a keyboard
        fun getFakeKeyboardId(elementId: Int): KeyboardId {
            val params = Params()
            params.mEditorInfo = EditorInfo()
            val emojiSubtype = RichInputMethodSubtype.emojiSubtype
            params.mSubtype = emojiSubtype
            emojiSubtype.mainLayoutName
            return KeyboardId(elementId, params)
        }
    }
}
