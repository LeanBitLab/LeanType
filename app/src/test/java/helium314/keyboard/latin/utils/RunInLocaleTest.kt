package helium314.keyboard.latin.utils

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class RunInLocaleTest {

    @Test
    fun testRunInLocaleContext() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val targetLocale = Locale("fr", "FR")

        val resultLocale = runInLocale(baseContext, targetLocale) { localeContext ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                localeContext.resources.configuration.locales[0]
            } else {
                @Suppress("deprecation")
                localeContext.resources.configuration.locale
            }
        }

        assertEquals(targetLocale, resultLocale)
    }

    @Test
    fun testRunInLocaleResources() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val baseResources = baseContext.resources
        val targetLocale = Locale("de", "DE")

        val resultLocale = runInLocale(baseResources, targetLocale) { localeResources ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                localeResources.configuration.locales[0]
            } else {
                @Suppress("deprecation")
                localeResources.configuration.locale
            }
        }

        assertEquals(targetLocale, resultLocale)
    }
}
