### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 LeanType 4.3.2-beta7

---

### 🐛 Fixed
- **Multi-Row Popup Key Hitboxes**: Fixed inverted top and bottom edge flags in `PopupKeysKeyboard.Builder.markAsEdgeKey`, eliminating overlapping touch hitboxes across rows in multi-row popup panels such as the 2-row Arabic punctuation popup.

### ✨ Added
- **Text Expander Autocomplete Toggle**: Added an **Autocomplete shortcuts** setting under Text Expander (`PREF_PREFIX_AUTOCOMPLETE`, disabled by default) to optionally suggest partial matches for unprefixed text and image shortcuts after typing 2+ characters as well as prefixed image shortcuts, while keeping explicitly prefixed text shortcuts autocompleting normally.

### 🧪 Testing & Localization
- **Localization**: Added localized strings for the new Text Expander autocomplete toggle across 30 languages.
- **Unit Test Coverage**: Added regression and unit tests covering multi-row Arabic popup vertical hit detection (`PopupKeysKeyboardTest.kt`) and Text Expander prefix autocomplete toggle behavior (`TextExpanderTabTest.kt`).

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.2-standard-debug.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.2-offline-debug.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
