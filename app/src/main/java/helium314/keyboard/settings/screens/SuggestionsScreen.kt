// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.personalization.PersonalizationHelper
import helium314.keyboard.latin.permissions.PermissionsUtil
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.fineTuneExpanded
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.ExpandablePreference
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.preferences.SwitchPreferenceWithEmojiDictWarning
import helium314.keyboard.settings.previewDark
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun SuggestionsScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val activity = ctx.getActivity() as? SettingsActivity
    val prefChanged = (activity?.prefChanged ?: MutableStateFlow(0)).collectAsState()

    val suggestionsVisible = Settings.readToolbarMode(prefs) in setOf(ToolbarMode.SUGGESTION_STRIP, ToolbarMode.EXPANDABLE)
    val suggestionsEnabled = suggestionsVisible && prefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, Defaults.PREF_SHOW_SUGGESTIONS)
    val bigramPredictionsEnabled = prefs.getBoolean(Settings.PREF_BIGRAM_PREDICTIONS, Defaults.PREF_BIGRAM_PREDICTIONS)
    val fineTunePredictionExpanded = fineTuneExpanded(prefs, Settings.PREF_EXPAND_FINE_TUNE_PREDICTION, Defaults.PREF_EXPAND_FINE_TUNE_PREDICTION)

    val items = remember(suggestionsVisible, suggestionsEnabled, bigramPredictionsEnabled, fineTunePredictionExpanded, prefChanged.value) {
        listOf(
            // Suggestions strip
            R.string.settings_category_suggestions,
            if (suggestionsVisible) Settings.PREF_SHOW_SUGGESTIONS else null,
            if (suggestionsEnabled) Settings.PREF_ALWAYS_SHOW_SUGGESTIONS else null,
            if (suggestionsEnabled && prefs.getBoolean(Settings.PREF_ALWAYS_SHOW_SUGGESTIONS, Defaults.PREF_ALWAYS_SHOW_SUGGESTIONS))
                Settings.PREF_ALWAYS_SHOW_SUGGESTIONS_EXCEPT_WEB_TEXT else null,
            if (suggestionsEnabled) Settings.PREF_SUGGESTIONS_COUNT_IN_STRIP else null,
            if (suggestionsEnabled) Settings.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER else null,
            if (suggestionsEnabled) Settings.PREF_CENTER_TYPED_WORD else null,
            Settings.PREF_SUGGEST_PUNCTUATION,

            // Prediction & learning
            R.string.settings_category_prediction,
            Settings.PREF_BIGRAM_PREDICTIONS,
            if (bigramPredictionsEnabled) Settings.PREF_SUGGESTION_BALANCE else null,
            Settings.PREF_KEY_USE_PERSONALIZED_DICTS,
            Settings.PREF_CLEAR_LEARNED_WORDS,
            if (bigramPredictionsEnabled) Settings.PREF_EXPAND_FINE_TUNE_PREDICTION else null,
            if (bigramPredictionsEnabled && fineTunePredictionExpanded) Settings.PREF_PRIORITIZE_PERSONAL_SUGGESTIONS else null,
            if (bigramPredictionsEnabled && fineTunePredictionExpanded) Settings.PREF_NEXT_WORD_STRICT_NGRAM else null,
            if (suggestionsEnabled && bigramPredictionsEnabled && fineTunePredictionExpanded) Settings.PREF_DISABLE_MULTI_WORD_SUGGESTIONS else null,

            // Smart suggestions & tools
            R.string.settings_category_smart_suggestions,
            Settings.PREF_SUGGEST_EMOJIS,
            Settings.PREF_INLINE_EMOJI_SEARCH,
            Settings.PREF_SUGGEST_CLIPBOARD_CONTENT,
            Settings.PREF_SUGGEST_SCREENSHOTS,
            if (prefs.getBoolean(Settings.PREF_SUGGEST_SCREENSHOTS, Defaults.PREF_SUGGEST_SCREENSHOTS))
                Settings.PREF_COMPRESS_SCREENSHOTS else null,
            // Settings.PREF_AUTO_READ_OTP,
            Settings.PREF_INLINE_MATH_CALCULATION,
            Settings.PREF_USE_APPS,
        )
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_suggestions),
        settings = items
    )
}

fun createSuggestionsSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_SHOW_SUGGESTIONS,
        R.string.prefs_show_suggestions, R.string.prefs_show_suggestions_summary
    ) {
        SwitchPreference(it, Defaults.PREF_SHOW_SUGGESTIONS)
    },
    Setting(context, Settings.PREF_ALWAYS_SHOW_SUGGESTIONS,
        R.string.prefs_always_show_suggestions, R.string.prefs_always_show_suggestions_summary
    ) {
        SwitchPreference(it, Defaults.PREF_ALWAYS_SHOW_SUGGESTIONS)
    },
    Setting(context, Settings.PREF_ALWAYS_SHOW_SUGGESTIONS_EXCEPT_WEB_TEXT,
        R.string.prefs_always_show_suggestions_except_web_text, R.string.prefs_always_show_suggestions_except_web_text_summary
    ) {
        SwitchPreference(it, Defaults.PREF_ALWAYS_SHOW_SUGGESTIONS_EXCEPT_WEB_TEXT)
    },
    Setting(context, Settings.PREF_SUGGESTIONS_COUNT_IN_STRIP,
        R.string.suggestions_count_in_strip, R.string.suggestions_count_in_strip_summary
    ) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_SUGGESTIONS_COUNT_IN_STRIP,
            range = 2f..6f,
            stepSize = 1,
            onConfirmed = {
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            },
            description = { value ->
                if (value.toInt() == Defaults.PREF_SUGGESTIONS_COUNT_IN_STRIP) {
                    "${value.toInt()} (${stringResource(R.string.button_default)})"
                } else {
                    "${value.toInt()}"
                }
            }
        )
    },
    Setting(context, Settings.PREF_KEY_USE_PERSONALIZED_DICTS,
        R.string.use_personalized_dicts, R.string.use_personalized_dicts_summary
    ) { setting ->
        var showConfirmDialog by rememberSaveable { mutableStateOf(false) }
        SwitchPreference(setting, Defaults.PREF_KEY_USE_PERSONALIZED_DICTS,
            allowCheckedChange = {
                showConfirmDialog = !it
                it
            }
        )
        if (showConfirmDialog) {
            val prefs = LocalContext.current.prefs()
            ConfirmationDialog(
                onDismissRequest = { showConfirmDialog = false },
                onConfirmed = {
                    prefs.edit { putBoolean(setting.key, false) }
                },
                content = { Text(stringResource(R.string.disable_personalized_dicts_message)) }
            )
        }
    },
    Setting(context, Settings.PREF_CLEAR_LEARNED_WORDS,
        R.string.clear_learned_words_title, R.string.clear_learned_words_summary
    ) { setting ->
        var showConfirmDialog by rememberSaveable { mutableStateOf(false) }
        val ctx = LocalContext.current
        Preference(
            name = setting.title,
            description = setting.description,
            icon = R.drawable.ic_clear_all,
            onClick = { showConfirmDialog = true }
        )
        if (showConfirmDialog) {
            ConfirmationDialog(
                onDismissRequest = { showConfirmDialog = false },
                onConfirmed = {
                    PersonalizationHelper.clearUserHistory(ctx)
                    Toast.makeText(ctx, ctx.getString(R.string.clear_learned_words_success), Toast.LENGTH_SHORT).show()
                },
                content = { Text(stringResource(R.string.clear_learned_words_confirmation)) }
            )
        }
    },
    Setting(context, Settings.PREF_BIGRAM_PREDICTIONS,
        R.string.bigram_prediction, R.string.bigram_prediction_summary
    ) {
        SwitchPreference(it, Defaults.PREF_BIGRAM_PREDICTIONS) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_PRIORITIZE_PERSONAL_SUGGESTIONS,
        R.string.prioritize_personal_suggestions, R.string.prioritize_personal_suggestions_summary
    ) {
        SwitchPreference(it, Defaults.PREF_PRIORITIZE_PERSONAL_SUGGESTIONS)
    },
    Setting(context, Settings.PREF_SUGGESTION_BALANCE,
        R.string.suggestion_balance_title, R.string.suggestion_balance_summary
    ) {
        val prefs = LocalContext.current.prefs()
        SliderPreference(
            name = it.title,
            key = it.key,
            default = Defaults.PREF_SUGGESTION_BALANCE,
            range = 1f..5f,
            stepSize = 1,
            onConfirmed = { value ->
                Settings.applySuggestionBalancePreset(prefs, value.toInt())
            },
            description = { value ->
                when (value.toInt()) {
                    Settings.SUGGESTION_BALANCE_DICTIONARY_FOCUSED -> stringResource(R.string.suggestion_balance_desc_1)
                    Settings.SUGGESTION_BALANCE_CONSERVATIVE -> stringResource(R.string.suggestion_balance_desc_2)
                    Settings.SUGGESTION_BALANCE_PERSONALIZED -> stringResource(R.string.suggestion_balance_desc_4)
                    Settings.SUGGESTION_BALANCE_HIGHLY_PERSONALIZED -> stringResource(R.string.suggestion_balance_desc_5)
                    else -> stringResource(R.string.suggestion_balance_desc_3)
                }
            }
        )
    },
    Setting(context, Settings.PREF_EXPAND_FINE_TUNE_PREDICTION,
        R.string.fine_tune_prediction_title, R.string.fine_tune_prediction_summary
    ) {
        ExpandablePreference(it, Defaults.PREF_EXPAND_FINE_TUNE_PREDICTION)
    },
    Setting(context, Settings.PREF_NEXT_WORD_STRICT_NGRAM,
        R.string.next_word_strict_ngram, R.string.next_word_strict_ngram_summary
    ) {
        SwitchPreference(it, Defaults.PREF_NEXT_WORD_STRICT_NGRAM)
    },
    Setting(context, Settings.PREF_SUGGEST_PUNCTUATION, R.string.suggest_punctuation, R.string.suggest_punctuation_summary
    ) {
        SwitchPreference(it, Defaults.PREF_SUGGEST_PUNCTUATION) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER,
        R.string.center_suggestion_text_to_enter, R.string.center_suggestion_text_to_enter_summary
    ) {
        SwitchPreference(it, Defaults.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER)
    },
    Setting(context, Settings.PREF_CENTER_TYPED_WORD,
        R.string.center_typed_word, R.string.center_typed_word_summary
    ) {
        SwitchPreference(it, Defaults.PREF_CENTER_TYPED_WORD)
    },
    Setting(context, Settings.PREF_SUGGEST_CLIPBOARD_CONTENT,
        R.string.suggest_clipboard_content, R.string.suggest_clipboard_content_summary
    ) {
        SwitchPreference(it, Defaults.PREF_SUGGEST_CLIPBOARD_CONTENT)
    },
    Setting(context, Settings.PREF_SUGGEST_SCREENSHOTS,
        R.string.suggest_screenshots, R.string.suggest_screenshots_summary
    ) { setting ->
        val activity = LocalContext.current.getActivity() ?: return@Setting
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        var granted by remember { mutableStateOf(PermissionsUtil.checkAllPermissionsGranted(activity, permission)) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted = it
            if (granted)
                activity.prefs().edit { putBoolean(setting.key, true) }
        }
        SwitchPreference(setting, Defaults.PREF_SUGGEST_SCREENSHOTS,
            allowCheckedChange = {
                if (it && !granted) {
                    launcher.launch(permission)
                    false
                } else true
            }
        )
    },
    Setting(context, Settings.PREF_COMPRESS_SCREENSHOTS,
        R.string.compress_screenshots, R.string.compress_screenshots_summary
    ) {
        SwitchPreference(it, Defaults.PREF_COMPRESS_SCREENSHOTS)
    },
    /*
    Setting(context, Settings.PREF_AUTO_READ_OTP,
        R.string.auto_read_otp, R.string.auto_read_otp_summary
    ) { setting ->
        val activity = LocalContext.current.getActivity() as? SettingsActivity ?: return@Setting
        var granted by remember { mutableStateOf(PermissionsUtil.isNotificationListenerEnabled(activity)) }
        var pendingOtpEnable by rememberSaveable { mutableStateOf(false) }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val currentGranted = PermissionsUtil.isNotificationListenerEnabled(activity)
                    granted = currentGranted
                    if (pendingOtpEnable && currentGranted) {
                        activity.prefs().edit { putBoolean(Settings.PREF_AUTO_READ_OTP, true) }
                        PermissionsUtil.updateNotificationListenerComponent(activity, true)
                        pendingOtpEnable = false
                    } else if (!currentGranted && activity.prefs().getBoolean(Settings.PREF_AUTO_READ_OTP, false)) {
                        activity.prefs().edit { putBoolean(Settings.PREF_AUTO_READ_OTP, false) }
                        PermissionsUtil.updateNotificationListenerComponent(activity, false)
                    }
                    activity.prefChanged()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        val description = if (!granted && activity.prefs().getBoolean(Settings.PREF_AUTO_READ_OTP, false)) {
            "${setting.description}\n\n⚠ Notification access required. Tap to grant permission."
        } else {
            setting.description
        }

        SwitchPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_AUTO_READ_OTP,
            description = description,
            allowCheckedChange = { enable ->
                if (enable) {
                    PermissionsUtil.updateNotificationListenerComponent(activity, true)
                    if (!PermissionsUtil.isNotificationListenerEnabled(activity)) {
                        pendingOtpEnable = true
                        PermissionsUtil.openNotificationListenerSettings(activity)
                        false
                    } else true
                } else {
                    PermissionsUtil.updateNotificationListenerComponent(activity, false)
                    true
                }
            },
            onCheckedChange = {
                PermissionsUtil.updateNotificationListenerComponent(activity, it)
                activity.prefChanged()
            }
        )
    },
    */
    Setting(context, Settings.PREF_INLINE_MATH_CALCULATION,
        R.string.pref_inline_calculator_suggestions, R.string.pref_inline_calculator_suggestions_summary
    ) {
        SwitchPreference(it, Defaults.PREF_INLINE_MATH_CALCULATION)
    },
    Setting(context, Settings.PREF_USE_APPS,
        R.string.use_apps_dict, R.string.use_apps_dict_summary
    ) { setting ->
        SwitchPreference(setting, Defaults.PREF_USE_APPS)
    },
    Setting(context, Settings.PREF_DISABLE_MULTI_WORD_SUGGESTIONS,
        R.string.disable_multi_word_suggestions_title, R.string.disable_multi_word_suggestions_summary
    ) {
        SwitchPreference(it, Defaults.PREF_DISABLE_MULTI_WORD_SUGGESTIONS)
    },
    Setting(
        context, Settings.PREF_SUGGEST_EMOJIS, R.string.suggest_emojis, R.string.suggest_emojis_summary
    ) {
        SwitchPreference(it, Defaults.PREF_SUGGEST_EMOJIS) {
            context.sendBroadcast(Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION))
        }
    },
    Setting(
        context, Settings.PREF_INLINE_EMOJI_SEARCH, R.string.inline_emoji_search, R.string.inline_emoji_search_summary) {
        SwitchPreferenceWithEmojiDictWarning(it, Defaults.PREF_INLINE_EMOJI_SEARCH)
    },
)

@Preview
@Composable
private fun PreferencePreview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            SuggestionsScreen { }
        }
    }
}
