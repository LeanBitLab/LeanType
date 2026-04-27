package helium314.keyboard.settings

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33]) // optional, explicitly setting SDK can sometimes resolve robolectric issues
class MiscTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun isWideScreen_true_whenWidthGreaterThan600() {
        var result = false
        composeTestRule.setContent {
            val config = Configuration().apply {
                screenWidthDp = 601
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                result = isWideScreen()
            }
        }
        assertTrue(result)
    }

    @Test
    fun isWideScreen_false_whenWidthIs600() {
        var result = true
        composeTestRule.setContent {
            val config = Configuration().apply {
                screenWidthDp = 600
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                result = isWideScreen()
            }
        }
        assertFalse(result)
    }

    @Test
    fun isWideScreen_false_whenWidthLessThan600() {
        var result = true
        composeTestRule.setContent {
            val config = Configuration().apply {
                screenWidthDp = 599
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                result = isWideScreen()
            }
        }
        assertFalse(result)
    }
}
