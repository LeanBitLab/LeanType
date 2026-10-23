package helium314.keyboard.latin.utils

import android.view.inputmethod.InputMethodSubtype
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class SubtypeLocaleUtilsTest {

    @Before
    fun setup() {
        SubtypeLocaleUtils.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun testGetSubtypeLocaleDisplayName() {
        // Standard locale
        assertEquals("English (US)", SubtypeLocaleUtils.getSubtypeLocaleDisplayName(Locale("en", "US")))

        // NO_LANGUAGE should fallback to system default layout or "No language" handling
        // Based on NO_LANGUAGE = "zz", test system logic
        val noLanguage = SubtypeLocaleUtils.getSubtypeLocaleDisplayName(Locale(SubtypeLocaleUtils.NO_LANGUAGE))
        assert(noLanguage.isNotEmpty())
    }

    @Test
    fun testGetSubtypeLanguageDisplayName() {
        assertEquals("English", SubtypeLocaleUtils.getSubtypeLanguageDisplayName(Locale("en", "US")))
        assertEquals("Deutsch", SubtypeLocaleUtils.getSubtypeLanguageDisplayName(Locale("de", "DE")))

        // NO_LANGUAGE
        val noLanguage = SubtypeLocaleUtils.getSubtypeLanguageDisplayName(Locale(SubtypeLocaleUtils.NO_LANGUAGE))
        assert(noLanguage.isNotEmpty())
    }

    @Test
    fun testGetSubtypeNameForLogging() {
        assertEquals("<null subtype>", SubtypeLocaleUtils.getSubtypeNameForLogging(null))

        val subtype = InputMethodSubtype.InputMethodSubtypeBuilder()
            .setLanguageTag("fr")
            .build()

        assertEquals("fr/qwerty", SubtypeLocaleUtils.getSubtypeNameForLogging(subtype)) // Fallbacks to qwerty because mainLayoutName missing
    }

    @Test
    fun testSubtypeDisplayNameExtension() {
        val subtype = InputMethodSubtype.InputMethodSubtypeBuilder()
            .setLanguageTag("en-US")
            .setSubtypeExtraValue("KeyboardLayoutSet=MAIN:qwerty")
            .build()

        val displayName = SubtypeLocaleUtils.run { subtype.displayName() }
        assert(displayName.isNotEmpty())
    }
}
