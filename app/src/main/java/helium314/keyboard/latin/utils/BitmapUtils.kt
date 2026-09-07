// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.WorkerThread
import java.io.File
import java.io.FileInputStream

object BitmapUtils {
    private const val DEFAULT_BACKGROUND_MAX_DIM = 2048

    /**
     * Decodes a file into a Bitmap using a two-pass approach with sub-sampling to avoid OOM.
     * Returns null if the file cannot be decoded.
     *
     * @param file the file to decode
     * @param maxDim the maximum width/height of the resulting bitmap; larger images are sub-sampled down
     * @param preferLowConfig if true, prefer RGB_565 (half the memory) when the image has no alpha
     */
    @WorkerThread
    fun decodeSampledBitmap(file: File, maxDim: Int = DEFAULT_BACKGROUND_MAX_DIM, preferLowConfig: Boolean = true): Bitmap? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        try {
            FileInputStream(file).use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (_: Exception) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options()
        opts.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
        if (preferLowConfig && !bounds.outMimeType.equals("image/png", ignoreCase = true)) {
            opts.inPreferredConfig = Bitmap.Config.RGB_565
        }
        return try {
            FileInputStream(file).use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxDim && h / 2 >= maxDim) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }
}
