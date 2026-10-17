// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.heatmap

import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.WordComposer
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues

/**
 * Hooks for "Adapt to my typing", so the core classes only need single calls.
 *
 * [TypingRecorder] is used by InputLogic: it learns where the user touches each key, from the
 * words finally committed. [TouchAdaptation] is used by KeyDetector and PointerTracker: it picks
 * the key the user most likely meant near key borders. With the setting off, nothing is recorded
 * and key detection is unchanged. The data is in [TypingHeatmap].
 */
class TypingRecorder(private val latinIME: LatinIME, private val wordComposer: WordComposer) {
    // last letter of the composing word deleted with a single backspace press, for learning from self-corrections
    private var deletedLetterIndex = -1
    private var deletedLetter = 0
    private var deletedLetterX = 0
    private var deletedLetterY = 0

    fun onStartInput(settingsValues: SettingsValues) {
        deletedLetterIndex = -1
        // load the data in background, so it is ready for key detection; nothing at all when off
        if (settingsValues.mAdaptToTyping) TypingHeatmap.init(latinIME)
    }

    fun onFinishInput() = TypingHeatmap.onFinishInput()

    /** Called for every event before the word composer processes it */
    fun onEvent(event: Event, settingsValues: SettingsValues) {
        if (event.keyCode != KeyCode.DELETE) TypingHeatmap.finalizePending()
        trackLetterRetype(event, settingsValues)
    }

    /**
     * Unless this backspace reverts the autocorrection ([onCommitReverted]), the user edits the
     * committed word, so we don't know which word was finally used.
     */
    fun onBackspace() = TypingHeatmap.onBackspace()

    fun onCommitReverted() = TypingHeatmap.onCommitReverted()

    /** A word is committed: remember where its keys were touched. Call before the word composer is reset. */
    fun onWordCommitted(settingsValues: SettingsValues, finalWord: String) {
        if (!isRecording(settingsValues)) return
        val keyboard = alphabetKeyboard() ?: return
        val pointers = wordComposer.getInputPointers()
        val size = pointers.pointerSize
        val geometry = KeyboardGeometry(keyboard, KeyboardGeometry.isLandscape())
        TypingHeatmap.onWordCommitted(
            latinIME, geometry, wordComposer.getTypedWord(), finalWord,
            pointers.xCoordinates.copyOf(size), pointers.yCoordinates.copyOf(size), isAdapted(geometry)
        )
    }

    /**
     * If the user deletes the last letter and types a neighboring letter instead, the touch of the
     * deleted letter was meant for the new one. See [TypingHeatmap.onLetterRetyped].
     */
    private fun trackLetterRetype(event: Event, settingsValues: SettingsValues) {
        val deletedIndex = deletedLetterIndex
        deletedLetterIndex = -1
        if (!isRecording(settingsValues)) return
        if (event.keyCode == KeyCode.DELETE) {
            if (event.isKeyRepeat || !wordComposer.isComposingWord() || wordComposer.isCursorFrontOrMiddleOfComposingWord()) return
            val codePoints = StringUtils.toCodePointArray(wordComposer.getTypedWord())
            val index = codePoints.size - 1
            val pointers = wordComposer.getInputPointers()
            if (index < 0 || index >= pointers.pointerSize) return
            deletedLetterIndex = index
            deletedLetter = codePoints[index]
            deletedLetterX = pointers.xCoordinates[index]
            deletedLetterY = pointers.yCoordinates[index]
            return
        }
        if (deletedIndex < 0) return
        val codePoint = event.codePoint
        if (codePoint <= 0 || !Character.isLetter(codePoint) || event.x < 0 || event.y < 0) return
        if (wordComposer.size() != deletedIndex) return
        val keyboard = alphabetKeyboard() ?: return
        val geometry = KeyboardGeometry(keyboard)
        TypingHeatmap.onLetterRetyped(
            latinIME, geometry, deletedLetter, deletedLetterX, deletedLetterY, codePoint, isAdapted(geometry)
        )
    }

    // no real touch positions for gestures or words restarted from existing text
    private fun isRecording(settingsValues: SettingsValues) = settingsValues.mAdaptToTyping
            && !settingsValues.mIncognitoModeEnabled && !wordComposer.isBatchMode() && !wordComposer.isResumed()

    private fun alphabetKeyboard(): Keyboard? =
        latinIME.keyboardSwitcher.keyboard?.takeIf { it.mId.isAlphabetKeyboard }

    /** whether adaptation was already active for this layout, for comparing misses before and after */
    private fun isAdapted(geometry: KeyboardGeometry) =
        TypingHeatmap.getOffsets(null, geometry.layoutId, TypingHeatmap.DEFAULT_STRENGTH).isNotEmpty()
}

object TouchAdaptation {
    private fun isActive(keyboard: Keyboard, code: Int): Boolean {
        val settingsValues = Settings.getValues()
        return settingsValues.mAdaptToTyping && !settingsValues.mInputAttributes.mIsPasswordField
                && Character.isLetter(code) && keyboard.mId.isAlphabetKeyboard
    }

    private fun offsets(keyboard: Keyboard) =
        TypingHeatmap.getOffsets(null, KeyboardGeometry.layoutId(keyboard), TypingHeatmap.DEFAULT_STRENGTH)

    /**
     * Key chosen with the learned typing offsets of the user, if it differs from [primaryKey].
     * Only letter keys on alphabet keyboards are affected, never in password fields.
     * [touchX] and [touchY] are keyboard coordinates, like the learned data.
     */
    fun adaptedKey(keyboard: Keyboard, touchX: Int, touchY: Int, primaryKey: Key?): Key? {
        if (primaryKey == null || !isActive(keyboard, primaryKey.code)) return null
        val offsets = offsets(keyboard)
        if (offsets.isEmpty()) return null
        val keys = keyboard.getNearestKeys(touchX, touchY).filter { Character.isLetter(it.code) && it.width > 0 && it.height > 0 }
        val candidates = keys.map {
            TypingHeatmap.Candidate(Character.toLowerCase(it.code), it.x.toFloat(), it.y.toFloat(), it.width.toFloat(), it.height.toFloat())
        }
        val code = TypingHeatmap.adaptedKeyCode(touchX.toFloat(), touchY.toFloat(), candidates, offsets) ?: return null
        return keys.firstOrNull { Character.toLowerCase(it.code) == code }
    }

    /**
     * Touch moved by the learned offset of the key, so the dictionary sees a typical touch.
     * [x] and [y] are view coordinates, [correctionX] and [correctionY] convert them to keyboard coordinates.
     */
    fun adjustedTouch(keyboard: Keyboard, key: Key, code: Int, x: Int, y: Int, correctionX: Int, correctionY: Int): IntArray {
        if (!isActive(keyboard, code)) return intArrayOf(x, y)
        val offset = offsets(keyboard)[Character.toLowerCase(code)] ?: return intArrayOf(x, y)
        return TypingHeatmap.adjustForDictionary(x, y, correctionX, correctionY, key.width, key.height, offset)
    }
}
