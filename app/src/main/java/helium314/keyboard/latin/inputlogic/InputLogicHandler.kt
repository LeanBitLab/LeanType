/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.inputlogic

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.Suggest
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.common.InputPointers

/**
 * A helper to manage deferred tasks for the input logic.
 */
class InputLogicHandler(
    private val latinIMEHandler: LatinIME.UIHandler,
    private val inputLogic: InputLogic
) : Handler.Callback {
    private val nonUIThreadHandler: Handler
    private val lock = Any()
    private var inBatchInput = false // synchronized using lock.

    init {
        val handlerThread = HandlerThread(InputLogicHandler::class.java.simpleName)
        handlerThread.start()
        nonUIThreadHandler = Handler(handlerThread.looper, this)
    }

    fun reset() {
        nonUIThreadHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Handle a message.
     *
     * @see Handler.Callback.handleMessage
     */
    // Called on the Non-UI handler thread by the Handler code.
    override fun handleMessage(msg: Message): Boolean {
        if (msg.what == MSG_GET_SUGGESTED_WORDS) {
            (msg.obj as? Runnable)?.run()
        }
        return true
    }

    // Called on the UI thread by InputLogic.
    fun onStartBatchInput() {
        synchronized(lock) {
            inBatchInput = true
        }
    }

    fun isInBatchInput(): Boolean {
        return inBatchInput
    }

    /**
     * Fetch suggestions corresponding to an update of a batch input.
     *
     * @param batchPointers the updated pointers, including the part that was passed last time.
     * @param sequenceNumber the sequence number associated with this batch input.
     * @param isTailBatchInput true if this is the end of a batch input, false if it's an update.
     */
    // This method can be called from any thread and will see to it that the correct threads
    // are used for parts that require it. This method will send a message to the Non-UI handler
    // thread to pull suggestions, and get the inlined callback to get called on the Non-UI
    // handler thread. If this is the end of a batch input, the callback will then proceed to
    // send a message to the UI handler in LatinIME so that showing suggestions can be done on
    // the UI thread.
    private fun updateBatchInput(
        batchPointers: InputPointers,
        sequenceNumber: Int,
        isTailBatchInput: Boolean
    ) {
        synchronized(lock) {
            if (!inBatchInput) {
                // Batch input has ended or canceled while the message was being delivered.
                return
            }
            inputLogic.wordComposer.setBatchInputPointers(batchPointers)
            getSuggestedWords {
                inputLogic.getSuggestedWords(
                    if (isTailBatchInput) SuggestedWords.INPUT_STYLE_TAIL_BATCH else SuggestedWords.INPUT_STYLE_UPDATE_BATCH,
                    sequenceNumber,
                    object : Suggest.OnGetSuggestedWordsCallback {
                        override fun onGetSuggestedWords(suggestedWords: SuggestedWords?) {
                            if (suggestedWords != null) {
                                showGestureSuggestionsWithPreviewVisuals(suggestedWords, isTailBatchInput)
                            }
                        }
                    }
                )
            }
        }
    }

    private fun showGestureSuggestionsWithPreviewVisuals(
        suggestedWordsForBatchInput: SuggestedWords,
        isTailBatchInput: Boolean
    ) {
        // We're now inside the callback. This always runs on the Non-UI thread,
        // no matter what thread updateBatchInput was originally called on.
        val suggestedWordsToShowSuggestions = if (suggestedWordsForBatchInput.isEmpty) {
            // Use old suggestions if we don't have any new ones.
            // Previous suggestions are found in InputLogic#mSuggestedWords.
            // Since these are the most recent ones and we just recomputed
            // new ones to update them, then the previous ones are there.
            inputLogic.suggestedWords
        } else {
            suggestedWordsForBatchInput
        }
        latinIMEHandler.showGesturePreviewAndSetSuggestions(suggestedWordsToShowSuggestions, isTailBatchInput)
        if (isTailBatchInput) {
            inBatchInput = false
            // The following call schedules onEndBatchInputInternal
            // to be called on the UI thread.
            latinIMEHandler.showTailBatchInputResult(suggestedWordsToShowSuggestions)
        }
    }

    /**
     * Update a batch input.
     *
     * This fetches suggestions and updates the suggestion strip and the floating text preview.
     *
     * @param batchPointers the updated batch pointers.
     * @param sequenceNumber the sequence number associated with this batch input.
     */
    // Called on the UI thread by InputLogic.
    fun onUpdateBatchInput(
        batchPointers: InputPointers,
        sequenceNumber: Int
    ) {
        updateBatchInput(batchPointers, sequenceNumber, false)
    }

    /**
     * Cancel a batch input.
     *
     * Note that as opposed to updateTailBatchInput, we do the UI side of this immediately on the
     * same thread, rather than get this to call a method in LatinIME. This is because
     * canceling a batch input does not necessitate the long operation of pulling suggestions.
     */
    // Called on the UI thread by InputLogic.
    fun onCancelBatchInput() {
        synchronized(lock) {
            inBatchInput = false
        }
    }

    /**
     * Trigger an update for a tail batch input.
     *
     * A tail batch input is the last update for a gesture, the one that is triggered after the
     * user lifts their finger. This method schedules fetching suggestions on the non-UI thread,
     * then when the suggestions are computed it comes back on the UI thread to update the
     * suggestion strip, commit the first suggestion, and dismiss the floating text preview.
     *
     * @param batchPointers the updated batch pointers.
     * @param sequenceNumber the sequence number associated with this batch input.
     */
    // Called on the UI thread by InputLogic.
    fun updateTailBatchInput(
        batchPointers: InputPointers,
        sequenceNumber: Int
    ) {
        updateBatchInput(batchPointers, sequenceNumber, true)
    }

    fun getSuggestedWords(callback: Runnable) {
        nonUIThreadHandler.obtainMessage(MSG_GET_SUGGESTED_WORDS, callback).sendToTarget()
    }

    companion object {
        private const val MSG_GET_SUGGESTED_WORDS = 1
    }
}
