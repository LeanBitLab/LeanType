### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## 🚀 LeanType 4.3.4-beta3

---

### 🐛 Fixed & Hardened
- **Handwriting Stops After Reopening the Keyboard (#851, #854)**: Handwriting worked only until the keyboard was closed, and then needed a forced restart (Settings → About → Restart keyboard) every time. Closing the keyboard released the recognizer, and loading it again failed because Android allows the plugin's native library to be loaded only once. The plugin loader is now kept, so handwriting keeps working across keyboard closes and reopens.
- **Handwriting Panel Closes After the First Word (#854)**: The first handwritten word after opening the keyboard switched back to the normal keyboard, because auto-capitalization turning off reloaded the letter layout under the panel. The panel now stays open, and the correct capitalization is applied when you leave handwriting.
- **"Tap to Download Model" With the Model Installed (#854)**: Right after the keyboard started, the check whether the handwriting model is downloaded could time out and report it as missing. The check is now retried before the download prompt is shown.
- **Chinese and Japanese Input After Switching Apps (#842, #855, #857)**: When Android freed the keyboard's memory (for example after switching apps), the CJK engine reloaded without its Pinyin library, and the keyboard fell back to a Chinese placeholder engine, also for Japanese. The engine now reloads cleanly, and it is kept when only Japanese is available on a device.
- **Extra Space Below the Keys (#856)**: The space reserved for the navigation bar is now measured on the screen instead of inside the keyboard window, so a keyboard window that already ends above the navigation bar (seen on Samsung) no longer gets it twice. The space is also checked again after the keyboard is rebuilt by a settings change such as a custom font or the toolbar mode, where extra space stayed until a key was tapped. If you saw this, please report back with an exported log.

---

### 🧹 Maintenance & Testing
- **Navigation Bar Values in the Exported Log (#856)**: When the space below the keys changes, the exported log now records the values it was computed from, including whether they came from the system or the keyboard's own navigation bar, so reports of wrong space can be matched to a device. The overlap test now uses screen coordinates and covers a window that ends above the navigation bar.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.4-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.4-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 6.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
