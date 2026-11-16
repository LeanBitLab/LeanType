### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## 🚀 LeanType 4.3.4-beta1

---

### 🐛 Fixed & Hardened
- **Japanese Input Falling Back to Chinese (#844)**: With the CJK plugin, Japanese input could switch to Chinese Pinyin after switching to another app, until the keyboard was restarted. The keyboard remembered which language it had sent to the CJK engine, but the engine returns to its default language when an input session ends or the plugin reloads, so the language was never sent again. It is now sent again. This was reported on a physical keyboard and could not be tested on that device, so please report back if you were affected.
- **Emoji Sub-Category Swipe (#846)**: Swiping the Emoticons and Unicode sub-category chips could switch the whole emoji category instead of scrolling the chips. The chips now scroll without moving the category, and sit flush so a swipe never starts in a gap.
- **Space Bar Text on Clipboard and Emoji Pages (#846)**: The custom space bar text and the language name now also show on the bottom row of the clipboard and emoji pages.
- **Network Hardening (#840)**: The app's own hosts (Groq, Gemini, OpenAI, Hugging Face, GitHub, Codeberg) now accept only HTTPS with the system certificate store, so a user-installed CA is not trusted for API keys, model downloads or update checks. Custom AI endpoints are unchanged: HTTP on localhost or LAN (with "Allow insecure connections"), private CAs and self-signed HTTPS keep working. If you inspect HTTPS traffic with a user CA (for example AdGuard), calls to these hosts will now fail in release builds.
- **Dictionary Downloads (#833)**: A failed download no longer leaks the connection, and the existing dictionary is kept if installing the new one fails. Downloads now send the real app version as the User-Agent instead of `3.8.9`.
- **Floating Keyboard Touch Area (#835)**: A view that lies completely outside the window is no longer counted as part of the touchable region.
- **Accessibility Leak (#835)**: The accessibility helper no longer holds on to the keyboard service, which could keep it alive after it was stopped.
- **Bluetooth Audio Detection (#835)**: BLE broadcast devices are only checked on Android 13+, where that device type exists.

---

### ✨ Added
- **Clearer Incognito Indicator (#846)**: The incognito icon on the space bar is larger and drawn at full strength, and sits next to the language name instead of overlapping it. On very narrow keys, such as the numpad, the language name is dropped to keep the icon visible.
- **Text Edit Key Icon (#847)**: The text edit toolbar key icon was redrawn with larger arrows and a clear gap around the cursor, so it is no longer crowded.

---

### 🧹 Maintenance & Testing
- **Lint Cleanup (#833 to #841)**: Android lint warnings dropped from 1252 to 170 with no errors. Removed 12 unused strings and bool values, 2 unused drawables, 324 translated copies that duplicated non-translatable strings, and dead Android 5 version checks. Style-only checks (typography, KTX hints, plurals) are now disabled, since the translation tooling does not support them.
- **Dictionary Message Translations (#839)**: Regenerated `no_dictionary_message` in Greek, Malayalam and Urdu, which still used an older one-argument text. The spacing around the link placeholder is slightly off in those three languages.
- **Documentation (#830, #831, #832)**: The voice typing section, the Whisper models repository entry and the Setup Guide link in the README were updated.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.4-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.4-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 6.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
