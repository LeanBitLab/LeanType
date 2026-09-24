### 💖 Support Our Work

As an open-source, community-funded project, we operate on a very limited budget. If LeanType helps you daily, please consider supporting us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org). Sharing LeanType with friends and family makes a huge difference!

## 🚀 What's New in v4.2.7

### ✨ Highlights
- **Hexagonal Honeycomb Layout Engine (`hex_typewise` & `hex_qwerty`)**: Native integration of authentic Typewise and hexagonal QWERTY layouts directly into the keyboard engine pipeline. Features custom axial hexagonal hit testing, vector path rendering, interlocking row math, dual spacebars, and half-hex functional keys (Shift/Delete).
- **Landscape Hex Ergonomic Split**: In landscape orientation, hexagonal keys are ergonomically split into left and right clusters positioned at each end of the screen with a central gap, preserving comfortable key dimensions and enabling natural thumb reachability while maintaining vertical honeycomb alignment.
- **Mode Switch (`?123` / `ABC`) Input Protection**: Resolved a touch-tracker state transition issue where switching layouts on touch-down caused accidental spacebar or 'x' character insertions upon release due to differing key coordinates between alphabet and symbol layouts.

### 🛠️ Improvements & Enhancements
- **Customizable Popup Key Vertical Offset**: Introduced an adjustable popup position slider in **Settings → Appearance → Popup key vertical offset** (0–10 dp) with smoother popup preview animations and refined container positioning.
- **Hex Touch Hit Detection Bounds**: Added distance threshold clamping in the hexagonal key detector to eliminate ghost clicks when tapping in the central gap in landscape mode.

## 📦 Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater |
|:---|:---|:---|:---|:---|:---|
| **`1-LeanType_4.2.7-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or File import | Optional (AI/plugins) | ✅ View Release |
| **`2-LeanType_4.2.7-offline-release.apk`** | **Offline** | Local LLM Plugin (8.0+) | Browser download + File import | 🚫 Zero Internet (No Permission) | ❌ None |

> 💡 **Plugin Compatibility**: All flavors support **Offline Voice Dictation** (Android 8.1+), **Offline Translation** (Android 6.0+), **Offline Handwriting Recognition** (Android 6.0+), **Offline OCR Text Extraction** (Android 5.0+), and **Offline AI Proofreading** (Android 8.0+, 64-bit) via modular plugins, and work 100% offline.
