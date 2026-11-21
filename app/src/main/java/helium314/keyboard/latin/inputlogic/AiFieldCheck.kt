// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.inputlogic

/**
 * Whether an AI result can be inserted into the field it was asked for. The field is identified by
 * the app, the input type and its text: the text around the cursor plus the selection, and as a second way
 * the whole text as the app gives it for extraction, because some apps report the selection or the text
 * around the cursor unreliably. Without a match the result goes to the clipboard instead.
 */
internal object AiFieldCheck {
    class Snapshot(
        val packageName: String?,
        val inputType: Int,
        val isPassword: Boolean,
        /** text before the cursor + selected text + text after the cursor */
        val aroundCursor: String,
        /** the whole text from getExtractedText, null if the app does not give it */
        val extracted: String?,
    )

    class Fingerprint internal constructor(
        private val packageName: String?,
        private val inputType: Int,
        private val aroundHash: Int,
        private val aroundLength: Int,
        private val extractedHash: Int?,
        private val extractedLength: Int,
    ) {
        fun matches(now: Snapshot): Boolean {
            if (now.isPassword || now.packageName != packageName || now.inputType != inputType) return false
            if (now.aroundCursor.length == aroundLength && now.aroundCursor.hashCode() == aroundHash) return true
            val extracted = now.extracted ?: return false
            return extractedHash != null && extracted.length == extractedLength && extracted.hashCode() == extractedHash
        }
    }

    fun fingerprint(s: Snapshot) = Fingerprint(
        s.packageName, s.inputType, s.aroundCursor.hashCode(), s.aroundCursor.length,
        s.extracted?.hashCode(), s.extracted?.length ?: -1,
    )
}
