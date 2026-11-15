### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## 🚀 LeanType 4.3.3-beta4

---

### 🐛 Fixed & Hardened
- **Extra Space Below the Keyboard (#828)**: On devices that report the navigation bar although the window already leaves room for it (seen on a Samsung Note 10 with Android 12), the gap below the keys was too large and came back after the first show. The keyboard now reserves only the part of the navigation bar it really overlaps. This could not be tested on that device, so please report back if you were affected.
- **Settings Search Results (#823)**: The expandable section headers (Swipe gestures, Prediction details, and so on) are no longer listed in the results, because opening them there did nothing and the settings inside them are listed themselves.
- **Readable Jump Highlight (#823)**: After a long press on a search result, the highlighted setting is a tint of the accent color, so the text stays readable in light and dark themes.

### ⚡ Performance & Battery
- **Faster Settings Screens (#823)**: Large settings screens such as Toolbar, Advanced and Appearance compose their settings as you scroll instead of all at once, which shortens the slowest frame when a screen opens.

### 🧹 Maintenance & Testing
- **Typed Words Kept Out of the Log (#825)**: The word added to the personal dictionary and the emoji search query are no longer written to the in-app log.
- **Dead OTP Code Removed (#827)**: The OTP auto-fill code that was disabled in 4.3.0 is removed, together with its strings and documentation. The calculator suggestion now has a plain "Close" label on its dismiss button.
- **Release Badges (#822)**: The download badge follows the latest stable release and the Get Pre-release button shows the current beta.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.3-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.3-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 6.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
