### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## LeanType 4.3.6

### Added
- **Language Packs and Lite Builds (#898, #899, #900)**: New English-only Lite APKs for Android 11+ are about 4 MB smaller. Other app languages come as language packs, downloaded in Languages & Layouts > App translation or imported from a file in the offline flavor. The regular APKs keep all languages and stay the main download.
- **What's New Dialog (#894)**: After an update, the changelog opens once on the first settings visit. The support section moves to the top of Updates and the sponsor dialog gets a cleaner look.

### Changed
- **Reorganised Settings (#887, #892)**: The main screen is grouped under Keyboard, Typing, Extras and System. Gesture Typing becomes Gestures & swipes, App Profiles moves to Advanced, Remember language per app to Languages, and custom currency and timestamp format to Preferences > Additional keys. Search long-press on AI and Translation settings opens their own screens (#873).
- **Offline AI Chat Format (#888)**: Qwen and LFM2 models now get the chat format they expect, so they answer better and Qwen no longer spends its tokens thinking (unless "show thinking" is on). The log keeps only text lengths, never your text.
- **Wider Landscape Split**: The split keyboard in landscape now uses the maximum split distance by default. You can still adjust it with the split distance slider.

### Fixed
- **Doubled Single Letters in Browsers (#880)**: In Chromium browsers such as Brave, typing a one-letter word and pressing space could double it ("a" became "aa"). The word is now finished in place in web fields, so the letter appears once.
- **Toolbar Under the Taskbar Buttons (#880)**: With a physical keyboard on tablets with a taskbar, the toolbar-only bar no longer sits under the back button and handle.
- **Caps Lock on PC QWERTY (#883)**: Caps lock now only capitalizes letters, like a physical keyboard, instead of typing the shifted symbols on the number row and punctuation keys.
- **Hebrew Symbols Page (#882)**: Hebrew is back to the normal symbols layout instead of the Arabic one it used since 4.2.9.
- **Delete Word Swipe While Typing (#878)**: One swipe now deletes a word you are still typing, and the suggestion strip no longer appends a prediction without a space.
- **Sound Packs (#896)**: The first preview and key presses after choosing a pack are no longer silent, and switching packs quickly no longer mixes or loses sounds. Sound toggles apply right away.
- **Space Swipe at the Edge of the Text (#895)**: Moving the cursor with a space swipe now stops at the edge of the text instead of moving the focus to another field (part of #891).
- **AI Model Pickers (#886)**: A saved model that the provider no longer lists stays in the list. The Gemini voice default moves from the retired `gemini-2.0-flash` to `gemini-flash-latest`.
- **Settings Navigation (#892)**: With several settings windows open, a tap on a search result or link now always works.
- **Setup Title (#898)**: The space next to the app name in the setup screen title was missing in 25 languages and the title was empty in Russian.

### Maintenance
- **Translations (#893, #897)**: Japanese, Korean, Vietnamese, Traditional Chinese, Persian, Bengali, Tamil and Malay are now complete. Unused strings and near-empty UI translations (including Malayalam) were dropped, and the app language picker lists only languages with a real translation. Keyboard layouts, dictionaries and spell checkers stay.
- **Docs and Release Checks (#899, #905)**: README and FEATURES.md follow the reorganised settings, with a settings map generated from the code. CI flags outdated settings paths and checks the release notes format.

### Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.6-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or file import | Optional (AI/plugins) | View Release | - |
| **`2-LeanType_4.3.6-offline-release.apk`** | **Offline** | Local LLM plugin | Browser download + file import | Zero Internet (no permission) | None | - |
| **`1-LeanType_4.3.6-standard-lite-release.apk`** | **Lite** (English-only) | Cloud AI | Standard + app language import (Android 11+) | Optional (AI/plugins) | View Release | - |
| **`2-LeanType_4.3.6-offline-lite-release.apk`** | **Offline Lite** (English-only) | Local LLM plugin | Offline + app language import (Android 11+) | Zero Internet (no permission) | None | - |
