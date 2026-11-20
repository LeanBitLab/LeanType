### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## 🚀 LeanType 4.3.6

---

### 🐛 Fixed & Hardened
- **Doubled Single Letters in Browsers (#880)**: In Chromium browsers such as Brave, typing a one-letter word and pressing space could double it ("a" became "aa"). Chromium turns a one-character commit into a key press that is inserted next to the word being typed instead of replacing it. LeanType now finishes the word in place in web fields, so the letter appears once.
- **Toolbar Under the Taskbar Buttons (#880)**: With a physical keyboard on tablets with a taskbar (seen on the OnePlus Pad 3), the toolbar-only bar sat under the keyboard's back button and handle. The space below the toolbar is now measured from where that bar really is on screen.

---

### ✨ Added
- **Wider Landscape Split**: The split keyboard in landscape now uses the maximum split distance by default, leaving more room in the middle. You can still adjust it with the split distance slider.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.6-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.6-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 6.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
