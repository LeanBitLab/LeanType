package helium314.keyboard.latin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.common.endsWithWordCodepoint
import helium314.keyboard.latin.common.getFullEmojiAtEnd
import helium314.keyboard.latin.common.getTouchedWordRange
import helium314.keyboard.latin.common.hasLetterBeforeLastSpaceBeforeCursor
import helium314.keyboard.latin.common.nonWordCodePointAndNoSpaceBeforeCursor
import helium314.keyboard.latin.common.UnicodeSurrogate
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.inputlogic.PrivateCommandPerformer
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SpacingAndPunctuations
import helium314.keyboard.latin.utils.CapsModeUtils
import helium314.keyboard.latin.utils.DebugLogUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.NgramContextUtils
import helium314.keyboard.latin.utils.StatsUtils
import helium314.keyboard.latin.utils.TextRange
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Enrichment class for InputConnection to simplify interaction and add functionality.
 *
 * This class serves as a wrapper to be able to simply add hooks to any calls to the underlying
 * InputConnection. It also keeps track of a number of things to avoid having to call upon IPC
 * all the time to find out what text is in the buffer, when we need it to determine caps mode
 * for example.
 */
class RichInputConnection(private val mParent: InputMethodService) : PrivateCommandPerformer {

    private var mIC: InputConnection? = null
    private var mNestLevel = 0

    private var mExpectedSelStart = INVALID_CURSOR_POSITION // in chars, not code points
    private var mExpectedSelEnd = INVALID_CURSOR_POSITION   // in chars, not code points

    private val mCommittedTextBeforeComposingText = StringBuilder()
    private val mComposingText = StringBuilder()

    private val mTempObjectForCommitText = SpannableStringBuilder()

    private var mLastSlowInputConnectionTime = -SLOW_INPUTCONNECTION_PERSIST_MS

    fun isConnected(): Boolean = mIC != null

    fun hasSlowInputConnection(): Boolean {
        return (SystemClock.uptimeMillis() - mLastSlowInputConnectionTime) <= SLOW_INPUTCONNECTION_PERSIST_MS
    }

    fun onStartInput() {
        mLastSlowInputConnectionTime = -SLOW_INPUTCONNECTION_PERSIST_MS
        mNestLevel = 0
    }

    private fun checkConsistencyForDebug() {
        val r = ExtractedTextRequest()
        r.hintMaxChars = 0
        r.hintMaxLines = 0
        r.token = 1
        r.flags = 0

        val et: ExtractedText? = mIC?.getExtractedText(r, 0)
        val beforeCursor = getTextBeforeCursor(Constants.EDITOR_CONTENTS_CACHE_SIZE, 0)
        val internal = StringBuilder(mCommittedTextBeforeComposingText).append(mComposingText)

        if (et == null || beforeCursor == null) return

        val actualLength = minOf(beforeCursor.length, internal.length)
        if (internal.length > actualLength) {
            internal.delete(0, internal.length - actualLength)
        }

        val reference = if (beforeCursor.length <= actualLength) {
            beforeCursor.toString()
        } else {
            beforeCursor.subSequence(beforeCursor.length - actualLength, beforeCursor.length).toString()
        }

        if (et.selectionStart != mExpectedSelStart || reference != internal.toString()) {
            val context = "Expected selection start = $mExpectedSelStart\n" +
                    "Actual selection start = ${et.selectionStart}\n" +
                    "Expected text = ${internal.length} $internal\n" +
                    "Actual text = ${reference.length} $reference"
            (mParent as LatinIME).debugDumpStateAndCrashWithException(context)
        } else {
            Log.e(TAG, DebugLogUtils.getStackTrace(2))
            Log.e(TAG, "Exp <> Actual : $mExpectedSelStart <> ${et.selectionStart}")
        }
    }

    fun beginBatchEdit() {
        if (++mNestLevel == 1) {
            mIC = mParent.currentInputConnection
            if (isConnected()) {
                mIC?.beginBatchEdit()
            }
        } else if (mNestLevel > 10) {
            Log.w(TAG, "Nest level unusually high : $mNestLevel")
        }

        if (DEBUG_BATCH_NESTING) checkBatchEdit()
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()
    }

    fun endBatchEdit() {
        if (mNestLevel <= 0) {
            Log.e(TAG, "Batch edit not in progress!")
        }

        if (--mNestLevel == 0 && isConnected()) {
            mIC?.endBatchEdit()
        }

        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()
    }

    fun ensureBatchEditClosed() {
        if (mNestLevel > 0 && isConnected()) {
            mIC?.endBatchEdit()
        }
        mNestLevel = 0
    }

    /**
     * Reset the cached text and retrieve it again from the editor.
     *
     * This should be called when the cursor moved. It's possible that we can't connect to
     * the application when doing this; notably, this happens sometimes during rotation, probably
     * because of a race condition in the framework. In this case, we just can't retrieve the
     * data, so we empty the cache and note that we don't know the new cursor position, and we
     * return false so that the caller knows about this and can retry later.
     */
    fun resetCachesUponCursorMoveAndReturnSuccess(
        newSelStart: Int,
        newSelEnd: Int,
        shouldFinishComposition: Boolean
    ): Boolean {
        mComposingText.setLength(0)

        mExpectedSelStart = newSelStart
        mExpectedSelEnd = newSelEnd

        val didReloadTextSuccessfully = reloadTextCache()

        if (!didReloadTextSuccessfully) {
            Log.d(TAG, "Will try to retrieve text later.")
            return false
        }

        if (mExpectedSelStart != newSelStart || mExpectedSelEnd != newSelEnd) {
            Log.i(
                TAG,
                "resetCachesUponCursorMove: tried to set $newSelStart/$newSelEnd, " +
                        "but input field has $mExpectedSelStart/$mExpectedSelEnd"
            )
        }

        if (isConnected() && shouldFinishComposition) {
            mIC?.finishComposingText()
        }

        return true
    }

    private fun reloadTextCache(): Boolean {
        mCommittedTextBeforeComposingText.setLength(0)
        mComposingText.setLength(0)

        mIC = mParent.currentInputConnection

        if (!isConnected()) {
            return false
        }

        val textBeforeCursor = getTextBeforeCursorAndDetectLaggyConnection(
            OPERATION_RELOAD_TEXT_CACHE,
            SLOW_INPUT_CONNECTION_ON_FULL_RELOAD_MS,
            Constants.EDITOR_CONTENTS_CACHE_SIZE,
            0
        )

        if (textBeforeCursor == null) {
            mExpectedSelStart = INVALID_CURSOR_POSITION
            mExpectedSelEnd = INVALID_CURSOR_POSITION
            Log.w(TAG, "Unable to connect to the editor to retrieve text.")
            return false
        }

        mCommittedTextBeforeComposingText.append(textBeforeCursor)
        return true
    }

    private fun reloadCursorPosition() {
        if (!isConnected()) return

        val et = mIC?.getExtractedText(ExtractedTextRequest(), 0) ?: return

        mExpectedSelStart = et.selectionStart + et.startOffset
        mExpectedSelEnd = et.selectionEnd + et.startOffset
    }

    private fun checkBatchEdit() {
        if (mNestLevel != 1) {
            Log.e(TAG, "Batch edit level incorrect : $mNestLevel")
            Log.e(TAG, DebugLogUtils.getStackTrace(4))
        }
    }

    fun finishComposingText() {
        if (DEBUG_BATCH_NESTING) checkBatchEdit()
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()

        mCommittedTextBeforeComposingText.append(mComposingText)
        mComposingText.setLength(0)

        if (isConnected()) {
            mIC?.finishComposingText()
        }
    }

    fun commitCodePoint(codePoint: Int) {
        commitText(StringUtils.newSingleCodePointString(codePoint), 1)
    }

    fun commitText(text: CharSequence?, newCursorPosition: Int) {
        if (text == null) return
        if (DEBUG_BATCH_NESTING) checkBatchEdit()
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()

        if (DebugFlags.DEBUG_ENABLED) {
            Log.d(TAG, "committing ${text.length} characters")
        }

        mCommittedTextBeforeComposingText.append(text)

        mExpectedSelStart += text.length - mComposingText.length
        mExpectedSelEnd = mExpectedSelStart
        mComposingText.setLength(0)

        if (isConnected()) {
            mTempObjectForCommitText.clear()
            mTempObjectForCommitText.append(text)

            val spans: Array<CharacterStyle> = mTempObjectForCommitText.getSpans(
                0,
                text.length,
                CharacterStyle::class.java
            )

            for (span in spans) {
                val spanStart = mTempObjectForCommitText.getSpanStart(span)
                val spanEnd = mTempObjectForCommitText.getSpanEnd(span)
                val spanFlags = mTempObjectForCommitText.getSpanFlags(span)

                if (0 < spanEnd && spanEnd < mTempObjectForCommitText.length) {
                    val spanEndChar = mTempObjectForCommitText[spanEnd - 1]
                    val nextChar = mTempObjectForCommitText[spanEnd]

                    if (UnicodeSurrogate.isLowSurrogate(spanEndChar) &&
                        UnicodeSurrogate.isHighSurrogate(nextChar)
                    ) {
                        mTempObjectForCommitText.setSpan(span, spanStart, spanEnd + 1, spanFlags)
                    }
                }
            }

            mIC?.commitText(mTempObjectForCommitText, newCursorPosition)
        }
    }

    fun getSelectedText(flags: Int): CharSequence? {
        return if (isConnected()) mIC?.getSelectedText(flags) else null
    }

    fun canDeleteCharacters(): Boolean = mExpectedSelStart > 0

    fun hasTextAfterCursor(): Boolean {
        val after = getTextAfterCursor(1, 0)
        return !TextUtils.isEmpty(after)
    }

    fun getCursorCapsMode(
        inputType: Int,
        spacingAndPunctuations: SpacingAndPunctuations,
        hasSpaceBefore: Boolean
    ): Int {
        mIC = mParent.currentInputConnection

        if (!isConnected()) {
            return Constants.TextUtils.CAP_MODE_OFF
        }

        if (mComposingText.isNotEmpty()) {
            if (hasSpaceBefore) {
                return (TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_WORDS) and inputType
            }
            return TextUtils.CAP_MODE_CHARACTERS and inputType
        }

        if (mCommittedTextBeforeComposingText.isEmpty() && mExpectedSelStart != 0) {
            if (!reloadTextCache()) {
                Log.w(TAG, "Unable to connect to the editor. Setting caps mode without knowing text.")
            }
        }

        return CapsModeUtils.getCapsMode(
            mCommittedTextBeforeComposingText.toString(),
            inputType,
            spacingAndPunctuations,
            hasSpaceBefore
        )
    }

    val codePointBeforeCursor: Int
        get() {
            val text: CharSequence = if (mComposingText.isEmpty()) {
                mCommittedTextBeforeComposingText
            } else {
                mComposingText
            }

            val length = text.length
            if (length < 1) return Constants.NOT_A_CODE

            return Character.codePointBefore(text, length)
        }

    val charBeforeBeforeCursor: Int
        get() {
            if (mComposingText.length >= 2) {
                return mComposingText[mComposingText.length - 2].code
            }

            val length = mCommittedTextBeforeComposingText.length

            if (mComposingText.length == 1) {
                if (length < 1) return Constants.NOT_A_CODE
                return mCommittedTextBeforeComposingText[length - 1].code
            }

            if (length < 2) return Constants.NOT_A_CODE

            return mCommittedTextBeforeComposingText[length - 2].code
        }

    fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
        val cachedLength = mCommittedTextBeforeComposingText.length + mComposingText.length

        if (INVALID_CURSOR_POSITION != mExpectedSelStart &&
            (cachedLength >= n || cachedLength >= mExpectedSelStart)
        ) {
            val composingSnapshot = mComposingText.toString()
            val committedSnapshot = mCommittedTextBeforeComposingText.toString()
            val totalLen = committedSnapshot.length + composingSnapshot.length

            if (totalLen <= n) {
                if (composingSnapshot.isEmpty()) return committedSnapshot
                if (committedSnapshot.isEmpty()) return composingSnapshot
                return committedSnapshot + composingSnapshot
            }

            val combined = committedSnapshot + composingSnapshot
            return combined.substring(combined.length - n)
        }

        return getTextBeforeCursorAndDetectLaggyConnection(
            OPERATION_GET_TEXT_BEFORE_CURSOR,
            SLOW_INPUT_CONNECTION_ON_PARTIAL_RELOAD_MS,
            n,
            flags
        )
    }

    private fun getTextBeforeCursorAndDetectLaggyConnection(
        operation: Int,
        timeout: Long,
        n: Int,
        flags: Int
    ): CharSequence? {
        mIC = mParent.currentInputConnection

        if (!isConnected()) {
            return null
        }

        val startTime = SystemClock.uptimeMillis()
        val result = mIC?.getTextBeforeCursor(n, flags)

        detectLaggyConnection(operation, timeout, startTime)

        if ((mCommittedTextBeforeComposingText.isNotEmpty() || mComposingText.isNotEmpty()) &&
            result != null &&
            !checkTextBeforeCursorConsistency(result)
        ) {
            Log.w(TAG, "cached text out of sync, reloading")
            reloadCursorPosition()
            reloadTextCache()
        }

        return result
    }

    private fun checkTextBeforeCursorConsistency(textField: CharSequence): Boolean {
        val lastIndex = textField.length - 1
        if (lastIndex == -1) return true

        val lastChar = textField[lastIndex]
        val composingLength = mComposingText.length

        val lastCachedChar = when {
            composingLength > 0 -> mComposingText[composingLength - 1]
            mCommittedTextBeforeComposingText.isNotEmpty() ->
                mCommittedTextBeforeComposingText[mCommittedTextBeforeComposingText.length - 1]
            else -> return true
        }

        if (lastCachedChar != lastChar) return false

        if (lastIndex > 0 && textField[lastIndex - 1] != lastChar) return true

        for (i in 0..lastIndex) {
            val currentTextFieldChar = textField[lastIndex - i]

            val currentCachedChar = if (i < composingLength) {
                mComposingText[composingLength - 1 - i]
            } else {
                val index = mCommittedTextBeforeComposingText.length - 1 - (i - composingLength)
                if (index < mCommittedTextBeforeComposingText.length && index >= 0) {
                    mCommittedTextBeforeComposingText[index]
                } else {
                    return lastIndex > 100
                }
            }

            if (currentTextFieldChar != currentCachedChar) return false
            if (lastChar != currentTextFieldChar) return true
        }

        return true
    }

    fun getTextAfterCursor(n: Int, flags: Int): CharSequence? {
        return getTextAfterCursorAndDetectLaggyConnection(
            OPERATION_GET_TEXT_AFTER_CURSOR,
            SLOW_INPUT_CONNECTION_ON_PARTIAL_RELOAD_MS,
            n,
            flags
        )
    }

    private fun getTextAfterCursorAndDetectLaggyConnection(
        operation: Int,
        timeout: Long,
        n: Int,
        flags: Int
    ): CharSequence? {
        mIC = mParent.currentInputConnection

        if (!isConnected()) {
            return null
        }

        val startTime = SystemClock.uptimeMillis()
        val result = mIC?.getTextAfterCursor(n, flags)

        detectLaggyConnection(operation, timeout, startTime)

        return result
    }

    private fun detectLaggyConnection(operation: Int, timeout: Long, startTime: Long) {
        val duration = SystemClock.uptimeMillis() - startTime

        if (duration >= timeout) {
            val operationName = OPERATION_NAMES[operation]
            Log.w(TAG, "Slow InputConnection: $operationName took $duration ms.")
            StatsUtils.onInputConnectionLaggy(operation, duration)
            mLastSlowInputConnectionTime = SystemClock.uptimeMillis()
        } else if (duration < timeout / 5 && hasSlowInputConnection()) {
            mLastSlowInputConnectionTime -= SLOW_INPUTCONNECTION_PERSIST_MS / 2
            Log.d(TAG, "InputConnection: much faster now, reducing persist time")
        }
    }

    fun deleteTextBeforeCursor(beforeLength: Int) {
        if (DEBUG_BATCH_NESTING) checkBatchEdit()

        if (DebugFlags.DEBUG_ENABLED) {
            Log.d(TAG, "deleting $beforeLength characters before cursor")
        }

        val remainingChars = mComposingText.length - beforeLength

        if (remainingChars >= 0) {
            mComposingText.setLength(remainingChars)
        } else {
            mComposingText.setLength(0)
            val len = max(mCommittedTextBeforeComposingText.length + remainingChars, 0)
            mCommittedTextBeforeComposingText.setLength(len)
        }

        if (mExpectedSelStart > beforeLength) {
            mExpectedSelStart -= beforeLength
            mExpectedSelEnd -= beforeLength
        } else {
            mExpectedSelEnd -= mExpectedSelStart
            mExpectedSelStart = 0
        }

        if (isConnected()) {
            mIC?.deleteSurroundingText(beforeLength, 0)
        }

        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()
    }

    fun deleteSurroundingText(beforeLength: Int, afterLength: Int) {
        if (DEBUG_BATCH_NESTING) checkBatchEdit()

        if (DebugFlags.DEBUG_ENABLED) {
            Log.d(TAG, "deleting $beforeLength before and $afterLength after cursor")
        }

        val remainingChars = mComposingText.length - beforeLength

        if (remainingChars >= 0) {
            mComposingText.setLength(remainingChars)
        } else {
            mComposingText.setLength(0)
            val len = max(mCommittedTextBeforeComposingText.length + remainingChars, 0)
            mCommittedTextBeforeComposingText.setLength(len)
        }

        if (mExpectedSelStart > beforeLength) {
            mExpectedSelStart -= beforeLength
            mExpectedSelEnd -= beforeLength
        } else {
            mExpectedSelEnd -= mExpectedSelStart
            mExpectedSelStart = 0
        }

        if (isConnected()) {
            mIC?.deleteSurroundingText(beforeLength, afterLength)
        }

        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()
    }

    fun performEditorAction(actionId: Int) {
        mIC = mParent.currentInputConnection
        val ic = mIC ?: return

        if (mComposingText.isNotEmpty()) {
            finishComposingText()
        }

        ic.performEditorAction(actionId)
    }

    fun sendKeyEvent(keyEvent: KeyEvent) {
        if (DEBUG_BATCH_NESTING) checkBatchEdit()

        if (DebugFlags.DEBUG_ENABLED) {
            Log.d(
                TAG,
                "key event with action ${keyEvent.action}, is control: " +
                        Character.isISOControl(keyEvent.unicodeChar)
            )
        }

        if (keyEvent.action == KeyEvent.ACTION_DOWN) {
            if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()

            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    mCommittedTextBeforeComposingText.append("\n")
                    mExpectedSelStart += 1
                    mExpectedSelEnd = mExpectedSelStart
                }

                KeyEvent.KEYCODE_DEL -> {
                    if (mComposingText.isEmpty()) {
                        if (mCommittedTextBeforeComposingText.isNotEmpty()) {
                            mCommittedTextBeforeComposingText.delete(
                                mCommittedTextBeforeComposingText.length - 1,
                                mCommittedTextBeforeComposingText.length
                            )
                        }
                    } else {
                        mComposingText.delete(mComposingText.length - 1, mComposingText.length)
                    }

                    if (mExpectedSelStart > 0 && mExpectedSelStart == mExpectedSelEnd) {
                        mExpectedSelStart -= 1
                    }

                    mExpectedSelEnd = mExpectedSelStart
                }

                KeyEvent.KEYCODE_UNKNOWN -> {
                    val characters = keyEvent.characters
                    if (characters != null) {
                        mCommittedTextBeforeComposingText.append(characters)
                        mExpectedSelStart += characters.length
                        mExpectedSelEnd = mExpectedSelStart
                    }
                }

                else -> {
                    val codePoint = keyEvent.unicodeChar
                    if (!Character.isISOControl(codePoint)) {
                        val text = StringUtils.newSingleCodePointString(codePoint)
                        mCommittedTextBeforeComposingText.append(text)
                        mExpectedSelStart += text.length
                        mExpectedSelEnd = mExpectedSelStart
                    }
                }
            }
        }

        if (isConnected()) {
            mIC?.sendKeyEvent(keyEvent)
        }
    }

    fun setComposingRegion(start: Int, end: Int) {
        if (DEBUG_BATCH_NESTING) checkBatchEdit()
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()

        val moveBy = mExpectedSelStart - start

        val textBeforeCursor = getTextBeforeCursor(
            Constants.EDITOR_CONTENTS_CACHE_SIZE + (end - start),
            0
        )

        mCommittedTextBeforeComposingText.setLength(0)
        mComposingText.setLength(0)

        textBeforeCursor?.let { text ->
            val indexOfStartOfComposingText = max(text.length - moveBy, 0)

            mComposingText.append(
                text.subSequence(indexOfStartOfComposingText, text.length)
            )

            mCommittedTextBeforeComposingText.append(
                text.subSequence(0, indexOfStartOfComposingText)
            )
        }

        if (isConnected()) {
            mIC?.setComposingRegion(start, end)
        }
    }

    fun setComposingTextWithBackgroundColor(
        newComposingText: CharSequence,
        newCursorPosition: Int,
        backgroundColor: Int,
        coloredTextLength: Int
    ): Boolean {
        val composingTextToBeSet = if (backgroundColor == Color.TRANSPARENT) {
            newComposingText
        } else {
            val spannable = SpannableString(newComposingText)
            val backgroundColorSpan = BackgroundColorSpan(backgroundColor)
            val spanLength = minOf(coloredTextLength, spannable.length)

            spannable.setSpan(
                backgroundColorSpan,
                0,
                spanLength,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING
            )

            spannable
        }

        return setComposingText(composingTextToBeSet, newCursorPosition)
    }

    fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
        if (DEBUG_BATCH_NESTING) checkBatchEdit()
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()

        mExpectedSelStart += text.length - mComposingText.length
        mExpectedSelEnd = mExpectedSelStart

        mComposingText.setLength(0)
        mComposingText.append(text)

        if (isConnected()) {
            if (DebugFlags.DEBUG_ENABLED) {
                Log.d(TAG, "setting composing text of length ${text.length}")
            }
            mIC?.setComposingText(text, newCursorPosition)
        }

        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()

        return true
    }

    fun setSelection(start: Int, end: Int): Boolean {
        if (DEBUG_BATCH_NESTING) checkBatchEdit()
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()

        if (DebugFlags.DEBUG_ENABLED) {
            Log.d(TAG, "setting selection from $start to $end")
        }

        if (start < 0 || end < 0) {
            return false
        }

        if (start > end) {
            mExpectedSelStart = end
            mExpectedSelEnd = start
        } else {
            mExpectedSelStart = start
            mExpectedSelEnd = end
        }

        if (isConnected()) {
            val isIcValid = mIC?.setSelection(start, end) ?: false
            if (!isIcValid) {
                return false
            }
        }

        return reloadTextCache()
    }

    fun performContextMenuAction(actionId: Int): Boolean {
        mIC = mParent.currentInputConnection

        return if (isConnected()) {
            mIC?.performContextMenuAction(actionId) ?: false
        } else {
            false
        }
    }

    fun selectAll() {
        if (!isConnected()) return

        if (mExpectedSelStart != mExpectedSelEnd &&
            mExpectedSelStart == 0 &&
            !hasTextAfterCursor()
        ) {
            mIC?.setSelection(mExpectedSelEnd, mExpectedSelEnd)
        } else {
            mIC?.performContextMenuAction(android.R.id.selectAll)
        }
    }

    fun selectWord(spacingAndPunctuations: SpacingAndPunctuations, script: String) {
        if (!isConnected()) return

        if (mExpectedSelStart != mExpectedSelEnd) {
            mIC?.setSelection(mExpectedSelEnd, mExpectedSelEnd)
            return
        }

        val range = getWordRangeAtCursor(spacingAndPunctuations, script) ?: return

        mIC?.setSelection(
            mExpectedSelStart - range.getNumberOfCharsInWordBeforeCursor(),
            mExpectedSelStart + range.getNumberOfCharsInWordAfterCursor()
        )
    }

    fun copyText(getSelection: Boolean) {
        if (getSelection && hasSelection()) {
            if (performContextMenuAction(android.R.id.copy)) {
                return
            }
        }

        var text: CharSequence? = if (getSelection) {
            getSelectedText(InputConnection.GET_TEXT_WITH_STYLES)
        } else {
            null
        }

        if (text == null || text.isEmpty()) {
            val etr = ExtractedTextRequest()
            etr.flags = InputConnection.GET_TEXT_WITH_STYLES
            etr.hintMaxChars = Int.MAX_VALUE

            val et = mIC?.getExtractedText(etr, 0) ?: return
            text = et.text
        }

        if (text == null || text.isEmpty()) return

        val cm = mParent.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("copied text", text))

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            KeyboardSwitcher.getInstance()
                .showToast(mParent.getString(R.string.toast_msg_clipboard_copy), true)
        }
    }

    fun commitCorrection(correctionInfo: CorrectionInfo) {
        if (DEBUG_BATCH_NESTING) checkBatchEdit()
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()

        if (isConnected()) {
            mIC?.commitCorrection(correctionInfo)
        }

        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()
    }

    fun commitCompletion(completionInfo: CompletionInfo?) {
        if (completionInfo == null) return
        if (DEBUG_BATCH_NESTING) checkBatchEdit()
        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()

        var text: CharSequence? = completionInfo.text

        if (DebugFlags.DEBUG_ENABLED) {
            Log.d(TAG, "committing completion of length ${text?.length ?: 0}")
        }

        if (text == null) {
            text = ""
        }

        mCommittedTextBeforeComposingText.append(text)

        mExpectedSelStart += text.length - mComposingText.length
        mExpectedSelEnd = mExpectedSelStart
        mComposingText.setLength(0)

        if (isConnected()) {
            mIC?.commitCompletion(completionInfo)
        }

        if (DEBUG_PREVIOUS_TEXT) checkConsistencyForDebug()
    }

    fun getNgramContextFromNthPreviousWord(
        spacingAndPunctuations: SpacingAndPunctuations,
        n: Int
    ): NgramContext {
        mIC = mParent.currentInputConnection

        if (!isConnected()) {
            return NgramContext.EMPTY_PREV_WORDS_INFO
        }

        val prev = getTextBeforeCursor(NUM_CHARS_TO_GET_BEFORE_CURSOR, 0)

        if (DEBUG_PREVIOUS_TEXT && prev != null) {
            val checkLength = NUM_CHARS_TO_GET_BEFORE_CURSOR - 1

            val reference = if (prev.length <= checkLength) {
                prev.toString()
            } else {
                prev.subSequence(prev.length - checkLength, prev.length).toString()
            }

            val internal = StringBuilder()
                .append(mCommittedTextBeforeComposingText)
                .append(mComposingText)

            if (internal.length > checkLength) {
                internal.delete(0, internal.length - checkLength)

                if (reference != internal.toString()) {
                    val context = "Expected text = $internal\nActual text = $reference"
                    (mParent as LatinIME).debugDumpStateAndCrashWithException(context)
                }
            }
        }

        return NgramContextUtils.getNgramContextFromNthPreviousWord(prev, spacingAndPunctuations, n)
    }

    fun getWordRangeAtCursor(
        spacingAndPunctuations: SpacingAndPunctuations,
        script: String
    ): TextRange? {
        mIC = mParent.currentInputConnection

        if (!isConnected()) {
            return null
        }

        val before = getTextBeforeCursorAndDetectLaggyConnection(
            OPERATION_GET_WORD_RANGE_AT_CURSOR,
            SLOW_INPUT_CONNECTION_ON_PARTIAL_RELOAD_MS,
            NUM_CHARS_TO_GET_BEFORE_CURSOR,
            InputConnection.GET_TEXT_WITH_STYLES
        )

        val after = getTextAfterCursorAndDetectLaggyConnection(
            OPERATION_GET_WORD_RANGE_AT_CURSOR,
            SLOW_INPUT_CONNECTION_ON_PARTIAL_RELOAD_MS,
            NUM_CHARS_TO_GET_AFTER_CURSOR,
            InputConnection.GET_TEXT_WITH_STYLES
        )

        if (before == null || after == null) {
            return null
        }

        return getTouchedWordRange(before, after, script, spacingAndPunctuations)
    }

    fun isCursorTouchingWord(
        spacingAndPunctuations: SpacingAndPunctuations,
        checkTextAfter: Boolean
    ): Boolean {
        if (checkTextAfter && isCursorFollowedByWordCharacter(spacingAndPunctuations)) {
            return true
        }

        if (mComposingText.isNotEmpty()) {
            return true
        }

        return endsWithWordCodepoint(
            mCommittedTextBeforeComposingText.toString(),
            spacingAndPunctuations
        )
    }

    fun isCursorFollowedByWordCharacter(spacingAndPunctuations: SpacingAndPunctuations): Boolean {
        val after = getTextAfterCursor(1, 0)

        if (TextUtils.isEmpty(after)) {
            return false
        }

        val codePointAfterCursor = Character.codePointAt(after!!, 0)

        return !spacingAndPunctuations.isWordSeparator(codePointAfterCursor) &&
                !spacingAndPunctuations.isWordConnector(codePointAfterCursor)
    }

    fun removeTrailingSpace() {
        if (DEBUG_BATCH_NESTING) checkBatchEdit()

        val codePointBeforeCursor = codePointBeforeCursor

        if (Constants.CODE_SPACE == codePointBeforeCursor) {
            deleteTextBeforeCursor(1)
        }
    }

    fun sameAsTextBeforeCursor(text: CharSequence): Boolean {
        val beforeText = getTextBeforeCursor(text.length, 0)
        return TextUtils.equals(text, beforeText)
    }

    fun revertDoubleSpacePeriod(spacingAndPunctuations: SpacingAndPunctuations): Boolean {
        if (DEBUG_BATCH_NESTING) checkBatchEdit()

        val textBeforeCursor = getTextBeforeCursor(2, 0)

        if (!TextUtils.equals(spacingAndPunctuations.mSentenceSeparatorAndSpace, textBeforeCursor)) {
            Log.d(
                TAG,
                "Tried to revert double-space combo but we didn't find \"" +
                        spacingAndPunctuations.mSentenceSeparatorAndSpace +
                        "\" just before the cursor."
            )
            return false
        }

        deleteTextBeforeCursor(2)
        commitText(" ", 1)

        return true
    }

    fun revertSwapPunctuation(): Boolean {
        if (DEBUG_BATCH_NESTING) checkBatchEdit()

        val textBeforeCursor = getTextBeforeCursor(2, 0)

        if (TextUtils.isEmpty(textBeforeCursor) ||
            Constants.CODE_SPACE != textBeforeCursor!![1].code
        ) {
            Log.d(TAG, "Tried to revert a swap of punctuation but we didn't find a space just before the cursor.")
            return false
        }

        deleteTextBeforeCursor(2)

        val text = " " + textBeforeCursor.subSequence(0, 1)
        commitText(text, 1)

        return true
    }

    fun isBelatedExpectedUpdate(
        oldSelStart: Int,
        newSelStart: Int,
        oldSelEnd: Int,
        newSelEnd: Int,
        composingSpanStart: Int,
        composingSpanEnd: Int
    ): Boolean {
        if (mExpectedSelStart == newSelStart && mExpectedSelEnd == newSelEnd) {
            if (composingSpanEnd - composingSpanStart < mComposingText.length) {
                return false
            }
            return true
        }

        if (mExpectedSelStart == oldSelStart &&
            mExpectedSelEnd == oldSelEnd &&
            (oldSelStart != newSelStart || oldSelEnd != newSelEnd)
        ) {
            return false
        }

        return (newSelStart == newSelEnd) &&
                (newSelStart - oldSelStart) * (mExpectedSelStart - newSelStart) >= 0 &&
                (newSelEnd - oldSelEnd) * (mExpectedSelEnd - newSelEnd) >= 0
    }

    fun textBeforeCursorLooksLikeURL(): Boolean {
        return StringUtils.lastPartLooksLikeURL(mCommittedTextBeforeComposingText)
    }

    fun nonWordCodePointAndNoSpaceBeforeCursor(spacingAndPunctuations: SpacingAndPunctuations): Boolean {
        return nonWordCodePointAndNoSpaceBeforeCursor(
            mCommittedTextBeforeComposingText,
            spacingAndPunctuations
        )
    }

    fun spaceBeforeCursor(): Boolean {
        return mCommittedTextBeforeComposingText.indexOf(" ") != -1
    }

    val charCountToDeleteBeforeCursor: Int
        get() {
            val lastCodePoint = codePointBeforeCursor

            if (StringUtils.mightBeEmoji(lastCodePoint)) {
                val text = mCommittedTextBeforeComposingText.toString() + mComposingText.toString()
                val emojiLength = getFullEmojiAtEnd(text).length

                if (emojiLength > 0) {
                    return emojiLength
                }
            }

            return if (Character.isSupplementaryCodePoint(lastCodePoint)) 2 else 1
        }

    fun hasLetterBeforeLastSpaceBeforeCursor(): Boolean {
        return hasLetterBeforeLastSpaceBeforeCursor(mCommittedTextBeforeComposingText)
    }

    fun wordBeforeCursorMayBeEmail(): Boolean {
        return mCommittedTextBeforeComposingText.lastIndexOf(" ") <
                mCommittedTextBeforeComposingText.lastIndexOf("@")
    }

    fun textBeforeCursorUntilLastWhitespaceOrDoubleSlash(): CharSequence {
        var startIndex = 0
        var previousWasSlash = false

        for (i in mCommittedTextBeforeComposingText.length - 1 downTo 0) {
            val c = mCommittedTextBeforeComposingText[i]

            if (Character.isWhitespace(c)) {
                startIndex = i + 1
                break
            }

            if (c == '/') {
                if (previousWasSlash) {
                    startIndex = i + 2
                    break
                }
                previousWasSlash = true
            } else {
                previousWasSlash = false
            }
        }

        return mCommittedTextBeforeComposingText.subSequence(
            startIndex,
            mCommittedTextBeforeComposingText.length
        )
    }

    fun isInsideDoubleQuoteOrAfterDigit(): Boolean {
        return StringUtils.isInsideDoubleQuoteOrAfterDigit(mCommittedTextBeforeComposingText)
    }

    fun tryFixIncorrectCursorPosition() {
        mIC = mParent.currentInputConnection

        val textBeforeCursor = getTextBeforeCursor(Constants.EDITOR_CONTENTS_CACHE_SIZE, 0)
        val selectedText = if (isConnected()) mIC?.getSelectedText(0) else null

        if (textBeforeCursor == null ||
            (!TextUtils.isEmpty(selectedText) && mExpectedSelEnd == mExpectedSelStart)
        ) {
            mExpectedSelStart = Constants.NOT_A_CURSOR_POSITION
            mExpectedSelEnd = Constants.NOT_A_CURSOR_POSITION
        } else {
            val textLength = textBeforeCursor.length

            if (textLength < Constants.EDITOR_CONTENTS_CACHE_SIZE &&
                (textLength > mExpectedSelStart || mExpectedSelStart < Constants.EDITOR_CONTENTS_CACHE_SIZE)
            ) {
                val wasEqual = mExpectedSelStart == mExpectedSelEnd

                mExpectedSelStart = textLength

                if (wasEqual || mExpectedSelStart > mExpectedSelEnd) {
                    mExpectedSelEnd = mExpectedSelStart
                }
            } else {
                reloadCursorPosition()
            }
        }
    }

    override fun performPrivateCommand(action: String?, data: Bundle?): Boolean {
        mIC = mParent.currentInputConnection

        if (!isConnected()) {
            return false
        }

        return mIC?.performPrivateCommand(action, data) ?: false
    }

    val expectedSelectionStart: Int
        get() = mExpectedSelStart

    val expectedSelectionEnd: Int
        get() = mExpectedSelEnd

    fun hasSelection(): Boolean = mExpectedSelEnd != mExpectedSelStart

    fun isCursorPositionKnown(): Boolean = INVALID_CURSOR_POSITION != mExpectedSelStart

    fun requestCursorUpdates(enableMonitor: Boolean, requestImmediateCallback: Boolean): Boolean {
        mIC = mParent.currentInputConnection

        if (!isConnected()) {
            return false
        }

        val cursorUpdateMode =
            (if (enableMonitor) InputConnection.CURSOR_UPDATE_MONITOR else 0) or
                    (if (requestImmediateCallback) InputConnection.CURSOR_UPDATE_IMMEDIATE else 0)

        return mIC?.requestCursorUpdates(cursorUpdateMode) ?: false
    }

    companion object {
        private const val TAG = "RichInputConnection"
        private const val DBG = false
        private const val DEBUG_PREVIOUS_TEXT = false
        private const val DEBUG_BATCH_NESTING = false

        private const val NUM_CHARS_TO_GET_BEFORE_CURSOR = 40
        private const val NUM_CHARS_TO_GET_AFTER_CURSOR = 40
        private const val INVALID_CURSOR_POSITION = -1

        private const val SLOW_INPUT_CONNECTION_ON_FULL_RELOAD_MS = 300L
        private const val SLOW_INPUT_CONNECTION_ON_PARTIAL_RELOAD_MS = 200L

        private const val OPERATION_GET_TEXT_BEFORE_CURSOR = 0
        private const val OPERATION_GET_TEXT_AFTER_CURSOR = 1
        private const val OPERATION_GET_WORD_RANGE_AT_CURSOR = 2
        private const val OPERATION_RELOAD_TEXT_CACHE = 3

        val OPERATION_NAMES = arrayOf(
            "GET_TEXT_BEFORE_CURSOR",
            "GET_TEXT_AFTER_CURSOR",
            "GET_WORD_RANGE_AT_CURSOR",
            "RELOAD_TEXT_CACHE"
        )

        private val SLOW_INPUTCONNECTION_PERSIST_MS = TimeUnit.MINUTES.toMillis(2)
    }
}
