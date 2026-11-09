### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 LeanType 4.3.2-beta3

---

### 🐛 Fixed
- **Single-Line Text Field Auto-Correction**: Enabled auto-correction for standard single-line text inputs (messaging apps, SMS, notes, and search fields). Removed the strict multi-line flag requirement while strictly preserving protections for passwords, email addresses, URIs, and fields requesting no suggestions.
- **Lock-Screen & Keyguard Security Hardening**: Guarded sensitive keyboard actions and automation receivers while the device is locked (`isDeviceLocked`). Blocks clipboard access, paste operations, clipboard history clearing, settings access, IME switching, AI features, translation, and intent dispatch keys from keyguard/lock screens, and ignores external automation broadcast switching when locked or focused on password fields.
- **Floating Mode Split Keyboard Suppression**: Automatically suppresses split keyboard layouts when floating keyboard mode is active, preventing visual overlap and key positioning glitches.
- **Supplementary Plane KeyCode Truncation Guard**: Guarded `codePointToKeyEventCode` against int-to-char truncation for Unicode supplementary plane characters (code points 0x10000 and above).
- **Clipboard Search & Toolbar Alignment**: Canceling clipboard search via the close button or Back key now cleanly resets active query filters, tapping the search toolbar key clears active search filters, recycled clip viewholders correctly bind pinned icons and colors, and clipboard toolbar keys honor layout alignment settings.
- **Setup Wizard & Dimension Polish**: Fixed the height scale step checkmark in `WelcomeWizard` by verifying boolean preference values, added reactive triggers on height scale adjustments, and enhanced multi-slider preferences to default single-dimension controls to Portrait.
- **Vector Drawable Compatibility**: Fixed vector drawable inflation crashes on pre-Android 7 devices for full-screen editor action icons.
- **Browser Compatibility**: Added IronFox Nightly (`org.ironfox.nightly`) to browser detection logic.

### ✨ Added
- **Text Expander Suggestions & Suggestion-Only Mode (#774)**: Text Expander shortcuts are now surfaced as interactive suggestions in the suggestion strip. Added a dedicated "Suggestion only" mode in Text Expander settings, allowing users to expand shortcuts exclusively from suggestion chips rather than expanding automatically on space or punctuation.
- **Text Expander Full-Screen Bulk Editor**: Added a comprehensive full-screen editor to view, edit, import, and export all text shortcuts in human-readable plain text format (`=== shortcut ===` syntax). Includes real-time syntax error checking with exact line numbers, a change preview dialog detailing added, modified, and deleted items, and `%tab%` variable support for inserting tab characters.
- **Clipboard Clip Editing & Live Character Counter (#678)**: Rebuilt the inline clipboard clip editor with a live character counter and streamlined action buttons in `ClipboardHistoryView`.
- **Localized Number Row Popup Reordering & Toggles (#721)**: Added user controls in Preferences and Subtype screens to toggle and reorder localized numeral systems (such as Arabic-Indic, Eastern Arabic, Devanagari, and Bengali) in number row popups.
- **Skip Setup Wizard to Browse Settings (#768)**: Added the ability to skip the initial setup wizard to explore and customize settings prior to enabling the keyboard, with a setup banner on the main settings screen and wizard relaunch from About.
- **Restart Keyboard Process Option**: Added a dedicated "Restart keyboard" option in About settings to terminate the IME process and cleanly reload models, plugins, and background services, complete with a confirmation dialog.

### 🧹 Maintenance & Testing
- **Settings Simplification & Resource Pruning**: Removed redundant typography styles settings screen in favor of direct toolbar and strip controls, and simplified the main settings header title to "LeanType" across all 68 locales.
- **Build & Dependency Scoping**: Scoped JitPack repository resolution to `com.github.*` and resolved lint warnings, API compatibility issues, and deprecations across network, audio, haptic, and dictionary components.
- **Unit Test Coverage Expansion**: Added unit test suites covering single-line input attributes auto-correction (`InputAttributesTest.kt`) and text expander plain text parsing and tab substitution (`TextExpanderTextTest.kt`, `TextExpanderTabTest.kt`).

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.2-standard-debug.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.2-offline-debug.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
