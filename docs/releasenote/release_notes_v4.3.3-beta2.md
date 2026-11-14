### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## 🚀 LeanType 4.3.3-beta2

---

### 🐛 Fixed & Hardened
- **Navigation Bar Strip & Bottom Gap (#811)**: On Android 15+ the keyboard's own navigation bar frame had no background, so the app behind it showed through the strip under the keys. The frame is now painted with the navigation bar color, and the keyboard reserves the frame's real measured height, so the bottom row no longer sits under the nav row or leaves an extra gap.
- **Editor Crash on Trailing Empty Lines**: Fixed a crash in `requestTextBoundsInfo` when the cursor is on a trailing empty line, requested bounds in screen coordinates, and keep the line above visible when the cursor is on the empty last line.
- **Toolbar Stability**: Guarded an empty-range `coerceIn` crash in toolbar key sizing, proportioned toolbar key height to the suggestion strip, relaxed corner curvature, and restored the toolbar after pasting the clipboard suggestion chip.
- **Floating Keyboard Geometry**: Size and position are now remembered separately for each orientation, landscape vertical resize bounds are fixed, and the strip, keys, and font scale with the keyboard width. The navigation bar color is no longer reset when entering floating mode.
- **Swipe Typing Markers**: Reset `exactLinesBottom` and guarded mapped marker coordinates to avoid stale or invalid gesture markers.
- **Emoji Search Touch Clipping**: Fixed language switch and popup key touches being clipped during emoji search.
- **Local Whisper Model Import (#807)**: The import no longer dispatches before the voice plugin has connected, and the service stays alive during the background model copy.

### ✨ Added
- **Whisper Model Matrix**: The voice model registry now offers a 6-model matrix (tiny, base, small), linked to the unified v1.0.0 Whisper models release, with a link to FUTO ACFT models in the download dialog.
- **Voice Processing Limit & Live Transcription**: Final recognition time now scales with the recording length instead of a fixed 15 s, the plugin runs at full priority while dictating, and a new option (off by default) transcribes each part at every pause in speech.
- **Swapped-Letter Auto-Correction**: Short words with two swapped letters are corrected, such as `teh` to `the` and `adn` to `and`.
- **Settings Search Long Press**: Long press a search result to show the setting inside its own screen.
- **Unfolded Side Padding Scale**: New side padding setting for unfolded devices, resolved per screen profile.

### ⚡ Performance & Battery
- **Faster Voice Input**: The default silence timeout is reduced to 3 s, and the voice input source now prefers `VOICE_RECOGNITION`.

### 🧹 Maintenance & Testing
- **Debug Voice Plugin Binding**: Debug builds can bind to the debug voice plugin.
- **Bug Report Template**: Added a plugin version check and field to the bug report template.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.3-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.3-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
