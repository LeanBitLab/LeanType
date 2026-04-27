package helium314.keyboard.latin.utils

import android.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ColorUtilTest {

    @Test
    fun testIsBrightColor_white() {
        assertTrue("White should be bright", isBrightColor(Color.WHITE))
    }

    @Test
    fun testIsBrightColor_black() {
        assertFalse("Black should not be bright", isBrightColor(Color.BLACK))
    }

    @Test
    fun testIsBrightColor_transparent() {
        // android.R.color.transparent has a specific int value which should immediately return true.
        assertTrue("Transparent should be bright", isBrightColor(android.R.color.transparent))
    }

    @Test
    fun testIsBrightColor_thresholds() {
        // Brightness threshold calculation:
        // R^2 * .241 + G^2 * .691 + B^2 * .068 >= 210 * 210 = 44100

        // Exact threshold check for RGB(210, 210, 210)
        // 210*210*.241 + 210*210*.691 + 210*210*.068 = 44100 * (1.0) = 44100
        val exactlyThreshold = Color.rgb(210, 210, 210)
        assertTrue("RGB(210, 210, 210) should be bright", isBrightColor(exactlyThreshold))

        // Just below threshold check for RGB(209, 209, 209)
        // 209*209*.241 + 209*209*.691 + 209*209*.068 = 43681 * (1.0) = 43681 (< 44100)
        val justBelowThreshold = Color.rgb(209, 209, 209)
        assertFalse("RGB(209, 209, 209) should not be bright", isBrightColor(justBelowThreshold))
    }

    @Test
    fun testIsBrightColor_pureColors() {
        // Pure red: 255*255 * .241 = 15671 (< 44100) -> not bright
        assertFalse("Pure red should not be bright", isBrightColor(Color.RED))

        // Pure green: 255*255 * .691 = 44932 (>= 44100) -> bright
        assertTrue("Pure green should be bright", isBrightColor(Color.GREEN))

        // Pure blue: 255*255 * .068 = 4421 (< 44100) -> not bright
        assertFalse("Pure blue should not be bright", isBrightColor(Color.BLUE))
    }
}
