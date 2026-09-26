/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.event

import android.view.KeyCharacterMap
import android.view.KeyEvent
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants

/**
 * A hardware event decoder for a hardware qwerty-ish keyboard.
 *
 * The events are always hardware keypresses, but they can be key down or key up events, they
 * can be dead keys, they can be meta keys like shift or ctrl... This does not deal with
 * 10-key like keyboards; a different decoder is used for this.
 */
class HardwareKeyboardEventDecoder(val mDeviceId: Int) : HardwareEventDecoder {
    override fun decodeHardwareKey(keyEvent: KeyEvent, layoutName: String?): Event {
        val keyCode = keyEvent.keyCode
        val metaState = keyEvent.metaState
        val isKeyRepeat = 0 != keyEvent.repeatCount

        // Resolve character via active physical keyboard layout if configured
        val mappedUnicode = if (layoutName != null) {
            PhysicalKeyboardLayouts.mapHardwareKey(keyCode, metaState, layoutName)
        } else null

        // KeyEvent#getUnicodeChar() does not exactly return a unicode char, but rather a value
        // that includes both the unicode char in the lower 21 bits and flags in the upper bits,
        // hence the name "codePointAndFlags". {@see KeyEvent#getUnicodeChar()} for more info.
        // For numpad keys, if Android has NumLock off in metaState, resolve using META_NUM_LOCK_ON
        // or the key's numeric label so numpad typing works consistently across all devices.
        val rawUnicode = mappedUnicode
            ?: keyEvent.unicodeChar.takeIf { it != 0 }
            ?: if (isNumpadKey(keyCode)) {
                keyEvent.getUnicodeChar(metaState or KeyEvent.META_NUM_LOCK_ON).takeIf { it != 0 }
                    ?: keyEvent.number.takeIf { it != 0.toChar() }?.code
            } else null
        val codePointAndFlags = rawUnicode ?: Event.NOT_A_CODE_POINT

        return if (KeyEvent.KEYCODE_DEL == keyCode) {
            Event.createHardwareKeypressEvent(Event.NOT_A_CODE_POINT, KeyCode.DELETE, metaState, null, isKeyRepeat)
        } else if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            // The Enter key. If the Shift key is not being pressed, this should send a
            // CODE_ENTER to trigger the action if any, or a carriage return otherwise. If the
            // Shift key is being pressed, this should send a CODE_SHIFT_ENTER and let
            // Latin IME decide what to do with it.
            if (keyEvent.isShiftPressed) {
                Event.createHardwareKeypressEvent(Event.NOT_A_CODE_POINT,
                        KeyCode.SHIFT_ENTER, 0, null, isKeyRepeat)
            } else Event.createHardwareKeypressEvent(Constants.CODE_ENTER, keyCode, metaState, null, isKeyRepeat)
        } else if (keyCode == KeyEvent.KEYCODE_SPACE) {
            if (keyEvent.isCtrlPressed) {
                Event.createHardwareKeypressEvent(Event.NOT_A_CODE_POINT, KeyCode.LANGUAGE_SWITCH, 0, null, isKeyRepeat)
            } else {
                val spaceMeta = metaState and (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_RIGHT_ON).inv()
                Event.createHardwareKeypressEvent(Constants.CODE_SPACE, keyCode, spaceMeta, null, isKeyRepeat)
            }
        } else if (codePointAndFlags != Event.NOT_A_CODE_POINT) {
            if (0 != codePointAndFlags and KeyCharacterMap.COMBINING_ACCENT) { // A dead key.
                val deadCodePoint = codePointAndFlags and KeyCharacterMap.COMBINING_ACCENT_MASK
                if (Character.isValidCodePoint(deadCodePoint)) {
                    Event.createDeadEvent(deadCodePoint, keyCode, metaState, null)
                } else {
                    Event.notHandledEvent
                }
            } else {
                Event.createHardwareKeypressEvent(codePointAndFlags, keyCode, metaState, null, isKeyRepeat)
            }
        } else if (isDpadDirection(keyCode)) {
            Event.createHardwareKeypressEvent(codePointAndFlags, keyCode, metaState, null, isKeyRepeat)
        } else {
            Event.notHandledEvent
        }
    }

    companion object {
        private fun isDpadDirection(keyCode: Int) = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT, KeyEvent.KEYCODE_DPAD_DOWN_RIGHT, KeyEvent.KEYCODE_DPAD_UP_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP_LEFT -> true
            else -> false
        }

        private fun isNumpadKey(keyCode: Int) = when (keyCode) {
            KeyEvent.KEYCODE_NUMPAD_0,
            KeyEvent.KEYCODE_NUMPAD_1,
            KeyEvent.KEYCODE_NUMPAD_2,
            KeyEvent.KEYCODE_NUMPAD_3,
            KeyEvent.KEYCODE_NUMPAD_4,
            KeyEvent.KEYCODE_NUMPAD_5,
            KeyEvent.KEYCODE_NUMPAD_6,
            KeyEvent.KEYCODE_NUMPAD_7,
            KeyEvent.KEYCODE_NUMPAD_8,
            KeyEvent.KEYCODE_NUMPAD_9,
            KeyEvent.KEYCODE_NUMPAD_DOT,
            KeyEvent.KEYCODE_NUMPAD_COMMA,
            KeyEvent.KEYCODE_NUMPAD_DIVIDE,
            KeyEvent.KEYCODE_NUMPAD_MULTIPLY,
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT,
            KeyEvent.KEYCODE_NUMPAD_ADD,
            KeyEvent.KEYCODE_NUMPAD_EQUALS,
            KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN,
            KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN -> true
            else -> false
        }
    }
}
