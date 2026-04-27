package helium314.keyboard.latin.utils

import helium314.keyboard.latin.utils.DictionaryInfoUtils.getWordListIdFromFileName
import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryInfoUtilsTest {

    @Test
    fun testGetWordListIdFromFileName() {
        // Happy paths (no encoding)
        assertEquals("en_US", getWordListIdFromFileName("en_US"))
        assertEquals("main_en", getWordListIdFromFileName("main_en"))
        assertEquals("test-dict_123", getWordListIdFromFileName("test-dict_123"))

        // With encoded spaces (space is 0x20)
        assertEquals("hello world", getWordListIdFromFileName("hello%000020world"))
        assertEquals(" ", getWordListIdFromFileName("%000020"))

        // With encoded emoji (U+1F600 Grinning Face)
        // Emoji surrogate pairs are encoded as two separate code units
        assertEquals("\uD83D\uDE00", getWordListIdFromFileName("%00d83d%00de00"))
        assertEquals("emoji \uD83D\uDE00 test", getWordListIdFromFileName("emoji%000020%00d83d%00de00%000020test"))

        // Empty string
        assertEquals("", getWordListIdFromFileName(""))

        // With encoded letters (A is 0x41) - even if not normally encoded, decoding should work
        assertEquals("A", getWordListIdFromFileName("%000041"))
    }
}
