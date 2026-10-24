package helium314.keyboard.latin.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KtxTest {

    @Test
    fun testSumOf() {
        val list = listOf("a", "ab", "abc")
        val sum = list.sumOf { it.length.toFloat() }
        assertEquals(6.0f, sum, 0.001f)

        val emptyList = emptyList<String>()
        val emptySum = emptyList.sumOf { it.length.toFloat() }
        assertEquals(0.0f, emptySum, 0.001f)
    }

    @Test
    fun testSplitAt() {
        val list = listOf(1, 2, 3, 4, 5)

        // Split at first element > 3
        val (first, second) = list.splitAt { it > 3 }

        assertEquals(listOf(1, 2, 3), first)
        assertEquals(listOf(5), second) // 4 is discarded

        // Split at condition that is never met
        val (firstNoMatch, secondNoMatch) = list.splitAt { it > 10 }
        assertEquals(listOf(1, 2, 3, 4, 5), firstNoMatch)
        assertTrue(secondNoMatch.isEmpty())

        // Split at first element
        val (firstMatchFirst, secondMatchFirst) = list.splitAt { it == 1 }
        assertTrue(firstMatchFirst.isEmpty())
        assertEquals(listOf(2, 3, 4, 5), secondMatchFirst)
    }

    @Test
    fun testAddCollections() {
        val list1 = listOf(1, 2)
        val list2 = listOf(3, 4)

        assertEquals(listOf(1, 2, 3, 4), addCollections(list1, list2))

        val empty = emptyList<Int>()
        assertEquals(list1, addCollections(list1, empty))
        assertEquals(list1, addCollections(list1, null))
        assertEquals(list2, addCollections(empty, list2))
        assertEquals(list2, addCollections(null, list2))

        assertNull(addCollections(null as List<Int>?, null as List<Int>?))
        assertTrue(addCollections(empty, empty)?.isEmpty() == true)
    }

    @Test
    fun testRemoveFirst() {
        val list = mutableListOf(1, 2, 3, 2, 4)

        list.removeFirst { it == 2 }
        assertEquals(listOf(1, 3, 2, 4), list) // only the first '2' is removed

        list.removeFirst { it > 10 }
        assertEquals(listOf(1, 3, 2, 4), list) // no elements match, list unchanged
    }

    @Test
    fun testReplaceFirst() {
        val list = mutableListOf(1, 2, 3, 2, 4)

        list.replaceFirst({ it == 2 }, { it * 10 })
        assertEquals(listOf(1, 20, 3, 2, 4), list) // only the first '2' is replaced

        list.replaceFirst({ it > 100 }, { it * 10 })
        assertEquals(listOf(1, 20, 3, 2, 4), list) // no elements match, list unchanged
    }
}
