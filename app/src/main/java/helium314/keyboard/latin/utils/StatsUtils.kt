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

    fun onCreate(settingsValues: SettingsValues?, richImm: RichInputMethodManager?) {
    }

    fun onPickSuggestionManually(
        suggestedWords: SuggestedWords?,
        suggestionInfo: SuggestedWords.SuggestedWordInfo?,
        dictionaryFacilitator: DictionaryFacilitator?
    ) {
    }

    fun onBackspaceWordDelete(wordLength: Int) {
    }

    fun onBackspacePressed(lengthToDelete: Int) {
    }

    fun onBackspaceSelectedText(selectedTextLength: Int) {
    }

    fun onDeleteMultiCharInput(multiCharLength: Int) {
    }

    fun onRevertAutoCorrect() {
    }

    fun onRevertDoubleSpacePeriod() {
    }

    fun onRevertSwapPunctuation() {
    }

    fun onFinishInputView() {
    }

    fun onCreateInputView() {
    }

    fun onStartInputView(inputType: Int, displayOrientation: Int, restarting: Boolean) {
    }

    fun onAutoCorrection(
        typedWord: String?,
        autoCorrectionWord: String?,
        isBatchInput: Boolean,
        dictionaryFacilitator: DictionaryFacilitator?,
        prevWordsContext: String?
    ) {
    }

    fun onWordCommitUserTyped(commitWord: String?, isBatchMode: Boolean) {
    }

    fun onWordCommitAutoCorrect(commitWord: String?, isBatchMode: Boolean) {
    }

    fun onWordCommitSuggestionPickedManually(commitWord: String?, isBatchMode: Boolean) {
    }

    fun onDoubleSpacePeriod() {
    }

    fun onLoadSettings(settingsValues: SettingsValues?) {
    }

    fun onInvalidWordIdentification(invalidWord: String?) {
    }

    fun onSubtypeChanged(oldSubtype: InputMethodSubtype?, newSubtype: InputMethodSubtype?) {
    }

    fun onSettingsActivity(entryPoint: String?) {
    }

    fun onInputConnectionLaggy(operation: Int, duration: Long) {
    }

    fun onDecoderLaggy(operation: Int, duration: Long) {
    }
}
