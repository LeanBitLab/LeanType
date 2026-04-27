package helium314.keyboard.latin.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class ChecksumCalculatorTest {

    @Test
    fun checksum_emptyInputStream_returnsExpectedHash() {
        val emptyStream = ByteArrayInputStream(ByteArray(0))
        val result = ChecksumCalculator.checksum(emptyStream)
        // SHA-256 for empty string
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", result)
    }

    @Test
    fun checksum_stringInputStream_returnsExpectedHash() {
        val stream = ByteArrayInputStream("hello world".toByteArray())
        val result = ChecksumCalculator.checksum(stream)
        // SHA-256 for "hello world"
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", result)
    }

    @Test
    fun checksum_largeInputStream_returnsExpectedHash() {
        // Create an input larger than 8192 buffer to exercise the while loop
        val largeData = ByteArray(10000) { it.toByte() }
        val stream = ByteArrayInputStream(largeData)
        val result = ChecksumCalculator.checksum(stream)

        // Let's compute the hash separately or use a known one. We can use MessageDigest directly here to verify
        val digester = java.security.MessageDigest.getInstance("SHA-256")
        val expectedDigest = digester.digest(largeData)
        val s = java.lang.StringBuilder()
        for (i in expectedDigest.indices) {
            s.append(String.format("%1$02x", expectedDigest[i]))
        }
        val expected = s.toString()

        assertEquals(expected, result)
    }

    @Test
    fun checksum_file_returnsExpectedHash() {
        val tempFile = Files.createTempFile("test", ".txt").toFile()
        try {
            tempFile.writeText("file content")
            val result = ChecksumCalculator.checksum(tempFile)
            // SHA-256 for "file content"
            assertEquals("e0ac3601005dfa1864f5392aabaf7d898b1b5bab854f1acb4491bcd806b76b0c", result)
        } finally {
            tempFile.delete()
        }
    }
}
