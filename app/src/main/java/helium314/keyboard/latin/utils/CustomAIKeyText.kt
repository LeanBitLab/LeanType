// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

/** Text handling of the custom AI keys that does not need an input connection */
object CustomAIKeyText {
    /**
     * Instructions of the selected keywords go before the user's prompt, so a prompt that ends
     * with an answer start (e.g. "Answer:") or embeds the text with `{text}` keeps its ending.
     */
    fun withInstructions(prompt: String, instructions: String): String {
        val trimmed = instructions.trim()
        return if (trimmed.isEmpty()) prompt else "$trimmed\n\n$prompt"
    }

    /** Line breaks to put before a result appended after [textBefore], so it starts in a new paragraph */
    fun separatorBefore(textBefore: CharSequence): String = when {
        textBefore.isBlank() -> ""
        textBefore.endsWith("\n\n") -> ""
        textBefore.endsWith("\n") -> "\n"
        else -> "\n\n"
    }
}
