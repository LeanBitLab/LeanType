// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.compat

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build

object ClipboardManagerCompat {

    fun clearPrimaryClip(cm: ClipboardManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                cm.clearPrimaryClip()
            } catch (e: Exception) {
                // workaround for system-caused crash in https://github.com/Helium314/HeliBoard/issues/203
                cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } else {
            cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    fun getClipTimestamp(cd: ClipData): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timestamp = cd.description.timestamp
            if (timestamp > 0) // timestamp is 0 if not set
                return timestamp
        }
        return System.currentTimeMillis()
    }

    fun getClipSensitivity(cd: ClipDescription?): Boolean? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return cd != null && cd.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true
        }
        return null // can't determine
    }
}
