/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.settings

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.core.util.TypedValueCompat
import helium314.keyboard.compat.locale
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.keyboard_parser.POPUP_KEYS_NORMAL
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.permissions.PermissionsUtil
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.getHasLocalizedNumberRow
import helium314.keyboard.latin.utils.getMoreKeys
import helium314.keyboard.latin.utils.getPopupKeyLabelSources
import helium314.keyboard.latin.utils.getPopupKeyTypes
import helium314.keyboard.latin.utils.getSecondaryLocales
import java.util.Locale

/**
 * When you call the constructor of this class, you may want to change the current system locale by
 * using [helium314.keyboard.latin.utils.RunInLocaleKt].
 */
// Non-final for testing via mock library.
open class SettingsValues(
    context: Context,
    prefs: SharedPreferences,
    res: Resources,
    @JvmField val mInputAttributes: InputAttributes,
    currentKeyboardScript: String
) {
    // From resources:
    @JvmField val mSpacingAndPunctuations: SpacingAndPunctuations
    @JvmField val mDoubleSpacePeriodTimeout: Long
    // From configuration:
    @JvmField val mLocale: Locale
    @JvmField val mCurrentKeyboardScript: String
    @JvmField val mHasHardwareKeyboard: Boolean
    @JvmField val mShowToolbarOnly: Boolean
    @JvmField val mPhysicalKeyboardSuggestionShortcuts: String
    @JvmField val mDisplayOrientation: Int
    @JvmField val mScreenProfile: helium314.keyboard.latin.utils.ScreenProfile
    // From preferences
    @JvmField val mAutoCap: Boolean
    @JvmField val mVibrateOn: Boolean
    @JvmField val mVibrateInDndMode: Boolean
    @JvmField val mSoundOn: Boolean
    @JvmField val mSuggestEmojis: Boolean
    @JvmField val mInlineEmojiSearch: Boolean
    @JvmField val mShowEmojiDescriptions: Boolean
    @JvmField val mKeyPreviewPopupOn: Boolean
    @JvmField val mShowsVoiceInputKey: Boolean
    @JvmField val mLanguageSwitchKeyToOtherImes: Boolean
    @JvmField val mLanguageSwitchKeyToOtherSubtypes: Boolean
    private val mShowsLanguageSwitchKey: Boolean
    @JvmField val mShowsNumberRow: Boolean
    @JvmField val mShowsNumberRowInSymbols: Boolean
    @JvmField val mCompactNumberRowInSymbols: Boolean
    @JvmField val mLocalizedNumberRow: Boolean
    @JvmField val mShowNumberRowHints: Boolean
    @JvmField val mShowsHints: Boolean
    @JvmField val mShowsPopupHints: Boolean
    @JvmField val mShowTldPopupKeys: Boolean
    @JvmField val mSpaceForLangChange: Boolean
    @JvmField val mShowsEmojiKey: Boolean
    @JvmField val mVarToolbarDirection: Boolean
    @JvmField val mUsePersonalizedDicts: Boolean
    @JvmField val mUseDoubleSpacePeriod: Boolean
    @JvmField val mBlockPotentiallyOffensive: Boolean
    @JvmField val mSpaceSwipeHorizontal: Int
    @JvmField val mSpaceSwipeVertical: Int
    @JvmField val mLanguageSwipeDistance: Int
    @JvmField val mTouchpadSensitivity: Int
    @JvmField val mTouchpadFullscreen: Boolean
    @JvmField val mForceAutoCaps: Boolean
    @JvmField val mDeleteSwipeEnabled: Boolean
    @JvmField val mAutospaceAfterPunctuation: Boolean
    @JvmField val mAutospaceAfterEmoji: Boolean
    @JvmField val mAutospaceAfterSuggestion: Boolean
    @JvmField val mImmediateAutoSpace: Boolean
    @JvmField val mAutospaceAfterGestureTyping: Boolean
    @JvmField val mAutospaceBeforeGestureTyping: Boolean
    @JvmField val mShiftRemovesAutospace: Boolean
    @JvmField val mPreserveSpaceBeforePunctuation: Boolean
    @JvmField val mClipboardHistoryEnabled: Boolean
    @JvmField val mClipboardHistoryRetentionTime: Long
    @JvmField val mClipboardHistoryPinnedFirst: Boolean
    @JvmField val mClipboardFoldPinned: Boolean
    @JvmField val mOneHandedModeEnabled: Boolean
    @JvmField val mOneHandedModeGravity: Int
    @JvmField val mOneHandedModeScale: Float
    @JvmField val mNarrowKeyGaps: Boolean
    @JvmField val mNarrowKeyGapsLevel: Int
    @JvmField val mThemeKeyBorders: Boolean
    @JvmField val mShowMorePopupKeys: String
    @JvmField val mPopupKeyTypes: List<String>
    @JvmField val mPopupKeyLabelSources: List<String>
    @JvmField val mSecondaryLocales: List<Locale>
    @JvmField val mBigramPredictionEnabled: Boolean
    @JvmField val mFirstWordPredictionEnabled: Boolean
    @JvmField val mSuggestPunctuation: Boolean
    @JvmField val mCenterSuggestionTextToEnter: Boolean
    @JvmField val mGestureMethod: String
    @JvmField val mGestureInputEnabled: Boolean
    @JvmField val mGestureTrailEnabled: Boolean
    @JvmField val mGestureFloatingPreviewTextEnabled: Boolean
    @JvmField val mGestureFloatingPreviewDynamicEnabled: Boolean
    @JvmField val mGestureFastTypingCooldown: Int
    @JvmField val mGestureTrailFadeoutDuration: Int
    @JvmField val mSlidingKeyInputPreviewEnabled: Boolean
    @JvmField val mKeyLongpressTimeout: Int
    @JvmField val mEnableEmojiAltPhysicalKey: Boolean
    @JvmField val mIsSplitKeyboardEnabled: Boolean
    @JvmField val mSplitKeyboardSpacerRelativeWidth: Float
    @JvmField val mQuickPinToolbarKeys: Boolean
    @JvmField val mScreenMetrics: Int
    @JvmField val mAddToPersonalDictionary: Boolean
    @JvmField val mAddToPersonalDictThreshold: Int
    @JvmField val mUseContactsDictionary: Boolean
    @JvmField val mUseAppsDictionary: Boolean
    @JvmField val mEnableSpellCheckerService: Boolean
    @JvmField val mEnableContactsObserver: Boolean
    @JvmField val mEnableClipboardListener: Boolean
    @JvmField val mEnableSmsOtpReceiver: Boolean
    @JvmField val mEnableAppSyncListener: Boolean
    @JvmField val mCustomNavBarColor: Boolean
    @JvmField val mKeyboardHeightScale: Float
    @JvmField val mUrlDetectionEnabled: Boolean
    @JvmField val mBottomPaddingScale: Float
    @JvmField val mSidePaddingScale: Float
    @JvmField val mToolbarMode: ToolbarMode
    @JvmField val mToolbarHidingGlobal: Boolean
    @JvmField val mSplitToolbar: Boolean
    @JvmField val mAutoSpanToolbarKeys: Boolean
    @JvmField val mToolbarKeysAlignment: String
    @JvmField val mClipboardKeysAlignment: String
    @JvmField val mShowDownloadButtonInToolbar: Boolean
    @JvmField val mAutoShowToolbar: Boolean
    @JvmField val mAutoShowToolbarOnSelect: Boolean
    @JvmField val mAutoHideToolbar: Boolean
    @JvmField val mToolbarSwipeDownDismiss: Boolean
    @JvmField val mAutoHidePinnedKeys: Boolean
    @JvmField val mRememberToolbarState: Boolean
    @JvmField val mAlphaAfterEmojiInEmojiView: Boolean
    @JvmField val mAlphaAfterClipHistoryEntry: Boolean
    @JvmField val mAlphaAfterSymbolAndSpace: Boolean
    @JvmField val mAlphaAfterNumpadAndSpace: Boolean
    @JvmField val mRemoveRedundantPopups: Boolean
    @JvmField val mSpaceBarText: String
    @JvmField val mFontSizeMultiplier: Float
    @JvmField val mFontSizeMultiplierEmoji: Float
    @JvmField val mEmojiKeyFit: Boolean

    // Deduced settings
    @JvmField val mSuggestionStripHiddenPerUserSettings: Boolean
    @JvmField val mSecondaryStripVisible: Boolean
    @JvmField val mKeypressVibrationDuration: Int
    @JvmField val mKeypressVibrationAmplitude: Int
    @JvmField val mKeypressSoundVolume: Float
    @JvmField val mKeypressSoundStyle: String
    @JvmField val mSoundPitchScale: Float
    @JvmField val mSoundRandomPitch: Boolean
    @JvmField val mSoundStereoPan: Boolean
    @JvmField val mSoundDynamicVelocity: Boolean
    @JvmField val mSoundMuteInSilent: Boolean
    @JvmField val mSoundMuteInDnd: Boolean
    @JvmField val mSoundVolSpace: Float
    @JvmField val mSoundVolDelete: Float
    @JvmField val mSoundVolEnter: Float
    @JvmField val mSoundVolModifiers: Float
    @JvmField val mAutoCorrectionEnabledPerUserSettings: Boolean
    @JvmField val mAutoCorrectTrigger: String
    @JvmField val mAutoCorrectEnabled: Boolean
    @JvmField val mAutoCorrectionThreshold: Float
    @JvmField val mAutoCorrectShortcuts: Boolean
    @JvmField val mPersistFloatingKeyboard: Boolean
    @JvmField val mRememberFloatingKeyboard: Boolean
    @JvmField val mPersistTextEditMode: Boolean
    @JvmField val mBackspaceRevertsAutocorrect: Boolean
    @JvmField val mDisableMultiWordSuggestions: Boolean
    @JvmField val mPrioritizePersonalSuggestions: Boolean
    @JvmField val mSuggestionBalance: Int
    @JvmField val mNextWordBoostLevel: Int
    @JvmField val mNextWordStrictNgram: Boolean
    @JvmField val mScoreLimitForAutocorrect: Int
    private val mSuggestionsEnabledPerUserSettings: Boolean
    private val mOverrideShowingSuggestions: Boolean
    @JvmField val mSuggestClipboardContent: Boolean
    @JvmField val mSuggestScreenshots: Boolean
    @JvmField val mAutoReadOtp: Boolean
    @JvmField val mInlineMathCalculation: Boolean
    @JvmField val mCompressScreenshots: Boolean
    @JvmField val mSettingsValuesForSuggestion: SettingsValuesForSuggestion
    @JvmField val mIncognitoModeEnabled: Boolean
    @JvmField val mLongPressSymbolsForNumpad: Boolean
    @JvmField val mFoldableMode: Boolean
    @JvmField val mColors: Colors

    init {
        mLocale = res.configuration.locale()
        mCurrentKeyboardScript = currentKeyboardScript
        mDisplayOrientation = res.configuration.orientation
        mFoldableMode = prefs.getBoolean(Settings.PREF_FOLDABLE_MODE, false)
        mScreenProfile = helium314.keyboard.latin.utils.ScreenProfileProvider.getScreenProfile(context, res.configuration, this)
        val selectedSubtype = SubtypeSettings.getSelectedSubtype(prefs)

        mToolbarMode = Settings.readToolbarMode(prefs)
        mPhysicalKeyboardSuggestionShortcuts = prefs.getString(Settings.PREF_PHYSICAL_KEYBOARD_SUGGESTION_SHORTCUTS, Defaults.PREF_PHYSICAL_KEYBOARD_SUGGESTION_SHORTCUTS)!!
        mToolbarHidingGlobal = prefs.getBoolean(Settings.PREF_TOOLBAR_HIDING_GLOBAL, Defaults.PREF_TOOLBAR_HIDING_GLOBAL)
        mSplitToolbar = prefs.getBoolean(Settings.PREF_SPLIT_TOOLBAR, Defaults.PREF_SPLIT_TOOLBAR)
        mAutoSpanToolbarKeys = prefs.getBoolean(Settings.PREF_AUTO_SPAN_TOOLBAR_KEYS, Defaults.PREF_AUTO_SPAN_TOOLBAR_KEYS)
        mToolbarKeysAlignment = prefs.getString(Settings.PREF_TOOLBAR_KEYS_ALIGNMENT, prefs.getString(Settings.PREF_CLIPBOARD_KEYS_ALIGNMENT, Defaults.PREF_TOOLBAR_KEYS_ALIGNMENT))!!
        mClipboardKeysAlignment = mToolbarKeysAlignment
        mShowDownloadButtonInToolbar = prefs.getBoolean(Settings.PREF_SHOW_DOWNLOAD_BUTTON_IN_TOOLBAR, Defaults.PREF_SHOW_DOWNLOAD_BUTTON_IN_TOOLBAR)
        mAutoCap = prefs.getBoolean(Settings.PREF_AUTO_CAP, Defaults.PREF_AUTO_CAP) && ScriptUtils.scriptSupportsUppercase(mLocale)
        mVibrateOn = Settings.readVibrationEnabled(prefs)
        mVibrateInDndMode = prefs.getBoolean(Settings.PREF_VIBRATE_IN_DND_MODE, Defaults.PREF_VIBRATE_IN_DND_MODE)
        mSoundOn = prefs.getBoolean(Settings.PREF_SOUND_ON, Defaults.PREF_SOUND_ON)
        mSuggestEmojis = prefs.getBoolean(Settings.PREF_SUGGEST_EMOJIS, Defaults.PREF_SUGGEST_EMOJIS)
        mInlineEmojiSearch = prefs.getBoolean(Settings.PREF_INLINE_EMOJI_SEARCH, Defaults.PREF_INLINE_EMOJI_SEARCH)
        mShowEmojiDescriptions = prefs.getBoolean(Settings.PREF_SHOW_EMOJI_DESCRIPTIONS, Defaults.PREF_SHOW_EMOJI_DESCRIPTIONS)
        mKeyPreviewPopupOn = prefs.getBoolean(Settings.PREF_POPUP_ON, Defaults.PREF_POPUP_ON)
        mSlidingKeyInputPreviewEnabled = prefs.getBoolean(DebugSettings.PREF_SLIDING_KEY_INPUT_PREVIEW, Defaults.PREF_SLIDING_KEY_INPUT_PREVIEW)
        mShowsVoiceInputKey = mInputAttributes.mShouldShowVoiceInputKey

        val languagePref = prefs.getString(Settings.PREF_LANGUAGE_SWITCH_KEY, Defaults.PREF_LANGUAGE_SWITCH_KEY)!!
        mLanguageSwitchKeyToOtherImes = languagePref == "input_method" || languagePref == "both"
        mLanguageSwitchKeyToOtherSubtypes = languagePref == "internal" || languagePref == "both"
        mShowsLanguageSwitchKey = prefs.getBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, Defaults.PREF_SHOW_LANGUAGE_SWITCH_KEY)

        mShowsNumberRow = prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, Defaults.PREF_SHOW_NUMBER_ROW)
        mShowsNumberRowInSymbols = prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS, Defaults.PREF_SHOW_NUMBER_ROW_IN_SYMBOLS)
        mCompactNumberRowInSymbols = prefs.getBoolean(Settings.PREF_COMPACT_NUMBER_ROW_IN_SYMBOLS, Defaults.PREF_COMPACT_NUMBER_ROW_IN_SYMBOLS)
        mLocalizedNumberRow = getHasLocalizedNumberRow(selectedSubtype, prefs)
        mShowNumberRowHints = prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW_HINTS, Defaults.PREF_SHOW_NUMBER_ROW_HINTS)
        mShowsHints = prefs.getBoolean(Settings.PREF_SHOW_HINTS, Defaults.PREF_SHOW_HINTS)
        mShowsPopupHints = prefs.getBoolean(Settings.PREF_SHOW_POPUP_HINTS, Defaults.PREF_SHOW_POPUP_HINTS)
        mShowTldPopupKeys = prefs.getBoolean(Settings.PREF_SHOW_TLD_POPUP_KEYS, Defaults.PREF_SHOW_TLD_POPUP_KEYS)
        mSpaceForLangChange = prefs.getBoolean(Settings.PREF_SPACE_TO_CHANGE_LANG, Defaults.PREF_SPACE_TO_CHANGE_LANG)
        mShowsEmojiKey = prefs.getBoolean(Settings.PREF_SHOW_EMOJI_KEY, Defaults.PREF_SHOW_EMOJI_KEY)
        mVarToolbarDirection = mToolbarMode != ToolbarMode.HIDDEN && prefs.getBoolean(Settings.PREF_VARIABLE_TOOLBAR_DIRECTION, Defaults.PREF_VARIABLE_TOOLBAR_DIRECTION)
        mUsePersonalizedDicts = prefs.getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, Defaults.PREF_KEY_USE_PERSONALIZED_DICTS)
        mEnableSpellCheckerService = prefs.getBoolean(Settings.PREF_ENABLE_SPELL_CHECKER_SERVICE, Defaults.PREF_ENABLE_SPELL_CHECKER_SERVICE)
        mEnableContactsObserver = prefs.getBoolean(Settings.PREF_ENABLE_CONTACTS_OBSERVER, Defaults.PREF_ENABLE_CONTACTS_OBSERVER)
        mEnableClipboardListener = prefs.getBoolean(Settings.PREF_ENABLE_CLIPBOARD_LISTENER, Defaults.PREF_ENABLE_CLIPBOARD_LISTENER)
        mEnableSmsOtpReceiver = prefs.getBoolean(Settings.PREF_ENABLE_SMS_OTP_RECEIVER, Defaults.PREF_ENABLE_SMS_OTP_RECEIVER)
        mEnableAppSyncListener = prefs.getBoolean(Settings.PREF_ENABLE_APP_SYNC_LISTENER, Defaults.PREF_ENABLE_APP_SYNC_LISTENER)
        mUseDoubleSpacePeriod = prefs.getBoolean(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, Defaults.PREF_KEY_USE_DOUBLE_SPACE_PERIOD) && mInputAttributes.mIsGeneralTextInput
        mBlockPotentiallyOffensive = prefs.getBoolean(Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE, Defaults.PREF_BLOCK_POTENTIALLY_OFFENSIVE)
        mUrlDetectionEnabled = prefs.getBoolean(Settings.PREF_URL_DETECTION, Defaults.PREF_URL_DETECTION)
        mAutoCorrectionEnabledPerUserSettings = prefs.getBoolean(Settings.PREF_AUTO_CORRECTION, Defaults.PREF_AUTO_CORRECTION)
        mAutoCorrectTrigger = prefs.getString(Settings.PREF_AUTO_CORRECT_TRIGGER, Defaults.PREF_AUTO_CORRECT_TRIGGER)!!
        mAutoCorrectEnabled = mAutoCorrectionEnabledPerUserSettings && (mInputAttributes.mInputTypeShouldAutoCorrect || prefs.getBoolean(Settings.PREF_MORE_AUTO_CORRECTION, Defaults.PREF_MORE_AUTO_CORRECTION)) && (mUrlDetectionEnabled || !InputTypeUtils.isUriOrEmailType(mInputAttributes.mInputType))
        mCenterSuggestionTextToEnter = prefs.getBoolean(Settings.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER, Defaults.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER)
        mAutoCorrectionThreshold = if (mAutoCorrectEnabled) prefs.getFloat(Settings.PREF_AUTO_CORRECT_THRESHOLD, Defaults.PREF_AUTO_CORRECT_THRESHOLD) else Float.MAX_VALUE
        mScoreLimitForAutocorrect = if (mAutoCorrectionThreshold < 0) 600000 else (if (mAutoCorrectionThreshold < 0.07f) 800000 else 950000)
        mAutoCorrectShortcuts = prefs.getBoolean(Settings.PREF_AUTOCORRECT_SHORTCUTS, Defaults.PREF_AUTOCORRECT_SHORTCUTS)
        mPersistFloatingKeyboard = prefs.getBoolean(Settings.PREF_PERSIST_FLOATING_KEYBOARD, Defaults.PREF_PERSIST_FLOATING_KEYBOARD)
        mRememberFloatingKeyboard = prefs.getBoolean(Settings.PREF_REMEMBER_FLOATING_KEYBOARD, Defaults.PREF_REMEMBER_FLOATING_KEYBOARD)
        mPersistTextEditMode = prefs.getBoolean(Settings.PREF_PERSIST_TEXT_EDIT_MODE, Defaults.PREF_PERSIST_TEXT_EDIT_MODE)
        mBackspaceRevertsAutocorrect = prefs.getBoolean(Settings.PREF_BACKSPACE_REVERTS_AUTOCORRECT, Defaults.PREF_BACKSPACE_REVERTS_AUTOCORRECT)
        mDisableMultiWordSuggestions = prefs.getBoolean(Settings.PREF_DISABLE_MULTI_WORD_SUGGESTIONS, Defaults.PREF_DISABLE_MULTI_WORD_SUGGESTIONS)
        mBigramPredictionEnabled = prefs.getBoolean(Settings.PREF_BIGRAM_PREDICTIONS, Defaults.PREF_BIGRAM_PREDICTIONS)
        mPrioritizePersonalSuggestions = prefs.getBoolean(Settings.PREF_PRIORITIZE_PERSONAL_SUGGESTIONS, Defaults.PREF_PRIORITIZE_PERSONAL_SUGGESTIONS)
        mSuggestionBalance = prefs.getInt(Settings.PREF_SUGGESTION_BALANCE, Defaults.PREF_SUGGESTION_BALANCE)

        var boostLevel = 500
        try {
            boostLevel = prefs.getString(Settings.PREF_NEXT_WORD_BOOST_LEVEL, Defaults.PREF_NEXT_WORD_BOOST_LEVEL)!!.toInt()
        } catch (e: Exception) {
            boostLevel = 500
        }
        mNextWordBoostLevel = boostLevel
        mNextWordStrictNgram = prefs.getBoolean(Settings.PREF_NEXT_WORD_STRICT_NGRAM, Defaults.PREF_NEXT_WORD_STRICT_NGRAM)
        mFirstWordPredictionEnabled = prefs.getBoolean(Settings.PREF_FIRST_WORD_PREDICTIONS, Defaults.PREF_FIRST_WORD_PREDICTIONS)
        mSuggestPunctuation = prefs.getBoolean(Settings.PREF_SUGGEST_PUNCTUATION, Defaults.PREF_SUGGEST_PUNCTUATION)
        mSuggestClipboardContent = prefs.getBoolean(Settings.PREF_SUGGEST_CLIPBOARD_CONTENT, Defaults.PREF_SUGGEST_CLIPBOARD_CONTENT)
        mSuggestScreenshots = prefs.getBoolean(Settings.PREF_SUGGEST_SCREENSHOTS, Defaults.PREF_SUGGEST_SCREENSHOTS)
        mAutoReadOtp = prefs.getBoolean(Settings.PREF_AUTO_READ_OTP, Defaults.PREF_AUTO_READ_OTP)
        mInlineMathCalculation = prefs.getBoolean(Settings.PREF_INLINE_MATH_CALCULATION, Defaults.PREF_INLINE_MATH_CALCULATION)
        mCompressScreenshots = prefs.getBoolean(Settings.PREF_COMPRESS_SCREENSHOTS, Defaults.PREF_COMPRESS_SCREENSHOTS)
        mDoubleSpacePeriodTimeout = 1100L
        mHasHardwareKeyboard = Settings.readHasHardwareKeyboard(res.configuration)
        mShowToolbarOnly = mHasHardwareKeyboard && prefs.getBoolean(Settings.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD, Defaults.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD)

        val isLandscape = mDisplayOrientation == Configuration.ORIENTATION_LANDSCAPE
        val displayWidthDp = TypedValueCompat.pxToDp(res.displayMetrics.widthPixels.toFloat(), res.displayMetrics)
        mIsSplitKeyboardEnabled = Settings.readSplitKeyboardEnabled(prefs, isLandscape, mScreenProfile)
        mSplitKeyboardSpacerRelativeWidth = if (mIsSplitKeyboardEnabled)
            minOf(maxOf((displayWidthDp - 600f) / 600f + 0.15f, 0.15f), 0.35f) * Settings.readSplitSpacerScale(prefs, isLandscape)
        else 0f

        mQuickPinToolbarKeys = mToolbarMode == ToolbarMode.EXPANDABLE && prefs.getBoolean(Settings.PREF_QUICK_PIN_TOOLBAR_KEYS, Defaults.PREF_QUICK_PIN_TOOLBAR_KEYS)
        mScreenMetrics = Settings.readScreenMetrics(res)
        mKeyLongpressTimeout = prefs.getInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, Defaults.PREF_KEY_LONGPRESS_TIMEOUT)
        mKeypressVibrationDuration = prefs.getInt(Settings.PREF_VIBRATION_DURATION_SETTINGS, Defaults.PREF_VIBRATION_DURATION_SETTINGS)
        mKeypressVibrationAmplitude = prefs.getInt(Settings.PREF_VIBRATION_AMPLITUDE_SETTINGS, Defaults.PREF_VIBRATION_AMPLITUDE_SETTINGS)
        mKeypressSoundVolume = prefs.getFloat(Settings.PREF_KEYPRESS_SOUND_VOLUME, Defaults.PREF_KEYPRESS_SOUND_VOLUME)
        mKeypressSoundStyle = prefs.getString(Settings.PREF_KEYPRESS_SOUND_STYLE, Defaults.PREF_KEYPRESS_SOUND_STYLE)!!
        mSoundPitchScale = prefs.getFloat(Settings.PREF_SOUND_PITCH_SCALE, Defaults.PREF_SOUND_PITCH_SCALE)
        mSoundRandomPitch = prefs.getBoolean(Settings.PREF_SOUND_RANDOM_PITCH, Defaults.PREF_SOUND_RANDOM_PITCH)
        mSoundStereoPan = prefs.getBoolean(Settings.PREF_SOUND_STEREO_PAN, Defaults.PREF_SOUND_STEREO_PAN)
        mSoundDynamicVelocity = prefs.getBoolean(Settings.PREF_SOUND_DYNAMIC_VELOCITY, Defaults.PREF_SOUND_DYNAMIC_VELOCITY)
        mSoundMuteInSilent = prefs.getBoolean(Settings.PREF_SOUND_MUTE_IN_SILENT, Defaults.PREF_SOUND_MUTE_IN_SILENT)
        mSoundMuteInDnd = prefs.getBoolean(Settings.PREF_SOUND_MUTE_IN_DND, Defaults.PREF_SOUND_MUTE_IN_DND)
        mSoundVolSpace = prefs.getFloat(Settings.PREF_SOUND_VOL_SPACE, Defaults.PREF_SOUND_VOL_SPACE)
        mSoundVolDelete = prefs.getFloat(Settings.PREF_SOUND_VOL_DELETE, Defaults.PREF_SOUND_VOL_DELETE)
        mSoundVolEnter = prefs.getFloat(Settings.PREF_SOUND_VOL_ENTER, Defaults.PREF_SOUND_VOL_ENTER)
        mSoundVolModifiers = prefs.getFloat(Settings.PREF_SOUND_VOL_MODIFIERS, Defaults.PREF_SOUND_VOL_MODIFIERS)
        mEnableEmojiAltPhysicalKey = prefs.getBoolean(Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY, Defaults.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY)
        mGestureMethod = prefs.getString(Settings.PREF_GESTURE_METHOD, "fallback")!!
        mGestureInputEnabled = JniUtils.sHaveGestureLib && prefs.getBoolean(Settings.PREF_GESTURE_INPUT, Defaults.PREF_GESTURE_INPUT)
        mGestureTrailEnabled = prefs.getBoolean(Settings.PREF_GESTURE_PREVIEW_TRAIL, Defaults.PREF_GESTURE_PREVIEW_TRAIL)
        mGestureFloatingPreviewTextEnabled = !mInputAttributes.mDisableGestureFloatingPreviewText && prefs.getBoolean(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, Defaults.PREF_GESTURE_FLOATING_PREVIEW_TEXT)
        mGestureFloatingPreviewDynamicEnabled = Settings.readGestureDynamicPreviewEnabled(prefs)
        mGestureFastTypingCooldown = prefs.getInt(Settings.PREF_GESTURE_FAST_TYPING_COOLDOWN, Defaults.PREF_GESTURE_FAST_TYPING_COOLDOWN)
        mGestureTrailFadeoutDuration = prefs.getInt(Settings.PREF_GESTURE_TRAIL_FADEOUT_DURATION, Defaults.PREF_GESTURE_TRAIL_FADEOUT_DURATION)
        mSuggestionStripHiddenPerUserSettings = mToolbarMode == ToolbarMode.HIDDEN || mToolbarMode == ToolbarMode.TOOLBAR_KEYS

        val moreAutoCorrection = prefs.getBoolean(Settings.PREF_MORE_AUTO_CORRECTION, Defaults.PREF_MORE_AUTO_CORRECTION)
        val isUriOrEmail = InputTypeUtils.isUriOrEmailType(mInputAttributes.mInputType)
        mOverrideShowingSuggestions = mInputAttributes.mMayOverrideShowingSuggestions && (prefs.getBoolean(Settings.PREF_ALWAYS_SHOW_SUGGESTIONS, Defaults.PREF_ALWAYS_SHOW_SUGGESTIONS) || (moreAutoCorrection && !isUriOrEmail)) && ((mInputAttributes.mInputType and InputType.TYPE_MASK_VARIATION) != InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT || !prefs.getBoolean(Settings.PREF_ALWAYS_SHOW_SUGGESTIONS_EXCEPT_WEB_TEXT, Defaults.PREF_ALWAYS_SHOW_SUGGESTIONS_EXCEPT_WEB_TEXT))

        val suggestionsEnabled = prefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, Defaults.PREF_SHOW_SUGGESTIONS)
        mSuggestionsEnabledPerUserSettings = suggestionsEnabled && (mInputAttributes.mShouldShowSuggestions || mOverrideShowingSuggestions) && !mSuggestionStripHiddenPerUserSettings
        mSecondaryStripVisible = mToolbarMode != ToolbarMode.HIDDEN || !mToolbarHidingGlobal
        mIncognitoModeEnabled = prefs.getBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, Defaults.PREF_ALWAYS_INCOGNITO_MODE) || mInputAttributes.mNoLearning || mInputAttributes.mIsPasswordField
        mKeyboardHeightScale = Settings.readHeightScale(prefs, isLandscape, mScreenProfile)
        mSpaceSwipeHorizontal = Settings.readHorizontalSpaceSwipe(prefs)
        mSpaceSwipeVertical = Settings.readVerticalSpaceSwipe(prefs)
        mLanguageSwipeDistance = prefs.getInt(Settings.PREF_LANGUAGE_SWIPE_DISTANCE, Defaults.PREF_LANGUAGE_SWIPE_DISTANCE)
        mTouchpadSensitivity = prefs.getInt(Settings.PREF_TOUCHPAD_SENSITIVITY, Defaults.PREF_TOUCHPAD_SENSITIVITY)
        mTouchpadFullscreen = prefs.getBoolean(Settings.PREF_TOUCHPAD_FULLSCREEN, Defaults.PREF_TOUCHPAD_FULLSCREEN)
        mForceAutoCaps = prefs.getBoolean(Settings.PREF_FORCE_AUTO_CAPS, Defaults.PREF_FORCE_AUTO_CAPS)
        mDeleteSwipeEnabled = prefs.getBoolean(Settings.PREF_DELETE_SWIPE, Defaults.PREF_DELETE_SWIPE)
        mAutospaceAfterPunctuation = prefs.getBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, Defaults.PREF_AUTOSPACE_AFTER_PUNCTUATION)
        mAutospaceAfterEmoji = prefs.getBoolean(Settings.PREF_AUTOSPACE_AFTER_EMOJI, Defaults.PREF_AUTOSPACE_AFTER_EMOJI)
        mAutospaceAfterSuggestion = prefs.getBoolean(Settings.PREF_AUTOSPACE_AFTER_SUGGESTION, Defaults.PREF_AUTOSPACE_AFTER_SUGGESTION)
        mImmediateAutoSpace = prefs.getBoolean(Settings.PREF_IMMEDIATE_AUTO_SPACE, Defaults.PREF_IMMEDIATE_AUTO_SPACE)
        mAutospaceAfterGestureTyping = prefs.getBoolean(Settings.PREF_AUTOSPACE_AFTER_GESTURE_TYPING, Defaults.PREF_AUTOSPACE_AFTER_GESTURE_TYPING)
        mAutospaceBeforeGestureTyping = prefs.getBoolean(Settings.PREF_AUTOSPACE_BEFORE_GESTURE_TYPING, Defaults.PREF_AUTOSPACE_BEFORE_GESTURE_TYPING)
        mShiftRemovesAutospace = prefs.getBoolean(Settings.PREF_SHIFT_REMOVES_AUTOSPACE, Defaults.PREF_SHIFT_REMOVES_AUTOSPACE)
        mPreserveSpaceBeforePunctuation = prefs.getBoolean(Settings.PREF_PRESERVE_SPACE_BEFORE_PUNCTUATION, Defaults.PREF_PRESERVE_SPACE_BEFORE_PUNCTUATION)
        mClipboardHistoryEnabled = prefs.getBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, Defaults.PREF_ENABLE_CLIPBOARD_HISTORY)
        mClipboardHistoryRetentionTime = prefs.getInt(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME, Defaults.PREF_CLIPBOARD_HISTORY_RETENTION_TIME).toLong()
        mClipboardHistoryPinnedFirst = prefs.getBoolean(Settings.PREF_CLIPBOARD_HISTORY_PINNED_FIRST, Defaults.PREF_CLIPBOARD_HISTORY_PINNED_FIRST)
        mClipboardFoldPinned = prefs.getBoolean(Settings.PREF_CLIPBOARD_FOLD_PINNED, Defaults.PREF_CLIPBOARD_FOLD_PINNED)
        mOneHandedModeEnabled = Settings.readOneHandedModeEnabled(prefs, isLandscape, mIsSplitKeyboardEnabled)
        mOneHandedModeGravity = Settings.readOneHandedModeGravity(prefs, isLandscape, mIsSplitKeyboardEnabled)
        mOneHandedModeScale = if (mOneHandedModeEnabled) {
            val baseScale = res.getFraction(R.fraction.config_one_handed_mode_width, 1, 1)
            val extraScale = Settings.readOneHandedModeScale(prefs, isLandscape, mIsSplitKeyboardEnabled)
            1f - (1f - baseScale) * extraScale
        } else 1f
        mSecondaryLocales = getSecondaryLocales(selectedSubtype.extraValue)
        mShowMorePopupKeys = if (selectedSubtype.isAsciiCapable) getMoreKeys(selectedSubtype, prefs) else POPUP_KEYS_NORMAL
        mColors = KeyboardTheme.getColorsForCurrentTheme(context)
        mPopupKeyTypes = getPopupKeyTypes(selectedSubtype, prefs)
        mPopupKeyLabelSources = getPopupKeyLabelSources(selectedSubtype, prefs)
        mAddToPersonalDictionary = prefs.getBoolean(Settings.PREF_ADD_TO_PERSONAL_DICTIONARY, Defaults.PREF_ADD_TO_PERSONAL_DICTIONARY)
        mAddToPersonalDictThreshold = prefs.getInt(Settings.PREF_ADD_TO_PERSONAL_DICT_THRESHOLD, Defaults.PREF_ADD_TO_PERSONAL_DICT_THRESHOLD)
        mUseContactsDictionary = readUseContactsEnabled(prefs, context)
        mUseAppsDictionary = prefs.getBoolean(Settings.PREF_USE_APPS, Defaults.PREF_USE_APPS)
        mCustomNavBarColor = prefs.getBoolean(Settings.PREF_NAVBAR_COLOR, Defaults.PREF_NAVBAR_COLOR)
        mNarrowKeyGaps = prefs.getBoolean(Settings.PREF_NARROW_KEY_GAPS, Defaults.PREF_NARROW_KEY_GAPS)
        mNarrowKeyGapsLevel = prefs.getInt(Settings.PREF_NARROW_KEY_GAPS_LEVEL, Defaults.PREF_NARROW_KEY_GAPS_LEVEL)
        mThemeKeyBorders = prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, Defaults.PREF_THEME_KEY_BORDERS)
        mSettingsValuesForSuggestion = SettingsValuesForSuggestion(mBlockPotentiallyOffensive, prefs.getBoolean(Settings.PREF_GESTURE_SPACE_AWARE, Defaults.PREF_GESTURE_SPACE_AWARE), mGestureMethod)
        mSpacingAndPunctuations = SpacingAndPunctuations(res, mUrlDetectionEnabled)
        mBottomPaddingScale = Settings.readBottomPaddingScale(prefs, isLandscape)
        mSidePaddingScale = Settings.readSidePaddingScale(prefs, isLandscape, mIsSplitKeyboardEnabled)
        mLongPressSymbolsForNumpad = prefs.getBoolean(Settings.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD, Defaults.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD)
        mAutoShowToolbarOnSelect = mToolbarMode == ToolbarMode.EXPANDABLE && !mSplitToolbar && prefs.getBoolean(Settings.PREF_AUTO_SHOW_TOOLBAR_ON_SELECT, Defaults.PREF_AUTO_SHOW_TOOLBAR_ON_SELECT)
        mAutoShowToolbar = mToolbarMode == ToolbarMode.EXPANDABLE && !mAutoShowToolbarOnSelect && prefs.getBoolean(Settings.PREF_AUTO_SHOW_TOOLBAR, Defaults.PREF_AUTO_SHOW_TOOLBAR)
        mAutoHideToolbar = mSuggestionsEnabledPerUserSettings && prefs.getBoolean(Settings.PREF_AUTO_HIDE_TOOLBAR, Defaults.PREF_AUTO_HIDE_TOOLBAR)
        mToolbarSwipeDownDismiss = prefs.getBoolean(Settings.PREF_TOOLBAR_SWIPE_DOWN_DISMISS, Defaults.PREF_TOOLBAR_SWIPE_DOWN_DISMISS)
        mAutoHidePinnedKeys = mToolbarMode == ToolbarMode.EXPANDABLE && !mSplitToolbar && prefs.getBoolean(Settings.PREF_AUTO_HIDE_PINNED_KEYS, Defaults.PREF_AUTO_HIDE_PINNED_KEYS)
        mRememberToolbarState = prefs.getBoolean(Settings.PREF_REMEMBER_TOOLBAR_STATE, Defaults.PREF_REMEMBER_TOOLBAR_STATE)

        if (!prefs.contains(Settings.PREF_AUTO_HIDE_PINNED_KEYS)) {
            prefs.edit().putBoolean(Settings.PREF_AUTO_HIDE_PINNED_KEYS, Defaults.PREF_AUTO_HIDE_PINNED_KEYS).apply()
        }

        mAlphaAfterEmojiInEmojiView = prefs.getBoolean(Settings.PREF_ABC_AFTER_EMOJI, Defaults.PREF_ABC_AFTER_EMOJI)
        mAlphaAfterClipHistoryEntry = prefs.getBoolean(Settings.PREF_ABC_AFTER_CLIP, Defaults.PREF_ABC_AFTER_CLIP)
        mAlphaAfterSymbolAndSpace = prefs.getBoolean(Settings.PREF_ABC_AFTER_SYMBOL_SPACE, Defaults.PREF_ABC_AFTER_SYMBOL_SPACE)
        mAlphaAfterNumpadAndSpace = prefs.getBoolean(Settings.PREF_ABC_AFTER_NUMPAD_SPACE, Defaults.PREF_ABC_AFTER_NUMPAD_SPACE)
        mRemoveRedundantPopups = prefs.getBoolean(Settings.PREF_REMOVE_REDUNDANT_POPUPS, Defaults.PREF_REMOVE_REDUNDANT_POPUPS)
        mSpaceBarText = prefs.getString(Settings.PREF_SPACE_BAR_TEXT, Defaults.PREF_SPACE_BAR_TEXT)!!
        mFontSizeMultiplier = prefs.getFloat(Settings.PREF_FONT_SCALE, Defaults.PREF_FONT_SCALE)
        mFontSizeMultiplierEmoji = prefs.getFloat(Settings.PREF_EMOJI_FONT_SCALE, Defaults.PREF_EMOJI_FONT_SCALE)
        mEmojiKeyFit = prefs.getBoolean(Settings.PREF_EMOJI_KEY_FIT, Defaults.PREF_EMOJI_KEY_FIT)
    }

    fun isApplicationSpecifiedCompletionsOn(): Boolean {
        return mInputAttributes.mApplicationSpecifiedCompletionOn
    }

    fun needsToLookupSuggestions(): Boolean {
        return (mInputAttributes.mShouldShowSuggestions || mOverrideShowingSuggestions) && (mAutoCorrectEnabled || mSuggestionsEnabledPerUserSettings)
    }

    fun isSuggestionsEnabledPerUserSettings(): Boolean {
        return mSuggestionsEnabledPerUserSettings
    }

    fun isWordSeparator(code: Int): Boolean {
        return mSpacingAndPunctuations.isWordSeparator(code)
    }

    fun isWordConnector(code: Int): Boolean {
        return mSpacingAndPunctuations.isWordConnector(code)
    }

    fun isWordCodePoint(code: Int): Boolean {
        return mSpacingAndPunctuations.isWordCodePoint(code)
    }

    fun isUsuallyPrecededBySpace(code: Int): Boolean {
        return mSpacingAndPunctuations.isUsuallyPrecededBySpace(code)
    }

    fun isUsuallyFollowedBySpace(code: Int): Boolean {
        return mSpacingAndPunctuations.isUsuallyFollowedBySpace(code)
    }

    fun shouldInsertSpacesAutomatically(): Boolean {
        return mInputAttributes.mShouldInsertSpacesAutomatically
    }

    fun isLanguageSwitchKeyEnabled(): Boolean {
        if (!mShowsLanguageSwitchKey) {
            return false
        }
        val imm = RichInputMethodManager.getInstance()
        if (!mLanguageSwitchKeyToOtherSubtypes) {
            return imm.hasMultipleEnabledIMEsOrSubtypes(false)
        }
        if (!mLanguageSwitchKeyToOtherImes) {
            return imm.hasMultipleEnabledSubtypesInThisIme(false)
        }
        return imm.hasMultipleEnabledSubtypesInThisIme(false) || imm.hasMultipleEnabledIMEsOrSubtypes(false)
    }

    fun isSameInputType(editorInfo: EditorInfo): Boolean {
        return mInputAttributes.isSameInputType(editorInfo)
    }

    fun hasSameOrientation(configuration: Configuration): Boolean {
        return mDisplayOrientation == configuration.orientation
    }

    fun dump(): String = """
Current settings :
   mSpacingAndPunctuations = ${mSpacingAndPunctuations.dump()}
   mAutoCap = $mAutoCap
   mVibrateOn = $mVibrateOn
   mSoundOn = $mSoundOn
   mKeyPreviewPopupOn = $mKeyPreviewPopupOn
   mShowsVoiceInputKey = $mShowsVoiceInputKey
   mLanguageSwitchKeyToOtherImes = $mLanguageSwitchKeyToOtherImes
   mLanguageSwitchKeyToOtherSubtypes = $mLanguageSwitchKeyToOtherSubtypes
   mUsePersonalizedDicts = $mUsePersonalizedDicts
   mUseDoubleSpacePeriod = $mUseDoubleSpacePeriod
   mBlockPotentiallyOffensive = $mBlockPotentiallyOffensive
   mBigramPredictionEnabled = $mBigramPredictionEnabled
   mFirstWordPredictionEnabled = $mFirstWordPredictionEnabled
   mGestureInputEnabled = $mGestureInputEnabled
   mGestureTrailEnabled = $mGestureTrailEnabled
   mGestureFloatingPreviewTextEnabled = $mGestureFloatingPreviewTextEnabled
   mSlidingKeyInputPreviewEnabled = $mSlidingKeyInputPreviewEnabled
   mKeyLongpressTimeout = $mKeyLongpressTimeout
   mLocale = $mLocale
   mCurrentKeyboardScript = $mCurrentKeyboardScript
   mInputAttributes = $mInputAttributes
   mKeypressVibrationDuration = $mKeypressVibrationDuration
   mKeypressVibrationAmplitude = $mKeypressVibrationAmplitude
   mKeypressSoundVolume = $mKeypressSoundVolume
   mAutoCorrectEnabled = $mAutoCorrectEnabled
   mAutoCorrectionThreshold = $mAutoCorrectionThreshold
   mAutoCorrectionEnabledPerUserSettings = $mAutoCorrectionEnabledPerUserSettings
   mSuggestionsEnabledPerUserSettings = $mSuggestionsEnabledPerUserSettings
   mDisplayOrientation = $mDisplayOrientation
   mAppWorkarounds = 
    """.trimIndent()

    companion object {
        @JvmStatic
        private fun readUseContactsEnabled(prefs: SharedPreferences, ctx: Context): Boolean {
            val setting = prefs.getBoolean(Settings.PREF_USE_CONTACTS, Defaults.PREF_USE_CONTACTS)
            if (!setting) return false
            if (PermissionsUtil.checkAllPermissionsGranted(ctx, Manifest.permission.READ_CONTACTS)) {
                return true
            }
            prefs.edit().putBoolean(Settings.PREF_USE_CONTACTS, false).apply()
            return false
        }
    }
}
