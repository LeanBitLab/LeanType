// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.style

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/**
 * Interactive strip displaying preview candidate chips for Unicode styles.
 * Shown in place of the suggestion strip when FONT_STYLE is activated.
 */
class StylePreviewStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val closeButton: ImageButton
    private val chipsContainer: LinearLayout
    private var onApplyListener: ((String) -> Unit)? = null
    private var onDismissListener: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.style_preview_strip_view, this, true)

        closeButton = findViewById(R.id.style_strip_close)
        chipsContainer = findViewById(R.id.style_strip_chips_container)

        closeButton.setOnClickListener {
            onDismissListener?.invoke()
        }
        applyTheme()
    }

    fun applyTheme() {
        val colors = Settings.getValues().mColors
        colors.setBackground(this, ColorType.STRIP_BACKGROUND)
        colors.setColor(closeButton, ColorType.TOOL_BAR_KEY)
    }

    /**
     * Populates style chips previewing the target text or sample word.
     */
    fun setTargetText(
        targetText: String,
        onApply: (String) -> Unit,
        onDismiss: () -> Unit
    ) {
        this.onApplyListener = onApply
        this.onDismissListener = onDismiss

        chipsContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val chipHeight = (32 * density).toInt()
        val marginH = (4 * density).toInt()
        val padH = (12 * density).toInt()
        val colors = Settings.getValues().mColors

        val isDefaultWord = targetText.isBlank()
        val previewWord = if (isDefaultWord) "Style" else targetText.take(18)

        // 1. Revert to plain text chip (if target text is not blank)
        if (!isDefaultWord) {
            val plainText = UnicodeStyleEngine.toPlainText(targetText)
            val revertChip = TextView(context).apply {
                text = context.getString(R.string.style_revert_plain)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(padH, 0, padH, 0)
                setBackgroundResource(R.drawable.toolbar_key_background)
                setTextColor(colors.get(ColorType.KEY_TEXT))
                colors.setColor(background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
                setOnClickListener {
                    onApplyListener?.invoke(plainText)
                    onDismissListener?.invoke()
                }
            }
            val lp = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, chipHeight).apply {
                marginStart = marginH
                marginEnd = marginH
                gravity = Gravity.CENTER_VERTICAL
            }
            chipsContainer.addView(revertChip, lp)
        }

        // 2. Style preview chips
        val allStyles = UnicodeStyleEngine.getAllStyles()
        for (style in allStyles) {
            val transformedPreview = UnicodeStyleEngine.transformText(previewWord, style.id)
            val isCurrentLive = UnicodeStyleEngine.activeLiveStyleId == style.id

            val chip = TextView(context).apply {
                text = transformedPreview
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(padH, 0, padH, 0)
                setBackgroundResource(R.drawable.toolbar_key_background)
                if (isCurrentLive) {
                    setTextColor(colors.get(ColorType.EMOJI_CATEGORY_SELECTED))
                    colors.setColor(background, ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND)
                } else {
                    setTextColor(colors.get(ColorType.KEY_TEXT))
                    colors.setColor(background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
                }

                setOnClickListener {
                    val fullTransformed = if (isDefaultWord) {
                        // User clicked a style without text: toggle live style
                        if (style.supportsLiveTyping) {
                            toggleLiveStyle(style)
                        } else {
                            Toast.makeText(context, "${style.name} can only convert typed text", Toast.LENGTH_SHORT).show()
                        }
                        ""
                    } else {
                        UnicodeStyleEngine.transformText(targetText, style.id)
                    }
                    if (fullTransformed.isNotEmpty()) {
                        onApplyListener?.invoke(fullTransformed)
                    }
                    onDismissListener?.invoke()
                }

                setOnLongClickListener {
                    if (style.supportsLiveTyping) {
                        toggleLiveStyle(style)
                        onDismissListener?.invoke()
                        true
                    } else {
                        false
                    }
                }
            }

            val lp = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, chipHeight).apply {
                marginStart = marginH
                marginEnd = marginH
                gravity = Gravity.CENTER_VERTICAL
            }
            chipsContainer.addView(chip, lp)
        }
    }

    private fun toggleLiveStyle(style: StyleDefinition) {
        if (UnicodeStyleEngine.activeLiveStyleId == style.id) {
            UnicodeStyleEngine.clearActiveLiveStyle()
            Toast.makeText(context, context.getString(R.string.style_active_none), Toast.LENGTH_SHORT).show()
        } else {
            UnicodeStyleEngine.activeLiveStyleId = style.id
            Toast.makeText(context, context.getString(R.string.style_active_indicator, style.name), Toast.LENGTH_SHORT).show()
        }
        helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().suggestionStripView?.updateToolbarButtonsActivatedState()
    }
}
