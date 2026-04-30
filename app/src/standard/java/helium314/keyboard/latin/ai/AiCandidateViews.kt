/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.ai

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

object AiCandidateViews {
    private const val PREFS_NAME = "ai_candidate_prefs"
    private const val KEY_WECHAT_CLOUD_CONSENT = "wechat_cloud_consent"

    fun hasWechatCloudConsent(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WECHAT_CLOUD_CONSENT, false)

    fun setWechatCloudConsent(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_WECHAT_CLOUD_CONSENT, true) }
    }

    fun loading(context: Context): View = messageRow(context, context.getString(R.string.ai_candidates_loading))

    fun error(context: Context, message: String): View = messageRow(context, message)

    fun permission(context: Context): View {
        val row = actionRow(context, context.getString(R.string.ai_candidates_accessibility_explainer))
        row.addView(button(context, context.getString(R.string.ai_candidates_open_accessibility)) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        return row
    }

    fun consent(context: Context, onConsent: () -> Unit): View {
        val row = actionRow(context, context.getString(R.string.ai_candidates_wechat_consent))
        row.addView(button(context, context.getString(R.string.ai_candidates_agree_generate)) {
            setWechatCloudConsent(context)
            onConsent()
        })
        return row
    }

    fun candidates(
        context: Context,
        candidates: List<AiCandidate>,
        onPick: (AiCandidate) -> Unit
    ): View {
        val scroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4.dp(context), 4.dp(context), 4.dp(context), 4.dp(context))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        candidates.take(3).forEach { candidate ->
            row.addView(candidateCard(context, candidate) { onPick(candidate) })
        }
        scroller.addView(row)
        return scroller
    }

    private fun messageRow(context: Context, message: String): View =
        actionRow(context, message)

    private fun actionRow(context: Context, message: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8.dp(context), 4.dp(context), 8.dp(context), 4.dp(context))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            addView(TextView(context).apply {
                text = message
                setSingleLine(false)
                maxLines = 2
                textSize = 13f
                setTextColor(Settings.getValues().mColors.get(ColorType.SUGGESTED_WORD))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }

    private fun candidateCard(context: Context, candidate: AiCandidate, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = candidate.text
            textSize = 14f
            maxLines = 2
            setTextColor(Settings.getValues().mColors.get(ColorType.SUGGESTED_WORD))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(context), 4.dp(context), 12.dp(context), 4.dp(context))
            background = roundedStroke(context)
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                172.dp(context),
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                marginEnd = 8.dp(context)
            }
        }

    private fun button(context: Context, label: String, onClick: () -> Unit): Button =
        Button(context).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

    private fun roundedStroke(context: Context): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = 8f
            setColor(Color.TRANSPARENT)
            setStroke(1.dp(context), Settings.getValues().mColors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND))
        }

    private fun Int.dp(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()
}
