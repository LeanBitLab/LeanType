### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 LeanType 4.3.1-beta2

---

### 🐛 Fixed
- **Android 7.1.2 Compatibility Crash (#756)**: Avoided `ThreadLocal.withInitial` in Thai word break iterator to prevent fatal crashes on Android 7.1.2 (API 25) and below.
- **Transient Shift State Reset on Input Restarts**: Cleared one-shot shift state on input restarts and text field switches to prevent inadvertent capitalization, while strictly preserving Caps Lock.
- **NULL Input Type Auto-Capitalization Guard**: Guarded `TYPE_NULL` input fields and invalid editor selections (`initialSelStart == -1 && initialSelEnd == -1`) from receiving auto-capitalization flags.

### ✨ Added
- **Irish & Group 1 Latin Input Subtypes**: Added input subtypes in `method.xml` for Irish (`ga`, `ga_IE`), Bosnian (`bs`, `bs_BA`), Norwegian Nynorsk (`nn`, `nn_NO`), Cebuano (`ceb`), Malagasy (`mg`), Sardinian (`sc`), and Toki Pona (`tok`).

### 🧪 Tests
- **Shift State Unit Test Suite**: Added dedicated unit tests in `KeyboardStateShiftTest` covering shift state clearing and caps lock persistence across input restarts.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.1-standard-debug.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.1-offline-debug.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
