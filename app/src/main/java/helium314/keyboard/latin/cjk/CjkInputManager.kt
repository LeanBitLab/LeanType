// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.cjk

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import helium314.keyboard.event.Event
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.utils.Log

/**
 * Coordinates CJK engine lifecycle, key event interception, composing text synchronization,
 * and candidate UI updates.
 */
class CjkInputManager private constructor(private val context: Context) {

    interface CjkUiListener {
        fun onUpdateCandidates(candidates: List<CjkCandidate>, selectedIndex: Int, preedit: String)
        fun onClearCandidates()
        fun onSetCandidateStripVisibility(visible: Boolean)
    }

    private var uiListener: CjkUiListener? = null
    private var activeLocale: String? = null
    var isComposing: Boolean = false
        internal set

    fun setUiListener(listener: CjkUiListener?) {
        this.uiListener = listener
    }

    /**
     * Checks if the currently active subtype is a CJK subtype (Chinese or Japanese),
     * or uses a layout specifically designed for CJK composition.
     * Note: Korean (ko) uses native HangulCombiner unless an explicit CjkEngine extra value is present.
     */
    fun isCjkSubtypeActive(subtype: RichInputMethodSubtype?): Boolean {
        if (subtype == null) return false
        if (subtype.hasExtraValue("CjkEngine")) return true
        val lang = subtype.locale.language.lowercase()
        if (lang == "zh" || lang == "ja") return true
        val layout = subtype.mainLayoutName.lowercase()
        if (layout.contains("pinyin") || layout.contains("wubi") || layout.contains("romaji")) return true
        return false
    }

    /**
     * Intercepts an input event if CJK is active.
     * Returns true if the event was consumed by the CJK engine and should bypass LatinIME InputLogic.
     */
    fun onEvent(event: Event, ic: InputConnection?, subtype: RichInputMethodSubtype?): Boolean {
        if (!isCjkSubtypeActive(subtype)) return false

        val engine = CjkPluginLoader.getEngine(context)
        if (engine == null) {
            Log.w(TAG, "onEvent: CJK subtype active (${subtype?.locale}) but engine is not available")
            return false
        }

        val lang = subtype?.locale?.language?.lowercase() ?: "zh"
        if (activeLocale != lang) {
            engine.setLocale(lang)
            activeLocale = lang
        }

        val keysym = KeycodeTranslator.toKeysym(event)
        if (keysym == 0) return false

        var metaState = event.metaState
        if (event.codePoint in 'A'.code..'Z'.code) {
            metaState = metaState or android.view.KeyEvent.META_SHIFT_ON
        }
        val mask = KeycodeTranslator.getModifierMask(metaState)
        val consumed = engine.processKey(keysym, mask)
        Log.d(TAG, "onEvent: keysym=0x${Integer.toHexString(keysym)}, consumed=$consumed, isComposing=$isComposing")

        if (consumed) {
            syncStateWithInputConnection(engine, ic)
            return true
        }

        // If the engine did not consume the key, but we were composing, check if backspace or enter cleared it
        if (isComposing) {
            syncStateWithInputConnection(engine, ic)
        }

        return false
    }

    /**
     * Synchronizes engine state with the target application's InputConnection and the candidate UI.
     */
    private fun syncStateWithInputConnection(engine: ICjkEngine, ic: InputConnection?) {
        // 1. Commit text if any
        val commitText = engine.getCommit()
        if (!commitText.isNullOrEmpty()) {
            ic?.commitText(commitText, 1)
        }

        // 2. Fetch Pre-edit and Candidates
        val ctx = engine.getContext()
        if (ctx != null && (ctx.preedit.isNotEmpty() || ctx.candidates.isNotEmpty())) {
            isComposing = true
            if (ctx.preedit.isNotEmpty()) {
                val composingText = ctx.preedit.replace("|", "")
                ic?.setComposingText(composingText, 1)
            }
            uiListener?.onSetCandidateStripVisibility(true)
            uiListener?.onUpdateCandidates(ctx.candidates, ctx.selectedIndex, ctx.preedit)
        } else {
            val wasComposing = isComposing
            isComposing = false
            if (commitText.isNullOrEmpty() && wasComposing) {
                ic?.setComposingText("", 1)
                ic?.finishComposingText()
            } else if (!commitText.isNullOrEmpty()) {
                ic?.finishComposingText()
            }
            uiListener?.onClearCandidates()
            uiListener?.onSetCandidateStripVisibility(false)
        }
    }

    /**
     * Selects a candidate by page index and commits it to the InputConnection.
     */
    fun selectCandidate(index: Int, ic: InputConnection?): Boolean {
        val engine = CjkPluginLoader.getEngine(context) ?: return false
        val selected = engine.selectCandidate(index)
        if (selected) {
            syncStateWithInputConnection(engine, ic)
            return true
        }
        return false
    }

    /**
     * Clears active composition in both the engine and the InputConnection.
     */
    fun clearComposition(ic: InputConnection? = null) {
        val engine = CjkPluginLoader.getEngine(context)
        engine?.clearComposition()
        if (isComposing) {
            ic?.finishComposingText()
        }
        isComposing = false
        uiListener?.onClearCandidates()
        uiListener?.onSetCandidateStripVisibility(false)
    }

    /**
     * Detects cursor jumping outside the active composing span and cleans up phantom state.
     */
    fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        composingSpanStart: Int, composingSpanEnd: Int,
        ic: InputConnection? = null
    ) {
        if (!isComposing) return

        // If user made a range selection, clear active composition
        if (newSelStart != newSelEnd) {
            Log.d(TAG, "Selection range active ($newSelStart, $newSelEnd); clearing CJK composition")
            clearComposition(ic)
            return
        }

        // Only validate cursor bounds if editor accurately reports the composing span
        if (composingSpanStart >= 0 && composingSpanEnd >= composingSpanStart) {
            if (newSelStart < composingSpanStart || newSelEnd > composingSpanEnd) {
                Log.d(TAG, "Cursor moved outside composing span ($newSelStart not in [$composingSpanStart, $composingSpanEnd]); clearing CJK composition")
                clearComposition(ic)
            }
        }
    }

    fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        // Ready for new input view session
    }

    fun onFinishInputView(finishingInput: Boolean, ic: InputConnection? = null) {
        if (isComposing) {
            clearComposition(ic)
        }
        uiListener?.onSetCandidateStripVisibility(false)
        CjkPluginLoader.getEngine(context)?.destroySession()
    }

    companion object {
        private const val TAG = "CjkInputManager"

        @Volatile
        private var instance: CjkInputManager? = null

        fun getInstance(context: Context): CjkInputManager =
            instance ?: synchronized(this) {
                instance ?: CjkInputManager(context.applicationContext).also { instance = it }
            }
    }
}
