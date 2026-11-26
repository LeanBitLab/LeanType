### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## LeanType 4.3.7

> **Note:** Lite builds also run on Android 6.0+, but importing other app languages (language packs) needs Android 11+.

### Added
- **Learns from corrections after a swipe (#928)**: When you replace a swiped word with another suggestion, your pick gets a small boost in later swipes where the choice is close.
- **Remove downloaded translations (#927)**: Storage and cache now lists the app translations you added and lets you remove them to free space.

### Changed
- **Personal dictionary shortcuts (#924)**: With Auto-correct shortcuts on, exact personal dictionary shortcuts now correct typed words even when they are valid words (e.g. fir to for).
- **Contractions (#916)**: Contractions typed without the apostrophe are promoted in suggestions, and invalid words can now be corrected by case only.
- **Suggestion removal and blocking (#918)**: Removing a suggestion hides only that dictionary word, capitalized variants are no longer blocked, and your personal dictionary overrides blocks. A toast confirms removal from the suggestion strip.
- **Smoother gesture trail (#929)**: The swipe trail fades, tapers and curves more smoothly.
- **Clipboard listener setting (#911, #915)**: Turning the clipboard listener on or off now applies right away, without restarting the keyboard. Clips that cannot be read are now logged.

### Fixed
- **Handwriting, camera and voice errors (#930)**: The keyboard now says when handwriting is not recognized or the camera fails to start, with tap to retry for the camera. Voice typing errors stay visible instead of vanishing at once.
- **Duplicate suggestions (#914, #923)**: Title case and all caps copies of lowercase words are dropped from suggestions, and words swiped mid-sentence no longer come out in title case.
- **English fallback words**: Languages without a word list no longer show English words as suggestions.
- **Floating toolbar keys (#922)**: Pinned and full toolbar keys share one clamped size.
- **Long-press popups (#920, #922)**: Tall popups (e.g. Vietnamese) no longer close when sliding above the keyboard.
- **Translucent accent colors (#909, #913)**: Fixed a crash when picking the action key icon color with a translucent accent.
- **What's new dialog**: The sponsor buttons are stacked so their labels fit.

### Maintenance
- **Docs (#906, #907)**: README redesigned to be compact and easier to scan, with a More LeanBitLab apps section. The feature guide covers the changes in this release.

### Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.7-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or file import | Optional (AI/plugins) | View Release | - |
| **`2-LeanType_4.3.7-offline-release.apk`** | **Offline** | Local LLM plugin | Browser download + file import | Zero Internet (no permission) | None | - |
| **`1-LeanType_4.3.7-standard-lite-release.apk`** | **Lite** (English-only) | Cloud AI | Standard + app language import (Android 11+) | Optional (AI/plugins) | View Release | - |
| **`2-LeanType_4.3.7-offline-lite-release.apk`** | **Offline Lite** (English-only) | Local LLM plugin | Offline + app language import (Android 11+) | Zero Internet (no permission) | None | - |
