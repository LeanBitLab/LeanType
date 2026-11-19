### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## 🚀 LeanType 4.3.5

---

### 🐛 Fixed & Hardened
- **Japanese Input Falling Back to Chinese (#842)**: On a physical keyboard, the CJK plugin engine was released on every app switch, and loading it again could fail to reopen its native library, so Japanese fell back to Chinese. The engine is now kept while you type and is only released under real memory pressure when no input is active. The plugin is also reused per install instead of being loaded again, and loading and releasing can no longer overlap.
- **Removed Suggestions and Capitalized Words (#861)**: Removing a suggestion such as "im" could block a dictionary word that only exists capitalized, like "I'm", so it was never suggested or autocorrected again. Words from the main dictionary are no longer added to Blocked words, in any capitalization.
- **Tiny Floating Toolbar (#860)**: In the floating keyboard, the toolbar height was derived from the key height, so layouts with more rows (number row, PC layouts) got very small, hard to tap toolbar keys, especially in landscape. The toolbar now scales from the normal docked height, never smaller than 24dp and never taller than the docked toolbar.
- **One-Handed Mode Side and Controls**: One-handed mode could jump to the other side while typing and lose its side switch arrow. The side now stays in sync, the controls come back after leaving touchpad mode, and they sit above the navigation bar so the resize button is no longer hidden behind it.
- **Hex Layout Bottom Row**: Hex layouts place their space keys in a middle row, so the clipboard and emoji pages could put their bottom row in the wrong place. The bottom row is now taken from the layout geometry and stays at the bottom.
- **Hex Functional Key Icons**: With some themes, the icons and labels of the filled hex functional keys were hard to read. They now fall back to a contrasting color when the contrast is too low.
- **Clipped Emojis**: Tall emojis could be cut off at the top by the row above. Emojis are now scaled to fit inside their key and centered.
- **GIF Plugin Status**: Settings and the plugins page briefly showed the GIF plugin as disconnected or connecting before switching to Active. An installed plugin now shows as Active right away.

---

### ✨ Added
- **Delete/Space Keyboard Swipe (#791)**: A new swipe action: swipe left anywhere on the keyboard to delete a character, swipe right to insert a space. It needs half the usual swipe distance, so repeated flicks are quick.
- **Khipro Blinder Mode (#789)**: Backslash switches Khipro to plain Latin input until `;`, as in the official spec. `;;` types a literal `;`, and moving to a new input field leaves the mode. Backslash is available as a long-press popup on the `;` key.
- **GIF Packs as Tiles**: With a GIF plugin that supports packs, local packs show as square tiles first, with the pack name and item count. Tapping a tile opens the pack, closing it restores your place, and searching still covers everything.
- **Animated GIF Previews**: A new Preview switch in GIF settings plays the GIFs on screen without holding them. Only visible tiles animate, at tile size, to keep CPU use low. Needs Android 9 or newer.
- **GIF Plugin 1.0.8**: Pack names keep accented letters such as ç, ã and õ. Long-press a pack for a small icon menu to rename, reorder or delete it, and the keyboard uses the same pack order. The Preview switch sits in the plugin toolbar.
- **Smoother One-Handed Resizing**: Dragging the resize button now shows a live preview and rebuilds the keyboard once when you lift your finger, instead of rebuilding it on every move.
- **One-Handed Mode Look**: The keyboard slides to the other side when switching sides, and the control buttons are now square keys styled like the toolbar keys.

---

### ⚡ Performance & Battery
- **Faster AI Integration Settings**: Opening Settings > AI Integration created the encrypted key storage several times, which froze the screen for a moment. It is now created once and shared.

---

### 🧹 Maintenance & Testing
- **Downloads Badge**: The README downloads badge shows the full download total, rounded like other badges.
- **Faster PR Checks**: CI compiles only the app flavors a pull request touches.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.5-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.5-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 6.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
