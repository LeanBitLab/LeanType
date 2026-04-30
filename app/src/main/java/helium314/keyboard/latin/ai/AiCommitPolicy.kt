/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.ai

object AiCommitPolicy {
    fun createOperation(
        requestContext: AiRequestContext,
        currentSnapshot: AiEditorSnapshot,
        candidate: AiCandidate
    ): AiCommitOperation? {
        val requestSnapshot = requestContext.editorSnapshot
        if (!requestSnapshot.sameEditorIdentity(currentSnapshot)) {
            return null
        }

        val action = candidate.action
        val textUnchanged = requestSnapshot.sameSelectionAndText(currentSnapshot)
        val hadSelection = requestSnapshot.hasSelection
        if (!textUnchanged) {
            return null
        }

        return when (action) {
            AiCandidateAction.REPLY -> {
                if (hadSelection) {
                    AiCommitOperation.Replace(requestSnapshot.selectionStart, requestSnapshot.selectionEnd, candidate.text)
                } else if (requestSnapshot.fullDraft.isNotEmpty()) {
                    AiCommitOperation.Replace(0, requestSnapshot.fullDraft.length, candidate.text)
                } else {
                    AiCommitOperation.Insert(requestSnapshot.selectionStart, candidate.text)
                }
            }
            AiCandidateAction.REWRITE -> {
                if (hadSelection) {
                    AiCommitOperation.Replace(requestSnapshot.selectionStart, requestSnapshot.selectionEnd, candidate.text)
                } else {
                    AiCommitOperation.Replace(0, requestSnapshot.fullDraft.length, candidate.text)
                }
            }
            AiCandidateAction.INSPIRE -> {
                if (hadSelection) {
                    AiCommitOperation.Replace(requestSnapshot.selectionStart, requestSnapshot.selectionEnd, candidate.text)
                } else {
                    AiCommitOperation.Insert(requestSnapshot.selectionStart, candidate.text)
                }
            }
        }
    }
}
