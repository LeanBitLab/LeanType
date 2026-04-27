package helium314.keyboard.latin.utils

import com.android.inputmethod.latin.utils.BinaryDictionaryUtils
import helium314.keyboard.latin.makedict.UnsupportedFormatException
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import org.mockito.Mockito
import org.junit.Before
import org.junit.After
import org.mockito.MockedStatic

class DictionaryInfoUtilsTest {

    private lateinit var mockedLog: MockedStatic<helium314.keyboard.latin.utils.Log>
    private lateinit var mockedBinaryDictionaryUtils: MockedStatic<BinaryDictionaryUtils>

    @Before
    fun setUp() {
        mockedLog = Mockito.mockStatic(helium314.keyboard.latin.utils.Log::class.java)
        mockedBinaryDictionaryUtils = Mockito.mockStatic(BinaryDictionaryUtils::class.java)
    }

    @After
    fun tearDown() {
        mockedLog.close()
        mockedBinaryDictionaryUtils.close()
    }

    @Test
    fun testGetDictionaryFileHeaderOrNull_ioException_returnsNull() {
        val mockFile = Mockito.mock(File::class.java)

        mockedBinaryDictionaryUtils.`when`<Any> { BinaryDictionaryUtils.getHeader(mockFile) }
            .thenThrow(java.io.IOException::class.java)

        val result = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(mockFile)
        assertNull(result)
    }

    @Test
    fun testGetDictionaryFileHeaderOrNull_unsupportedFormatException_returnsNull() {
        val mockFile = Mockito.mock(File::class.java)

        mockedBinaryDictionaryUtils.`when`<Any> { BinaryDictionaryUtils.getHeader(mockFile) }
            .thenThrow(UnsupportedFormatException::class.java)

        val result = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(mockFile)
        assertNull(result)
    }
}
