// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.calculator.CalculatorHistoryManager
import helium314.keyboard.latin.calculator.MathEvaluator
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.databinding.InlineSuggestionBinding
import helium314.keyboard.latin.utils.ToolbarKey

class MathSuggestionManager(private val latinIME: LatinIME) {

    private var mathSuggestionView: View? = null
    private var cachedBinding: InlineSuggestionBinding? = null
    private var lastDismissedExpression: String? = null
    private val historyManager by lazy { CalculatorHistoryManager.getInstance(latinIME) }

    fun getMathSuggestionView(parent: ViewGroup?): View? {
        mathSuggestionView = null
        if (parent == null) return null
        if (!latinIME.mSettings.current.mInlineMathCalculation) return null

        val connection = latinIME.mInputLogic?.connection ?: return null
        val textBefore = connection.getTextBeforeCursor(60, 0)?.toString() ?: return null
        if (!textBefore.contains('=')) return null

        val match = MathEvaluator.evaluateInline(textBefore, historyManager.getLastAnswer()?.let { runCatching { java.math.BigDecimal(it) }.getOrNull() }) ?: return null
        if (match.expression == lastDismissedExpression) return null

        val binding = cachedBinding?.also { b ->
            (b.root.parent as? ViewGroup)?.removeView(b.root)
            b.root.isGone = false
        } ?: InlineSuggestionBinding.inflate(LayoutInflater.from(latinIME), parent, false).also {
            cachedBinding = it
        }
        val textView = binding.inlineSuggestionText
        latinIME.mSettings.getCustomTypeface()?.let { textView.typeface = it }
        
        textView.text = match.resultFormatted
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)

        textView.setOnClickListener {
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS
            )
            // Replace the typed expression including '=' with the evaluated result
            val deleteLen = match.fullMatchedText.length
            connection.deleteSurroundingText(deleteLen, 0)
            latinIME.onTextInput(match.resultFormatted)
            historyManager.addEntry(match.expression, match.resultFormatted)
            binding.root.isGone = true
            lastDismissedExpression = match.expression
        }

        val closeButton = binding.inlineSuggestionClose
        closeButton.setImageDrawable(latinIME.mKeyboardSwitcher.keyboard?.mIconsSet?.getIconDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase()))
        closeButton.setOnClickListener {
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
            binding.root.isGone = true
            lastDismissedExpression = match.expression
            removeMathSuggestion()
        }

        val colors = latinIME.mSettings.current.mColors
        textView.setTextColor(colors.get(ColorType.KEY_TEXT))
        colors.setColor(closeButton, ColorType.REMOVE_SUGGESTION_ICON)
        colors.setBackground(binding.root, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)

        mathSuggestionView = binding.root
        return mathSuggestionView
    }

    fun removeMathSuggestion() {
        mathSuggestionView?.isGone = true
        mathSuggestionView = null
        latinIME.removeExternalSuggestions()
    }
}
