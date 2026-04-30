/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.ai

import org.json.JSONObject
import java.util.Locale

object AiCandidateParser {
    private const val MAX_CANDIDATES = 3
    private const val MAX_TEXT_LENGTH = 240

    fun parse(raw: String, fallbackAction: AiCandidateAction): Result<List<AiCandidate>> = runCatching {
        val candidates = parseJson(raw, fallbackAction) ?: parseLines(raw, fallbackAction)
        val cleaned = candidates
            .mapNotNull { candidate ->
                val text = candidate.text.trim().take(MAX_TEXT_LENGTH)
                if (text.isBlank()) null else candidate.copy(text = text)
            }
            .take(MAX_CANDIDATES)
        require(cleaned.isNotEmpty()) { "No AI candidates returned" }
        cleaned
    }

    private fun parseJson(raw: String, fallbackAction: AiCandidateAction): List<AiCandidate>? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) return null
        val root = JSONObject(trimmed)
        val array = root.optJSONArray("candidates") ?: return null
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val text = item.optString("text", "")
                val action = parseAction(item.optString("action", ""), fallbackAction)
                add(AiCandidate(action, text))
            }
        }
    }

    private fun parseLines(raw: String, fallbackAction: AiCandidateAction): List<AiCandidate> =
        raw.lineSequence()
            .map { it.trim().trimStart('-', '*').trim() }
            .filter { it.isNotBlank() }
            .map { AiCandidate(fallbackAction, it) }
            .toList()

    private fun parseAction(raw: String, fallbackAction: AiCandidateAction): AiCandidateAction =
        try {
            AiCandidateAction.valueOf(raw.trim().uppercase(Locale.US))
        } catch (_: IllegalArgumentException) {
            fallbackAction
        }
}
