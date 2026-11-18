### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## 🚀 LeanType 4.3.5-beta2

---

### 🐛 Fixed & Hardened
- **Hex Layout Bottom Row**: Hex layouts place their space keys in a middle row, so the clipboard and emoji pages could put their bottom row in the wrong place. The bottom row is now taken from the layout geometry and stays at the bottom.
- **Hex Functional Key Icons**: Hex draws functional keys filled even without key borders, so with some themes their icons and labels were hard to read. They now fall back to a contrasting color when the contrast is too low.

---

### ✨ Added
- **Delete/Space Keyboard Swipe (#791)**: A new swipe action: swipe left anywhere on the keyboard to delete a character, swipe right to insert a space.
- **Khipro Blinder Mode (#789)**: Backslash switches Khipro to plain Latin input until `;`, as in the official spec. `;;` types a literal `;`, and moving to a new input field leaves the mode. Backslash is available as a long-press popup on the `;` key.
- **GIF Packs as Tiles**: With a GIF plugin that supports packs, local packs show as tiles first. Tapping a tile opens the pack, closing it restores your place, and searching still covers everything.

---

### 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.5-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release | - |
| **`2-LeanType_4.3.5-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None | - |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 6.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
