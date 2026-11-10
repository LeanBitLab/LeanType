/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextExpanderImageUtilsTest {

    @Test
    fun testSniffExtensionJpg() {
        val jpgHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        assertEquals(".jpg", TextExpanderImageUtils.sniffExtension(jpgHeader, null))
    }

    @Test
    fun testSniffExtensionPng() {
        val pngHeader = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        )
        assertEquals(".png", TextExpanderImageUtils.sniffExtension(pngHeader, null))
    }

    @Test
    fun testSniffExtensionWebp() {
        val webpHeader = byteArrayOf(
            0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte(), // RIFF
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte()  // WEBP
        )
        assertEquals(".webp", TextExpanderImageUtils.sniffExtension(webpHeader, null))
    }

    @Test
    fun testSniffExtensionGif() {
        val gifHeader = byteArrayOf(0x47.toByte(), 0x49.toByte(), 0x46.toByte())
        assertEquals(".gif", TextExpanderImageUtils.sniffExtension(gifHeader, null))
    }

    @Test
    fun testGetMimeType() {
        assertEquals("image/jpeg", TextExpanderImageUtils.getMimeType("/path/to/photo.jpg"))
        assertEquals("image/jpeg", TextExpanderImageUtils.getMimeType("/path/to/photo.jpeg"))
        assertEquals("image/png", TextExpanderImageUtils.getMimeType("/path/to/diagram.png"))
        assertEquals("image/webp", TextExpanderImageUtils.getMimeType("/path/to/image.webp"))
        assertEquals("image/gif", TextExpanderImageUtils.getMimeType("/path/to/animation.gif"))
    }

    @Test
    fun testIsImageShortcut() {
        val textEntry = TextExpanderUtils.ShortcutEntry(template = "Hello world", prefix = "")
        assertFalse(TextExpanderImageUtils.isImageShortcut(textEntry))

        val imageEntry = TextExpanderUtils.ShortcutEntry(
            template = "Tag photo",
            prefix = "",
            imageUri = "/data/user/0/app/files/text_expander_images/img_123.jpg"
        )
        assertTrue(TextExpanderImageUtils.isImageShortcut(imageEntry))
    }
}
