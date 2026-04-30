/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.ai

class AiRequestState {
    private var currentContext: AiRequestContext? = null

    fun start(context: AiRequestContext) {
        currentContext = context
    }

    fun cancel(reason: AiCancelReason) {
        currentContext = null
    }

    fun shouldAcceptResult(requestId: Long, currentSnapshot: AiEditorSnapshot): Boolean {
        val context = currentContext ?: return false
        return context.requestId == requestId && context.editorSnapshot.sameSelectionAndText(currentSnapshot)
    }

    fun currentContext(): AiRequestContext? = currentContext
}
