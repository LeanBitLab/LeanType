// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

object FastBlurEngine {

    suspend fun generateBlurredThumbnailAsync(
        source: Bitmap,
        maxThumbnailDim: Int = 200,
        blurPasses: Int = 2,
        radius: Int = 4
    ): Bitmap = withContext(Dispatchers.Default) {
        generateBlurredThumbnail(source, maxThumbnailDim, blurPasses, radius)
    }

    fun generateBlurredThumbnail(
        source: Bitmap,
        maxThumbnailDim: Int = 200,
        blurPasses: Int = 2,
        radius: Int = 4
    ): Bitmap {
        val srcWidth = source.width
        val srcHeight = source.height
        if (srcWidth <= 0 || srcHeight <= 0) return source

        return try {
            // 1. Progressive downscale to small thumbnail (~160-200px max dimension)
            val scale = maxThumbnailDim.toFloat() / max(srcWidth, srcHeight).coerceAtLeast(1)
            val targetWidth = max(1, (srcWidth * scale).toInt())
            val targetHeight = max(1, (srcHeight * scale).toInt())

            val smallBitmap = progressiveDownscale(source, targetWidth, targetHeight)

            // 2. Sliding-window box blur passes (2-3 passes approximate Gaussian)
            var current = smallBitmap
            val passes = blurPasses.coerceIn(1, 4)
            val blurRadius = radius.coerceIn(1, 15)
            repeat(passes) {
                val next = slidingBoxBlur(current, blurRadius)
                if (current != smallBitmap && current != source) {
                    current.recycle()
                }
                current = next
            }
            if (smallBitmap != current && smallBitmap != source) {
                smallBitmap.recycle()
            }

            current
        } catch (e: Throwable) {
            Log.w("FastBlurEngine", "OOM or error generating blurred thumbnail", e)
            source
        }
    }

    private fun progressiveDownscale(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
        var current = source
        while (current.width > targetW * 2 && current.height > targetH * 2) {
            val next = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
            if (current != source) current.recycle()
            current = next
        }
        return Bitmap.createScaledBitmap(current, targetW, targetH, true)
    }

    private fun slidingBoxBlur(source: Bitmap, radius: Int): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val temp = IntArray(w * h)

        // Horizontal sliding-window pass (O(1) per pixel)
        val divH = radius * 2 + 1
        for (y in 0 until h) {
            val rowOffset = y * w
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0

            val firstPx = pixels[rowOffset]
            val a0 = (firstPx ushr 24) and 0xFF
            val r0 = (firstPx ushr 16) and 0xFF
            val g0 = (firstPx ushr 8) and 0xFF
            val b0 = firstPx and 0xFF

            sumA = a0 * radius
            sumR = r0 * radius
            sumG = g0 * radius
            sumB = b0 * radius

            for (i in 0..radius) {
                val px = pixels[rowOffset + i.coerceAtMost(w - 1)]
                sumA += (px ushr 24) and 0xFF
                sumR += (px ushr 16) and 0xFF
                sumG += (px ushr 8) and 0xFF
                sumB += px and 0xFF
            }

            for (x in 0 until w) {
                temp[rowOffset + x] = ((sumA / divH) shl 24) or
                        ((sumR / divH) shl 16) or
                        ((sumG / divH) shl 8) or
                        (sumB / divH)

                val leftPx = pixels[rowOffset + (x - radius).coerceAtLeast(0)]
                val rightPx = pixels[rowOffset + (x + radius + 1).coerceAtMost(w - 1)]

                sumA += ((rightPx ushr 24) and 0xFF) - ((leftPx ushr 24) and 0xFF)
                sumR += ((rightPx ushr 16) and 0xFF) - ((leftPx ushr 16) and 0xFF)
                sumG += ((rightPx ushr 8) and 0xFF) - ((leftPx ushr 8) and 0xFF)
                sumB += (rightPx and 0xFF) - (leftPx and 0xFF)
            }
        }

        // Vertical sliding-window pass (O(1) per pixel)
        val outPixels = IntArray(w * h)
        val divV = radius * 2 + 1
        for (x in 0 until w) {
            var sumA = 0
            var sumR = 0
            var sumG = 0
            var sumB = 0

            val firstPx = temp[x]
            val a0 = (firstPx ushr 24) and 0xFF
            val r0 = (firstPx ushr 16) and 0xFF
            val g0 = (firstPx ushr 8) and 0xFF
            val b0 = firstPx and 0xFF

            sumA = a0 * radius
            sumR = r0 * radius
            sumG = g0 * radius
            sumB = b0 * radius

            for (i in 0..radius) {
                val px = temp[i.coerceAtMost(h - 1) * w + x]
                sumA += (px ushr 24) and 0xFF
                sumR += (px ushr 16) and 0xFF
                sumG += (px ushr 8) and 0xFF
                sumB += px and 0xFF
            }

            for (y in 0 until h) {
                outPixels[y * w + x] = ((sumA / divV) shl 24) or
                        ((sumR / divV) shl 16) or
                        ((sumG / divV) shl 8) or
                        (sumB / divV)

                val topPx = temp[(y - radius).coerceAtLeast(0) * w + x]
                val btmPx = temp[(y + radius + 1).coerceAtMost(h - 1) * w + x]

                sumA += ((btmPx ushr 24) and 0xFF) - ((topPx ushr 24) and 0xFF)
                sumR += ((btmPx ushr 16) and 0xFF) - ((topPx ushr 16) and 0xFF)
                sumG += ((btmPx ushr 8) and 0xFF) - ((topPx ushr 8) and 0xFF)
                sumB += (btmPx and 0xFF) - (topPx and 0xFF)
            }
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(outPixels, 0, w, 0, 0, w, h)
        return output
    }
}
