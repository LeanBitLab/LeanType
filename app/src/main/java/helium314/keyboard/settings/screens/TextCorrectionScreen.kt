// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.ExpandablePreference
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.previewDark
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun TextCorrectionScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val activity = ctx.getActivity() as? SettingsActivity
    val prefChanged = (activity?.prefChanged ?: MutableStateFlow(0)).collectAsState()

    val autocorrectEnabled = prefs.getBoolean(Settings.PREF_AUTO_CORRECTION, Defaults.PREF_AUTO_CORRECTION)
    val fineTuneAutocorrectExpanded = prefs.getBoolean(Settings.PREF_EXPAND_FINE_TUNE_AUTOCORRECT, Defaults.PREF_EXPAND_FINE_TUNE_AUTOCORRECT)

    val items = remember(autocorrectEnabled, fineTuneAutocorrectExpanded, prefChanged.value) {
        listOf(
            // Corrections
            R.string.settings_category_correction,
            Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE,
            Settings.PREF_AUTO_CORRECTION,
            if (autocorrectEnabled) Settings.PREF_AUTO_CORRECT_AGGRESSIVENESS else null,
            if (autocorrectEnabled) Settings.PREF_AUTO_CORRECT_TRIGGER else null,
            if (autocorrectEnabled) Settings.PREF_BACKSPACE_REVERTS_AUTOCORRECT else null,
            Settings.PREF_BACKSPACE_REPEAT_DELETES_WORDS,
            if (autocorrectEnabled) Settings.PREF_EXPAND_FINE_TUNE_AUTOCORRECT else null,
            if (autocorrectEnabled && fineTuneAutocorrectExpanded) Settings.PREF_AUTO_CORRECT_THRESHOLD else null,
            if (autocorrectEnabled && fineTuneAutocorrectExpanded) Settings.PREF_MORE_AUTO_CORRECTION else null,
            if (autocorrectEnabled && fineTuneAutocorrectExpanded) Settings.PREF_AUTOCORRECT_SHORTCUTS else null,

            // Capitalization
            R.string.auto_cap,
            Settings.PREF_AUTO_CAP,
            Settings.PREF_FORCE_AUTO_CAPS,
            Settings.PREF_SHIFT_RECAPITALIZES_WORD,

            // Space
            R.string.settings_category_space,
            Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD,
            Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION,
            Settings.PREF_AUTOSPACE_AFTER_EMOJI,
            Settings.PREF_AUTOSPACE_AFTER_SUGGESTION,
            Settings.PREF_SHIFT_REMOVES_AUTOSPACE,
            Settings.PREF_PRESERVE_SPACE_BEFORE_PUNCTUATION,

            // Switch keyboard after
            R.string.switch_keyboard_after,
            Settings.PREF_ABC_AFTER_SYMBOL_SPACE,
            Settings.PREF_ABC_AFTER_NUMPAD_SPACE,
            Settings.PREF_ABC_AFTER_EMOJI,
            Settings.PREF_ABC_AFTER_CLIP,

            // Typing heatmap
            R.string.typing_heatmap_category,
            Settings.PREF_ADAPT_TO_TYPING,
            SettingsWithoutKey.SCREEN_NAV_TYPING_HEATMAP
        )
    }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_correction),
        settings = items
    )
}

fun createCorrectionSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_ADAPT_TO_TYPING, R.string.adapt_to_typing, R.string.adapt_to_typing_summary) {
        SwitchPreference(it, Defaults.PREF_ADAPT_TO_TYPING)
    },

    Setting(context, Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE,
        R.string.prefs_block_potentially_offensive_title, R.string.prefs_block_potentially_offensive_summary
    ) {
        SwitchPreference(it, Defaults.PREF_BLOCK_POTENTIALLY_OFFENSIVE)
    },
    Setting(context, Settings.PREF_AUTO_CORRECTION,
        R.string.autocorrect, R.string.auto_correction_summary
    ) {
        SwitchPreference(it, Defaults.PREF_AUTO_CORRECTION)
    },
    Setting(context, Settings.PREF_AUTO_CORRECT_AGGRESSIVENESS,
        R.string.auto_correct_aggressiveness_title
    ) { setting ->
        val prefs = LocalContext.current.prefs()
        val defaultLevel = Settings.readAutoCorrectAggressiveness(prefs)
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = defaultLevel,
            range = 1f..4f,
            stepSize = 1,
            onConfirmed = { value ->
                Settings.applyAutoCorrectAggressivenessPreset(prefs, value.toInt())
            },
            description = { value ->
                when (value.toInt()) {
                    Settings.AUTO_CORRECT_LEVEL_MODEST -> stringResource(R.string.auto_correct_aggressiveness_desc_1)
                    Settings.AUTO_CORRECT_LEVEL_AGGRESSIVE -> stringResource(R.string.auto_correct_aggressiveness_desc_3)
                    Settings.AUTO_CORRECT_LEVEL_VERY_AGGRESSIVE -> stringResource(R.string.auto_correct_aggressiveness_desc_4)
                    else -> stringResource(R.string.auto_correct_aggressiveness_desc_2)
                }
            }
        )
    },
    Setting(context, Settings.PREF_EXPAND_FINE_TUNE_AUTOCORRECT,
        R.string.fine_tune_autocorrect_title, R.string.fine_tune_autocorrect_summary
    ) {
        ExpandablePreference(it, Defaults.PREF_EXPAND_FINE_TUNE_AUTOCORRECT)
    },
    Setting(context, Settings.PREF_AUTO_CORRECT_TRIGGER, R.string.auto_correction_trigger) {
        val items = listOf(
            stringResource(R.string.auto_correction_trigger_both) to "both",
            stringResource(R.string.auto_correction_trigger_space) to "space",
            stringResource(R.string.auto_correction_trigger_punctuation) to "punctuation",
        )
        ListPreference(it, items, Defaults.PREF_AUTO_CORRECT_TRIGGER)
    },
    Setting(context, Settings.PREF_MORE_AUTO_CORRECTION,
        R.string.more_autocorrect, R.string.more_autocorrect_summary
    ) { setting ->
        val prefs = LocalContext.current.prefs()
        SwitchPreference(
            setting = setting,
            default = Defaults.PREF_MORE_AUTO_CORRECTION,
            onCheckedChange = { more ->
                val threshold = prefs.getFloat(Settings.PREF_AUTO_CORRECT_THRESHOLD, Defaults.PREF_AUTO_CORRECT_THRESHOLD)
                val level = when {
                    threshold < 0f -> Settings.AUTO_CORRECT_LEVEL_VERY_AGGRESSIVE
                    more -> Settings.AUTO_CORRECT_LEVEL_AGGRESSIVE
                    threshold >= 0.18f -> Settings.AUTO_CORRECT_LEVEL_MODEST
                    else -> Settings.AUTO_CORRECT_LEVEL_BALANCED
                }
                prefs.edit { putInt(Settings.PREF_AUTO_CORRECT_AGGRESSIVENESS, level) }
            }
        )
    },
    Setting(context, Settings.PREF_AUTOCORRECT_SHORTCUTS,
        R.string.auto_correct_shortcuts, R.string.auto_correct_shortcuts_summary
    ) {
        SwitchPreference(it, Defaults.PREF_AUTOCORRECT_SHORTCUTS)
    },
    Setting(context, Settings.PREF_AUTO_CORRECT_THRESHOLD, R.string.auto_correction_confidence) { setting ->
        val items = listOf(
            stringResource(R.string.auto_correction_threshold_mode_modest) to 0.185f,
            stringResource(R.string.auto_correction_threshold_mode_aggressive) to 0.067f,
            stringResource(R.string.auto_correction_threshold_mode_very_aggressive) to -1f,
        )
        val prefs = LocalContext.current.prefs()
        ListPreference(
            setting = setting,
            items = items,
            default = Defaults.PREF_AUTO_CORRECT_THRESHOLD,
            onChanged = { threshold ->
                val more = prefs.getBoolean(Settings.PREF_MORE_AUTO_CORRECTION, Defaults.PREF_MORE_AUTO_CORRECTION)
                val level = when {
                    threshold < 0f -> Settings.AUTO_CORRECT_LEVEL_VERY_AGGRESSIVE
                    more -> Settings.AUTO_CORRECT_LEVEL_AGGRESSIVE
                    threshold >= 0.18f -> Settings.AUTO_CORRECT_LEVEL_MODEST
                    else -> Settings.AUTO_CORRECT_LEVEL_BALANCED
                }
                prefs.edit { putInt(Settings.PREF_AUTO_CORRECT_AGGRESSIVENESS, level) }
            }
        )
    },
    Setting(context, Settings.PREF_BACKSPACE_REVERTS_AUTOCORRECT, R.string.backspace_reverts_autocorrect) {
        SwitchPreference(it, Defaults.PREF_BACKSPACE_REVERTS_AUTOCORRECT)
    },
    Setting(context, Settings.PREF_BACKSPACE_REPEAT_DELETES_WORDS,
        R.string.backspace_repeat_deletes_words, R.string.backspace_repeat_deletes_words_summary
    ) {
        SwitchPreference(it, Defaults.PREF_BACKSPACE_REPEAT_DELETES_WORDS)
    },
    Setting(context, Settings.PREF_AUTO_CAP,
        R.string.auto_cap, R.string.auto_cap_summary
    ) {
        SwitchPreference(it, Defaults.PREF_AUTO_CAP)
    },
    Setting(context, Settings.PREF_FORCE_AUTO_CAPS, R.string.force_auto_caps_title, R.string.force_auto_caps_summary) {
        SwitchPreference(it, Defaults.PREF_FORCE_AUTO_CAPS)
    },
    Setting(context, Settings.PREF_SHIFT_RECAPITALIZES_WORD,
        R.string.shift_recapitalizes_word, R.string.shift_recapitalizes_word_summary
    ) {
        SwitchPreference(it, Defaults.PREF_SHIFT_RECAPITALIZES_WORD)
    },
    Setting(context, Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD,
        R.string.use_double_space_period, R.string.use_double_space_period_summary
    ) {
        SwitchPreference(it, Defaults.PREF_KEY_USE_DOUBLE_SPACE_PERIOD)
    },
    Setting(context, Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION,
        R.string.autospace_after_punctuation, R.string.autospace_after_punctuation_summary
    ) {
        SwitchPreference(it, Defaults.PREF_AUTOSPACE_AFTER_PUNCTUATION)
    },
    Setting(context, Settings.PREF_AUTOSPACE_AFTER_EMOJI,
        R.string.autospace_after_emoji, R.string.autospace_after_emoji_summary
    ) {
        SwitchPreference(it, Defaults.PREF_AUTOSPACE_AFTER_EMOJI)
    },
    Setting(context, Settings.PREF_AUTOSPACE_AFTER_SUGGESTION, R.string.autospace_after_suggestion) {
        SwitchPreference(it, Defaults.PREF_AUTOSPACE_AFTER_SUGGESTION)
    },
    Setting(context, Settings.PREF_IMMEDIATE_AUTO_SPACE, R.string.immediate_auto_space, R.string.immediate_auto_space_summary) {
        SwitchPreference(it, Defaults.PREF_IMMEDIATE_AUTO_SPACE)
    },
    Setting(context, Settings.PREF_SHIFT_REMOVES_AUTOSPACE, R.string.shift_removes_autospace, R.string.shift_removes_autospace_summary) {
        SwitchPreference(it, Defaults.PREF_SHIFT_REMOVES_AUTOSPACE)
    },
    Setting(context, Settings.PREF_PRESERVE_SPACE_BEFORE_PUNCTUATION, R.string.preserve_space_before_punctuation, R.string.preserve_space_before_punctuation_summary) {
        SwitchPreference(it, Defaults.PREF_PRESERVE_SPACE_BEFORE_PUNCTUATION)
    },
    Setting(context, Settings.PREF_ABC_AFTER_SYMBOL_SPACE,
        R.string.switch_keyboard_after, R.string.after_symbol_and_space)
    {
        SwitchPreference(it, Defaults.PREF_ABC_AFTER_SYMBOL_SPACE)
    },
    Setting(context, Settings.PREF_ABC_AFTER_NUMPAD_SPACE,
        R.string.switch_keyboard_after, R.string.after_numpad_and_space)
    {
        SwitchPreference(it, Defaults.PREF_ABC_AFTER_NUMPAD_SPACE)
    },
    Setting(context, Settings.PREF_ABC_AFTER_EMOJI, R.string.switch_keyboard_after, R.string.after_emoji) {
        SwitchPreference(it, Defaults.PREF_ABC_AFTER_EMOJI)
    },
    Setting(context, Settings.PREF_ABC_AFTER_CLIP, R.string.switch_keyboard_after, R.string.after_clip) {
        SwitchPreference(it, Defaults.PREF_ABC_AFTER_CLIP)
    },
)

@Preview
@Composable
private fun PreferencePreview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            TextCorrectionScreen { }
        }
    }
}
