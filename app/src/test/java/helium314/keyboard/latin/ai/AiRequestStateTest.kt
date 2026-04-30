// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiRequestStateTest {
    @Test fun acceptsOnlyCurrentRequestForSameSnapshot() {
        val state = AiRequestState()
        val context = requestContext(requestId = 1, snapshot = snapshot(beforeCursor = "hi"))

        state.start(context)

        assertTrue(state.shouldAcceptResult(1, context.editorSnapshot))
        assertFalse(state.shouldAcceptResult(2, context.editorSnapshot))
        assertFalse(state.shouldAcceptResult(1, context.editorSnapshot.copy(beforeCursor = "changed")))
    }

    @Test fun cancelRejectsLateResult() {
        val state = AiRequestState()
        val context = requestContext(requestId = 1, snapshot = snapshot(beforeCursor = "hi"))

        state.start(context)
        state.cancel(AiCancelReason.USER_CLOSED)

        assertFalse(state.shouldAcceptResult(1, context.editorSnapshot))
    }

    private fun requestContext(requestId: Long, snapshot: AiEditorSnapshot) = AiRequestContext(
        requestId = requestId,
        action = AiCandidateAction.REPLY,
        editorSnapshot = snapshot,
        incognito = false,
        noPersonalizedLearning = false,
        localeTag = "zh-CN",
        wechatMessages = emptyList()
    )

    private fun snapshot(beforeCursor: String) = AiEditorSnapshot(
        packageName = "com.tencent.mm",
        inputType = 1,
        imeOptions = 0,
        fieldId = 42,
        privateImeOptions = "field",
        selectionStart = beforeCursor.length,
        selectionEnd = beforeCursor.length,
        beforeCursor = beforeCursor,
        selectedText = "",
        afterCursor = ""
    )
}
