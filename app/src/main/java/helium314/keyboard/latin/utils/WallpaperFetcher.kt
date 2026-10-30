// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

object WallpaperFetcher {

    @SuppressLint("MissingPermission")
    fun getSystemWallpaper(context: Context): Bitmap? {
        return try {
            val wm = WallpaperManager.getInstance(context)
            val drawable = wm.drawable ?: return null
            drawableToBitmap(drawable)
        } catch (e: SecurityException) {
            Log.w("WallpaperFetcher", "System wallpaper access restricted: ${e.message}")
            null
        } catch (e: Throwable) {
            Log.w("WallpaperFetcher", "Failed to retrieve system wallpaper: ${e.message}")
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1080
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1920
        return try {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            bitmap
        } catch (e: OutOfMemoryError) {
            Log.w("WallpaperFetcher", "OutOfMemoryError converting wallpaper drawable to bitmap")
            null
        } catch (e: Exception) {
            Log.w("WallpaperFetcher", "Error converting wallpaper drawable to bitmap: ${e.message}")
            null
        }
    }
}
