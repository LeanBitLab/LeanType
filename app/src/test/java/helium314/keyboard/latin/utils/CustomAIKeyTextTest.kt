// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomAIKeyTextTest {

    @Test
    fun instructionsGoBeforeThePrompt() {
        val prompt = "Message: {text}\nAnswer:"
        assertEquals("Output ONLY the result.\n\n$prompt", CustomAIKeyText.withInstructions(prompt, " Output ONLY the result."))
    }

    @Test
    fun noInstructionsKeepThePrompt() {
        assertEquals("Summarize.", CustomAIKeyText.withInstructions("Summarize.", ""))
    }

    @Test
    fun appendedResultStartsANewParagraph() {
        assertEquals("\n\n", CustomAIKeyText.separatorBefore("Best regards, Sam"))
        assertEquals("\n", CustomAIKeyText.separatorBefore("Best regards, Sam\n"))
        assertEquals("", CustomAIKeyText.separatorBefore("Best regards, Sam\n\n"))
        assertEquals("", CustomAIKeyText.separatorBefore(""))
    }
}
