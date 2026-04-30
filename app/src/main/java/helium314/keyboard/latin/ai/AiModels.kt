/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.ai

enum class AiCandidateAction {
    REPLY,
    REWRITE,
    INSPIRE
}

data class AiCandidate(
    val action: AiCandidateAction,
    val text: String
)

enum class AiCancelReason {
    NEW_REQUEST,
    USER_CLOSED,
    START_INPUT_VIEW,
    FINISH_INPUT_VIEW,
    HIDE_WINDOW
}

data class AiEditorSnapshot(
    val packageName: String?,
    val inputType: Int,
    val imeOptions: Int,
    val fieldId: Int,
    val privateImeOptions: String?,
    val selectionStart: Int,
    val selectionEnd: Int,
    val beforeCursor: String,
    val selectedText: String,
    val afterCursor: String
) {
    val hasSelection: Boolean
        get() = selectionStart != selectionEnd

    val fullDraft: String
        get() = beforeCursor + selectedText + afterCursor

    fun sameEditorIdentity(other: AiEditorSnapshot): Boolean =
        packageName == other.packageName &&
            inputType == other.inputType &&
            imeOptions == other.imeOptions &&
            fieldId == other.fieldId &&
            privateImeOptions == other.privateImeOptions

    fun sameSelectionAndText(other: AiEditorSnapshot): Boolean =
        sameEditorIdentity(other) &&
            selectionStart == other.selectionStart &&
            selectionEnd == other.selectionEnd &&
            beforeCursor == other.beforeCursor &&
            selectedText == other.selectedText &&
            afterCursor == other.afterCursor
}

data class AiRequestContext(
    val requestId: Long,
    val action: AiCandidateAction,
    val editorSnapshot: AiEditorSnapshot,
    val incognito: Boolean,
    val noPersonalizedLearning: Boolean,
    val localeTag: String,
    val wechatMessages: List<String>
) {
    val packageName: String?
        get() = editorSnapshot.packageName

    val inputType: Int
        get() = editorSnapshot.inputType

    val imeOptions: Int
        get() = editorSnapshot.imeOptions

    val fieldId: Int
        get() = editorSnapshot.fieldId

    val privateImeOptions: String?
        get() = editorSnapshot.privateImeOptions
}

sealed class AiCommitOperation {
    data class Replace(val start: Int, val end: Int, val text: String) : AiCommitOperation()
    data class Insert(val position: Int, val text: String) : AiCommitOperation()
}
