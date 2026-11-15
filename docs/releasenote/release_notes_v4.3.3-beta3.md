### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## 🚀 LeanType 4.3.3-beta3

> ⚠️ **Offline flavor now requires Android 6.0+**: The `offline` flavor minimum is raised from Android 5.0 (SDK 21) to Android 6.0 (SDK 23), the same as `standard`. Android 5.0 and 5.1 devices can no longer install or update it.

---

### 🐛 Fixed & Hardened
- **Letter Size Regression (#814)**: Wide letters such as `w` and `m` (and `ш`, `щ` in Russian) no longer render smaller than the other letters. The key label width fit now only applies to multi-character labels on the floating keyboard.
- **Voice Strip with an Open Toolbar (#820)**: The voice animation stays visible when voice input is started from the expanded toolbar, instead of the toolbar covering it while text is typed.
- **Backspace Before Symbol Runs (#820)**: One backspace no longer deletes everything before the cursor when the text consists of circled letters, arrows, or math symbols.
- **Voice Key While Processing (#821)**: Tapping the voice key during processing no longer cancels the pending text. The cancel button on the strip still does.
- **Stray Words After Dictation (#821)**: "Transcribe while speaking" cuts the silence after the last speech from each piece and ignores pieces with only noise, so Whisper no longer invents trailing words.
- **Live Transcription Stability (#816)**: Late callbacks from a finished dictation can no longer affect a new one, and a piece that never finishes now times out instead of blocking the pieces after it.
- **Android 6 Compatibility (#817)**: Replaced calls that need API 23 or 24 in the settings search, the custom styles parser, and the input view, which could crash older devices.

### ✨ Added
- **Custom Text Styles (#815, #820)**: Add your own Unicode font styles from `Toolbar > Custom text styles`. Write one style per line as `Name:` followed by the styled A-Z, a-z and optionally 0-9 (52 or 62 characters). A pasted alphabet without a name is accepted, invisible paste characters are ignored, and a Features Guide button opens the documentation. Custom styles appear in the Font Style strip with live typing and undo.

### ⚡ Performance & Battery
- **Voice Plugin Binding (#819)**: The voice plugin is no longer left bound after a failed model import, and the idle unbind retries while the plugin is busy instead of giving up.

### 🧹 Maintenance & Testing
- **Offline Flavor minSdk 23 (#818)**: Raised the offline flavor minimum to Android 6.0, which also clears about 295 lint errors that only applied below API 23.
- **Release Verifier (#812)**: The release check and notes expect `-release.apk` names for beta builds.
- **Shorter Voice Toggle Text**: The "Transcribe while speaking" description is now a single short line.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.3-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.3-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 6.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
