/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import android.text.InputType
import android.view.inputmethod.EditorInfo
import helium314.keyboard.compat.AppWorkarounds

object InputTypeUtils {
    private const val WEB_TEXT_PASSWORD_INPUT_TYPE = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
    private const val NUMBER_PASSWORD_INPUT_TYPE = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    private const val TEXT_PASSWORD_INPUT_TYPE = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    private const val TEXT_VISIBLE_PASSWORD_INPUT_TYPE = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    private const val TEXT_NUMBER_INPUT_TYPE = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    private val SUPPRESSING_AUTO_SPACES_FIELD_VARIATION = intArrayOf(
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
    )
    const val IME_ACTION_CUSTOM_LABEL = EditorInfo.IME_MASK_ACTION + 1

    @JvmStatic
    fun isNumberInputType(inputType: Int): Boolean {
        return (inputType and TEXT_NUMBER_INPUT_TYPE) != 0
    }

    @JvmStatic
    fun isEmailVariation(variation: Int): Boolean {
        return variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
    }

    @JvmStatic
    fun isUriOrEmailType(inputType: Int): Boolean {
        if ((inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        val maskedInputType = inputType and InputType.TYPE_MASK_VARIATION
        return maskedInputType == InputType.TYPE_TEXT_VARIATION_URI || isEmailVariation(maskedInputType)
    }

    // Please refer to TextView.isPasswordInputType
    @JvmStatic
    fun isPasswordInputType(inputType: Int): Boolean {
        val maskedInputType = inputType and (InputType.TYPE_MASK_CLASS or InputType.TYPE_MASK_VARIATION)
        return (maskedInputType == WEB_TEXT_PASSWORD_INPUT_TYPE || maskedInputType == TEXT_PASSWORD_INPUT_TYPE
                || maskedInputType == NUMBER_PASSWORD_INPUT_TYPE)
    }

    // Please refer to TextView.isVisiblePasswordInputType
    @JvmStatic
    fun isVisiblePasswordInputType(inputType: Int): Boolean {
        val maskedInputType = inputType and (InputType.TYPE_MASK_CLASS or InputType.TYPE_MASK_VARIATION)
        return maskedInputType == TEXT_VISIBLE_PASSWORD_INPUT_TYPE
    }

    @JvmStatic
    fun isAnyPasswordInputType(inputType: Int): Boolean {
        return isPasswordInputType(inputType) || isVisiblePasswordInputType(inputType)
    }

    @JvmStatic
    fun isAutoSpaceFriendlyType(inputType: Int): Boolean {
        if (InputType.TYPE_CLASS_TEXT != (InputType.TYPE_MASK_CLASS and inputType)) return false
        val variation = InputType.TYPE_MASK_VARIATION and inputType
        for (fieldVariation in SUPPRESSING_AUTO_SPACES_FIELD_VARIATION) {
            if (variation == fieldVariation) return false
        }
        return true
    }

    @JvmStatic
    fun getImeOptionsActionIdFromEditorInfo(editorInfo: EditorInfo): Int {
        val imeOptions = AppWorkarounds.adjustImeOptions(editorInfo.imeOptions, editorInfo.packageName)
        if ((imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            return EditorInfo.IME_ACTION_NONE
        } else if (editorInfo.actionLabel != null) {
            return IME_ACTION_CUSTOM_LABEL
        } else {
            val actionId = imeOptions and EditorInfo.IME_MASK_ACTION
            if (actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                if ((imeOptions and EditorInfo.IME_FLAG_NAVIGATE_NEXT) != 0) {
                    return EditorInfo.IME_ACTION_NEXT
                } else if ((imeOptions and EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS) != 0) {
                    return EditorInfo.IME_ACTION_PREVIOUS
                }
            }
            return actionId
        }
    }
}
