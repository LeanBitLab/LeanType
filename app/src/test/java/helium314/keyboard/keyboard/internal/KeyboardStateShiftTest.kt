// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal

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
        override fun requestUpdatingShiftState(autoCapsFlags: Int, recapitalizeMode: RecapitalizeMode?) {}
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
}
