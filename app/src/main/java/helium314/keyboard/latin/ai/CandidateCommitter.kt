/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.ai

import android.view.inputmethod.EditorInfo
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.RichInputConnection
import helium314.keyboard.latin.utils.Log

interface CandidateCommitter {
    fun commitCandidate(requestContext: AiRequestContext, candidate: AiCandidate): Boolean
}

class RichInputCandidateCommitter(
    private val latinIME: LatinIME,
    private val connection: RichInputConnection
) : CandidateCommitter {
    override fun commitCandidate(requestContext: AiRequestContext, candidate: AiCandidate): Boolean {
        val editorInfo = latinIME.currentInputEditorInfo ?: return false
        val currentSnapshot = AiSnapshotBuilder.from(editorInfo, connection)
        val operation = AiCommitPolicy.createOperation(requestContext, currentSnapshot, candidate)
            ?: return false

        return try {
            connection.beginBatchEdit()
            connection.finishComposingText()
            when (operation) {
                is AiCommitOperation.Replace -> {
                    if (!connection.setSelection(operation.start, operation.end)) return false
                    connection.commitText(operation.text, 1)
                }
                is AiCommitOperation.Insert -> {
                    if (!connection.setSelection(operation.position, operation.position)) return false
                    connection.commitText(operation.text, 1)
                }
            }
            true
        } catch (e: Exception) {
            Log.e("RichInputCandidateCommitter", "Failed to commit AI candidate", e)
            false
        } finally {
            connection.endBatchEdit()
        }
    }
}

object AiSnapshotBuilder {
    private const val MAX_CONTEXT_CHARS = 4000

    fun from(editorInfo: EditorInfo, connection: RichInputConnection): AiEditorSnapshot {
        val selectionStart = connection.expectedSelectionStart
        val selectionEnd = connection.expectedSelectionEnd
        val selected = if (connection.hasSelection()) {
            connection.getSelectedText(0)?.toString().orEmpty()
        } else {
            ""
        }
        return AiEditorSnapshot(
            packageName = editorInfo.packageName,
            inputType = editorInfo.inputType,
            imeOptions = editorInfo.imeOptions,
            fieldId = editorInfo.fieldId,
            privateImeOptions = editorInfo.privateImeOptions,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            beforeCursor = safeText { connection.getTextBeforeCursor(MAX_CONTEXT_CHARS, 0)?.toString() },
            selectedText = selected,
            afterCursor = safeText { connection.getTextAfterCursor(MAX_CONTEXT_CHARS, 0)?.toString() }
        )
    }

    private fun safeText(block: () -> String?): String = try {
        block().orEmpty()
    } catch (_: Exception) {
        ""
    }
}
