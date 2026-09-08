// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.emoji

import helium314.keyboard.keyboard.Key

/**
 * Interface to handle callbacks from child elements
 * such as Emoji buttons and keyboard views.
 */
interface EmojiViewCallback {
    /**
     * Called when a key is pressed by the user
     */
    fun onPressKey(key: Key)

    /**
     * Called when a key is released.
     * This may be called without any prior call to {@link EmojiViewCallback#onPressKey(Key)},
     * for example when a key from a popup keys keyboard is selected by releasing touch on it.
     */
    fun onReleaseKey(key: Key)

    /**
     * Called from keyboard view to get an emoji description
     */
    fun getDescription(emoji: String): String?
}
