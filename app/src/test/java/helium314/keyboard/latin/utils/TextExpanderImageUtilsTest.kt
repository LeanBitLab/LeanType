/*
 * Copyright (C) 2026 LeanBitLab
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.utils

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

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

    @Test
    fun testSniffExtensionHeicAvifBmp() {
        val bmpHeader = byteArrayOf(0x42, 0x4D, 0x00, 0x00)
        assertEquals(".bmp", TextExpanderImageUtils.sniffExtension(bmpHeader, null))

        val heicHeader = byteArrayOf(
            0x00, 0x00, 0x00, 0x18,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'h'.code.toByte(), 'e'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte()
        )
        assertEquals(".heic", TextExpanderImageUtils.sniffExtension(heicHeader, null))

        val avifHeader = byteArrayOf(
            0x00, 0x00, 0x00, 0x1C,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'a'.code.toByte(), 'v'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte()
        )
        assertEquals(".avif", TextExpanderImageUtils.sniffExtension(avifHeader, null))
    }

    @Test
    fun testSaveImageWithResultPortableReferenceAndErrors() {
        val context = RuntimeEnvironment.getApplication()
        val shadowResolver = Shadows.shadowOf(context.contentResolver)

        // Valid PNG content URI -> portable bare filename (no '/')
        val tempPng = File(context.cacheDir, "input_sample.png")
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        FileOutputStream(tempPng).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val pngBytes = tempPng.readBytes()
        tempPng.delete()

        val pngUri = Uri.parse("content://test.provider/sample.png")
        shadowResolver.registerInputStream(pngUri, ByteArrayInputStream(pngBytes))
        val pngResult = TextExpanderImageUtils.saveImageWithResult(context, pngUri)
        assertTrue(pngResult is TextExpanderImageUtils.SaveImageResult.Success)
        val ref = (pngResult as TextExpanderImageUtils.SaveImageResult.Success).reference
        assertFalse(ref.contains("/"))
        assertTrue(TextExpanderImageUtils.resolveImageFile(context, ref)?.exists() == true)

        // Valid minimal 1x1 24-bit BMP content URI -> normalized portable image
        val bmpBytes = byteArrayOf(
            0x42, 0x4D, 0x3A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x36, 0x00, 0x00, 0x00,
            0x28, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00,
            0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x13, 0x0B, 0x00, 0x00,
            0x13, 0x0B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0xFF.toByte(), 0x00, 0x00, 0x00
        )
        val bmpUri = Uri.parse("content://test.provider/sample.bmp")
        shadowResolver.registerInputStream(bmpUri, ByteArrayInputStream(bmpBytes))
        val bmpResult = TextExpanderImageUtils.saveImageWithResult(context, bmpUri)
        assertTrue(bmpResult is TextExpanderImageUtils.SaveImageResult.Success)
        val bmpRef = (bmpResult as TextExpanderImageUtils.SaveImageResult.Success).reference
        assertFalse(bmpRef.contains("/"))
        assertTrue(bmpRef.endsWith(".png") || bmpRef.endsWith(".jpg"))

        // >20MB stream -> TooLarge without OOM
        val largeUri = Uri.parse("content://test.provider/huge.png")
        shadowResolver.registerInputStream(largeUri, object : InputStream() {
            private var remaining = 21L * 1024L * 1024L
            private var headerPos = 0
            private val header = byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
            )
            override fun read(): Int {
                if (headerPos < header.size) {
                    remaining--
                    return header[headerPos++].toInt() and 0xFF
                }
                if (remaining <= 0) return -1
                remaining--
                return 0
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                if (headerPos < header.size) {
                    val toCopy = minOf(len, header.size - headerPos)
                    System.arraycopy(header, headerPos, b, off, toCopy)
                    headerPos += toCopy
                    remaining -= toCopy
                    return toCopy
                }
                val chunk = minOf(len.toLong(), remaining).toInt()
                java.util.Arrays.fill(b, off, off + chunk, 0.toByte())
                remaining -= chunk
                return chunk
            }
        })
        assertEquals(
            TextExpanderImageUtils.SaveImageResult.TooLarge,
            TextExpanderImageUtils.saveImageWithResult(context, largeUri)
        )

        // Too-short / empty stream and unreadable URI -> InvalidOrUnreadable, with temp file cleaned up
        val tinyUri = Uri.parse("content://test.provider/tiny.bin")
        shadowResolver.registerInputStream(tinyUri, ByteArrayInputStream(byteArrayOf(1, 2)))
        assertEquals(
            TextExpanderImageUtils.SaveImageResult.InvalidOrUnreadable,
            TextExpanderImageUtils.saveImageWithResult(context, tinyUri)
        )
        val missingUri = Uri.parse("content://test.provider/missing.png")
        assertEquals(
            TextExpanderImageUtils.SaveImageResult.InvalidOrUnreadable,
            TextExpanderImageUtils.saveImageWithResult(context, missingUri)
        )
        assertFalse(
            TextExpanderImageUtils.getImagesDir(context).listFiles()?.any { it.name.startsWith("pending_") } == true
        )
    }

    @Test
    fun testTranscodeForEditorCompat() {
        val context = RuntimeEnvironment.getApplication()
        val managedDir = TextExpanderImageUtils.getImagesDir(context)

        val pngFile = File(managedDir, "compat_source.png")
        val pngBitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        FileOutputStream(pngFile).use { pngBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        pngBitmap.recycle()

        val jpegCompat = TextExpanderImageUtils.transcodeForEditorCompat(
            context,
            pngFile,
            "image/png",
            arrayOf("image/jpeg")
        )
        assertNotNull(jpegCompat)
        assertEquals("image/jpeg", jpegCompat!!.second)
        assertTrue(jpegCompat.first.exists())
        assertTrue(jpegCompat.first.length() > 0L)

        val jpgFile = File(managedDir, "compat_source.jpg")
        val jpgBitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        FileOutputStream(jpgFile).use { jpgBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        jpgBitmap.recycle()

        val pngCompat = TextExpanderImageUtils.transcodeForEditorCompat(
            context,
            jpgFile,
            "image/jpeg",
            arrayOf("image/png")
        )
        assertNotNull(pngCompat)
        assertEquals("image/png", pngCompat!!.second)
        assertTrue(pngCompat.first.exists())
        assertTrue(pngCompat.first.length() > 0L)
    }

    @Test
    fun testCleanUpOrphanedImagesPreservesPreviousBackupReferences() {
        val context = RuntimeEnvironment.getApplication()
        val managedDir = TextExpanderImageUtils.getImagesDir(context)
        val now = 1_700_000_000_000L

        val backupReferenced = File(managedDir, "img_backup_keep.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        backupReferenced.setLastModified(now - TextExpanderImageUtils.ORPHAN_GRACE_PERIOD_MS - 60_000L)

        val expiredOrphan = File(managedDir, "img_orphan_drop.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        expiredOrphan.setLastModified(now - TextExpanderImageUtils.ORPHAN_GRACE_PERIOD_MS - 60_000L)

        TextExpanderUtils.saveShortcuts(
            context,
            mapOf("bak" to TextExpanderUtils.ShortcutEntry(template = "", prefix = "", imageUri = backupReferenced.name))
        )
        val previousJson = context.prefs().getString(TextExpanderUtils.PREF_DATA, "")
        context.prefs().edit().putString(TextExpanderUtils.PREF_DATA_PREVIOUS, previousJson).commit()
        TextExpanderUtils.saveShortcuts(context, emptyMap())

        TextExpanderImageUtils.cleanUpOrphanedImages(context, nowMs = now)

        assertTrue(backupReferenced.exists())
        assertFalse(expiredOrphan.exists())
        context.prefs().edit().remove(TextExpanderUtils.PREF_DATA_PREVIOUS).commit()
    }

    @Test
    fun testDeleteImageIfUnreferencedRespectsActiveAndPreviousReferences() {
        val context = RuntimeEnvironment.getApplication()
        val managedDir = TextExpanderImageUtils.getImagesDir(context)

        val sharedFile = File(managedDir, "img_shared.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val backupOnlyFile = File(managedDir, "img_backup_only.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val entryB = TextExpanderUtils.ShortcutEntry(template = "", prefix = "", imageUri = sharedFile.name)
        TextExpanderImageUtils.deleteImageIfUnreferenced(context, sharedFile.name, mapOf("b" to entryB))
        assertTrue(sharedFile.exists())

        TextExpanderUtils.saveShortcuts(
            context,
            mapOf("prev" to TextExpanderUtils.ShortcutEntry(template = "", prefix = "", imageUri = backupOnlyFile.name))
        )
        val previousJson = context.prefs().getString(TextExpanderUtils.PREF_DATA, "")
        context.prefs().edit().putString(TextExpanderUtils.PREF_DATA_PREVIOUS, previousJson).commit()
        TextExpanderUtils.saveShortcuts(context, emptyMap())

        TextExpanderImageUtils.deleteImageIfUnreferenced(context, backupOnlyFile.name, emptyMap())
        assertTrue(backupOnlyFile.exists())

        context.prefs().edit().remove(TextExpanderUtils.PREF_DATA_PREVIOUS).commit()
        TextExpanderImageUtils.deleteImageIfUnreferenced(context, sharedFile.name, emptyMap())
        TextExpanderImageUtils.deleteImageIfUnreferenced(context, backupOnlyFile.name, emptyMap())
        assertFalse(sharedFile.exists())
        assertFalse(backupOnlyFile.exists())
    }
}
