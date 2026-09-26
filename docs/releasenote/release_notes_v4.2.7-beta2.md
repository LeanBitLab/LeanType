## 🧪 LeanType 4.2.7 Beta (Build 2)

> [!NOTE]
> This is the **second pre-release testing build** for LeanType 4.2.7. Please test and report any issues, input regressions, or feedback on [GitHub Issues](https://github.com/LeanBitLab/LeanType/issues).

---

### ⌨️ Core Engine & Input Fixes
- **Composing Backspace Acceleration (#510)**: Synchronized hold-to-delete acceleration across composing words and non-composing text. Suppressed continuous word suggestion recalculations during repeated backspace events, eliminating deletion stutter and latency when holding backspace.
- **Physical Keyboard Layout Mapping (#559)**: Added a physical keyboard layout preference under **Settings → Language & Layout** with real-time hardware scan code translation (supporting QWERTY, QWERTZ, AZERTY, Dvorak, Colemak, Workman, or matching the active on-screen layout).
- **Boundary Whitespace Protection**: Prevented backspacing after separators from swallowing preceding whitespace into the composing span.
- **Text Expander Fixes**: Ensured numeric and non-composing shortcut expansions trigger reliably upon typing space or punctuation separators.

### 🎨 Honeycomb Layout & Theming
- **Dynamic Theme Action Key**: Fixed the hexagonal honeycomb action key background which was stuck on static `#2979FF` blue. It now dynamically inherits your active theme accent and Material You wallpaper colors with proper icon contrast and borders.
- **Streamlined Honeycomb Layout**: Prioritized the authentic `hex_typewise` layout with optimized popup keys and removed redundant layout variants.
- **Single-Entry Popup Keys Fix**: Fixed popup key panel dimensions so single-entry keys are never squashed or clipped.

### ⚡ Animations, Haptics & UI
- **Animation Speed Slider**: Added an animation speed slider to **Settings → Appearance** (range `0.0x` to `2.0x`, defaulting to `1.0x`, with `0.0x` as `Off (Instant)`).
- **Smooth Keyboard Transitions**: Scaled key previews and popup panels dynamically; added smooth minimal entrance transitions for emoji palettes, clipboard history, toolbar toggles, and external suggestion chips.
- **Modernized Haptic Engine**: Upgraded feedback on Android 11+ with `VibrationAttributes` (`USAGE_TOUCH`) and composition primitives (`PRIMITIVE_CLICK`, `PRIMITIVE_TICK`, `PRIMITIVE_LOW_TICK`) for crisp tactile feedback.

### 🌐 Translation Engine & Global i18n
- **In-Keyboard Translation Enhancements**: Added default target language selection, source language controls, smart detection, and improved pivot model fallbacks.
- **Comprehensive Global Translations**: Completed 100% of missing settings strings across 25+ languages (Greek, Hindi, Urdu, Arabic, Turkish, Portuguese, Italian, Spanish, Russian, French, German, Malayalam, Dutch, Polish, Ukrainian, Czech, Hungarian, Simplified Chinese, Bulgarian, Estonian, Croatian, Indonesian, Hebrew, Romanian, Belarusian, Lithuanian).

---

### 📦 Beta Build Artifacts

| File | Flavor | Description | Size |
|:---|:---|:---|:---:|
| `1-LeanType_4.2.7-standard-release.apk` | Standard | Full features & online/offline AI voice | - |
| `2-LeanType_4.2.7-offline-release.apk` | Offline | Fully air-gapped (zero internet permission) | - |
