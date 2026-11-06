// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal

import android.text.TextUtils
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.utils.RecapitalizeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeyboardStateShiftTest {
    private var doubleTapTimerRunning = false

    private val switchActions = object : KeyboardState.SwitchActions {
        override fun setAlphabetKeyboard() {}
        override fun setAlphabetManualShiftedKeyboard() {}
        override fun setAlphabetAutomaticShiftedKeyboard() {}
        override fun setAlphabetShiftLockedKeyboard() {}
        override fun setAlphabetShiftLockShiftedKeyboard() {}
        override fun setEmojiKeyboard() {}
        override fun setClipboardKeyboard() {}
        override fun setNumpadKeyboard() {}
        override fun toggleNumpad(withSliding: Boolean, autoCapsFlags: Int, recapitalizeMode: RecapitalizeMode?, forceReturnToAlpha: Boolean) {}
        override fun setSymbolsKeyboard() {}
        override fun setSymbolsShiftedKeyboard() {}
        override fun setCustomKeyboard(customIndex: Int) {}
        override fun requestUpdatingShiftState(autoCapsFlags: Int, recapitalizeMode: RecapitalizeMode?) {
            keyboardState.onUpdateShiftState(autoCapsFlags, recapitalizeMode)
        }
        override fun startDoubleTapShiftKeyTimer() { doubleTapTimerRunning = true }
        override val isInDoubleTapShiftKeyTimeout: Boolean get() = doubleTapTimerRunning
        override fun cancelDoubleTapShiftKeyTimer() { doubleTapTimerRunning = false }
        override fun setOneHandedModeEnabled(enabled: Boolean) {}
        override fun switchOneHandedMode() {}
        override fun toggleFloatingKeyboard() {}
    }

    private lateinit var keyboardState: KeyboardState

    @Before
    fun setUp() {
        doubleTapTimerRunning = false
        keyboardState = KeyboardState(switchActions)
        keyboardState.onLoadKeyboard(Constants.TextUtils.CAP_MODE_OFF, null, false)
    }

    @Test
    fun testSingleTapShiftClearedOnInputRestart() {
        // Single tap Shift key
        keyboardState.onPressKey(KeyCode.SHIFT, true, Constants.TextUtils.CAP_MODE_OFF, null)
        keyboardState.onReleaseKey(KeyCode.SHIFT, false, Constants.TextUtils.CAP_MODE_OFF, null)

        assertTrue("Alphabet should be shifted after single tap Shift", keyboardState.isAlphabetShifted)
        assertFalse("Alphabet should NOT be shift-locked after single tap Shift", keyboardState.isAlphabetShiftLocked)
        assertTrue("Double-tap timer should be running", doubleTapTimerRunning)

        // Clear transient shift state (as on input restart)
        keyboardState.clearTransientShiftState()

        assertFalse("Alphabet should be unshifted after clearing transient shift", keyboardState.isAlphabetShifted)
        assertFalse("Alphabet should remain unlocked", keyboardState.isAlphabetShiftLocked)
        assertFalse("Double-tap timer should be cancelled", doubleTapTimerRunning)
    }

    @Test
    fun testDoubleTapShiftLockPreservedOnInputRestart() {
        // Double tap Shift key to lock Caps Lock
        keyboardState.onPressKey(KeyCode.SHIFT, true, Constants.TextUtils.CAP_MODE_OFF, null)
        keyboardState.onReleaseKey(KeyCode.SHIFT, false, Constants.TextUtils.CAP_MODE_OFF, null)
        keyboardState.onPressKey(KeyCode.SHIFT, true, Constants.TextUtils.CAP_MODE_OFF, null)
        keyboardState.onReleaseKey(KeyCode.SHIFT, false, Constants.TextUtils.CAP_MODE_OFF, null)

        assertTrue("Alphabet should be shift-locked after double tap", keyboardState.isAlphabetShiftLocked)
        assertTrue("Alphabet should be shifted", keyboardState.isAlphabetShifted)

        // Clear transient shift state (as on input restart)
        keyboardState.clearTransientShiftState()

        assertTrue("Shift lock MUST be preserved across input restarts", keyboardState.isAlphabetShiftLocked)
        assertTrue("Alphabet should remain shifted when locked", keyboardState.isAlphabetShifted)
    }

    @Test
    fun testSinglePointerPressPreservesAutoCaps() {
        keyboardState.onLoadKeyboard(TextUtils.CAP_MODE_WORDS, null, false)
        assertTrue("Alphabet should be auto-shifted on load", keyboardState.isAlphabetShifted)

        // First letter down with single pointer
        keyboardState.onPressKey('K'.code, true, TextUtils.CAP_MODE_WORDS, null)
        assertTrue("Alphabet should remain shifted on initial single-pointer letter down", keyboardState.isAlphabetShifted)
    }

    @Test
    fun testMultiPointerPressUnshiftsAutoCapsDuringFastTyping() {
        keyboardState.onLoadKeyboard(TextUtils.CAP_MODE_WORDS, null, false)
        assertTrue("Alphabet should be auto-shifted on load", keyboardState.isAlphabetShifted)

        // First letter down with single pointer
        keyboardState.onPressKey('K'.code, true, TextUtils.CAP_MODE_WORDS, null)
        assertTrue("First letter should keep shift", keyboardState.isAlphabetShifted)

        // Second letter down before first is released (isSinglePointer = false)
        keyboardState.onPressKey('D'.code, false, TextUtils.CAP_MODE_WORDS, null)
        assertFalse("Second overlapping letter press must unshift keyboard to avoid duplicate capitalization", keyboardState.isAlphabetShifted)
    }

    @Test
    fun testHoldingShiftChordingPreservesShiftWithMultiplePointers() {
        // Press Shift and hold (no release)
        keyboardState.onPressKey(KeyCode.SHIFT, true, Constants.TextUtils.CAP_MODE_OFF, null)
        assertTrue("Alphabet should be shifted when Shift is pressed", keyboardState.isAlphabetShifted)

        // Press letter while Shift is held with another finger (isSinglePointer = false)
        keyboardState.onPressKey('A'.code, false, Constants.TextUtils.CAP_MODE_OFF, null)
        assertTrue("Alphabet should remain shifted when Shift is held down (chording)", keyboardState.isAlphabetShifted)
    }

    @Test
    fun testShiftLockPreservedWithMultiplePointers() {
        // Double tap Shift to engage Caps Lock
        keyboardState.onPressKey(KeyCode.SHIFT, true, Constants.TextUtils.CAP_MODE_OFF, null)
        keyboardState.onReleaseKey(KeyCode.SHIFT, false, Constants.TextUtils.CAP_MODE_OFF, null)
        keyboardState.onPressKey(KeyCode.SHIFT, true, Constants.TextUtils.CAP_MODE_OFF, null)
        keyboardState.onReleaseKey(KeyCode.SHIFT, false, Constants.TextUtils.CAP_MODE_OFF, null)

        assertTrue(keyboardState.isAlphabetShiftLocked)
        assertTrue(keyboardState.isAlphabetShifted)

        // Multi-touch fast typing in Caps Lock
        keyboardState.onPressKey('A'.code, false, Constants.TextUtils.CAP_MODE_OFF, null)
        assertTrue("Shift lock must remain active during multi-pointer typing", keyboardState.isAlphabetShiftLocked)
        assertTrue("Alphabet must remain shifted during multi-pointer typing in Caps Lock", keyboardState.isAlphabetShifted)
    }
}
