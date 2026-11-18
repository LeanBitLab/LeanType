### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## 🚀 LeanType 4.3.5-beta1

---

### 🐛 Fixed & Hardened
- **Japanese Input Falling Back to Chinese (#842)**: On a physical keyboard, the CJK plugin engine was released on every app switch, and loading it again could fail to reopen its native library, so Japanese fell back to Chinese. The engine is now kept while you type and is only released under real memory pressure when no input is active. The plugin is also reused per install instead of being loaded again, and loading and releasing can no longer overlap. This could not be tested on a physical keyboard, so please report back if you were affected.
- **Tiny Floating Toolbar (#860)**: In the floating keyboard, the toolbar height was derived from the key height, so layouts with more rows (number row, PC layouts) got very small, hard to tap toolbar keys, especially in landscape. The toolbar now scales from the normal docked height, never smaller than 24dp and never taller than the docked toolbar.
- **One-Handed Mode Side and Controls**: One-handed mode could jump to the other side while typing and lose its side switch arrow. The side now stays in sync, the controls come back after leaving touchpad mode, and they sit above the navigation bar so the resize button is no longer hidden behind it.
- **Clipped Emojis**: Tall emojis could be cut off at the top by the row above. Emojis are now scaled to fit inside their key and centered.

---

### ✨ Added
- **Smoother One-Handed Resizing**: Dragging the resize button now shows a live preview and rebuilds the keyboard once when you lift your finger, instead of rebuilding it on every move.
- **One-Handed Mode Look**: The keyboard slides to the other side when switching sides, and the control buttons are now square keys styled like the toolbar keys.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.5-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.5-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 6.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
