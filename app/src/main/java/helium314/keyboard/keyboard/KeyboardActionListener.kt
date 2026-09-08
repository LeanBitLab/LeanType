/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard

import android.view.KeyEvent
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.latin.common.InputPointers

interface KeyboardActionListener {
    /**
     * Called when the user presses a key. This is sent before onCodeInput is called.
     * For keys that repeat, this is only called once.
     */
    fun onPressKey(
        primaryCode: Int,
        repeatCount: Int,
        isSinglePointer: Boolean,
        hapticEvent: HapticEvent
    )

    fun onLongPressKey(primaryCode: Int)

    /**
     * Called when the user releases a key. This is sent after onCodeInput is called.
     * For keys that repeat, this is only called once.
     */
    fun onReleaseKey(primaryCode: Int, withSliding: Boolean)

    /** For handling hardware key presses. Returns whether the event was handled. */
    fun onKeyDown(keyCode: Int, keyEvent: KeyEvent): Boolean

    /** For handling hardware key presses. Returns whether the event was handled. */
    fun onKeyUp(keyCode: Int, keyEvent: KeyEvent): Boolean

    /**
     * Send a key code to the listener.
     */
    fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean)

    /**
     * Sends a string of characters to the listener.
     */
    fun onTextInput(text: String?)

    /**
     * Sends an image URI to be pasted into the text field.
     */
    fun onImageSelected(imageUri: String)

    /**
     * Called when user started batch input.
     */
    fun onStartBatchInput()

    /**
     * Sends the ongoing batch input points data.
     */
    fun onUpdateBatchInput(batchPointers: InputPointers)

    /**
     * Sends the final batch input points data.
     */
    fun onEndBatchInput(batchPointers: InputPointers)

    fun onCancelBatchInput()

    /**
     * Called when user released a finger outside any key.
     */
    fun onCancelInput()

    /**
     * Called when user finished sliding key input.
     */
    fun onFinishSlidingInput()

    /**
     * Send a non-"code input" custom request to the listener.
     *
     * @return true if the request has been consumed, false otherwise.
     */
    fun onCustomRequest(requestCode: Int): Boolean

    /**
     * Called when the user performs a horizontal or vertical swipe gesture on the space bar.
     */
    fun onHorizontalSpaceSwipe(steps: Int): Boolean

    fun onVerticalSpaceSwipe(steps: Int): Boolean

    fun onEndSpaceSwipe()

    fun toggleNumpad(withSliding: Boolean, forceReturnToAlpha: Boolean): Boolean

    fun onMoveDeletePointer(steps: Int)

    fun onUpWithDeletePointerActive()

    fun resetMetaState()

    open class Adapter : KeyboardActionListener {
        override fun onPressKey(
            primaryCode: Int,
            repeatCount: Int,
            isSinglePointer: Boolean,
            hapticEvent: HapticEvent
        ) {
        }

        override fun onLongPressKey(primaryCode: Int) {
        }

        override fun onReleaseKey(primaryCode: Int, withSliding: Boolean) {
        }

        override fun onKeyDown(keyCode: Int, keyEvent: KeyEvent): Boolean {
            return false
        }

        override fun onKeyUp(keyCode: Int, keyEvent: KeyEvent): Boolean {
            return false
        }

        override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) {
        }

        override fun onTextInput(text: String?) {
        }

        override fun onImageSelected(imageUri: String) {
        }

        override fun onStartBatchInput() {
        }

        override fun onUpdateBatchInput(batchPointers: InputPointers) {
        }

        override fun onEndBatchInput(batchPointers: InputPointers) {
        }

        override fun onCancelBatchInput() {
        }

        override fun onCancelInput() {
        }

        override fun onFinishSlidingInput() {
        }

        override fun onCustomRequest(requestCode: Int): Boolean {
            return false
        }

        override fun onHorizontalSpaceSwipe(steps: Int): Boolean {
            return false
        }

        override fun onVerticalSpaceSwipe(steps: Int): Boolean {
            return false
        }

        override fun toggleNumpad(withSliding: Boolean, forceReturnToAlpha: Boolean): Boolean {
            return false
        }

        override fun onEndSpaceSwipe() {
        }

        override fun onMoveDeletePointer(steps: Int) {
        }

        override fun onUpWithDeletePointerActive() {
        }

        override fun resetMetaState() {
        }
    }

    companion object {
        val EMPTY_LISTENER: KeyboardActionListener = Adapter()

        const val SWIPE_NO_ACTION = 0
        const val SWIPE_MOVE_CURSOR = 1
        const val SWIPE_SWITCH_LANGUAGE = 2
        const val SWIPE_TOGGLE_NUMPAD = 3
        const val SWIPE_HIDE_KEYBOARD = 4
        const val SWIPE_TOUCHPAD_MODE = 5

        const val CODE_TOUCHPAD_ON = 1000
        const val CODE_TOUCHPAD_OFF = 1001
    }
}
