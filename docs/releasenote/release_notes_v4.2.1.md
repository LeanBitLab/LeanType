### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 What's New in v4.2.1

### ✨ Highlights
- **100% Kotlin Migration**: Complete migration of the entire Android application layer from Java to idiomatic Kotlin. 0 Java files remain across the codebase, greatly improving type safety and modern language interoperability.
- **Null-Safety & Interop Hardening**: Eliminated over 425 force-unwrap (`!!`) assertions across input logic, suggestions, layout, and settings. Stripped 920+ obsolete `@JvmField` and `@JvmStatic` interop annotations.
- **R8 Minification & 60% Smaller Release APK**: Hardened ProGuard and R8 JNI keep rules for release builds, shrinking the release APK footprint from 28MB down to **11MB** (a 60.7% binary reduction).

### 🐛 Bug Fixes & Refinements
- **Offline AI Settings Scope**: Scoped the offline AI settings configuration strictly to the offline flavor and refined main AI preference screens.
- **Test Suite Integrity**: Verified full pass across all 206 unit tests in core engine, dictionary, and suggest modules.
- **Documentation & Links**: Fixed legacy repository URLs across community links and updated repository metadata.

## 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Self-Updater |
|:---|:---|:---|:---|:---|:---|
| **`1-LeanType_4.2.1-standardfull-release.apk`** | **Convenience (Recommended)** | Cloud AI | In-app download or File import | Optional (AI/Updates/plugins) | ✅ In-App Auto Update |
| **`1-LeanType_4.2.1-standard-release.apk`** | **F-Droid** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ❌ None |
| **`2-LeanType_4.2.1-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 5.0+), **Offline Translation** (Android 7.0+), **Offline Handwriting Recognition** (Android 8.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+) via modular plugins, and work 100% offline.
