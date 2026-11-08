// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.style

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.dpToPx

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

        // Dynamically compute strip height and button dimensions
        val stripHeight = ResourceUtils.getSuggestionsStripHeight(resources)
        val defaultStripHeight = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height).toFloat()
        val defaultEdgeWidth = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width)
        val ratio = if (defaultStripHeight > 0f) defaultEdgeWidth.toFloat() / defaultStripHeight else 0.9f
        val keyDimension = (stripHeight * ratio).toInt()
        val effectiveScale = if (defaultStripHeight > 0f) stripHeight.toFloat() / defaultStripHeight else 1.0f
        val expandPadding = (9 * effectiveScale).toInt().dpToPx(resources).coerceAtLeast(2)

        val lp = closeButton.layoutParams as? LayoutParams ?: LayoutParams(keyDimension, keyDimension)
        lp.width = keyDimension
        lp.height = keyDimension
        lp.gravity = Gravity.CENTER_VERTICAL
        closeButton.layoutParams = lp

        closeButton.setPadding(expandPadding, expandPadding, expandPadding, expandPadding)
        closeButton.scaleType = ImageView.ScaleType.CENTER_INSIDE

        // Themed close icon matching the active keyboard theme (e.g. ic_close_rounded on rounded themes)
        val closeDrawable = KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.CLOSE_HISTORY.name, context)
            ?: ContextCompat.getDrawable(context, R.drawable.ic_close)
        closeButton.setImageDrawable(closeDrawable)
        closeButton.setBackgroundResource(R.drawable.toolbar_key_background)
        colors.setColor(closeButton, ColorType.TOOL_BAR_KEY)
        colors.setColor(closeButton.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
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
        val chipHeight = (30 * density).toInt()
        val cornerRadius = chipHeight / 2f
        val marginH = (4 * density).toInt()
        val padH = (14 * density).toInt()
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
                setTextColor(colors.get(ColorType.KEY_TEXT))
                background = createChipDrawable(
                    bgColor = colors.get(ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND),
                    cornerRadiusPx = cornerRadius,
                    rippleColor = colors.get(ColorType.KEY_TEXT),
                    strokeColor = ColorUtils.setAlphaComponent(colors.get(ColorType.KEY_TEXT), 70),
                    strokeWidthPx = (1 * density).toInt().coerceAtLeast(1)
                )
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
                text = if (isCurrentLive) "✓ $transformedPreview" else transformedPreview
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(padH, 0, padH, 0)

                if (isCurrentLive) {
                    setTypeface(null, Typeface.BOLD)
                    // High-contrast live indicator: Accent background with contrasting actionKeyIcon text
                    val accentBg = colors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND)
                    val actionIconColor = colors.get(ColorType.ACTION_KEY_ICON)
                    setTextColor(actionIconColor)
                    background = createChipDrawable(
                        bgColor = accentBg,
                        cornerRadiusPx = cornerRadius,
                        rippleColor = actionIconColor
                    )
                } else {
                    setTypeface(null, Typeface.NORMAL)
                    setTextColor(colors.get(ColorType.KEY_TEXT))
                    background = createChipDrawable(
                        bgColor = colors.get(ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND),
                        cornerRadiusPx = cornerRadius,
                        rippleColor = colors.get(ColorType.KEY_TEXT)
                    )
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
        KeyboardSwitcher.getInstance().suggestionStripView?.updateToolbarButtonsActivatedState()
    }

    private fun createChipDrawable(
        bgColor: Int,
        cornerRadiusPx: Float,
        rippleColor: Int,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidthPx: Int = 0
    ): Drawable {
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(bgColor)
            if (strokeWidthPx > 0 && strokeColor != Color.TRANSPARENT) {
                setStroke(strokeWidthPx, strokeColor)
            }
        }
        val mask = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(Color.WHITE)
        }
        val rippleAlpha = ColorUtils.setAlphaComponent(rippleColor, 40)
        return RippleDrawable(ColorStateList.valueOf(rippleAlpha), shape, mask)
    }
}
