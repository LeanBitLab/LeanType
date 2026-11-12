### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 LeanType 4.3.2-beta6

---

### 🐛 Fixed
- **Idle Battery, Main-Thread Looper & Animator Lifecycle**: Eliminated hidden main-thread Looper spins, background view animators, and idle voice service bindings when keyboard views (`ClipboardHistoryView`, `EmojiPalettesView`, `GifPickerView`, `OcrCameraView`, `SuggestionStripView`, `VoiceVisualizerView`) are hidden or detached. Made `VoiceSettingsScreen` plugin binding and status polling strictly lifecycle-aware, marshaled binder disconnects to the main thread, and respected keep-loaded settings during UI hide.
- **Voice Screen-On Auto-Release & Extended Duration**: Automatically releases keep-screen-on flags after prolonged voice silence to prevent battery drain, and adds 10-minute and unlimited recording duration options across all 30 languages.
- **CJK Pinyin Uppercase Preservation & Shift State (#787)**: Fixed Pinyin composition in `CjkInputManager` and `LatinIME` to preserve typed uppercase characters and active shift states instead of lowercasing letters prematurely when mixing acronyms or English words with Pinyin input.
- **Text Expander Image Reliability, MIME Gating & Exact Match (#784)**: Hardened image storage, normalization, async import, and missing-file fallback UX. Supported image shortcuts during immediate and separator expansion, gated expansion on host editor MIME type compatibility, loaded thumbnails asynchronously to prevent UI stutters, required exact matching for unprefixed and image shortcuts, preserved add/edit dialog state across configuration changes, and deduplicated/included `text_expander_images` in dictionary backup/restore archives.
- **Emoji Inline Search Pinning & Horizontal Result Scrolling**: Pinned the emoji inline search bar in place and introduced `EmojiTabStripScrollView` to allow smooth independent horizontal result scrolling without shifting the search input out of view.
- **Arabic Period Key Diacritics Hint (#786)**: Centered the diacritics hint cleanly on the Arabic period key and restricted it exclusively to alphabet mode.
- **Localization Cleanup**: Removed an empty auto-translated Estonian string resource entry (#784).

### 🧹 Maintenance & Testing
- **Repository & Release Link Updates**: Updated repository, release, and documentation URLs across `Constants.kt` and `LibrariesHubScreen.kt` from `HeliboardL` to `LeanType`.
- **Comprehensive Unit Test Suites**: Added unit test coverage for view animator and scroll lifecycle (`AnimatorAndScrollLifecycleTest.kt`), voice plugin lifecycle and duration options (`VoicePluginManagerTest.kt`), CJK Pinyin uppercase preservation (`CjkEngineTest.kt`), pinned emoji search layout (`EmojiSearchKeyboardTest.kt`), text expander backup/restore (`BackupPreferencesTest.kt`), and exact shortcut matching (`TextExpanderTabTest.kt`, `TextExpanderImageUtilsTest.kt`).

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.2-standard-debug.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.2-offline-debug.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
