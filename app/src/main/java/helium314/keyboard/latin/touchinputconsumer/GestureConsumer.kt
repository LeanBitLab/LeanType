/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.touchinputconsumer

import android.view.inputmethod.EditorInfo
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.DictionaryFacilitator
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.inputlogic.PrivateCommandPerformer
import java.util.Locale

/**
 * Stub for GestureConsumer.
 * <br>
 * The methods of this class should only be called from a single thread, e.g.,
 * the UI Thread.
 */
@Suppress("unused")
class GestureConsumer private constructor() {

    fun willConsume(): Boolean = false

    fun onInit(locale: Locale?, keyboard: Keyboard?) {}

    fun onGestureStarted(locale: Locale?, keyboard: Keyboard?) {}

    fun onGestureCanceled() {}

    fun onGestureCompleted(inputPointers: InputPointers?) {}

    fun onImeSuggestionsProcessed(
        suggestedWords: SuggestedWords?,
        composingStart: Int,
        composingLength: Int,
        dictionaryFacilitator: DictionaryFacilitator?
    ) {}

    companion object {
        @JvmField
        val NULL_GESTURE_CONSUMER: GestureConsumer = GestureConsumer()

        @JvmStatic
        fun newInstance(
            editorInfo: EditorInfo?,
            commandPerformer: PrivateCommandPerformer?,
            locale: Locale?,
            keyboard: Keyboard?
        ): GestureConsumer {
            return NULL_GESTURE_CONSUMER
        }
    }
}
