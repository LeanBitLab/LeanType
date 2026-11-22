### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## 🚀 LeanType 4.3.6-beta3

---

### 🐛 Fixed & Hardened
- **Sound Packs (#896)**: After choosing a sound pack, the first preview and the first key presses were sometimes silent because the sounds were still loading. The preview now plays as soon as the sound is ready, keys use the system click until then, and switching packs quickly no longer mixes or loses sounds. Sound toggles changed in settings apply right away.
- **Space Swipe at the Edge of the Text (#895)**: Moving the cursor with a space swipe past the start or end of the text could move the focus to another field. The swipe now stops at the edge of the text (part of #891).
- **AI Model Pickers (#886)**: A saved model that the provider no longer lists kept collapsing its row. It now stays in the list. The Gemini voice default moves from the retired `gemini-2.0-flash` to `gemini-flash-latest`.
- **Settings Navigation (#892)**: With several settings windows open, a tap on a search result or link could do nothing. Only the visible window handles it now.

---

### ✨ Added
- **Reorganised Settings (#887, #892)**: The main screen is grouped under Keyboard, Typing, Extras and System. Gesture Typing becomes Gestures & swipes, App Profiles moves to Advanced, Remember language per app to Languages, and custom currency and timestamp format to Preferences > Additional keys. Search long-press on AI and Translation settings opens their own screens (#873).
- **What's New Dialog (#894)**: After an update, the changelog opens once on the first settings visit. The support section moves to the top of Updates and the sponsor dialog gets a cleaner look.
- **Offline AI Chat Format (#888)**: Qwen and LFM2 models now get the chat format they expect, so they answer better and Qwen no longer spends its tokens thinking (unless "show thinking" is on). The log keeps only text lengths, never your text.

---

### 🧹 Maintenance & Testing
- **Strings (#893)**: Removed unused strings and the Malayalam UI translation. The Malayalam layout and spell checker stay.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.6-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.6-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 6.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
