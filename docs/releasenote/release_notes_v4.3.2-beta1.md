### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 LeanType 4.3.2-beta1

---

### 🐛 Fixed
- **Fast Typing Duplicate Capitalization & Auto-Caps Race (#598, #743)**: Resolved capitalization timing bugs during rapid typing and multi-finger gestures. Evaluates pointer state via `hasOlderLetterKeyDown()` so held spacebars or sentence boundary punctuation do not prematurely unshift auto-caps, preserves shift-retention states across key detection cycles, allows case-insensitive character matching without fragile queue element lifetimes, and guards cursor capitalization calculations against asynchronous selection updates and empty text cache invalidations.
- **Layout Popup Keys Priority Over Auto-Generated Symbols (#772)**: Reordered default popup key priority (`POPUP_KEYS_LAYOUT` before `POPUP_KEYS_SYMBOLS`) and migrated legacy preferences so explicitly defined layout popups (such as Bengali 'খ' on 'ক') are directly accessible instead of being buried behind secondary mathematical and punctuation symbols.
- **Custom Theme Functional Keys & Spacebar Pressed Color (#771)**: Fixed pressed feedback colors on functional keys and spacebar in custom borderless color palettes, ensuring responsive and consistent visual feedback on keypress across all theme variations.

### ✨ Added
- **Automation Broadcast Receiver for Layout & Subtype Switching (#501)**: Added external broadcast intent support (`ACTION_SWITCH_SUBTYPE`, `ACTION_SWITCH_LAYOUT`, `ACTION_SWITCH_MODE`) for automation apps (Tasker, Automate, MacroDroid, ADB shell). Allows switching keyboard modes (`alpha`, `symbols`, `numpad`, `emoji`, `clipboard`, `text_edit`, `one_handed`), cycling subtypes sequentially (`next`/`prev` or step count), jumping to specific subtype indices, and matching target languages, locales, or layout names.
- **Bracket, Symbol & CJK Quote Auto-Pairing (#754)**: Added automatic closing symbol insertion in Text Correction settings for brackets `()`, `[]`, `{}`, `<>`, quotation marks, and CJK book and quotation marks (`《》`, `「」`, `『』`, `【】`). Includes cursor step-over when manually typing the matching closing mark and pair deletion when backspacing an empty pair, complete with localized settings strings across all 30 languages.
- **Plain Text Export for User Dictionaries (#431)**: Added plain text export functionality in Personal Dictionary settings, enabling users to export all user dictionaries or individual locale wordlists to standard `.txt` files compatible with third-party tools and backup utilities.
- **Suppress Repeating Auto-Correction Reverted by Backspace (#415)**: Integrated `RevertedCorrectionHistory` in the suggestion engine to remember corrections reverted by backspacing, preventing the keyboard from repeatedly re-applying the unwanted auto-correction on subsequent space presses in the same input session.
- **Prioritized Inline Autofill on Password Fields (#416)**: Elevated inline autofill credential chips prominently above password and account fields for immediate access during logins.

### 🧪 Tests & Quality
- **Shift & Auto-Caps Unit Test Suite**: Added a comprehensive Robolectric unit test suite (`KeyboardStateShiftTest.kt`) covering fast typing sequences, two-finger chording, and auto-caps unshift behaviors to prevent regressions in typing mechanics.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.2-standard-debug.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.2-offline-debug.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
