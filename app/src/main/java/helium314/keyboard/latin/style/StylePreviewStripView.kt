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
import android.widget.HorizontalScrollView
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
import helium314.keyboard.latin.utils.isBrightColor

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
    private val scrollView: HorizontalScrollView
    private val chipsContainer: LinearLayout
    private var onApplyListener: ((String) -> Unit)? = null
    private var onDismissListener: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.style_preview_strip_view, this, true)

        closeButton = findViewById(R.id.style_strip_close)
        scrollView = findViewById(R.id.style_strip_scroll)
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

        scrollView.scrollTo(0, 0)
        chipsContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val chipHeight = (30 * density).toInt()
        val cornerRadius = chipHeight / 2f
        val marginH = (4 * density).toInt()
        val padH = (14 * density).toInt()
        val colors = Settings.getValues().mColors

        val isDefaultWord = targetText.isBlank()
        val plainTargetText = UnicodeStyleEngine.toPlainText(targetText)
        val previewWord = if (isDefaultWord) "Style" else plainTargetText.take(18)
        val isLiveActive = UnicodeStyleEngine.isLiveStyleActive()

        // 1. Normal / Plain text chip (always available to revert text or disable live style)
        val isNormalActive = !isLiveActive
        val normalChip = TextView(context).apply {
            text = if (isNormalActive) "✓ ${context.getString(R.string.style_plain)}" else context.getString(R.string.style_plain)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, if (isNormalActive) Typeface.BOLD else Typeface.NORMAL)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(padH, 0, padH, 0)

            if (isNormalActive) {
                val accentBg = colors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND)
                val opaqueBg = if (Color.alpha(accentBg) < 255) ColorUtils.compositeColors(accentBg, Color.BLACK) else accentBg
                val isLightBg = isBrightColor(accentBg) ||
                    ColorUtils.calculateContrast(Color.DKGRAY, opaqueBg) > ColorUtils.calculateContrast(Color.WHITE, opaqueBg)
                val actionIconColor = if (isLightBg) Color.DKGRAY else colors.get(ColorType.ACTION_KEY_ICON)
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
                if (UnicodeStyleEngine.isLiveStyleActive()) {
                    UnicodeStyleEngine.clearActiveLiveStyle()
                    KeyboardSwitcher.getInstance().suggestionStripView?.updateToolbarButtonsActivatedState()
                    Toast.makeText(context, context.getString(R.string.style_active_none), Toast.LENGTH_SHORT).show()
                }
                if (!isDefaultWord) {
                    onApplyListener?.invoke(plainTargetText)
                }
                onDismissListener?.invoke()
            }
        }
        val lpNormal = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, chipHeight).apply {
            marginStart = marginH
            marginEnd = marginH
            gravity = Gravity.CENTER_VERTICAL
        }
        chipsContainer.addView(normalChip, lpNormal)

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
                    val opaqueBg = if (Color.alpha(accentBg) < 255) ColorUtils.compositeColors(accentBg, Color.BLACK) else accentBg
                    val isLightBg = isBrightColor(accentBg) ||
                        ColorUtils.calculateContrast(Color.DKGRAY, opaqueBg) > ColorUtils.calculateContrast(Color.WHITE, opaqueBg)
                    val actionIconColor = if (isLightBg) Color.DKGRAY else colors.get(ColorType.ACTION_KEY_ICON)
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
                    if (isCurrentLive) {
                        // Tapping the active live style turns it OFF
                        toggleLiveStyle(style)
                    } else {
                        // Activate style for all subsequent typing
                        if (style.supportsLiveTyping) {
                            toggleLiveStyle(style)
                        } else {
                            Toast.makeText(context, "${style.name} can only convert typed text", Toast.LENGTH_SHORT).show()
                        }
                        if (!isDefaultWord) {
                            val fullTransformed = UnicodeStyleEngine.transformText(plainTargetText, style.id)
                            if (fullTransformed.isNotEmpty()) {
                                onApplyListener?.invoke(fullTransformed)
                            }
                        }
                    }
                    onDismissListener?.invoke()
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
