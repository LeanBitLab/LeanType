// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import helium314.keyboard.latin.settings.Settings

class ClipboardHistoryEntry(
    val id: Long,
    var timeStamp: Long,
    var isPinned: Boolean,
    val text: String,
    val imageUri: String? = null
) : Comparable<ClipboardHistoryEntry> {
    override fun compareTo(other: ClipboardHistoryEntry): Int {
        if (Settings.getValues()?.mClipboardHistoryPinnedFirst != false) {
            val result = other.isPinned.compareTo(isPinned)
            if (result != 0) return result
        }
        return other.timeStamp.compareTo(timeStamp)
    }
}
