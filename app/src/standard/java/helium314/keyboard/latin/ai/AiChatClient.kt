/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.ai

import android.content.Context
import helium314.keyboard.latin.utils.ProofreadService

class AiChatClient(private val context: Context) {
    suspend fun generateCandidates(requestContext: AiRequestContext): Result<List<AiCandidate>> {
        val prompt = buildPrompt(requestContext)
        val service = ProofreadService(context)
        return service.proofread(text = "", overridePrompt = prompt, showThinking = false).mapCatching { raw ->
            AiCandidateParser.parse(raw, requestContext.action).getOrThrow()
        }
    }

    private fun buildPrompt(requestContext: AiRequestContext): String {
        val snapshot = requestContext.editorSnapshot
        val draft = snapshot.fullDraft.take(1200)
        val selected = snapshot.selectedText.take(1200)
        val wechatContext = requestContext.wechatMessages.joinToString("\n") { "- $it" }

        val task = when (requestContext.action) {
            AiCandidateAction.REPLY -> "Generate three short natural Chinese reply candidates for the visible WeChat conversation."
            AiCandidateAction.REWRITE -> "Rewrite the selected or current text into three polished alternatives while preserving meaning."
            AiCandidateAction.INSPIRE -> "Generate three concise continuation or inspiration candidates that can be inserted at the cursor."
        }

        return """
            You are an input method assistant. $task
            Return ONLY strict JSON, no markdown, no explanation:
            {"candidates":[{"action":"${requestContext.action.name}","text":"candidate one"},{"action":"${requestContext.action.name}","text":"candidate two"},{"action":"${requestContext.action.name}","text":"candidate three"}]}

            Rules:
            - Each candidate must be short enough for a mobile candidate bar.
            - Do not mention that you are an AI.
            - Do not include quotes around the text value beyond JSON syntax.
            - Never suggest sending automatically.

            Locale: ${requestContext.localeTag}
            Current draft: $draft
            Selected text: $selected
            Text after cursor: ${snapshot.afterCursor.take(800)}
            Visible WeChat context:
            $wechatContext
        """.trimIndent()
    }
}
