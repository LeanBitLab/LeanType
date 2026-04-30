// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai

import android.text.InputType
import android.view.inputmethod.EditorInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiPrivacyGuardTest {
    @Test fun allowsGeneralTextInput() {
        val decision = AiPrivacyGuard.evaluate(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL,
            imeOptions = 0,
            incognito = false,
            noPersonalizedLearning = false,
            isGeneralTextInput = true
        )

        assertTrue(decision.allowed)
    }

    @Test fun blocksPasswordInput() {
        val decision = AiPrivacyGuard.evaluate(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            imeOptions = 0,
            incognito = false,
            noPersonalizedLearning = false,
            isGeneralTextInput = true
        )

        assertFalse(decision.allowed)
        assertEquals(AiPrivacyBlockReason.SENSITIVE_INPUT, decision.reason)
    }

    @Test fun blocksVisiblePasswordInput() {
        val decision = AiPrivacyGuard.evaluate(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            imeOptions = 0,
            incognito = false,
            noPersonalizedLearning = false,
            isGeneralTextInput = true
        )

        assertFalse(decision.allowed)
        assertEquals(AiPrivacyBlockReason.SENSITIVE_INPUT, decision.reason)
    }

    @Test fun blocksNoPersonalizedLearning() {
        val decision = AiPrivacyGuard.evaluate(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL,
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            incognito = false,
            noPersonalizedLearning = true,
            isGeneralTextInput = true
        )

        assertFalse(decision.allowed)
        assertEquals(AiPrivacyBlockReason.NO_PERSONALIZED_LEARNING, decision.reason)
    }

    @Test fun blocksIncognitoMode() {
        val decision = AiPrivacyGuard.evaluate(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL,
            imeOptions = 0,
            incognito = true,
            noPersonalizedLearning = false,
            isGeneralTextInput = true
        )

        assertFalse(decision.allowed)
        assertEquals(AiPrivacyBlockReason.INCOGNITO, decision.reason)
    }

    @Test fun blocksNonTextInput() {
        val decision = AiPrivacyGuard.evaluate(
            inputType = InputType.TYPE_CLASS_NUMBER,
            imeOptions = 0,
            incognito = false,
            noPersonalizedLearning = false,
            isGeneralTextInput = false
        )

        assertFalse(decision.allowed)
        assertEquals(AiPrivacyBlockReason.NON_TEXT_INPUT, decision.reason)
    }
}
