package helium314.keyboard.latin.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JsonUtilsTest {

    @Test
    fun testJsonStrToList_validList() {
        val jsonStr = "[{\"Integer\": 1}, {\"String\": \"hello\"}, {\"Integer\": 42}]"
        val list = JsonUtils.jsonStrToList(jsonStr)

        assertEquals(3, list.size)
        assertEquals(1, list[0])
        assertEquals("hello", list[1])
        assertEquals(42, list[2])
    }

    @Test
    fun testJsonStrToList_emptyString() {
        val list = JsonUtils.jsonStrToList("")
        assertTrue(list.isEmpty())
    }

    @Test
    fun testJsonStrToList_emptyArray() {
        val list = JsonUtils.jsonStrToList("[]")
        assertTrue(list.isEmpty())
    }

    @Test
    fun testJsonStrToList_invalidName() {
        // Objects with invalid property names should be skipped gracefully.
        val jsonStr = "[{\"Boolean\": true}, {\"Integer\": 123}]"
        val list = JsonUtils.jsonStrToList(jsonStr)

        assertEquals(1, list.size)
        assertEquals(123, list[0])
    }

    @Test
    fun testJsonStrToList_malformedJson() {
        // A malformed JSON should result in returning an empty list without throwing an unhandled exception.
        val jsonStr = "[malformed"
        val list = JsonUtils.jsonStrToList(jsonStr)
        assertTrue(list.isEmpty())
    }
}
