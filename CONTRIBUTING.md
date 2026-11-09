# Contributing to LeanType

Thank you for your interest in contributing to **LeanType**! LeanType is a privacy-first, highly customizable, and responsive Android input method editor (IME) forked from HeliBoard and AOSP LatinIME.

## Getting Started

LeanType is built using Gradle and the Android Gradle Plugin. To get started:
1. Clone the repository: `git clone https://github.com/LeanBitLab/LeanType.git`
2. Open the project in [Android Studio](https://developer.android.com/studio) (Ladybug or newer recommended) or compile via the command line.
3. Verify your local environment by assembling the debug build:
   ```bash
   ./gradlew :app:assembleStandardDebug
   ```

## Architecture & Codebase Overview

LeanType contains a blend of original AOSP IME logic, HeliBoard foundational code, modern Jetpack Compose settings, and native NDK / C++ components (such as whisper.cpp and llama.cpp).

Key components:
- **Layout Parsing**: Stored in `app/src/main/assets/layouts/`, interpreted by `KeyboardParser` and `LayoutParser`.
- **Touch & Gesture Input Handling**: `PointerTracker`, `GestureConsumer`, and swipe engines.
- **Typing Logic & State Machine**: `InputLogic` and `LatinIME`.
- **Word Suggestions & Dictionaries**: `DictionaryFacilitatorImpl`, `Suggest`, and `SuggestionStripView`.
- **Text Connection & Commit**: `RichInputConnection`.
- **Settings & Preferences**: Jetpack Compose screens under `app/src/main/java/helium314/keyboard/settings/`, backed by `SettingsValues` and `Settings`.

## Contribution Guidelines

To maintain typing responsiveness and prevent bloat, all contributions are evaluated against the following principles:

### Core Principles
- **Zero Typing Latency**: The input hot path (touch events, key dispatch, and candidate evaluation) must remain instantaneous. Never introduce blocking IPC calls, heavy synchronous allocations, or sleep delays on the main input loop.
- **Privacy by Design**: LeanType is strictly privacy-first. Network communication is isolated strictly to user-configured opt-in features (such as user-provided cloud AI endpoints in the Standard flavor). The Offline flavor remains 100% network-permission-free.
- **Anti-Bloat & Surgical Scope**: Features must serve broad typing utility. Personal, hyper-specific niche workflows or complex UI re-themes that clutter settings should be avoided.
- **Minimal Changes**: Keep changes focused and surgical. Do not reformat unrelated code, reorder methods, or introduce speculative abstractions.
- **Granular Commits**: Commit logical units separately with clear, descriptive commit messages.

### Pull Request Requirements
- **Focused Scope**: Each pull request should address a single bug fix or feature. Do not combine multiple unrelated changes into one PR.
- **Clear Motivation & Reproduction**: Provide a thorough explanation of what is modified and why. For bug fixes, explain the root cause and how the fix addresses it.
- **Verification**: Always verify your changes compile cleanly without warnings or errors via `./gradlew :app:assembleStandardDebug`.
- **Punctuation Standard**: In code comments, descriptions, documentation, and commit messages, avoid using em dashes between words; use standard punctuation (commas, hyphens `-`, colons, or parentheses) instead.

## Translations

LeanType does not use Weblate. Translations are maintained directly within the repository.
- **Submitting Translations**: You can contribute new translations or fix existing strings directly via pull requests by editing `app/src/main/res/values-<locale>/strings.xml`.
- **Automated Tooling**: When new English string keys are added to `app/src/main/res/values/strings.xml`, LeanType's internal translation hooks and tools (`tools/check_missing_translations.py`) handle syncing and missing key coverage across target locales.

## Layouts & Dictionaries

- **Layouts**: For details on the simple `.txt` and FlorisBoard `.json` layout formats, see [layouts.md](layouts.md).
- **Dictionaries**: Binary `.dict` files are maintained separately. Check existing dictionaries or submit dictionary wordlists to the [aosp-dictionaries repository](https://codeberg.org/Helium314/aosp-dictionaries).
