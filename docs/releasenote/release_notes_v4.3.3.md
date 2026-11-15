### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## 🚀 LeanType 4.3.3

> ⚠️ **Offline flavor now requires Android 6.0+**: The `offline` flavor minimum is raised from Android 5.0 (SDK 21) to Android 6.0 (SDK 23), the same as `standard`. Android 5.0 and 5.1 devices can no longer install or update it.

---

### 🐛 Fixed & Hardened
- **Space Below the Keyboard (#811, #828)**: On Android 15+ the IME navigation bar frame is now painted with the navigation bar color and the keyboard reserves its real height. On devices that report the navigation bar although the window already leaves room for it (seen on a Samsung Note 10 with Android 12), the keyboard now reserves only the part of the bar it really overlaps, which fixes a too large gap below the keys.
- **Letter Size Regression (#814)**: Wide letters such as `w` and `m` (and `ш`, `щ` in Russian) no longer render smaller than the other letters.
- **Backspace Before Symbol Runs (#820)**: One backspace no longer deletes everything before the cursor when the text consists of circled letters, arrows, or math symbols.
- **Editor Crash on Trailing Empty Lines**: Fixed a crash in `requestTextBoundsInfo` when the cursor is on a trailing empty line, and the line above stays visible when the cursor is on the empty last line.
- **Toolbar Stability**: Guarded an empty-range crash in toolbar key sizing, proportioned the toolbar key height to the suggestion strip, and restored the toolbar after pasting the clipboard suggestion chip.
- **Floating Keyboard**: Size and position are remembered separately for each orientation, landscape vertical resize is fixed, and the strip, keys, and font scale with the keyboard width. The navigation bar color is no longer reset when entering floating mode.
- **Swipe Typing Markers**: Reset stale gesture markers and guarded invalid marker coordinates.
- **Emoji Search Touch Clipping**: Fixed language switch and popup key touches being clipped during emoji search.
- **GIF & Sticker Insertion (#794)**: Grants the destination app read permission for the image and falls back to copying the image to the clipboard with a toast when direct insertion is not supported.
- **Text Expander Symbol & Number Shortcuts (#796)**: Shortcuts that start with symbols or numbers (such as `@g`, `#todo`) now match and expand in the suggestion strip.
- **Custom Popup Keys (#801)**: Automatic symbol hints no longer overwrite keys that already have custom popup keys.
- **Hexagonal Custom Layouts (#795)**: Custom and modified hexagonal layouts are detected even if the layout name does not contain "hex".
- **Voice Battery & Screen Off (#803)**: Voice recording is cancelled, the screen-on lock released, and the voice plugin unbound when the screen turns off.
- **Local Whisper Model Import (#807)**: The import no longer starts before the voice plugin has connected, and the service stays alive during the model copy. The plugin is not left bound after a failed import, and the idle unbind retries while the plugin is busy (#819).
- **Voice Dictation Fixes (#816, #820, #821)**: The voice animation stays visible when dictation is started from the open toolbar, tapping the voice key during processing no longer cancels the pending text, live transcription no longer adds stray words at the end, late callbacks cannot affect a new dictation, and a piece that never finishes times out.
- **Android 6 Compatibility (#817)**: Replaced calls that need API 23 or 24 in the settings search, the custom styles parser, and the input view.
- **Settings Search (#823)**: Expandable section headers are no longer listed in the results, and the highlight after a long press stays readable in light and dark themes.

### ✨ Added
- **Custom Text Styles (#815, #820)**: Add your own Unicode font styles from `Toolbar > Custom text styles`, one style per line as `Name:` followed by the styled A-Z, a-z and optionally 0-9. Pasted alphabets without a name are accepted, and custom styles appear in the Font Style strip with live typing and undo. A Guide button opens the documentation.
- **Personal Dictionary in Backups (#829)**: The backup now includes your personal dictionary, and the options are separate: Dictionaries, Personal Dictionary and Text Expander. Restoring only adds the words that are not on the device yet. The import of a personal dictionary file also keeps the language of each word.
- **Whisper Model Matrix**: The voice model registry offers 6 models (tiny, base, small), linked to the unified v1.0.0 Whisper models release, with a link to FUTO ACFT models in the download dialog.
- **Voice Processing Limit & Live Transcription**: Final recognition time scales with the recording length, the plugin runs at full priority while dictating, and a new option (off by default) transcribes each part at every pause in speech.
- **Swapped-Letter Auto-Correction**: Short words with two swapped letters are corrected, such as `teh` to `the` and `adn` to `and`.
- **Settings Search Long Press**: Long press a search result to show the setting inside its own screen.
- **Unfolded Side Padding Scale**: New side padding setting for unfolded devices.

### ⚡ Performance & Battery
- **Faster Settings Screens (#823)**: Large settings screens compose their settings as you scroll, which shortens the slowest frame when a screen opens.
- **Faster Voice Input**: The default silence timeout is 3 s, and the voice input source prefers `VOICE_RECOGNITION`.

### 🧹 Maintenance & Testing
- **Offline Flavor minSdk 23 (#818)**: Raised the offline flavor minimum to Android 6.0, which also clears about 295 lint errors that only applied below API 23.
- **Typed Words Kept Out of the Log (#825)**: The word added to the personal dictionary and the emoji search query are no longer written to the in-app log.
- **Dead OTP Code Removed (#827)**: The OTP auto-fill code that was disabled in 4.3.0 is removed, together with its strings and documentation.
- **Release Tooling (#812, #822)**: Release notes and the verifier expect `-release.apk` names, the download badge follows the latest stable release, and the Get Pre-release button shows the current beta.
- **Bug Report Template**: Added a plugin version check and field.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.3-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.3-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 6.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
