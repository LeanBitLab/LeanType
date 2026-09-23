# LeanType Features & Setup Guide

LeanType combines a lightweight, privacy-focused keyboard foundation with cutting-edge productivity tools: **Multi-Provider Cloud & Offline AI**, **On-Device Whisper Voice Typing**, **Handwriting Recognition**, **Dual-Engine In-Keyboard Translation**, **Rich Text Utilities**, and **Deep UI Customization**.

---

## 📑 Index

| Section | Description |
| :--- | :--- |
| 🆕 **[Summary of New Features](#-summary-of-new-features)** | Complete matrix of all features & settings locations |
| 🤖 **[Multi-Provider Cloud AI](#1-multi-provider-cloud-ai)** | Google Gemini, Groq, and OpenAI-compatible inference |
| 🧠 **[Custom AI Keys & Keywords](#2-custom-ai-keys--keywords)** | 10 custom toolbar prompt keys, personas, and themed capsules |
| 🛡️ **[Offline Neural Proofreading (GGUF)](#3-offline-neural-proofreading-gguf)** | 100% on-device private LLM execution via `llama.cpp` |
| 🌐 **[Dual-Engine In-Keyboard Translation](#4-dual-engine-in-keyboard-translation)** | AI Translation vs Google Translation Plugin with auto-fallback |
| 🎙️ **[On-Device Whisper Voice Typing](#5-on-device-whisper-voice-typing)** | Fast speech-to-text with quantized multilingual Whisper models |
| ✍️ **[Handwriting Input](#6-handwriting-input)** | Draw letters directly on a handwriting canvas (ML Kit) |
| 🧭 **[Dedicated Text Editing Panel](#7-dedicated-text-editing-panel)** | Gboard-style precision DPAD arrow navigation & selection mode |
| 📐 **[Smart Auto-Spanning Toolbar](#8-smart-auto-spanning-toolbar)** | Symmetrical dynamic toolbar key expansion across screen widths |
| 🖱️ **[Touchpad Mode & Gestures](#9-touchpad-mode--gestures)** | Spacebar swipe gesture & full-screen laptop-style touchpad |
| 🪟 **[Native IME Floating & Resizable Keyboard](#10-native-ime-floating--resizable-keyboard)** | Touch-passthrough, bottom dock bar, and zero dead space floating window |
| ⌨️ **[Dual Toolbar & Split Suggestions](#11-dual-toolbar--split-suggestions)** | Split toolbar actions and word suggestions into separate rows |
| 📝 **[Text Expander](#12-text-expander)** | Shortcut expansion with dynamic template placeholders |
| 📋 **[Searchable Clipboard, Editing & Gestures](#13-searchable-clipboard-editing--gestures)** | Real-time search, swipe-to-edit inline, swipe-to-delete undo, pinned folding, and sliding select |
| 📸 **[Screenshot Suggestions & Capture](#14-screenshot-suggestions--capture)** | Recent screenshot suggestion strip and clipboard storage |
| 🔎 **[Emoji Search](#15-emoji-search)** | Search for emojis by keyword with an Emoji Dictionary |
| 🚫 **[Blocked Words & Regex Blacklist](#16-blocked-words--regex-blacklist)** | Filter out offensive or unwanted words using custom regex patterns |
| ✉️ **[Privacy-First OTP Auto-Fill](#17-privacy-first-otp-auto-fill)** | Notification-based OTP extraction from messaging apps without SMS permissions |
| 📚 **[Adaptive Personal Dictionary Learning](#18-adaptive-personal-dictionary-learning)** | Customizable repeat learning thresholds & session word boosting |
| 👆 **[Gesture / Glide Typing](#19-gesture--glide-typing)** | Smooth swipe typing powered by native C++ library |
| ⌨️ **[Direct Switch Target IME](#20-direct-switch-target-ime)** | Switch directly to a specific target keyboard with keycode `-10076` |
| 🎨 **[Custom Layout Profiles](#21-custom-layout-profiles)** | Save up to 5 custom layout profiles with persistent slot tracking |
| 🔄 **[In-App Update Checker & Release Viewer](#22-in-app-update-checker--release-viewer)** | Direct GitHub release checks and 1-tap release viewer (`standard` flavor) |
| 📦 **[Flavor Architecture & Privacy](#23-flavor-architecture--privacy)** | Breakdown of Standard and Offline flavors |
| 📷 **[Offline Camera OCR & Screenshot Extraction](#24-offline-camera-ocr--screenshot-extraction)** | In-keyboard camera viewfinder, automated screenshot extraction pill, and advanced formatting cleaners |
| 🔢 **[Inline Math Calculation Suggestions](#25-inline-math-calculation-suggestions)** | High-precision arithmetic expression evaluator on typing `=` with 1-tap replacement |
| 🎵 **[Custom Sound Packs & Audio Customization](#26-custom-sound-packs--audio-customization)** | Zero-latency key audio engine, 12+ built-in presets, remote repository catalog, and `.zip` imports |
| ⚙️ **[Per-App Profiles & Compatibility Engine](#27-per-app-profiles--compatibility-engine)** | Per-app composing rules, symbol composing for Tasker, auto-incognito, and Enter overrides |
| ⌨️ **[Hardware & Physical Keyboard Support](#28-hardware--physical-keyboard-support)** | External Bluetooth/USB keyboard suggestions, shortcuts (`1`,`2`,`3`), D-PAD emoji navigation |
| 🎨 **[Advanced Appearance & Key Ergonomics](#29-advanced-appearance--key-ergonomics)** | Independent corner radii (normal, functional, action), key gaps, padding scales, Shift icons |
| ⌨️ **[Comprehensive Keycodes & Actions Reference](#30-comprehensive-keycodes--actions-reference)** | Complete reference table of all functional, navigation, editing, AI, and hardware keycodes |

---

## 🆕 Summary of New Features

| Feature | Description | Settings Location |
| :--- | :--- | :--- |
| **Multi-Provider Cloud AI** | Proofread, rewrite, and fix grammar via Gemini, Groq, or OpenAI-compatible custom endpoints. | `AI Integration > Set AI Provider` |
| **Custom AI Keys** | 10 customizable toolbar keys with prompt templates, hashtags (`#editor`, `#proofread`), and tag capsules. | `AI Integration > Custom Keys` |
| **Offline Proofreading (GGUF)** | Zero-network, on-device neural proofreading powered by embedded `llama.cpp`. | `Advanced > GGUF Model (.gguf)` |
| **Multi-Mode In-Keyboard Translation** | Translate text on-device (Offline ML Kit), via Translation Plugin, or Cloud/Local AI with auto-fallback. | `Translation > Translation Mode` |
| **Whisper Voice Typing** | On-device speech-to-text with quantized multilingual Whisper models and audio visualizer. | `Voice typing > Whisper Speech Models` |
| **Handwriting Recognition** | Draw characters on a dedicated canvas with in-app model manager (supported across all flavors via plugin). | `Handwriting > Handwriting recognition` |
| **Offline Camera & Screenshot OCR** | Live in-keyboard camera scanner and screenshot suggestion pill with rich text cleaners (casing, join styles, dehyphenation). | `OCR & Text Extraction` / `Plugins > OCR` |
| **Inline Math Calculation** | Instant arithmetic calculation suggestions on typing `=` with 1-tap expression replacement. | `Text correction > Inline math calculation` |
| **Custom Sound Packs** | Zero-latency key audio engine with 12+ built-in presets, remote catalog downloads, and `.zip` import. | `Plugins > Sound` / `Preferences > Sound on keypress` |
| **Text Editing Panel** | Precision DPAD arrow navigation, Shift selection mode, and clipboard shortcuts. | Toolbar > Text Editing Icon |
| **Auto-Spanning Toolbar** | Dynamically expands and balances toolbar keys symmetrically across device widths. | `Appearance > Toolbar auto-spacing` |
| **Touchpad Mode** | Swipe up on Spacebar to activate full cursor control and laptop-style touchpad gestures. | `Gesture typing > Vertical spacebar swipe` |
| **Native IME Floating Window** | Moveable floating keyboard window with touch-passthrough, bottom dock bar, resize handle, zero dead space, and persistent memory. | Toolbar > Floating Keyboard / `Preferences > Remember floating mode` |
| **Per-App Profiles & Quirks** | Fine-tune composing behavior per app (Tasker symbol composing, web editor compatibility, auto-incognito, and Enter action overrides). | `Preferences > App Profiles` |
| **Hardware Keyboard Support** | Full predictive suggestions, auto-correction, candidate shortcuts (`1`, `2`, `3`), and D-PAD emoji navigation for external keyboards. | `Languages > Physical keyboard` / Auto-detected |
| **Appearance & Key Ergonomics** | Independent key corner radius sliders, adjustable key gaps, customizable padding scales, and distinct Shift/Caps visual indicators. | `Appearance > Key borders` / `Theme` |
| **Split Toolbar & Suggestions** | Separates suggestions from the toolbar into a dual-row view. | `Appearance > Split toolbar & suggestions` |
| **Versatile Text Expander** | Expand shortcuts with dynamic variables, citation stripper (`%clipboard:clean%`), and modifiers. | `Text correction > Text Expander` |
| **Clipboard History & Inline Edit** | Search history, swipe-right to edit inline, swipe-left to delete with undo, fold pinned clips, and slide-select. | Clipboard Toolbar > Search / Swipe items |
| **Screenshot Suggestions** | Instant 1-tap sharing of recently taken screenshots via the suggestion strip. | `Text correction > Suggest recent screenshots` |
| **Emoji Search** | Search emojis by name/keyword directly from the emoji palette. | `Emoji Key > Search Icon` |
| **Blocked Words Blacklist** | Prevent unwanted words from being suggested with regex pattern matching. | `Text correction > Blocked words blacklist` |
| **Privacy-First OTP Auto-Fill** | Extracts OTP verification codes from incoming notifications with app package selector. | `Text correction > OTP Auto-Fill` |
| **Smart Learning & Boost** | Adjustable personal dictionary learning threshold (1-5 times) and temporary session word boost. | `Text correction > Dictionary learning threshold` |
| **Gesture Typing** | Swipe typing powered by native C++ spatial scoring engine. | `Gesture typing > Enable gesture typing` |
| **Direct Switch Target IME** | Fast 1-tap switching to another configured IME using custom keycode `-10076`. | `Preferences > Direct Switch Target IME` |
| **Custom Layout Profiles** | Store up to 5 custom keyboard layouts with persistent slot tracking. | `Languages > Custom layouts` |
| **In-App Update Checker** | Checks GitHub releases with changelogs and 1-tap release viewing (`standard` flavor). | `Settings > Updates` |

---

## 1. Multi-Provider Cloud AI

LeanType connects directly with top AI providers for ultra-fast proofreading, grammar corrections, tone adjustments, and rewrites.

### Supported Providers

| Provider | Privacy Level | Setup Speed | Free Tier | Best For |
| :--- | :---: | :---: | :---: | :--- |
| **Groq** | 🟡 Average | 🟢 Fast | High RPM | **Lightning-fast inference speeds** |
| **Google Gemini** | 🔴 Standard | 🟢 Fast | Generous | **High-quality general reasoning** |
| **OpenAI-Compatible** | ⚙️ *Custom* | 🟡 Moderate | *Custom* | **Any custom endpoint (OpenRouter, DeepSeek, Mistral)** |

### Setup Instructions
1. Obtain an API key:
   - **Google Gemini**: [Google AI Studio](https://aistudio.google.com/apikey) (key starts with `AIzaSy...`).
   - **Groq**: [Groq Console](https://console.groq.com/keys) (key starts with `gsk_...`).
   - **OpenAI-compatible**: [OpenRouter](https://openrouter.ai/keys), [DeepSeek Platform](https://platform.deepseek.com), or your local LLM server.
2. In LeanType, open **Settings → AI Integration → Set AI Provider**.
3. Select your provider, paste your API token, and pick your preferred model and target language.

---

## 2. Custom AI Keys & Keywords

You can assign custom prompts, personas, and custom label tags to **10 dedicated toolbar keys**.

### Custom Text Capsules
- Assign custom labels (e.g. `French`, `Rephrase`, `Reply`) in **Settings → AI Integration → Custom Keys**.
- Enable **Show tags on keyboard** to render them as themed pill capsules directly on the keyboard toolbar.

### AI Persona Keywords (Hashtags)
Include these hashtags in your custom prompts to enforce strict system roles:

| Keyword | Persona / Role | System Instruction Injected |
| :--- | :--- | :--- |
| `#editor` | **Text Editor** | "Output ONLY the edited text. Do not add any conversational filler." |
| `#outputonly` | **Strict Output** | "Output ONLY the result. Do not add introductions or explanations." |
| `#proofread` | **Proofreader** | "Fix grammar and spelling errors. Output ONLY the fixed text." |
| `#paraphrase` | **Rewriter** | "Rewrite using different words while preserving original meaning." |
| `#summarize` | **Summarizer** | "Provide a concise, direct summary." |
| `#expand` | **Content Writer** | "Expand on the text with more details." |
| `#toneshift` | **Tone Adjuster** | "Adjust the tone as requested." |
| `#append` | **Append Mode** | Adds output to the end of the text field instead of replacing. |
| `#showthought` | **Show Thinking** | Preserves reasoning output (`<think>...</think>`) from reasoning models. |

---

## 3. Offline Neural Proofreading (GGUF)

> [!IMPORTANT]
> **Zero-Network Guarantee**: This feature runs 100% locally via the companion [**LeanType Offline AI Plugin**](https://github.com/LeanBitLab/LeanType-Offline-AI-Plugin) powered by `llama.cpp` and is available in the **Offline** build flavor (`-offline-release.apk`). No internet permission exists in the manifest.

### Setup Instructions
1. Download `ai_plugin-arm64-v8a.apk` (or `ai_plugin-x86_64.apk`) from the [LeanType Offline AI Plugin Releases](https://github.com/LeanBitLab/LeanType-Offline-AI-Plugin/releases/latest).
2. In LeanType, open **Settings → Plugins → Offline AI** and tap **Load Offline AI plugin** to load the `.apk`.
3. Download a compact GGUF model:
   - **Qwen 2.5 0.5B Instruct (Q4_K_M)**: Extremely lightweight & fast (~350 MB).
   - **Llama 3.2 1B Instruct (Q4_K_M)**: High-quality compact reasoning (~900 MB).
   - **Qwen 2.5 1.5B Instruct (Q4_K_M)**: High intelligence for modern devices (~1.1 GB).
4. Open **Settings → Advanced → GGUF Model (.gguf)** and select the `.gguf` file from your storage.
5. Configure sampling temperature, Top-K, Top-P, and custom system instructions.

---

## 4. Multi-Mode In-Keyboard Translation

LeanType offers a flexible translation architecture supporting all app flavors:

1. **Translation Plugin** (Supported across all flavors):
   - High-speed, private translation powered by the companion [LeanType Translation Plugin](https://github.com/LeanBitLab/LeanType-Translation-Plugin/releases/latest).
   - In-app model downloads for online builds, and browser download + local file importing for offline builds.
2. **Built-in Offline Translation (ML Kit)**:
   - 100% On-Device & Private translation on supported builds.
   - Download 59+ language translation models directly inside keyboard settings (~30 MB per language pack).
3. **Cloud & Local AI Translation**:
   - Uses your configured **AI Provider** (Google Gemini, Groq, OpenAI, Ollama, or local GGUF models) with customizable translation prompts.

### How to Setup
1. **Online Flavors (`Standard` / `Standard Full`)**: Open **Settings → Translation** and tap **Download Plugin** to install the [LeanType Translation Plugin](https://github.com/LeanBitLab/LeanType-Translation-Plugin/releases/latest) automatically.
2. **Offline Flavors (`Offline` / `Offline Lite`)**: Download `translation_plugin-arm64-v8a.apk` from [GitHub Releases](https://github.com/LeanBitLab/LeanType-Translation-Plugin/releases/latest) and load it in **Settings → Plugins → Translation**.
3. Download or import your required source and target language pairs.
4. Tap the **Translate** icon on the keyboard toolbar to instantly translate selected text or entire input fields.

---

## 5. On-Device Whisper Voice Typing

LeanType integrates high-accuracy, private speech-to-text powered by OpenAI's Whisper architecture via `whisper.cpp` and the [LeanType Voice Plugin](https://github.com/LeanBitLab/LeanType-Voice-Plugin).

### Available Multilingual Whisper Models
- **Tiny** (`ggml-tiny.bin`): **~39 MB** — Ultra-fast, minimal memory usage, 99+ languages.
- **Base** (`ggml-base.bin`): **~74 MB** — Best balance of accuracy and speed for daily typing.
- **Small** (`ggml-small.bin`): **~244 MB** — High accuracy for complex vocabulary and accents.
- **Custom Model**: Import any standard `.bin` GGML Whisper model from device storage.

### Setup Instructions
1. Download and install the [LeanType Voice Plugin APK](https://github.com/LeanBitLab/LeanType-Voice-Plugin/releases/latest) on your Android device (installed as a background IPC service).
2. Grant **Microphone permission** to the LeanType Voice Plugin.
3. In LeanType, open **Settings → Voice typing** (or **Settings → Plugins → Voice**) and tap **Whisper Speech Models**.
4. Download or import your preferred model (e.g. *Multilingual Base* ~74 MB).
5. Configure voice options:
   - **Voice Recognition Language**: Choose **Follow keyboard language (Default)**, **Auto-detect spoken language (`auto`)**, or pick from 99+ specific Whisper languages.
   - **Audio Visualizer**: Displays a real-time sound waveform directly on the keyboard toolbar.
   - **Silence Detection**: Configurable auto-stop sensitivity slider.
   - **Keep Model in Memory**: Prevents model reload latency during consecutive voice typing sessions.
6. Tap the **Microphone** icon on the toolbar to start voice typing.

---

## 6. Handwriting Input

Draw letters, words, or symbols directly on a handwriting recognition canvas using your finger or stylus via the companion [LeanType Handwriting Plugin](https://github.com/LeanBitLab/Leantype-Handwriting-Plugin) (supported across all flavors).

### Setup Instructions
1. **Online Flavors**: Open **Settings → Handwriting** and tap **Download Plugin** to install the [LeanType Handwriting Plugin](https://github.com/LeanBitLab/Leantype-Handwriting-Plugin/releases/latest).
2. **Offline Flavors**: Download `handwriting_plugin-arm64-v8a.apk` from [GitHub Releases](https://github.com/LeanBitLab/Leantype-Handwriting-Plugin/releases/latest) and load it in **Settings → Plugins → Handwriting**.
3. Use the **Offline Handwriting Models** dialog to download recognition packs directly (or import downloaded `.zip` model packs on offline builds).
4. Customize stroke width, stroke fade timeout, and recognition sensitivity.
5. Tap the **Handwriting (Pencil)** icon on the keyboard toolbar to open the drawing canvas and write naturally.

---

## 7. Dedicated Text Editing Panel

A Gboard-style precision editing panel designed for frictionless text manipulation:
- **DPAD Arrow Keys**: Move cursor character-by-character or line-by-line.
- **Selection Mode (Shift + DPAD)**: Highlight text with precision.
- **Quick Selection**: 1-tap **Select Word** and **Select All**.
- **Clipboard Actions**: Direct Cut, Copy, and Paste buttons within the panel.
- **Line Navigation**: Jump directly to Start of Line or End of Line.

---

## 8. Smart Auto-Spanning Toolbar

The **Auto-Spanning Toolbar** dynamically measures available screen width and proportionately balances toolbar keys symmetrically.
- Eliminates awkward blank space on large screens, tablets, and landscape orientation.
- Unifies alignment between standard toolbar keys and clipboard action rows.
- Configure via **Settings → Appearance → Toolbar auto-spacing**.

---

## 9. Touchpad Mode & Gestures

Turn the entire keyboard space into a fluid laptop-style trackpad:
- **Activate via Swipe**: Swipe up on the **Spacebar** to toggle Touchpad Mode.
- **Activate via Toolbar**: Tap the **Touchpad** icon in the toolbar.

### Trackpad Gestures
- **1-Finger Drag**: Smooth, pixel-perfect cursor movement.
- **1-Finger Double Tap**: Selects the word under the cursor.
- **1-Finger Long Press & Drag**: Starts continuous text selection.
- **2-Finger Drag Left/Right**: Jumps word-by-word.
- **2-Finger Swipe Up / Down**: Undo / Redo.
- **2-Finger Tap**: Inserts a space.
- **2-Finger Double Tap**: Copies selected text (or Pastes if nothing is selected).
- **2-Finger Long Press**: Continuous backspace deletion.

---

## 10. Native IME Floating & Resizable Keyboard

LeanType features an advanced **Native IME Floating Window** architecture that runs directly within the Android Input Method window rather than relying on invasive `SYSTEM_ALERT_WINDOW` system overlays. This delivers seamless background touch passthrough, zero extra permissions, and robust stability across multi-window and split-screen setups.

### 🪟 Key Floating Features
- **Native Window Migration (v4.2.5)**: Floating mode operates directly inside the native Android Input Method window using `TOUCHABLE_INSETS_REGION`. Touches outside the floating keyboard bubble pass directly to underlying applications (allowing simultaneous typing, reading, and scrolling) with zero `SYSTEM_ALERT_WINDOW` permission overhead.
- **Floating Bottom Dock**: A dedicated, clean control bar anchored beneath the key rows:
  - **`[✕]` Close / Dock Button**: 1-tap return to standard docked keyboard mode.
  - **Center Drag Pill**: Smooth, fluid multi-touch drag handle to position the keyboard anywhere on the screen.
  - **`[⤢]` Corner Resize Handle**: Proportional live resizing (0.5×–1.8× scale) with instantaneous key scaling and zero layout jumps.
- **Zero Dead-Space Layout (v4.2.6)**: Insets interception, deferred layout recalculation, and dynamic padding clamping completely eliminate docked-mode vertical dead space in apps like Telegram, ensuring the keyboard frame tightly wraps keys and controls across all display densities.
- **Persistent Floating Memory**: Optionally preserve floating mode and window coordinates across keyboard dismissals and app switches (**Settings → Preferences → Remember floating mode**).
- **Instant Activation**: Tap the **Floating Keyboard** icon on the toolbar or trigger it via custom shortcuts.

---

## 11. Dual Toolbar & Split Suggestions

Split your toolbar and suggestion strip into two independent rows for fast, unhindered access to both word predictions and quick actions.
- Configure via **Settings → Appearance → Split toolbar & suggestions**.

---

## 12. Versatile Text Expander & Modifiers

Define custom abbreviations that instantly expand into rich text templates with dynamic variables, citation cleaning, and chained text modifiers:

### Supported Dynamic Placeholders
- `%date%`: Inserts current date (YYYY-MM-DD).
- `%time%`: Inserts current local time (HH:MM).
- `%tomorrow%`: Inserts tomorrow's date.
- `%clipboard%`: Inserts latest copied clipboard content.
- `%cursor%`: Places typing cursor at this exact position after expansion.
- `%greeting%`: Inserts time-appropriate greeting (*Good morning*, *Good afternoon*, *Good evening*).
- `%bullets%` / `%list%`: Inserts templated bulleted or numbered lists.
- `%custom_variable%`: Prompts an interactive popup to fill in custom text on the fly.

### Composable Clipboard Modifiers
Transform clipboard content on the fly by appending modifiers (`%clipboard:<mod1>:<mod2>%`):
- `%clipboard:clean%` / `%clipboard:nocite%`: Automatically strips bracketed Wikipedia / academic citations (`[1]`, `[1][2]`, `[note 1]`, `[citation needed]`) and cleans formatting.
- `%clipboard:singleline%` / `%clipboard:oneline%`: Flattens multi-line text into a single line.
- `%clipboard:title%`: Converts clipboard text to Title Case.
- `%clipboard:slug%` / `%clipboard:kebab%`: Converts text into a kebab-case URL slug (e.g. `my-awesome-post`).
- `%clipboard:snake%` / `%clipboard:camel%`: Converts text to `snake_case` or `camelCase`.
- `%clipboard:upper%` / `%clipboard:lower%`: Converts text to UPPERCASE or lowercase.
- `%clipboard:trim%`: Removes leading and trailing whitespace.
- `%clipboard:unquote%`: Strips outer quotation marks.
- `%clipboard:nourl%`: Removes URLs from text.
- `%clipboard:replace(pattern, replacement)%`: Performs custom regex find-and-replace.

### Setup Instructions
1. Open **Settings → Text correction → Text Expander**.
2. Tap **+ (Add)**, define the shortcut (e.g. `cite`), and enter your expansion template (e.g. `%clipboard:clean%`).

---

## 13. Searchable Clipboard, Editing & Gestures

LeanType features a comprehensive, privacy-first clipboard manager with rich gestural editing:

- **🔍 Real-Time Search**: Filter through your entire clipboard history instantly using the inline search bar on the toolbar.
- **✏️ Swipe-Right Inline Editing**: Swipe right on any clipboard snippet to edit its text directly inside the keyboard toolbar (`[Text│] [✔] [✕]`):
  - **Tap-to-Position Cursor**: Tap anywhere in the text strip to place the cursor accurately.
  - **Gesture Support in Edit Buffer**: Swipe on the spacebar to glide the cursor horizontally, or swipe left from Backspace to delete words in the edit strip.
  - **In-Place Layout Switching**: Toggle `?123` Symbols, `Shift`, and Caps Lock directly on the bottom row without losing your active edit session.
- **🗑️ Swipe-Left to Delete with Undo**: Swipe left on any clip to remove it, backed by a 5-second timed undo bar to restore accidental deletions.
- **📌 Pin / Unpin & Folding**: Long-press any snippet to pin it permanently. Enable **Fold pinned items** to keep pinned clips collapsed under an expandable `▶ Pinned (N)` header.
- **👆 Sliding Clipboard Selection**: Hold the Clipboard key, slide your finger over the desired clip, and release to paste and return to typing immediately.
- **🖼️ Image & Screenshot History**: Captures and displays copied images and screenshots with rich visual thumbnails.

---

## 14. Screenshot Suggestions & Capture

- **Instant Suggestion**: Automatically detects newly captured screenshots (within 4 minutes) and presents a thumbnail preview in the suggestion strip for 1-tap insertion.
- **Clipboard Sync**: Automatically saves captured screenshots into your clipboard image history.
- Enable via **Settings → Text correction → Suggest recent screenshots**.

---

## 15. Emoji Search

- Search through thousands of emojis by keyword or name directly inside the emoji palette.
- **Setup**: Ensure an **Emoji Dictionary** (e.g. *Emoji English*) is enabled under **Settings → Text correction → Dictionary**.

---

## 16. Blocked Words & Regex Blacklist

Prevent offensive, sensitive, or unwanted words from ever appearing in the suggestion strip:
- Supports literal words and custom **regular expression (regex)** patterns.
- Manage rules via **Settings → Text correction → Blocked words blacklist**.

---

## 17. Privacy-First OTP Auto-Fill

- **Zero SMS Permissions (`RECEIVE_SMS`)**: Uses Android's secure `NotificationListenerService` to parse verification codes directly from incoming notifications without accessing private SMS message stores.
- **Dynamic Messaging App Selector**: Choose which specific messaging apps (Google Messages, Signal, WhatsApp, Telegram, etc.) LeanType should monitor for OTP codes.
- **1-Tap Insertion**: Automatically detects OTP codes and offers them in the suggestion strip for instant 1-tap pasting.
- Manage via **Settings → Text correction → OTP Auto-Fill**.

---

## 18. Adaptive Personal Dictionary Learning & Suggestion Engine Tuning

LeanType learns your vocabulary organically as you type while providing deep granular control over the suggestion scoring pipeline:
- **Adjustable Learning Threshold**: Choose how many times a new word must be typed (1 to 5 times) before it is automatically added to your personal dictionary.
- **Session Word Boost**: Temporarily boosts recently typed, verified words for immediate next-word ranking during active typing sessions.
- **Suggestion Balance Master Sliders**: Fine-tune the exact scoring weights between unigram frequency, bigram/ngram context, and dictionary matches (**Settings → Suggestions → Suggestion Balance**).
- **SuggestTrace & ScoreAudit Telemetry**: Built-in developer/power-user instrumentation to audit why specific words are being predicted or auto-corrected, backed by early beam pruning (`BEAM_DELTA = 60`) for optimal typing latency.
- **Google Dictionary Import**: Import existing user dictionaries exported from Gboard.
- Configure via **Settings → Suggestions** and **Settings → Text correction → Dictionary learning threshold**.

---

## 19. Gesture / Glide Typing

- Smooth swipe typing powered by native C++ spatial scoring (`libjni_latinime.so`).
- Supports floating preview text, customizable trail colors, and space-aware gesture input.
- In `standard` builds, the gesture library is downloaded automatically via **Settings → Gesture typing**.

---

## 20. Direct Switch Target IME

Map the custom keycode `-10076` (`SWITCH_TO_USER_IME`) to any toolbar key:
- Switches directly to a designated secondary input method (e.g. Japanese, Korean, or Voice IME) without opening the system IME selection dialog.
- Configure via **Settings → Preferences → Direct Switch Target IME**.

---

## 21. Custom Layout Profiles

- Create and save up to **5 persistent custom layout profiles**.
- Switch between layout profiles seamlessly while preserving active slot indices across orientation and symbol states.
- Manage via **Settings → Languages → Custom layouts**.

---

## 22. In-App Update Checker & Release Viewer

> [!NOTE]
> Available in the **Standard** (`-standard-release.apk`) build flavor.

- Automatically checks GitHub releases for updates in the background.
- Eliminates sensitive package installation permissions (`REQUEST_INSTALL_PACKAGES`) by redirecting to official GitHub Releases for safe and verified APK updates.
- View single-version changelogs directly inside the update screen.
- Configure check frequency under **Settings → Updates**.

---

## 23. Flavor Architecture & Privacy
 
LeanType is published in two purpose-built flavors to balance cloud AI capabilities and strict air-gapped offline operation:

> [!NOTE]
> **Flavor Consolidation**:  
> In **v4.2.6**, `standardfull` has been completely merged into `standard`. Users previously using `standardfull` can transition directly to `standard`.
 
| Flavor | Cloud AI | Offline AI | Voice Input | Handwriting | OCR Extraction | Translation | Update Checker | Internet Permission | Min SDK | Approx Size |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Standard (Recommended)** | ✅ | ❌ | ✅ *(Plugin)* | ✅ *(Plugin)* | ✅ *(Plugin)* | ✅ *(Plugin/AI)* | ✅ *(View Release)* | 🌐 Optional *(Opt-in)* | SDK 23 (6.0+) | **~10.8 MB** |
| **Offline** | ❌ | ✅ *(Plugin on 8.0+)* | ✅ *(Plugin)* | ✅ *(Plugin)* | ✅ *(Plugin)* | ✅ *(Plugin)* | ❌ | 🚫 **None** | SDK 21 (5.0+) | **~9.8 MB** |

> [!TIP]
> **Concurrent Installation**: The `offline` (`com.leanbitlab.leantype.offline`) build uses a unique package ID, allowing you to install it alongside `standard` on the same device!

### 🛡️ Privacy & Engineering Highlights
- **Zero-Permission Privacy Overhaul (v4.2.5)**: LeanType removed `READ_CONTACTS` (contact dictionary eliminated entirely), `SYSTEM_ALERT_WINDOW` (floating mode runs natively in the IME window), and `REQUEST_INSTALL_PACKAGES` (update checks link directly to verified GitHub releases). The `offline` flavor contains zero network permissions in its manifest.
- **Ultra-Lightweight Modular Plugin Architecture (v4.1.4 / v4.1.6)**: Heavy machine learning dependencies—including ML Kit (Handwriting, OCR, Translation) and on-device LLM inference (`llama.cpp`)—are completely unbundled into standalone companion plugins. This preserves a sub-11 MB core APK footprint while granting users total on-demand control over local AI models.

---

## 24. Offline Camera OCR & Screenshot Extraction

LeanType features an on-device OCR engine powered by ML Kit via the [LeanType OCR Plugin](https://github.com/LeanBitLab/LeanType-Ocr-Plugin) (supported across all flavors), enabling instant text extraction from live camera feeds or captured screenshots with zero internet connectivity.

### 📷 In-Keyboard Camera Scanner
- Tap the **Camera / OCR** key on the toolbar to open a live camera viewfinder embedded directly inside the keyboard window.
- **Controls**: Top flash toggle, real-time autofocus, shutter capture button, and gallery image picker fallback.
- **Instant Result Strip**: Displays recognized text immediately with 1-tap options to copy, insert into the current input field, or apply transformations.

### 🖼️ Screenshot Extraction Suggestion Pill
- Automatically detects screenshots captured on your device (within 4 minutes) and renders a unified compact pill on the suggestion strip: `[OCR] [Screenshot] [X]`.
- Tap **`[OCR]`** to extract text directly from the screenshot without leaving your current app.
- Tap **`[Screenshot]`** to paste or share the image directly.
- Tap **`[X]`** to dismiss the suggestion.

### 🛠️ Advanced Text Formatting Cleaners
Customize how extracted text is processed and formatted before insertion:
- **Casing Transformations**: Original, UPPERCASE, lowercase, Title Case, or Sentence case.
- **Line Joining Modes**: Keep original line breaks, merge all lines into a single continuous paragraph, or automatically rejoin hyphenated words split across lines (`anti-` + `gravity` $\rightarrow$ `antigravity`).
- **Punctuation Normalization**: Cleans irregular punctuation marks, curly quotes, and repeated spaces.
- **Bullet & List-Marker Stripping**: Cleans away bullet characters (`•`, `-`, `*`, `1.`, `a)`) for clean paragraph flow.
- **Whitespace & Noise Filtering**: Automatically strips leading/trailing blank spaces and filtered OCR noise artifacts.
- **Configurable Preferences**: Auto-copy recognized text to clipboard, auto-insert directly into text fields, persistent camera flash state, and search indexing support.

---

## 25. Inline Math Calculation Suggestions

Perform calculations instantly while typing in any app without switching to an external calculator:

### 🔢 How It Works
- Type any arithmetic expression followed immediately by an equals sign (`=`).
- The evaluated result appears instantly as a clean suggestion chip in the suggestion strip (e.g. typing `25*4=` offers `100`).
- Tap the chip to replace the entire typed math expression in-place with the evaluated answer.

### ➕ Supported Operations & Math Functions
- **Basic Arithmetic**: Addition (`+`), subtraction/unary negation (`-`, `−`), multiplication (`*`, `×`), division (`/`, `÷`).
- **Percentages**: e.g. `500-15%=` $\rightarrow$ `425`, `200+10%=` $\rightarrow$ `220`.
- **Exponents & Powers**: e.g. `2^8=` $\rightarrow$ `256`.
- **Grouping Parentheses**: e.g. `(12+8)/4=` $\rightarrow$ `5`.
- **High Precision**: Built with a pure Kotlin `BigDecimal` parsing engine with robust scientific formatting and division-by-zero protection.

### ⚙️ Settings
- Enable or disable via **Settings → Text correction → Inline math calculation**.

---

## 26. Custom Sound Packs & Audio Customization

LeanType includes a zero-latency native keypress audio feedback engine that delivers rich auditory tactile response:

### 🎵 12+ Built-in Audio Presets
- **iOS Tap**: Crisp, modern Apple-style click sound.
- **Mechanical Cherry MX**: Classic mechanical keyboard tactile switch clicks.
- **Thocky Mechanical**: Deep, resonant mechanical switch sound profile.
- **Vintage Typewriter**: Authentic acoustic typewriter key strikes and carriage feel.
- **Retro CRT Terminal**: Nostalgic 80s phosphor green terminal clicks.
- **Bubble Pop**: Playful, gentle bubble popping sounds.
- **Soft Velvet / Pudding**: Muted, low-profile quiet typing experience.
- **Woodblock Minimal**: Clean organic wooden percussion clicks.
- **Acoustic Marimba**: Melodic wooden bar acoustic chime feedback.
- **Modern Crisp Tick**: Subtle, high-frequency modern key tick.
- **Sci-Fi Cyberpunk**: Futuristic electronic digital interface hums.
- **8-Bit Chiptune Arcade**: Retro arcade game console key blips.

### 🌐 Remote Sound Pack Repository & Custom Imports
- **Remote Catalog**: Download additional physical modeling and synthesized instrument packs on demand from the official GitHub sound pack repository (`LeanBitLab/LeanType-Sound-Packs`).
- **Unbundled Light Footprint**: Sound packs are unbundled from the core APK to keep download sizes under 11 MB.
- **Custom `.zip` Pack Import**: Import custom sound packs packaged as a `.zip` containing a `soundpack.json` manifest and keypress audio files (`.wav` or `.ogg`).
- **Live Audition & Volume**: Audition sounds with live sample playback (▶️) and fine-tune keypress audio volume independently from system media volume.
- **Dedicated Settings Screen**: Access via **Settings → Plugins → Keypress Audio / Sound** or **Settings → Preferences → Sound on keypress**.

---

## 27. Per-App Profiles & Compatibility Engine

Certain Android apps, terminal emulators, code editors, automation tools, and web-based input fields handle text entry in non-standard ways. LeanType features a dedicated **Per-App Profiles & Compatibility Engine** that lets you customize composing, auto-correction, and key behaviors on a granular per-application basis.

Access via **Settings → Preferences → App Profiles**.

### ⚙️ Available Per-App Quirks & Overrides

| Quirk / Setting | Description & Use Case |
| :--- | :--- |
| **🌐 Web Editor Compatibility** | Forces web-safe backspace, batch sync, and internal cache bypass for text fields. Ideal for web-based text editors (e.g. Google Docs in Chrome, Notion, Obsidian web clips) that suffer from duplicated or stuck text. |
| **🕵️ Automatic Incognito** | Forces LeanType into incognito mode whenever this specific app is active. Never learns typed words, disables personal dictionary boosting, and leaves zero trace in history. |
| **🔓 Force Non-Incognito** | Overrides aggressive system incognito flags (such as `IME_FLAG_NO_PERSONALIZED_LEARNING`) set by certain apps or search fields, restoring full personal dictionary suggestions and auto-correction. |
| **⚡ Force Direct Text Commit** | Bypasses composing spans completely and commits characters directly to the input connection. Resolves input glitches in custom apps and games with broken `TextWatcher` implementations. |
| **🧩 Allow Symbol Composing** | Keeps punctuation and symbols (such as `%` and `_`) inside the active composing word instead of immediately committing them. Essential for automation tools like **Tasker** where variables such as `%var_name` would otherwise break composing. |
| **🚫 Strip No-Enter Flag** | Ignores `IME_FLAG_NO_ENTER_ACTION` set by apps (e.g. Pixel Launcher search or specific chat inputs) to ensure an Enter / Action key is always available. |
| **🔘 Action Key Override** | Overrides the bottom-right Enter key action for the target app. Options include **System Default**, **Force Newline (None)**, **Force Send**, **Force Search**, **Force Go**, **Force Next**, or **Force Done**. |
| **✏️ Auto-Correction Overrides** | Force-enable or force-disable auto-correction specifically for an app (e.g. always disabled for terminal emulators like Termux, always enabled for messaging apps). |
| **␣ Disable Auto-Spacing** | Prevents automatic space insertion before or after punctuation and symbols in sensitive coding or terminal environments. |
| **📄 Allow on Non-Editable Fields** | Keeps the keyboard available and interactable even when an app specifies a non-editable text field (`InputType.TYPE_NULL`). |

### 🔍 App Profiles Management
- **`TYPE_NULL` Out-of-the-Box Support (v4.2.6)**: Explicit show-request tracking allows LeanType to commit text seamlessly in dialer search bars, launchers, and custom non-editable views that normally block soft keyboards.
- **Tasker & Macro Integration**: "Allow Symbol Composing" enables `%var_name` dynamic placeholders for Tasker automation without prematurely breaking composing spans.
- **Instant Search & Filter**: Filter apps by name or package identifier, or toggle **Configured only** to review your active overrides.
- **Visual Status Badges**: Each application card displays colored status chips (`Web`, `Incognito`, `Direct Commit`, `Symbol Composing`, `Action`, `Auto-Correct`) for immediate at-a-glance auditing.
- **1-Tap Reset**: Easily revert any custom profile back to default with the **Reset to Default** option.

---

## 28. Hardware & Physical Keyboard Support

LeanType provides a premium, fully-integrated experience for external Bluetooth keyboards, USB keyboards, tablet folio keyboards, and foldable devices. Unlike standard IMEs that disable their UI when a physical keyboard is attached, LeanType adapts intelligently:

### ⌨️ Key Hardware Keyboard Features
- **Persistent Suggestion Pipeline**: Full predictive text, auto-correction, and next-word suggestions remain active and visible on the candidate strip.
- **Candidate Shortcuts**: Rapidly select predictions using physical keyboard number keys (`1`, `2`, `3`).
- **Full D-PAD Navigation**: Seamlessly navigate and select emojis, clipboard history entries, and toolbar actions using physical arrow keys and Enter/Space.
- **Smart Toolbar Elevation**: The smart toolbar automatically elevates above the system navigation bar and IME switcher buttons when a physical keyboard is detected.
- **Advanced Keycode Resolution**: Native support for physical numpad keycodes, NumLock modifier composing, and dead-key combining for complex international layouts.
- **Language Switching Shortcuts**: Seamless Ctrl+Space and Shift+Space keyboard shortcut handling to toggle active subtypes without UI interruptions.

---

## 29. Advanced Appearance & Key Ergonomics

Go beyond simple color themes with granular, user-level control over the keyboard's physical geometry, border radiuses, and visual feedback:

### 🎨 Ergonomic & Appearance Controls
- **Independent Corner Radii**: Independent sliders to control the corner rounding of **Normal Keys**, **Functional Keys** (Shift / Backspace / Symbols), and **Action Keys** (Enter / Search / Go) when key borders are active (**Settings → Appearance → Key borders**).
- **Dynamic Key Gaps & Padding**: Adjust horizontal and vertical key gaps, as well as independent side and bottom padding scales to match your hand ergonomics and screen dimensions.
- **Shift & Caps State Distinction**: Enhanced visual clarity through three distinct icon states for the Shift key:
  - **Outline Arrow**: Inactive / lowercase.
  - **Filled Arrow**: Single Shift active.
  - **Underlined Arrow**: Caps Lock locked.
- **Auto-Spanning Key Balance**: Proportionately expands toolbar keys symmetrically across wider displays and landscape orientations (**Settings → Appearance → Toolbar auto-spacing**).

---

## 30. Comprehensive Keycodes & Actions Reference

LeanType provides an extensible keycode architecture that allows customizing keys in:
- **Custom JSON Layouts**: Map keys via `"code": <value>` (e.g. `{"code": -9, "label": "Del"}`).
- **Simple Text Layouts**: Defined directly in custom layout `.txt` files.
- **Toolbar Customization**: Custom primary and long-press codes via **Settings → Preferences → Customize toolbar key codes**.
- **Popup Keys**: Extended keys revealed on long-pressing any primary keyboard key.

> [!TIP]
> All negative keycodes listed below are actively recognized and validated by `KeyCode.checkAndConvertCode()`. Positive integer codes correspond to standard Unicode codepoints (or ASCII values like `10` for Enter, `32` for Space, `9` for Tab).

---

### 1. ✏️ Text Editing & Deletion

| Keycode | Constant Name | Action / Description |
| :---: | :--- | :--- |
| **`-7`** | `DELETE` | Backspace; deletes the character preceding the cursor. |
| **`-8`** | `DELETE_WORD` | Backspace word; deletes the word preceding the cursor (`Ctrl` + Backspace). |
| **`-9`** | `FORWARD_DELETE` | Forward delete; deletes the character after the cursor (`KeyEvent.KEYCODE_FORWARD_DEL`). |
| **`-10`** | `FORWARD_DELETE_WORD` | Forward delete word; deletes the word following the cursor (`Ctrl` + Forward Delete). |
| **`-131`** | `UNDO` | Reverts the last text modification (`Ctrl` + `Z`). |
| **`-132`** | `REDO` | Re-applies the last undone modification (`Ctrl` + `Y`). |

---

### 2. 🧭 Cursor Navigation & Text Selection

| Keycode | Constant Name | Action / Description |
| :---: | :--- | :--- |
| **`-21`** | `ARROW_LEFT` | Moves cursor left by one character (`DPAD_LEFT`). |
| **`-22`** | `ARROW_RIGHT` | Moves cursor right by one character (`DPAD_RIGHT`). |
| **`-23`** | `ARROW_UP` | Moves cursor up by one line (`DPAD_UP`). |
| **`-24`** | `ARROW_DOWN` | Moves cursor down by one line (`DPAD_DOWN`). |
| **`-27`** | `MOVE_START_OF_LINE` | Moves cursor to the start of the current line (`MOVE_HOME`). |
| **`-28`** | `MOVE_END_OF_LINE` | Moves cursor to the end of the current line (`MOVE_END`). |
| **`-10015`** | `WORD_LEFT` | Moves cursor one word to the left (`Ctrl` + `DPAD_LEFT`, RTL aware). |
| **`-10016`** | `WORD_RIGHT` | Moves cursor one word to the right (`Ctrl` + `DPAD_RIGHT`, RTL aware). |
| **`-10010`** | `PAGE_UP` | Scrolls or moves cursor up by one page (`PAGE_UP`). |
| **`-10011`** | `PAGE_DOWN` | Scrolls or moves cursor down by one page (`PAGE_DOWN`). |
| **`-25`** | `MOVE_START_OF_PAGE` | Moves cursor to the start of the document/page. |
| **`-26`** | `MOVE_END_OF_PAGE` | Moves cursor to the end of the document/page. |
| **`-305`** | `TOGGLE_TEXT_EDIT_MODE` | Toggles the dedicated Text Editing navigation pad. |
| **`-306`** | `TOGGLE_SELECTION_MODE` | Toggles persistent Shift selection mode for arrow navigation keys. |

---

### 3. 📋 Clipboard Utilities

| Keycode | Constant Name | Action / Description |
| :---: | :--- | :--- |
| **`-31`** | `CLIPBOARD_COPY` | Copies the current text selection to the clipboard. |
| **`-32`** | `CLIPBOARD_CUT` | Cuts the current text selection to the clipboard. |
| **`-33`** | `CLIPBOARD_PASTE` | Pastes text from the primary clipboard clip. |
| **`-34`** | `CLIPBOARD_SELECT_WORD` | Automatically selects the entire word under the cursor. |
| **`-35`** | `CLIPBOARD_SELECT_ALL` | Selects all text in the active editor field. |
| **`-10009`** | `CLIPBOARD_COPY_ALL` | Selects all text and immediately copies it to the clipboard. |
| **`-36`** | `CLIPBOARD_CLEAR_HISTORY`| Clears unpinned history entries from the in-app clipboard history. |
| **`-213`** | `CLIPBOARD` | Opens the rich Clipboard History browser view. |
| **`-10071`** | `CLIPBOARD_SEARCH` | Focuses the inline search bar within the clipboard manager. |

---

### 4. 🔣 Keyboard Views, Layouts & Modes

| Keycode | Constant Name | Action / Description |
| :---: | :--- | :--- |
| **`-201`** | `ALPHA` | Switches to the standard Alphabet keyboard view. |
| **`-202`** | `SYMBOL` | Switches to the primary Symbols keyboard view. |
| **`-10001`** | `SYMBOL_ALPHA` | Toggles between Alphabet and Symbols views. |
| **`-205`** | `NUMPAD` | Switches to the dedicated numeric Numpad keyboard view. |
| **`-206`** | `VIEW_PHONE` | Opens the telephone dialer layout. |
| **`-207`** | `VIEW_PHONE2` | Opens the secondary phone symbols layout. |
| **`-212`** | `EMOJI` | Opens the interactive Emoji & Media palette. |
| **`-113`** | `SPLIT_LAYOUT` | Toggles split keyboard mode for wide screens and landscape. |
| **`-10072`** | `TOGGLE_FLOATING_KEYBOARD` | Toggles the moveable native IME floating keyboard window. |
| **`-10073`** | `TOGGLE_TOUCHPAD_MODE` | Toggles the full-keyboard touchpad / trackpad cursor surface. |
| **`-10002`** | `TOGGLE_ONE_HANDED_MODE` | Toggles one-handed keyboard mode (compact docked to left or right). |
| **`-10004`** | `SWITCH_ONE_HANDED_MODE` | Switches the one-handed dock side between left and right. |
| **`-10074`** | `HANDWRITING` | Toggles the on-device handwriting drawing canvas. |
| **`-10075`** | `CLEAR_HANDWRITING` | Clears all current drawing strokes from the handwriting canvas. |
| **`-10077`** | `OCR` | Opens the live camera viewfinder and screenshot OCR scanner. |
| **`-10076`** | `SWITCH_TO_USER_IME` | Instantly switches to the user's configured target IME keyboard. |

---

### 5. 🤖 AI Integration & Translation

| Keycode | Constant Name | Action / Description |
| :---: | :--- | :--- |
| **`-10052`** | `PROOFREAD` | Triggers AI proofreading / grammar correction on the current input. |
| **`-10053`** | `TRANSLATE` | Translates the active field or selection using configured translation engine. |
| **`-10054`** | `SHOW_TRANSLATE_LANGUAGES`| Opens the quick language selector for in-keyboard translation. |
| **`-10061`** | `CUSTOM_AI_1` | Executes prompt template assigned to Custom AI Key #1. |
| **`-10062`** | `CUSTOM_AI_2` | Executes prompt template assigned to Custom AI Key #2. |
| **`-10063`** | `CUSTOM_AI_3` | Executes prompt template assigned to Custom AI Key #3. |
| **`-10064`** | `CUSTOM_AI_4` | Executes prompt template assigned to Custom AI Key #4. |
| **`-10065`** | `CUSTOM_AI_5` | Executes prompt template assigned to Custom AI Key #5. |
| **`-10066`** | `CUSTOM_AI_6` | Executes prompt template assigned to Custom AI Key #6. |
| **`-10067`** | `CUSTOM_AI_7` | Executes prompt template assigned to Custom AI Key #7. |
| **`-10068`** | `CUSTOM_AI_8` | Executes prompt template assigned to Custom AI Key #8. |
| **`-10069`** | `CUSTOM_AI_9` | Executes prompt template assigned to Custom AI Key #9. |
| **`-10070`** | `CUSTOM_AI_10` | Executes prompt template assigned to Custom AI Key #10. |

---

### 6. ⚙️ Input Controls & Toggles

| Keycode | Constant Name | Action / Description |
| :---: | :--- | :--- |
| **`-10005`** | `SHIFT_ENTER` | Sends Shift+Enter (forces a literal newline in chat apps that send on Enter). |
| **`-10006`** | `ACTION_NEXT` | Performs the `IME_ACTION_NEXT` editor action (jump to next form field). |
| **`-10007`** | `ACTION_PREVIOUS` | Performs the `IME_ACTION_PREVIOUS` editor action (jump to previous field). |
| **`-227`** | `LANGUAGE_SWITCH` | Cycles to the next enabled language subtype. |
| **`-232`** | `IME_HIDE_UI` | Dismisses / closes the soft keyboard window. |
| **`-233`** | `VOICE_INPUT` | Initiates voice typing (Whisper on-device or system voice IME). |
| **`-244`** | `TOGGLE_INCOGNITO_MODE`| Toggles incognito private mode (disables dictionary learning). |
| **`-245`** | `TOGGLE_AUTOCORRECT` | Instantly toggles auto-correction on or off. |
| **`-301`** | `SETTINGS` | Opens LeanType Settings. |
| **`-10043`** | `TIMESTAMP` | Inserts the current localized date and time stamp at cursor. |
| **`-10051`** | `INLINE_EMOJI_SEARCH_DONE` | Confirms and closes inline emoji search. |

---

### 7. ⌨️ Modifiers & Locks

| Keycode | Constant Name | Action / Description |
| :---: | :--- | :--- |
| **`-11`** | `SHIFT` | Shift modifier; capitalizes next character or shifts symbols. |
| **`-13`** | `CAPS_LOCK` | Locks keyboard in uppercase state. |
| **`-1`** | `CTRL` | Control modifier (`KeyEvent.META_CTRL_ON`). |
| **`-2`** | `CTRL_LOCK` | Locks Control modifier. |
| **`-3`** | `ALT` | Alt modifier (`KeyEvent.META_ALT_ON`). |
| **`-4`** | `ALT_LOCK` | Locks Alt modifier. |
| **`-5`** | `FN` | Function modifier. |
| **`-6`** | `FN_LOCK` | Locks Function modifier. |
| **`-10012`** | `META` | Meta / Super / Windows modifier. |
| **`-10013`** | `META_LOCK` | Locks Meta modifier. |
| **`-10044`** | `CTRL_LEFT` | Left Control key specifically. |
| **`-10045`** | `CTRL_RIGHT` | Right Control key specifically. |
| **`-10046`** | `ALT_LEFT` | Left Alt key specifically. |
| **`-10047`** | `ALT_RIGHT` | Right Alt key specifically. |
| **`-10048`** | `META_LEFT` | Left Meta key specifically. |
| **`-10049`** | `META_RIGHT` | Right Meta key specifically. |

---

### 8. 💻 Hardware & Media Controls

| Keycode | Constant Name | Action / Description |
| :---: | :--- | :--- |
| **`-10014`** | `TAB` | Tab key (`KeyEvent.KEYCODE_TAB`). |
| **`-10017`** | `ESCAPE` | Escape key (`KeyEvent.KEYCODE_ESCAPE`). |
| **`-10018`** | `INSERT` | Insert key (`KeyEvent.KEYCODE_INSERT`). |
| **`-10040`** | `BACK` | Android Back button (`KeyEvent.KEYCODE_BACK`). |
| **`-10019`** | `SLEEP` | System Sleep key (`KeyEvent.KEYCODE_SLEEP`). |
| **`-10020`** | `MEDIA_PLAY` | Media Play (`KeyEvent.KEYCODE_MEDIA_PLAY`). |
| **`-10021`** | `MEDIA_PAUSE` | Media Pause (`KeyEvent.KEYCODE_MEDIA_PAUSE`). |
| **`-10022`** | `MEDIA_PLAY_PAUSE` | Media Play/Pause toggle (`KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE`). |
| **`-10023`** | `MEDIA_NEXT` | Next Track (`KeyEvent.KEYCODE_MEDIA_NEXT`). |
| **`-10024`** | `MEDIA_PREVIOUS` | Previous Track (`KeyEvent.KEYCODE_MEDIA_PREVIOUS`). |
| **`-10025`** | `VOL_UP` | Volume Up (`KeyEvent.KEYCODE_VOLUME_UP`). |
| **`-10026`** | `VOL_DOWN` | Volume Down (`KeyEvent.KEYCODE_VOLUME_DOWN`). |
| **`-10027`** | `MUTE` | Audio Mute (`KeyEvent.KEYCODE_VOLUME_MUTE`). |
| **`-10028` .. `-10039`** | `F1` .. `F12` | Function keys F1 through F12 (`KeyEvent.KEYCODE_F1` .. `KEYCODE_F12`). |

---

### 9. 🎨 Custom Layout Slots & Currency Keys

| Keycode | Constant Name | Action / Description |
| :---: | :--- | :--- |
| **`-10081`** | `CUSTOM1` | Activates Custom Layout Profile #1. |
| **`-10082`** | `CUSTOM2` | Activates Custom Layout Profile #2. |
| **`-10083`** | `CUSTOM3` | Activates Custom Layout Profile #3. |
| **`-10084`** | `CUSTOM4` | Activates Custom Layout Profile #4. |
| **`-10085`** | `CUSTOM5` | Activates Custom Layout Profile #5. |
| **`-801` .. `-806`** | `CURRENCY_SLOT_1` .. `_6` | Inserts the currency symbol assigned to slots 1 through 6. |

---

### 10. 📡 Custom Intents & Typography Codes

| Keycode | Constant Name | Action / Description |
| :---: | :--- | :--- |
| **`-20000`** | `SEND_INTENT_ONE` | Dispatches user-configured broadcast Intent #1. |
| **`-20001`** | `SEND_INTENT_TWO` | Dispatches user-configured broadcast Intent #2. |
| **`-20002`** | `SEND_INTENT_THREE` | Dispatches user-configured broadcast Intent #3. |
| **`1600`** | `KESHIDA` | Arabic Tatweel / Keshida elongator (`\u0640`). |
| **`8204`** | `ZWNJ` | Zero-Width Non-Joiner (`\u200C`). |
| **`8205`** | `ZWJ` | Zero-Width Joiner (`\u200D`). |
| **`12288`** | `CJK_SPACE` | CJK Fullwidth Ideographic Space (`\u3000`). |
| **`-902`** | `MULTIPLE_CODE_POINTS` | Special container key producing multi-character text sequences. |
| **`-10008`** | `NOT_SPECIFIED` | Structural dummy spacer key (disables key interaction, renders blank). |



