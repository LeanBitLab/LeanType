### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## 🚀 LeanType 4.3.4-beta2

---

### 🐛 Fixed & Hardened
- **Split Keyboard on Regular Phones (#850)**: Since 4.3.2, a regular phone with a small display size setting (500 dp or more in width and height) was treated as a large screen, which turns the split keyboard on by default. The normal "Enable split keyboard" switch had no effect there. Such phones now stay on the compact layout, while unfolded foldables (for example 588x706 dp) still get the large layout. If your phone was showing a split keyboard since 4.3.2, please report back whether it is gone.
- **Update Indicator (#849)**: The update dot and the plugin update state were saved by the last check. The dot stayed on after a plugin was updated, and a plugin installed after the last check showed nothing until you tapped Check for updates. The state is now worked out from the latest known version and the installed version every time, and a check looks up all plugins, not only the installed ones. A release published after the last automatic check (every 7 days by default) still needs the next check or a manual one.
- **Clipboard Action Menu Colors (#848)**: The pin, edit, delete and select menu after a long press on a clipboard entry was drawn white even on mid-tone themes with light icons, so the icons could not be seen. The menu now uses the toolbar color whenever the icons are light.

---

### 🧹 Maintenance & Testing
- **Device Info in the Exported Log (#850)**: The startup lines of the exported log now include the device model, screen size in dp and density, and the screen profile line is part of the log, so reports can be matched to a screen. A new test covers a phone with a small display size.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.4-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.4-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 6.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
