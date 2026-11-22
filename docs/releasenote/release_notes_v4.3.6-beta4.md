### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## 🚀 LeanType 4.3.6-beta4

---

### ✨ Added
- **Language Packs and Lite Builds (#898, #899, #900)**: New English-only **Lite** APKs for Android 11+ are about 4 MB smaller. Other app languages come as language packs: download one in Languages & Layouts > App translation, or import it from a file in the offline flavor. Packs are built for each release in the LeanType-Language-Packs repo and match that exact build. The "App translation" row shows only when a pack is needed. The regular APKs keep all languages and stay the main download.
- **Eight Completed Languages (#897)**: Japanese, Korean, Vietnamese, Traditional Chinese, Persian, Bengali, Tamil and Malay translations are now complete, and missing TalkBack, language name and settings translations are filled in for the other languages.

---

### 🐛 Fixed & Hardened
- **Setup Title (#898)**: The space next to the app name in the setup screen title was missing in 25 languages and the title was empty in Russian.

---

### 🧹 Maintenance & Testing
- **App Languages (#897)**: Near-empty UI translations were dropped, and the app language picker lists only the languages with a real translation. Keyboard layouts, dictionaries and typing settings for those languages are unchanged.
- **Docs (#899)**: README and FEATURES.md follow the reorganised settings. FEATURES.md gets a settings map generated from the code, and CI flags outdated settings paths in the docs.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.6-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.6-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |
| **`1-LeanType_4.3.6-standard-lite-release.apk`** | **Lite** (English-only, **Android 11+**) | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.6-offline-lite-release.apk`** | **Offline Lite** (English-only, **Android 11+**) | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 🌍 **Lite builds** are the same app with English as the only built-in UI language. Other app languages come as downloadable language packs (Languages & Layouts > App translation). They install over the regular build of the same flavor. The regular builds keep all languages.

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 6.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
