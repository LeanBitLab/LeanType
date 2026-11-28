// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import androidx.core.graphics.ColorUtils
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/** Corner radius of the keyboard's chips, cards, buttons and popups, following the key corner radius setting. */
fun keyboardCornerRadiusPx(resources: Resources): Float =
    (Settings.getValues().mKeyBorderRadius.takeIf { it >= 0f } ?: 8f) * resources.displayMetrics.density

/** The field of the search bars inside the keyboard. */
fun keyboardSearchBarBackground(resources: Resources): Drawable = GradientDrawable().apply {
    cornerRadius = keyboardCornerRadiusPx(resources)
    // half as strong as the toolbar buttons, a whole bar in their colour looks heavy
    val color = Settings.getValues().mColors.get(ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
    setColor(ColorUtils.setAlphaComponent(color, Color.alpha(color) / 2))
}

/** For a background from XML, also inside a ripple or another layer drawable: gives it the shared corner radius. */
fun Drawable.withKeyboardCorners(resources: Resources): Drawable {
    val mutated = mutate()
    when (mutated) {
        is GradientDrawable -> mutated.cornerRadius = keyboardCornerRadiusPx(resources)
        is LayerDrawable -> for (i in 0 until mutated.numberOfLayers) mutated.getDrawable(i)?.withKeyboardCorners(resources)
    }
    return mutated
}
