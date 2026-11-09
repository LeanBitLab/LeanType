package helium314.keyboard.compat

import android.os.Build
import java.net.URLConnection

fun URLConnection.contentLengthLongCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        contentLengthLong
    } else {
        getHeaderField("content-length")?.toLongOrNull() ?: contentLength.toLong()
    }
