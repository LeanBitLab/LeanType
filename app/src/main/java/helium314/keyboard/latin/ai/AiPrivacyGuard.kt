/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.ai

import android.text.InputType
import android.view.inputmethod.EditorInfo
import helium314.keyboard.latin.utils.InputTypeUtils

enum class AiPrivacyBlockReason {
    SENSITIVE_INPUT,
    NO_PERSONALIZED_LEARNING,
    INCOGNITO,
    NON_TEXT_INPUT
}
data class AiPrivacyDecision(
    val allowed: Boolean,
    val reason: AiPrivacyBlockReason? = null
)

object AiPrivacyGuard {
    @JvmStatic
    fun evaluate(
        inputType: Int,
        imeOptions: Int,
        incognito: Boolean,
        noPersonalizedLearning: Boolean,
        isGeneralTextInput: Boolean
    ): AiPrivacyDecision {
        if (InputTypeUtils.isAnyPasswordInputType(inputType)) {
            return AiPrivacyDecision(false, AiPrivacyBlockReason.SENSITIVE_INPUT)
        }
        if (incognito) {
            return AiPrivacyDecision(false, AiPrivacyBlockReason.INCOGNITO)
        }
        if (noPersonalizedLearning ||
            (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        ) {
            return AiPrivacyDecision(false, AiPrivacyBlockReason.NO_PERSONALIZED_LEARNING)
        }
        if ((inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT || !isGeneralTextInput) {
            return AiPrivacyDecision(false, AiPrivacyBlockReason.NON_TEXT_INPUT)
        }
        return AiPrivacyDecision(true)
    }
}
