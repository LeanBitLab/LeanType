// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.latin.utils.LanguageDetector
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageDetectorTest {

    @Test
    fun testSpanishDetection() {
        assertEquals("es", LanguageDetector.detect(null, "Hola como estas", "en"))
        assertEquals("es", LanguageDetector.detect(null, "¿Dónde está la biblioteca?", "en"))
        assertEquals("es", LanguageDetector.detect(null, "Buenos días amigo", "en"))
        assertEquals("es", LanguageDetector.detect(null, "Muchas gracias por tu ayuda", "en"))
    }

    @Test
    fun testFrenchDetection() {
        assertEquals("fr", LanguageDetector.detect(null, "Bonjour mon ami", "en"))
        assertEquals("fr", LanguageDetector.detect(null, "Merci beaucoup pour tout", "en"))
        assertEquals("fr", LanguageDetector.detect(null, "S'il vous plaît", "en"))
    }

    @Test
    fun testGermanDetection() {
        assertEquals("de", LanguageDetector.detect(null, "Guten Morgen wie geht es dir", "en"))
        assertEquals("de", LanguageDetector.detect(null, "Danke schön für alles", "en"))
    }

    @Test
    fun testItalianDetection() {
        assertEquals("it", LanguageDetector.detect(null, "Ciao come stai", "en"))
        assertEquals("it", LanguageDetector.detect(null, "Grazie mille per l'aiuto", "en"))
    }

    @Test
    fun testPortugueseDetection() {
        assertEquals("pt", LanguageDetector.detect(null, "Olá tudo bem com você", "en"))
        assertEquals("pt", LanguageDetector.detect(null, "Muito obrigado pelo suporte", "en"))
    }

    @Test
    fun testNonLatinScriptDetection() {
        // Hindi / Devanagari
        assertEquals("hi", LanguageDetector.detect(null, "नमस्ते आप कैसे हैं", "en"))
        // Russian / Cyrillic
        assertEquals("ru", LanguageDetector.detect(null, "Привет как дела", "en"))
        // Japanese
        assertEquals("ja", LanguageDetector.detect(null, "こんにちは世界", "en"))
        // Korean
        assertEquals("ko", LanguageDetector.detect(null, "안녕하세요", "en"))
        // Chinese
        assertEquals("zh", LanguageDetector.detect(null, "你好世界", "en"))
        // Arabic
        assertEquals("ar", LanguageDetector.detect(null, "مرحبا كيف حالك", "en"))
        // Greek
        assertEquals("el", LanguageDetector.detect(null, "Γειά σου κόσμε", "en"))
    }

    @Test
    fun testEnglishDetection() {
        assertEquals("en", LanguageDetector.detect(null, "Hello world how are you doing", "es"))
        assertEquals("en", LanguageDetector.detect(null, "Thank you very much for your help", "es"))
    }

    @Test
    fun testTargetCollisionAvoidance() {
        // When text has Spanish words and target is English, should detect Spanish rather than forcing English
        assertEquals("es", LanguageDetector.detect(null, "no problema amigo", "en"))
    }

    @Test
    fun testEmptyInput() {
        assertEquals("auto", LanguageDetector.detect(null, "", "en"))
        assertEquals("auto", LanguageDetector.detect(null, "   ", "en"))
    }
}
