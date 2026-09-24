## 🧪 LeanType 4.2.7 Beta (Build 1)

> [!NOTE]
> This is a **pre-release testing build** for LeanType 4.2.7. Please test and report any issues, input regressions, or feedback on [GitHub Issues](https://github.com/LeanBitLab/LeanType/issues).

---

### 🛠️ Input & Stability Fixes
- **Word Replacement & Backspace Desync**: Resolved cursor desync and text duplication during word replacement and rapid backspacing in `RichInputConnection`.
- **Backup & Restore UI Freeze**: Eliminated main thread `CountDownLatch` blocking during backup and restore operations to prevent ANRs.
- **Mode Switch Touch Protection**: Fixed `PointerTracker` touch state transitions so switching layouts (`?123` / `ABC`) does not trigger accidental `space` or `'x'` clicks upon release.
- **Settings Startup Race**: Decoupled preference change listener registration from `onCreate` to prevent early callback race conditions.
- **Voice Status Crash Fix**: Resolved a `NullPointerException` on null drawable during voice status display and theme color updates.

### ⌨️ Layout & Honeycomb Engine
- **Hexagonal Honeycomb Layout Engine**: Native integration of authentic Typewise and hex QWERTY layouts (`hex_typewise` & `hex_qwerty`) with axial hit detection, twin spacebars, and vector rendering.
- **Landscape Hex Ergonomic Split**: Positioned hex keys at each end of the screen with a central gap in landscape mode, preserving comfortable key sizing and natural thumb reachability.
- **Central Gap Touch Clamping**: Added distance threshold clamping in `HexKeyDetector` to eliminate ghost touches when tapping in the central landscape gap.

### 🎨 Appearance & Customization
- **Popup Key Vertical Offset**: Added an adjustable popup position slider (0–10 dp) in **Settings → Appearance** with refined preview animations.
- **Key Long-Press Mapping**: Added select mode long-press mapping and refined spacebar long-press behavior.
- **Dictionary & Locale Preservations**: Synchronously synchronized emoji settings during dictionary resets to avoid reload churn; preserved Catalan dictionary folder ('ca') from legacy cleanup.
- **Diagnostics & Documentation**: Logged missing system glide typing library as `INFO` instead of `WARN`; added comprehensive keycodes and actions reference to `FEATURES.md`.

---

### 📦 Beta Build Artifacts
Built using the `standard` flavor (`assembleStandardRelease`).

| File | Flavor | Size |
|:---|:---|:---:|
| `1-LeanType_4.2.7-standard-release.apk` | Standard | - |
