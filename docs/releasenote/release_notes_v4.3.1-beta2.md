### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 LeanType 4.3.1-beta2

---

### 🐛 Fixed
- **Dictionary Traverse Session Concurrency (SIGSEGV Fix) (#759)**: Synchronized native traverse session access during word suggestions in `BinaryDictionary` to prevent concurrent traversal race conditions and fatal native segmentation faults (`SIGSEGV`).
- **Samsung & Multi-Profile IME Crash Resolution**: Prevented fatal `RuntimeException` on Samsung devices (such as Galaxy S25 Ultra) and isolated environments (Knox, Secure Folder, Work Profiles, Direct Boot) where system `inputMethodList` excludes third-party IMEs, by replacing rigid equality checks with direct package matching and resilient `PackageManager` fallbacks.
- **Multilingual History Isolation & Learning (#763)**: Isolated learned and unlearned words to matching language dictionaries in multilingual setups instead of commingling vocabulary across unrelated language histories.
- **Multilingual Locale Priority Order (#762)**: Preserved primary locale dictionary lookup order using a linked set, ensuring main language suggestions take precedence during multilingual typing.
- **Android 7.1.2 Compatibility Crash (#756)**: Avoided `ThreadLocal.withInitial` in Thai word break iterator to prevent fatal crashes on Android 7.1.2 (API 25) and below.
- **InputConnection IPC Hardening & Memory Leak Prevention**: Hardened `RichInputConnection` against dead-process and remote app IPC crashes, safely closed unclosed dictionary and asset input streams, and eliminated memory leak vectors.
- **Dynamic Dictionary Download & Deletion Reloads (#761)**: Broadcast dictionary update intents upon downloading or deleting user dictionaries, ensuring the keyboard engine immediately refreshes active wordlists without requiring a restart.
- **Transient Shift State Reset on Input Restarts**: Cleared one-shot shift state on input restarts and text field switches to prevent inadvertent capitalization, while strictly preserving Caps Lock.
- **NULL Input Type Auto-Capitalization Guard**: Guarded `TYPE_NULL` input fields and invalid editor selections (`initialSelStart == -1 && initialSelEnd == -1`) from receiving auto-capitalization flags.
- **Synchronous Blacklist Enforcement**: Synchronously loads and reloads the blocked words blacklist to eliminate race conditions where blocked words could temporarily appear in suggestions, and resolved deletion persistence issues.
- **Autocorrect Default Threshold Preset Alignment (#760)**: Corrected default auto-correction threshold configuration to properly align with the Balanced preset.
- **N-Gram Context Hash Collision Fix**: Corrected hash collisions in `NgramContext` to ensure accurate next-word context lookups across multi-word prediction pipelines.
- **Missing Settings Menus & Sound Search Indexing**: Restored missing preference options across Text Correction, Toolbar, Gesture Typing, and Sound screens, and corrected sound settings indexing in search.

### ✨ Added
- **Clear Learned Words & User History**: Added a dedicated option in Text Correction / Suggestions settings to reset learned words, personalized dictionary history, and session word boost caches.
- **Irish & Group 1 Latin Input Subtypes**: Added input subtypes in `method.xml` for Irish (`ga`, `ga_IE`), Bosnian (`bs`, `bs_BA`), Norwegian Nynorsk (`nn`, `nn_NO`), Cebuano (`ceb`), Malagasy (`mg`), Sardinian (`sc`), and Toki Pona (`tok`).

### ⚡ Performance
- **Draw-Loop IPC Elimination & Zero Key Allocations**: Removed Binder IPC queries from active keyboard canvas draw routines, eliminated temporary object allocations per key draw, and optimized gesture trail invalidation for smooth 60fps rendering.

### 🧹 Maintenance & Tests
- **Plugin Update Deduplication & Packaging**: Consolidated plugin update checks into a centralized `PluginUpdateHelper` and hardened build scripts for F-Droid reproducibility.
- **Resource & Build Hygiene**: Pruned orphaned translation strings, removed redundant ProGuard rules, and removed unused legacy split keyboard configuration references (#751).
- **Shift State Unit Test Suite**: Added dedicated unit tests in `KeyboardStateShiftTest` covering shift state clearing and caps lock persistence across input restarts.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.1-standard-debug.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.1-offline-debug.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
