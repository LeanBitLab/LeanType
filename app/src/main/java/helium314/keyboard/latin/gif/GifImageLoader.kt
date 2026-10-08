package helium314.keyboard.latin.gif

import android.content.Context
import coil.ImageLoader
import coil.memory.MemoryCache

object GifImageLoader {
    @Volatile
    private var loader: ImageLoader? = null

    fun get(context: Context): ImageLoader = loader ?: synchronized(this) {
        loader ?: ImageLoader.Builder(context.applicationContext)
            .memoryCache {
                MemoryCache.Builder(context.applicationContext)
                    .maxSizeBytes(8 * 1024 * 1024) // 8 MB cap to protect IME process memory
                    .build()
            }
            .crossfade(false)
            .allowHardware(true)
            .build()
            .also { loader = it }
    }

    fun clearMemory() {
        loader?.memoryCache?.clear()
    }
}
