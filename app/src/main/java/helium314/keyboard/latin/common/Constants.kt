// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.BuildConfig

object Links {
    const val DICTIONARY_URL = "https://codeberg.org/Helium314/aosp-dictionaries"
    const val DICTIONARY_DOWNLOAD_SUFFIX = "/raw/branch/main/"
    const val DICTIONARY_NORMAL_SUFFIX = "dictionaries/"
    const val DICTIONARY_EXPERIMENTAL_SUFFIX = "dictionaries_experimental/"
    const val DICTIONARY_EMOJI_CLDR_SUFFIX = "emoji_cldr_signal_dictionaries/"
    // LeanBitBoard fork repo
    const val GITHUB = "https://github.com/LeanBitLab/HeliboardL"
    const val LICENSE = "$GITHUB/blob/main/LICENSE"
    const val SPONSOR = "https://github.com/sponsors/LeanBitLab"
    const val OPEN_COLLECTIVE = "https://opencollective.com/leanbitlab-org"
    const val GITHUB_RELEASES_API = "https://api.github.com/repos/LeanBitLab/HeliboardL/releases/latest"
    const val GITHUB_RELEASES_PAGE = "https://github.com/LeanBitLab/HeliboardL/releases"
    const val FEATURES_URL = "$GITHUB/blob/main/docs/FEATURES.md"
    // Original HeliBoard wiki and community links
    const val ORIGINAL_GITHUB = "https://github.com/Helium314/HeliBoard"
    const val LAYOUT_WIKI_URL = "$ORIGINAL_GITHUB/wiki/2.-Layouts"
    const val WIKI_URL = "$ORIGINAL_GITHUB/wiki"
    const val CUSTOM_LAYOUTS = "$GITHUB/discussions/categories/custom-layout"
    const val CUSTOM_COLORS = "$GITHUB/discussions/categories/custom-colors"
    // Voice Plugin Links
    const val VOICE_PLUGIN_REPO = "https://github.com/LeanBitLab/LeanType-Voice-Plugin"
    const val VOICE_PLUGIN_RELEASES_API = "https://api.github.com/repos/LeanBitLab/LeanType-Voice-Plugin/releases/latest"
    // Social & Official Links
    const val OFFICIAL_SITE = "https://leanbitlab.github.io/LeanBitLab/"
    const val TELEGRAM = "https://t.me/LeanBitLab"
    const val REDDIT = "https://www.reddit.com/r/LeanBitLab_/"
    const val X_TWITTER = "https://x.com/LeanBitLab"
    const val YOUTUBE = "https://www.youtube.com/@LeanBitLab"
}

val combiningRange = 0x300..0x35b

object Constants {

    object Color {
        /**
         * The alpha value for fully opaque.
         */
        const val ALPHA_OPAQUE = 255
    }

    object ImeOption {
        /**
         * The private IME option used to indicate that no microphone should be shown for a given
         * text field. For instance, this is specified by the search dialog when the dialog is
         * already showing a voice search button.
         */
        const val NO_MICROPHONE = "noMicrophoneKey"

        /**
         * The private IME option used to suppress the floating gesture preview for a given text
         * field. This overrides the corresponding keyboard settings preference.
         * [helium314.keyboard.latin.settings.SettingsValues.mGestureFloatingPreviewTextEnabled]
         */
        const val NO_FLOATING_GESTURE_PREVIEW = "noGestureFloatingPreview"
    }

    object Subtype {
        /** The subtype mode used to indicate that the subtype is a keyboard. */
        const val KEYBOARD_MODE = "keyboard"

        object ExtraValue {
            /** Indicates that this subtype is capable of entering ASCII characters (not used, but recommended for Android 9 and older). */
            const val ASCII_CAPABLE = "AsciiCapable"

            /** Indicates that this subtype is enabled when the default subtype is not marked as ascii capable (used where?). */
            const val ENABLED_WHEN_DEFAULT_IS_NOT_ASCII_CAPABLE = "EnabledWhenDefaultIsNotAsciiCapable"

            /** Indicates that this subtype is capable of entering emoji characters (always set?). */
            const val EMOJI_CAPABLE = "EmojiCapable"

            /** Indicates that the subtype does not have a shift key */
            const val NO_SHIFT_KEY = "NoShiftKey"

            /** Indicates that for this subtype corrections should not be based on proximity of keys for when shifted */
            const val NO_SHIFT_PROXIMITY_CORRECTION = "NoShiftProximityCorrection"

            /**
             * The subtype extra value used to indicate that the display name of this subtype
             * contains a "%s" for printf-like replacement and it should be replaced by
             * this extra value.
             * This extra value is supported on JellyBean and later.
             */
            const val UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME = "UntranslatableReplacementStringInSubtypeName"

            /** Contains the layouts used by this subtype. This extra value is private to LatinIME.*/
            const val KEYBOARD_LAYOUT_SET = "KeyboardLayoutSet"

            /** Indicates that this subtype is an additional subtype that the user defined. This extra value is private to LatinIME. */
            const val IS_ADDITIONAL_SUBTYPE = "isAdditionalSubtype"

            /** The subtype extra value used to specify the combining rules. */
            const val COMBINING_RULES = "CombiningRules"

            /** Overrides the general popup order setting */
            const val POPUP_ORDER = "PopupOrder"

            /** Overrides the general hint order / priority setting */
            const val HINT_ORDER = "HintOrder"

            /** Language tags indicating enabled secondary locales */
            const val SECONDARY_LOCALES = "SecondaryLocales"

            /** Overrides the general "more popups" setting */
            const val MORE_POPUPS = "MorePopups"

            /** Overrides the general "localized number row" setting */
            const val LOCALIZED_NUMBER_ROW = "LocalizedNumberRow"
        }
    }

    /** Separators for use in extra values and preferences. Notably cannot be = and , as they are already used in extra values */
    object Separators {
        /** key-value separator (to be used in subtype extra values) */
        const val KV = ":"
        /** separator between entries that might be key-value pairs (to be used in subtype extra values) */
        const val ENTRY = "|"
        /** separator between sets of entries (to be used for storing data for additional subtypes) */
        const val SET = "§"
        /** separator for sets (to be used for storing multiple extra additional subtypes in prefs) */
        const val SETS = ";"
    }

    object TextUtils {
        /**
         * Capitalization mode for [android.text.TextUtils.getCapsMode]: don't capitalize
         * characters.
         */
        const val CAP_MODE_OFF = 0
    }

    const val NOT_A_CODE = -1
    const val NOT_A_CURSOR_POSITION = -1
    const val NOT_A_COORDINATE = -1
    const val SUGGESTION_STRIP_COORDINATE = -2
    const val EXTERNAL_KEYBOARD_COORDINATE = -4

    const val EDITOR_CONTENTS_CACHE_SIZE = 1024
    const val MAX_CHARACTERS_FOR_RECAPITALIZATION = 1024 * 100

    const val LONG_PRESS_MILLISECONDS = 200

    // Non-constant expression (BuildConfig.DEBUG check): preserves public static field access for Java callers
    val GET_SUGGESTED_WORDS_TIMEOUT: Int = if (BuildConfig.DEBUG) 500 else 200

    const val DELETE_ACCELERATE_AT = 20

    const val WORD_SEPARATOR = " "

    @JvmStatic
    fun isValidCoordinate(coordinate: Int): Boolean {
        return coordinate >= 0
    }

    const val CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER = 1

    const val CODE_ENTER = '\n'.code
    const val CODE_TAB = '\t'.code
    const val CODE_SPACE = ' '.code
    const val CODE_PERIOD = '.'.code
    const val CODE_COMMA = ','.code
    const val CODE_DASH = '-'.code
    const val CODE_SINGLE_QUOTE = '\''.code
    const val CODE_DOUBLE_QUOTE = '"'.code
    const val CODE_SLASH = '/'.code
    const val CODE_BACKSLASH = '\\'.code
    const val CODE_VERTICAL_BAR = '|'.code
    const val CODE_COMMERCIAL_AT = '@'.code
    const val CODE_PLUS = '+'.code
    const val CODE_PERCENT = '%'.code
    const val CODE_CLOSING_PARENTHESIS = ')'.code
    const val CODE_CLOSING_SQUARE_BRACKET = ']'.code
    const val CODE_CLOSING_CURLY_BRACKET = '}'.code
    const val CODE_CLOSING_ANGLE_BRACKET = '>'.code
    const val CODE_INVERTED_QUESTION_MARK = '¿'.code
    const val CODE_INVERTED_EXCLAMATION_MARK = '¡'.code
    const val CODE_GRAVE_ACCENT = '`'.code
    const val CODE_CIRCUMFLEX_ACCENT = '^'.code
    const val CODE_TILDE = '~'.code
    const val RECENTS_TEMPLATE_KEY_CODE_0 = 0x30
    const val RECENTS_TEMPLATE_KEY_CODE_1 = 0x31

    const val REGEXP_PERIOD = "\\."
    const val STRING_SPACE = " "

    @JvmStatic
    fun isLetterCode(code: Int): Boolean {
        return code >= CODE_SPACE
    }

    @JvmStatic
    fun printableCode(code: Int): String {
        return when (code) {
            KeyCode.SHIFT -> "shift"
            KeyCode.CAPS_LOCK -> "capslock"
            KeyCode.SYMBOL_ALPHA -> "symbol_alpha"
            KeyCode.ALPHA -> "alpha"
            KeyCode.SYMBOL -> "symbol"
            KeyCode.MULTIPLE_CODE_POINTS -> "text"
            KeyCode.DELETE -> "delete"
            KeyCode.SETTINGS -> "settings"
            KeyCode.VOICE_INPUT -> "shortcut"
            KeyCode.ACTION_NEXT -> "actionNext"
            KeyCode.ACTION_PREVIOUS -> "actionPrevious"
            KeyCode.LANGUAGE_SWITCH -> "languageSwitch"
            KeyCode.EMOJI -> "emoji"
            KeyCode.CLIPBOARD -> "clipboard"
            KeyCode.SHIFT_ENTER -> "shiftEnter"
            KeyCode.NOT_SPECIFIED -> "unspec"
            CODE_TAB -> "tab"
            CODE_ENTER -> "enter"
            CODE_SPACE -> "space"
            KeyCode.TOGGLE_ONE_HANDED_MODE -> "toggleOneHandedMode"
            KeyCode.SWITCH_ONE_HANDED_MODE -> "switchOneHandedMode"
            KeyCode.TOGGLE_FLOATING_KEYBOARD -> "toggleFloatingKeyboard"
            KeyCode.SPLIT_LAYOUT -> "splitLayout"
            KeyCode.NUMPAD -> "numpad"
            KeyCode.SWITCH_TO_USER_IME -> "switchToUserIme"
            else -> {
                if (code < CODE_SPACE) String.format("\\u%02X", code)
                else if (code < 0x100) String.format("%c", code)
                else if (code < 0x10000) String.format("\\u%04X", code)
                else String.format("\\U%05X", code)
            }
        }
    }

    const val SCREEN_METRICS_SMALL_PHONE = 0
    const val SCREEN_METRICS_LARGE_PHONE = 1
    const val SCREEN_METRICS_LARGE_TABLET = 2
    const val SCREEN_METRICS_SMALL_TABLET = 3

    const val DEFAULT_GESTURE_POINTS_CAPACITY = 128

    const val MAX_IME_DECODER_RESULTS = 20
    const val DECODER_SCORE_SCALAR = 1000000
    const val DECODER_MAX_SCORE = 1000000000

    const val EVENT_BACKSPACE = 1
    const val EVENT_REJECTION = 2
    const val EVENT_REVERT = 3
}
