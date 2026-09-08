package helium314.keyboard.settings

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import android.content.ComponentName
import android.content.pm.ActivityInfo
import org.junit.Before
import org.robolectric.Shadows

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MiscTest {

    @Before
    fun setUp() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val shadowPm = Shadows.shadowOf(context.packageManager)
        val activityInfo = ActivityInfo().apply {
            name = "androidx.activity.ComponentActivity"
            packageName = context.packageName
            exported = true
        }
        val componentName = ComponentName(context.packageName, "androidx.activity.ComponentActivity")
        val intentFilter = android.content.IntentFilter(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        shadowPm.addOrUpdateActivity(activityInfo)
        shadowPm.addIntentFilterForActivity(componentName, intentFilter)
    }

    @Test
    fun isWideScreen_true_whenWidthGreaterThan600() = runComposeUiTest {
        var result = false
        setContent {
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
    fun isWideScreen_false_whenWidthIs600() = runComposeUiTest {
        var result = true
        setContent {
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
    fun isWideScreen_false_whenWidthLessThan600() = runComposeUiTest {
        var result = true
        setContent {
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
