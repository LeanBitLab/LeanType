// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.inputlogic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFieldCheckTest {
    private fun snapshot(around: String, extracted: String? = around, pkg: String = "com.example.mail",
                         inputType: Int = 1, password: Boolean = false) =
        AiFieldCheck.Snapshot(pkg, inputType, password, around, extracted)

    @Test
    fun sameFieldAndTextMatches() {
        val target = AiFieldCheck.fingerprint(snapshot("Hallo Welt"))
        assertTrue(target.matches(snapshot("Hallo Welt")))
    }

    @Test
    fun changedTextOrOtherAppDoesNotMatch() {
        val target = AiFieldCheck.fingerprint(snapshot("Hallo Welt"))
        assertFalse(target.matches(snapshot("Hallo Welt!")))
        assertFalse(target.matches(snapshot("Hallo Welt", pkg = "com.example.chat")))
        assertFalse(target.matches(snapshot("Hallo Welt", inputType = 2)))
        assertFalse(target.matches(snapshot("Hallo Welt", password = true)))
    }

    @Test
    fun wholeFieldTextHelpsWhenTheAppReportsTheSelectionUnreliably() {
        // when asked, the app did not report the selected part
        val target = AiFieldCheck.fingerprint(snapshot("Hallo ", extracted = "Hallo Welt"))
        // later it does: the text around the cursor differs, the whole field is the same
        assertTrue(target.matches(snapshot("Hallo Welt", extracted = "Hallo Welt")))
        // but not if the field really changed
        assertFalse(target.matches(snapshot("Hallo Welt", extracted = "Hallo Welt, wie geht's")))
        // nor without the whole text
        assertFalse(target.matches(snapshot("Hallo Welt", extracted = null)))
    }
}
