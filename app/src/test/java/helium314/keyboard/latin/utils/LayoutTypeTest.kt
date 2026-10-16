package helium314.keyboard.latin.utils

import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.utils.LayoutType.Companion.folder
import helium314.keyboard.latin.utils.LayoutType.Companion.getMainLayoutFromExtraValue
import helium314.keyboard.latin.utils.LayoutType.Companion.toExtraValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.EnumMap

class LayoutTypeTest {

    @Test
    fun testGetLayoutMap() {
        val str = "MAIN${Separators.KV}qwerty${Separators.ENTRY}SYMBOLS${Separators.KV}symbols"
        val map = LayoutType.getLayoutMap(str)
        assertEquals(2, map.size)
        assertEquals("qwerty", map[LayoutType.MAIN])
        assertEquals("symbols", map[LayoutType.SYMBOLS])
    }

    @Test
    fun testGetLayoutMap_nullInput() {
        val map = LayoutType.getLayoutMap(null)
        assertTrue(map.isEmpty())
    }

    @Test
    fun testGetLayoutMap_emptyString() {
        val map = LayoutType.getLayoutMap("")
        assertTrue(map.isEmpty())
    }

    @Test
    fun testGetLayoutMap_invalidEnum() {
        val str = "INVALID_LAYOUT${Separators.KV}qwerty"
        val map = LayoutType.getLayoutMap(str)
        assertTrue(map.isEmpty()) // runCatching should ignore it
    }

    @Test
    fun testGetLayoutMap_malformedEntry() {
        val str = "MAIN" // Missing Separators.KV
        val map = LayoutType.getLayoutMap(str)
        assertTrue(map.isEmpty()) // runCatching should ignore index out of bounds
    }

    @Test
    fun testToExtraValue() {
        val map = EnumMap<LayoutType, String>(LayoutType::class.java).apply {
            put(LayoutType.MAIN, "dvorak")
            put(LayoutType.NUMBER, "number_layout")
        }
        val extraValue = map.toExtraValue()
        assertEquals("MAIN${Separators.KV}dvorak${Separators.ENTRY}NUMBER${Separators.KV}number_layout", extraValue)
    }

    @Test
    fun testToExtraValue_emptyMap() {
        val map = EnumMap<LayoutType, String>(LayoutType::class.java)
        val extraValue = map.toExtraValue()
        assertEquals("", extraValue)
    }

    @Test
    fun testFolder() {
        assertEquals("layouts${File.separator}main", LayoutType.MAIN.folder)
        assertEquals("layouts${File.separator}custom", LayoutType.CUSTOM1.folder)
        assertEquals("layouts${File.separator}custom", LayoutType.CUSTOM5.folder)
        assertEquals("layouts${File.separator}symbols", LayoutType.SYMBOLS.folder)
    }

    @Test
    fun testGetMainLayoutFromExtraValue() {
        val extraValue = "SomeKey=SomeValue,${ExtraValue.KEYBOARD_LAYOUT_SET}=MAIN${Separators.KV}colemak${Separators.ENTRY}SYMBOLS${Separators.KV}symbols,AnotherKey=AnotherValue"
        val mainLayout = LayoutType.getMainLayoutFromExtraValue(extraValue)
        assertEquals("colemak", mainLayout)
    }

    @Test
    fun testGetMainLayoutFromExtraValue_missingLayoutSet() {
        val extraValue = "SomeKey=SomeValue,AnotherKey=AnotherValue"
        val mainLayout = LayoutType.getMainLayoutFromExtraValue(extraValue)
        assertNull(mainLayout)
    }

    @Test
    fun testGetMainLayoutFromExtraValue_missingMain() {
        val extraValue = "${ExtraValue.KEYBOARD_LAYOUT_SET}=SYMBOLS${Separators.KV}symbols"
        val mainLayout = LayoutType.getMainLayoutFromExtraValue(extraValue)
        assertNull(mainLayout)
    }
}
