### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 LeanType 4.3.1-beta3

---

### ✨ Added
- **Full Keyboard Swipe Delete Word Option (#757)**: Added "Delete word" as a selectable action for full keyboard horizontal swipe gestures, allowing users to quickly delete the preceding word (including composing word reset, selection deletion, dictionary unlearning, and web editor compatibility) with full localized translations across all 30 languages.

### 🐛 Fixed
- **Voice Silence Timeout & Recording Cleanup (#765)**: Adaptively tracks ambient noise RMS floor to ensure silence detection works reliably in noisy environments, automatically cancels empty sessions on initial silence timeout, and eliminates zombie recording states when audio loops terminate.
- **Dynamic Multiline Clipboard Editor & Cursor Navigation**: Rebuilt the inline clipboard editor with a dynamic multiline strip that expands smoothly up to 4 lines without hiding action buttons, added dedicated text wrapping, and integrated interactive cursor positioning with blinking cursor via `InlineCursorTextView`.
- **Emoji Search Shift Auto-Reset**: Automatically reverts the Shift key to unshifted lowercase layout after typing a character in the emoji palette search bar, while preserving Caps Lock state on double-tap or long-press.
- **Emoji Search Cursor & Caret Positioning**: Added interactive cursor rendering and blinking caret positioning in the emoji search input field via `InlineCursorTextView`.
- **GIF Picker Preview Frame Reload**: Fixed image swapping during GIF live preview long-presses in `GifPickerView`, ensuring subsequent previewed items correctly reload fresh media instead of displaying the first cached frame.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.1-standard-debug.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.1-offline-debug.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
