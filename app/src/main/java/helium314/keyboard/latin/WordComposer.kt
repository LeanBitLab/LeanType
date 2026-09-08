/*
 * Copyright (C) 2008 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin

import helium314.keyboard.event.CombinerChain
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.CoordinateUtils
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.define.DecoderSpecificConstants
import java.util.ArrayList

/**
 * A place to store the currently composing word with information such as adjacent key codes as well.
 */
class WordComposer {

    private var mCombinerChain: CombinerChain = CombinerChain("", "")
    private var mCombiningSpec: String? = null

    // The list of events that served to compose this string.
    private val mEvents: ArrayList<Event> = ArrayList(20)
    private val mInputPointers = InputPointers(MAX_WORD_LENGTH)

    private var mAutoCorrection: SuggestedWordInfo? = null
    private var mIsResumed = false
    private var mIsBatchMode = false

    // A memory of the last rejected batch mode suggestion, if any.
    private var mRejectedBatchModeSuggestion: String? = null

    // Cache these values for performance.
    private var mTypedWordCache: CharSequence = ""
    private var mCodePointArrayCache: IntArray? = null
    private var mCapsCount = 0
    private var mDigitsCount = 0
    private var mCapitalizedMode = CAPS_MODE_OFF

    // This is the number of code points entered so far. This is not limited to MAX_WORD_LENGTH.
    private var mCodePointSize = 0
    private var mCursorPositionWithinWord = 0

    /**
     * Whether the composing word has the only first char capitalized.
     */
    private var mIsOnlyFirstCharCapitalized = false

    constructor() {
        mCombinerChain = CombinerChain("", "")
        mEvents.clear()
        mAutoCorrection = null
        mIsResumed = false
        mIsBatchMode = false
        mCursorPositionWithinWord = 0
        mRejectedBatchModeSuggestion = null
        refreshTypedWordCache()
    }

    private constructor(isEmpty: Boolean) {
        mCodePointSize = if (isEmpty) 0 else 1
        mTypedWordCache = ""
        mCodePointArrayCache = null
    }

    fun getComposedDataSnapshot(): ComposedData {
        return ComposedData(getInputPointers(), isBatchMode(), mTypedWordCache.toString())
    }

    /**
     * Restart the combiners, possibly with a new spec.
     *
     * @param combiningSpec The spec string for combining. This is found in the extra value.
     */
    fun restartCombining(combiningSpec: String?) {
        val nonNullCombiningSpec = combiningSpec ?: ""
        if (nonNullCombiningSpec != mCombiningSpec) {
            mCombinerChain = CombinerChain(
                mCombinerChain.composingWordWithCombiningFeedback.toString(),
                nonNullCombiningSpec
            )
            mCombiningSpec = nonNullCombiningSpec
        }
    }

    /**
     * Clear out the keys registered so far.
     */
    fun reset() {
        mCombinerChain.reset()
        mEvents.clear()
        mAutoCorrection = null
        mCapsCount = 0
        mDigitsCount = 0
        mIsOnlyFirstCharCapitalized = false
        mIsResumed = false
        mIsBatchMode = false
        mCursorPositionWithinWord = 0
        mRejectedBatchModeSuggestion = null
        refreshTypedWordCache()
    }

    private fun refreshTypedWordCache() {
        val chain = mCombinerChain
        mTypedWordCache = chain?.composingWordWithCombiningFeedback ?: ""
        mCodePointSize = Character.codePointCount(mTypedWordCache, 0, mTypedWordCache.length)

        // Cache code point array to avoid repeated allocations in cursor movement.
        mCodePointArrayCache = StringUtils.toCodePointArray(mTypedWordCache)
    }

    /**
     * Number of keystrokes in the composing word.
     *
     * @return the number of keystrokes
     */
    fun size(): Int = mCodePointSize

    fun isSingleLetter(): Boolean = size() == 1

    fun isComposingWord(): Boolean = size() > 0

    fun getInputPointers(): InputPointers = mInputPointers

    /**
     * Process an event and return a processed event to apply.
     *
     * @param event the unprocessed event.
     * @return the processed event. Never null, but may be marked as consumed.
     */
    fun processEvent(event: Event): Event {
        val processedEvent = mCombinerChain.processEvent(mEvents, event)

        // The retained state of the combiner chain may have changed while processing the event,
        // so we need to update our cache.
        refreshTypedWordCache()
        mEvents.add(event)
        return processedEvent
    }

    /**
     * Apply a processed input event.
     *
     * All input events should be supported, including software/hardware events, characters as well
     * as deletions, multiple inputs and gestures.
     *
     * @param event the event to apply. Must not be null.
     */
    fun applyProcessedEvent(event: Event) {
        applyProcessedEvent(event, false)
    }

    /**
     * Specifically for KeyCode.MULTIPLE_CODE_POINTS Hangul event: try keeping cursor position
     * because typically nothing changes.
     */
    fun applyProcessedEvent(event: Event, keepCursorPosition: Boolean) {
        mCombinerChain.applyProcessedEvent(event)

        val primaryCode = event.codePoint
        val keyX = event.x
        val keyY = event.y
        val newIndex = size()

        refreshTypedWordCache()

        if (!keepCursorPosition || newIndex == mCodePointSize) {
            mCursorPositionWithinWord = mCodePointSize
        }

        // We may have deleted the last one.
        if (mCodePointSize == 0) {
            mIsOnlyFirstCharCapitalized = false
        }

        if (KeyCode.DELETE != event.keyCode) {
            if (newIndex < MAX_WORD_LENGTH) {
                // In the batch input mode, mInputPointers holds batch input points and shouldn't
                // be overridden by the "typed key" coordinates.
                if (!mIsBatchMode) {
                    // TODO: Set correct pointer id and time.
                    mInputPointers.addPointerAt(newIndex, keyX, keyY, 0, 0)
                }
            }

            mIsOnlyFirstCharCapitalized = if (newIndex == 0) {
                Character.isUpperCase(primaryCode)
            } else {
                mIsOnlyFirstCharCapitalized && !Character.isUpperCase(primaryCode)
            }

            if (Character.isUpperCase(primaryCode)) mCapsCount++
            if (Character.isDigit(primaryCode)) mDigitsCount++
        }

        mAutoCorrection = null
    }

    fun setCursorPositionWithinWord(posWithinWord: Int) {
        mCursorPositionWithinWord = posWithinWord
        // TODO: compute where that puts us inside the events.
    }

    fun isCursorFrontOrMiddleOfComposingWord(): Boolean {
        if (DebugFlags.DEBUG_ENABLED && mCursorPositionWithinWord > mCodePointSize) {
            throw RuntimeException(
                "Wrong cursor position : " + mCursorPositionWithinWord +
                        "in a word of size " + mCodePointSize
            )
        }
        return mCursorPositionWithinWord != mCodePointSize
    }

    fun isCursorInFrontOfComposingWord(): Boolean {
        return isComposingWord() && mCursorPositionWithinWord == 0
    }

    /**
     * When the cursor is moved by the user, update its position.
     *
     * If it falls inside the currently composing word, don't reset the composition, and only update
     * the cursor position.
     *
     * @param expectedMoveAmount How many Java chars to move the cursor. Negative values move
     * backward, positive values move forward.
     * @return true if the cursor is still inside the composing word, false otherwise.
     */
    fun moveCursorByAndReturnIfInsideComposingWord(expectedMoveAmount: Int): Boolean {
        var actualMoveAmount = 0
        var cursorPos = mCursorPositionWithinWord

        // Use cached code point array to avoid repeated allocations.
        val codePoints = mCodePointArrayCache ?: StringUtils.toCodePointArray(mTypedWordCache)

        if (expectedMoveAmount >= 0) {
            // Moving the cursor forward for the expected amount or until the end of the word has
            // been reached, whichever comes first.
            while (actualMoveAmount < expectedMoveAmount && cursorPos < codePoints.size) {
                actualMoveAmount += Character.charCount(codePoints[cursorPos])
                ++cursorPos
            }
        } else {
            // Moving the cursor backward for the expected amount or until the start of the word
            // has been reached, whichever comes first.
            while (actualMoveAmount > expectedMoveAmount && cursorPos > 0) {
                --cursorPos
                actualMoveAmount -= Character.charCount(codePoints[cursorPos])
            }
        }

        // If the actual and expected amounts differ, we crossed the start or the end of the word,
        // so the result would not be inside the composing word.
        if (actualMoveAmount != expectedMoveAmount) {
            return false
        }

        mCursorPositionWithinWord = cursorPos

        mCombinerChain.applyProcessedEvent(
            mCombinerChain.processEvent(mEvents, Event.createCursorMovedEvent(cursorPos))
        )
        return true
    }

    fun setBatchInputPointers(batchPointers: InputPointers) {
        mInputPointers.set(batchPointers)
        mIsBatchMode = true
    }

    fun setBatchInputWord(word: String) {
        reset()
        mIsBatchMode = true

        var i = 0
        val length = word.length
        while (i < length) {
            val codePoint = Character.codePointAt(word, i)

            // We don't want to override the batch input points that are held in mInputPointers.
            val processedEvent = processEvent(
                Event.createEventForCodePointFromUnknownSource(codePoint)
            )
            applyProcessedEvent(processedEvent)

            i = Character.offsetByCodePoints(word, i, 1)
        }
    }

    /**
     * Set the currently composing word to the one passed as an argument.
     *
     * This will register NOT_A_COORDINATE for X and Ys, and use the passed keyboard for proximity.
     *
     * @param codePoints the code points to set as the composing word.
     * @param coordinates the x, y coordinates of the key in the CoordinateUtils format
     */
    fun setComposingWord(codePoints: IntArray, coordinates: IntArray) {
        reset()

        for (i in codePoints.indices) {
            val processedEvent = processEvent(
                Event.createEventForCodePointFromAlreadyTypedText(
                    codePoints[i],
                    CoordinateUtils.xFromArray(coordinates, i),
                    CoordinateUtils.yFromArray(coordinates, i)
                )
            )
            applyProcessedEvent(processedEvent)
        }

        mIsResumed = true
    }

    /**
     * Returns the word as it was typed, without any correction applied.
     *
     * @return the word that was typed so far. Never returns null.
     */
    fun getTypedWord(): String = mTypedWordCache.toString()

    /**
     * Whether this composer is composing or about to compose a word in which only the first letter
     * is a capital.
     */
    fun isOrWillBeOnlyFirstCharCapitalized(): Boolean {
        return if (isComposingWord()) {
            mIsOnlyFirstCharCapitalized
        } else {
            CAPS_MODE_OFF != mCapitalizedMode
        }
    }

    /**
     * Whether or not all of the user typed chars are upper case.
     */
    fun isAllUpperCase(): Boolean {
        if (size() <= 1) {
            return mCapitalizedMode == CAPS_MODE_AUTO_SHIFT_LOCKED ||
                    mCapitalizedMode == CAPS_MODE_MANUAL_SHIFT_LOCKED
        }
        return mCapsCount == size()
    }

    fun wasShiftedNoLock(): Boolean {
        return mCapitalizedMode == CAPS_MODE_AUTO_SHIFTED ||
                mCapitalizedMode == CAPS_MODE_MANUAL_SHIFTED
    }

    fun lastChar(): Char {
        if (!isComposingWord()) return 0.toChar()
        return mTypedWordCache[mTypedWordCache.length - 1]
    }

    /**
     * Returns true if more than one character is upper case.
     */
    fun isMostlyCaps(): Boolean = mCapsCount > 1

    /**
     * Returns true if we have digits in the composing word.
     */
    fun hasDigits(): Boolean = mDigitsCount > 0

    /**
     * Saves the caps mode at the start of composing.
     */
    fun setCapitalizedModeAtStartComposingTime(mode: Int) {
        mCapitalizedMode = mode
    }

    /**
     * Before fetching suggestions, we don't necessarily know about the capitalized mode yet.
     */
    fun adviseCapitalizedModeBeforeFetchingSuggestions(mode: Int) {
        if (!isComposingWord()) {
            mCapitalizedMode = mode
        }
    }

    /**
     * Returns whether the word was automatically capitalized.
     */
    fun wasAutoCapitalized(): Boolean {
        return mCapitalizedMode == CAPS_MODE_AUTO_SHIFT_LOCKED ||
                mCapitalizedMode == CAPS_MODE_AUTO_SHIFTED
    }

    /**
     * Sets the auto-correction for this word.
     */
    fun setAutoCorrection(autoCorrection: SuggestedWordInfo?) {
        mAutoCorrection = autoCorrection
    }

    /**
     * @return the auto-correction for this word, or null if none.
     */
    fun getAutoCorrectionOrNull(): SuggestedWordInfo? = mAutoCorrection

    /**
     * @return whether we started composing this word by resuming suggestion on an existing string.
     */
    fun isResumed(): Boolean = mIsResumed

    /**
     * `type` should be one of the LastComposedWord.COMMIT_TYPE_* constants.
     * committedWord should contain suggestion spans if applicable.
     */
    fun commitWord(
        type: Int,
        committedWord: CharSequence,
        separatorString: String,
        ngramContext: NgramContext
    ): LastComposedWord {
        val events = mEvents

        // Note: currently, we come here whenever we commit a word. If it's a MANUAL_PICK
        // or a DECIDED_WORD we may cancel the commit later; otherwise, deactivate the last
        // composed word to ensure this does not happen.
        val lastComposedWord = LastComposedWord(
            events,
            mInputPointers,
            mTypedWordCache.toString(),
            committedWord,
            separatorString,
            ngramContext,
            mCapitalizedMode
        )

        mInputPointers.reset()

        if (type != LastComposedWord.COMMIT_TYPE_DECIDED_WORD &&
            type != LastComposedWord.COMMIT_TYPE_MANUAL_PICK
        ) {
            lastComposedWord.deactivate()
        }

        mCapsCount = 0
        mDigitsCount = 0
        mIsBatchMode = false
        mCombinerChain.reset()
        events.clear()
        mCodePointSize = 0
        mIsOnlyFirstCharCapitalized = false
        mCapitalizedMode = CAPS_MODE_OFF
        refreshTypedWordCache()
        mAutoCorrection = null
        mCursorPositionWithinWord = 0
        mIsResumed = false
        mRejectedBatchModeSuggestion = null

        return lastComposedWord
    }

    fun resumeSuggestionOnLastComposedWord(lastComposedWord: LastComposedWord) {
        val events = mEvents
        events.clear()

        // NOTE:
        // The original Java used Collections.copy(mEvents, lastComposedWord.mEvents) after clear().
        // That requires the destination list to already be at least as large as the source and can
        // throw IndexOutOfBoundsException. addAll() preserves the intended behavior.
        events.addAll(lastComposedWord.mEvents)

        mInputPointers.set(lastComposedWord.mInputPointers)
        mCombinerChain.reset()
        refreshTypedWordCache()
        mCapitalizedMode = lastComposedWord.mCapitalizedMode
        mAutoCorrection = null // This will be filled by the next call to updateSuggestion.
        mCursorPositionWithinWord = mCodePointSize
        mRejectedBatchModeSuggestion = null
        mIsResumed = true
    }

    fun isBatchMode(): Boolean = mIsBatchMode

    fun unsetBatchMode() {
        mIsBatchMode = false
    }

    fun setRejectedBatchModeSuggestion(rejectedSuggestion: String?) {
        mRejectedBatchModeSuggestion = rejectedSuggestion
    }

    fun getRejectedBatchModeSuggestion(): String? = mRejectedBatchModeSuggestion

    /**
     * Get the current combining spec.
     *
     * @return the combining spec string, or null if none is set.
     */
    fun getCombiningSpec(): String? = mCombiningSpec

    fun addInputPointerForTest(index: Int, keyX: Int, keyY: Int) {
        mInputPointers.addPointerAt(index, keyX, keyY, 0, 0)
    }

    fun setTypedWordCacheForTests(typedWordCacheForTests: String) {
        mTypedWordCache = typedWordCacheForTests
    }

    companion object {
        private const val MAX_WORD_LENGTH = DecoderSpecificConstants.DICTIONARY_MAX_WORD_LENGTH

        const val CAPS_MODE_OFF = 0

        // 1 is shift bit, 2 is caps bit, 4 is auto bit but this is just a convention as these bits
        // aren't used anywhere in the code.
        const val CAPS_MODE_MANUAL_SHIFTED = 0x1
        const val CAPS_MODE_MANUAL_SHIFT_LOCKED = 0x3
        const val CAPS_MODE_AUTO_SHIFTED = 0x5
        const val CAPS_MODE_AUTO_SHIFT_LOCKED = 0x7

        fun getComposerForTest(isEmpty: Boolean): WordComposer {
            return WordComposer(isEmpty)
        }
    }
}

val WordComposer.typedWord: String
    get() = this.getTypedWord()

val WordComposer.combiningSpec: String?
    get() = this.getCombiningSpec()
