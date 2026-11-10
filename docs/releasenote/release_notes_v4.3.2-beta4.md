### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 LeanType 4.3.2-beta4

---

### ✨ Added
- **Text Expander Image & Photo Shortcuts (#784)**: Added photo and image attachment support to Text Expander shortcuts. Renders live image thumbnails and labels in the suggestion strip styled as rounded pill chips, directly inserts images via `InputConnectionCompat.commitContent` with seamless fallback to the Android system share sheet, and stores images securely in private internal storage for 100% offline availability.
- **Elevated Active Keyboards on Language Screen**: Redesigned the Languages settings screen to elevate all active keyboards to the top of the list, displaying primary layout names, subtype tags, and direct layout switching for simplified multi-layout management.
- **Always Reopen on Main Alphabet Keyboard**: Added an option in settings to always reopen the keyboard on the primary alphabet view, automatically resetting secondary symbol, numpad, or emoji states when the keyboard is dismissed and reopened.
- **Auto-Correct Underline Indicator Toggle**: Added a toggle in Text Correction settings to enable or disable the indicator underline displayed beneath auto-corrected words.

### 🐛 Fixed & Polished
- **Text Expander Thumbnail Caching & Eviction**: Pre-warms image thumbnails on a background executor, caches decoded bitmaps in memory, and evicts all dimension variants from the cache when shortcuts are deleted or edited.
- **Memory & Bitmap Optimization**: Uses exact downsampling (`inSampleSize`) when decoding full-resolution photos, hooks into `onTrimMemory` to release bitmap caches under memory pressure, and purges orphaned image files when shortcuts are deleted.
- **Text Expander Card UI Polish**: Arranged shortcut prefix and name vertically for better readability on narrow screens, and replaced oversized action buttons with streamlined icon buttons.
- **Suggestion Strip Image Layout**: Centered image thumbnails in suggestion chips with rounded borders to match clipboard strip styling.

### 🧹 Maintenance & Testing
- **Unit Test Coverage Expansion**: Added unit tests covering image shortcut insertion and URI parsing in `TextExpanderImageUtilsTest.kt`, plain text parsing in `TextExpanderTextTest.kt`, and alphabet keyboard reopening state in `KeyboardStateShiftTest.kt`.
- **Global Translations**: Added 100% complete translation coverage across all 30 languages for newly added image shortcut settings, language screen headers, and keyboard reopening toggles.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.2-standard-debug.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.2-offline-debug.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
