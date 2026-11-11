### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 LeanType 4.3.2-beta5

---

### 🐛 Fixed & Hardened
- **Voice Input Leak & Watchdog Timeouts**: Unconditionally terminates voice recording sessions and audio loops on keyboard window hide and input finish, adds watchdog timeouts for plugin connection (8s) and final processing (15s) to eliminate hung states, unbinds voice services when hidden or trimming memory, and binds services without foreground elevation.
- **Battery & Resource Drain Prevention**: Unconditionally releases the OCR camera preview on view detachment and keyboard dismissals, and eliminates a potential 100% CPU busy-spin loop in audio recording streams when read buffers report 0 bytes.
- **InputConnection IPC Hardening**: Defensively wrapped all critical `InputConnection` IPC calls across `RichInputConnection` with graceful fallback handling to prevent crashes caused by buggy or unresponsive host applications.
- **Window Leak & Process Lifecycle Fixes**: Automatically dismisses active dialog windows on keyboard destruction to prevent Android window manager memory leaks, and guards against uninitialized clipboard database access during shutdown.
- **Settings Safe Drawing Insets**: Reinstated full-screen safe drawing insets across Compose scaffolds in `SettingsActivity`, ensuring headers and action buttons never overlap the system status bar or camera cutouts on modern edge-to-edge Android displays.
- **SupervisorJob Coroutine Isolation**: Wrapped background coroutine scopes with `SupervisorJob` to isolate coroutine failures and prevent child task cancellations from tearing down root application scopes or leaking activity references.

### ⚡ Performance & Memory
- **Typing Hot Paths & Keyguard IPC Optimization**: Cached device lock status with a 2-second TTL in `IsLockedCompat` to eliminate expensive repeated `KeyguardManager` Binder IPC calls during fast typing, cached missing dictionary lookups in `ConcurrentHashMap`, and hoisted nearest key proximity calculations in `HexKeyDetector`.
- **Memory Trimming & Native Plugin Lifecycle**: Hooked handwriting, CJK, translation, and speech plugin unloads into `LatinIME.onTrimMemory` to immediately reclaim native heap under low-memory conditions, protected offline AI model unloading during active inference, and capped custom sound manager preview caches.
- **Asynchronous Clipboard Image Decoding**: Decodes clipboard image thumbnails asynchronously off the main thread with exact downsampling in `ClipboardAdapter`, eliminating UI freezes when browsing rich media clips.

### ✨ Added
- **Arabic Period Key Diacritics Hint (#786)**: Added a visual diacritics hint to the period key on Arabic keyboards, providing immediate discoverability and quick access to essential Arabic tashkeel marks.

### 🧹 Maintenance & Refactoring
- **String Resource Deduplication**: Consolidated common action strings (save, delete, cancel, add) and pruned redundant string resources across 107 localization files.
- **Settings Navigation Pipeline**: Streamlined the settings navigation channel and flattened scaffold hierarchies for smoother Compose screen transitions.
- **Unit Test Coverage**: Added targeted unit test suites covering Arabic period popup key hierarchies (`KeyboardParserTest.kt`) and voice provider routing timeouts (`VoiceProviderRoutingTest.kt`).

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.2-standard-debug.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.2-offline-debug.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
