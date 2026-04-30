/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.ai

import android.view.inputmethod.EditorInfo
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputConnection
import helium314.keyboard.latin.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

object StandardAiCandidateFeature : AiCandidateFeature {
    private const val WECHAT_PACKAGE = "com.tencent.mm"

    private val requestIds = AtomicLong(1)
    private val requestState = AiRequestState()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentJob: Job? = null

    override fun requestCandidates(
        latinIME: LatinIME,
        connection: RichInputConnection,
        editorInfo: EditorInfo?,
        committer: CandidateCommitter
    ) {
        editorInfo ?: return
        cancelCurrentRequest(AiCancelReason.NEW_REQUEST)

        val privacyDecision = privacyDecision(editorInfo)
        if (!privacyDecision.allowed) {
            KeyboardSwitcher.getInstance().showToast(latinIME.getString(R.string.ai_candidates_privacy_blocked), true)
            return
        }

        val snapshot = AiSnapshotBuilder.from(editorInfo, connection)
        val isWechat = snapshot.packageName == WECHAT_PACKAGE
        if (isWechat && !WechatAccessibilityService.isEnabled(latinIME)) {
            latinIME.setAiCandidateSuggestionView(AiCandidateViews.permission(latinIME))
            return
        }

        val wechatMessages = if (isWechat) WechatAccessibilityService.readVisibleMessages() else emptyList()
        if (isWechat && wechatMessages.isNotEmpty() && !AiCandidateViews.hasWechatCloudConsent(latinIME)) {
            latinIME.setAiCandidateSuggestionView(AiCandidateViews.consent(latinIME) {
                requestCandidates(latinIME, connection, editorInfo, committer)
            })
            return
        }

        val action = chooseAction(snapshot, isWechat, wechatMessages)
        if (snapshot.fullDraft.isBlank() && wechatMessages.isEmpty()) {
            KeyboardSwitcher.getInstance().showToast(latinIME.getString(R.string.ai_candidates_no_context), true)
            return
        }

        val context = AiRequestContext(
            requestId = requestIds.getAndIncrement(),
            action = action,
            editorSnapshot = snapshot,
            incognito = Settings.getValues().mIncognitoModeEnabled,
            noPersonalizedLearning = Settings.getValues().mInputAttributes.mNoLearning,
            localeTag = Locale.getDefault().toLanguageTag(),
            wechatMessages = wechatMessages
        )
        requestState.start(context)
        latinIME.setAiCandidateSuggestionView(AiCandidateViews.loading(latinIME))

        currentJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                AiChatClient(latinIME.applicationContext).generateCandidates(context)
            }

            val currentEditorInfo = latinIME.currentInputEditorInfo
            val currentSnapshot = if (currentEditorInfo != null) {
                AiSnapshotBuilder.from(currentEditorInfo, connection)
            } else {
                null
            }
            if (currentSnapshot == null || !requestState.shouldAcceptResult(context.requestId, currentSnapshot)) {
                return@launch
            }

            result.fold(
                onSuccess = { candidates ->
                    latinIME.setAiCandidateSuggestionView(
                        AiCandidateViews.candidates(latinIME, candidates) { candidate ->
                            if (!committer.commitCandidate(context, candidate)) {
                                KeyboardSwitcher.getInstance().showToast(
                                    latinIME.getString(R.string.ai_candidates_commit_stale),
                                    true
                                )
                            }
                            cancelCurrentRequest(AiCancelReason.USER_CLOSED)
                            latinIME.removeExternalSuggestions()
                        }
                    )
                },
                onFailure = { error ->
                    val message = error.message ?: latinIME.getString(R.string.ai_candidates_error)
                    KeyboardSwitcher.getInstance().showToast(message, false)
                    latinIME.setAiCandidateSuggestionView(AiCandidateViews.error(latinIME, message))
                }
            )
        }
    }

    override fun cancelCurrentRequest(reason: AiCancelReason) {
        currentJob?.cancel()
        currentJob = null
        requestState.cancel(reason)
    }

    override fun onStartInputView(latinIME: LatinIME, editorInfo: EditorInfo?, connection: RichInputConnection) {
        cancelCurrentRequest(AiCancelReason.START_INPUT_VIEW)
    }

    override fun onFinishInputView() {
        cancelCurrentRequest(AiCancelReason.FINISH_INPUT_VIEW)
    }

    override fun onHideWindow() {
        cancelCurrentRequest(AiCancelReason.HIDE_WINDOW)
    }

    override fun shouldShowTransientToolbarKey(
        latinIME: LatinIME,
        editorInfo: EditorInfo?,
        connection: RichInputConnection
    ): Boolean {
        editorInfo ?: return false
        val decision = privacyDecision(editorInfo)
        if (!decision.allowed) return false
        val snapshot = AiSnapshotBuilder.from(editorInfo, connection)
        return snapshot.packageName == WECHAT_PACKAGE || snapshot.hasSelection || snapshot.fullDraft.isNotBlank()
    }

    private fun privacyDecision(editorInfo: EditorInfo) = AiPrivacyGuard.evaluate(
        inputType = Settings.getValues().mInputAttributes.mInputType,
        imeOptions = editorInfo.imeOptions,
        incognito = Settings.getValues().mIncognitoModeEnabled,
        noPersonalizedLearning = Settings.getValues().mInputAttributes.mNoLearning,
        isGeneralTextInput = Settings.getValues().mInputAttributes.mIsGeneralTextInput
    )

    private fun chooseAction(
        snapshot: AiEditorSnapshot,
        isWechat: Boolean,
        wechatMessages: List<String>
    ): AiCandidateAction =
        when {
            isWechat && wechatMessages.isNotEmpty() -> AiCandidateAction.REPLY
            snapshot.hasSelection -> AiCandidateAction.REWRITE
            else -> AiCandidateAction.INSPIRE
        }
}
