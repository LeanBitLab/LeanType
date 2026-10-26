package helium314.keyboard.latin.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TextExpanderUtilsTest {

    @Test
    fun testDefaultDateFormat() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 8, 14, 30, 0)
        }
        val date = cal.time
        val result = TextExpanderUtils.resolveDateWithModifiers(date, null)
        val expected = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        assertEquals(expected, result)
    }

    @Test
    fun testCustomDateFormatSlash() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 8, 14, 30, 0)
        }
        val date = cal.time
        val result = TextExpanderUtils.resolveDateWithModifiers(date, "yyyy/MM/dd")
        val expected = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(date)
        assertEquals(expected, result)
    }

    @Test
    fun testCustomDateFormatDash() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 8, 14, 30, 0)
        }
        val date = cal.time
        val result = TextExpanderUtils.resolveDateWithModifiers(date, "dd-MM-yyyy")
        val expected = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(date)
        assertEquals(expected, result)
    }

    @Test
    fun testExplicitFormatModifier() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 8, 14, 30, 0)
        }
        val date = cal.time
        val result = TextExpanderUtils.resolveDateWithModifiers(date, "format(yyyy.MM.dd)")
        val expected = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(date)
        assertEquals(expected, result)
    }

    @Test
    fun testFormatModifierWithChainedUpper() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 8, 14, 30, 0)
        }
        val date = cal.time
        val result = TextExpanderUtils.resolveDateWithModifiers(date, "format(MMMM):upper")
        val rawMonth = SimpleDateFormat("MMMM", Locale.getDefault()).format(date)
        assertEquals(rawMonth.uppercase(Locale.getDefault()), result)
    }

    @Test
    fun testNamedFormatIso() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 8, 14, 30, 0)
        }
        val date = cal.time
        val result = TextExpanderUtils.resolveDateWithModifiers(date, "iso")
        val expected = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        assertEquals(expected, result)
    }

    @Test
    fun testParseModifierTokens() {
        val tokens = TextExpanderUtils.parseModifierTokens("format(yyyy-MM-dd, HH:mm):upper:trim")
        assertEquals(listOf("format(yyyy-MM-dd, HH:mm)", "upper", "trim"), tokens)
    }

    private val enter = TextExpanderUtils.ENTER_KEY_MARKER.toString()
    private val tab = TextExpanderUtils.TAB_KEY_MARKER.toString()

    @Test
    fun testKeyPressStepsInOrder() {
        val steps = TextExpanderUtils.splitKeyPresses(TextExpanderUtils.markKeyPresses("user%tabkey%%tabkey%pw%enter%"))
        assertEquals(listOf("user", tab, tab, "pw", enter), steps)
    }

    @Test
    fun testInsertedTextCannotPressKeys() {
        // like %clipboard%: the template is marked first, then the text is inserted
        val template = TextExpanderUtils.markKeyPresses("Hi %clipboard%%enter%")
        val clipboard = TextExpanderUtils.withoutKeyPressMarkers("x %enter% %tabkey% y$enter$tab")
        val steps = TextExpanderUtils.splitKeyPresses(template.replace("%clipboard%", clipboard))
        assertEquals(listOf("Hi x %enter% %tabkey% y", enter), steps)
    }

    @Test
    fun testOnlyVisiblePlaceholdersPressKeys() {
        // a marker character in the template itself (e.g. from an imported file) is not a key press
        val steps = TextExpanderUtils.splitKeyPresses(TextExpanderUtils.markKeyPresses("a${enter}b"))
        assertEquals(listOf("ab"), steps)
        assertEquals("plain text", TextExpanderUtils.markKeyPresses("plain text"))
    }
}
