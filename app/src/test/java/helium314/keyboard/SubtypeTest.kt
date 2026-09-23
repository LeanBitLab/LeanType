package helium314.keyboard

import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.POPUP_KEYS_NORMAL
import helium314.keyboard.keyboard.internal.keyboard_parser.addLocaleKeyTextsToParams
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.POPUP_KEYS_LAYOUT
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowInputMethodManager2::class
])
class SubtypeTest {
    private lateinit var latinIME: LatinIME
    private lateinit var params: KeyboardParams

    @BeforeTest fun setUp() {
        latinIME = Robolectric.setupService(LatinIME::class.java)
        val prefs = latinIME.prefs()
        prefs.edit().clear().commit()
        SubtypeSettings.init(latinIME)
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out
        params = KeyboardParams()
        params.mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardId.ELEMENT_ALPHABET)
        params.mPopupKeyTypes.add(POPUP_KEYS_LAYOUT)
        addLocaleKeyTextsToParams(latinIME, params, POPUP_KEYS_NORMAL)
    }

    @Test fun testGetDictionaryLocales() {
        val prefs = latinIME.prefs()
        prefs.edit().putString(Settings.PREF_ENABLED_SUBTYPES, "").apply()
        SubtypeSettings.reloadEnabledSubtypes(latinIME)

        val enSubtype = SubtypeSettings.getResourceSubtypesForLocale("en_US".constructLocale()).first()
        val frSubtype = SubtypeSettings.getResourceSubtypesForLocale("fr".constructLocale()).first()

        SubtypeSettings.addEnabledSubtype(prefs, enSubtype)
        SubtypeSettings.addEnabledSubtype(prefs, frSubtype)
        SubtypeSettings.reloadEnabledSubtypes(latinIME)

        val locales = helium314.keyboard.latin.utils.getDictionaryLocales(latinIME)
        assertTrue(locales.contains("en_US".constructLocale()))
        assertTrue(locales.contains("fr".constructLocale()))
    }

    @Test fun emptyAdditionalSubtypesResultsInEmptyList() {
        // avoid issues where empty string results in additional subtype for undefined locale
        val prefs = latinIME.prefs()
        prefs.edit().putString(Settings.PREF_ADDITIONAL_SUBTYPES, "").apply()
        assertTrue(SubtypeSettings.getAdditionalSubtypes().isEmpty())
        val from = SubtypeSettings.getResourceSubtypesForLocale("es".constructLocale()).first()

        // no change, and "changed" subtype actually is resource subtype -> still expect empty list
        SubtypeUtilsAdditional.changeAdditionalSubtype(from.toSettingsSubtype(), from.toSettingsSubtype(), latinIME)
        assertEquals(emptyList(), SubtypeSettings.getAdditionalSubtypes().map { it.toSettingsSubtype() })
    }

    @Test fun subtypeStaysEnabledOnEdits() {
        val prefs = latinIME.prefs()
        prefs.edit().putString(Settings.PREF_ADDITIONAL_SUBTYPES, "").putString(Settings.PREF_ENABLED_SUBTYPES, "").apply() // clear it for convenience
        SubtypeSettings.reloadEnabledSubtypes(latinIME)

        // edit enabled resource subtype
        val from = SubtypeSettings.getResourceSubtypesForLocale("es".constructLocale()).first()
        SubtypeSettings.addEnabledSubtype(prefs, from)
        val to = from.toSettingsSubtype().withLayout(LayoutType.SYMBOLS, "symbols_arabic")
        SubtypeUtilsAdditional.changeAdditionalSubtype(from.toSettingsSubtype(), to, latinIME)
        assertEquals(to, SubtypeSettings.getEnabledSubtypes(false).single().toSettingsSubtype())

        // change the new subtype to effectively be the same as original resource subtype
        val toNew = to.withoutLayout(LayoutType.SYMBOLS)
        assertEquals(from.toSettingsSubtype(), toNew)
        SubtypeUtilsAdditional.changeAdditionalSubtype(to, toNew, latinIME)
        assertEquals(emptyList(), SubtypeSettings.getAdditionalSubtypes().map { it.toSettingsSubtype() })
        assertEquals(from.toSettingsSubtype(), SubtypeSettings.getEnabledSubtypes(false).single().toSettingsSubtype())
    }

    @Test fun catalanDictionaryFolderIsPreserved() {
        val dictDir = File(DictionaryInfoUtils.getWordListCacheDirectory(latinIME))
        val caFolder = File(dictDir, "ca").apply { mkdirs() }
        val caDictFile = File(caFolder, "main.dict").apply { writeText("dummy") }
        val enCaFolder = File(dictDir, "en-CA").apply { mkdirs() }
        File(enCaFolder, "main.dict").apply { writeText("dummy-en") }

        helium314.keyboard.latin.utils.getDictionaryLocales(latinIME)

        assertTrue(caFolder.exists(), "ca folder should not be deleted or renamed by legacy migration")
        assertTrue(caDictFile.exists(), "ca dictionary file should be preserved")
    }
}
