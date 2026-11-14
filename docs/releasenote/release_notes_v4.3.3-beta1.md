### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## 🚀 LeanType 4.3.3-beta1

---

### 🐛 Fixed & Hardened
- **GIF & Sticker Permission and Clipboard Fallback (#794)**: Explicitly grants `FLAG_GRANT_READ_URI_PERMISSION` to destination apps across all Android versions, and gracefully falls back to copying the image or sticker URI to the system clipboard with an informative toast when direct editor insertion is unsupported or rejected.
- **Voice Idle Battery & Screen-Off Hardening (#803)**: Automatically cancels active voice recording sessions, releases the screen-on wake lock, and unbinds background voice plugin services when the device screen turns off, eliminating idle battery drain.
- **Text Expander Symbol & Number Shortcuts (#796)**: Enables suggestion strip matching and expansion for shortcuts containing leading symbols or numbers (such as `@g` expanding to `@gmail.com`, `#todo`, and numerical prefixes) by accurately computing non-word trigger offsets against text before the cursor.
- **Custom Popup Key Symbol Fallback Suppression (#801)**: Prevents auto-generated positional symbol key overlays from overwriting or injecting duplicate symbol hints onto keys that already have explicitly configured custom popup keys.
- **Hexagonal Custom Layout Auto-Detection (#795)**: Automatically detects custom and modified hexagonal layouts containing `hexVariant` even if the layout name does not contain the literal "hex" keyword, with cached layout inspection to maintain fast keyboard initialization.

### 🧪 Testing
- **Unit Test Coverage**: Added targeted unit test coverage for symbol-prefixed shortcut suggestions and expansions in `TextExpanderTabTest.kt`.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.3-standard-debug.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.3-offline-debug.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
