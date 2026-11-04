### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 LeanType 4.3.1

---

### ✨ Added
- **Full Keyboard Swipe Delete Word Option (#757)**: Added "Delete word" as a selectable action for full keyboard horizontal swipe gestures under Gesture Typing settings. Allows swiping left or right across the keyboard to delete the preceding word, including active composing word reset, selection deletion, dictionary unlearning, and repeated DEL key events for web editor compatibility, with localized translations across all 30 languages.
- **Clear Learned Words & User History**: Added a dedicated, privacy-focused option in Text Correction / Suggestions settings to reset learned words, personalized dictionary history, and session word boost caches, complete with a security confirmation dialog and localized feedback toasts.
- **Irish & Group 1 Latin Input Subtypes**: Added standardized input subtypes in `method.xml` for Irish (`ga`, `ga_IE`), Bosnian (`bs`, `bs_BA`), Norwegian Nynorsk (`nn`, `nn_NO`), Cebuano (`ceb`), Malagasy (`mg`), Sardinian (`sc`), and Toki Pona (`tok`), backed by comprehensive unit tests.

### 🐛 Fixed
- **Dictionary Traverse Session Concurrency (SIGSEGV Fix) (#759)**: Synchronized native traverse session access during word suggestions in `BinaryDictionary` to prevent concurrent traversal race conditions between background suggestion lookups and rapid typing, eliminating fatal native segmentation faults (`SIGSEGV`).
- **Samsung & Multi-Profile IME Crash Resolution**: Replaced rigid `InputMethodInfo` equality checks with resilient package matching and `PackageManager` fallbacks, resolving fatal `RuntimeException` crashes on Samsung devices (such as Galaxy S25 Ultra) and isolated environments (Knox, Secure Folder, Work Profiles, Direct Boot) where system `inputMethodList` excludes third-party IMEs.
- **Multilingual History Isolation & Learning (#763)**: Isolated learned and forgotten words to matching language dictionaries in multilingual setups, preventing cross-language vocabulary pollution where words from one language were inadvertently learned into another language's personal dictionary history.
- **Multilingual Primary Locale Priority Order (#762)**: Preserved primary locale dictionary lookup order using a linked set in `DictionaryFacilitatorImpl`, guaranteeing that suggestions from the active primary language take precedence over secondary languages during multilingual input.
- **Android 7.1.2 Compatibility Crash (#756)**: Replaced `ThreadLocal.withInitial` (introduced in API 26 / Android 8.0) with an anonymous `ThreadLocal` subclass for the Thai word break iterator, eliminating `NoSuchMethodError` crashes on Android 7.1.2 (API 25) and below.
- **Voice Silence Timeout & Recording Cleanup (#765)**: Adaptively tracks ambient noise RMS floor to ensure silence detection triggers reliably in noisy environments, automatically cancels empty sessions on initial silence timeout, and eliminates zombie recording states by ensuring `stopVoice()` is dispatched when audio loops terminate.
- **Dynamic Multiline Clipboard Editor & Cursor Navigation**: Rebuilt the inline clipboard editor toolbar to support dynamic multiline expansion (expanding height seamlessly from 1 line up to 4 lines with automatic text wrapping) without hiding action pill buttons, and integrated interactive caret positioning with blinking cursor via `InlineCursorTextView`.
- **Emoji Search Shift Auto-Reset & Caret Navigation**: Automatically reverts the Shift key to unshifted lowercase layout after typing a character in the emoji palette search bar (while preserving Caps Lock on double-tap or long-press), and added interactive caret positioning with a smooth blinking cursor via `InlineCursorTextView`.
- **GIF Picker Preview Frame Reload**: Fixed image swapping during GIF live preview long-presses in `GifPickerView`, ensuring subsequent previewed items correctly reload fresh media instead of displaying the first cached frame.
- **InputConnection IPC Hardening & Memory Leak Prevention**: Hardened `RichInputConnection` operations against dead-process and remote app IPC crashes, safely closed unclosed dictionary and asset input streams, and eliminated memory leak vectors.
- **Dynamic Dictionary Download & Deletion Reloads (#761)**: Broadcast dictionary update intents upon downloading or deleting user dictionaries, ensuring the keyboard engine immediately refreshes active wordlists without requiring an app restart or device reboot.
- **Transient Shift State Reset on Input Restarts**: Cleared one-shot shift state on input restarts and text field switches to prevent inadvertent capitalization, while strictly preserving Caps Lock.
- **NULL Input Type Auto-Capitalization Guard**: Guarded `TYPE_NULL` input fields and invalid editor selections (`initialSelStart == -1 && initialSelEnd == -1`) from receiving auto-capitalization flags.
- **Synchronous Blacklist Enforcement**: Synchronously loads and reloads the blocked words blacklist to eliminate race conditions where blocked words could temporarily appear in suggestions during asynchronous loading, and resolved deletion persistence issues.
- **Autocorrect Default Threshold Preset Alignment (#760)**: Corrected default auto-correction threshold configuration to properly align with the Balanced preset.
- **N-Gram Context Hash Collision Fix**: Corrected hash collisions in `NgramContext` to ensure accurate next-word context lookups across multi-word prediction pipelines.
- **Missing Settings Menus & Sound Search Indexing**: Restored missing preference options across Text Correction, Toolbar, Gesture Typing, and Sound screens, and corrected sound settings indexing in the global settings search bar.

### ⚡ Performance & Optimization
- **Draw-Loop IPC Elimination & Zero Key Allocations**: Removed Binder IPC queries from active keyboard canvas draw routines, eliminated temporary object allocations per key draw, and optimized gesture trail invalidation for smooth 60fps rendering.

### 🧹 Maintenance & Testing
- **Plugin Update Deduplication & Packaging**: Consolidated plugin update checks across settings screens into a centralized `PluginUpdateHelper` and hardened build scripts for F-Droid reproducibility.
- **Resource & Build Hygiene**: Pruned orphaned translation strings, removed redundant ProGuard rules, and removed unused legacy split keyboard configuration references (`config_default_split_keyboard`) (#751).
- **Unit Test Coverage Expansion**: Added dedicated unit test suites covering Irish and Latin input subtypes (`SubtypeTest.kt`), transient shift state resets and caps lock persistence (`KeyboardStateShiftTest.kt`), and emoji search shift behavior (`EmojiSearchKeyboardTest.kt`).

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.1-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | ~12.8 MB |
| **`2-LeanType_4.3.1-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | ~12.5 MB |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
