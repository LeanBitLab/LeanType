### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 LeanType 4.3.2-beta2

---

### 🐛 Fixed
- **Foldable & Tablet Unfolded Split Detection (#778)**: Adjusted dimension thresholds in `ScreenProfile` so book-style foldables (e.g. Galaxy Z Fold, Pixel Fold) in portrait orientation correctly detect `ScreenProfile.LARGE` even when `smallestScreenWidthDp` reports outer-screen metrics (360-400dp). Decoupled unfolded split keyboard settings (`PREF_ENABLE_SPLIT_KEYBOARD_UNFOLDED`) from compact split preferences so enabling/disabling split mode on the outer screen never clobbers inner screen preferences.
- **Shift & Auto-Caps Reload Desynchronization (#598)**: Prevented shift state desynchronization during keyboard reloads and unshifting. Calling `setShiftLocked(false)` preserves active auto-caps modes in `AlphabetShiftState`, auto-caps is properly retained across `KeyboardState.onRestoreKeyboardState` cache reloads, and `isDisplayedAlphabetShifted` layout inspection enforces unshift visual switches when displayed layouts are shifted.
- **Style Preview Chaining & Text Reversion**: Reverts existing styled Unicode characters back to plain text before applying new styles in `UnicodeStyleEngine.transformText`, allowing consecutive style previews and switches without corrupted or chained Unicode transformations.
- **Live Typing Isolation & Leaking Guard**: Protected password, URL, email, phone, and non-text input fields from live font styling in `InputLogic`, automatically resets active live styling when switching application packages in `LatinIME` to prevent leaking decorative styles into system searches or terminal apps, and isolates user history dictionaries (`UserHistoryDictionary`, `TextExpander`) from styled characters.
- **Backspace Unicode Surrogate Handling**: Correctly handles Unicode supplementary characters (surrogate pairs) and combining diacritics during backspace deletion in `InputLogic`, preventing surrogate tearing or orphaned combining marks.
- **Toolbar Style Icon & Contrast Polish**: Replaced style icon with a clean outlined 'A', fixed deactivation tinting on custom themes, and improved contrast calculations across light and dark theme palettes.

### ✨ Added
- **Unicode Font Style Engine & Stylizer**: Added complete typographic Unicode styling engine with 26+ canonical font styles (Bold, Italic, Bold Italic, Script, Script Bold, Fraktur, Fraktur Bold, Double-Struck, Sans-Serif, Sans Bold, Sans Italic, Monospace, Fullwidth, Small Caps, Circled, Squared, Inverted, Strikethrough, Underline, Slash, Bubble, etc.) featuring instant live typing transformation, gesture typing support, and plain text normalization.
- **Interactive Style Preview Strip**: Replaces the suggestion strip with an interactive preview strip (`StylePreviewStripView`) when font styles are active. Features live candidate previews, a persistent "Normal" plain text toggle chip, long-press to reset styles via `KeyCode.RESET_FONT_STYLE`, and smooth horizontal scrolling.
- **Topic-Categorized Unicode Symbol Palette (#776)**: Added a comprehensive Unicode symbol palette tab to the emoji view, categorized by topics (Arrows, Math, Typographic, Geometric, Stars, Weather, Music, Technical, Currency, etc.) loaded from `UNICODE_SYMBOLS.txt`.
- **Horizontally Scrollable Emoji Category Bar**: Replaced the fixed emoji category strip with a horizontally scrollable bar in `EmojiPalettesView` to cleanly display all emoji categories and the new Unicode symbol palette across all display sizes.
- **Dedicated Font Style Settings Screen**: Added `StyleSettingsScreen` under Settings > Libraries & Tools with style previews, live typing toggles, and toolbar integration controls.
- **Toolbar Style & Unicode Actions**: Added dedicated toolbar actions and keycodes (`FONT_STYLE`, `UNICODE_PANEL`) for rapid one-tap access to font styles and the Unicode symbol palette.

### 🧪 Tests & Quality
- **ScreenProfile Foldable & Preference Unit Tests**: Added unit tests in `ScreenProfileTest.kt` verifying realistic portrait foldable unfolded dimensions, hinge sensor fallbacks, and decoupled split preferences.
- **KeyboardState Auto-Caps Preservation Tests**: Added tests in `KeyboardStateShiftTest.kt` covering auto-caps retention across keyboard reloads and displayed shift state enforcement.
- **UnicodeStyleEngine Unit Test Suite**: Added comprehensive test coverage in `UnicodeStyleEngineTest.kt` covering bidirectional text transformation, style chaining, plain text roundtrip reversion, and live gesture typing normalization.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.2-standard-debug.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.2-offline-debug.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
