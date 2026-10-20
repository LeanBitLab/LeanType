package helium314.keyboard.latin.utils

import android.view.inputmethod.InputMethodSubtype
import helium314.keyboard.latin.common.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class SubtypeUtilsTest {

    @Test
    fun testLocale_fromLanguageTag() {
        val subtype = InputMethodSubtype.InputMethodSubtypeBuilder()
            .setLanguageTag("fr-CA")
            .build()
        assertEquals(Locale("fr", "CA"), subtype.locale())
    }

    @Test
    fun testLocale_fromSubtypeLocale() {
        val subtype = InputMethodSubtype.InputMethodSubtypeBuilder()
            .setSubtypeLocale("de_DE")
            .build()
        assertEquals(Locale("de", "DE"), subtype.locale())
    }

    @Test
    fun testMainLayoutName() {
        val subtype = InputMethodSubtype.InputMethodSubtypeBuilder()
            .setSubtypeExtraValue("${Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET}=MAIN:qwerty")
            .build()
        assertEquals("qwerty", subtype.mainLayoutName())
        assertEquals("qwerty", subtype.mainLayoutNameOrQwerty())
    }

    @Test
    fun testMainLayoutName_missing() {
        val subtype = InputMethodSubtype.InputMethodSubtypeBuilder()
            .setSubtypeExtraValue("SomeOtherValue=test")
            .build()
        assertNull(subtype.mainLayoutName())
        assertEquals("qwerty", subtype.mainLayoutNameOrQwerty())
    }

    @Test
    fun testGetSecondaryLocales() {
        val extraValue = "${Constants.Subtype.ExtraValue.SECONDARY_LOCALES}=en_US:fr:de_DE"
        val locales = getSecondaryLocales(extraValue)
        assertEquals(3, locales.size)
        assertEquals(Locale("en", "US"), locales[0])
        assertEquals(Locale("fr"), locales[1])
        assertEquals(Locale("de", "DE"), locales[2])
    }

    @Test
    fun testGetSecondaryLocales_empty() {
        val extraValue = "SomeOtherValue=test"
        val locales = getSecondaryLocales(extraValue)
        assertEquals(0, locales.size)
    }
}
