// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.handwriting

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ProgressBar
import android.graphics.drawable.GradientDrawable
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.PointerTracker
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputConnection
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.dictionary.Dictionary
import android.view.inputmethod.EditorInfo
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.utils.LanguageOnSpacebarUtils

class HandwritingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), KeyboardActionListener {

    private lateinit var languageLabel: TextView
    private lateinit var clearButton: ImageButton
    private lateinit var canvas: HandwritingCanvas
    private lateinit var bottomRowKeyboard: MainKeyboardView
    private lateinit var downloadProgress: ProgressBar
    private var toolbar: View? = null // ponytail: track toolbar

    private var keyboardActionListener: KeyboardActionListener? = null
    private var editorInfo: EditorInfo? = null
    private var currentLanguage: String = ""

    private var currentComposingText = ""

    override fun onFinishInflate() {
        super.onFinishInflate()
        languageLabel = findViewById(R.id.handwriting_language_label)
        clearButton = findViewById(R.id.handwriting_clear_button)
        canvas = findViewById(R.id.handwriting_canvas)
        bottomRowKeyboard = findViewById(R.id.handwriting_bottom_row_keyboard)
        downloadProgress = findViewById(R.id.handwriting_download_progress)
        toolbar = findViewById(R.id.handwriting_toolbar)

        clearButton.setOnClickListener {
            clearCanvasAndComposition()
        }

        canvas.onStrokeStarted = {
            commitCurrentComposition()
            canvas.clear()
        }

        canvas.onRecognitionTriggered = { strokes ->
            performRecognition(strokes)
            canvas.fadeOutAndClear()
        }
    }

    fun startHandwriting(
        editorInfo: EditorInfo,
        keyboardActionListener: KeyboardActionListener,
        language: String
    ) {
        this.editorInfo = editorInfo
        this.keyboardActionListener = keyboardActionListener
        this.currentLanguage = language

        val colors = Settings.getValues().mColors
        toolbar?.let {
            it.background = null
            it.visibility = View.GONE // ponytail: hide by default to avoid duplicate toolbar/X buttons
        }
        canvas.background = null

        languageLabel.setTextColor(colors.get(ColorType.KEY_TEXT))
        colors.setColor(clearButton, ColorType.KEY_ICON)
        canvas.setColors(colors.get(ColorType.KEY_TEXT), colors.get(ColorType.KEY_HINT_TEXT))
        canvas.hintText = context.getString(R.string.handwriting_hint_write_here)

        languageLabel.text = language
        downloadProgress.visibility = View.GONE

        // Setup bottom row keyboard
        bottomRowKeyboard.setKeyPreviewPopupEnabled(Settings.getValues().mKeyPreviewPopupOn)
        bottomRowKeyboard.setKeyboardActionListener(this)

        try {
            PointerTracker.switchTo(bottomRowKeyboard)
            val kls = KeyboardLayoutSet.Builder.buildEmojiClipBottomRow(context, editorInfo)
            val keyboard = kls.getKeyboard(KeyboardId.ELEMENT_HANDWRITING_BOTTOM_ROW)
            bottomRowKeyboard.setKeyboard(keyboard)

            val languageOnSpacebarFormatType = LanguageOnSpacebarUtils.getLanguageOnSpacebarFormatType(keyboard.mId.mSubtype)
            val hasMultipleEnabledIMEsOrSubtypes = RichInputMethodManager.getInstance().hasMultipleEnabledIMEsOrSubtypes(true)
            bottomRowKeyboard.startDisplayLanguageOnSpacebar(
                true,
                languageOnSpacebarFormatType,
                hasMultipleEnabledIMEsOrSubtypes
            )
        } catch (e: Exception) {
            Log.e("HandwritingView", "Failed to setup bottom row keyboard", e)
        }

        clearCanvasAndComposition()

        val hasPlugin = HandwritingLoader.hasPlugin(context)
        val overlay = findViewById<View>(R.id.handwriting_plugin_overlay)
        if (!hasPlugin) {
            overlay?.visibility = View.VISIBLE
            val titleText = findViewById<TextView>(R.id.handwriting_plugin_title)
            val summaryText = findViewById<TextView>(R.id.handwriting_plugin_summary)
            val iconView = findViewById<ImageView>(R.id.handwriting_plugin_icon)
            val button = findViewById<TextView>(R.id.handwriting_plugin_button)

            if (titleText != null) titleText.setTextColor(colors.get(ColorType.KEY_TEXT))
            if (summaryText != null) summaryText.setTextColor(colors.get(ColorType.KEY_HINT_TEXT))
            if (iconView != null) colors.setColor(iconView, ColorType.KEY_ICON)

            if (button != null) {
                val btnBackground = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8f * context.resources.displayMetrics.density
                    setColor(colors.get(ColorType.ACTION_KEY_BACKGROUND))
                }
                button.background = btnBackground
                button.setTextColor(colors.get(ColorType.KEY_TEXT))

                // ponytail: download plugin directly on standard/standardfull flavor, otherwise go to Settings
                if ("standard" == helium314.keyboard.latin.BuildConfig.FLAVOR || "standardfull" == helium314.keyboard.latin.BuildConfig.FLAVOR) {
                    button.text = "Download Plugin"
                    button.setOnClickListener {
                        downloadPlugin(button)
                    }
                } else {
                    button.setOnClickListener {
                        val intent = android.content.Intent()
                        intent.setClass(context, helium314.keyboard.settings.SettingsActivity2::class.java)
                        intent.putExtra("screen", helium314.keyboard.settings.SettingsDestination.Libraries)
                        intent.putExtra("from_ime", true)
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("HandwritingView", "Failed to start settings activity", e)
                        }
                        KeyboardSwitcher.getInstance().latinIME?.requestHideSelf(0)
                    }
                }
            }
        } else {
            overlay?.visibility = View.GONE
        }

        val recognizer = HandwritingLoader.getRecognizer(context)
        val displayName = HandwritingLoader.getEffectiveDisplayName(context, language)
        if (recognizer != null) {
            recognizer.setLanguage(language)
            recognitionExecutor.execute {
                val isReady = recognizer.isLanguageReady(language)
                mainHandler.post {
                    if (!isReady) {
                        toolbar?.visibility = View.VISIBLE
                        val isOnlineFlavor = "standard" == helium314.keyboard.latin.BuildConfig.FLAVOR || "standardfull" == helium314.keyboard.latin.BuildConfig.FLAVOR
                        downloadProgress.visibility = View.GONE
                        if (isOnlineFlavor) {
                            languageLabel.text = "$displayName (Tap to download model)"
                            fun setupDownloadClickListener() {
                                languageLabel.setOnClickListener {
                                    languageLabel.setOnClickListener(null)
                                    languageLabel.text = "$displayName (Downloading...)"
                                    downloadProgress.visibility = View.VISIBLE
                                    downloadProgress.progress = 0
                                    recognizer.downloadModel(language, object : ModelDownloadListener {
                                        override fun onProgress(progress: Float) {
                                            mainHandler.post {
                                                val percent = (progress * 100).toInt()
                                                languageLabel.text = "$displayName (Downloading $percent%)"
                                                downloadProgress.progress = percent
                                            }
                                        }
                                        override fun onComplete(success: Boolean) {
                                            mainHandler.post {
                                                downloadProgress.visibility = View.GONE
                                                if (success) {
                                                    toolbar?.visibility = View.GONE
                                                    languageLabel.text = displayName
                                                    android.widget.Toast.makeText(context, "Handwriting model downloaded", android.widget.Toast.LENGTH_SHORT).show()
                                                } else {
                                                    toolbar?.visibility = View.VISIBLE
                                                    languageLabel.text = "$displayName (Download failed - tap to retry)"
                                                    android.widget.Toast.makeText(context, "Failed to download handwriting model", android.widget.Toast.LENGTH_LONG).show()
                                                    setupDownloadClickListener()
                                                }
                                            }
                                        }
                                    })
                                }
                            }
                            setupDownloadClickListener()
                        } else {
                            // Offline flavor has no internet access; redirect directly to handwriting settings for model management
                            languageLabel.text = "$displayName (No model - tap for settings)"
                            languageLabel.setOnClickListener {
                                val intent = android.content.Intent().apply {
                                    setClass(context, helium314.keyboard.settings.SettingsActivity2::class.java)
                                    putExtra("screen", helium314.keyboard.settings.SettingsDestination.Handwriting)
                                    putExtra("from_ime", true)
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("HandwritingView", "Failed to start handwriting settings activity", e)
                                }
                                KeyboardSwitcher.getInstance().latinIME?.requestHideSelf(0)
                            }
                        }
                    } else {
                        toolbar?.visibility = View.GONE
                        languageLabel.text = displayName
                        languageLabel.setOnClickListener(null)
                        downloadProgress.visibility = View.GONE
                    }
                }
            }
        }
    }

    fun stopHandwriting() {
        commitCurrentComposition()
        canvas.clear()
        bottomRowKeyboard.closing()
    }

    fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        if (enabled) {
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }
    }

    fun commitCurrentComposition() {
        if (currentComposingText.isNotEmpty()) {
            val latinIME = KeyboardSwitcher.getInstance().latinIME ?: return
            val ic = latinIME.currentInputConnection ?: return
            ic.finishComposingText()
            currentComposingText = ""
            latinIME.setSuggestions(SuggestedWords.getEmptyInstance())
        }
    }

    fun clearCanvasAndComposition() {
        canvas.clear()
        currentComposingText = ""
        val latinIME = KeyboardSwitcher.getInstance().latinIME
        if (latinIME != null) {
            val ic = latinIME.currentInputConnection
            if (ic != null) {
                ic.finishComposingText()
            }
            latinIME.setSuggestions(SuggestedWords.getEmptyInstance())
        }
    }

    private val recognitionExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun performRecognition(strokes: List<FloatArray>) {
        if (strokes.isEmpty()) return
        val recognizer = HandwritingLoader.getRecognizer(context) ?: return

        // setLanguage is fast (no blocking I/O), safe on main thread
        recognizer.setLanguage(currentLanguage)

        // recognize() uses Tasks.await() which must not run on main thread
        recognitionExecutor.execute {
            try {
                val results = recognizer.recognize(strokes)
                if (results.isNullOrEmpty()) return@execute

                mainHandler.post {
                    val mainCandidate = results[0]

                    val latinIME = KeyboardSwitcher.getInstance().latinIME ?: return@post
                    val ic = latinIME.currentInputConnection ?: return@post

                    if (currentComposingText.isNotEmpty()) {
                        ic.finishComposingText()
                        val textBefore = ic.getTextBeforeCursor(1, 0)
                        if (textBefore != null && textBefore.isNotEmpty() && textBefore != " " && textBefore != "\n") {
                            ic.commitText(" ", 1)
                        }
                    }

                    currentComposingText = mainCandidate

                    // Update composing text
                    ic.setComposingText(mainCandidate, 1)

                    // Populate suggestion strip with alternative candidates
                    val suggestionInfos = ArrayList<SuggestedWordInfo>()
                    for (word in results) {
                        suggestionInfos.add(
                            SuggestedWordInfo(
                                word,
                                "",
                                SuggestedWordInfo.MAX_SCORE,
                                SuggestedWordInfo.KIND_TYPED,
                                Dictionary.DICTIONARY_USER_TYPED,
                                SuggestedWordInfo.NOT_AN_INDEX,
                                SuggestedWordInfo.NOT_A_CONFIDENCE
                            )
                        )
                    }

                    val typedWordInfo = SuggestedWordInfo(
                        mainCandidate,
                        "",
                        SuggestedWordInfo.MAX_SCORE,
                        SuggestedWordInfo.KIND_TYPED,
                        Dictionary.DICTIONARY_USER_TYPED,
                        SuggestedWordInfo.NOT_AN_INDEX,
                        SuggestedWordInfo.NOT_A_CONFIDENCE
                    )

                    val suggestedWords = SuggestedWords(
                        suggestionInfos,
                        null,
                        typedWordInfo,
                        false,
                        false,
                        false,
                        SuggestedWords.INPUT_STYLE_TYPING,
                        SuggestedWords.NOT_A_SEQUENCE_NUMBER
                    )
                    latinIME.setSuggestions(suggestedWords)
                }
            } catch (e: Exception) {
                Log.e("HandwritingView", "Error during recognition", e)
            }
        }
    }

    // Intercept KeyboardActionListener events for the bottom row
    override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) {
        if (primaryCode == KeyCode.ALPHA) {
            // Close handwriting mode
            KeyboardSwitcher.getInstance().setAlphabetKeyboard()
            return
        }
        if (primaryCode == KeyCode.CLEAR_HANDWRITING) {
            clearCanvasAndComposition()
            return
        }
        
        // For other keys, commit the composition first when relevant
        if (primaryCode == Constants.CODE_SPACE || primaryCode == Constants.CODE_ENTER) {
            commitCurrentComposition()
        }
        
        keyboardActionListener?.onCodeInput(primaryCode, x, y, isKeyRepeat)
    }

    override fun onTextInput(text: String?) {
        commitCurrentComposition()
        keyboardActionListener?.onTextInput(text)
    }

    override fun onImageSelected(imageUri: String) {
        keyboardActionListener?.onImageSelected(imageUri)
    }

    override fun onPressKey(primaryCode: Int, repeatCount: Int, isSinglePointer: Boolean, hapticEvent: HapticEvent) {
        keyboardActionListener?.onPressKey(primaryCode, repeatCount, isSinglePointer, hapticEvent)
    }

    override fun onReleaseKey(primaryCode: Int, withSliding: Boolean) {
        keyboardActionListener?.onReleaseKey(primaryCode, withSliding)
    }

    override fun onLongPressKey(primaryCode: Int) {
        if (primaryCode == KeyCode.CLEAR_HANDWRITING) {
            PointerTracker.cancelAllPointerTrackers()
            KeyboardSwitcher.getInstance().setAlphabetKeyboard()
            return
        }
        keyboardActionListener?.onLongPressKey(primaryCode)
    }

    override fun onKeyDown(keyCode: Int, keyEvent: android.view.KeyEvent): Boolean {
        return keyboardActionListener?.onKeyDown(keyCode, keyEvent) ?: false
    }

    override fun onKeyUp(keyCode: Int, keyEvent: android.view.KeyEvent): Boolean {
        return keyboardActionListener?.onKeyUp(keyCode, keyEvent) ?: false
    }

    override fun onStartBatchInput() { keyboardActionListener?.onStartBatchInput() }
    override fun onUpdateBatchInput(p: helium314.keyboard.latin.common.InputPointers) { keyboardActionListener?.onUpdateBatchInput(p) }
    override fun onEndBatchInput(p: helium314.keyboard.latin.common.InputPointers) { keyboardActionListener?.onEndBatchInput(p) }
    override fun onCancelBatchInput() { keyboardActionListener?.onCancelBatchInput() }
    override fun onCancelInput() { keyboardActionListener?.onCancelInput() }
    override fun onFinishSlidingInput() { keyboardActionListener?.onFinishSlidingInput() }
    override fun onCustomRequest(requestCode: Int): Boolean { return keyboardActionListener?.onCustomRequest(requestCode) ?: false }
    override fun onHorizontalSpaceSwipe(steps: Int): Boolean { return keyboardActionListener?.onHorizontalSpaceSwipe(steps) ?: false }
    override fun onVerticalSpaceSwipe(steps: Int): Boolean { return keyboardActionListener?.onVerticalSpaceSwipe(steps) ?: false }
    override fun onEndSpaceSwipe() { keyboardActionListener?.onEndSpaceSwipe() }
    override fun toggleNumpad(w: Boolean, f: Boolean): Boolean { return keyboardActionListener?.toggleNumpad(w, f) ?: false }
    override fun onMoveDeletePointer(steps: Int) { keyboardActionListener?.onMoveDeletePointer(steps) }
    override fun onUpWithDeletePointerActive() { keyboardActionListener?.onUpWithDeletePointerActive() }
    override fun resetMetaState() { keyboardActionListener?.resetMetaState() }

    // ponytail: downloads the latest handwriting plugin apk, imports it and updates overlay visibility
    private fun downloadPlugin(button: TextView) {
        button.text = "Downloading..."
        button.isEnabled = false
        android.widget.Toast.makeText(context, "Downloading Handwriting Plugin...", android.widget.Toast.LENGTH_SHORT).show()

        recognitionExecutor.execute {
            try {
                val tempFile = java.io.File(context.cacheDir, "temp_handwriting_plugin.apk")
                val downloaded = HandwritingLoader.downloadPluginApk(context, null, tempFile)
                if (!downloaded) {
                    throw java.io.IOException("Failed to download handwriting plugin APK")
                }

                val success = HandwritingLoader.importPlugin(context, android.net.Uri.fromFile(tempFile))
                tempFile.delete()

                mainHandler.post {
                    button.isEnabled = true
                    if (success) {
                        button.text = "Success"
                        android.widget.Toast.makeText(context, "Handwriting plugin installed!", android.widget.Toast.LENGTH_SHORT).show()
                        val overlay = findViewById<View>(R.id.handwriting_plugin_overlay)
                        overlay?.visibility = View.GONE
                        editorInfo?.let { ei ->
                            keyboardActionListener?.let { listener ->
                                startHandwriting(ei, listener, currentLanguage)
                            }
                        }
                    } else {
                        button.text = "Download Plugin"
                        android.widget.Toast.makeText(context, "Failed to install plugin", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("HandwritingView", "Failed to download plugin", e)
                mainHandler.post {
                    button.isEnabled = true
                    button.text = "Download Plugin"
                    android.widget.Toast.makeText(context, "Download failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
