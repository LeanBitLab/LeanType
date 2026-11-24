// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import helium314.keyboard.latin.utils.isBrightColor
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ColorsTest {
    private val darkBackground = 0xff1b1b1f.toInt()
    private val lightBackground = 0xfff2f0f4.toInt()

    @Test fun `translucent accent does not crash (#909)`() {
        // accent colors from the #909 crash reports
        for (accent in listOf(0x7379a5ff.toInt(), 0x9d79a5ff.toInt())) {
            isLightAccent(accent, darkBackground)
            isLightAccent(accent, lightBackground)
            isLightAccent(accent, 0x80000000.toInt()) // translucent background
            isLightAccent(accent, Color.TRANSPARENT)
        }
        isLightAccent(Color.TRANSPARENT, Color.TRANSPARENT)
    }

    @Test fun `opaque accent gives the same result as before`() {
        val accents = listOf(Color.WHITE, Color.BLACK, Color.YELLOW, Color.BLUE, Color.RED, 0xff79a5ff.toInt(),
            0xff5e9e92.toInt(), 0xffd0bcff.toInt(), 0xff6750a4.toInt(), 0xffb3b3b3.toInt(), 0xff777777.toInt())
        for (accent in accents) {
            val before = isBrightColor(accent) ||
                ColorUtils.calculateContrast(Color.DKGRAY, accent) > ColorUtils.calculateContrast(Color.WHITE, accent)
            assertEquals(before, isLightAccent(accent, darkBackground), "accent ${Integer.toHexString(accent)}")
            assertEquals(before, isLightAccent(accent, lightBackground), "accent ${Integer.toHexString(accent)}")
        }
    }

    @Test fun `translucent accent is judged by the color shown on the background`() {
        val faintWhite = 0x20ffffff
        assertFalse(isLightAccent(faintWhite, darkBackground)) // looks dark: white icon
        assertTrue(isLightAccent(faintWhite, lightBackground)) // looks light: dark icon
    }
}
