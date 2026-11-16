### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## 🚀 LeanType 4.3.4

---

### 🐛 Fixed & Hardened
- **Handwriting Stops After Reopening the Keyboard (#851, #854)**: Handwriting worked only until the keyboard was closed, and then needed a forced restart (Settings → About → Restart keyboard) every time. Closing the keyboard released the recognizer, and loading it again failed because Android allows the plugin's native library to be loaded only once. The plugin loader is now kept, so handwriting keeps working across keyboard closes and reopens.
- **Handwriting Panel Closes After the First Word (#854)**: The first handwritten word after opening the keyboard switched back to the normal keyboard, because auto-capitalization turning off reloaded the letter layout under the panel. The panel now stays open, and the correct capitalization is applied when you leave handwriting.
- **"Tap to Download Model" With the Model Installed (#854)**: Right after the keyboard started, the check whether the handwriting model is downloaded could time out and report it as missing. The check is now retried before the download prompt is shown.
- **Japanese Input Falling Back to Chinese (#842, #844, #855, #857)**: With the CJK plugin, Japanese input could switch to Chinese after switching to another app, until the keyboard was restarted. Two things caused it: the keyboard did not send the language to the engine again after an input session ended or the plugin reloaded, and when Android freed the keyboard's memory the engine reloaded without its Pinyin library, so the keyboard fell back to a Chinese placeholder engine. Both are fixed, and the engine is now kept when only Japanese is available on a device.
- **Split Keyboard on Regular Phones (#850)**: Since 4.3.2, a regular phone with a small display size setting (500 dp or more in width and height) was treated as a large screen, which turns the split keyboard on by default. Such phones now stay on the compact layout, while unfolded foldables (for example 588x706 dp) still get the large layout.
- **Extra Space Below the Keys (#856)**: The space reserved for the navigation bar is now measured on the screen instead of inside the keyboard window, so a keyboard window that already ends above the navigation bar (seen on Samsung) no longer gets it twice. The space is also checked again after the keyboard is rebuilt by a settings change such as a custom font or the toolbar mode, where extra space stayed until a key was tapped. Confirmed fixed on a Samsung Galaxy S20 FE (Android 13).
- **Update Indicator (#849)**: The update dot and the plugin update state were saved by the last check. The dot stayed on after a plugin was updated, and a plugin installed after the last check showed nothing until you tapped Check for updates. The state is now worked out from the latest known version and the installed version every time, and a check looks up all plugins, not only the installed ones.
- **Clipboard Action Menu Colors (#848)**: The pin, edit, delete and select menu after a long press on a clipboard entry was drawn white even on mid-tone themes with light icons, so the icons could not be seen. The menu now uses the toolbar color whenever the icons are light.
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
- **Device and Layout Info in the Exported Log (#850, #856)**: The startup lines of the exported log now include the device model, screen size in dp and density, the screen profile line is part of the log, and changes of the space below the keys are logged with the values they were computed from, so reports can be matched to a device. New tests cover a phone with a small display size and a keyboard window that ends above the navigation bar.
- **Documentation (#830, #831, #832)**: The voice typing section, the Whisper models repository entry and the Setup Guide link in the README were updated.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.4-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.4-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 6.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
