### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 What's New in v4.2.1

### ✨ Highlights
- **100% Kotlin Migration**: Complete migration of the entire Android application layer from Java to idiomatic Kotlin. 0 Java files remain across the codebase, greatly improving type safety and modern language interoperability.
- **Key & Functional Corner Radius Customization**: Added independent user-configurable corner radius sliders for normal and functional keys in Appearance settings when key borders are active, with full theme-default preservation.
- **Suggestion Engine Intelligence**: Enhanced word ranking with user dictionary authority, smart contextual bigram gating to prevent unigram overrides, and instant session typo eviction on backspace.
- **Fast OCR Scanning**: Defaulted offline OCR recognition script to Latin for significantly faster single-language scanning, with reordered script picker and improved camera lifecycle.
- **Null-Safety & Interop Hardening**: Eliminated over 425 force-unwrap (`!!`) assertions across input logic, suggestions, layout, and settings. Stripped 920+ obsolete `@JvmField` and `@JvmStatic` interop annotations.

### 🐛 Bug Fixes & Refinements
- **Selection Deletion & Auto-Caps Restoration**: Fixed input connection text cache desynchronization when deleting text selections, and restored correct sentence-boundary auto-capitalization after backspacing to punctuation (#448).
- **Unwanted Mid-Sentence Auto-Caps**: Suppressed accidental auto-capitalization of intentional lowercase words mid-sentence (e.g., `in`, `ok`, `id`) while keeping suggestion strip options active (#482).
- **Emoji Surrogate Safety**: Guarded cursor navigation and backspace deletion against splitting multi-byte emoji surrogate pairs (#423).
- **OCR Viewfinder Lifecycle & Back Press**: Cleanly dismissed and reset camera viewfinder layout dimensions on window hide, and enabled back key support to exit OCR scanning into the keyboard (#487).
- **Foldable Screen Layout**: Resolved key layout sizing on folded outer/cover screens of foldable devices by prioritizing available display width over smallest screen width (#400).
- **Hardware Keyboard Support**: Fixed crash on Ctrl+Space / Shift+Space language switching and guarded code point conversions for external physical keyboards (#444).
- **Clipboard Toolbar Sync**: Ensured the toolbar clipboard suggestion chip refreshes immediately after copying text (#493).
- **AI Rate Limiting & Overload Handling**: Added clear user-friendly error messages for HTTP 429 (quota limit) and HTTP 503 (server overloaded) during cloud AI proofreading (#492).
- **Android <= 8.1 Voice Input Compatibility**: Guarded `requestShowSelf` API calls with an API 28 check to eliminate `IllegalAccessError` crashes on Android Oreo and older.
- **Plugins Hub Polish**: Refined OCR plugin status summary in the Plugins settings hub to display clean Active/Inactive state.
- **Offline AI Settings Scope**: Scoped the offline AI settings configuration strictly to the offline flavor and refined main AI preference screens.
- **Test Suite Integrity**: Verified full pass across all 206 unit tests in core engine, dictionary, and suggest modules.
- **Documentation & Links**: Fixed legacy repository URLs across community links and updated repository metadata.

## 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Self-Updater |
|:---|:---|:---|:---|:---|:---|
| **`1-LeanType_4.2.1-standardfull-release.apk`** | **Convenience (Recommended)** | Cloud AI | In-app download or File import | Optional (AI/Updates/plugins) | ✅ In-App Auto Update |
| **`1-LeanType_4.2.1-standard-release.apk`** | **F-Droid** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ❌ None |
| **`2-LeanType_4.2.1-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
