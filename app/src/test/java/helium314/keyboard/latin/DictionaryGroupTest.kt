package helium314.keyboard.latin

import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.ExpandableBinaryDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import java.util.Locale

class DictionaryGroupTest {

    @Test
    fun testCloseDict_MainDict() {
        val dictGroupClass = Class.forName("helium314.keyboard.latin.DictionaryGroup")
        val constructor = dictGroupClass.declaredConstructors.first { it.parameterCount == 4 }
        constructor.isAccessible = true

        val mockMainDict = mock(Dictionary::class.java)
        val instance = constructor.newInstance(Locale.ENGLISH, mockMainDict, emptyMap<String, ExpandableBinaryDictionary>(), null)

        val closeDictMethod = dictGroupClass.getDeclaredMethod("closeDict", String::class.java)
        closeDictMethod.isAccessible = true

        closeDictMethod.invoke(instance, Dictionary.TYPE_MAIN)

        verify(mockMainDict).close()
    }

    @Test
    fun testCloseDict_SubDict() {
        val dictGroupClass = Class.forName("helium314.keyboard.latin.DictionaryGroup")
        val constructor = dictGroupClass.declaredConstructors.first { it.parameterCount == 4 }
        constructor.isAccessible = true

        val mockSubDict = mock(ExpandableBinaryDictionary::class.java)
        val subDictsMap = mapOf(Dictionary.TYPE_USER_HISTORY to mockSubDict)

        val instance = constructor.newInstance(Locale.ENGLISH, null, subDictsMap, null)

        val closeDictMethod = dictGroupClass.getDeclaredMethod("closeDict", String::class.java)
        closeDictMethod.isAccessible = true

        val getSubDictMethod = dictGroupClass.getDeclaredMethod("getSubDict", String::class.java)
        getSubDictMethod.isAccessible = true

        assertEquals(mockSubDict, getSubDictMethod.invoke(instance, Dictionary.TYPE_USER_HISTORY))

        closeDictMethod.invoke(instance, Dictionary.TYPE_USER_HISTORY)

        verify(mockSubDict).close()
        assertNull(getSubDictMethod.invoke(instance, Dictionary.TYPE_USER_HISTORY))
    }

    @Test
    fun testCloseDict_MissingDict() {
        val dictGroupClass = Class.forName("helium314.keyboard.latin.DictionaryGroup")
        val constructor = dictGroupClass.declaredConstructors.first { it.parameterCount == 4 }
        constructor.isAccessible = true

        val instance = constructor.newInstance(Locale.ENGLISH, null, emptyMap<String, ExpandableBinaryDictionary>(), null)

        val closeDictMethod = dictGroupClass.getDeclaredMethod("closeDict", String::class.java)
        closeDictMethod.isAccessible = true

        // This should not throw any exceptions
        closeDictMethod.invoke(instance, "nonexistent_dict_type")
    }
}
