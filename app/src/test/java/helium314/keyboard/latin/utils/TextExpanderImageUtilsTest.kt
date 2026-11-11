/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.utils

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TextExpanderImageUtilsTest {

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        TextExpanderUtils.clearCache()
        TextExpanderImageUtils.clearCache()
        TextExpanderImageUtils.getImagesDir(context).listFiles()?.forEach { it.delete() }
        TextExpanderUtils.saveShortcuts(context, emptyMap())
    }

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

    @Test
    fun testResolveImageFileDirectAndManagedFallback() {
        val context = RuntimeEnvironment.getApplication()
        val managedDir = TextExpanderImageUtils.getImagesDir(context)
        val managedFile = File(managedDir, "img_12345_abcd1234.png").apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        }

        assertEquals(managedFile.canonicalPath, TextExpanderImageUtils.resolveImageFile(context, managedFile.absolutePath)?.canonicalPath)
        assertTrue(TextExpanderImageUtils.imageExists(context, managedFile.absolutePath))

        val crossFlavorPath = "/data/user/0/helium314.keyboard.offline/files/text_expander_images/img_12345_abcd1234.png"
        assertEquals(managedFile.canonicalPath, TextExpanderImageUtils.resolveImageFile(context, crossFlavorPath)?.canonicalPath)
        assertTrue(TextExpanderImageUtils.imageExists(context, crossFlavorPath))

        val fileUri = "file://$crossFlavorPath"
        assertEquals(managedFile.canonicalPath, TextExpanderImageUtils.resolveImageFile(context, fileUri)?.canonicalPath)

        assertNull(TextExpanderImageUtils.resolveImageFile(context, "/data/user/0/app/files/text_expander_images/img_missing.png"))
        assertFalse(TextExpanderImageUtils.imageExists(context, "/data/user/0/app/files/text_expander_images/img_missing.png"))
        assertNull(TextExpanderImageUtils.resolveImageFile(context, "/data/user/0/app/other_dir/img_12345_abcd1234.png"))
    }

    @Test
    fun testCleanUpOrphanedImagesRespectsGracePeriodAndManagedFallback() {
        val context = RuntimeEnvironment.getApplication()
        val managedDir = TextExpanderImageUtils.getImagesDir(context)
        val now = 1_700_000_000_000L

        val referencedFile = File(managedDir, "img_keep.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        referencedFile.setLastModified(now - TextExpanderImageUtils.ORPHAN_GRACE_PERIOD_MS - 60_000L)

        val recentOrphan = File(managedDir, "img_recent.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        recentOrphan.setLastModified(now - 30_000L)

        val expiredOrphan = File(managedDir, "img_expired.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        expiredOrphan.setLastModified(now - TextExpanderImageUtils.ORPHAN_GRACE_PERIOD_MS - 60_000L)

        TextExpanderUtils.saveShortcuts(
            context,
            mapOf("pic" to TextExpanderUtils.ShortcutEntry(
                template = "",
                prefix = "",
                imageUri = "/data/user/0/helium314.keyboard.offline/files/text_expander_images/img_keep.png"
            ))
        )

        TextExpanderImageUtils.cleanUpOrphanedImages(context, nowMs = now)

        assertTrue(referencedFile.exists())
        assertTrue(recentOrphan.exists())
        assertFalse(expiredOrphan.exists())
    }

    @Test
    fun testGetThumbnailDrawableDoesNotRecycleCachedBitmap() {
        val context = RuntimeEnvironment.getApplication()
        val managedDir = TextExpanderImageUtils.getImagesDir(context)
        val imageFile = File(managedDir, "img_cache_test.png")
        val source = Bitmap.createBitmap(100, 40, Bitmap.Config.ARGB_8888)
        FileOutputStream(imageFile).use { out ->
            source.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        source.recycle()

        val cachedBitmap = TextExpanderImageUtils.loadThumbnailBitmap(context, imageFile.absolutePath, 80, 52)
        assertNotNull(cachedBitmap)
        assertFalse(cachedBitmap!!.isRecycled)

        val drawable = TextExpanderImageUtils.getThumbnailDrawable(context, imageFile.absolutePath, 40, 26)
        assertNotNull(drawable)
        assertTrue(drawable is BitmapDrawable)
        assertFalse((drawable as BitmapDrawable).bitmap.isRecycled)
        assertFalse(cachedBitmap.isRecycled)
    }
}
