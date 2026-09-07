/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin

import android.os.Build
import android.text.InputType
import android.view.inputmethod.EditorInfo
import helium314.keyboard.compat.AppWorkarounds
import helium314.keyboard.latin.common.Constants.ImeOption.NO_FLOATING_GESTURE_PREVIEW
import helium314.keyboard.latin.common.Constants.ImeOption.NO_MICROPHONE
import helium314.keyboard.latin.common.containsValueWhenSplit
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.Log
import java.util.Arrays

/**
 * Class to hold attributes of the input field.
 */
class InputAttributes(
    editorInfo: EditorInfo?,
    isFullscreenMode: Boolean,
    packageNameForPrivateImeOptions: String?
) {
    val mTargetApplicationPackageName: String? = editorInfo?.packageName
    val mInputTypeShouldAutoCorrect: Boolean
    val mIsPasswordField: Boolean
    val mShouldShowSuggestions: Boolean
    val mMayOverrideShowingSuggestions: Boolean
    val mApplicationSpecifiedCompletionOn: Boolean
    val mShouldInsertSpacesAutomatically: Boolean
    val mShouldShowVoiceInputKey: Boolean
    val mNoLearning: Boolean
    val mDisableGestureFloatingPreviewText: Boolean
    val mIsGeneralTextInput: Boolean
    val mInputType: Int = AppWorkarounds.adjustInputType(editorInfo?.inputType ?: 0, mTargetApplicationPackageName)

    private val mEditorInfo: EditorInfo? = editorInfo
    private val mPackageNameForPrivateImeOptions: String? = packageNameForPrivateImeOptions

    init {
        val inputClass = mInputType and InputType.TYPE_MASK_CLASS
        mIsPasswordField = InputTypeUtils.isPasswordInputType(mInputType) || InputTypeUtils.isVisiblePasswordInputType(mInputType)

        if (inputClass != InputType.TYPE_CLASS_TEXT) {
            if (editorInfo == null) {
                Log.w(TAG, "No editor info for this field. Bug?")
            } else if (InputType.TYPE_NULL == mInputType) {
                Log.i(TAG, "InputType.TYPE_NULL is specified")
            } else if (inputClass == 0) {
                Log.w(TAG, String.format("Unexpected input class: inputType=0x%08x imeOptions=0x%08x", mInputType, editorInfo.imeOptions))
            }
            mShouldShowSuggestions = false
            mMayOverrideShowingSuggestions = false
            mInputTypeShouldAutoCorrect = false
            mApplicationSpecifiedCompletionOn = false
            mShouldInsertSpacesAutomatically = false
            mShouldShowVoiceInputKey = false
            mDisableGestureFloatingPreviewText = false
            mIsGeneralTextInput = false
            mNoLearning = false
        } else {
            val variation = mInputType and InputType.TYPE_MASK_VARIATION
            val flagNoSuggestions = 0 != (mInputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            val flagMultiLine = 0 != (mInputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            val flagAutoCorrect = 0 != (mInputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
            val flagAutoComplete = 0 != (mInputType and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)

            mShouldShowSuggestions = !mIsPasswordField && !flagNoSuggestions
            mMayOverrideShowingSuggestions = !mIsPasswordField
            mShouldInsertSpacesAutomatically = InputTypeUtils.isAutoSpaceFriendlyType(mInputType)

            val noMicrophone = mIsPasswordField ||
                    InputTypeUtils.isEmailVariation(variation) ||
                    hasNoMicrophoneKeyOption() ||
                    !RichInputMethodManager.isInitialized() ||
                    !RichInputMethodManager.getInstance().isShortcutImeReady
            mShouldShowVoiceInputKey = !noMicrophone

            mDisableGestureFloatingPreviewText = inPrivateImeOptions(mPackageNameForPrivateImeOptions, NO_FLOATING_GESTURE_PREVIEW, editorInfo)

            mInputTypeShouldAutoCorrect = flagAutoCorrect || (flagMultiLine &&
                    variation != InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT &&
                    variation != InputType.TYPE_TEXT_VARIATION_URI &&
                    !InputTypeUtils.isEmailVariation(variation) &&
                    !flagNoSuggestions)

            mApplicationSpecifiedCompletionOn = flagAutoComplete && isFullscreenMode

            mIsGeneralTextInput = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS != variation &&
                    InputType.TYPE_TEXT_VARIATION_PASSWORD != variation &&
                    InputType.TYPE_TEXT_VARIATION_PHONETIC != variation &&
                    InputType.TYPE_TEXT_VARIATION_URI != variation &&
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD != variation &&
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS != variation &&
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD != variation

            mNoLearning = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (editorInfo?.imeOptions?.and(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) ?: 0) != 0
            } else {
                false
            }
        }
    }

    val isTypeNull: Boolean
        get() = InputType.TYPE_NULL == mInputType

    fun isSameInputType(editorInfo: EditorInfo): Boolean {
        return editorInfo.inputType == mInputType && mEditorInfo != null &&
                (mEditorInfo.imeOptions and EditorInfo.IME_FLAG_FORCE_ASCII) == (editorInfo.imeOptions and EditorInfo.IME_FLAG_FORCE_ASCII)
    }

    private fun hasNoMicrophoneKeyOption(): Boolean {
        return inPrivateImeOptions(mPackageNameForPrivateImeOptions, NO_MICROPHONE, mEditorInfo)
    }

    @Suppress("unused")
    private fun dumpFlags(inputType: Int) {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val inputClassString = toInputClassString(inputClass)
        val variationString = toVariationString(
            inputClass, inputType and InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        val flagsString = toFlagsString(inputType and InputType.TYPE_MASK_FLAGS)
        Log.i(TAG, "Input class: $inputClassString")
        Log.i(TAG, "Variation: $variationString")
        Log.i(TAG, "Flags: $flagsString")
    }

    override fun toString(): String {
        return String.format(
            "%s: inputType=0x%08x%s%s%s%s%s targetApp=%s\n", javaClass.simpleName,
            mInputType,
            if (mInputTypeShouldAutoCorrect) " shouldAutoCorrect" else "",
            if (mIsPasswordField) " password" else "",
            if (mShouldShowSuggestions) " shouldShowSuggestions" else "",
            if (mApplicationSpecifiedCompletionOn) " appSpecified" else "",
            if (mShouldInsertSpacesAutomatically) " insertSpaces" else "",
            mTargetApplicationPackageName
        )
    }

    companion object {
        private val TAG = InputAttributes::class.java.simpleName

        @JvmStatic
        fun inPrivateImeOptions(packageName: String?, key: String?, editorInfo: EditorInfo?): Boolean {
            if (editorInfo == null || key == null) return false
            val findingKey = if (packageName != null) "$packageName.$key" else key
            return containsValueWhenSplit(editorInfo.privateImeOptions, findingKey, ",")
        }

        private fun toInputClassString(inputClass: Int): String = when (inputClass) {
            InputType.TYPE_CLASS_TEXT -> "TYPE_CLASS_TEXT"
            InputType.TYPE_CLASS_PHONE -> "TYPE_CLASS_PHONE"
            InputType.TYPE_CLASS_NUMBER -> "TYPE_CLASS_NUMBER"
            InputType.TYPE_CLASS_DATETIME -> "TYPE_CLASS_DATETIME"
            else -> String.format("unknownInputClass<0x%08x>", inputClass)
        }

        private fun toVariationString(inputClass: Int, variation: Int): String = when (inputClass) {
            InputType.TYPE_CLASS_TEXT -> toTextVariationString(variation)
            InputType.TYPE_CLASS_NUMBER -> toNumberVariationString(variation)
            InputType.TYPE_CLASS_DATETIME -> toDatetimeVariationString(variation)
            else -> ""
        }

        private fun toTextVariationString(variation: Int): String = when (variation) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> " TYPE_TEXT_VARIATION_EMAIL_ADDRESS"
            InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT -> "TYPE_TEXT_VARIATION_EMAIL_SUBJECT"
            InputType.TYPE_TEXT_VARIATION_FILTER -> "TYPE_TEXT_VARIATION_FILTER"
            InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE -> "TYPE_TEXT_VARIATION_LONG_MESSAGE"
            InputType.TYPE_TEXT_VARIATION_NORMAL -> "TYPE_TEXT_VARIATION_NORMAL"
            InputType.TYPE_TEXT_VARIATION_PASSWORD -> "TYPE_TEXT_VARIATION_PASSWORD"
            InputType.TYPE_TEXT_VARIATION_PERSON_NAME -> "TYPE_TEXT_VARIATION_PERSON_NAME"
            InputType.TYPE_TEXT_VARIATION_PHONETIC -> "TYPE_TEXT_VARIATION_PHONETIC"
            InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS -> "TYPE_TEXT_VARIATION_POSTAL_ADDRESS"
            InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE -> "TYPE_TEXT_VARIATION_SHORT_MESSAGE"
            InputType.TYPE_TEXT_VARIATION_URI -> "TYPE_TEXT_VARIATION_URI"
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> "TYPE_TEXT_VARIATION_VISIBLE_PASSWORD"
            InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT -> "TYPE_TEXT_VARIATION_WEB_EDIT_TEXT"
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> "TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS"
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> "TYPE_TEXT_VARIATION_WEB_PASSWORD"
            else -> String.format("unknownVariation<0x%08x>", variation)
        }

        private fun toNumberVariationString(variation: Int): String = when (variation) {
            InputType.TYPE_NUMBER_VARIATION_NORMAL -> "TYPE_NUMBER_VARIATION_NORMAL"
            InputType.TYPE_NUMBER_VARIATION_PASSWORD -> "TYPE_NUMBER_VARIATION_PASSWORD"
            else -> String.format("unknownVariation<0x%08x>", variation)
        }

        private fun toDatetimeVariationString(variation: Int): String = when (variation) {
            InputType.TYPE_DATETIME_VARIATION_NORMAL -> "TYPE_DATETIME_VARIATION_NORMAL"
            InputType.TYPE_DATETIME_VARIATION_DATE -> "TYPE_DATETIME_VARIATION_DATE"
            InputType.TYPE_DATETIME_VARIATION_TIME -> "TYPE_DATETIME_VARIATION_TIME"
            else -> String.format("unknownVariation<0x%08x>", variation)
        }

        private fun toFlagsString(flags: Int): String {
            val flagsArray = ArrayList<String>()
            if (0 != (flags and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)) flagsArray.add("TYPE_TEXT_FLAG_NO_SUGGESTIONS")
            if (0 != (flags and InputType.TYPE_TEXT_FLAG_MULTI_LINE)) flagsArray.add("TYPE_TEXT_FLAG_MULTI_LINE")
            if (0 != (flags and InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE)) flagsArray.add("TYPE_TEXT_FLAG_IME_MULTI_LINE")
            if (0 != (flags and InputType.TYPE_TEXT_FLAG_CAP_WORDS)) flagsArray.add("TYPE_TEXT_FLAG_CAP_WORDS")
            if (0 != (flags and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)) flagsArray.add("TYPE_TEXT_FLAG_CAP_SENTENCES")
            if (0 != (flags and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)) flagsArray.add("TYPE_TEXT_FLAG_CAP_CHARACTERS")
            if (0 != (flags and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)) flagsArray.add("TYPE_TEXT_FLAG_AUTO_CORRECT")
            if (0 != (flags and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)) flagsArray.add("TYPE_TEXT_FLAG_AUTO_COMPLETE")
            return if (flagsArray.isEmpty()) "" else Arrays.toString(flagsArray.toTypedArray())
        }
    }
}
