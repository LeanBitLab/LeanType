### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## 🚀 LeanType 4.3.6-beta2

---

### 🐛 Fixed & Hardened
- **Caps Lock on PC QWERTY (#883)**: With caps lock on, the number row typed `!@#$` and keys like `-` `=` `[` `;` typed their shifted symbols. Caps lock now only capitalizes letters, like a physical keyboard. A single shift still gives the symbols, and they stay in the long-press popups.
- **Hebrew Symbols Page (#882)**: Since 4.2.9 the Hebrew symbols page used the Arabic layout, so `?` typed `؟` and `%` `@` `#` were swapped for Arabic signs. Hebrew is back to the normal symbols layout. Arabic, Farsi, Urdu and Kurdish still use Arabic symbols.
- **Delete Word Swipe While Typing (#878)**: With the full keyboard swipe set to "Delete word", swiping over a word you were still typing only finished it, and a second swipe was needed. One swipe now deletes it, and the suggestion strip no longer appends a prediction without a space.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.6-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.6-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 6.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
