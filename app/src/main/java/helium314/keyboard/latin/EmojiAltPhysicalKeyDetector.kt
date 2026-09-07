/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin

import android.content.res.Resources
import android.util.Pair
import android.view.KeyEvent
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log

/**
 * A class for detecting Emoji-Alt physical key.
 */
class EmojiAltPhysicalKeyDetector(resources: Resources) {

    private val mHotKeysList: MutableList<EmojiHotKeys> = ArrayList()

    private class HotKeySet : HashSet<Pair<Int, Int>>()

    private abstract inner class EmojiHotKeys(private val mName: String, private val mKeySet: HotKeySet) {
        var mCanFire = false
            private set
        var mMetaState = 0
            private set

        fun onKeyDown(keyEvent: KeyEvent) {
            if (DEBUG) Log.d(TAG, "EmojiHotKeys.onKeyDown() - $mName - considering $keyEvent")

            val key = Pair.create(keyEvent.keyCode, keyEvent.metaState)
            if (mKeySet.contains(key)) {
                if (DEBUG) Log.d(TAG, "EmojiHotKeys.onKeyDown() - $mName - enabling action")
                mCanFire = true
                mMetaState = keyEvent.metaState
            } else if (mCanFire) {
                if (DEBUG) Log.d(TAG, "EmojiHotKeys.onKeyDown() - $mName - disabling action")
                mCanFire = false
            }
        }

        fun onKeyUp(keyEvent: KeyEvent) {
            if (DEBUG) Log.d(TAG, "EmojiHotKeys.onKeyUp() - $mName - considering $keyEvent")

            val keyCode = keyEvent.keyCode
            var metaState = keyEvent.metaState
            if (KeyEvent.isModifierKey(keyCode)) {
                // Try restoring meta state in case the released key was a modifier.
                metaState = metaState or mMetaState
            }

            val key = Pair.create(keyCode, metaState)
            if (mKeySet.contains(key)) {
                if (mCanFire) {
                    if (!keyEvent.isCanceled) {
                        if (DEBUG) Log.d(TAG, "EmojiHotKeys.onKeyUp() - $mName - firing action")
                        action()
                    } else {
                        if (DEBUG) Log.d(TAG, "EmojiHotKeys.onKeyUp() - $mName - canceled, ignoring action")
                    }
                    mCanFire = false
                }
            }

            if (mCanFire) {
                if (DEBUG) Log.d(TAG, "EmojiHotKeys.onKeyUp() - $mName - disabling action")
                mCanFire = false
            }
        }

        protected abstract fun action()
    }

    init {
        val emojiSwitchSet = parseHotKeys(resources, R.array.keyboard_switcher_emoji)
        val emojiHotKeys = object : EmojiHotKeys("emoji", emojiSwitchSet) {
            override fun action() {
                val switcher = KeyboardSwitcher.getInstance()
                switcher.onToggleKeyboard(KeyboardSwitcher.KeyboardSwitchState.EMOJI)
            }
        }
        mHotKeysList.add(emojiHotKeys)

        val symbolsSwitchSet = parseHotKeys(resources, R.array.keyboard_switcher_symbols_shifted)
        val symbolsHotKeys = object : EmojiHotKeys("symbols", symbolsSwitchSet) {
            override fun action() {
                val switcher = KeyboardSwitcher.getInstance()
                switcher.onToggleKeyboard(KeyboardSwitcher.KeyboardSwitchState.SYMBOLS_SHIFTED)
            }
        }
        mHotKeysList.add(symbolsHotKeys)
    }

    fun onKeyDown(keyEvent: KeyEvent) {
        if (DEBUG) Log.d(TAG, "onKeyDown(): $keyEvent")
        if (shouldProcessEvent()) {
            for (hotKeys in mHotKeysList) {
                hotKeys.onKeyDown(keyEvent)
            }
        }
    }

    fun onKeyUp(keyEvent: KeyEvent) {
        if (DEBUG) Log.d(TAG, "onKeyUp(): $keyEvent")
        if (shouldProcessEvent()) {
            for (hotKeys in mHotKeysList) {
                hotKeys.onKeyUp(keyEvent)
            }
        }
    }

    companion object {
        private const val TAG = "EmojiAltPhysKeyDetector"
        private const val DEBUG = false

        private fun shouldProcessEvent(): Boolean {
            if (!Settings.getValues().mEnableEmojiAltPhysicalKey) {
                if (DEBUG) Log.d(TAG, "shouldProcessEvent(): Disabled")
                return false
            }
            return true
        }

        private fun parseHotKeys(resources: Resources, resourceId: Int): HotKeySet {
            val keySet = HotKeySet()
            val name = resources.getResourceEntryName(resourceId)
            val values = resources.getStringArray(resourceId)
            if (values != null) {
                for (i in values.indices) {
                    val valuePair = values[i].split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (valuePair.size != 2) {
                        Log.w(TAG, "Expected 2 integers in $name[$i] : ${values[i]}")
                    }
                    try {
                        val keyCode = valuePair[0].toInt()
                        val metaState = valuePair[1].toInt()
                        val key = Pair.create(keyCode, KeyEvent.normalizeMetaState(metaState))
                        keySet.add(key)
                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "Failed to parse $name[$i] : ${values[i]}", e)
                    }
                }
            }
            return keySet
        }
    }
}
