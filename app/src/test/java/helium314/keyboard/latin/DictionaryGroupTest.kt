package helium314.keyboard.latin

import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.ExpandableBinaryDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`
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

    @Test
    fun testIsBlacklisted_BlankAndPunctuation() {
        val dictGroupClass = Class.forName("helium314.keyboard.latin.DictionaryGroup")
        val constructor = dictGroupClass.declaredConstructors.first { it.parameterCount == 4 }
        constructor.isAccessible = true

        val instance = constructor.newInstance(Locale.ENGLISH, null, emptyMap<String, ExpandableBinaryDictionary>(), null)
        val isBlacklistedMethod = dictGroupClass.getDeclaredMethod("isBlacklisted", String::class.java)
        isBlacklistedMethod.isAccessible = true

        assertFalse(isBlacklistedMethod.invoke(instance, "") as Boolean)
        assertFalse(isBlacklistedMethod.invoke(instance, "   ") as Boolean)
        assertFalse(isBlacklistedMethod.invoke(instance, ":") as Boolean)
    }

    @Test
    fun testIsBlacklisted_ExactAndRegexMatching() {
        val dictGroupClass = Class.forName("helium314.keyboard.latin.DictionaryGroup")
        val constructor = dictGroupClass.declaredConstructors.first { it.parameterCount == 4 }
        constructor.isAccessible = true

        val instance = constructor.newInstance(Locale.ENGLISH, null, emptyMap<String, ExpandableBinaryDictionary>(), null)
        val addToBlacklistMethod = dictGroupClass.getDeclaredMethod("addToBlacklist", String::class.java)
        val isBlacklistedMethod = dictGroupClass.getDeclaredMethod("isBlacklisted", String::class.java)
        addToBlacklistMethod.isAccessible = true
        isBlacklistedMethod.isAccessible = true

        addToBlacklistMethod.invoke(instance, "spam")
        addToBlacklistMethod.invoke(instance, "c++")
        addToBlacklistMethod.invoke(instance, ".*bad.*")

        assertTrue(isBlacklistedMethod.invoke(instance, "spam") as Boolean)
        assertTrue(isBlacklistedMethod.invoke(instance, "SPAM") as Boolean)
        assertFalse(isBlacklistedMethod.invoke(instance, "spammer") as Boolean)

        assertTrue(isBlacklistedMethod.invoke(instance, "c++") as Boolean)
        assertFalse(isBlacklistedMethod.invoke(instance, "c") as Boolean)

        assertTrue(isBlacklistedMethod.invoke(instance, "badword") as Boolean)
        assertTrue(isBlacklistedMethod.invoke(instance, "superbad") as Boolean)
        assertFalse(isBlacklistedMethod.invoke(instance, "goodword") as Boolean)
    }

    @Test
    fun testRemoveWord_MainDictWordIsOnlyHiddenFromPredictions() {
        val dictGroupClass = Class.forName("helium314.keyboard.latin.DictionaryGroup")
        val constructor = dictGroupClass.declaredConstructors.first { it.parameterCount == 4 }
        constructor.isAccessible = true

        // The main dictionary only has the capitalized form, like "I'm" in English (#861), and lowercase "of".
        val mockMainDict = mock(Dictionary::class.java)
        `when`(mockMainDict.isValidWord("I'm")).thenReturn(true)
        `when`(mockMainDict.isValidWord("of")).thenReturn(true)
        val instance = constructor.newInstance(Locale.ENGLISH, mockMainDict, emptyMap<String, ExpandableBinaryDictionary>(), null)
        val isMainDictWordMethod = dictGroupClass.getDeclaredMethod("isMainDictWord", String::class.java)
        val isLowercaseFormMethod = dictGroupClass.getDeclaredMethod("isLowercaseFormMainDictWord", String::class.java)
        val removeWordMethod = dictGroupClass.getDeclaredMethod("removeWord", String::class.java, Boolean::class.javaPrimitiveType)
        val isBlacklistedMethod = dictGroupClass.getDeclaredMethod("isBlacklisted", String::class.java)
        listOf(isMainDictWordMethod, isLowercaseFormMethod, removeWordMethod, isBlacklistedMethod).forEach { it.isAccessible = true }

        for (word in listOf("I'm", "I,m", "Of")) {
            removeWordMethod.invoke(instance, word, !(isLowercaseFormMethod.invoke(instance, word) as Boolean))
        }

        // "I'm" is blocked, but as a main dictionary word the facilitator only hides it from predictions
        assertTrue(isBlacklistedMethod.invoke(instance, "I'm") as Boolean)
        assertTrue(isMainDictWordMethod.invoke(instance, "i'm") as Boolean)
        assertTrue(isBlacklistedMethod.invoke(instance, "I,m") as Boolean)
        assertFalse(isMainDictWordMethod.invoke(instance, "I,m") as Boolean)
        // removing "Of" must not block the valid lowercase "of" (blocking ignores case)
        assertFalse(isBlacklistedMethod.invoke(instance, "of") as Boolean)
    }
}
