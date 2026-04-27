package helium314.keyboard.latin

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DictionaryGroupTest {

    @Test
    fun testIncreaseConfidence() {
        val clazz = Class.forName("helium314.keyboard.latin.DictionaryGroup")
        // DictionaryGroup has arguments (Locale, Dictionary?, Map, Context?)
        val constructor = clazz.declaredConstructors.firstOrNull { it.parameterCount == 4 }
            ?: clazz.declaredConstructors.first()

        constructor.isAccessible = true

        val instance = constructor.newInstance(Locale.ENGLISH, null, emptyMap<String, Any>(), null)

        val confidenceField = clazz.getDeclaredField("confidence")
        confidenceField.isAccessible = true

        val increaseMethod = clazz.getDeclaredMethod("increaseConfidence")
        increaseMethod.isAccessible = true

        val initialConfidence = confidenceField.get(instance) as Float
        assertEquals(1f, initialConfidence, 0.0001f)

        increaseMethod.invoke(instance)

        val newConfidence = confidenceField.get(instance) as Float
        assertEquals(1.2f, newConfidence, 0.0001f)

        // Test limit
        increaseMethod.invoke(instance)
        val cappedConfidence = confidenceField.get(instance) as Float
        assertEquals(1.2f, cappedConfidence, 0.0001f)
    }
}
