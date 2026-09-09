/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.inputlogic

import android.graphics.Color
import android.os.SystemClock
import android.text.InputType
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.SuggestionSpan
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.EditorInfo
import helium314.keyboard.event.Event
import helium314.keyboard.event.InputTransaction
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.DictionaryFacilitator
import helium314.keyboard.latin.LastComposedWord
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.RichInputConnection
import helium314.keyboard.latin.SingleDictionaryFacilitator
import helium314.keyboard.latin.Suggest
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.WordComposer
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.common.getTextWithAutoCorrectionIndicatorUnderline
import helium314.keyboard.latin.common.getTextWithSuggestionSpan
import helium314.keyboard.latin.common.isEmoji
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.DictionaryFactory
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.settings.SpacingAndPunctuations
import helium314.keyboard.latin.suggestions.SuggestionStripViewAccessor
import helium314.keyboard.latin.utils.AsyncResultHolder
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.IntentUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ProofreadHelper
import helium314.keyboard.latin.utils.RecapitalizeMode
import helium314.keyboard.latin.utils.RecapitalizeStatus
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.StatsUtils
import helium314.keyboard.latin.utils.TextExpanderUtils
import helium314.keyboard.latin.utils.TextPlacement
import helium314.keyboard.latin.utils.TextRange
import helium314.keyboard.latin.utils.getTimestamp
import java.text.BreakIterator
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.TimeUnit

class InputLogic(
    private val mLatinIME: LatinIME,
    private val mSuggestionStripViewAccessor: SuggestionStripViewAccessor,
    private val mDictionaryFacilitator: DictionaryFacilitator
) {
    private var mSpaceState = SpaceState.NONE
    private var mSuggestedWords: SuggestedWords = SuggestedWords.getEmptyInstance()
    private val mSuggest: Suggest = Suggest(mDictionaryFacilitator)
    private var mEmojiDictionaryFacilitator: SingleDictionaryFacilitator? = null

    private var mLastComposedWord: LastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD
    private val mWordComposer = WordComposer()
    private val mConnection = RichInputConnection(mLatinIME)
    private val mRecapitalizeStatus = RecapitalizeStatus()

    private var mDeleteCount = 0
    private var mLastKeyTime = 0L
    private var mEnteredText: String? = null
    private var mIsAutoCorrectionIndicatorOn = false
    private var mDoubleSpacePeriodCountdownStart = 0L
    private var mWordBeingCorrectedByCursor: String? = null
    private var mLastExpandedText: String? = null
    private var mLastShortcutText: String? = null
    private var mLastExpandedCursorPosition = -1
    private var mLastExpandedCursorOffset = -1
    private var mJustRevertedExpandedShortcut: String? = null
    private var mJustRevertedACommit = false

    private var mAutoCommitSequenceNumber = 1
    private var mTextBeforeProofread: String? = null
    private var mTextBeforeTranslate: String? = null

    private val mInputLogicHandler = InputLogicHandler(mLatinIME.mHandler, this)

    val connection: RichInputConnection
        get() = mConnection

    val suggest: Suggest
        get() = mSuggest

    val wordComposer: WordComposer
        get() = mWordComposer

    val suggestedWords: SuggestedWords
        get() = mSuggestedWords

    fun startInput(combiningSpec: String?, settingsValues: SettingsValues) {
        mEnteredText = null
        mWordBeingCorrectedByCursor = null
        mConnection.onStartInput()
        if (mWordComposer.getTypedWord().isNotEmpty()) {
            StatsUtils.onWordCommitUserTyped(mWordComposer.getTypedWord(), mWordComposer.isBatchMode())
        }
        mWordComposer.restartCombining(combiningSpec)
        resetComposingState(true)
        mDeleteCount = 0
        mSpaceState = SpaceState.NONE
        mRecapitalizeStatus.disable()

        mSuggestedWords = SuggestedWords.getEmptyInstance()
        mConnection.tryFixIncorrectCursorPosition()
        cancelDoubleSpacePeriodCountdown()
        mInputLogicHandler.reset()
        mConnection.requestCursorUpdates(true, true)
        setInlineEmojiSearchAction(false)
    }

    fun onSubtypeChanged(combiningSpec: String?, settingsValues: SettingsValues) {
        finishInput()
        startInput(combiningSpec, settingsValues)
    }

    fun onOrientationChange(settingsValues: SettingsValues) {
        if (mWordComposer.isComposingWord()) {
            mConnection.beginBatchEdit()
            commitTyped(settingsValues, LastComposedWord.NOT_A_SEPARATOR)
            mConnection.endBatchEdit()
        }
    }

    fun finishInput() {
        if (mWordComposer.isComposingWord()) {
            mConnection.finishComposingText()
            StatsUtils.onWordCommitUserTyped(mWordComposer.getTypedWord(), mWordComposer.isBatchMode())
        }
        resetComposingState(true)
        mInputLogicHandler.reset()
        mSpaceState = SpaceState.NONE
        mConnection.ensureBatchEditClosed()
    }

    fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        composingSpanStart: Int, composingSpanEnd: Int,
        settingsValues: SettingsValues
    ): Boolean {
        if (mConnection.isBelatedExpectedUpdate(oldSelStart, newSelStart, oldSelEnd, newSelEnd, composingSpanStart, composingSpanEnd)) {
            return false
        }
        mSpaceState = SpaceState.NONE

        if (oldSelStart != newSelStart || oldSelEnd != newSelEnd) {
            if (newSelStart != mLastExpandedCursorPosition) {
                mLastExpandedText = null
                mLastShortcutText = null
                mLastExpandedCursorPosition = -1
                mLastExpandedCursorOffset = -1
                mJustRevertedExpandedShortcut = null
            }
        }

        val selectionChangedOrSafeToReset = (oldSelStart != newSelStart || oldSelEnd != newSelEnd) || !mWordComposer.isComposingWord()
        val hasOrHadSelection = (oldSelStart != oldSelEnd || newSelStart != newSelEnd)
        val moveAmount = newSelStart - oldSelStart

        if (hasOrHadSelection || !settingsValues.needsToLookupSuggestions() ||
            (selectionChangedOrSafeToReset && !mWordComposer.moveCursorByAndReturnIfInsideComposingWord(moveAmount))) {
            resetEntireInputState(newSelStart, newSelEnd, false)
            val wordBeingCorrected = mWordBeingCorrectedByCursor
            if (!wordBeingCorrected.isNullOrEmpty()) {
                performAdditionToUserHistoryDictionary(settingsValues, wordBeingCorrected, NgramContext.EMPTY_PREV_WORDS_INFO)
            }
        } else {
            mConnection.resetCachesUponCursorMoveAndReturnSuccess(newSelStart, newSelEnd, false)
        }

        mRecapitalizeStatus.enable()
        mLatinIME.mHandler.postResumeSuggestions(true)
        mRecapitalizeStatus.stop()
        mWordBeingCorrectedByCursor = null
        return true
    }

    fun moveCursorByAndReturnIfInsideComposingWord(distance: Int): Boolean {
        return mWordComposer.moveCursorByAndReturnIfInsideComposingWord(distance)
    }

    fun onTextInput(
        settingsValues: SettingsValues,
        event: Event,
        keyboardShiftMode: Int,
        handler: LatinIME.UIHandler
    ): InputTransaction {
        val rawText = event.textToCommit.toString()
        val inputTransaction = InputTransaction(
            settingsValues, event,
            SystemClock.uptimeMillis(), mSpaceState,
            getActualCapsMode(settingsValues, keyboardShiftMode)
        )
        mConnection.beginBatchEdit()
        if (mWordComposer.isComposingWord()) {
            if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
                // stop composing, otherwise the text will end up at the end of the current word
                mConnection.finishComposingText()
                resetComposingState(false)
            } else {
                commitCurrentAutoCorrection(settingsValues, rawText, handler)
                addToHistoryIfEmoji(rawText, settingsValues) // add emoji after committing text
            }
        } else {
            addToHistoryIfEmoji(rawText, settingsValues) // add emoji before resetting, otherwise lastComposedWord is empty
            resetComposingState(true /* alsoResetLastComposedWord */)
        }
        handler.postUpdateSuggestionStrip(SuggestedWords.INPUT_STYLE_TYPING)
        val text = performSpecificTldProcessingOnTextInput(rawText)
        if (SpaceState.PHANTOM == mSpaceState) {
            insertAutomaticSpaceIfOptionsAndTextAllow(settingsValues)
        }
        mConnection.commitText(text, 1)
        StatsUtils.onWordCommitUserTyped(mEnteredText, mWordComposer.isBatchMode())
        mConnection.endBatchEdit()
        // Space state must be updated before calling updateShiftState
        // ponytail: set PHANTOM space state after emoji if autospace after emoji is enabled
        mSpaceState = if (settingsValues.mAutospaceAfterEmoji && isEmoji(text)) SpaceState.PHANTOM else SpaceState.NONE
        mEnteredText = text
        mWordBeingCorrectedByCursor = null
        inputTransaction.setDidAffectContents()
        inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)
        return inputTransaction
    }

    fun onPickSuggestionManually(
        settingsValues: SettingsValues,
        suggestionInfo: SuggestedWordInfo,
        keyboardShiftState: Int,
        handler: LatinIME.UIHandler
    ): InputTransaction {
        if (isInlineEmojiSearchAction()) {
            deleteTextReplacedByEmoji()
        }

        val suggestedWords = mSuggestedWords
        val suggestion = suggestionInfo.mWord
        // If this is a punctuation picked from the suggestion strip, pass it to onCodeInput
        if (suggestion.length == 1 && suggestedWords.isPunctuationSuggestions) {
            // We still want to log a suggestion click.
            StatsUtils.onPickSuggestionManually(mSuggestedWords, suggestionInfo, mDictionaryFacilitator)
            // Word separators are suggested before the user inputs something.
            // Rely on onCodeInput to do the complicated swapping/stripping logic consistently.
            val event = Event.createPunctuationSuggestionPickedEvent(suggestionInfo)
            return onCodeInput(settingsValues, event, keyboardShiftState, handler)
        }

        val event = Event.createSuggestionPickedEvent(suggestionInfo)
        val inputTransaction = InputTransaction(
            settingsValues,
            event, SystemClock.uptimeMillis(), mSpaceState, keyboardShiftState
        )
        if (DebugFlags.DEBUG_ENABLED) {
            Log.i("SuggestTrace", "pickSuggestion: composingBefore=" + mWordComposer.isComposingWord())
        }
        // Manual pick affects the contents of the editor, so we take note of this. It's important for the sequence of language switching.
        inputTransaction.setDidAffectContents()
        mConnection.beginBatchEdit()
        if (SpaceState.PHANTOM == mSpaceState && suggestion.isNotEmpty()
            // In the batch input mode, a manually picked suggested word should just replace the current batch input text and there is no need for a phantom space.
            && !mWordComposer.isBatchMode()
            // when a commit was reverted and user chose a different suggestion, we don't want to insert a space before the picked word
            && !mJustRevertedACommit
        ) {
            val firstChar = Character.codePointAt(suggestion, 0)
            if (!settingsValues.isWordSeparator(firstChar) || settingsValues.isUsuallyPrecededBySpace(firstChar)) {
                insertAutomaticSpaceIfOptionsAndTextAllow(settingsValues)
            }
        }
        mJustRevertedACommit = false

        if (suggestionInfo.isKindOf(SuggestedWordInfo.KIND_APP_DEFINED)) {
            mSuggestedWords = SuggestedWords.getEmptyInstance()
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
            inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)
            resetComposingState(true /* alsoResetLastComposedWord */)
            mConnection.commitCompletion(suggestionInfo.mApplicationSpecifiedCompletionInfo)
            mConnection.endBatchEdit()
            return inputTransaction
        }

        commitChosenWord(
            settingsValues, suggestion, LastComposedWord.COMMIT_TYPE_MANUAL_PICK,
            LastComposedWord.NOT_A_SEPARATOR
        )
        if (settingsValues.mAutospaceAfterSuggestion) {
            if (settingsValues.mImmediateAutoSpace) {
                mConnection.finishComposingText()
                mConnection.commitText(" ", 1)
                mConnection.finishComposingText()
                resetComposingState(false)
                mSpaceState = SpaceState.DOUBLE
            } else {
                mSpaceState = SpaceState.PHANTOM
            }
        }
        mConnection.endBatchEdit()
        inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)
        setInlineEmojiSearchAction(false)

        handler.postUpdateSuggestionStrip(SuggestedWords.INPUT_STYLE_NONE)

        StatsUtils.onPickSuggestionManually(mSuggestedWords, suggestionInfo, mDictionaryFacilitator)
        StatsUtils.onWordCommitSuggestionPickedManually(suggestionInfo.mWord, mWordComposer.isBatchMode())
        return inputTransaction
    }

    fun onCodeInput(
        settingsValues: SettingsValues,
        event: Event,
        keyboardShiftMode: Int,
        handler: LatinIME.UIHandler
    ): InputTransaction {
        mWordBeingCorrectedByCursor = null
        mJustRevertedACommit = false
        val processedEvent = mWordComposer.processEvent(event)
        val inputTransaction = InputTransaction(
            settingsValues,
            processedEvent, SystemClock.uptimeMillis(), mSpaceState,
            getActualCapsMode(settingsValues, keyboardShiftMode)
        )
        if (processedEvent.keyCode != KeyCode.DELETE
            || inputTransaction.timestamp > mLastKeyTime + Constants.LONG_PRESS_MILLISECONDS
        ) {
            mDeleteCount = 0
        }
        if (processedEvent.keyCode != KeyCode.DELETE) {
            mLastExpandedText = null
            mLastShortcutText = null
            mLastExpandedCursorPosition = -1
            mLastExpandedCursorOffset = -1
            mJustRevertedExpandedShortcut = null
        }
        mLastKeyTime = inputTransaction.timestamp
        mConnection.beginBatchEdit()
        if (!mWordComposer.isComposingWord()) {
            mIsAutoCorrectionIndicatorOn = false
        }

        if (processedEvent.codePoint != Constants.CODE_SPACE && event.codePoint != Constants.CODE_SPACE) {
            cancelDoubleSpacePeriodCountdown()
        }

        var currentEvent: Event? = processedEvent
        while (null != currentEvent) {
            if (currentEvent.isConsumed) {
                handleConsumedEvent(currentEvent, inputTransaction)
            } else if (currentEvent.isFunctionalKeyEvent) {
                handleFunctionalEvent(currentEvent, inputTransaction, handler)
            } else {
                handleNonFunctionalEvent(currentEvent, inputTransaction, handler)
            }
            currentEvent = currentEvent.nextEvent
        }

        if (!mConnection.hasSlowInputConnection() && !mWordComposer.isComposingWord()
            && (settingsValues.isWordCodePoint(processedEvent.codePoint)
                || processedEvent.keyCode == KeyCode.DELETE)
        ) {
            mWordBeingCorrectedByCursor = getWordAtCursor(settingsValues)
        }
        if (!inputTransaction.didAutoCorrect() && processedEvent.keyCode != KeyCode.SHIFT
            && processedEvent.keyCode != KeyCode.CAPS_LOCK
            && processedEvent.keyCode != KeyCode.SYMBOL_ALPHA
            && processedEvent.keyCode != KeyCode.ALPHA
            && processedEvent.keyCode != KeyCode.SYMBOL
        ) {
            mLastComposedWord.deactivate()
        }
        if (KeyCode.DELETE != processedEvent.keyCode) {
            mEnteredText = null
        }
        mConnection.endBatchEdit()
        return inputTransaction
    }

    fun onStartBatchInput(
        settingsValues: SettingsValues,
        keyboardSwitcher: KeyboardSwitcher,
        handler: LatinIME.UIHandler
    ) {
        mWordBeingCorrectedByCursor = null
        mInputLogicHandler.onStartBatchInput()
        handler.showGesturePreviewAndSetSuggestions(SuggestedWords.getEmptyBatchInstance(), false)
        handler.cancelUpdateSuggestionStrip()
        ++mAutoCommitSequenceNumber
        mConnection.beginBatchEdit()
        if (mWordComposer.isComposingWord()) {
            if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
                unlearnWord(mWordComposer.getTypedWord(), settingsValues, Constants.EVENT_BACKSPACE)
                resetEntireInputState(mConnection.expectedSelectionStart, mConnection.expectedSelectionEnd, true)
            } else if (mWordComposer.isSingleLetter() && !isInlineEmojiSearchAction()) {
                commitCurrentAutoCorrection(settingsValues, LastComposedWord.NOT_A_SEPARATOR, handler)
            } else {
                commitTyped(settingsValues, LastComposedWord.NOT_A_SEPARATOR)
            }
        } else if (mConnection.hasSelection()) {
            val selectedText = mConnection.getSelectedText(0)
            if (selectedText != null) {
                mWordComposer.setRejectedBatchModeSuggestion(selectedText.toString())
            }
        }
        val codePointBeforeCursor = mConnection.codePointBeforeCursor
        if (Character.isLetterOrDigit(codePointBeforeCursor)
            || settingsValues.isUsuallyFollowedBySpace(codePointBeforeCursor)
        ) {
            val autoShiftHasBeenOverriden = keyboardSwitcher.keyboardShiftMode != getCurrentAutoCapsState(settingsValues)
            if (settingsValues.mAutospaceBeforeGestureTyping) {
                mSpaceState = SpaceState.PHANTOM
            }
            if (!autoShiftHasBeenOverriden) {
                keyboardSwitcher.requestUpdatingShiftState(
                    getCurrentAutoCapsState(settingsValues),
                    getCurrentRecapitalizeState()
                )
            }
        }
        mConnection.endBatchEdit()
        mWordComposer.setCapitalizedModeAtStartComposingTime(
            getActualCapsMode(settingsValues, keyboardSwitcher.keyboardShiftMode)
        )
    }

    fun onUpdateBatchInput(batchPointers: InputPointers) {
        mInputLogicHandler.onUpdateBatchInput(batchPointers, mAutoCommitSequenceNumber)
    }

    fun onEndBatchInput(batchPointers: InputPointers) {
        mInputLogicHandler.updateTailBatchInput(batchPointers, mAutoCommitSequenceNumber)
        ++mAutoCommitSequenceNumber
    }

    fun onCancelBatchInput(handler: LatinIME.UIHandler) {
        mInputLogicHandler.onCancelBatchInput()
        handler.showGesturePreviewAndSetSuggestions(
            SuggestedWords.getEmptyInstance(), true /* dismissGestureFloatingPreviewText */
        )
    }

    fun setSuggestedWords(suggestedWords: SuggestedWords) {
        if (!suggestedWords.isEmpty) {
            val suggestedWordInfo = if (suggestedWords.mWillAutoCorrect) {
                suggestedWords.getInfo(SuggestedWords.INDEX_OF_AUTO_CORRECTION)
            } else {
                suggestedWords.mTypedWordInfo
            }
            mWordComposer.setAutoCorrection(suggestedWordInfo)
        }
        mSuggestedWords = suggestedWords
        val newAutoCorrectionIndicator = suggestedWords.mWillAutoCorrect

        if (mIsAutoCorrectionIndicatorOn != newAutoCorrectionIndicator && mWordComposer.isComposingWord()) {
            mIsAutoCorrectionIndicatorOn = newAutoCorrectionIndicator
            val textWithUnderline = getTextWithUnderline(mWordComposer.getTypedWord())
            setComposingTextInternal(textWithUnderline, 1)
        }
    }

    private fun handleConsumedEvent(event: Event, inputTransaction: InputTransaction) {
        val textToCommit = event.textToCommit
        if (!TextUtils.isEmpty(textToCommit)) {
            mConnection.commitText(textToCommit, 1)
            inputTransaction.setDidAffectContents()
        }
        if (mWordComposer.isComposingWord()) {
            if (SpaceState.PHANTOM == inputTransaction.spaceState
                && "bn_khipro" == mWordComposer.getCombiningSpec()
            ) {
                insertAutomaticSpaceIfOptionsAndTextAllow(inputTransaction.settingsValues)
                mSpaceState = SpaceState.NONE
            }
            setComposingTextInternal(mWordComposer.getTypedWord(), 1)
            inputTransaction.setDidAffectContents()
            inputTransaction.setRequiresUpdateSuggestions()
        }
    }

    private fun handleClipboardPaste() {
        val clipboardContent = mLatinIME.clipboardHistoryManager.retrieveClipboardContent().toString()
        if (clipboardContent.isEmpty()) {
            return
        }
        if (clipboardContent.length > 1000) {
            mConnection.performContextMenuAction(android.R.id.paste)
        } else {
            mLatinIME.onTextInput(clipboardContent)
        }
    }

    private fun handleProofread() {
        Log.i(TAG, "handleProofread() called")
        if (ProofreadHelper.isOperationInProgress) {
            Log.i(TAG, "Cancelling current proofreading operation")
            ProofreadHelper.cancelCurrentOperation()
            return
        }
        val textToProofread: String
        val hasSelection = mConnection.hasSelection()

        if (hasSelection) {
            val selectedText = mConnection.getSelectedText(0)
            textToProofread = selectedText?.toString() ?: ""
            Log.i(TAG, "Proofreading selected text: ${textToProofread.length} chars")
        } else {
            val maxChars = 60000
            var textBefore: CharSequence? = null
            var textAfter: CharSequence? = null
            try {
                textBefore = mConnection.getTextBeforeCursor(maxChars, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get text before cursor: $e")
            }
            try {
                textAfter = mConnection.getTextAfterCursor(maxChars, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get text after cursor: $e")
                try {
                    textAfter = mConnection.getTextAfterCursor(2048, 0)
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to get text after cursor (retry): $e2")
                }
            }
            val before = textBefore?.toString() ?: ""
            val after = textAfter?.toString() ?: ""
            textToProofread = before + after
            mConnection.selectAll()
        }

        mTextBeforeProofread = textToProofread

        ProofreadHelper.proofreadAsync(
            mLatinIME,
            textToProofread,
            hasSelection,
            onSuccess = { proofreadText ->
                val textBefore = mTextBeforeProofread
                if (proofreadText.isNotEmpty() && proofreadText != textBefore) {
                    if (textBefore != null && textBefore.length > 20 && proofreadText.length < textBefore.length * 0.3) {
                        Log.w(TAG, "Proofread result suspiciously short (${proofreadText.length} vs ${textBefore.length}), aborting replacement to prevent truncation data loss")
                        KeyboardSwitcher.getInstance().showToast("Proofread output truncated by model; replacement aborted.", false)
                        if (!hasSelection) {
                            val len = textBefore.length
                            mConnection.setSelection(len, len)
                        }
                        return@proofreadAsync
                    }
                    mConnection.commitText(proofreadText, 1)
                } else {
                    if (!hasSelection) {
                        val len = textBefore?.length ?: 0
                        mConnection.setSelection(len, len)
                    }
                }
            },
            onError = { errorMessage ->
                Log.e(TAG, "Proofreading error: $errorMessage")
            }
        )
    }

    private fun handleTranslate() {
        val textToTranslate: String
        val hasSelection = mConnection.hasSelection()

        if (hasSelection) {
            val selectedText = mConnection.getSelectedText(0)
            textToTranslate = selectedText?.toString() ?: ""
        } else {
            val maxChars = 60000
            var textBefore: CharSequence? = null
            var textAfter: CharSequence? = null
            try {
                textBefore = mConnection.getTextBeforeCursor(maxChars, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get text before cursor: $e")
            }
            try {
                textAfter = mConnection.getTextAfterCursor(maxChars, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get text after cursor: $e")
                try {
                    textAfter = mConnection.getTextAfterCursor(2048, 0)
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to get text after cursor (retry): $e2")
                }
            }
            val before = textBefore?.toString() ?: ""
            val after = textAfter?.toString() ?: ""
            textToTranslate = before + after
            mConnection.selectAll()
        }

        mTextBeforeTranslate = textToTranslate

        ProofreadHelper.translateAsync(
            mLatinIME,
            textToTranslate,
            hasSelection,
            onSuccess = { translatedText ->
                val textBefore = mTextBeforeTranslate
                if (translatedText.isNotEmpty() && translatedText != textBefore) {
                    if (textBefore != null && textBefore.length > 20 && translatedText.length < textBefore.length * 0.3) {
                        Log.w(TAG, "Translation result suspiciously short (${translatedText.length} vs ${textBefore.length}), aborting replacement to prevent truncation data loss")
                        KeyboardSwitcher.getInstance().showToast("Translation output truncated by model; replacement aborted.", false)
                        if (!hasSelection) {
                            val len = textBefore.length
                            mConnection.setSelection(len, len)
                        }
                        return@translateAsync
                    }
                    mConnection.commitText(translatedText, 1)
                } else {
                    if (!hasSelection) {
                        val len = textBefore?.length ?: 0
                        mConnection.setSelection(len, len)
                    }
                }
            },
            onError = { errorMessage ->
                Log.e(TAG, "Translation error: $errorMessage")
            }
        )
    }

    private fun handleShowTranslateLanguages() {
        mLatinIME.showTranslateLanguageSelector()
    }

    private fun handleFunctionalEvent(
        event: Event,
        inputTransaction: InputTransaction,
        handler: LatinIME.UIHandler
    ) {
        val currentKeyboardScript = inputTransaction.settingsValues.mCurrentKeyboardScript
        val keyCode = event.keyCode
        when (keyCode) {
            KeyCode.DELETE -> {
                handleBackspaceEvent(event, inputTransaction)
                inputTransaction.setDidAffectContents()
            }
            KeyCode.SHIFT -> {
                val keyboard = KeyboardSwitcher.getInstance().keyboard
                if (keyboard != null && !keyboard.mId.isAlphabetKeyboard && keyboard.mId.mElementId != KeyboardId.ELEMENT_TEXT_EDIT) {
                    return
                }
                performRecapitalization(inputTransaction.settingsValues)
                inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)
                inputTransaction.setRequiresUpdateSuggestions()
                if (mSpaceState == SpaceState.PHANTOM && inputTransaction.settingsValues.mShiftRemovesAutospace) {
                    mSpaceState = SpaceState.NONE
                }
            }
            KeyCode.SETTINGS -> onSettingsKeyPressed()
            KeyCode.ACTION_NEXT -> performEditorAction(EditorInfo.IME_ACTION_NEXT, inputTransaction.settingsValues, handler)
            KeyCode.ACTION_PREVIOUS -> performEditorAction(EditorInfo.IME_ACTION_PREVIOUS, inputTransaction.settingsValues, handler)
            KeyCode.LANGUAGE_SWITCH -> handleLanguageSwitchKey()
            KeyCode.CLIPBOARD -> {
                if (!inputTransaction.settingsValues.mClipboardHistoryEnabled) {
                    handleClipboardPaste()
                }
            }
            KeyCode.CLIPBOARD_PASTE -> handleClipboardPaste()
            KeyCode.SHIFT_ENTER -> {
                val tmpEvent = Event.createSoftwareKeypressEvent(
                    Constants.CODE_ENTER, keyCode, 0, event.x, event.y, event.isKeyRepeat
                )
                handleNonSpecialCharacterEvent(tmpEvent, inputTransaction, handler)
                inputTransaction.setDidAffectContents()
            }
            KeyCode.MULTIPLE_CODE_POINTS -> mWordComposer.applyProcessedEvent(event, true)
            KeyCode.CLIPBOARD_SELECT_ALL -> mConnection.selectAll()
            KeyCode.CLIPBOARD_SELECT_WORD -> mConnection.selectWord(
                inputTransaction.settingsValues.mSpacingAndPunctuations, currentKeyboardScript
            )
            KeyCode.CLIPBOARD_COPY -> mConnection.copyText(true)
            KeyCode.CLIPBOARD_COPY_ALL -> mConnection.copyText(false)
            KeyCode.CLIPBOARD_CLEAR_HISTORY -> mLatinIME.clipboardHistoryManager.clearHistory()
            KeyCode.CLIPBOARD_CUT -> {
                if (mConnection.hasSelection()) {
                    mConnection.copyText(true)
                    val backspaceEvent = Event.createSoftwareKeypressEvent(
                        KeyCode.DELETE, 0, event.x, event.y, event.isKeyRepeat
                    )
                    handleBackspaceEvent(backspaceEvent, inputTransaction)
                    inputTransaction.setDidAffectContents()
                }
            }
            KeyCode.WORD_LEFT -> {
                sendDownUpKeyEventWithMetaState(
                    if (ScriptUtils.isScriptRtl(currentKeyboardScript)) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.META_CTRL_ON or event.metaState
                )
            }
            KeyCode.WORD_RIGHT -> {
                sendDownUpKeyEventWithMetaState(
                    if (ScriptUtils.isScriptRtl(currentKeyboardScript)) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.META_CTRL_ON or event.metaState
                )
            }
            KeyCode.MOVE_START_OF_PAGE -> {
                val selectionEnd1 = mConnection.expectedSelectionEnd
                val selectionStart1 = mConnection.expectedSelectionStart
                sendDownUpKeyEventWithMetaState(
                    KeyEvent.KEYCODE_MOVE_HOME,
                    KeyEvent.META_CTRL_ON or event.metaState
                )
                if (mConnection.expectedSelectionStart == selectionStart1 && mConnection.expectedSelectionEnd == selectionEnd1) {
                    val newEnd = if ((event.metaState and KeyEvent.META_SHIFT_MASK) != 0) selectionEnd1 else 0
                    mConnection.setSelection(0, newEnd)
                }
            }
            KeyCode.MOVE_END_OF_PAGE -> {
                val selectionStart2 = mConnection.expectedSelectionStart
                val selectionEnd2 = mConnection.expectedSelectionEnd
                sendDownUpKeyEventWithMetaState(
                    KeyEvent.KEYCODE_MOVE_END,
                    KeyEvent.META_CTRL_ON or event.metaState
                )
                if (mConnection.expectedSelectionStart == selectionStart2 && mConnection.expectedSelectionEnd == selectionEnd2) {
                    try {
                        val newStart = if ((event.metaState and KeyEvent.META_SHIFT_MASK) != 0) selectionStart2 else Int.MAX_VALUE
                        mConnection.setSelection(newStart, Int.MAX_VALUE)
                    } catch (e: Exception) {
                        Log.i(TAG, "error when trying to move cursor to last position: $e")
                    }
                }
            }
            KeyCode.UNDO -> sendDownUpKeyEventWithMetaState(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON)
            KeyCode.REDO -> sendDownUpKeyEventWithMetaState(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            KeyCode.CUSTOM_AI_1 -> handleCustomAIKey(1)
            KeyCode.CUSTOM_AI_2 -> handleCustomAIKey(2)
            KeyCode.CUSTOM_AI_3 -> handleCustomAIKey(3)
            KeyCode.CUSTOM_AI_4 -> handleCustomAIKey(4)
            KeyCode.CUSTOM_AI_5 -> handleCustomAIKey(5)
            KeyCode.CUSTOM_AI_6 -> handleCustomAIKey(6)
            KeyCode.CUSTOM_AI_7 -> handleCustomAIKey(7)
            KeyCode.CUSTOM_AI_8 -> handleCustomAIKey(8)
            KeyCode.CUSTOM_AI_9 -> handleCustomAIKey(9)
            KeyCode.CUSTOM_AI_10 -> handleCustomAIKey(10)
            KeyCode.PROOFREAD -> handleProofread()
            KeyCode.TRANSLATE -> handleTranslate()
            KeyCode.SHOW_TRANSLATE_LANGUAGES -> handleShowTranslateLanguages()
            KeyCode.SPLIT_LAYOUT -> KeyboardSwitcher.getInstance().toggleSplitKeyboardMode()
            KeyCode.TIMESTAMP -> mLatinIME.onTextInput(getTimestamp(mLatinIME))
            KeyCode.SEND_INTENT_ONE, KeyCode.SEND_INTENT_TWO, KeyCode.SEND_INTENT_THREE -> {
                IntentUtils.handleSendIntentKey(mLatinIME, event.keyCode)
            }
            KeyCode.IME_HIDE_UI -> mLatinIME.requestHideSelf(0)
            KeyCode.INLINE_EMOJI_SEARCH_DONE -> {
                setInlineEmojiSearchAction(false)
                inputTransaction.setRequiresUpdateSuggestions()
            }
            KeyCode.VOICE_INPUT, KeyCode.EMOJI, KeyCode.TOGGLE_ONE_HANDED_MODE, KeyCode.SWITCH_ONE_HANDED_MODE,
            KeyCode.TOGGLE_FLOATING_KEYBOARD, KeyCode.HANDWRITING, KeyCode.CLEAR_HANDWRITING, KeyCode.CLIPBOARD_SEARCH,
            KeyCode.TOGGLE_TOUCHPAD_MODE, KeyCode.TOGGLE_TEXT_EDIT_MODE, KeyCode.TOGGLE_SELECTION_MODE, KeyCode.SWITCH_TO_USER_IME -> {
                // Handled elsewhere
            }
            KeyCode.CAPS_LOCK -> {
                val keyboard = KeyboardSwitcher.getInstance().keyboard
                if (keyboard == null || keyboard.mId.isAlphabetKeyboard) {
                    inputTransaction.setRequiresUpdateSuggestions()
                }
            }
            else -> {
                val isModifier = with(KeyCode) { keyCode.isModifier() }
                if (isModifier) return
                val keyEventCode = if (keyCode > 0) {
                    keyCode
                } else if (event.codePoint >= 0) {
                    KeyCode.codePointToKeyEventCode(event.codePoint)
                } else {
                    KeyCode.keyCodeToKeyEventCode(keyCode)
                }
                if (keyEventCode != KeyEvent.KEYCODE_UNKNOWN) {
                    sendDownUpKeyEventWithMetaState(keyEventCode, event.metaState)
                    return
                }
                if (event.metaState != 0 && event.codePoint >= 32) {
                    handleNonFunctionalEvent(event, inputTransaction, handler)
                    return
                }
                Log.e(TAG, "unknown event, key code: $keyCode, codepoint ${event.codePoint}, meta: ${event.metaState}")
                if (DebugFlags.DEBUG_ENABLED) {
                    throw RuntimeException("Unknown event")
                }
            }
        }
    }

    private fun handleNonFunctionalEvent(
        event: Event,
        inputTransaction: InputTransaction,
        handler: LatinIME.UIHandler
    ) {
        inputTransaction.setDidAffectContents()
        if (event.codePoint == Constants.CODE_ENTER) {
            if (tryJumpToNextPlaceholder()) {
                return
            }
            val editorInfo = getCurrentInputEditorInfo()
            val imeOptionsActionId = InputTypeUtils.getImeOptionsActionIdFromEditorInfo(editorInfo)
            if (InputTypeUtils.IME_ACTION_CUSTOM_LABEL == imeOptionsActionId) {
                performEditorAction(editorInfo.actionId, inputTransaction.settingsValues, handler)
            } else if (EditorInfo.IME_ACTION_NONE != imeOptionsActionId) {
                performEditorAction(imeOptionsActionId, inputTransaction.settingsValues, handler)
            } else {
                handleNonSpecialCharacterEvent(event, inputTransaction, handler)
            }
        } else {
            handleNonSpecialCharacterEvent(event, inputTransaction, handler)
        }
    }

    private fun handleNonSpecialCharacterEvent(
        event: Event,
        inputTransaction: InputTransaction,
        handler: LatinIME.UIHandler
    ) {
        val codePoint = event.codePoint
        mSpaceState = SpaceState.NONE
        val sv = inputTransaction.settingsValues
        if (Character.getType(codePoint) == Character.OTHER_SYMBOL.toInt()
            || (Character.getType(codePoint) == Character.UNASSIGNED.toInt() && StringUtils.mightBeEmoji(codePoint))
            || (sv.isWordSeparator(codePoint)
                && (Character.isWhitespace(codePoint)
                    || !textBeforeCursorMayBeUrlOrSimilar(sv, false)
                    || (codePoint == '/'.code && mWordComposer.lastChar() == '/')))
        ) {
            handleSeparatorEvent(event, inputTransaction, handler)
            addToHistoryIfEmoji(StringUtils.newSingleCodePointString(codePoint), sv)
        } else {
            if (SpaceState.PHANTOM == inputTransaction.spaceState) {
                if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
                    unlearnWord(mWordComposer.getTypedWord(), sv, Constants.EVENT_BACKSPACE)
                    resetEntireInputState(mConnection.expectedSelectionStart, mConnection.expectedSelectionEnd, true)
                } else {
                    commitTyped(sv, LastComposedWord.NOT_A_SEPARATOR)
                }
            }
            handleNonSeparatorEvent(event, sv, inputTransaction)
        }
    }

    private fun addToHistoryIfEmoji(text: String, settingsValues: SettingsValues) {
        if (mLastComposedWord == LastComposedWord.NOT_A_COMPOSED_WORD
            || mWordComposer.isComposingWord()
            || !settingsValues.mBigramPredictionEnabled
            || settingsValues.mIncognitoModeEnabled
            || !settingsValues.isSuggestionsEnabledPerUserSettings()
            || !isEmoji(text)
        ) {
            return
        }
        if (mConnection.hasSlowInputConnection()) {
            Log.w(TAG, "Skipping learning due to slow InputConnection.")
            return
        }
        mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD
        mDictionaryFacilitator.addToUserHistory(
            text,
            false,
            mConnection.getNgramContextFromNthPreviousWord(settingsValues.mSpacingAndPunctuations, 2),
            TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
            settingsValues.mBlockPotentiallyOffensive
        )
    }

    private fun handleNonSeparatorEvent(
        event: Event,
        settingsValues: SettingsValues,
        inputTransaction: InputTransaction
    ) {
        val codePoint = event.codePoint
        var isComposingWord = mWordComposer.isComposingWord()
        mWordComposer.unsetBatchMode()

        if (settingsValues.mUrlDetectionEnabled && settingsValues.needsToLookupSuggestions()
            && !isComposingWord && SpaceState.NONE == inputTransaction.spaceState
            && settingsValues.mSpacingAndPunctuations.isSometimesWordConnector(mConnection.codePointBeforeCursor)
            && !settingsValues.mSpacingAndPunctuations.isSometimesWordConnector(mConnection.charBeforeBeforeCursor)
            && mConnection.hasLetterBeforeLastSpaceBeforeCursor()
        ) {
            val text = mConnection.textBeforeCursorUntilLastWhitespaceOrDoubleSlash()
            val range = TextRange(text, 0, text.length, text.length, false)
            isComposingWord = true
            restartSuggestions(range)
        }

        if (SpaceState.PHANTOM == inputTransaction.spaceState
            && !settingsValues.isWordConnector(codePoint)
            && !settingsValues.isUsuallyFollowedBySpace(codePoint)
        ) {
            if (isComposingWord) {
                throw RuntimeException("Should not be composing here")
            }
            insertAutomaticSpaceIfOptionsAndTextAllow(settingsValues)
        }

        if (mWordComposer.isCursorInFrontOfComposingWord()) {
            resetEntireInputState(mConnection.expectedSelectionStart, mConnection.expectedSelectionEnd, false)
            isComposingWord = false
        } else if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
            unlearnWord(mWordComposer.getTypedWord(), inputTransaction.settingsValues, Constants.EVENT_BACKSPACE)
            resetEntireInputState(mConnection.expectedSelectionStart, mConnection.expectedSelectionEnd, true)
            isComposingWord = false
        }

        if (!isComposingWord
            && settingsValues.isWordCodePoint(codePoint)
            && settingsValues.needsToLookupSuggestions()
            && (!settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
                || !mConnection.isCursorTouchingWord(settingsValues.mSpacingAndPunctuations, !mConnection.hasSlowInputConnection())
                || isCursorAtStartOrAfterSeparator(settingsValues))
        ) {
            isComposingWord = !settingsValues.mSpacingAndPunctuations.isWordConnector(codePoint)
            resetComposingState(false /* alsoResetLastComposedWord */)
        }

        enterInlineEmojiSearchIfNeeded(codePoint, settingsValues)

        if (isComposingWord) {
            mWordComposer.applyProcessedEvent(event)
            if (mWordComposer.isSingleLetter()) {
                mWordComposer.setCapitalizedModeAtStartComposingTime(inputTransaction.shiftState)
            }
            var didSetComposingText = false
            var didExpand = false
            var shouldDeferSegmentation = false
            if (TextExpanderUtils.isEnabled(mLatinIME)
                && TextExpanderUtils.isImmediateEnabled(mLatinIME)
            ) {
                val typedWord = mWordComposer.getTypedWord()
                setComposingTextInternal(getTextWithUnderline(typedWord), 1)
                didSetComposingText = true
                val textBefore = mConnection.getTextBeforeCursor(50, 0)
                if (textBefore != null) {
                    val textStr = textBefore.toString()
                    val result = TextExpanderUtils.getExpandedWordForTyped(typedWord, textStr, mLatinIME)
                    if (result != null) {
                        if (mJustRevertedExpandedShortcut != null && result.matchedString.equals(mJustRevertedExpandedShortcut, ignoreCase = true)) {
                            // Skip re-expanding
                        } else {
                            if (result.prefixLength > 0) {
                                mConnection.commitText("", 1)
                                mConnection.deleteTextBeforeCursor(result.prefixLength)
                            }
                            commitExpandedText(result.matchedString, result.expandedText)
                            resetComposingState(true)
                            didExpand = true
                        }
                    }
                    shouldDeferSegmentation = !didExpand
                        && ScriptUtils.needsWordSegmentation(settingsValues.mLocale)
                        && TextExpanderUtils.isPrefixOfNonRegexShortcut(typedWord, textStr, mLatinIME)
                }
            }
            if (!didExpand && !shouldDeferSegmentation) {
                val didCommitCompletedWordSegments = maybeCommitCompletedWordSegments(settingsValues)
                if (!didSetComposingText || didCommitCompletedWordSegments) {
                    setComposingTextInternal(getTextWithUnderline(mWordComposer.getTypedWord()), 1)
                }
            }
        } else {
            val swapWeakSpace = tryStripSpaceAndReturnWhetherShouldSwapInstead(event, inputTransaction)

            if (swapWeakSpace && trySwapSwapperAndSpace(event, inputTransaction)) {
                mSpaceState = SpaceState.WEAK
            } else if ((settingsValues.mInputAttributes.mInputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT
                && codePoint >= '0'.code && codePoint <= '9'.code
            ) {
                sendDownUpKeyEvent(codePoint - '0'.code + KeyEvent.KEYCODE_0)
            } else {
                mConnection.commitCodePoint(codePoint)
                if (settingsValues.needsToLookupSuggestions() && settingsValues.isWordCodePoint(codePoint)) {
                    inputTransaction.setRequiresUpdateSuggestions()
                }
            }
            if (TextExpanderUtils.isEnabled(mLatinIME)
                && TextExpanderUtils.isImmediateEnabled(mLatinIME)
            ) {
                val textBefore = mConnection.getTextBeforeCursor(50, 0)
                if (textBefore != null) {
                    val result = TextExpanderUtils.getExpandedWordForTyped(null, textBefore.toString(), mLatinIME)
                    if (result != null) {
                        if (mJustRevertedExpandedShortcut != null && result.matchedString.equals(mJustRevertedExpandedShortcut, ignoreCase = true)) {
                            // Skip re-expanding
                        } else {
                            mConnection.deleteTextBeforeCursor(result.matchedString.length)
                            commitExpandedText(result.matchedString, result.expandedText)
                        }
                    }
                }
            }
        }
        inputTransaction.setRequiresUpdateSuggestions()
    }

    private fun isCursorAtStartOrAfterSeparator(settingsValues: SettingsValues): Boolean {
        val codePointBeforeCursor = mConnection.codePointBeforeCursor
        return codePointBeforeCursor == Constants.NOT_A_CODE
            || settingsValues.mSpacingAndPunctuations.isWordSeparator(codePointBeforeCursor)
    }

    private fun maybeCommitCompletedWordSegments(settingsValues: SettingsValues): Boolean {
        if (!ScriptUtils.needsWordSegmentation(settingsValues.mLocale)
            || settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
        ) {
            return false
        }

        val typedWord = mWordComposer.getTypedWord()
        val length = typedWord.length
        if (length <= 1) {
            return false
        }

        val iterator = THAI_WORD_BREAK_ITERATOR.get() ?: return false
        iterator.setText(typedWord)
        var segmentStart = iterator.first()
        var wordBoundary = iterator.next()
        var didCommitSegment = false
        while (wordBoundary != BreakIterator.DONE && wordBoundary < length) {
            val completedWordSegment = typedWord.substring(segmentStart, wordBoundary)
            if (!TextUtils.isEmpty(completedWordSegment)) {
                commitCompletedWordSegment(settingsValues, completedWordSegment)
                didCommitSegment = true
            }
            segmentStart = wordBoundary
            wordBoundary = iterator.next()
        }
        if (!didCommitSegment) {
            return false
        }

        val remainingWord = typedWord.substring(segmentStart)
        val codePoints = StringUtils.toCodePointArray(remainingWord)
        mWordComposer.setComposingWord(codePoints, mLatinIME.getCoordinatesForCurrentKeyboard(codePoints))
        return true
    }

    private fun commitCompletedWordSegment(
        settingsValues: SettingsValues,
        completedWordSegment: String
    ) {
        val ngramContext = getNgramContextFromNthPreviousWordForSuggestion(settingsValues.mSpacingAndPunctuations, 2)
        mConnection.commitText(completedWordSegment, 1)
        performAdditionToUserHistoryDictionary(settingsValues, completedWordSegment, ngramContext)
        mLastComposedWord = LastComposedWord(
            ArrayList(), null, completedWordSegment, completedWordSegment,
            LastComposedWord.NOT_A_SEPARATOR, ngramContext, WordComposer.CAPS_MODE_OFF
        )
        StatsUtils.onWordCommitUserTyped(completedWordSegment, mWordComposer.isBatchMode())
    }

    private fun handleSeparatorEvent(
        event: Event,
        inputTransaction: InputTransaction,
        handler: LatinIME.UIHandler
    ) {
        val codePoint = event.codePoint
        val settingsValues = inputTransaction.settingsValues
        val wasComposingWord = mWordComposer.isComposingWord()
        val needsSegmentation = ScriptUtils.needsWordSegmentation(settingsValues.mLocale)
        val shouldAvoidSendingCode = Constants.CODE_SPACE == codePoint
            && !settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
            && wasComposingWord
            && !needsSegmentation

        if (!wasComposingWord && mConnection.hasSelection()) {
            val pairedCodepoint = settingsValues.mSpacingAndPunctuations.getSecondInSymbolPair(codePoint)
            if (pairedCodepoint != Constants.NOT_A_CODE) {
                wrapSelection(codePoint, pairedCodepoint)
                inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)
                return
            }
        }
        if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
            unlearnWord(mWordComposer.getTypedWord(), inputTransaction.settingsValues, Constants.EVENT_BACKSPACE)
            resetEntireInputState(mConnection.expectedSelectionStart, mConnection.expectedSelectionEnd, true)
        }
        if (mWordComposer.isComposingWord()) {
            var shouldTriggerAutoCorrect = settingsValues.mAutoCorrectEnabled
            if (shouldTriggerAutoCorrect) {
                if ("space" == settingsValues.mAutoCorrectTrigger) {
                    shouldTriggerAutoCorrect = Character.isWhitespace(codePoint)
                } else if ("punctuation" == settingsValues.mAutoCorrectTrigger) {
                    shouldTriggerAutoCorrect = !Character.isWhitespace(codePoint)
                }
            }
            if (shouldTriggerAutoCorrect && !isInlineEmojiSearchAction()) {
                val separator = if (shouldAvoidSendingCode) LastComposedWord.NOT_A_SEPARATOR else StringUtils.newSingleCodePointString(codePoint)
                commitCurrentAutoCorrection(settingsValues, separator, handler)
                inputTransaction.setDidAutoCorrect()
            } else {
                commitTyped(settingsValues, StringUtils.newSingleCodePointString(codePoint))
            }
        }

        val swapWeakSpace = tryStripSpaceAndReturnWhetherShouldSwapInstead(event, inputTransaction)
        val isInsideDoubleQuoteOrAfterDigit = Constants.CODE_DOUBLE_QUOTE == codePoint && mConnection.isInsideDoubleQuoteOrAfterDigit()

        val needsPrecedingSpace = if (SpaceState.PHANTOM != inputTransaction.spaceState) {
            false
        } else if (Constants.CODE_DOUBLE_QUOTE == codePoint) {
            !isInsideDoubleQuoteOrAfterDigit
        } else if (settingsValues.mSpacingAndPunctuations.isClusteringSymbol(codePoint)
            && settingsValues.mSpacingAndPunctuations.isClusteringSymbol(mConnection.codePointBeforeCursor)
        ) {
            false
        } else {
            settingsValues.isUsuallyPrecededBySpace(codePoint) || isEmoji(codePoint)
        }

        if (needsPrecedingSpace) {
            insertAutomaticSpaceIfOptionsAndTextAllow(settingsValues)
        }

        if (tryPerformDoubleSpacePeriod(event, inputTransaction)) {
            mSpaceState = SpaceState.DOUBLE
            inputTransaction.setRequiresUpdateSuggestions()
            StatsUtils.onDoubleSpacePeriod()
        } else if (swapWeakSpace && trySwapSwapperAndSpace(event, inputTransaction)) {
            mSpaceState = SpaceState.SWAP_PUNCTUATION
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
        } else if (Constants.CODE_SPACE == codePoint) {
            if (DebugFlags.DEBUG_ENABLED) {
                Log.i("SuggestTrace", "space: wasComposing=$wasComposingWord suggestedWordsEmpty=${mSuggestedWords.isEmpty}")
            }
            if (!mSuggestedWords.isPunctuationSuggestions) {
                mSpaceState = SpaceState.WEAK
            }

            startDoubleSpacePeriodCountdown(inputTransaction)
            if (wasComposingWord || mSuggestedWords.isEmpty) {
                inputTransaction.setRequiresUpdateSuggestions()
            }

            if (!shouldAvoidSendingCode) {
                mConnection.commitCodePoint(codePoint)
            }
        } else {
            if (SpaceState.PHANTOM == inputTransaction.spaceState
                && (settingsValues.isUsuallyFollowedBySpace(codePoint) || isInsideDoubleQuoteOrAfterDigit)
            ) {
                mSpaceState = SpaceState.PHANTOM
            } else {
                if (wasComposingWord
                    && settingsValues.mAutospaceAfterPunctuation
                    && (settingsValues.isUsuallyFollowedBySpace(codePoint) || isInsideDoubleQuoteOrAfterDigit)
                ) {
                    mSpaceState = SpaceState.PHANTOM
                }
            }

            enterInlineEmojiSearchIfNeeded(codePoint, settingsValues)
            mConnection.commitCodePoint(codePoint)

            if (isInlineEmojiSearchAction()) {
                inputTransaction.setRequiresUpdateSuggestions()
            } else {
                mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
            }
        }

        inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)
    }

    private fun handleBackspaceEvent(event: Event, inputTransaction: InputTransaction) {
        mSpaceState = SpaceState.NONE
        mDeleteCount++
        // Decide acceleration independently of whether the cursor is in a composing word.
        // Selection deletion, undo and gesture-word rejection remain single actions.
        val accelerated = mDeleteCount > Constants.DELETE_ACCELERATE_AT
        if (!handleBackspaceStep(event, inputTransaction, resumeSuggestions = !accelerated)
            || !accelerated
            || mConnection.expectedSelectionStart == 0
        ) return

        // This is a new logical deletion, not a second application of the processed event.
        // The first step may have emptied/resumed a word or changed a combiner's state.
        val extraBackspace = Event.createSoftwareKeypressEvent(
            KeyCode.DELETE, event.metaState, event.x, event.y, event.isKeyRepeat
        )
        var extraEvent: Event? = mWordComposer.processEvent(extraBackspace)
        while (extraEvent != null) {
            when {
                extraEvent.isConsumed -> handleConsumedEvent(extraEvent, inputTransaction)
                extraEvent.keyCode == KeyCode.DELETE -> handleBackspaceStep(extraEvent, inputTransaction)
                extraEvent.isFunctionalKeyEvent -> handleFunctionalEvent(extraEvent, inputTransaction, mLatinIME.mHandler)
                else -> handleNonFunctionalEvent(extraEvent, inputTransaction, mLatinIME.mHandler)
            }
            extraEvent = extraEvent.nextEvent
        }
    }

    // Returns true only for ordinary deletion, which may receive an accelerated second step.
    private fun handleBackspaceStep(
        event: Event, inputTransaction: InputTransaction, resumeSuggestions: Boolean = true
    ): Boolean {
        val currentKeyboardScript = inputTransaction.settingsValues.mCurrentKeyboardScript

        val selection = mConnection.getSelectedText(0)
        val hasSelection = !selection.isNullOrEmpty() || mConnection.hasSelection()
        if (hasSelection) {
            val numCharsDeleted = if (!selection.isNullOrEmpty()) selection.length else (mConnection.expectedSelectionEnd - mConnection.expectedSelectionStart)
            if (!selection.isNullOrEmpty()) {
                unlearnWord(selection.toString(), inputTransaction.settingsValues, Constants.EVENT_BACKSPACE)
            }
            mWordComposer.reset()
            sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL)
            StatsUtils.onBackspaceSelectedText(numCharsDeleted)
            if (inputTransaction.settingsValues.needsToLookupSuggestions()
                && inputTransaction.settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
            ) {
                restartSuggestionsOnWordTouchedByCursor(inputTransaction.settingsValues)
            }
            inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_LATER)
            return false
        }

        val lastExpandedText = mLastExpandedText
        if (lastExpandedText != null && !event.isKeyRepeat
            && TextExpanderUtils.isBackspaceRevertsEnabled(mLatinIME)
        ) {
            val expectedCursor = mConnection.expectedSelectionEnd
            if (expectedCursor == mLastExpandedCursorPosition) {
                val beforeLen = mLastExpandedCursorOffset
                val afterLen = lastExpandedText.length - beforeLen
                val textBefore = mConnection.getTextBeforeCursor(beforeLen, 0)
                val textAfter = mConnection.getTextAfterCursor(afterLen, 0)
                val expectedBefore = lastExpandedText.substring(0, beforeLen)
                val expectedAfter = lastExpandedText.substring(beforeLen)
                if (textBefore != null && textBefore.toString() == expectedBefore
                    && textAfter != null && textAfter.toString() == expectedAfter
                ) {
                    mJustRevertedExpandedShortcut = mLastShortcutText
                    mConnection.deleteSurroundingText(beforeLen, afterLen)
                    mConnection.commitText(mLastShortcutText ?: "", 1)
                    mLastExpandedText = null
                    mLastShortcutText = null
                    mLastExpandedCursorPosition = -1
                    mLastExpandedCursorOffset = -1
                    mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD
                    return false
                }
            }
        }

        val shiftUpdateKind = if (event.isKeyRepeat && mConnection.expectedSelectionStart > 0) {
            InputTransaction.SHIFT_UPDATE_LATER
        } else {
            InputTransaction.SHIFT_UPDATE_NOW
        }
        inputTransaction.requireShiftUpdate(shiftUpdateKind)

        if (mWordComposer.isCursorFrontOrMiddleOfComposingWord()) {
            unlearnWord(mWordComposer.getTypedWord(), inputTransaction.settingsValues, Constants.EVENT_BACKSPACE)
            resetEntireInputState(mConnection.expectedSelectionStart, mConnection.expectedSelectionEnd, true)
        }
        if (mWordComposer.isComposingWord()) {
            val wasBatchMode = mWordComposer.isBatchMode()
            var expandedShortcut = false
            if (mWordComposer.isBatchMode()) {
                val rejectedSuggestion = mWordComposer.getTypedWord()
                mWordComposer.reset()
                mWordComposer.setRejectedBatchModeSuggestion(rejectedSuggestion)
                if (!TextUtils.isEmpty(rejectedSuggestion)) {
                    unlearnWord(rejectedSuggestion, inputTransaction.settingsValues, Constants.EVENT_REJECTION)
                }
                StatsUtils.onBackspaceWordDelete(rejectedSuggestion.length)
            } else {
                mWordComposer.applyProcessedEvent(event)
                StatsUtils.onBackspacePressed(1)
            }
            if (mWordComposer.isComposingWord()) {
                val typedWord = mWordComposer.getTypedWord()
                setComposingTextInternal(getTextWithUnderline(typedWord), 1)
                if (TextExpanderUtils.isEnabled(mLatinIME)
                    && TextExpanderUtils.isImmediateEnabled(mLatinIME)
                ) {
                    val textBefore = mConnection.getTextBeforeCursor(50, 0)
                    if (textBefore != null) {
                        val result = TextExpanderUtils.getExpandedWordForTyped(typedWord, textBefore.toString(), mLatinIME)
                        if (result != null) {
                            if (mJustRevertedExpandedShortcut == null || !result.matchedString.equals(mJustRevertedExpandedShortcut, ignoreCase = true)) {
                                if (result.prefixLength > 0) {
                                    mConnection.commitText("", 1)
                                    mConnection.deleteTextBeforeCursor(result.prefixLength)
                                }
                                commitExpandedText(result.matchedString, result.expandedText)
                                resetComposingState(true)
                                expandedShortcut = true
                            }
                        }
                    }
                }
            } else {
                if (wasBatchMode) {
                    mConnection.commitText("", 1)
                } else {
                    mConnection.finishComposingText()
                    mConnection.deleteTextBeforeCursor(1)
                }
            }
            updateInlineEmojiSearch()
            inputTransaction.setRequiresUpdateSuggestions()
            return !wasBatchMode && !expandedShortcut
        } else {
            if (mJustRevertedExpandedShortcut != null) {
                mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD
                mJustRevertedExpandedShortcut = null
            }
            if (mLastComposedWord.canRevertCommit()
                && inputTransaction.settingsValues.mBackspaceRevertsAutocorrect
                && !TextUtils.isDigitsOnly(mLastComposedWord.mCommittedWord)
            ) {
                val lastComposedWord = mLastComposedWord.mTypedWord
                revertCommit(inputTransaction)
                StatsUtils.onRevertAutoCorrect()
                StatsUtils.onWordCommitUserTyped(lastComposedWord, mWordComposer.isBatchMode())
                if (inputTransaction.settingsValues.needsToLookupSuggestions()
                    && inputTransaction.settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
                ) {
                    restartSuggestionsOnWordTouchedByCursor(inputTransaction.settingsValues)
                }
                return false
            }
            if (SpaceState.DOUBLE == inputTransaction.spaceState) {
                cancelDoubleSpacePeriodCountdown()
                if (mConnection.revertDoubleSpacePeriod(inputTransaction.settingsValues.mSpacingAndPunctuations)) {
                    inputTransaction.setRequiresUpdateSuggestions()
                    mWordComposer.setCapitalizedModeAtStartComposingTime(WordComposer.CAPS_MODE_OFF)
                    StatsUtils.onRevertDoubleSpacePeriod()
                    return false
                }
            } else if (SpaceState.SWAP_PUNCTUATION == inputTransaction.spaceState) {
                if (mConnection.revertSwapPunctuation()) {
                    StatsUtils.onRevertSwapPunctuation()
                    return false
                }
            }

            val fallbackSel = mConnection.getSelectedText(0)
            if (!TextUtils.isEmpty(fallbackSel) || mConnection.hasSelection()) {
                mWordComposer.reset()
                sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL)
                return false
            } else {
                if (inputTransaction.settingsValues.mInputAttributes.isTypeNull
                    || Constants.NOT_A_CURSOR_POSITION == mConnection.expectedSelectionEnd
                ) {
                    sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL)
                    StatsUtils.onBackspacePressed(1)
                } else {
                    val codePointBeforeCursor = mConnection.codePointBeforeCursor
                    if (codePointBeforeCursor == Constants.NOT_A_CODE) {
                        if ((getCurrentInputEditorInfo().inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT) {
                            sendDownUpKeyEvent(KeyEvent.KEYCODE_DEL)
                        } else {
                            mConnection.deleteTextBeforeCursor(1)
                        }
                        return false
                    }
                    val lengthToDelete = if (codePointBeforeCursor > 0xFE00 || StringUtils.mightBeEmoji(codePointBeforeCursor)) {
                        mConnection.charCountToDeleteBeforeCursor
                    } else {
                        1
                    }
                    mConnection.deleteTextBeforeCursor(lengthToDelete)
                    StatsUtils.onBackspacePressed(lengthToDelete)
                }
            }
            unlearnWordBeingDeleted(inputTransaction.settingsValues)
            if (mConnection.hasSlowInputConnection()) {
                mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
            } else if (resumeSuggestions && inputTransaction.settingsValues.needsToLookupSuggestions()
                && inputTransaction.settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
            ) {
                restartSuggestionsOnWordTouchedByCursor(inputTransaction.settingsValues)
            }
        }
        return true
    }

    internal fun getWordAtCursor(settingsValues: SettingsValues): String {
        val currentKeyboardScript = settingsValues.mCurrentKeyboardScript
        if (!mConnection.hasSelection()
            && settingsValues.needsToLookupSuggestions()
            && settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
        ) {
            val range = mConnection.getWordRangeAtCursor(settingsValues.mSpacingAndPunctuations, currentKeyboardScript)
            if (range != null) {
                return range.mWord.toString()
            }
        }
        return ""
    }

    internal fun unlearnWordBeingDeleted(settingsValues: SettingsValues): Boolean {
        if (mConnection.hasSlowInputConnection()) {
            Log.w(TAG, "Skipping unlearning due to slow InputConnection.")
            return false
        }
        if (!mConnection.isCursorFollowedByWordCharacter(settingsValues.mSpacingAndPunctuations)) {
            val wordBeingDeleted = getWordAtCursor(settingsValues)
            if (!TextUtils.isEmpty(wordBeingDeleted)) {
                unlearnWord(wordBeingDeleted, settingsValues, Constants.EVENT_BACKSPACE)
                return true
            }
        }
        return false
    }

    internal fun unlearnWord(word: String, settingsValues: SettingsValues, eventType: Int) {
        val ngramContext = mConnection.getNgramContextFromNthPreviousWord(settingsValues.mSpacingAndPunctuations, 2)
        val timeStampInSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        mDictionaryFacilitator.unlearnFromUserHistory(word, ngramContext, timeStampInSeconds, eventType)
    }

    private fun handleLanguageSwitchKey() {
        mLatinIME.switchToNextSubtype()
    }

    private fun trySwapSwapperAndSpace(
        event: Event,
        inputTransaction: InputTransaction
    ): Boolean {
        val codePointBeforeCursor = mConnection.codePointBeforeCursor
        if (Constants.CODE_SPACE != codePointBeforeCursor) {
            return false
        }
        mConnection.deleteTextBeforeCursor(1)
        val text = "${event.textToCommit} "
        mConnection.commitText(text, 1)
        inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)
        return true
    }

    private fun tryStripSpaceAndReturnWhetherShouldSwapInstead(
        event: Event,
        inputTransaction: InputTransaction
    ): Boolean {
        val codePoint = event.codePoint
        val isFromSuggestionStrip = event.isSuggestionStripPress
        if (Constants.CODE_ENTER == codePoint
            && SpaceState.SWAP_PUNCTUATION == inputTransaction.spaceState
        ) {
            mConnection.removeTrailingSpace()
            return false
        }

        // ponytail: only strip auto-inserted spaces (WEAK/PHANTOM/SWAP), never manual (NONE)
        if (!inputTransaction.settingsValues.mPreserveSpaceBeforePunctuation
            && isSpaceStrippingPunctuation(codePoint)
            && !inputTransaction.settingsValues.isUsuallyPrecededBySpace(codePoint)
            && inputTransaction.spaceState != SpaceState.NONE
        ) {
            if (mConnection.codePointBeforeCursor == Constants.CODE_SPACE) {
                mConnection.removeTrailingSpace()
            }
        }

        if (!inputTransaction.settingsValues.mPreserveSpaceBeforePunctuation
            && (SpaceState.WEAK == inputTransaction.spaceState
                || SpaceState.SWAP_PUNCTUATION == inputTransaction.spaceState)
            && isFromSuggestionStrip
        ) {
            if (inputTransaction.settingsValues.isUsuallyPrecededBySpace(codePoint)) {
                return false
            }
            if (inputTransaction.settingsValues.isUsuallyFollowedBySpace(codePoint)) {
                return true
            }
            mConnection.removeTrailingSpace()
        }
        return false
    }

    fun startDoubleSpacePeriodCountdown(inputTransaction: InputTransaction) {
        mDoubleSpacePeriodCountdownStart = inputTransaction.timestamp
    }

    fun cancelDoubleSpacePeriodCountdown() {
        mDoubleSpacePeriodCountdownStart = 0
    }

    fun isDoubleSpacePeriodCountdownActive(inputTransaction: InputTransaction): Boolean {
        return inputTransaction.timestamp - mDoubleSpacePeriodCountdownStart < inputTransaction.settingsValues.mDoubleSpacePeriodTimeout
    }

    private fun tryPerformDoubleSpacePeriod(
        event: Event,
        inputTransaction: InputTransaction
    ): Boolean {
        if (!inputTransaction.settingsValues.mUseDoubleSpacePeriod
            || Constants.CODE_SPACE != event.codePoint
            || !isDoubleSpacePeriodCountdownActive(inputTransaction)
        ) {
            return false
        }
        val lastTwo = mConnection.getTextBeforeCursor(3, 0) ?: return false
        val length = lastTwo.length
        if (length < 2) return false
        if (lastTwo[length - 1].code != Constants.CODE_SPACE) {
            return false
        }
        val firstCodePoint = if (Character.isSurrogatePair(lastTwo[0], lastTwo[1])) {
            Character.codePointAt(lastTwo, length - 3)
        } else {
            lastTwo[length - 2].code
        }
        if (canBeFollowedByDoubleSpacePeriod(firstCodePoint)) {
            cancelDoubleSpacePeriodCountdown()
            mConnection.deleteTextBeforeCursor(1)
            val textToInsert = inputTransaction.settingsValues.mSpacingAndPunctuations.mSentenceSeparatorAndSpace
            mConnection.commitText(textToInsert, 1)
            inputTransaction.requireShiftUpdate(InputTransaction.SHIFT_UPDATE_NOW)
            inputTransaction.setRequiresUpdateSuggestions()
            return true
        }
        return false
    }

    private fun performRecapitalization(settingsValues: SettingsValues) {
        mRecapitalizeStatus.enable()
        if (!mConnection.hasSelection()) {
            return
        }
        val selectionStart = mConnection.expectedSelectionStart
        val selectionEnd = mConnection.expectedSelectionEnd
        val numCharsSelected = selectionEnd - selectionStart
        if (numCharsSelected > Constants.MAX_CHARACTERS_FOR_RECAPITALIZATION) {
            return
        }
        if (!mRecapitalizeStatus.isStarted() || !mRecapitalizeStatus.isSetAt(selectionStart, selectionEnd)) {
            val selectedText = mConnection.getSelectedText(0)
            if (TextUtils.isEmpty(selectedText)) return
            mRecapitalizeStatus.start(
                selectedText.toString(), selectionStart, settingsValues.mLocale,
                settingsValues.mSpacingAndPunctuations.mSortedWordSeparators
            )
        }
        mConnection.finishComposingText()
        mRecapitalizeStatus.rotate()
        mConnection.setSelection(selectionEnd, selectionEnd)
        mConnection.deleteTextBeforeCursor(numCharsSelected)
        val replacement = mRecapitalizeStatus.textReplacement()
        mConnection.commitText(replacement.text, 0)
        mConnection.setSelection(replacement.selectionStart, replacement.selectionEnd())
    }

    internal fun performAdditionToUserHistoryDictionary(
        settingsValues: SettingsValues,
        suggestion: String,
        ngramContext: NgramContext
    ) {
        if (!settingsValues.isSuggestionsEnabledPerUserSettings() || TextUtils.isEmpty(suggestion)) {
            return
        }
        val wasAutoCapitalized = mWordComposer.wasAutoCapitalized() && !mWordComposer.isMostlyCaps()
        val word = stripWordSeparatorsFromEnd(suggestion, settingsValues)
        if (settingsValues.mIncognitoModeEnabled) {
            mDictionaryFacilitator.adjustConfidences(word, wasAutoCapitalized)
            return
        }
        if (mConnection.hasSlowInputConnection()) {
            Log.w(TAG, "Skipping learning due to slow InputConnection.")
            mDictionaryFacilitator.adjustConfidences(word, wasAutoCapitalized)
            return
        }
        val timeStampInSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        mDictionaryFacilitator.addToUserHistory(
            word, wasAutoCapitalized, ngramContext,
            timeStampInSeconds, settingsValues.mBlockPotentiallyOffensive
        )
    }

    private fun stripWordSeparatorsFromEnd(word: String, settingsValues: SettingsValues): String {
        return if (settingsValues.mSpacingAndPunctuations.isWordSeparator(word.codePointBefore(word.length))) {
            var endIndex = word.length - 1
            while (endIndex != 0 && settingsValues.mSpacingAndPunctuations.isWordSeparator(word.codePointBefore(endIndex))) {
                --endIndex
            }
            if (endIndex > 0) word.substring(0, endIndex) else word
        } else {
            word
        }
    }

    fun performUpdateSuggestionStripSync(settingsValues: SettingsValues, inputStyle: Int) {
        var startTimeMillis = 0L
        if (DebugFlags.DEBUG_ENABLED) {
            startTimeMillis = System.currentTimeMillis()
            Log.d(TAG, "performUpdateSuggestionStripSync()")
        }
        if (!settingsValues.needsToLookupSuggestions()) {
            if (mWordComposer.isComposingWord()) {
                Log.w(TAG, "Called updateSuggestionsOrPredictions but suggestions were not requested!")
            }
            mSuggestionStripViewAccessor.setSuggestions(SuggestedWords.getEmptyInstance())
            return
        }

        if (!mWordComposer.isComposingWord()) {
            val ngramContext = getNgramContextFromNthPreviousWordForSuggestion(settingsValues.mSpacingAndPunctuations, 1)
            val isFirstWord = ngramContext.isBeginningOfSentenceContext
            if ((isFirstWord && !settingsValues.mFirstWordPredictionEnabled)
                || (!isFirstWord && !settingsValues.mBigramPredictionEnabled)
            ) {
                mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
                return
            }
        }

        val holder = AsyncResultHolder<SuggestedWords>("Suggest")
        mInputLogicHandler.getSuggestedWords {
            getSuggestedWords(
                inputStyle, SuggestedWords.NOT_A_SEQUENCE_NUMBER
            ) { suggestedWords ->
                val typedWordString = mWordComposer.getTypedWord()
                val typedWordInfo = SuggestedWordInfo(
                    typedWordString, "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED,
                    Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX,
                    SuggestedWordInfo.NOT_A_CONFIDENCE
                )
                if (suggestedWords != null && (suggestedWords.size() > 1 || typedWordString.length <= 1)) {
                    holder.set(suggestedWords)
                } else {
                    holder.set(retrieveOlderSuggestions(typedWordInfo, mSuggestedWords))
                }
            }
        }
        val suggestedWords = holder.get(null, Constants.GET_SUGGESTED_WORDS_TIMEOUT.toLong())
        if (suggestedWords != null) {
            if (!(suggestedWords.mInputStyle == SuggestedWords.INPUT_STYLE_BEGINNING_OF_SENTENCE_PREDICTION
                    && mLatinIME.tryShowClipboardSuggestion())
            ) {
                mSuggestionStripViewAccessor.setSuggestions(suggestedWords)
            }
            if (!suggestedWords.isEmpty && settingsValues.isSuggestionsEnabledPerUserSettings()
                && isInlineEmojiSearchAction()
            ) {
                mSuggestionStripViewAccessor.showSuggestionStrip()
            }
        }
        if (DebugFlags.DEBUG_ENABLED && suggestedWords != null) {
            Log.i("SuggestTrace", "updateStrip: composing=${mWordComposer.isComposingWord()}"
                + " typedLen=${mWordComposer.getTypedWord().length}"
                + " suggestedSize=${suggestedWords.size()}"
                + " punctuation=${suggestedWords.isPunctuationSuggestions}")
            val runTimeMillis = System.currentTimeMillis() - startTimeMillis
            Log.d(TAG, "performUpdateSuggestionStripSync() : $runTimeMillis ms to finish")
        }
    }

    fun restartSuggestionsOnWordTouchedByCursor(settingsValues: SettingsValues) {
        val currentKeyboardScript = settingsValues.mCurrentKeyboardScript
        if (!settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
            || !settingsValues.needsToLookupSuggestions()
            || mInputLogicHandler.isInBatchInput()
            || mConnection.hasSelection()
            || mConnection.expectedSelectionStart < 0
        ) {
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
            return
        }

        updateInlineEmojiSearch()
        if (isInlineEmojiSearchAction()) {
            mInputLogicHandler.getSuggestedWords {
                getSuggestedWords(
                    SuggestedWords.INPUT_STYLE_TYPING,
                    SuggestedWords.NOT_A_SEQUENCE_NUMBER
                ) { words ->
                    if (words != null) doShowSuggestionsAndClearAutoCorrectionIndicator(words)
                }
            }
            return
        }

        if (!mConnection.isCursorTouchingWord(settingsValues.mSpacingAndPunctuations, true /* checkTextAfter */)) {
            mWordComposer.setCapitalizedModeAtStartComposingTime(WordComposer.CAPS_MODE_OFF)
            mLatinIME.mHandler.postUpdateSuggestionStrip(SuggestedWords.INPUT_STYLE_RECORRECTION)
            mConnection.finishComposingText()
            return
        }
        val range = mConnection.getWordRangeAtCursor(settingsValues.mSpacingAndPunctuations, currentKeyboardScript)
            ?: return
        if (range.length() <= 0) {
            mLatinIME.setNeutralSuggestionStrip()
            mConnection.finishComposingText()
            return
        }
        if (range.mHasUrlSpans) return
        if (!isResumableWord(settingsValues, range.mWord.toString())) {
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
            mConnection.finishComposingText()
            return
        }
        restartSuggestions(range)
    }

    private fun restartSuggestions(range: TextRange) {
        val numberOfCharsInWordBeforeCursor = range.getNumberOfCharsInWordBeforeCursor()
        val expectedCursorPosition = mConnection.expectedSelectionStart
        if (numberOfCharsInWordBeforeCursor > expectedCursorPosition) return
        val suggestions = ArrayList<SuggestedWordInfo>()
        val typedWordString = range.mWord.toString()
        val typedWordInfo = SuggestedWordInfo(
            typedWordString,
            "", SuggestedWords.MAX_SUGGESTIONS + 1,
            SuggestedWordInfo.KIND_TYPED, Dictionary.DICTIONARY_USER_TYPED,
            SuggestedWordInfo.NOT_AN_INDEX,
            SuggestedWordInfo.NOT_A_CONFIDENCE
        )
        suggestions.add(typedWordInfo)
        var i = 0
        for (span in range.getSuggestionSpansAtWord()) {
            for (s in span.suggestions) {
                ++i
                if (!TextUtils.equals(s, typedWordString)) {
                    suggestions.add(
                        SuggestedWordInfo(
                            s,
                            "", SuggestedWords.MAX_SUGGESTIONS - i,
                            SuggestedWordInfo.KIND_RESUMED, Dictionary.DICTIONARY_RESUMED,
                            SuggestedWordInfo.NOT_AN_INDEX,
                            SuggestedWordInfo.NOT_A_CONFIDENCE
                        )
                    )
                }
            }
        }
        if (!TextUtils.isDigitsOnly(typedWordString)) {
            val codePoints = StringUtils.toCodePointArray(typedWordString)
            mWordComposer.setComposingWord(codePoints, mLatinIME.getCoordinatesForCurrentKeyboard(codePoints))
            mWordComposer.setCursorPositionWithinWord(typedWordString.codePointCount(0, numberOfCharsInWordBeforeCursor))
            mConnection.setComposingRegion(
                expectedCursorPosition - numberOfCharsInWordBeforeCursor,
                expectedCursorPosition + range.getNumberOfCharsInWordAfterCursor()
            )
        }
        if (suggestions.size <= 1) {
            mInputLogicHandler.getSuggestedWords {
                getSuggestedWords(
                    SuggestedWords.INPUT_STYLE_TYPING,
                    SuggestedWords.NOT_A_SEQUENCE_NUMBER
                ) { words ->
                    if (words != null) doShowSuggestionsAndClearAutoCorrectionIndicator(words)
                }
            }
        } else {
            val suggestedWords = SuggestedWords(
                suggestions, null, typedWordInfo, false,
                false, false, SuggestedWords.INPUT_STYLE_RECORRECTION, SuggestedWords.NOT_A_SEQUENCE_NUMBER
            )
            doShowSuggestionsAndClearAutoCorrectionIndicator(suggestedWords)
        }
    }

    private fun doShowSuggestionsAndClearAutoCorrectionIndicator(suggestedWords: SuggestedWords) {
        mIsAutoCorrectionIndicatorOn = false
        mLatinIME.mHandler.setSuggestions(suggestedWords)
    }

    private fun revertCommit(inputTransaction: InputTransaction) {
        val originallyTypedWord = mLastComposedWord.mTypedWord
        val committedWord = mLastComposedWord.mCommittedWord
        val committedWordString = committedWord.toString()
        val cancelLength = committedWord.length
        val separatorString = mLastComposedWord.mSeparatorString
        val usePhantomSpace = separatorString == Constants.STRING_SPACE
        val separatorLength = separatorString.length
        val deleteLength = cancelLength + separatorLength
        if (DebugFlags.DEBUG_ENABLED) {
            if (mWordComposer.isComposingWord()) {
                throw RuntimeException("revertCommit, but we are composing a word")
            }
            val wordBeforeCursor = mConnection.getTextBeforeCursor(deleteLength, 0)?.subSequence(0, cancelLength)
            if (!TextUtils.equals(committedWord, wordBeforeCursor)) {
                throw RuntimeException("revertCommit check failed: we thought we were reverting \"$committedWord\", but before the cursor we found \"$wordBeforeCursor\"")
            }
        }
        mConnection.deleteTextBeforeCursor(deleteLength)
        if (!TextUtils.isEmpty(committedWord)) {
            unlearnWord(committedWordString, inputTransaction.settingsValues, Constants.EVENT_REVERT)
        }
        val stringToCommit = originallyTypedWord + (if (usePhantomSpace) "" else separatorString)
        val textToCommit = SpannableString(stringToCommit)
        if (committedWord is SpannableString) {
            val spans = committedWord.getSpans(0, committedWord.length, Any::class.java)
            val lastCharIndex = textToCommit.length - 1
            val suggestions = ArrayList<String>()
            suggestions.add(committedWordString)
            for (span in spans) {
                if (span is SuggestionSpan) {
                    for (suggestion in span.suggestions) {
                        if (suggestion != committedWordString) {
                            suggestions.add(suggestion)
                        }
                    }
                } else {
                    textToCommit.setSpan(span, 0, lastCharIndex, committedWord.getSpanFlags(span))
                }
            }
            textToCommit.setSpan(
                SuggestionSpan(
                    mLatinIME, inputTransaction.settingsValues.mLocale,
                    suggestions.toTypedArray(), 0, null
                ),
                0, lastCharIndex, 0
            )
        }

        if (inputTransaction.settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces) {
            mConnection.commitText(textToCommit, 1)
            if (usePhantomSpace) {
                mJustRevertedACommit = true
                mSpaceState = SpaceState.PHANTOM
            }
        } else {
            val codePoints = StringUtils.toCodePointArray(stringToCommit)
            mWordComposer.setComposingWord(codePoints, mLatinIME.getCoordinatesForCurrentKeyboard(codePoints))
            setComposingTextInternal(textToCommit, 1)
        }
        mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD
        inputTransaction.setRequiresUpdateSuggestions()
    }

    private fun getActualCapsMode(settingsValues: SettingsValues, keyboardShiftMode: Int): Int {
        if (keyboardShiftMode != WordComposer.CAPS_MODE_AUTO_SHIFTED) {
            return keyboardShiftMode
        }
        val auto = getCurrentAutoCapsState(settingsValues)
        if (0 != (auto and TextUtils.CAP_MODE_CHARACTERS)) {
            return WordComposer.CAPS_MODE_AUTO_SHIFT_LOCKED
        }
        if (0 != auto) {
            return WordComposer.CAPS_MODE_AUTO_SHIFTED
        }
        return WordComposer.CAPS_MODE_OFF
    }

    val isComposingWord: Boolean
        get() = mWordComposer.isComposingWord()

    fun getCurrentAutoCapsState(settingsValues: SettingsValues): Int {
        if (!settingsValues.mAutoCap) return Constants.TextUtils.CAP_MODE_OFF

        val ei = getCurrentInputEditorInfo() ?: return Constants.TextUtils.CAP_MODE_OFF
        var inputType = ei.inputType
        if (!InputTypeUtils.isAnyPasswordInputType(inputType)
            && !InputTypeUtils.isUriOrEmailType(inputType)
            && (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
        ) {
            if (settingsValues.mForceAutoCaps
                || (inputType and (InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)) == 0
            ) {
                inputType = inputType or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
        }
        return mConnection.getCursorCapsMode(
            inputType, settingsValues.mSpacingAndPunctuations, SpaceState.PHANTOM == mSpaceState
        )
    }

    fun getCurrentRecapitalizeState(): RecapitalizeMode? {
        if (!mRecapitalizeStatus.isStarted()
            || !mRecapitalizeStatus.isSetAt(mConnection.expectedSelectionStart, mConnection.expectedSelectionEnd)
        ) {
            return null
        }
        return mRecapitalizeStatus.getCurrentMode()
    }

    private fun getCurrentInputEditorInfo(): EditorInfo {
        return mLatinIME.currentInputEditorInfo
    }

    fun getNgramContextFromNthPreviousWordForSuggestion(
        spacingAndPunctuations: SpacingAndPunctuations,
        nthPreviousWord: Int
    ): NgramContext {
        if (spacingAndPunctuations.mCurrentLanguageHasSpaces) {
            return mConnection.getNgramContextFromNthPreviousWord(spacingAndPunctuations, nthPreviousWord)
        }
        if (LastComposedWord.NOT_A_COMPOSED_WORD == mLastComposedWord) {
            return NgramContext.BEGINNING_OF_SENTENCE
        }
        return NgramContext(NgramContext.WordInfo(mLastComposedWord.mCommittedWord.toString()))
    }

    private fun performEditorAction(
        actionId: Int,
        settingsValues: SettingsValues,
        handler: LatinIME.UIHandler
    ) {
        if (mWordComposer.isComposingWord()) {
            val typedWord = mWordComposer.getTypedWord()
            val ngramContext = mConnection.getNgramContextFromNthPreviousWord(settingsValues.mSpacingAndPunctuations, 1)
            performAdditionToUserHistoryDictionary(settingsValues, typedWord, ngramContext)
            mLastComposedWord = mWordComposer.commitWord(
                LastComposedWord.COMMIT_TYPE_USER_TYPED_WORD, typedWord,
                LastComposedWord.NOT_A_SEPARATOR, ngramContext
            )
            mConnection.finishComposingText()
            StatsUtils.onWordCommitUserTyped(typedWord, mWordComposer.isBatchMode())
        }
        mConnection.performEditorAction(actionId)
    }

    private fun performSpecificTldProcessingOnTextInput(text: String): String {
        if (text.length <= 1 || text[0] != Constants.CODE_PERIOD.toChar()
            || !Character.isLetter(text[1])
        ) {
            return text
        }
        mSpaceState = SpaceState.NONE
        val codePointBeforeCursor = mConnection.codePointBeforeCursor
        if (Constants.CODE_PERIOD == codePointBeforeCursor) {
            return text.substring(1)
        }
        return text
    }

    private fun onSettingsKeyPressed() {
        mLatinIME.displaySettingsDialog()
    }

    private fun resetEntireInputState(
        newSelStart: Int,
        newSelEnd: Int,
        clearSuggestionStrip: Boolean
    ) {
        val shouldFinishComposition = mWordComposer.isComposingWord()
        resetComposingState(true /* alsoResetLastComposedWord */)
        if (clearSuggestionStrip) {
            mSuggestionStripViewAccessor.setNeutralSuggestionStrip()
        }
        mConnection.resetCachesUponCursorMoveAndReturnSuccess(newSelStart, newSelEnd, shouldFinishComposition)
    }

    private fun resetComposingState(alsoResetLastComposedWord: Boolean) {
        mWordComposer.reset()
        if (alsoResetLastComposedWord) {
            mLastComposedWord = LastComposedWord.NOT_A_COMPOSED_WORD
        }
    }

    private fun getDictionaryFacilitatorLocale(): Locale {
        return mDictionaryFacilitator.currentLocale ?: Locale.ROOT
    }

    private fun getTextWithUnderline(text: String): CharSequence {
        return if (mIsAutoCorrectionIndicatorOn) {
            getTextWithAutoCorrectionIndicatorUnderline(
                mLatinIME, text, getDictionaryFacilitatorLocale()
            )
        } else {
            text
        }
    }

    fun sendDownUpKeyEvent(keyCode: Int) {
        sendDownUpKeyEventWithMetaState(keyCode, 0)
    }

    fun sendDownUpKeyEventWithMetaState(keyCode: Int, metaState: Int) {
        val eventTime = SystemClock.uptimeMillis()
        mConnection.sendKeyEvent(
            KeyEvent(
                eventTime, eventTime,
                KeyEvent.ACTION_DOWN, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
        mConnection.sendKeyEvent(
            KeyEvent(
                SystemClock.uptimeMillis(), eventTime,
                KeyEvent.ACTION_UP, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
    }

    private fun insertAutomaticSpaceIfOptionsAndTextAllow(settingsValues: SettingsValues) {
        if (settingsValues.shouldInsertSpacesAutomatically()
            && settingsValues.mSpacingAndPunctuations.mCurrentLanguageHasSpaces
            && !textBeforeCursorMayBeUrlOrSimilar(settingsValues, true)
            && !mConnection.textBeforeCursorLooksLikeURL()
            && !(mConnection.codePointBeforeCursor == Constants.CODE_PERIOD
                && mConnection.wordBeforeCursorMayBeEmail())
        ) {
            mConnection.commitCodePoint(Constants.CODE_SPACE)
        }
    }

    private fun textBeforeCursorMayBeUrlOrSimilar(settingsValues: SettingsValues, forAutoSpace: Boolean): Boolean {
        if (InputTypeUtils.isUriOrEmailType(settingsValues.mInputAttributes.mInputType)
            && (if (forAutoSpace) {
                mConnection.nonWordCodePointAndNoSpaceBeforeCursor(settingsValues.mSpacingAndPunctuations)
            } else {
                !mConnection.spaceBeforeCursor()
            })
        ) {
            return true
        }
        if (settingsValues.mUrlDetectionEnabled
            && settingsValues.mSpacingAndPunctuations.containsSometimesWordConnector(mWordComposer.getTypedWord())
        ) {
            return true
        }
        val textBeforeCursor = mConnection.getTextBeforeCursor(mWordComposer.getTypedWord().length + 3, 0)
        if (textBeforeCursor != null && textBeforeCursor.toString().startsWith("://")) {
            return true
        }
        return false
    }

    fun onUpdateTailBatchInputCompleted(
        settingsValues: SettingsValues,
        suggestedWords: SuggestedWords,
        keyboardSwitcher: KeyboardSwitcher
    ) {
        val batchInputText = if (suggestedWords.isEmpty) null else suggestedWords.getWord(0)
        if (batchInputText.isNullOrEmpty()) {
            return
        }
        mConnection.beginBatchEdit()
        if (SpaceState.PHANTOM == mSpaceState) {
            insertAutomaticSpaceIfOptionsAndTextAllow(settingsValues)
            mSpaceState = SpaceState.NONE
        }
        enterInlineEmojiSearchIfNeeded(batchInputText.codePointAt(0), settingsValues)
        mWordComposer.setBatchInputWord(batchInputText)
        setComposingTextInternal(batchInputText, 1)
        mConnection.endBatchEdit()
        if (settingsValues.mAutospaceAfterGestureTyping) {
            mSpaceState = SpaceState.PHANTOM
        }
        keyboardSwitcher.requestUpdatingShiftState(
            getCurrentAutoCapsState(settingsValues),
            getCurrentRecapitalizeState()
        )

        if (isInlineEmojiSearchAction()) {
            searchForEmojiInline(SuggestedWords.NOT_A_SEQUENCE_NUMBER) { words ->
                if (words != null) mLatinIME.setSuggestions(words)
            }
        }
    }

    fun commitTyped(settingsValues: SettingsValues, separatorString: String) {
        if (!mWordComposer.isComposingWord()) return
        val typedWord = mWordComposer.getTypedWord()
        if (typedWord.isNotEmpty()) {
            val isBatchMode = mWordComposer.isBatchMode()
            commitChosenWord(settingsValues, typedWord, LastComposedWord.COMMIT_TYPE_USER_TYPED_WORD, separatorString)
            StatsUtils.onWordCommitUserTyped(typedWord, isBatchMode)
        }
    }

    private fun commitCurrentAutoCorrection(
        settingsValues: SettingsValues,
        separator: String,
        handler: LatinIME.UIHandler
    ) {
        if (handler.hasPendingUpdateSuggestions()) {
            handler.cancelUpdateSuggestionStrip()
            performUpdateSuggestionStripSync(settingsValues, SuggestedWords.INPUT_STYLE_TYPING)
        }
        val autoCorrectionOrNull = mWordComposer.getAutoCorrectionOrNull()
        val typedWord = mWordComposer.getTypedWord()
        val stringToCommit = autoCorrectionOrNull?.mWord ?: typedWord
        if (stringToCommit != null) {
            val isBatchMode = mWordComposer.isBatchMode()
            commitChosenWord(settingsValues, stringToCommit, LastComposedWord.COMMIT_TYPE_DECIDED_WORD, separator)
            if (typedWord != stringToCommit) {
                mConnection.commitCorrection(
                    CorrectionInfo(
                        mConnection.expectedSelectionEnd - stringToCommit.length,
                        typedWord, stringToCommit
                    )
                )
                val prevWordsContext = autoCorrectionOrNull?.mPrevWordsContext ?: ""
                StatsUtils.onAutoCorrection(
                    typedWord, stringToCommit, isBatchMode,
                    mDictionaryFacilitator, prevWordsContext
                )
                StatsUtils.onWordCommitAutoCorrect(stringToCommit, isBatchMode)
            } else {
                StatsUtils.onWordCommitUserTyped(stringToCommit, isBatchMode)
            }
        }
    }

    private fun commitChosenWord(
        settingsValues: SettingsValues,
        chosenWord: String,
        commitType: Int,
        separatorString: String
    ) {
        var startTimeMillis = 0L
        if (DebugFlags.DEBUG_ENABLED) {
            startTimeMillis = System.currentTimeMillis()
            Log.d(TAG, "commitChosenWord() : [$chosenWord]")
        }
        val isEnabled = TextExpanderUtils.isEnabled(mLatinIME)
        if (isEnabled) {
            val textBefore = mConnection.getTextBeforeCursor(50, 0)
            if (textBefore != null) {
                val textStr = textBefore.toString()
                val result = TextExpanderUtils.getExpandedWordForTyped(chosenWord, textStr, mLatinIME)
                if (result != null) {
                    if (mJustRevertedExpandedShortcut != null
                        && result.matchedString.equals(mJustRevertedExpandedShortcut, ignoreCase = true)
                    ) {
                        // Skip re-expanding
                    } else {
                        mConnection.commitText(getTextWithSuggestionSpan(mLatinIME, chosenWord, mSuggestedWords, getDictionaryFacilitatorLocale()), 1)
                        mConnection.deleteTextBeforeCursor(result.prefixLength + chosenWord.length)
                        commitExpandedText(result.matchedString, result.expandedText)
                        resetComposingState(true)
                        return
                    }
                }
            }
        }
        val chosenWordWithSuggestions = getTextWithSuggestionSpan(
            mLatinIME, chosenWord, mSuggestedWords, getDictionaryFacilitatorLocale()
        )
        if (DebugFlags.DEBUG_ENABLED) {
            var runTimeMillis = System.currentTimeMillis() - startTimeMillis
            Log.d(TAG, "commitChosenWord() : $runTimeMillis ms to run SuggestionSpanUtils.getTextWithSuggestionSpan()")
            startTimeMillis = System.currentTimeMillis()
        }
        val ngramContext = mConnection.getNgramContextFromNthPreviousWord(
            settingsValues.mSpacingAndPunctuations, if (mWordComposer.isComposingWord()) 2 else 1
        )
        if (DebugFlags.DEBUG_ENABLED) {
            var runTimeMillis = System.currentTimeMillis() - startTimeMillis
            Log.d(TAG, "commitChosenWord() : $runTimeMillis ms to run Connection.getNgramContextFromNthPreviousWord()")
            Log.d(TAG, "commitChosenWord() : NgramContext = $ngramContext")
            startTimeMillis = System.currentTimeMillis()
        }
        mConnection.commitText(chosenWordWithSuggestions, 1)
        if (DebugFlags.DEBUG_ENABLED) {
            var runTimeMillis = System.currentTimeMillis() - startTimeMillis
            Log.d(TAG, "commitChosenWord() : $runTimeMillis ms to run Connection.commitText")
            startTimeMillis = System.currentTimeMillis()
        }
        performAdditionToUserHistoryDictionary(settingsValues, chosenWord, ngramContext)
        if (DebugFlags.DEBUG_ENABLED) {
            var runTimeMillis = System.currentTimeMillis() - startTimeMillis
            Log.d(TAG, "commitChosenWord() : $runTimeMillis ms to run performAdditionToUserHistoryDictionary()")
            startTimeMillis = System.currentTimeMillis()
        }
        mLastComposedWord = mWordComposer.commitWord(commitType, chosenWord, separatorString, ngramContext)
        if (DebugFlags.DEBUG_ENABLED) {
            val runTimeMillis = System.currentTimeMillis() - startTimeMillis
            Log.d(TAG, "commitChosenWord() : $runTimeMillis ms to run WordComposer.commitWord()")
        }
    }

    private fun wrapSelection(start: Int, end: Int) {
        val selected = mConnection.getSelectedText(0) ?: ""
        if (mConnection.codePointBeforeCursor == start) {
            val afterCursor = mConnection.getTextAfterCursor(1, 0)
            if (!afterCursor.isNullOrEmpty() && afterCursor[0].code == end) {
                mConnection.setSelection(mConnection.expectedSelectionStart - 1, mConnection.expectedSelectionEnd + 1)
                mConnection.commitText(selected, 1)
                return
            }
        }
        mConnection.commitText(
            StringUtils.newSingleCodePointString(start) + selected + StringUtils.newSingleCodePointString(end), 1
        )
    }

    fun retryResetCachesAndReturnSuccess(
        tryResumeSuggestions: Boolean,
        remainingTries: Int,
        handler: LatinIME.UIHandler
    ): Boolean {
        val shouldFinishComposition = mConnection.hasSelection() || !mConnection.isCursorPositionKnown()
        if (!mConnection.resetCachesUponCursorMoveAndReturnSuccess(
                mConnection.expectedSelectionStart, mConnection.expectedSelectionEnd, shouldFinishComposition
            )
        ) {
            if (0 < remainingTries) {
                handler.postResetCaches(tryResumeSuggestions, remainingTries - 1)
                return false
            }
        }
        mConnection.tryFixIncorrectCursorPosition()
        if (tryResumeSuggestions) {
            handler.postResumeSuggestions(true /* shouldDelay */)
        }
        return true
    }

    fun getSuggestedWords(
        inputStyle: Int,
        sequenceNumber: Int,
        callback: Suggest.OnGetSuggestedWordsCallback
    ) {
        val keyboard = KeyboardSwitcher.getInstance().keyboard
        if (keyboard == null) {
            callback.onGetSuggestedWords(SuggestedWords.getEmptyInstance())
            return
        }
        if (inputStyle != SuggestedWords.INPUT_STYLE_UPDATE_BATCH
            && inputStyle != SuggestedWords.INPUT_STYLE_TAIL_BATCH
            && isInlineEmojiSearchAction()
        ) {
            searchForEmojiInline(sequenceNumber, callback)
            return
        }
        val settingsValues = Settings.getValues()
        mWordComposer.adviseCapitalizedModeBeforeFetchingSuggestions(
            getActualCapsMode(settingsValues, KeyboardSwitcher.getInstance().keyboardShiftMode)
        )
        try {
            val suggestedWords = mSuggest.getSuggestedWords(
                mWordComposer,
                getNgramContextFromNthPreviousWordForSuggestion(
                    settingsValues.mSpacingAndPunctuations,
                    if (mWordComposer.isComposingWord()) 2 else 1
                ),
                keyboard,
                settingsValues.mSettingsValuesForSuggestion,
                settingsValues.mAutoCorrectEnabled,
                inputStyle, sequenceNumber
            )
            callback.onGetSuggestedWords(suggestedWords)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching suggested words, using empty words instead", e)
            callback.onGetSuggestedWords(SuggestedWords.getEmptyInstance())
            KeyboardSwitcher.getInstance().showToast("Error getting suggestions", true)
        }
    }

    private fun setComposingTextInternal(newComposingText: CharSequence, newCursorPosition: Int) {
        setComposingTextInternalWithBackgroundColor(
            newComposingText, newCursorPosition, Color.TRANSPARENT, newComposingText.length
        )
    }

    private fun setComposingTextInternalWithBackgroundColor(
        newComposingText: CharSequence,
        newCursorPosition: Int,
        backgroundColor: Int,
        coloredTextLength: Int
    ) {
        if (!mConnection.setComposingTextWithBackgroundColor(
                newComposingText, newCursorPosition, backgroundColor, coloredTextLength
            )
        ) {
            mWordComposer.reset()
        }
    }

    fun getPrivateCommandPerformer(): PrivateCommandPerformer {
        return mConnection
    }

    fun getComposingStart(): Int {
        if (!mConnection.isCursorPositionKnown() || mConnection.hasSelection()) {
            return -1
        }
        return mConnection.expectedSelectionStart - mWordComposer.size()
    }

    fun getComposingLength(): Int {
        return mWordComposer.size()
    }

    private fun enterInlineEmojiSearchIfNeeded(codePoint: Int, settingsValues: SettingsValues) {
        if (!settingsValues.mInlineEmojiSearch || mEmojiDictionaryFacilitator == null || isInlineEmojiSearchAction()) {
            return
        }
        if (isStartOfInlineEmojiSearch(
                codePoint, mConnection.codePointBeforeCursor,
                mConnection.charBeforeBeforeCursor, settingsValues
            )
        ) {
            setInlineEmojiSearchAction(true)
        }
    }

    private fun updateInlineEmojiSearch() {
        if (!Settings.getValues().mInlineEmojiSearch || mEmojiDictionaryFacilitator == null) {
            if (isInlineEmojiSearchAction()) {
                setInlineEmojiSearchAction(false)
            }
            return
        }
        setInlineEmojiSearchAction(getInlineEmojiSearchString() != null)
    }

    private fun setInlineEmojiSearchAction(on: Boolean) {
        if (on != isInlineEmojiSearchAction()) {
            KeyboardSwitcher.getInstance().loadKeyboard(
                mLatinIME.currentInputEditorInfo, Settings.getValues(),
                mLatinIME.currentAutoCapsState, mLatinIME.currentRecapitalizeState,
                if (on) KeyboardLayoutSet.InternalAction(KeyCode.INLINE_EMOJI_SEARCH_DONE, "!icon/close_history") else null
            )
        }
    }

    private fun searchForEmojiInline(sequenceNumber: Int, callback: Suggest.OnGetSuggestedWordsCallback) {
        val input = getInlineEmojiSearchString()
        if (input.isNullOrEmpty()) {
            callback.onGetSuggestedWords(SuggestedWords.getEmptyInstance())
            return
        }

        val suggestions = mEmojiDictionaryFacilitator?.getSuggestions(input.splitOnWhitespace())
        if (suggestions == null || suggestions.isEmpty()) {
            callback.onGetSuggestedWords(SuggestedWords.getEmptyInstance())
            return
        }

        val typedWordInfo = SuggestedWordInfo(
            input, "", SuggestedWordInfo.MAX_SCORE, SuggestedWordInfo.KIND_TYPED,
            Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE
        )
        val suggestedWordInfos = ArrayList<SuggestedWordInfo>(suggestions.size + 1)
        suggestedWordInfos.add(typedWordInfo)
        for (suggestion in suggestions) {
            if (suggestion.isEmoji) {
                Suggest.addDebugInfo(suggestion, input)
                suggestedWordInfos.add(suggestion)
            }
        }
        callback.onGetSuggestedWords(
            SuggestedWords(
                suggestedWordInfos, suggestions.mRawSuggestions, typedWordInfo,
                false, false, false, SuggestedWords.INPUT_STYLE_TYPING, sequenceNumber
            )
        )
    }

    private fun deleteTextReplacedByEmoji() {
        mConnection.finishComposingText()
        val inlineEmojiSearchString = getInlineEmojiSearchString()
        if (inlineEmojiSearchString != null) {
            mConnection.deleteTextBeforeCursor(inlineEmojiSearchString.length + 1)
        } else {
            Log.e("inlineEmojiSearch", "Inconsistent state - inlineEmojiSearchString is null")
        }
    }

    private fun getInlineEmojiSearchString(): String? {
        if (mEmojiDictionaryFacilitator == null) {
            return null
        }
        return getInlineEmojiSearchString(mConnection.getTextBeforeCursor(50, 0))
    }

    fun updateEmojiDictionary(locale: Locale?) {
        val sv = Settings.getValues()
        if (sv.mInlineEmojiSearch && sv.needsToLookupSuggestions() && locale != null) {
            val facilitator = mEmojiDictionaryFacilitator
            if (facilitator == null || !facilitator.isForLocale(locale)) {
                closeEmojiDictionary()
                val dictFile = DictionaryInfoUtils.getCachedDictForLocaleAndType(locale, "emoji", mLatinIME)
                val dictionary = if (dictFile != null) DictionaryFactory.getDictionary(dictFile, locale) else null
                mEmojiDictionaryFacilitator = if (dictionary != null) SingleDictionaryFacilitator(dictionary) else null
            }
        } else {
            closeEmojiDictionary()
        }
    }

    private fun closeEmojiDictionary() {
        mEmojiDictionaryFacilitator?.closeDictionaries()
        mEmojiDictionaryFacilitator = null
    }

    private fun handleCustomAIKey(index: Int) {
        val prefs = helium314.keyboard.latin.utils.DeviceProtectedUtils.getSharedPreferences(mLatinIME)
        var prompt = prefs.getString("pref_custom_ai_prompt_$index", "") ?: ""
        val systemInstructionBuilder = StringBuilder()
        var shouldAppend = false

        if (prompt.contains("#editor")) {
            systemInstructionBuilder.append(" You are a text editor tool. Output ONLY the edited text. Do not add any conversational filler.")
            prompt = prompt.replace("#editor", "").trim()
        }
        if (prompt.contains("#outputonly")) {
            systemInstructionBuilder.append(" Output ONLY the result. Do not add introductions or explanations.")
            prompt = prompt.replace("#outputonly", "").trim()
        }
        if (prompt.contains("#proofread")) {
            systemInstructionBuilder.append(" You are a proofreader. Fix grammar and spelling errors. Output ONLY the fixed text.")
            prompt = prompt.replace("#proofread", "").trim()
        }
        if (prompt.contains("#paraphrase")) {
            systemInstructionBuilder.append(" You are a paraphrasing tool. Rewrite the text using different words while keeping the meaning. Output ONLY the result.")
            prompt = prompt.replace("#paraphrase", "").trim()
        }
        if (prompt.contains("#summarize")) {
            systemInstructionBuilder.append(" You are a summarizer. Provide a concise summary of the text. Output ONLY the summary.")
            prompt = prompt.replace("#summarize", "").trim()
        }
        if (prompt.contains("#expand")) {
            systemInstructionBuilder.append(" You are a creative writing assistant. Expand on the text with more details. Output ONLY the result.")
            prompt = prompt.replace("#expand", "").trim()
        }
        if (prompt.contains("#toneshift")) {
            systemInstructionBuilder.append(" You are a tone modifier. Adjust the tone as requested. Output ONLY the result.")
            prompt = prompt.replace("#toneshift", "").trim()
        }
        if (prompt.contains("#generate")) {
            systemInstructionBuilder.append(" You are a creative content generator. Output ONLY the generated content.")
            prompt = prompt.replace("#generate", "").trim()
        }
        val systemInstruction = systemInstructionBuilder.toString()

        if (prompt.contains("#append")) {
            shouldAppend = true
            prompt = prompt.replace("#append", "").trim()
        }

        var showThinking = false
        if (prompt.contains("#showthought")) {
            showThinking = true
            prompt = prompt.replace("#showthought", "").trim()
        }

        if (TextUtils.isEmpty(prompt)) {
            KeyboardSwitcher.getInstance().showToast("Custom AI key is not set. Long-press to configure.", true)
            return
        }

        prompt += systemInstruction

        var textToProcess: String
        val hasSelection = mConnection.hasSelection()

        if (hasSelection) {
            val selectedText = mConnection.getSelectedText(0)
            textToProcess = selectedText?.toString() ?: ""
            if (shouldAppend) {
                mConnection.setSelection(mConnection.expectedSelectionEnd, mConnection.expectedSelectionEnd)
            }
        } else {
            val maxChars = 60000
            var textBefore: CharSequence? = null
            var textAfter: CharSequence? = null
            try {
                textBefore = mConnection.getTextBeforeCursor(maxChars, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get text before cursor: $e")
            }

            try {
                textAfter = mConnection.getTextAfterCursor(maxChars, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get text after cursor: $e")
                try {
                    textAfter = mConnection.getTextAfterCursor(2048, 0)
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to get text after cursor (retry): $e2")
                }
            }

            val before = textBefore?.toString() ?: ""
            val after = textAfter?.toString() ?: ""
            textToProcess = before + after

            if (!shouldAppend) {
                mConnection.selectAll()
            } else {
                if (textToProcess.isNotEmpty()) {
                    if (after.isNotEmpty() && mConnection.expectedSelectionEnd >= 0) {
                        val newPos = mConnection.expectedSelectionEnd + after.length
                        mConnection.setSelection(newPos, newPos)
                    }
                }
            }
        }

        ProofreadHelper.customAsync(
            mLatinIME,
            textToProcess, prompt, hasSelection, showThinking,
            onSuccess = { resultText ->
                mLatinIME.onTextInput(resultText)
            },
            onError = { errorMessage ->
                Log.e(TAG, "Custom AI Error: $errorMessage")
                KeyboardSwitcher.getInstance().showToast("AI Error: $errorMessage", true)
            }
        )
    }

    private fun tryJumpToNextPlaceholder(): Boolean {
        val before = mConnection.getTextBeforeCursor(1000, 0)
        val after = mConnection.getTextAfterCursor(1000, 0)
        val beforeStr = before?.toString() ?: ""
        val afterStr = after?.toString() ?: ""
        val fullText = beforeStr + afterStr

        Log.d(TAG, "tryJumpToNextPlaceholder: beforeStr=[$beforeStr] afterStr=[$afterStr]")

        val pattern = java.util.regex.Pattern.compile("%cursor(\\d+)%")
        val matcher = pattern.matcher(fullText)

        var bestStart = -1
        var bestEnd = -1
        var lowestNum = Int.MAX_VALUE

        while (matcher.find()) {
            try {
                val num = matcher.group(1)?.toInt() ?: continue
                Log.d(TAG, "tryJumpToNextPlaceholder: found %cursor$num% at [${matcher.start()},${matcher.end()})")
                if (num < lowestNum) {
                    lowestNum = num
                    bestStart = matcher.start()
                    bestEnd = matcher.end()
                }
            } catch (e: NumberFormatException) {
                // ignore
            }
        }

        if (bestStart != -1) {
            mConnection.finishComposingText()
            mConnection.tryFixIncorrectCursorPosition()
            resetComposingState(true)

            val before2 = mConnection.getTextBeforeCursor(1000, 0)
            val beforeStr2 = before2?.toString() ?: ""
            val cursorPositionInFull = beforeStr2.length

            val currentSelectionEnd = mConnection.expectedSelectionEnd
            val targetStart = currentSelectionEnd - cursorPositionInFull + bestStart
            val targetEnd = currentSelectionEnd - cursorPositionInFull + bestEnd
            Log.d(TAG, "tryJumpToNextPlaceholder: jumping to [$targetStart,$targetEnd) currentSelEnd=$currentSelectionEnd")
            mConnection.beginBatchEdit()
            mConnection.setSelection(targetStart, targetEnd)
            mConnection.commitText("", 1)
            mConnection.endBatchEdit()
            return true
        }
        Log.d(TAG, "tryJumpToNextPlaceholder: no placeholder found")
        return false
    }

    private fun commitExpandedText(shortcut: String, expanded: String) {
        val cursorOffset = expanded.indexOf("%cursor%")
        if (cursorOffset != -1) {
            val finalExpandedText = expanded.replace("%cursor%", "")
            mConnection.commitText(finalExpandedText, 1)
            mLastExpandedText = finalExpandedText
            mLastShortcutText = shortcut
            mLastExpandedCursorOffset = cursorOffset
            val moveBackAmount = finalExpandedText.length - cursorOffset
            if (moveBackAmount > 0) {
                val newCursorPos = mConnection.expectedSelectionEnd - moveBackAmount
                mConnection.setSelection(newCursorPos, newCursorPos)
            }
            mLastExpandedCursorPosition = mConnection.expectedSelectionEnd
            return
        }

        val pattern = java.util.regex.Pattern.compile("%cursor(\\d+)%")
        val matcher = pattern.matcher(expanded)
        var bestStart = -1
        var bestEnd = -1
        var lowestNum = Int.MAX_VALUE
        while (matcher.find()) {
            try {
                val num = matcher.group(1)?.toInt() ?: continue
                if (num < lowestNum) {
                    lowestNum = num
                    bestStart = matcher.start()
                    bestEnd = matcher.end()
                }
            } catch (e: NumberFormatException) {
                // ignore
            }
        }

        if (bestStart != -1) {
            val finalExpandedText = expanded.substring(0, bestStart) + expanded.substring(bestEnd)
            mConnection.commitText(finalExpandedText, 1)
            mLastExpandedText = finalExpandedText
            mLastShortcutText = shortcut
            mLastExpandedCursorOffset = bestStart
            val moveBackAmount = finalExpandedText.length - bestStart
            if (moveBackAmount > 0) {
                val newCursorPos = mConnection.expectedSelectionEnd - moveBackAmount
                mConnection.setSelection(newCursorPos, newCursorPos)
            }
        } else {
            mConnection.commitText(expanded, 1)
            mLastExpandedText = expanded
            mLastShortcutText = shortcut
            mLastExpandedCursorOffset = expanded.length
        }
        mLastExpandedCursorPosition = mConnection.expectedSelectionEnd
    }

    companion object {
        private const val TAG = "InputLogic"
        private const val INLINE_EMOJI_SEARCH_MARKER = ':'
        private val THAI_LOCALE = Locale.forLanguageTag("th")
        private val THAI_WORD_BREAK_ITERATOR = ThreadLocal.withInitial { BreakIterator.getWordInstance(THAI_LOCALE) }

        fun isSpaceStrippingPunctuation(codePoint: Int): Boolean {
            return codePoint == '.'.code
                || codePoint == ','.code
                || codePoint == ';'.code
                || codePoint == ':'.code
                || codePoint == '!'.code
                || codePoint == '?'.code
                || codePoint == ')'.code
                || codePoint == ']'.code
                || codePoint == '}'.code
                || codePoint == '؟'.code
                || codePoint == '،'.code
                || codePoint == '؛'.code
                || codePoint == '।'.code
                || codePoint == '॥'.code
                || codePoint == '。'.code
                || codePoint == '、'.code
                || codePoint == '，'.code
                || codePoint == '？'.code
                || codePoint == '！'.code
                || codePoint == '：'.code
                || codePoint == '；'.code
                || codePoint == '）'.code
                || codePoint == '】'.code
                || codePoint == '』'.code
        }

        fun canBeFollowedByDoubleSpacePeriod(codePoint: Int): Boolean {
            return Character.isLetterOrDigit(codePoint)
                || codePoint == Constants.CODE_SINGLE_QUOTE
                || codePoint == Constants.CODE_DOUBLE_QUOTE
                || codePoint == Constants.CODE_CLOSING_PARENTHESIS
                || codePoint == Constants.CODE_CLOSING_SQUARE_BRACKET
                || codePoint == Constants.CODE_CLOSING_CURLY_BRACKET
                || codePoint == Constants.CODE_CLOSING_ANGLE_BRACKET
                || codePoint == Constants.CODE_PLUS
                || codePoint == Constants.CODE_PERCENT
                || Character.getType(codePoint) == Character.OTHER_SYMBOL.toInt()
        }

        fun retrieveOlderSuggestions(
            typedWordInfo: SuggestedWordInfo,
            previousSuggestedWords: SuggestedWords
        ): SuggestedWords {
            val oldSuggestedWords = if (previousSuggestedWords.isPunctuationSuggestions) {
                SuggestedWords.getEmptyInstance()
            } else {
                previousSuggestedWords
            }
            val typedWordAndPreviousSuggestions = SuggestedWords.getTypedWordAndPreviousSuggestions(
                typedWordInfo, oldSuggestedWords
            )
            return SuggestedWords(
                typedWordAndPreviousSuggestions, null,
                typedWordInfo, false, false,
                true, oldSuggestedWords.mInputStyle,
                SuggestedWords.NOT_A_SEQUENCE_NUMBER
            )
        }

        fun isResumableWord(settings: SettingsValues, word: String): Boolean {
            val firstCodePoint = word.codePointAt(0)
            return settings.isWordCodePoint(firstCodePoint)
                && Constants.CODE_SINGLE_QUOTE != firstCodePoint
                && Constants.CODE_DASH != firstCodePoint
        }

        fun isInlineEmojiSearchAction(): Boolean {
            val keyboard = KeyboardSwitcher.getInstance().keyboard
            val internalAction = keyboard?.mId?.mInternalAction
            return internalAction != null && internalAction.code() == KeyCode.INLINE_EMOJI_SEARCH_DONE
        }

        fun isInlineEmojiSearchChar(codePoint: Int): Boolean {
            return Character.isLetterOrDigit(codePoint) || codePoint == '_'.code || codePoint == '+'.code || codePoint == '-'.code
        }

        fun getInlineEmojiSearchString(textBeforeCursor: CharSequence?): String? {
            if (textBeforeCursor == null) {
                return null
            }

            val text = textBeforeCursor.toString()
            val markerIndex = text.lastIndexOf(INLINE_EMOJI_SEARCH_MARKER)
            if (markerIndex < 0 || text.length < markerIndex + 2) {
                return null
            }

            if (markerIndex > 0
                && !isValidInlineEmojiSearchPreviousChar(text.codePointAt(markerIndex - 1), Settings.getValues())
            ) {
                return null
            }

            val searchString = text.substring(markerIndex + 1)
            for (i in 0 until searchString.length) {
                if (!isInlineEmojiSearchChar(searchString.codePointAt(i))) {
                    return null
                }
            }

            return searchString
        }

        fun isStartOfInlineEmojiSearch(
            codePoint: Int,
            codePointBeforeCursor: Int,
            charBeforeBeforeCursor: Int,
            settingsValues: SettingsValues
        ): Boolean {
            return codePointBeforeCursor == INLINE_EMOJI_SEARCH_MARKER.code && codePoint != INLINE_EMOJI_SEARCH_MARKER.code
                && isInlineEmojiSearchChar(codePoint)
                && isValidInlineEmojiSearchPreviousChar(charBeforeBeforeCursor, settingsValues)
        }

        fun isValidInlineEmojiSearchPreviousChar(
            charBeforeBeforeCursor: Int,
            settingsValues: SettingsValues
        ): Boolean {
            return !Character.isDigit(charBeforeBeforeCursor) && !settingsValues.isWordCodePoint(charBeforeBeforeCursor)
        }
    }
}
