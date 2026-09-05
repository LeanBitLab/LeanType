/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils

import android.view.inputmethod.InputMethodSubtype
import helium314.keyboard.latin.DictionaryFacilitator
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.settings.SettingsValues

@Suppress("unused")
object StatsUtils {

    @JvmStatic
    fun onCreate(settingsValues: SettingsValues?, richImm: RichInputMethodManager?) {
    }

    @JvmStatic
    fun onPickSuggestionManually(
        suggestedWords: SuggestedWords?,
        suggestionInfo: SuggestedWords.SuggestedWordInfo?,
        dictionaryFacilitator: DictionaryFacilitator?
    ) {
    }

    @JvmStatic
    fun onBackspaceWordDelete(wordLength: Int) {
    }

    @JvmStatic
    fun onBackspacePressed(lengthToDelete: Int) {
    }

    @JvmStatic
    fun onBackspaceSelectedText(selectedTextLength: Int) {
    }

    @JvmStatic
    fun onDeleteMultiCharInput(multiCharLength: Int) {
    }

    @JvmStatic
    fun onRevertAutoCorrect() {
    }

    @JvmStatic
    fun onRevertDoubleSpacePeriod() {
    }

    @JvmStatic
    fun onRevertSwapPunctuation() {
    }

    @JvmStatic
    fun onFinishInputView() {
    }

    @JvmStatic
    fun onCreateInputView() {
    }

    @JvmStatic
    fun onStartInputView(inputType: Int, displayOrientation: Int, restarting: Boolean) {
    }

    @JvmStatic
    fun onAutoCorrection(
        typedWord: String?,
        autoCorrectionWord: String?,
        isBatchInput: Boolean,
        dictionaryFacilitator: DictionaryFacilitator?,
        prevWordsContext: String?
    ) {
    }

    @JvmStatic
    fun onWordCommitUserTyped(commitWord: String?, isBatchMode: Boolean) {
    }

    @JvmStatic
    fun onWordCommitAutoCorrect(commitWord: String?, isBatchMode: Boolean) {
    }

    @JvmStatic
    fun onWordCommitSuggestionPickedManually(commitWord: String?, isBatchMode: Boolean) {
    }

    @JvmStatic
    fun onDoubleSpacePeriod() {
    }

    @JvmStatic
    fun onLoadSettings(settingsValues: SettingsValues?) {
    }

    @JvmStatic
    fun onInvalidWordIdentification(invalidWord: String?) {
    }

    @JvmStatic
    fun onSubtypeChanged(oldSubtype: InputMethodSubtype?, newSubtype: InputMethodSubtype?) {
    }

    @JvmStatic
    fun onSettingsActivity(entryPoint: String?) {
    }

    @JvmStatic
    fun onInputConnectionLaggy(operation: Int, duration: Long) {
    }

    @JvmStatic
    fun onDecoderLaggy(operation: Int, duration: Long) {
    }
}
