// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.common

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.View
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import helium314.keyboard.keyboard.KeyboardTheme.Companion.STYLE_HOLO
import helium314.keyboard.keyboard.KeyboardTheme.Companion.STYLE_MATERIAL
import helium314.keyboard.latin.common.ColorType.*
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.adjustLuminosityAndKeepAlpha
import helium314.keyboard.latin.utils.brighten
import helium314.keyboard.latin.utils.brightenOrDarken
import helium314.keyboard.latin.utils.darken
import helium314.keyboard.latin.utils.isBrightColor
import helium314.keyboard.latin.utils.isDarkColor
import java.util.EnumMap
import androidx.core.graphics.drawable.toDrawable

interface Colors {
    // these theme parameters should no be in here, but are still used
    /** used in KeyboardView for label placement */
    val themeStyle: String
    /** used in parser to decide background of ZWNJ key */
    val hasKeyBorders: Boolean

    /** use to check whether colors have changed, for colors (in)directly derived from context,
     *  e.g. night mode or potentially changing system colors */
    fun haveColorsChanged(context: Context): Boolean = false

    /** get the colorInt */
    @ColorInt fun get(color: ColorType): Int

    /** get the pressed state colorInt */
    @ColorInt fun getPressedColor(color: ColorType): Int

    /** apply a color to the [drawable], may be through color filter or tint (with or without state list) */
    fun setColor(drawable: Drawable?, color: ColorType)

    /** set a foreground color to the [view] */
    fun setColor(view: ImageView, color: ColorType)

    /** set a background to the [view], may replace or adjust existing background */
    fun setBackground(view: View, color: ColorType)

    /** returns a colored drawable selected from [attr], which must contain using R.styleable.KeyboardView_* */
    fun selectAndColorDrawable(attr: TypedArray, color: ColorType): Drawable {
        val drawable = when (color) {
            KEY_BACKGROUND, MORE_SUGGESTIONS_WORD_BACKGROUND, ACTION_KEY_POPUP_KEYS_BACKGROUND, POPUP_KEYS_BACKGROUND ->
                attr.getDrawable(R.styleable.KeyboardView_keyBackground)
            FUNCTIONAL_KEY_BACKGROUND -> attr.getDrawable(R.styleable.KeyboardView_functionalKeyBackground)
            SPACE_BAR_BACKGROUND -> attr.getDrawable(R.styleable.KeyboardView_spacebarBackground)
            ACTION_KEY_BACKGROUND -> {
                if (themeStyle == STYLE_HOLO && hasKeyBorders) // no borders has a very small pressed drawable otherwise
                    attr.getDrawable(R.styleable.KeyboardView_functionalKeyBackground)
                else
                    attr.getDrawable(R.styleable.KeyboardView_keyBackground)
            }
            else -> null // keyBackground
        }?.mutate() ?: attr.getDrawable(R.styleable.KeyboardView_keyBackground)?.mutate() ?: error("keyBackground always exists in KeyboardView style")

        setColor(drawable, color)
        return drawable
    }

    fun applyKeyBorderRadius(drawable: Drawable?, radiusPx: Float) {
        val current = drawable?.current ?: drawable ?: return
        if (appliedRadii[current] == radiusPx) return
        appliedRadii[current] = radiusPx
        applyRadiusRecursive(current, radiusPx)
    }

    private fun applyRadiusRecursive(drawable: Drawable?, radiusPx: Float) {
        when (drawable) {
            is GradientDrawable -> drawable.cornerRadius = radiusPx
            is LayerDrawable -> {
                for (i in 0 until drawable.numberOfLayers) {
                    applyRadiusRecursive(drawable.getDrawable(i), radiusPx)
                }
            }
            is InsetDrawable -> applyRadiusRecursive(drawable.drawable, radiusPx)
        }
    }
}

private val appliedRadii = java.util.WeakHashMap<Drawable, Float>()

@RequiresApi(Build.VERSION_CODES.S)
class DynamicColors(context: Context, override val themeStyle: String, override val hasKeyBorders: Boolean, private var keyboardBackground: Drawable? = null) : Colors {

    companion object {
        fun getFreshContext(context: Context): Context =
            try {
                context.createConfigurationContext(context.resources.configuration)
            } catch (_: Exception) {
                context
            }
    }

    private val freshContext = getFreshContext(context)
    private val isNight = freshContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private val accent = getAccent(freshContext)
    private val gesture = getGesture(freshContext)
    private val background = getBackground(freshContext)
    private val keyBackground = getKeyBackground(freshContext)
    private val functionalKey = getFunctionalKey(freshContext)
    private val spaceBar = getKeyBackground(freshContext)
    private val keyText = getKeyText(freshContext)
    private val keyHintText = getKeyHintText(freshContext)
    private val spaceBarText = getSpaceBarText(freshContext)

    private fun isNight(context: Context) =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun getAccent(context: Context) = if (isNight(context)) ContextCompat.getColor(context, android.R.color.system_accent1_100)
        else ContextCompat.getColor(context, android.R.color.system_accent1_600)
    private fun getGesture(context: Context) = if (isNight(context)) accent
        else ContextCompat.getColor(context, android.R.color.system_accent1_600)
    private fun getBackground(context: Context) = if (isNight(context)) ContextCompat.getColor(context, android.R.color.system_neutral1_900)
        else ContextCompat.getColor(context, android.R.color.system_neutral1_100)
    private fun getKeyBackground(context: Context) = if (isNight(context)) ContextCompat.getColor(context, android.R.color.system_neutral1_800)
        else  ContextCompat.getColor(context, android.R.color.system_neutral1_0)
    private fun getFunctionalKey(context: Context) = if (isNight(context)) ContextCompat.getColor(context, android.R.color.system_accent2_300)
        else ContextCompat.getColor(context, android.R.color.system_accent2_200)
    private fun getKeyText(context: Context): Int {
        val keyBg = getKeyBackground(context)
        if (isNight(context)) {
            val color = ContextCompat.getColor(context, android.R.color.system_neutral1_50)
            return if (ColorUtils.calculateContrast(color, keyBg) < 4.5) Color.WHITE else color
        } else {
            val color = ContextCompat.getColor(context, android.R.color.system_accent3_900)
            return if (ColorUtils.calculateContrast(color, keyBg) < 4.5) ContextCompat.getColor(context, android.R.color.system_neutral1_900) else color
        }
    }
    private fun getKeyHintText(context: Context): Int {
        val keyBg = getKeyBackground(context)
        if (isNight(context)) {
            return getKeyText(context)
        } else {
            val color = ContextCompat.getColor(context, android.R.color.system_accent3_700)
            return if (ColorUtils.calculateContrast(color, keyBg) < 3.0) ContextCompat.getColor(context, android.R.color.system_neutral1_700) else color
        }
    }
    private fun getSpaceBarText(context: Context) = if (isNight(context)) ColorUtils.setAlphaComponent(ContextCompat.getColor(context, android.R.color.system_neutral1_50), 127)
        else ColorUtils.setAlphaComponent(getKeyText(context), 127)

    override fun haveColorsChanged(context: Context): Boolean {
        val fresh = getFreshContext(context)
        return accent != getAccent(fresh)
                || gesture != getGesture(fresh)
                || background != getBackground(fresh)
                || keyBackground != getKeyBackground(fresh)
                || functionalKey != getFunctionalKey(fresh)
                || keyText != getKeyText(fresh)
                || keyHintText != getKeyHintText(fresh)
                || spaceBarText != getSpaceBarText(fresh)
    }

    private val navBar: Int
    /** brightened or darkened variant of [background], to be used if exact background color would be
     *  bad contrast, e.g. popup keys popup or no border space bar */
    private val adjustedBackground: Int
    /** further brightened or darkened variant of [adjustedBackground] */
    private val doubleAdjustedBackground: Int
    /** brightened or darkened variant of [keyText] */
    private val adjustedKeyText: Int

    private val backgroundFilter: ColorFilter
    private val adjustedBackgroundFilter: ColorFilter
    private val keyTextFilter: ColorFilter
    private val accentColorFilter: ColorFilter
    /** color filter for the white action key icons in material theme, switches to gray if necessary for contrast */
    private val actionKeyIconColorFilter: ColorFilter?

    private val backgroundStateList: ColorStateList
    private val keyStateList: ColorStateList
    private val functionalKeyStateList: ColorStateList
    private val actionKeyStateList: ColorStateList
    private val spaceBarStateList: ColorStateList
    private val adjustedBackgroundStateList: ColorStateList
    private val stripBackgroundList: ColorStateList
    private val toolbarKeyStateList = activatedStateList(
        keyText,
        if (isBrightColor(keyText)) darken(darken(keyText))
        else brighten(brighten(keyText))
    )

    /** darkened variant of [accent] because the accent color is always light for dynamic colors */
    private val adjustedAccent: Int = darken(accent)
    /** further darkened variant of [adjustedAccent] */
    private val doubleAdjustedAccent: Int = darken(adjustedAccent)

    /** darkened variant of [functionalKey] used in day mode */
    private val adjustedFunctionalKey: Int = darken(functionalKey)
    /** further darkened variant of [adjustedFunctionalKey] */
    private val doubleAdjustedFunctionalKey: Int = darken(adjustedFunctionalKey)

    /** brightened variant of [keyBackground] used in night mode */
    private val adjustedKeyBackground: Int = brighten(keyBackground)
    /** further brightened variant of [adjustedKeyBackground] */
    private val doubleAdjustedKeyBackground: Int = brighten(adjustedKeyBackground)
    private var backgroundSetupDone = false

    init {
        accentColorFilter = if (!isNight) colorFilter(accent) else colorFilter(doubleAdjustedAccent)

        if (themeStyle == STYLE_HOLO && keyboardBackground == null) {
            val darkerBackground = adjustLuminosityAndKeepAlpha(background, -0.2f)
            navBar = darkerBackground
            keyboardBackground = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(background, darkerBackground))
            backgroundSetupDone = true
        } else {
            navBar = background
        }

        // todo (idea): make better use of the states?
        //  could also use / create StateListDrawables in colors (though that's a style than a color...)
        //  this would better allow choosing e.g. cornered/rounded drawables for poup keys or moreSuggestions
        backgroundFilter = colorFilter(background)
        adjustedKeyText = brightenOrDarken(keyText, true)

        if (isDarkColor(background)) {
            adjustedBackground = brighten(background)
            doubleAdjustedBackground = brighten(adjustedBackground)
        } else {
            adjustedBackground = darken(background)
            doubleAdjustedBackground = darken(adjustedBackground)
        }
        adjustedBackgroundStateList =
            if (themeStyle == STYLE_HOLO) {
                pressedStateList(accent, adjustedBackground)
            } else if (isNight) {
                if (hasKeyBorders) pressedStateList(doubleAdjustedAccent, keyBackground)
                else pressedStateList(adjustedAccent, adjustedKeyBackground)
            } else {
                pressedStateList(accent, Color.WHITE)
            }

        val stripBackground = Color.TRANSPARENT
        val pressedStripElementBackground = if (keyboardBackground == null) adjustedBackground
        else if (isDarkColor(background)) 0x22ffffff else 0x11000000
        stripBackgroundList = pressedStateList(pressedStripElementBackground, stripBackground)

        adjustedBackgroundFilter =
            if (themeStyle == STYLE_HOLO) colorFilter(adjustedBackground)
            else colorFilter(keyBackground)

        if (hasKeyBorders) {
            backgroundStateList =
                if (!isNight) pressedStateList(adjustedFunctionalKey, background)
                else pressedStateList(adjustedKeyBackground, background)

            keyStateList =
                if (!isNight) pressedStateList(adjustedBackground, keyBackground)
                else pressedStateList(adjustedKeyBackground, keyBackground)

            functionalKeyStateList =
                if (!isNight) pressedStateList(doubleAdjustedFunctionalKey, functionalKey)
                else pressedStateList(functionalKey, doubleAdjustedKeyBackground)

            actionKeyStateList =
                if (!isNight) pressedStateList(brightenOrDarken(accent, true), accent)
                else pressedStateList(doubleAdjustedAccent, accent)

            spaceBarStateList =
                if (themeStyle == STYLE_HOLO) pressedStateList(spaceBar, spaceBar)
                else keyStateList

        } else {
            // need to set color to background if key borders are disabled, or there will be ugly keys
            backgroundStateList =
                if (!isNight) pressedStateList(adjustedFunctionalKey, background)
                else pressedStateList(adjustedKeyBackground, background)

            keyStateList =
                if (!isNight) pressedStateList(adjustedFunctionalKey, Color.TRANSPARENT)
                else pressedStateList(functionalKey, Color.TRANSPARENT)

            functionalKeyStateList =
                if (themeStyle == STYLE_HOLO) pressedStateList(functionalKey, Color.TRANSPARENT)
                else keyStateList

            actionKeyStateList =
                if (themeStyle == STYLE_HOLO) pressedStateList(accent, Color.TRANSPARENT)
                else if (!isNight) pressedStateList(brightenOrDarken(accent, true), accent)
                else pressedStateList(doubleAdjustedAccent, accent)

            val borderlessSpaceBar = androidx.core.graphics.ColorUtils.blendARGB(background, keyBackground, 0.45f)
            spaceBarStateList =
                if (themeStyle == STYLE_HOLO) pressedStateList(spaceBar, spaceBar)
                else pressedStateList(brightenOrDarken(borderlessSpaceBar, true), borderlessSpaceBar)
        }
        keyTextFilter = colorFilter(keyText)

        actionKeyIconColorFilter = when {
            themeStyle == STYLE_HOLO -> keyTextFilter
            // the white icon may not have enough contrast, and can't be adjusted by the user
            // Use 0.65 as threshold (white(1.0) on light accent > 0.65 is bad)
            // Or easier: if accent is bright, use dark icon
            androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.5 -> colorFilter(Color.DKGRAY)
            else -> colorFilter(Color.WHITE)
        }
    }

    override fun get(color: ColorType): Int = when (color) {
        TOOL_BAR_KEY_ENABLED_BACKGROUND, EMOJI_CATEGORY_SELECTED, ACTION_KEY_BACKGROUND,
        CLIPBOARD_PIN, SHIFT_KEY_ICON -> accent
        AUTOFILL_BACKGROUND_CHIP, GESTURE_PREVIEW, POPUP_KEYS_BACKGROUND, MORE_SUGGESTIONS_BACKGROUND, KEY_PREVIEW_BACKGROUND -> adjustedBackground
        TOOL_BAR_EXPAND_KEY_BACKGROUND -> doubleAdjustedBackground
        GESTURE_TRAIL -> gesture
        KEY_TEXT, SUGGESTION_AUTO_CORRECT, REMOVE_SUGGESTION_ICON, EMOJI_KEY_TEXT, KEY_PREVIEW_TEXT,
            KEY_ICON, ONE_HANDED_MODE_BUTTON, EMOJI_CATEGORY, TOOL_BAR_KEY, FUNCTIONAL_KEY_TEXT,
            POPUP_KEY_TEXT, POPUP_KEY_ICON -> keyText
        KEY_HINT_TEXT -> keyHintText
        SPACE_BAR_TEXT -> spaceBarText
        FUNCTIONAL_KEY_BACKGROUND -> functionalKey
        SPACE_BAR_BACKGROUND -> spaceBar
        MORE_SUGGESTIONS_WORD_BACKGROUND, MAIN_BACKGROUND -> background
        KEY_BACKGROUND -> keyBackground
        ACTION_KEY_POPUP_KEYS_BACKGROUND -> if (themeStyle == STYLE_HOLO) adjustedBackground else accent
        STRIP_BACKGROUND -> background
        CLIPBOARD_SUGGESTION_BACKGROUND -> doubleAdjustedBackground
        NAVIGATION_BAR -> navBar
        MORE_SUGGESTIONS_HINT, SUGGESTED_WORD, SUGGESTION_TYPED_WORD, SUGGESTION_VALID_WORD -> adjustedKeyText
        TOOL_BAR_EXPAND_KEY -> Color.WHITE
        ACTION_KEY_ICON -> if (isBrightColor(accent)) Color.DKGRAY else Color.WHITE
        EDIT_MODE_DELETE_BACKGROUND -> androidx.core.graphics.ColorUtils.blendARGB(accent, functionalKey, 0.4f)
        EDIT_MODE_FUNC_BACKGROUND -> androidx.core.graphics.ColorUtils.blendARGB(accent, functionalKey, 0.2f)
        EDIT_MODE_ALPHA_BACKGROUND -> androidx.core.graphics.ColorUtils.blendARGB(accent, functionalKey, 0.6f)
        EDIT_MODE_NAV_BACKGROUND -> androidx.core.graphics.ColorUtils.blendARGB(keyBackground, functionalKey, 0.3f)
        EDIT_MODE_JUMP_BACKGROUND -> androidx.core.graphics.ColorUtils.blendARGB(keyBackground, functionalKey, 0.7f)
    }

    override fun setColor(drawable: Drawable?, color: ColorType) {
        if (drawable == null) return
        val colorStateList = when (color) {
            MORE_SUGGESTIONS_WORD_BACKGROUND -> backgroundStateList
            KEY_BACKGROUND -> keyStateList
            FUNCTIONAL_KEY_BACKGROUND -> functionalKeyStateList
            ACTION_KEY_BACKGROUND -> actionKeyStateList
            SPACE_BAR_BACKGROUND -> spaceBarStateList
            POPUP_KEYS_BACKGROUND -> adjustedBackgroundStateList
            STRIP_BACKGROUND -> stripBackgroundList
            ACTION_KEY_POPUP_KEYS_BACKGROUND -> if (themeStyle == STYLE_HOLO) adjustedBackgroundStateList
                else actionKeyStateList
            TOOL_BAR_KEY -> toolbarKeyStateList
            else -> null // use color filter
        }
        if (colorStateList == null) {
            DrawableCompat.setTintList(drawable, null)
            drawable.colorFilter = getColorFilter(color)
            return
        }
        DrawableCompat.setTintMode(drawable, PorterDuff.Mode.MULTIPLY)
        DrawableCompat.setTintList(drawable, colorStateList)
    }

    override fun setColor(view: ImageView, color: ColorType) {
        if (color == TOOL_BAR_KEY) {
            view.clearColorFilter()
            view.drawable?.let { setColor(it, color) }
            return
        }
        view.drawable?.let { DrawableCompat.setTintList(it, null) }
        view.colorFilter = getColorFilter(color)
    }

    private fun getColorFilter(color: ColorType): ColorFilter? = when (color) {
        EMOJI_CATEGORY_SELECTED, CLIPBOARD_PIN, SHIFT_KEY_ICON -> accentColorFilter
        REMOVE_SUGGESTION_ICON, EMOJI_CATEGORY, KEY_TEXT,
            KEY_ICON, ONE_HANDED_MODE_BUTTON, TOOL_BAR_KEY, TOOL_BAR_EXPAND_KEY -> keyTextFilter
        KEY_PREVIEW_BACKGROUND -> adjustedBackgroundFilter
        ACTION_KEY_ICON -> actionKeyIconColorFilter
        else -> colorFilter(get(color))
    }

    override fun setBackground(view: View, color: ColorType) {
        if (view.background == null)
            view.setBackgroundColor(Color.WHITE) // set white to make the color filters work
        when (color) {
            KEY_PREVIEW_BACKGROUND -> view.background.colorFilter = adjustedBackgroundFilter
            FUNCTIONAL_KEY_BACKGROUND, KEY_BACKGROUND, MORE_SUGGESTIONS_WORD_BACKGROUND, SPACE_BAR_BACKGROUND, STRIP_BACKGROUND -> setColor(view.background, color)
            ONE_HANDED_MODE_BUTTON -> setColor(view.background, if (keyboardBackground == null) MAIN_BACKGROUND else STRIP_BACKGROUND)
            MORE_SUGGESTIONS_BACKGROUND -> view.background.colorFilter = backgroundFilter
            POPUP_KEYS_BACKGROUND ->
                if (themeStyle != STYLE_HOLO)
                    setColor(view.background, POPUP_KEYS_BACKGROUND)
                else view.background.colorFilter = adjustedBackgroundFilter
            MAIN_BACKGROUND -> {
                keyboardBackground?.let { bg ->
                    view.background = bg
                } ?: run { view.background.colorFilter = backgroundFilter }
            }
            else -> view.background.colorFilter = backgroundFilter
        }
     }

    override fun getPressedColor(color: ColorType): Int {
        if (color == ColorType.ACTION_KEY_POPUP_KEYS_BACKGROUND) {
            return if (themeStyle == STYLE_HOLO) adjustedBackground else accent
        }
        return if (themeStyle == STYLE_HOLO) {
            accent
        } else if (isNight) {
            if (hasKeyBorders) doubleAdjustedAccent
            else adjustedAccent
        } else {
            accent
        }
    }
}

class DefaultColors (
    override val themeStyle: String,
    override val hasKeyBorders: Boolean,
    private val accent: Int,
    private val background: Int,
    private val keyBackground: Int,
    private val functionalKey: Int,
    private val spaceBar: Int,
    private val keyText: Int,
    private val keyHintText: Int,
    private val suggestionText: Int = keyText,
    private val spaceBarText: Int = keyHintText,
    private val gesture: Int = accent,
    private var keyboardBackground: Drawable? = null,
    private val stripBackgroundOverride: Int? = null,
    private val toolbarKeyOverride: Int? = null,
) : Colors {
    private val navBar: Int
    /** brightened or darkened variant of [background], to be used if exact background color would be
     *  bad contrast, e.g. popup keys popup or no border space bar */
    private val adjustedBackground: Int
    /** further brightened or darkened variant of [adjustedBackground] */
    private val doubleAdjustedBackground: Int
    private val adjustedSuggestionText = brightenOrDarken(suggestionText, true)

    private val backgroundFilter = colorFilter(background)
    private val adjustedBackgroundFilter: ColorFilter
    private val keyTextFilter: ColorFilter
    private val suggestionTextFilter = colorFilter(suggestionText)
    private val accentColorFilter = colorFilter(accent)
    private val effectiveToolbarKeyColor = toolbarKeyOverride ?: suggestionText
    private val toolbarKeyFilter = if (toolbarKeyOverride != null) colorFilter(toolbarKeyOverride) else suggestionTextFilter

    /** color filter for the white action key icons in material theme, switches to gray if necessary for contrast */
    private val actionKeyIconColorFilter: ColorFilter?

    private val backgroundStateList: ColorStateList
    private val keyStateList: ColorStateList
    private val functionalKeyStateList: ColorStateList
    private val actionKeyStateList: ColorStateList
    private val spaceBarStateList: ColorStateList
    private val adjustedBackgroundStateList: ColorStateList
    private val stripBackgroundList: ColorStateList
    private val toolbarKeyStateList = activatedStateList(
        effectiveToolbarKeyColor,
        if (isBrightColor(effectiveToolbarKeyColor)) darken(darken(effectiveToolbarKeyColor))
        else brighten(brighten(effectiveToolbarKeyColor))
    )
    private var backgroundSetupDone = false

    init {
        if (isDarkColor(background)) {
            adjustedBackground = brighten(background)
            doubleAdjustedBackground = brighten(adjustedBackground)
        } else {
            adjustedBackground = darken(background)
            doubleAdjustedBackground = darken(adjustedBackground)
        }
        adjustedBackgroundStateList = pressedStateList(doubleAdjustedBackground, adjustedBackground)

        val stripBackground: Int
        val pressedStripElementBackground: Int
        if (keyboardBackground != null || themeStyle == STYLE_HOLO) {
            stripBackground = stripBackgroundOverride ?: Color.TRANSPARENT
            pressedStripElementBackground = if (isDarkColor(stripBackgroundOverride ?: background)) 0x22ffffff // assume background is similar to the background color
                else 0x11000000
        } else {
            stripBackground = stripBackgroundOverride ?: background
            pressedStripElementBackground = stripBackgroundOverride?.let { brightenOrDarken(it, true) } ?: adjustedBackground
        }
        stripBackgroundList = pressedStateList(pressedStripElementBackground, stripBackground)

        if (themeStyle == STYLE_HOLO && keyboardBackground == null) {
            val darkerBackground = adjustLuminosityAndKeepAlpha(background, -0.2f)
            navBar = darkerBackground
            keyboardBackground = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(background, darkerBackground))
            backgroundSetupDone = true
        } else {
            navBar = background
        }

        adjustedBackgroundFilter = colorFilter(adjustedBackground)
        if (hasKeyBorders) {
            backgroundStateList = pressedStateList(brightenOrDarken(background, true), background)
            keyStateList = if (themeStyle == STYLE_HOLO) pressedStateList(keyBackground, keyBackground)
                else pressedStateList(brightenOrDarken(keyBackground, true), keyBackground)
            functionalKeyStateList = pressedStateList(brightenOrDarken(functionalKey, true), functionalKey)
            actionKeyStateList = if (themeStyle == STYLE_HOLO) functionalKeyStateList
                else pressedStateList(brightenOrDarken(accent, true), accent)
            spaceBarStateList = if (themeStyle == STYLE_HOLO) pressedStateList(spaceBar, spaceBar)
                else pressedStateList(brightenOrDarken(spaceBar, true), spaceBar)
        } else {
            // need to set color to background if key borders are disabled, or there will be ugly keys
            backgroundStateList = pressedStateList(brightenOrDarken(background, true), background)
            keyStateList = pressedStateList(keyBackground, Color.TRANSPARENT)
            functionalKeyStateList = keyStateList
            actionKeyStateList = if (themeStyle == STYLE_HOLO) functionalKeyStateList
                else pressedStateList(brightenOrDarken(accent, true), accent)
            val borderlessSpaceBar = androidx.core.graphics.ColorUtils.blendARGB(background, spaceBar, 0.45f)
            spaceBarStateList = pressedStateList(brightenOrDarken(borderlessSpaceBar, true), borderlessSpaceBar)
        }
        keyTextFilter = colorFilter(keyText)
        actionKeyIconColorFilter = when {
            themeStyle == STYLE_HOLO -> keyTextFilter
            // the white icon may not have enough contrast, and can't be adjusted by the user
            isBrightColor(accent) -> colorFilter(Color.DKGRAY)
            else -> colorFilter(Color.WHITE)
        }
    }

    override fun get(color: ColorType): Int = when (color) {
        TOOL_BAR_KEY_ENABLED_BACKGROUND, EMOJI_CATEGORY_SELECTED, ACTION_KEY_BACKGROUND,
            CLIPBOARD_PIN, SHIFT_KEY_ICON -> accent
        AUTOFILL_BACKGROUND_CHIP -> if (themeStyle == STYLE_MATERIAL && !hasKeyBorders) background else adjustedBackground
        GESTURE_PREVIEW, POPUP_KEYS_BACKGROUND, MORE_SUGGESTIONS_BACKGROUND, KEY_PREVIEW_BACKGROUND -> adjustedBackground
        TOOL_BAR_EXPAND_KEY_BACKGROUND, CLIPBOARD_SUGGESTION_BACKGROUND -> doubleAdjustedBackground
        GESTURE_TRAIL -> gesture
        KEY_TEXT, REMOVE_SUGGESTION_ICON, FUNCTIONAL_KEY_TEXT, KEY_ICON, EMOJI_KEY_TEXT,
            POPUP_KEY_TEXT, POPUP_KEY_ICON, KEY_PREVIEW_TEXT -> keyText
        KEY_HINT_TEXT -> keyHintText
        SPACE_BAR_TEXT -> spaceBarText
        FUNCTIONAL_KEY_BACKGROUND -> functionalKey
        SPACE_BAR_BACKGROUND -> spaceBar
        MORE_SUGGESTIONS_WORD_BACKGROUND, MAIN_BACKGROUND -> background
        KEY_BACKGROUND -> keyBackground
        ACTION_KEY_POPUP_KEYS_BACKGROUND -> if (themeStyle == STYLE_HOLO) adjustedBackground else accent
        STRIP_BACKGROUND -> stripBackgroundOverride ?: background
        NAVIGATION_BAR -> navBar
        SUGGESTION_AUTO_CORRECT, EMOJI_CATEGORY, TOOL_BAR_EXPAND_KEY, ONE_HANDED_MODE_BUTTON -> suggestionText
        TOOL_BAR_KEY -> toolbarKeyOverride ?: suggestionText
        MORE_SUGGESTIONS_HINT, SUGGESTED_WORD, SUGGESTION_TYPED_WORD, SUGGESTION_VALID_WORD -> adjustedSuggestionText
        ACTION_KEY_ICON -> if (isBrightColor(accent)) Color.DKGRAY else Color.WHITE
        EDIT_MODE_DELETE_BACKGROUND -> androidx.core.graphics.ColorUtils.blendARGB(accent, functionalKey, 0.4f)
        EDIT_MODE_FUNC_BACKGROUND -> androidx.core.graphics.ColorUtils.blendARGB(accent, functionalKey, 0.2f)
        EDIT_MODE_ALPHA_BACKGROUND -> androidx.core.graphics.ColorUtils.blendARGB(accent, functionalKey, 0.6f)
        EDIT_MODE_NAV_BACKGROUND -> androidx.core.graphics.ColorUtils.blendARGB(keyBackground, functionalKey, 0.3f)
        EDIT_MODE_JUMP_BACKGROUND -> androidx.core.graphics.ColorUtils.blendARGB(keyBackground, functionalKey, 0.7f)
    }

    override fun setColor(drawable: Drawable?, color: ColorType) {
        if (drawable == null) return
        val colorStateList = when (color) {
            MORE_SUGGESTIONS_WORD_BACKGROUND -> backgroundStateList
            KEY_BACKGROUND -> keyStateList
            FUNCTIONAL_KEY_BACKGROUND -> functionalKeyStateList
            ACTION_KEY_BACKGROUND -> actionKeyStateList
            SPACE_BAR_BACKGROUND -> spaceBarStateList
            POPUP_KEYS_BACKGROUND -> adjustedBackgroundStateList
            STRIP_BACKGROUND -> stripBackgroundList
            ACTION_KEY_POPUP_KEYS_BACKGROUND -> if (themeStyle == STYLE_HOLO) adjustedBackgroundStateList
                else actionKeyStateList
            TOOL_BAR_KEY -> toolbarKeyStateList
            else -> null // use color filter
        }
        if (colorStateList == null) {
            DrawableCompat.setTintList(drawable, null)
            drawable.colorFilter = getColorFilter(color)
            return
        }
        DrawableCompat.setTintMode(drawable, PorterDuff.Mode.MULTIPLY)
        DrawableCompat.setTintList(drawable, colorStateList)
    }

    override fun setColor(view: ImageView, color: ColorType) {
        if (color == TOOL_BAR_KEY) {
            view.clearColorFilter()
            view.drawable?.let { setColor(it, color) }
            return
        }
        view.drawable?.let { DrawableCompat.setTintList(it, null) }
        view.colorFilter = getColorFilter(color)
    }

    override fun setBackground(view: View, color: ColorType) {
        if (view.background == null)
            view.setBackgroundColor(Color.WHITE) // set white to make the color filters work
        when (color) {
            KEY_PREVIEW_BACKGROUND, POPUP_KEYS_BACKGROUND -> view.background.colorFilter = adjustedBackgroundFilter
            FUNCTIONAL_KEY_BACKGROUND, KEY_BACKGROUND, MORE_SUGGESTIONS_WORD_BACKGROUND, SPACE_BAR_BACKGROUND, STRIP_BACKGROUND, CLIPBOARD_SUGGESTION_BACKGROUND -> setColor(view.background, color)
            ONE_HANDED_MODE_BUTTON -> setColor(view.background, if (keyboardBackground == null) MAIN_BACKGROUND else STRIP_BACKGROUND)
            MORE_SUGGESTIONS_BACKGROUND -> view.background.colorFilter = backgroundFilter
            MAIN_BACKGROUND -> {
                keyboardBackground?.let { bg ->
                    view.background = bg
                } ?: run { view.background.colorFilter = backgroundFilter }
            }
            else -> view.background.colorFilter = backgroundFilter
        }
    }

    private fun getColorFilter(color: ColorType): ColorFilter? = when (color) {
        EMOJI_CATEGORY_SELECTED, CLIPBOARD_PIN, SHIFT_KEY_ICON -> accentColorFilter
        KEY_TEXT, KEY_ICON -> keyTextFilter
        REMOVE_SUGGESTION_ICON, EMOJI_CATEGORY, ONE_HANDED_MODE_BUTTON, TOOL_BAR_EXPAND_KEY -> suggestionTextFilter
        TOOL_BAR_KEY -> toolbarKeyFilter
        KEY_PREVIEW_BACKGROUND -> adjustedBackgroundFilter
        ACTION_KEY_ICON -> actionKeyIconColorFilter
        else -> colorFilter(get(color)) // create color filter (not great for performance, so the frequently used filters should be stored)
    }

    override fun getPressedColor(color: ColorType): Int {
        if (color == ColorType.POPUP_KEYS_BACKGROUND || color == ColorType.ACTION_KEY_POPUP_KEYS_BACKGROUND) {
            return doubleAdjustedBackground
        }
        return brightenOrDarken(get(color), true)
    }
}

class AllColors(private val colorMap: EnumMap<ColorType, Int>, override val themeStyle: String, override val hasKeyBorders: Boolean, backgroundImage: Drawable?) : Colors {
    private var keyboardBackground: Drawable? = backgroundImage
    private val stateListMap = EnumMap<ColorType, ColorStateList>(ColorType::class.java)
    private var backgroundSetupDone = false
    private val colorFilters = hashMapOf<ColorType, ColorFilter>()
    private val toolbarKeyStateList by lazy {
        val color = get(ColorType.TOOL_BAR_KEY)
        activatedStateList(
            color,
            if (isBrightColor(color)) darken(darken(color))
            else brighten(brighten(color))
        )
    }

    override fun get(color: ColorType): Int = colorMap[color] ?: color.default()

    override fun setColor(drawable: Drawable?, color: ColorType) {
        if (drawable == null) return
        val colorStateList = when (color) {
            ColorType.MORE_SUGGESTIONS_WORD_BACKGROUND,
            ColorType.KEY_BACKGROUND,
            ColorType.FUNCTIONAL_KEY_BACKGROUND,
            ColorType.ACTION_KEY_BACKGROUND,
            ColorType.SPACE_BAR_BACKGROUND,
            ColorType.POPUP_KEYS_BACKGROUND,
            ColorType.STRIP_BACKGROUND,
            ColorType.ACTION_KEY_POPUP_KEYS_BACKGROUND -> stateListMap.getOrPut(color) {
                pressedStateList(brightenOrDarken(get(color), true), get(color))
            }
            ColorType.TOOL_BAR_KEY -> toolbarKeyStateList
            else -> null // use color filter
        }
        if (colorStateList == null) {
            DrawableCompat.setTintList(drawable, null)
            drawable.colorFilter = getColorFilter(color)
            return
        }
        drawable.colorFilter = null
        DrawableCompat.setTintMode(drawable, PorterDuff.Mode.MULTIPLY)
        DrawableCompat.setTintList(drawable, colorStateList)
    }

    override fun setColor(view: ImageView, color: ColorType) {
        if (color == ColorType.TOOL_BAR_KEY) {
            view.clearColorFilter()
            view.drawable?.let { setColor(it, color) }
            return
        }
        view.drawable?.let {
            DrawableCompat.setTintList(it, null)
            it.colorFilter = null
        }
        view.colorFilter = getColorFilter(color)
    }

    override fun setBackground(view: View, color: ColorType) {
        if (view.background == null)
            view.setBackgroundColor(Color.WHITE) // set white to make the color filters work
        when (color) {
            ColorType.KEY_PREVIEW_BACKGROUND, ColorType.POPUP_KEYS_BACKGROUND, ColorType.MORE_SUGGESTIONS_BACKGROUND -> {
                view.background.colorFilter = getColorFilter(color)
            }
            ColorType.ONE_HANDED_MODE_BUTTON -> setColor(view.background, ColorType.MAIN_BACKGROUND) // button has no separate background color
            ColorType.MAIN_BACKGROUND -> {
                keyboardBackground?.let { bg ->
                    view.background = bg
                } ?: run { view.background.colorFilter = getColorFilter(color) }
            }
            else -> setColor(view.background, color)
        }
    }

    private fun getColorFilter(color: ColorType) = colorFilters.getOrPut(color) {
        val c = get(color)
        if (color == ColorType.ACTION_KEY_ICON && !colorMap.containsKey(ColorType.ACTION_KEY_ICON)) {
            val accent = get(ColorType.ACTION_KEY_BACKGROUND)
            if (isBrightColor(accent)) colorFilter(Color.DKGRAY) else colorFilter(Color.WHITE)
        } else {
            colorFilter(c)
        }
    }

    override fun getPressedColor(color: ColorType): Int {
        return brightenOrDarken(get(color), true)
    }
}

private fun colorFilter(color: Int, mode: BlendModeCompat = BlendModeCompat.MODULATE): ColorFilter {
    // null is only returned for unsupported blend modes, which are not used here
    return BlendModeColorFilterCompat.createBlendModeColorFilterCompat(color, mode)
        ?: error("unsupported blend mode for color filter: $mode")
}

private fun pressedStateList(pressed: Int, normal: Int): ColorStateList {
    val states = arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf(-android.R.attr.state_pressed))
    return ColorStateList(states, intArrayOf(pressed, normal))
}

private fun activatedStateList(activated: Int, normal: Int): ColorStateList {
    val states = arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf(-android.R.attr.state_activated))
    return ColorStateList(states, intArrayOf(activated, normal))
}

enum class ColorType {
    ACTION_KEY_ICON,
    ACTION_KEY_BACKGROUND,
    ACTION_KEY_POPUP_KEYS_BACKGROUND,
    AUTOFILL_BACKGROUND_CHIP,
    CLIPBOARD_PIN,
    EMOJI_CATEGORY,
    EMOJI_CATEGORY_SELECTED,
    EMOJI_KEY_TEXT,
    FUNCTIONAL_KEY_TEXT,
    FUNCTIONAL_KEY_BACKGROUND,
    GESTURE_TRAIL,
    GESTURE_PREVIEW,
    KEY_BACKGROUND,
    KEY_ICON,
    KEY_TEXT,
    KEY_HINT_TEXT,
    KEY_PREVIEW_BACKGROUND,
    KEY_PREVIEW_TEXT,
    MORE_SUGGESTIONS_HINT,
    MORE_SUGGESTIONS_BACKGROUND,
    MORE_SUGGESTIONS_WORD_BACKGROUND,
    POPUP_KEYS_BACKGROUND,
    POPUP_KEY_TEXT,
    POPUP_KEY_ICON,
    NAVIGATION_BAR,
    SHIFT_KEY_ICON,
    SPACE_BAR_BACKGROUND,
    SPACE_BAR_TEXT,
    ONE_HANDED_MODE_BUTTON,
    REMOVE_SUGGESTION_ICON,
    STRIP_BACKGROUND,
    CLIPBOARD_SUGGESTION_BACKGROUND,
    SUGGESTED_WORD,
    SUGGESTION_AUTO_CORRECT,
    SUGGESTION_TYPED_WORD,
    SUGGESTION_VALID_WORD,
    TOOL_BAR_EXPAND_KEY,
    TOOL_BAR_EXPAND_KEY_BACKGROUND,
    TOOL_BAR_KEY,
    TOOL_BAR_KEY_ENABLED_BACKGROUND,
    EDIT_MODE_DELETE_BACKGROUND,
    EDIT_MODE_FUNC_BACKGROUND,
    EDIT_MODE_ALPHA_BACKGROUND,
    EDIT_MODE_NAV_BACKGROUND,
    EDIT_MODE_JUMP_BACKGROUND,
    MAIN_BACKGROUND,
}

// this is not used any more, but we keep in case a colorMap does not get filled for whatever reason
fun ColorType.default() = ColorUtils.setAlphaComponent(name.hashCode() and 0xffffff, 255)
