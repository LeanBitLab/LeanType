// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InputAttributesTest {

    private fun createAttributes(inputType: Int, packageName: String = "com.example.app"): InputAttributes {
        val editorInfo = EditorInfo().apply {
            this.inputType = inputType
            this.packageName = packageName
        }
        return InputAttributes(editorInfo, false, null)
    }

    @Test
    fun testStandardSingleLineTextInputShouldAutoCorrect() {
        val attrs = createAttributes(InputType.TYPE_CLASS_TEXT)
        assertTrue("Standard single-line text input should autocorrect", attrs.mInputTypeShouldAutoCorrect)
    }

    @Test
    fun testMultiLineTextInputShouldAutoCorrect() {
        val attrs = createAttributes(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        assertTrue("Multi-line text input should autocorrect", attrs.mInputTypeShouldAutoCorrect)
    }

    @Test
    fun testShortMessageTextInputShouldAutoCorrect() {
        val attrs = createAttributes(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE)
        assertTrue("Short message text input should autocorrect", attrs.mInputTypeShouldAutoCorrect)
    }

    @Test
    fun testPasswordFieldsShouldNotAutoCorrect() {
        val attrsPassword = createAttributes(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertFalse(attrsPassword.mInputTypeShouldAutoCorrect)

        val attrsVisiblePassword = createAttributes(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        assertFalse(attrsVisiblePassword.mInputTypeShouldAutoCorrect)

        val attrsWebPassword = createAttributes(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        assertFalse(attrsWebPassword.mInputTypeShouldAutoCorrect)
    }

    @Test
    fun testEmailAndUriFieldsShouldNotAutoCorrect() {
        val attrsEmail = createAttributes(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        assertFalse(attrsEmail.mInputTypeShouldAutoCorrect)

        val attrsWebEmail = createAttributes(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        assertFalse(attrsWebEmail.mInputTypeShouldAutoCorrect)

        val attrsUri = createAttributes(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        assertFalse(attrsUri.mInputTypeShouldAutoCorrect)
    }

    @Test
    fun testWebEditTextShouldNotAutoCorrectUnlessExplicit() {
        val attrsWeb = createAttributes(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT)
        assertFalse(attrsWeb.mInputTypeShouldAutoCorrect)
    }

    @Test
    fun testNoSuggestionsFlagShouldNotAutoCorrect() {
        val attrs = createAttributes(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        assertFalse(attrs.mInputTypeShouldAutoCorrect)
    }

    @Test
    fun testExplicitAutoCorrectFlagShouldAutoCorrect() {
        val attrs = createAttributes(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        assertTrue(attrs.mInputTypeShouldAutoCorrect)
    }
}
