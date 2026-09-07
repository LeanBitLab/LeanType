package helium314.keyboard.latin

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Message
import android.os.Process
import android.util.PrintWriterPrinter
import android.util.Printer
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.leanbitlab.leantype.voice.VoiceConstants
import helium314.keyboard.accessibility.AccessibilityUtils
import helium314.keyboard.compat.EditorInfoCompatUtils
import helium314.keyboard.compat.ImeCompat.shouldSwitchToOtherInputMethods
import helium314.keyboard.compat.ImeCompat.switchInputMethod
import helium314.keyboard.compat.ImeCompat.switchInputMethodAndSubtypeCompat
import helium314.keyboard.compat.ImeCompat.switchInputMethodCompat
import helium314.keyboard.compat.locale
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.event.Event
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.event.InputTransaction
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardActionListenerImpl
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.emoji.EmojiPalettesView
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.CoordinateUtils
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.common.setInsetsOutlineProvider
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.inputlogic.InputLogic
import helium314.keyboard.latin.personalization.PersonalizationHelper
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.latin.suggestions.SuggestionStripViewAccessor
import helium314.keyboard.latin.suggestions.VoiceVisualizerView
import helium314.keyboard.latin.touchinputconsumer.GestureConsumer
import helium314.keyboard.latin.utils.createInputMethodPickerDialog
import helium314.keyboard.latin.utils.getDisplayContext
import helium314.keyboard.latin.utils.isBrightColor
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.updateSoftInputWindowLayoutParameters
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.InlineAutofillUtils
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.LeakGuardHandlerWrapper
import helium314.keyboard.latin.utils.LocaleUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ProofreadHelper
import helium314.keyboard.latin.utils.RecapitalizeMode
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.ScreenProfileProvider
import helium314.keyboard.latin.utils.StatsUtils
import helium314.keyboard.latin.utils.StatsUtilsManager
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.SubtypeState
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.voice.VoiceInputManager
import helium314.keyboard.latin.voice.VoicePluginManager
import helium314.keyboard.settings.SettingsActivity2
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Input method implementation for Qwerty'ish keyboard.
 */
class LatinIME : InputMethodService(),
    SuggestionStripView.Listener,
    SuggestionStripViewAccessor,
    DictionaryFacilitator.DictionaryInitializationListener {

    private var lastSettingsLocale: Locale? = null
    private var lastInputType = 0
    private var lastOrientation = 0

    val mSettings: Settings = Settings.getInstance()
    val settings: Settings get() = mSettings

    private val dictionaryFacilitator: DictionaryFacilitator = DictionaryFacilitatorProvider.getDictionaryFacilitator(false)

    val mHandler: UIHandler = UIHandler(this)
    val handler: UIHandler get() = mHandler

    val mInputLogic: InputLogic = InputLogic(this, this, dictionaryFacilitator)
    val inputLogic: InputLogic get() = mInputLogic

    val mKeyboardSwitcher: KeyboardSwitcher = KeyboardSwitcher.getInstance()
    val keyboardSwitcher: KeyboardSwitcher get() = mKeyboardSwitcher

    val mKeyboardActionListener: KeyboardActionListener = KeyboardActionListenerImpl(this, mInputLogic)
    val keyboardActionListener: KeyboardActionListener get() = mKeyboardActionListener

    private var originalNavBarColor = 0
    private var originalNavBarFlags = 0
    private var originalNavBarSaved = false
    private var lastMainDictionaryAvailable = false

    var mInputView: View? = null
    val inputView: View?
        get() = mInputView

    private var insetsUpdater: helium314.keyboard.latin.common.InsetsOutlineProvider? = null
    private var suggestionStripView: SuggestionStripView? = null
    lateinit var richImm: RichInputMethodManager
        private set

    private val subtypeState = SubtypeState { subtype ->
        switchToSubtype(subtype)
    }

    private val statsUtilsManager: StatsUtilsManager = StatsUtilsManager.getInstance()

    private var isExecutingStartShowingInputView = false
    private var displayContext: Context? = null

    private val dictionaryPackInstallReceiver: BroadcastReceiver = DictionaryPackInstallBroadcastReceiver(this)
    private val dictionaryDumpBroadcastReceiver: BroadcastReceiver = DictionaryDumpBroadcastReceiver(this)
    private val restartAfterDeviceUnlockReceiver = RestartAfterDeviceUnlockReceiver()

    private var optionsDialog: AlertDialog? = null
    private val isHardwareAcceleratedDrawingEnabled: Boolean

    private var gestureConsumer: GestureConsumer = GestureConsumer.NULL_GESTURE_CONSUMER
    val clipboardHistoryManager = ClipboardHistoryManager(this)
    private val otpSuggestionManager = OtpSuggestionManager(this)
    private val mathSuggestionManager = MathSuggestionManager(this)
    var floatingKeyboardManager: FloatingKeyboardManager? = null

    private var voicePluginManager: VoicePluginManager? = null
    private var voiceInputManager: VoiceInputManager? = null
    private var lastVoiceState = VoiceInputManager.VoiceState.IDLE

    private var appliedLanguage = ""

    init {
        isHardwareAcceleratedDrawingEnabled = this.enableHardwareAcceleration()
        Log.i(TAG, "Hardware accelerated drawing: $isHardwareAcceleratedDrawingEnabled")
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        val prefs = DeviceProtectedUtils.getSharedPreferences(newBase)
        val lang = prefs.getString(Settings.PREF_APP_LANGUAGE, Defaults.PREF_APP_LANGUAGE)
        appliedLanguage = lang ?: Defaults.PREF_APP_LANGUAGE
        LocaleUtils.applyAppLanguageToResources(this, appliedLanguage)
    }

    override fun onCreate() {
        sInstance = this
        settings.startListener()
        KeyboardIconsSet.instance.loadIcons(this)
        richImm = RichInputMethodManager.getInstance()
        AudioAndHapticFeedbackManager.init(this)
        AccessibilityUtils.init(this)
        statsUtilsManager.onCreate(this, dictionaryFacilitator)
        displayContext = getDisplayContext()
        KeyboardSwitcher.init(this)
        floatingKeyboardManager = FloatingKeyboardManager(this, this)
        voicePluginManager = VoicePluginManager(this)
        voiceInputManager = VoiceInputManager(this, voicePluginManager!!)
        
        super.onCreate()

        loadSettings()
        clipboardHistoryManager.onCreate()
        handler.onCreate()

        val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        ContextCompat.registerReceiver(this, ringerModeChangeReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme(SCHEME_PACKAGE)
        }
        ContextCompat.registerReceiver(this, dictionaryPackInstallReceiver, packageFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val newDictFilter = IntentFilter(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)
        ContextCompat.registerReceiver(this, dictionaryPackInstallReceiver, newDictFilter, ContextCompat.RECEIVER_EXPORTED)

        val dictDumpFilter = IntentFilter(DictionaryDumpBroadcastReceiver.DICTIONARY_DUMP_INTENT_ACTION)
        ContextCompat.registerReceiver(this, dictionaryDumpBroadcastReceiver, dictDumpFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val restartAfterUnlockFilter = IntentFilter()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            restartAfterUnlockFilter.addAction(Intent.ACTION_USER_UNLOCKED)
        }
        ContextCompat.registerReceiver(this, restartAfterDeviceUnlockReceiver, restartAfterUnlockFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        StatsUtils.onCreate(settings.current, richImm)
    }

    private fun loadSettings() {
        val locale = richImm.currentSubtypeLocale
        val editorInfo = currentInputEditorInfo
        val inputType = editorInfo?.inputType ?: 0
        val orientation = resources.configuration.orientation

        if (!sSettingsDirty &&
            locale == lastSettingsLocale &&
            inputType == lastInputType &&
            orientation == lastOrientation
        ) {
            return
        }

        sSettingsDirty = false
        lastSettingsLocale = locale
        lastInputType = inputType
        lastOrientation = orientation

        val inputAttributes = InputAttributes(editorInfo, isFullscreenMode, packageName)
        val currentKeyboardScript = keyboardSwitcher.currentKeyboardScript
        settings.loadSettings(this, locale, inputAttributes, currentKeyboardScript)

        val currentSettingsValues = settings.current
        AudioAndHapticFeedbackManager.getInstance().onSettingsChanged(currentSettingsValues)

        val prefs = DeviceProtectedUtils.getSharedPreferences(this)
        if (prefs.getBoolean("pref_gesture_lib_just_installed", false)) {
            prefs.edit().remove("pref_gesture_lib_just_installed").apply()
            resetSuggestMainDict()
        } else if (!handler.hasPendingReopenDictionaries()) {
            resetDictionaryFacilitatorIfNecessary()
        }

        refreshPersonalizationDictionarySession(currentSettingsValues)
        inputLogic.suggest.clearNextWordSuggestionsCache()
        inputLogic.updateEmojiDictionary(locale)
        statsUtilsManager.onLoadSettings(this, currentSettingsValues)
    }

    private fun refreshPersonalizationDictionarySession(currentSettingsValues: SettingsValues) {
        if (!currentSettingsValues.mUsePersonalizedDicts) {
            PersonalizationHelper.removeAllUserHistoryDictionaries(this)
            dictionaryFacilitator.clearUserHistoryDictionary(this)
        }
    }

    override fun onUpdateMainDictionaryAvailability(isMainDictionaryAvailable: Boolean) {
        keyboardSwitcher.mainKeyboardView?.setMainDictionaryAvailability(isMainDictionaryAvailable)
        handler.post {
            if (lastMainDictionaryAvailable != isMainDictionaryAvailable) {
                inputLogic.suggest.clearNextWordSuggestionsCache()
                if (isMainDictionaryAvailable && !handler.hasPendingWaitForDictionaryLoad()) {
                    handler.postUpdateSuggestionStrip(SuggestedWords.INPUT_STYLE_TYPING)
                }
            }
            lastMainDictionaryAvailable = isMainDictionaryAvailable
            if (handler.hasPendingWaitForDictionaryLoad()) {
                handler.cancelWaitForDictionaryLoad()
                handler.postResumeSuggestions(false)
            }
        }
    }

    fun resetDictionaryFacilitatorIfNecessary() {
        val subtypeSwitcherLocale = richImm.currentSubtypeLocale
        val subtypeLocale = subtypeSwitcherLocale ?: resources.configuration.locale()

        val locales = ArrayList<Locale>().apply {
            add(subtypeLocale)
            addAll(settings.current.mSecondaryLocales)
        }

        if (dictionaryFacilitator.usesSameSettings(
                locales,
                settings.current.mUseContactsDictionary,
                settings.current.mUseAppsDictionary,
                settings.current.mUsePersonalizedDicts
            )
        ) {
            return
        }

        handler.post {
            inputLogic.suggest.clearNextWordSuggestionsCache()
            lastMainDictionaryAvailable = false
        }
        resetDictionaryFacilitator(subtypeLocale)
    }

    private fun resetDictionaryFacilitator(locale: Locale) {
        val settingsValues = settings.current
        try {
            dictionaryFacilitator.resetDictionaries(
                this, locale,
                settingsValues.mUseContactsDictionary, settingsValues.mUseAppsDictionary,
                settingsValues.mUsePersonalizedDicts, false, "", this
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Could not reset dictionary facilitator, please fix ASAP", e)
        }
        inputLogic.suggest.setAutoCorrectionThreshold(settingsValues.mAutoCorrectionThreshold)
    }

    fun resetSuggestMainDict() {
        val settingsValues = settings.current
        dictionaryFacilitator.resetDictionaries(
            this, dictionaryFacilitator.mainLocale,
            settingsValues.mUseContactsDictionary, settingsValues.mUseAppsDictionary,
            settingsValues.mUsePersonalizedDicts, true, "", this
        )
        EmojiPalettesView.closeDictionaryFacilitator()
    }

    val localeAndConfidenceInfo: String? get() = dictionaryFacilitator.localesAndConfidences()

    override fun onDestroy() {
        if (sInstance === this) sInstance = null
        voiceInputManager?.release(); voiceInputManager = null
        voicePluginManager?.release(); voicePluginManager = null
        handler.removeCallbacksAndMessages(null)
        floatingKeyboardManager?.destroy()
        clipboardHistoryManager.onDestroy()
        otpSuggestionManager.stop()
        
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            dictionaryFacilitator.closeDictionaries()
        }
        
        settings.onDestroy()
        try { unregisterReceiver(ringerModeChangeReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(dictionaryPackInstallReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(dictionaryDumpBroadcastReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(restartAfterDeviceUnlockReceiver) } catch (e: Exception) {}
        
        statsUtilsManager.onDestroy(this)
        AudioAndHapticFeedbackManager.getInstance().onDestroy()
        super.onDestroy()
        deallocateMemory()
    }

    private fun isImeSuppressedByHardwareKeyboard(): Boolean {
        val switcher = KeyboardSwitcher.getInstance()
        if (switcher.isShowingEmojiPalettes || switcher.isShowingClipboardHistory) return false
        return !onEvaluateInputViewShown() && switcher.isImeSuppressedByHardwareKeyboard(settings.current, switcher.keyboardSwitchState)
    }

    override fun onConfigurationChanged(conf: Configuration) {
        super.onConfigurationChanged(conf)
        ScreenProfileProvider.invalidateCache()
        loadSettings()
        
        val prefs = DeviceProtectedUtils.getSharedPreferences(this)
        val lang = prefs.getString(Settings.PREF_APP_LANGUAGE, Defaults.PREF_APP_LANGUAGE)
        if (!lang.isNullOrEmpty() && lang != "system") {
            val locale = LocaleUtils.parseLocale(lang)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                conf.setLocales(android.os.LocaleList(locale))
            } else {
                @Suppress("DEPRECATION")
                conf.locale = locale
            }
        }
        
        val settingsValues = settings.current
        Log.i(TAG, "onConfigurationChanged")
        SubtypeSettings.reloadSystemLocales(this)
        
        if (settingsValues.mDisplayOrientation != conf.orientation) {
            handler.startOrientationChanging()
            inputLogic.onOrientationChange(settings.current)
        }
        
        if (settingsValues.mHasHardwareKeyboard != Settings.readHasHardwareKeyboard(conf)) {
            if (isImeSuppressedByHardwareKeyboard()) {
                cleanupInternalStateForFinishInput()
            }
        }
        
        keyboardSwitcher.updateKeyboardTheme(getDisplayContext())
        keyboardSwitcher.onConfigurationChanged(conf)
        setNavigationBarColor()
    }

    override fun onInitializeInterface() {
        displayContext = getDisplayContext()
        Log.d(TAG, "onInitializeInterface")
        keyboardSwitcher.updateKeyboardTheme(displayContext ?: this)
    }

    override fun onCreateInputView(): View {
        StatsUtils.onCreateInputView()
        return keyboardSwitcher.onCreateInputView(getDisplayContext(), isHardwareAcceleratedDrawingEnabled)
    }

    override fun setInputView(view: View) {
        super.setInputView(view)
        mInputView = view
        insetsUpdater = setInsetsOutlineProvider(view)
        updateSoftInputWindowLayoutParameters(inputView)
        
        suggestionStripView = if (settings.current.mToolbarMode == ToolbarMode.HIDDEN) null else view.findViewById(R.id.suggestion_strip_view)
        if (hasSuggestionStripView()) {
            suggestionStripView!!.setRtl(richImm.currentSubtype.isRtlSubtype)
            suggestionStripView!!.setListener(this, view)
        }
        
        if (floatingKeyboardManager != null && floatingKeyboardManager!!.isFloating) {
            floatingKeyboardManager!!.onInputViewRecreated(view)
        }
        
        voiceInputManager?.setListener(object : VoiceInputManager.VoiceInputListener {
            override fun onStateChanged(state: VoiceInputManager.VoiceState) { onVoiceStateChanged(state) }
            override fun onError(message: String) {
                Toast.makeText(this@LatinIME, message, Toast.LENGTH_LONG).show()
                onVoiceStateChanged(VoiceInputManager.VoiceState.ERROR)
            }
        })
    }

    fun onVoiceStateChanged(state: VoiceInputManager.VoiceState) {
        handler.post {
            if (state == lastVoiceState) return@post
            lastVoiceState = state
            if (hasSuggestionStripView()) {
                when (state) {
                    VoiceInputManager.VoiceState.CONNECTING_PLUGIN, VoiceInputManager.VoiceState.STARTING_SESSION -> {
                        suggestionStripView!!.showVoiceStatus(
                            getString(R.string.voice_status_connecting), false,
                            { voiceInputManager?.stopVoice() }, { voiceInputManager?.cancelVoice() },
                            VoiceVisualizerView.Mode.CONNECTING
                        )
                    }
                    VoiceInputManager.VoiceState.RECORDING -> {
                        suggestionStripView!!.showVoiceStatus(
                            getString(R.string.voice_status_listening), false,
                            { voiceInputManager?.stopVoice() }, { voiceInputManager?.cancelVoice() },
                            VoiceVisualizerView.Mode.RECORDING
                        )
                    }
                    VoiceInputManager.VoiceState.PROCESSING_FINAL -> {
                        suggestionStripView!!.showVoiceStatus(
                            getString(R.string.voice_status_processing), true,
                            null, { voiceInputManager?.cancelVoice() },
                            VoiceVisualizerView.Mode.PROCESSING
                        )
                    }
                    else -> suggestionStripView!!.hideVoiceStatus()
                }
            }
            
            val kv = keyboardSwitcher.mainKeyboardView
            val kb = kv?.keyboard
            if (kv != null && kb != null) {
                for (key in kb.sortedKeys) {
                    if (key.code == KeyCode.VOICE_INPUT) kv.invalidateKey(key)
                }
            }
        }
    }

    fun onFloatingKeyboardShown() {
        inputView?.visibility = View.GONE
        requestHideSelf(0)
    }

    fun onFloatingKeyboardHidden(showDockedKeyboard: Boolean) {
        setInputView(onCreateInputView())
        updateInputViewShown()
        if (showDockedKeyboard) {
            requestShowSelf(0)
            startShowingInputView(true)
        }
    }

    override fun setCandidatesView(view: View) { /* To ensure that CandidatesView will never be set. */ }

    override fun onStartInput(editorInfo: EditorInfo, restarting: Boolean) {
        handler.onStartInput(editorInfo, restarting)
    }

    override fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
        handler.onStartInputView(editorInfo, restarting)
        statsUtilsManager.onStartInputView()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        StatsUtils.onFinishInputView()
        handler.onFinishInputView(finishingInput)
        statsUtilsManager.onFinishInputView()
        gestureConsumer = GestureConsumer.NULL_GESTURE_CONSUMER
        
        if (KeyboardActionListenerImpl.sPersistentTextEditModeActive && !Settings.getInstance().current.mPersistTextEditMode) {
            KeyboardActionListenerImpl.sPersistentTextEditModeActive = false
            keyboardSwitcher.hideTextEditView()
        }
    }

    override fun onFinishInput() {
        handler.onFinishInput()
        if (floatingKeyboardManager != null && floatingKeyboardManager!!.isFloating && !Settings.getInstance().current.mPersistFloatingKeyboard) {
            floatingKeyboardManager!!.hide(false)
        }
        if (KeyboardActionListenerImpl.sPersistentTextEditModeActive && !Settings.getInstance().current.mPersistTextEditMode) {
            KeyboardActionListenerImpl.sPersistentTextEditModeActive = false
            keyboardSwitcher.hideTextEditView()
        }
    }

    override fun onCurrentInputMethodSubtypeChanged(subtype: InputMethodSubtype) {
        if (subtype.hashCode() == 0x7000000f) return
        val oldSubtype = richImm.currentSubtype.rawSubtype
        if (subtype == oldSubtype) return
        
        subtypeState.onSubtypeChanged(oldSubtype, subtype)
        StatsUtils.onSubtypeChanged(oldSubtype, subtype)
        richImm.onSubtypeChanged(subtype)
        inputLogic.onSubtypeChanged(SubtypeLocaleUtils.getCombiningRulesExtraValue(subtype), settings.current)
        loadKeyboard()
        
        if (hasSuggestionStripView()) {
            suggestionStripView!!.setRtl(richImm.currentSubtype.isRtlSubtype)
        }
        settings.saveSubtypeForApp(richImm.currentSubtype, currentInputEditorInfo.packageName)
    }

    fun switchToSubtype(subtype: InputMethodSubtype?) {
        if (subtype == null) return
        onCurrentInputMethodSubtypeChanged(subtype)
    }

    fun onStartInputInternal(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInput(editorInfo, restarting)
        if (editorInfo == null || editorInfo.inputType == android.text.InputType.TYPE_NULL) {
            if (floatingKeyboardManager != null && floatingKeyboardManager!!.isFloating && !Settings.getInstance().current.mPersistFloatingKeyboard) {
                floatingKeyboardManager!!.hide(false)
            }
        }
        
        val subtypeForApp = if (editorInfo == null) null else settings.getSubtypeForApp(editorInfo.packageName)
        val hintLocales = EditorInfoCompatUtils.getHintLocales(editorInfo).toMutableList()
        val subtypeForLocales = subtypeState.getSubtypeForLocales(richImm, hintLocales, subtypeForApp)
        
        if (subtypeForLocales != null) {
            handler.postSwitchLanguage(subtypeForLocales)
        }
    }

    fun onStartInputViewInternal(editorInfo: EditorInfo, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        ProofreadHelper.preloadModel(this)
        
        if (voicePluginManager != null && !voicePluginManager!!.isPluginConnected() &&
            DeviceProtectedUtils.getSharedPreferences(this).getBoolean(VoiceConstants.PREF_VOICE_OFFLINE_ENABLED, false)
        ) {
            voicePluginManager!!.bindIfNeeded()
        }
        
        clipboardHistoryManager.onStartInputView()
        AudioAndHapticFeedbackManager.getInstance().onStartInputView()
        dictionaryFacilitator.onStartInput()
        gestureConsumer = GestureConsumer.NULL_GESTURE_CONSUMER
        richImm.refreshSubtypeCaches()
        
        val switcher = keyboardSwitcher
        switcher.updateKeyboardTheme(displayContext ?: this)
        val mainKeyboardView = switcher.mainKeyboardView
        
        var currentSettingsValues = settings.current
        if (DebugFlags.DEBUG_ENABLED && editorInfo == null) {
            throw NullPointerException("Null EditorInfo in onStartInputView()")
        }
        if (mainKeyboardView == null) return
        
        Log.i(TAG, "${if (restarting) "Res" else "S"}tarting input. Cursor position = ${editorInfo.initialSelStart},${editorInfo.initialSelEnd}")
        if (DebugFlags.DEBUG_ENABLED) EditorInfoCompatUtils.debugLog(editorInfo, TAG)
        
        gestureConsumer = GestureConsumer.newInstance(editorInfo, inputLogic.getPrivateCommandPerformer(), richImm.currentSubtypeLocale, switcher.keyboard)
        
        val accessUtils = AccessibilityUtils.instance
        if (accessUtils.isTouchExplorationEnabled) {
            accessUtils.onStartInputViewInternal(mainKeyboardView, editorInfo, restarting)
        }
        
        val inputTypeChanged = !currentSettingsValues.isSameInputType(editorInfo)
        val isDifferentTextField = !restarting || inputTypeChanged
        StatsUtils.onStartInputView(editorInfo.inputType, Settings.getValues().mDisplayOrientation, !isDifferentTextField)
        
        updateFullscreenMode()
        
        if (isDifferentTextField || !currentSettingsValues.hasSameOrientation(resources.configuration)) {
            loadSettings()
            currentSettingsValues = settings.current
            if (hasSuggestionStripView()) suggestionStripView!!.updateVoiceKey()
        }
        
        val needToCallLoadKeyboardLater: Boolean
        if (!isImeSuppressedByHardwareKeyboard() || currentSettingsValues.mHasHardwareKeyboard) {
            inputLogic.startInput(richImm.combiningRulesExtraValueOfCurrentSubtype, currentSettingsValues)
            resetDictionaryFacilitatorIfNecessary()
            
            if (!inputLogic.connection.resetCachesUponCursorMoveAndReturnSuccess(editorInfo.initialSelStart, editorInfo.initialSelEnd, false)) {
                handler.postResetCaches(isDifferentTextField, 5)
                needToCallLoadKeyboardLater = true
            } else {
                inputLogic.connection.tryFixIncorrectCursorPosition()
                if (inputLogic.connection.isCursorTouchingWord(currentSettingsValues.mSpacingAndPunctuations, true)) {
                    handler.postResumeSuggestions(true)
                }
                needToCallLoadKeyboardLater = false
            }
        } else {
            needToCallLoadKeyboardLater = false
        }
        
        if (isDifferentTextField) {
            mainKeyboardView.closing()
            inputLogic.suggest.setAutoCorrectionThreshold(currentSettingsValues.mAutoCorrectionThreshold)
            switcher.reloadMainKeyboard()
            if (needToCallLoadKeyboardLater) switcher.saveKeyboardState()
        } else if (restarting) {
            switcher.resetKeyboardStateToAlphabet(currentAutoCapsState, currentRecapitalizeState)
            switcher.requestUpdatingShiftState(currentAutoCapsState, currentRecapitalizeState)
        }
        
        if (hasSuggestionStripView() && !currentSettingsValues.mRememberToolbarState) {
            val defaultVisible = currentSettingsValues.mAutoShowToolbar
            suggestionStripView!!.isToolbarManuallyOpen = defaultVisible
            suggestionStripView!!.setToolbarVisibility(defaultVisible, false)
        }
        
        if (!handler.hasPendingResumeSuggestions()) {
            handler.cancelUpdateSuggestionStrip()
            setNeutralSuggestionStrip()
            if (hasSuggestionStripView() && currentSettingsValues.mAutoShowToolbar && !tryShowClipboardSuggestion()) {
                suggestionStripView!!.setToolbarVisibility(true)
            }
            if (shouldRequestInitialPredictions(currentSettingsValues)) {
                handler.postUpdateSuggestionStrip(SuggestedWords.INPUT_STYLE_RECORRECTION)
            }
        }
        
        mainKeyboardView.setMainDictionaryAvailability(dictionaryFacilitator.hasAtLeastOneInitializedMainDictionary())
        mainKeyboardView.setKeyPreviewPopupEnabled(currentSettingsValues.mKeyPreviewPopupOn)
        mainKeyboardView.setSlidingKeyInputPreviewEnabled(currentSettingsValues.mSlidingKeyInputPreviewEnabled)
        mainKeyboardView.setGestureHandlingEnabledByUser(
            currentSettingsValues.mGestureInputEnabled,
            currentSettingsValues.mGestureTrailEnabled,
            currentSettingsValues.mGestureFloatingPreviewTextEnabled
        )
        
        if (floatingKeyboardManager != null && floatingKeyboardManager!!.isFloating) {
            inputView?.visibility = View.GONE
            requestHideSelf(0)
        } else if (currentSettingsValues.mRememberFloatingKeyboard &&
            floatingKeyboardManager != null &&
            floatingKeyboardManager!!.wasFloatingLastTime() &&
            floatingKeyboardManager!!.canDrawOverlays()
        ) {
            floatingKeyboardManager!!.show()
        }
        
        if (isInputViewShown) setNavigationBarColor()
        if (TRACE) Debug.startMethodTracing("/data/trace/latinime")
        
        otpSuggestionManager.start()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        clipboardHistoryManager.onPrimaryClipChanged()
        if (isInputViewShown) {
            setNavigationBarColor()
            workaroundForHuaweiStatusBarIssue()
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        Log.i(TAG, "onWindowHidden")
        keyboardSwitcher.mainKeyboardView?.closing()
        clearNavigationBarColor()
        originalNavBarSaved = false
    }

    fun onFinishInputInternal() {
        super.onFinishInput()
        Log.i(TAG, "onFinishInput")
        if (floatingKeyboardManager != null && floatingKeyboardManager!!.isFloating && !Settings.getInstance().current.mPersistFloatingKeyboard) {
            floatingKeyboardManager!!.hide(false)
        }
        dictionaryFacilitator.onFinishInput()
        keyboardSwitcher.mainKeyboardView?.closing()
    }

    fun onFinishInputViewInternal(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Log.i(TAG, "onFinishInputView")
        if (voiceInputManager?.isRecording() == true) voiceInputManager!!.stopVoice()
        otpSuggestionManager.stop()
        clipboardHistoryManager.onFinishInputView()
        AudioAndHapticFeedbackManager.getInstance().onFinishInputView()
        cleanupInternalStateForFinishInput()
    }

    private fun cleanupInternalStateForFinishInput() {
        handler.cancelUpdateSuggestionStrip()
        inputLogic.finishInput()
        keyboardActionListener.resetMetaState()
    }

    protected fun deallocateMemory() {
        keyboardSwitcher.deallocateMemory()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        composingSpanStart: Int, composingSpanEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, composingSpanStart, composingSpanEnd)
        voiceInputManager?.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, composingSpanStart, composingSpanEnd)
        
        if (DebugFlags.DEBUG_ENABLED) {
            Log.i(TAG, "onUpdateSelection: oss=$oldSelStart, ose=$oldSelEnd, nss=$newSelStart, nse=$newSelEnd, cs=$composingSpanStart, ce=$composingSpanEnd")
        }
        
        val settingsValues = settings.current
        if ((isInputViewShown || keyboardSwitcher.isShowingStripContainer) &&
            inputLogic.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, composingSpanStart, composingSpanEnd, settingsValues)
        ) {
            val kb = keyboardSwitcher.keyboard
            if (kb != null && kb.mId.isAlphabetShiftedManually &&
                ((oldSelEnd == newSelEnd && oldSelStart != newSelStart) || (oldSelEnd != newSelEnd && oldSelStart == newSelStart))
            ) {
                return
            }
            keyboardSwitcher.requestUpdatingShiftState(currentAutoCapsState, currentRecapitalizeState)
        }
    }

    override fun onExtractedTextClicked() {
        if (settings.current.needsToLookupSuggestions()) return
        super.onExtractedTextClicked()
    }

    override fun onExtractedCursorMovement(dx: Int, dy: Int) {
        if (settings.current.needsToLookupSuggestions()) return
        super.onExtractedCursorMovement(dx, dy)
    }

    override fun hideWindow() {
        Log.i(TAG, "hideWindow")
        if (hasSuggestionStripView() && settings.current.mToolbarMode == ToolbarMode.EXPANDABLE) {
            suggestionStripView!!.setToolbarVisibility(false)
        }
        keyboardSwitcher.onHideWindow()
        if (TRACE) Debug.stopMethodTracing()
        if (isShowingOptionDialog()) {
            optionsDialog!!.dismiss()
            optionsDialog = null
        }
        super.hideWindow()
    }

    override fun requestHideSelf(flags: Int) {
        super.requestHideSelf(flags)
        Log.i(TAG, "requestHideSelf: $flags")
    }

    override fun onDisplayCompletions(applicationSpecifiedCompletions: Array<CompletionInfo>?) {
        if (DebugFlags.DEBUG_ENABLED) {
            Log.i(TAG, "Received completions:")
            applicationSpecifiedCompletions?.forEachIndexed { i, info -> Log.i(TAG, "  #$i: $info") }
        }
        
        if (!settings.current.isApplicationSpecifiedCompletionsOn()) return
        handler.cancelUpdateSuggestionStrip()
        
        if (applicationSpecifiedCompletions == null) {
            setNeutralSuggestionStrip()
            return
        }
        
        val applicationSuggestedWords = SuggestedWords.getFromApplicationSpecifiedCompletions(applicationSpecifiedCompletions)
        val suggestedWords = SuggestedWords(
            applicationSuggestedWords, null, null, false, false, false,
            SuggestedWords.INPUT_STYLE_APPLICATION_SPECIFIED, SuggestedWords.NOT_A_SEQUENCE_NUMBER
        )
        setSuggestedWords(suggestedWords)
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val view = inputView ?: return
        
        if (keyboardSwitcher.isOcrCameraShowing) {
            val inputWidth = view.width
            val inputHeight = view.height
            if (inputWidth > 0 && inputHeight > 0) {
                val wrapperView = keyboardSwitcher.wrapperView
                var ocrHeight = if (wrapperView != null && (wrapperView.isShown || wrapperView.visibility == View.VISIBLE)) wrapperView.height else 0
                if (ocrHeight <= 0) {
                    ocrHeight = ResourceUtils.getOcrCameraHeight((displayContext ?: this).resources, Settings.getValues())
                }
                val visibleTopY = max(0, inputHeight - ocrHeight)
                outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
                outInsets.touchableRegion.set(0, visibleTopY, inputWidth, inputHeight + EXTENDED_TOUCHABLE_REGION_HEIGHT)
                outInsets.contentTopInsets = visibleTopY
                outInsets.visibleTopInsets = visibleTopY
                insetsUpdater?.setInsets(outInsets)
                return
            }
        }
        
        val visibleKeyboardView = keyboardSwitcher.wrapperView ?: return
        val inputHeight = view.height
        val keyboardHeight = if (visibleKeyboardView.isShown) visibleKeyboardView.height else 0
        val stripHeight = if (keyboardSwitcher.isShowingStripContainer) keyboardSwitcher.stripContainer?.height ?: 0 else 0
        val visibleTopY = inputHeight - keyboardHeight - stripHeight
        
        if (hasSuggestionStripView()) suggestionStripView!!.setMoreSuggestionsHeight(visibleTopY)
        
        if (visibleKeyboardView.isShown || keyboardSwitcher.isShowingStripContainer) {
            val touchLeft = 0
            val touchTop = if (keyboardSwitcher.isShowingPopupKeysPanel) 0 else visibleTopY
            val touchRight = view.width
            val touchBottom = inputHeight + EXTENDED_TOUCHABLE_REGION_HEIGHT
            outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
            outInsets.touchableRegion.set(touchLeft, touchTop, touchRight, touchBottom)
        }
        
        outInsets.contentTopInsets = visibleTopY
        outInsets.visibleTopInsets = visibleTopY
        insetsUpdater?.setInsets(outInsets)
    }

    fun startShowingInputView(needsToLoadKeyboard: Boolean) {
        isExecutingStartShowingInputView = true
        showWindow(true)
        isExecutingStartShowingInputView = false
        if (needsToLoadKeyboard) loadKeyboard()
    }

    fun stopShowingInputView() {
        showWindow(false)
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        if (isImeSuppressedByHardwareKeyboard()) return true
        return super.onShowInputRequested(flags, configChange)
    }

    override fun onEvaluateInputViewShown(): Boolean {
        if (isExecutingStartShowingInputView) return true
        val settingsValues = settings.current
        if (settingsValues.mHasHardwareKeyboard && settingsValues.mShowToolbarOnly) return true
        return super.onEvaluateInputViewShown()
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        if (isImeSuppressedByHardwareKeyboard()) return false
        val isFullscreenModeAllowed = Settings.readFullscreenModeAllowed(resources)
        if (super.onEvaluateFullscreenMode() && isFullscreenModeAllowed) {
            val ei = currentInputEditorInfo ?: return false
            val noExtractUi = (ei.imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI) != 0
            val noFullscreen = (ei.imeOptions and EditorInfo.IME_FLAG_NO_FULLSCREEN) != 0
            if (noExtractUi || noFullscreen) return false
            
            val kbView = keyboardSwitcher.visibleKeyboardView
            val stripView = suggestionStripView
            if (kbView == null || stripView == null) return false
            
            val usedHeight = kbView.height + stripView.height
            val availableHeight = resources.displayMetrics.heightPixels
            return usedHeight > availableHeight * 0.6
        }
        return false
    }

    override fun updateFullscreenMode() {
        super.updateFullscreenMode()
        updateSoftInputWindowLayoutParameters(inputView)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        Log.d(TAG, "onCreateInlineSuggestionsRequest called")
        if (Settings.getValues().mSuggestionStripHiddenPerUserSettings) return null
        return InlineAutofillUtils.createInlineSuggestionRequest(displayContext ?: this)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        Log.d(TAG, "onInlineSuggestionsResponse called")
        if (Settings.getValues().mSuggestionStripHiddenPerUserSettings) return false
        val inlineSuggestions = response.inlineSuggestions
        if (inlineSuggestions.isEmpty()) return false
        
        val inlineSuggestionView = InlineAutofillUtils.createView(inlineSuggestions, displayContext ?: this)
        handler.cancelResumeSuggestions()
        suggestionStripView?.setExternalSuggestionView(inlineSuggestionView, true)
        return true
    }

    val currentAutoCapsState: Int get() = inputLogic.getCurrentAutoCapsState(settings.current)
    val currentRecapitalizeState: RecapitalizeMode? get() = inputLogic.getCurrentRecapitalizeState()

    fun getCoordinatesForCurrentKeyboard(codePoints: IntArray): IntArray {
        val keyboard = keyboardSwitcher.keyboard ?: return CoordinateUtils.newCoordinateArray(codePoints.size, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE)
        return keyboard.getCoordinates(codePoints)
    }

    fun displaySettingsDialog() { launchSettings() }

    fun showInputPickerDialog(): Boolean {
        if (isShowingOptionDialog()) return false
        if (richImm.hasMultipleEnabledIMEsOrSubtypes(true)) {
            val token = keyboardSwitcher.mainKeyboardView?.windowToken ?: return false
            optionsDialog = createInputMethodPickerDialog(this, richImm, token)
            optionsDialog?.show()
            return true
        }
        return false
    }

    private fun isShowingOptionDialog(): Boolean = optionsDialog != null && optionsDialog!!.isShowing

    fun switchToNextSubtype() {
        val switchSubtype = settings.current.mLanguageSwitchKeyToOtherSubtypes
        val switchIme = settings.current.mLanguageSwitchKeyToOtherImes
        
        val prefs = DeviceProtectedUtils.getSharedPreferences(this)
        val target = prefs.getString(Settings.PREF_DIRECT_IME_SWITCH_TARGET, Defaults.PREF_DIRECT_IME_SWITCH_TARGET)
        val hasDirectTarget = !target.isNullOrEmpty()
        
        if (switchIme && !switchSubtype) {
            if (hasDirectTarget) { switchToUserIme(); return }
            else if (switchInputMethod()) return
        }
        
        val hasMoreThanOneSubtype = richImm.hasMultipleEnabledSubtypesInThisIme(true)
        if (switchSubtype && !switchIme) {
            if (hasMoreThanOneSubtype) subtypeState.switchSubtype(richImm)
            return
        }
        
        if (hasMoreThanOneSubtype && subtypeState.currentSubtypeHasBeenUsed) {
            subtypeState.switchSubtype(richImm)
            return
        }
        
        if (shouldSwitchToOtherInputMethods()) {
            val nextSubtype = richImm.getNextSubtypeInThisIme(false)
            if (nextSubtype != null) { switchToSubtype(nextSubtype); return }
            else if (hasDirectTarget) { switchToUserIme(); return }
            else if (switchInputMethod()) return
        }
        subtypeState.switchSubtype(richImm)
    }

    fun switchToUserIme() {
        val prefs = DeviceProtectedUtils.getSharedPreferences(this)
        val target = prefs.getString(Settings.PREF_DIRECT_IME_SWITCH_TARGET, Defaults.PREF_DIRECT_IME_SWITCH_TARGET)
        if (target.isNullOrEmpty()) return
        
        val parts = target.split(";")
        if (parts.isEmpty()) return
        val imiId = parts[0]
        if (imiId.isEmpty()) return
        
        var targetImi: android.view.inputmethod.InputMethodInfo? = null
        for (imi in richImm.inputMethodManager.enabledInputMethodList) {
            if (imi.id == imiId) { targetImi = imi; break }
        }
        if (targetImi == null) return
        
        var targetSubtype: android.view.inputmethod.InputMethodSubtype? = null
        if (parts.size > 1 && parts[1].isNotEmpty()) {
            try {
                val subtypeHash = parts[1].toInt()
                for (subtype in richImm.getEnabledInputMethodSubtypes(targetImi, true)) {
                    if (subtype.hashCode() == subtypeHash) { targetSubtype = subtype; break }
                }
            } catch (ignored: NumberFormatException) {}
        }
        
        if (targetImi.id == richImm.inputMethodInfoOfThisIme.id) {
            if (targetSubtype != null) switchToSubtype(targetSubtype)
        } else if (targetSubtype != null) {
            switchInputMethodAndSubtypeCompat(targetImi, targetSubtype)
        } else {
            switchInputMethodCompat(targetImi.id)
        }
    }

    override fun onCodeInput(codePoint: Int, x: Int, y: Int, isKeyRepeat: Boolean) {
        keyboardActionListener.onCodeInput(codePoint, x, y, isKeyRepeat)
    }

    fun onEvent(event: Event) {
        if (event.keyCode == KeyCode.SWITCH_TO_USER_IME) { switchToUserIme(); return }
        if (event.keyCode == KeyCode.VOICE_INPUT) {
            val offlineEnabled = prefs().getBoolean(VoiceConstants.PREF_VOICE_OFFLINE_ENABLED, false)
            if (offlineEnabled) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    if (voiceInputManager != null) {
                        if (voiceInputManager!!.isRecording()) voiceInputManager!!.stopVoice() else voiceInputManager!!.startVoice()
                    }
                } else {
                    Toast.makeText(this, "Microphone permission required for offline voice input. Enable in Settings -> Voice", Toast.LENGTH_LONG).show()
                }
            } else {
                richImm.switchToShortcutIme(this)
            }
            return
        }
        
        val completeInputTransaction = inputLogic.onCodeInput(settings.current, event, keyboardSwitcher.keyboardShiftMode, handler)
        updateStateAfterInputTransaction(completeInputTransaction)
        keyboardSwitcher.onEvent(event, currentAutoCapsState, currentRecapitalizeState)
    }

    fun onTextInput(rawText: String?) {
        if (rawText == null) return
        val event = Event.createSoftwareTextEvent(rawText, KeyCode.MULTIPLE_CODE_POINTS, null)
        val completeInputTransaction = inputLogic.onTextInput(settings.current, event, keyboardSwitcher.keyboardShiftMode, handler)
        updateStateAfterInputTransaction(completeInputTransaction)
        inputLogic.restartSuggestionsOnWordTouchedByCursor(settings.current)
        keyboardSwitcher.onEvent(event, currentAutoCapsState, currentRecapitalizeState)
    }

    fun onImageSelected(imageUri: String) {
        val editorInfo = currentInputEditorInfo ?: return
        val contentUri = if (imageUri.startsWith("content://")) {
            android.net.Uri.parse(imageUri)
        } else {
            val file = java.io.File(imageUri)
            if (!file.exists()) return
            androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        }
        
        try {
            val mimeType = contentResolver.getType(contentUri) ?: "image/png"
            val inputContentInfoCompat = InputContentInfoCompat(
                contentUri,
                android.content.ClipDescription("Clipboard Image", arrayOf(mimeType)),
                null
            )
            val ic = currentInputConnection ?: return
            var flags = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
            }
            val inserted = try {
                InputConnectionCompat.commitContent(ic, editorInfo, inputContentInfoCompat, flags, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to commit content", e); false
            }
            if (!inserted) Toast.makeText(this, R.string.image_pasting_not_supported, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to paste image", e)
        }
    }

    fun onStartBatchInput() {
        if (!JniUtils.sHaveNativeGestureLib) {
            keyboardSwitcher.showToast(getString(R.string.load_gesture_library), true)
            inputLogic.onCancelBatchInput(handler)
            return
        }
        inputLogic.onStartBatchInput(settings.current, keyboardSwitcher, handler)
        gestureConsumer.onGestureStarted(richImm.currentSubtypeLocale, keyboardSwitcher.keyboard)
    }

    fun onUpdateBatchInput(batchPointers: InputPointers) {
        if (!JniUtils.sHaveNativeGestureLib) return
        inputLogic.onUpdateBatchInput(batchPointers)
    }

    fun onEndBatchInput(batchPointers: InputPointers) {
        if (!JniUtils.sHaveNativeGestureLib) return
        inputLogic.onEndBatchInput(batchPointers)
        gestureConsumer.onGestureCompleted(batchPointers)
    }

    fun onCancelBatchInput() {
        inputLogic.onCancelBatchInput(handler)
        gestureConsumer.onGestureCanceled()
    }

    fun onTailBatchInputResultShown(suggestedWords: SuggestedWords) {
        gestureConsumer.onImeSuggestionsProcessed(suggestedWords, inputLogic.getComposingStart(), inputLogic.getComposingLength(), dictionaryFacilitator)
    }

    private fun showGesturePreviewAndSetSuggestions(suggestedWords: SuggestedWords, dismissGestureFloatingPreviewText: Boolean) {
        setSuggestions(suggestedWords)
        keyboardSwitcher.mainKeyboardView?.showGestureFloatingPreviewText(suggestedWords, dismissGestureFloatingPreviewText)
    }

    private fun hasSuggestionStripView(): Boolean = suggestionStripView != null

    private fun setSuggestedWords(suggestedWords: SuggestedWords) {
        val currentSettingsValues = settings.current
        inputLogic.setSuggestedWords(suggestedWords)
        if (!hasSuggestionStripView()) return
        if (!onEvaluateInputViewShown() && !currentSettingsValues.mHasHardwareKeyboard) return
        
        val isEmptyApplicationSpecifiedCompletions = currentSettingsValues.isApplicationSpecifiedCompletionsOn() && suggestedWords.isEmpty
        val noSuggestionsFromDictionaries = suggestedWords.isEmpty || suggestedWords.isPunctuationSuggestions || isEmptyApplicationSpecifiedCompletions
        
        if (currentSettingsValues.isSuggestionsEnabledPerUserSettings() ||
            currentSettingsValues.isApplicationSpecifiedCompletionsOn() ||
            noSuggestionsFromDictionaries
        ) {
            suggestionStripView!!.setSuggestions(suggestedWords, richImm.currentSubtype.isRtlSubtype)
            if (currentSettingsValues.mAutoHideToolbar && !noSuggestionsFromDictionaries) {
                suggestionStripView!!.foldToolbar(true)
            }
        }
    }

    override fun setSuggestions(suggestedWords: SuggestedWords) {
        if (tryShowMathSuggestion()) return
        if (suggestedWords.isEmpty) {
            if (keyboardSwitcher.isHandwritingShowing) {
                setSuggestedWords(suggestedWords)
            } else if (suggestedWords.mInputStyle != SuggestedWords.INPUT_STYLE_UPDATE_BATCH) {
                setNeutralSuggestionStrip()
            }
        } else {
            setSuggestedWords(suggestedWords)
        }
        AccessibilityUtils.instance.setAutoCorrection(suggestedWords)
    }

    override fun showSuggestionStrip() {
        if (hasSuggestionStripView()) suggestionStripView!!.setToolbarVisibility(false)
    }

    override fun pickSuggestionManually(suggestionInfo: SuggestedWordInfo?) {
        if (suggestionInfo == null) return
        val completeInputTransaction = inputLogic.onPickSuggestionManually(settings.current, suggestionInfo, keyboardSwitcher.keyboardShiftMode, handler)
        updateStateAfterInputTransaction(completeInputTransaction)
        if (keyboardSwitcher.isHandwritingShowing) keyboardSwitcher.clearHandwritingCanvas()
        
        if (suggestionInfo.isEmoji || (suggestionInfo.mSourceDict != null && helium314.keyboard.latin.dictionary.Dictionary.TYPE_EMOJI == suggestionInfo.mSourceDict.mDictType)) {
            keyboardSwitcher.emojiPalettesView?.addRecentKey(suggestionInfo.mWord)
        }
    }

    fun tryShowOtpSuggestion(): Boolean {
        if (!hasSuggestionStripView()) return false
        val otpView = otpSuggestionManager.getOtpSuggestionView(suggestionStripView!!)
        if (otpView != null) {
            suggestionStripView!!.setExternalSuggestionView(otpView, false)
            return true
        }
        return false
    }

    fun tryShowMathSuggestion(): Boolean {
        if (!hasSuggestionStripView()) return false
        val mathView = mathSuggestionManager.getMathSuggestionView(suggestionStripView!!)
        if (mathView != null) {
            suggestionStripView!!.setExternalSuggestionView(mathView, false)
            return true
        }
        return false
    }

    fun tryShowClipboardSuggestion(): Boolean {
        val clipboardView = clipboardHistoryManager.getClipboardSuggestionView(currentInputEditorInfo, suggestionStripView)
        if (hasSuggestionStripView()) {
            if (clipboardView != null) {
                suggestionStripView!!.setExternalSuggestionView(clipboardView, false)
                return true
            } else {
                suggestionStripView!!.setExternalSuggestionView(null, false)
            }
        }
        return false
    }

    override fun setNeutralSuggestionStrip() {
        if (keyboardSwitcher.isHandwritingShowing) return
        val currentSettings = settings.current
        if (tryShowOtpSuggestion() || tryShowMathSuggestion() || tryShowClipboardSuggestion()) {
            if (hasSuggestionStripView() && currentSettings.mAutoHideToolbar) suggestionStripView!!.setToolbarVisibility(false)
            return
        }
        
        val ngramContext = inputLogic.getNgramContextFromNthPreviousWordForSuggestion(currentSettings.mSpacingAndPunctuations, 1)
        val isFirstWord = ngramContext.isBeginningOfSentenceContext
        val predictionEnabled = if (isFirstWord) currentSettings.mFirstWordPredictionEnabled else currentSettings.mBigramPredictionEnabled
        
        if (predictionEnabled) {
            inputLogic.getSuggestedWords(SuggestedWords.INPUT_STYLE_PREDICTION, 0, object : Suggest.OnGetSuggestedWordsCallback {
                override fun onGetSuggestedWords(suggestedWords: SuggestedWords?) {
                    if (suggestedWords != null && !suggestedWords.isEmpty) {
                        setSuggestedWords(suggestedWords)
                        if (hasSuggestionStripView()) {
                            if (currentSettings.mAutoShowToolbarOnSelect && inputLogic.connection.hasSelection()) {
                                suggestionStripView!!.setToolbarVisibility(true)
                            } else if (currentSettings.mAutoShowToolbarOnSelect) {
                                suggestionStripView!!.setToolbarVisibility(suggestionStripView!!.isToolbarManuallyOpen)
                            }
                        }
                    } else {
                        setNeutralPunctuationSuggestionStrip(currentSettings)
                    }
                }
            })
        } else {
            setNeutralPunctuationSuggestionStrip(currentSettings)
        }
    }

    private fun setNeutralPunctuationSuggestionStrip(currentSettings: SettingsValues) {
        val neutralSuggestions = if (currentSettings.mSuggestPunctuation) currentSettings.mSpacingAndPunctuations.mSuggestPuncList else SuggestedWords.getEmptyInstance()
        setSuggestedWords(neutralSuggestions)
        if (hasSuggestionStripView()) {
            if (currentSettings.mAutoShowToolbarOnSelect && inputLogic.connection.hasSelection()) {
                suggestionStripView!!.setToolbarVisibility(true)
            } else if (currentSettings.mAutoShowToolbarOnSelect) {
                suggestionStripView!!.setToolbarVisibility(suggestionStripView!!.isToolbarManuallyOpen)
            }
        }
    }

    private fun shouldRequestInitialPredictions(settingsValues: SettingsValues): Boolean {
        if (!dictionaryFacilitator.hasAtLeastOneInitializedMainDictionary()) return false
        if (!settingsValues.needsToLookupSuggestions()) return false
        
        val firstWordEnabled = settingsValues.mFirstWordPredictionEnabled
        val bigramEnabled = settingsValues.mBigramPredictionEnabled
        if (!firstWordEnabled && !bigramEnabled) return false
        if (firstWordEnabled && bigramEnabled) return true
        
        val ngramContext = inputLogic.getNgramContextFromNthPreviousWordForSuggestion(settingsValues.mSpacingAndPunctuations, 1)
        val firstWordContext = ngramContext.isBeginningOfSentenceContext
        return if (firstWordContext) firstWordEnabled else bigramEnabled
    }

    fun showTranslateLanguageSelector() {
        suggestionStripView?.showTranslateLanguageSelector()
    }

    override fun removeSuggestion(word: String?) {
        if (word == null) return
        dictionaryFacilitator.removeWord(word)
        inputLogic.suggest.clearNextWordSuggestionsCache()
    }

    fun reloadBlacklist() {
        dictionaryFacilitator.reloadBlacklist()
        inputLogic.suggest.clearNextWordSuggestionsCache()
    }

    fun getDictionaryFacilitator(): DictionaryFacilitator = dictionaryFacilitator

    override fun removeExternalSuggestions() {
        setNeutralSuggestionStrip()
        handler.postResumeSuggestions(false)
    }

    private fun loadKeyboard() {
        handler.postReopenDictionaries()
        loadSettings()
        if (keyboardSwitcher.mainKeyboardView != null) {
            keyboardSwitcher.reloadMainKeyboard()
        }
    }

    private fun updateStateAfterInputTransaction(inputTransaction: InputTransaction) {
        when (inputTransaction.requiredShiftUpdate) {
            InputTransaction.SHIFT_UPDATE_LATER -> handler.postUpdateShiftState()
            InputTransaction.SHIFT_UPDATE_NOW -> keyboardSwitcher.requestUpdatingShiftState(currentAutoCapsState, currentRecapitalizeState)
            else -> {}
        }
        
        if (inputTransaction.requiresUpdateSuggestions()) {
            val inputStyle = when {
                inputTransaction.event.isSuggestionStripPress -> SuggestedWords.INPUT_STYLE_NONE
                inputTransaction.event.isGesture -> SuggestedWords.INPUT_STYLE_TAIL_BATCH
                else -> SuggestedWords.INPUT_STYLE_TYPING
            }
            handler.postUpdateSuggestionStrip(inputStyle)
        }
        
        if (inputTransaction.didAffectContents()) {
            subtypeState.setCurrentSubtypeHasBeenUsed()
        }
    }

    fun hapticAndAudioFeedback(code: Int, repeatCount: Int, hapticEvent: HapticEvent) {
        val keyboardView = keyboardSwitcher.mainKeyboardView
        if (keyboardView != null && keyboardView.isInDraggingFinger()) return
        
        if (repeatCount > 0) {
            when (code) {
                KeyCode.DELETE, KeyCode.ARROW_LEFT, KeyCode.ARROW_UP, KeyCode.WORD_LEFT, KeyCode.PAGE_UP -> if (!inputLogic.connection.canDeleteCharacters()) return
                KeyCode.ARROW_RIGHT, KeyCode.ARROW_DOWN, KeyCode.WORD_RIGHT, KeyCode.PAGE_DOWN -> if (!inputLogic.connection.hasTextAfterCursor()) return
            }
            if (repeatCount % PERIOD_FOR_AUDIO_AND_HAPTIC_FEEDBACK_IN_KEY_REPEAT == 0) return
        }
        
        var keyXRatio = 0.5f
        if (keyboardView != null) {
            val keyboard = keyboardView.keyboard
            if (keyboard != null) {
                val key = keyboard.getKey(code)
                if (key != null && keyboard.mOccupiedWidth > 0) {
                    keyXRatio = (key.x + key.width / 2f) / keyboard.mOccupiedWidth.toFloat()
                    keyXRatio = max(0f, minOf(1f, keyXRatio))
                }
            }
        }
        
        val feedbackManager = AudioAndHapticFeedbackManager.getInstance()
        feedbackManager.performHapticFeedback(keyboardView, hapticEvent)
        feedbackManager.performAudioFeedback(code, hapticEvent, keyXRatio)
    }

    override fun onKeyDown(keyCode: Int, keyEvent: KeyEvent): Boolean {
        if (keyboardActionListener.onKeyDown(keyCode, keyEvent)) return true
        return super.onKeyDown(keyCode, keyEvent)
    }

    override fun onKeyUp(keyCode: Int, keyEvent: KeyEvent): Boolean {
        if (keyboardActionListener.onKeyUp(keyCode, keyEvent)) return true
        return super.onKeyUp(keyCode, keyEvent)
    }

    private val ringerModeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (AudioManager.RINGER_MODE_CHANGED_ACTION == intent.action) {
                val dnd = try {
                    android.provider.Settings.Global.getInt(context.contentResolver, "zen_mode") != 0
                } catch (e: android.provider.Settings.SettingNotFoundException) {
                    Log.w(TAG, "zen_mode setting not found, assuming disabled"); false
                }
                Log.i(TAG, "ringer mode changed, zen_mode on: $dnd")
                AudioAndHapticFeedbackManager.getInstance().onRingerModeChanged(dnd)
            }
        }
    }

    fun launchSettings() {
        inputLogic.commitTyped(settings.current, LastComposedWord.NOT_A_SEPARATOR)
        requestHideSelf(0)
        keyboardSwitcher.mainKeyboardView?.closing()
        
        val intent = Intent(this, SettingsActivity2::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_ime", true)
        }
        startActivity(intent)
    }

    fun dumpDictionaryForDebug(dictName: String) {
        if (!dictionaryFacilitator.isActive()) resetDictionaryFacilitatorIfNecessary()
        dictionaryFacilitator.dumpDictionaryForDebug(dictName)
    }

    fun debugDumpStateAndCrashWithException(context: String) {
        val settingsValues = settings.current
        val s = settingsValues.toString() + "\nAttributes : " + settingsValues.mInputAttributes + "\nContext : $context"
        throw RuntimeException(s)
    }

    override fun dump(fd: FileDescriptor, fout: PrintWriter, args: Array<String>) {
        super.dump(fd, fout, args)
        val p = PrintWriterPrinter(fout)
        p.println("LatinIME state :")
        p.println("  VersionCode = ${BuildConfig.VERSION_CODE}")
        p.println("  VersionName = ${BuildConfig.VERSION_NAME}")
        val keyboard = keyboardSwitcher.keyboard
        val keyboardMode = keyboard?.mId?.mMode ?: -1
        p.println("  Keyboard mode = $keyboardMode")
        val settingsValues = settings.current
        p.println(settingsValues.dump())
        p.println(dictionaryFacilitator.dump(this))
    }

    @Suppress("DEPRECATION")
    private fun setNavigationBarColor() {
        val settingsValues = settings.current
        if (!settingsValues.mCustomNavBarColor) return
        val window = window?.window ?: return
        
        if (!originalNavBarSaved) {
            originalNavBarColor = window.navigationBarColor
            originalNavBarFlags = window.decorView.systemUiVisibility
            originalNavBarSaved = true
        }
        
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        val color = settingsValues.mColors.get(ColorType.NAVIGATION_BAR)
        window.navigationBarColor = color
        
        val view = window.decorView
        val controller = WindowCompat.getInsetsController(window, view)
        if (controller != null) {
            controller.isAppearanceLightNavigationBars = isBrightColor(color)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var flags = view.systemUiVisibility
            flags = if (isBrightColor(color)) flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            view.systemUiVisibility = flags
        }
    }

    @Suppress("DEPRECATION")
    private fun clearNavigationBarColor() {
        val settingsValues = settings.current
        if (!settingsValues.mCustomNavBarColor) return
        val window = window?.window ?: return
        window.navigationBarColor = originalNavBarColor
        
        val view = window.decorView
        val controller = WindowCompat.getInsetsController(window, view)
        if (controller != null) {
            controller.isAppearanceLightNavigationBars = (originalNavBarFlags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) != 0
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            view.systemUiVisibility = originalNavBarFlags
        }
        originalNavBarSaved = false
    }

    private fun workaroundForHuaweiStatusBarIssue() {
        val window = window?.window ?: return
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S && Build.MANUFACTURER == "HUAWEI") {
            window.statusBarColor = Color.TRANSPARENT
        }
    }

    @SuppressLint("SwitchIntDef")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND || level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            keyboardSwitcher.trimMemory()
            deallocateMemory()
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            KeyboardLayoutSet.onSystemLocaleChanged()
            keyboardSwitcher.trimMemory()
        }
    }

    class UIHandler(owner: LatinIME) : LeakGuardHandlerWrapper<LatinIME>(owner) {
        val ownerInstance: LatinIME?
            get() = getOwnerInstance()

        private var delayInMillisecondsToUpdateSuggestions = 0
        private var delayInMillisecondsToUpdateShiftState = 0

        fun onCreate() {
            val latinIme = ownerInstance ?: return
            val res = latinIme.resources
            delayInMillisecondsToUpdateSuggestions = res.getInteger(R.integer.config_delay_in_milliseconds_to_update_suggestions)
            delayInMillisecondsToUpdateShiftState = res.getInteger(R.integer.config_delay_in_milliseconds_to_update_shift_state)
        }

        override fun handleMessage(msg: Message) {
            val latinIme = ownerInstance ?: return
            when (msg.what) {
                MSG_UPDATE_SUGGESTION_STRIP -> {
                    cancelUpdateSuggestionStrip()
                    latinIme.inputLogic.performUpdateSuggestionStripSync(latinIme.settings.current, msg.arg1)
                }
                MSG_UPDATE_SHIFT_STATE -> latinIme.keyboardSwitcher.requestUpdatingShiftState(latinIme.currentAutoCapsState, latinIme.currentRecapitalizeState)
                MSG_SHOW_GESTURE_PREVIEW_AND_SET_SUGGESTIONS -> {
                    if (msg.arg1 == ARG1_NOT_GESTURE_INPUT) {
                        latinIme.setSuggestions(msg.obj as SuggestedWords)
                    } else {
                        latinIme.showGesturePreviewAndSetSuggestions(msg.obj as SuggestedWords, msg.arg1 == ARG1_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT)
                    }
                }
                MSG_RESUME_SUGGESTIONS -> latinIme.inputLogic.restartSuggestionsOnWordTouchedByCursor(latinIme.settings.current)
                MSG_REOPEN_DICTIONARIES -> {
                    postWaitForDictionaryLoad()
                    latinIme.resetDictionaryFacilitatorIfNecessary()
                }
                MSG_UPDATE_TAIL_BATCH_INPUT_COMPLETED -> {
                    val suggestedWords = msg.obj as SuggestedWords
                    latinIme.inputLogic.onUpdateTailBatchInputCompleted(latinIme.settings.current, suggestedWords, latinIme.keyboardSwitcher)
                    latinIme.onTailBatchInputResultShown(suggestedWords)
                }
                MSG_RESET_CACHES -> {
                    if (latinIme.inputLogic.retryResetCachesAndReturnSuccess(msg.arg1 == ARG1_TRUE, msg.arg2, this)) {
                        latinIme.keyboardSwitcher.reloadMainKeyboard()
                    }
                }
                MSG_WAIT_FOR_DICTIONARY_LOAD -> Log.i(TAG, "Timeout waiting for dictionary load")
                MSG_DEALLOCATE_MEMORY -> latinIme.deallocateMemory()
                MSG_SWITCH_LANGUAGE_AUTOMATICALLY -> latinIme.switchToSubtype(msg.obj as InputMethodSubtype)
            }
        }

        fun postUpdateSuggestionStrip(inputStyle: Int) {
            val latinIme = ownerInstance
            if (latinIme != null && latinIme.keyboardSwitcher.isHandwritingShowing) return
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SUGGESTION_STRIP, inputStyle, 0), delayInMillisecondsToUpdateSuggestions.toLong())
        }

        fun postReopenDictionaries() { sendMessage(obtainMessage(MSG_REOPEN_DICTIONARIES)) }

        fun postResumeSuggestions(shouldDelay: Boolean) {
            val latinIme = ownerInstance ?: return
            if (latinIme.keyboardSwitcher.isHandwritingShowing) return
            if (!latinIme.settings.current.needsToLookupSuggestions()) return
            removeMessages(MSG_RESUME_SUGGESTIONS)
            if (shouldDelay) sendMessageDelayed(obtainMessage(MSG_RESUME_SUGGESTIONS), delayInMillisecondsToUpdateSuggestions.toLong())
            else sendMessage(obtainMessage(MSG_RESUME_SUGGESTIONS))
        }

        fun postResetCaches(tryResumeSuggestions: Boolean, remainingTries: Int) {
            removeMessages(MSG_RESET_CACHES)
            sendMessage(obtainMessage(MSG_RESET_CACHES, if (tryResumeSuggestions) 1 else 0, remainingTries, null))
        }

        fun postWaitForDictionaryLoad() { sendMessageDelayed(obtainMessage(MSG_WAIT_FOR_DICTIONARY_LOAD), DELAY_WAIT_FOR_DICTIONARY_LOAD_MILLIS) }
        fun cancelWaitForDictionaryLoad() { removeMessages(MSG_WAIT_FOR_DICTIONARY_LOAD) }
        fun hasPendingWaitForDictionaryLoad(): Boolean = hasMessages(MSG_WAIT_FOR_DICTIONARY_LOAD)
        fun cancelUpdateSuggestionStrip() { removeMessages(MSG_UPDATE_SUGGESTION_STRIP) }
        fun cancelResumeSuggestions() { removeMessages(MSG_RESUME_SUGGESTIONS) }
        fun hasPendingUpdateSuggestions(): Boolean = hasMessages(MSG_UPDATE_SUGGESTION_STRIP)
        fun hasPendingResumeSuggestions(): Boolean = hasMessages(MSG_RESUME_SUGGESTIONS)
        fun hasPendingReopenDictionaries(): Boolean = hasMessages(MSG_REOPEN_DICTIONARIES)

        fun postUpdateShiftState() {
            removeMessages(MSG_UPDATE_SHIFT_STATE)
            sendMessageDelayed(obtainMessage(MSG_UPDATE_SHIFT_STATE), delayInMillisecondsToUpdateShiftState.toLong())
        }

        fun postDeallocateMemory() { sendMessageDelayed(obtainMessage(MSG_DEALLOCATE_MEMORY), DELAY_DEALLOCATE_MEMORY_MILLIS) }
        fun cancelDeallocateMemory() { removeMessages(MSG_DEALLOCATE_MEMORY) }
        fun hasPendingDeallocateMemory(): Boolean = hasMessages(MSG_DEALLOCATE_MEMORY)

        fun removeAllMessages() {
            for (i in 0..MSG_LAST) removeMessages(i)
        }

        fun showGesturePreviewAndSetSuggestions(suggestedWords: SuggestedWords, dismissGestureFloatingPreviewText: Boolean) {
            removeMessages(MSG_SHOW_GESTURE_PREVIEW_AND_SET_SUGGESTIONS)
            val arg1 = if (dismissGestureFloatingPreviewText) ARG1_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT else ARG1_SHOW_GESTURE_FLOATING_PREVIEW_TEXT
            obtainMessage(MSG_SHOW_GESTURE_PREVIEW_AND_SET_SUGGESTIONS, arg1, ARG2_UNUSED, suggestedWords).sendToTarget()
        }

        fun setSuggestions(suggestedWords: SuggestedWords) {
            removeMessages(MSG_SHOW_GESTURE_PREVIEW_AND_SET_SUGGESTIONS)
            obtainMessage(MSG_SHOW_GESTURE_PREVIEW_AND_SET_SUGGESTIONS, ARG1_NOT_GESTURE_INPUT, ARG2_UNUSED, suggestedWords).sendToTarget()
        }

        fun showTailBatchInputResult(suggestedWords: SuggestedWords) {
            obtainMessage(MSG_UPDATE_TAIL_BATCH_INPUT_COMPLETED, suggestedWords).sendToTarget()
        }

        fun postSwitchLanguage(subtype: InputMethodSubtype) {
            obtainMessage(MSG_SWITCH_LANGUAGE_AUTOMATICALLY, subtype).sendToTarget()
        }

        private var isOrientationChanging = false
        private var pendingSuccessiveImsCallback = false
        private var hasPendingStartInput = false
        private var hasPendingFinishInputView = false
        private var hasPendingFinishInput = false
        private var appliedEditorInfo: EditorInfo? = null

        fun startOrientationChanging() {
            removeMessages(MSG_PENDING_IMS_CALLBACK)
            resetPendingImsCallback()
            isOrientationChanging = true
            val latinIme = ownerInstance
            if (latinIme?.isInputViewShown == true) latinIme.keyboardSwitcher.saveKeyboardState()
        }

        private fun resetPendingImsCallback() {
            hasPendingFinishInputView = false
            hasPendingFinishInput = false
            hasPendingStartInput = false
        }

        private fun executePendingImsCallback(latinIme: LatinIME, editorInfo: EditorInfo?, restarting: Boolean) {
            if (hasPendingFinishInputView) latinIme.onFinishInputViewInternal(hasPendingFinishInput)
            if (hasPendingFinishInput) latinIme.onFinishInputInternal()
            if (hasPendingStartInput) latinIme.onStartInputInternal(editorInfo, restarting)
            resetPendingImsCallback()
        }

        fun onStartInput(editorInfo: EditorInfo, restarting: Boolean) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                hasPendingStartInput = true
            } else {
                if (isOrientationChanging && restarting) {
                    isOrientationChanging = false
                    pendingSuccessiveImsCallback = true
                }
                val latinIme = ownerInstance
                if (latinIme != null) {
                    executePendingImsCallback(latinIme, editorInfo, restarting)
                    latinIme.onStartInputInternal(editorInfo, restarting)
                }
            }
        }

        fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK) && KeyboardId.equivalentEditorInfoForKeyboard(editorInfo, appliedEditorInfo)) {
                resetPendingImsCallback()
            } else {
                if (pendingSuccessiveImsCallback) {
                    pendingSuccessiveImsCallback = false
                    resetPendingImsCallback()
                    sendMessageDelayed(obtainMessage(MSG_PENDING_IMS_CALLBACK), PENDING_IMS_CALLBACK_DURATION_MILLIS.toLong())
                }
                val latinIme = ownerInstance
                if (latinIme != null) {
                    executePendingImsCallback(latinIme, editorInfo, restarting)
                    latinIme.onStartInputViewInternal(editorInfo, restarting)
                    appliedEditorInfo = editorInfo
                }
                cancelDeallocateMemory()
            }
        }

        fun onFinishInputView(finishingInput: Boolean) {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                hasPendingFinishInputView = true
            } else {
                val latinIme = ownerInstance
                if (latinIme != null) {
                    latinIme.onFinishInputViewInternal(finishingInput)
                    appliedEditorInfo = null
                }
                if (!hasPendingDeallocateMemory()) postDeallocateMemory()
            }
        }

        fun onFinishInput() {
            if (hasMessages(MSG_PENDING_IMS_CALLBACK)) {
                hasPendingFinishInput = true
            } else {
                val latinIme = ownerInstance
                if (latinIme != null) {
                    executePendingImsCallback(latinIme, null, false)
                    latinIme.onFinishInputInternal()
                }
            }
        }

        companion object {
            private const val MSG_UPDATE_SHIFT_STATE = 0
            private const val MSG_PENDING_IMS_CALLBACK = 1
            private const val MSG_UPDATE_SUGGESTION_STRIP = 2
            private const val MSG_SHOW_GESTURE_PREVIEW_AND_SET_SUGGESTIONS = 3
            private const val MSG_RESUME_SUGGESTIONS = 4
            private const val MSG_REOPEN_DICTIONARIES = 5
            private const val MSG_UPDATE_TAIL_BATCH_INPUT_COMPLETED = 6
            private const val MSG_RESET_CACHES = 7
            private const val MSG_WAIT_FOR_DICTIONARY_LOAD = 8
            private const val MSG_DEALLOCATE_MEMORY = 9
            private const val MSG_SWITCH_LANGUAGE_AUTOMATICALLY = 10
            private const val MSG_LAST = MSG_SWITCH_LANGUAGE_AUTOMATICALLY

            private const val ARG1_NOT_GESTURE_INPUT = 0
            private const val ARG1_DISMISS_GESTURE_FLOATING_PREVIEW_TEXT = 1
            private const val ARG1_SHOW_GESTURE_FLOATING_PREVIEW_TEXT = 2
            private const val ARG2_UNUSED = 0
            private const val ARG1_TRUE = 1
        }
    }

    private class RestartAfterDeviceUnlockReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Intent.ACTION_USER_UNLOCKED == intent.action) {
                val myPid = Process.myPid()
                Log.i(TAG, "Killing my process: pid=$myPid")
                Process.killProcess(myPid)
            } else {
                Log.e(TAG, "Unexpected intent $intent")
            }
        }
    }

    companion object {
        val TAG = LatinIME::class.java.simpleName
        private const val TRACE = false
        private const val EXTENDED_TOUCHABLE_REGION_HEIGHT = 100
        private const val PERIOD_FOR_AUDIO_AND_HAPTIC_FEEDBACK_IN_KEY_REPEAT = 2
        private const val PENDING_IMS_CALLBACK_DURATION_MILLIS = 800
        val DELAY_WAIT_FOR_DICTIONARY_LOAD_MILLIS = TimeUnit.SECONDS.toMillis(2)
        val DELAY_DEALLOCATE_MEMORY_MILLIS = TimeUnit.SECONDS.toMillis(10)
        private const val SCHEME_PACKAGE = "package"
        
        var sSettingsDirty = true
        
        @Volatile
        private var sInstance: LatinIME? = null

        val settings: Settings = Settings.getInstance()

        init {
            JniUtils.loadNativeLibrary()
        }

        @JvmStatic
        fun getInstance(): LatinIME? = sInstance
    }
}
