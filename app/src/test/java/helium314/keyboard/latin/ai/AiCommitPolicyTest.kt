// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AiCommitPolicyTest {
    @Test fun replyReplacesCurrentDraftWhenThereWasNoSelection() {
        val request = requestContext(
            action = AiCandidateAction.REPLY,
            snapshot = snapshot(selectionStart = 2, selectionEnd = 2, beforeCursor = "hi")
        )
        val operation = AiCommitPolicy.createOperation(
            request,
            request.editorSnapshot,
            AiCandidate(AiCandidateAction.REPLY, "hello")
        )

        assertEquals(AiCommitOperation.Replace(0, 2, "hello"), operation)
    }

    @Test fun rewriteReplacesSelectedText() {
        val request = requestContext(
            action = AiCandidateAction.REWRITE,
            snapshot = snapshot(
                selectionStart = 2,
                selectionEnd = 5,
                beforeCursor = "A ",
                selectedText = "bad",
                afterCursor = " text"
            )
        )
        val operation = AiCommitPolicy.createOperation(
            request,
            request.editorSnapshot,
            AiCandidate(AiCandidateAction.REWRITE, "better")
        )

        assertEquals(AiCommitOperation.Replace(2, 5, "better"), operation)
    }

    @Test fun inspireInsertsAtCursorWhenThereWasNoSelection() {
        val request = requestContext(
            action = AiCandidateAction.INSPIRE,
            snapshot = snapshot(selectionStart = 2, selectionEnd = 2, beforeCursor = "hi")
        )
        val operation = AiCommitPolicy.createOperation(
            request,
            request.editorSnapshot,
            AiCandidate(AiCandidateAction.INSPIRE, " there")
        )

        assertEquals(AiCommitOperation.Insert(2, " there"), operation)
    }

    @Test fun refusesCommitWhenEditorIdentityChanged() {
        val request = requestContext(
            action = AiCandidateAction.REPLY,
            snapshot = snapshot(packageName = "com.tencent.mm", beforeCursor = "hi")
        )
        val current = request.editorSnapshot.copy(packageName = "com.example.other")

        assertNull(
            AiCommitPolicy.createOperation(
                request,
                current,
                AiCandidate(AiCandidateAction.REPLY, "hello")
            )
        )
    }

    @Test fun refusesRewriteWhenDraftChanged() {
        val request = requestContext(
            action = AiCandidateAction.REWRITE,
            snapshot = snapshot(selectionStart = 2, selectionEnd = 2, beforeCursor = "hi")
        )
        val current = request.editorSnapshot.copy(beforeCursor = "changed")

        assertNull(
            AiCommitPolicy.createOperation(
                request,
                current,
                AiCandidate(AiCandidateAction.REWRITE, "hello")
            )
        )
    }

    private fun requestContext(
        action: AiCandidateAction,
        snapshot: AiEditorSnapshot
    ) = AiRequestContext(
        requestId = 7,
        action = action,
        editorSnapshot = snapshot,
        incognito = false,
        noPersonalizedLearning = false,
        localeTag = "zh-CN",
        wechatMessages = emptyList()
    )

    private fun snapshot(
        packageName: String = "com.tencent.mm",
        inputType: Int = 1,
        imeOptions: Int = 0,
        fieldId: Int = 42,
        privateImeOptions: String? = "field",
        selectionStart: Int = 0,
        selectionEnd: Int = 0,
        beforeCursor: String = "",
        selectedText: String = "",
        afterCursor: String = ""
    ) = AiEditorSnapshot(
        packageName = packageName,
        inputType = inputType,
        imeOptions = imeOptions,
        fieldId = fieldId,
        privateImeOptions = privateImeOptions,
        selectionStart = selectionStart,
        selectionEnd = selectionEnd,
        beforeCursor = beforeCursor,
        selectedText = selectedText,
        afterCursor = afterCursor
    )
}
