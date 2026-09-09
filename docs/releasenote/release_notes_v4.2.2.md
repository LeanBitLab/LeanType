### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 What's New in v4.2.2

### ✨ Highlights
- **Auto-Correction Engine Restoration**: Fixed critical regression where words from the personal user dictionary were erroneously promoted with maximum scores and whitelist flags, causing everyday words in typed sentences to be randomly replaced with personal dictionary entries (e.g. replacing "how" with "huewail").
- **Accurate Contextual Gating**: Correctly constrained contextual bigram gating to next-word prediction candidates, preventing main dictionary bigram checks from improperly dampening user history during active word typing.
- **Suggestion Balance Preservation**: Restored standard spatial and edit-distance weighting for user dictionary candidates, ensuring user words respect user-selected suggestion balance levels without hijacking typed text.

### 🐛 Bug Fixes & Refinements
- **Personal Dictionary Whitelist Fix**: Eliminated inappropriate `KIND_WHITELIST` promotion on personal dictionary unigram candidates in `DictionaryFacilitatorImpl`.
- **Typing Score Alignment**: Properly isolated normalized JNI typing score ranges from empty-word next-word probability thresholds.
- **Test Suite Integrity**: Full pass across all 206 unit tests in core engine, dictionary, and suggest modules.

## 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Self-Updater |
|:---|:---|:---|:---|:---|:---|
| **`1-LeanType_4.2.2-standardfull-release.apk`** | **Convenience (Recommended)** | Cloud AI | In-app download or File import | Optional (AI/Updates/plugins) | ✅ In-App Auto Update |
| **`1-LeanType_4.2.2-standard-release.apk`** | **F-Droid** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ❌ None |
| **`2-LeanType_4.2.2-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
