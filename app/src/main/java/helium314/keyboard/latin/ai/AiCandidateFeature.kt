/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.ai

import android.view.inputmethod.EditorInfo
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.RichInputConnection

interface AiCandidateFeature {
    fun requestCandidates(
        latinIME: LatinIME,
        connection: RichInputConnection,
        editorInfo: EditorInfo?,
        committer: CandidateCommitter
    )

    fun cancelCurrentRequest(reason: AiCancelReason)

    fun onStartInputView(latinIME: LatinIME, editorInfo: EditorInfo?, connection: RichInputConnection)

    fun onFinishInputView()

    fun onHideWindow()

    fun shouldShowTransientToolbarKey(
        latinIME: LatinIME,
        editorInfo: EditorInfo?,
        connection: RichInputConnection
    ): Boolean
}
object NoOpAiCandidateFeature : AiCandidateFeature {
    override fun requestCandidates(
        latinIME: LatinIME,
        connection: RichInputConnection,
        editorInfo: EditorInfo?,
        committer: CandidateCommitter
    ) = Unit

    override fun cancelCurrentRequest(reason: AiCancelReason) = Unit

    override fun onStartInputView(latinIME: LatinIME, editorInfo: EditorInfo?, connection: RichInputConnection) = Unit

    override fun onFinishInputView() = Unit

    override fun onHideWindow() = Unit

    override fun shouldShowTransientToolbarKey(
        latinIME: LatinIME,
        editorInfo: EditorInfo?,
        connection: RichInputConnection
    ): Boolean = false
}
