### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## LeanType 4.3.7

### Changed
- **Clipboard listener setting (#911, #915)**: Turning the clipboard listener on or off now applies right away, without restarting the keyboard. Clips that cannot be read are now logged.
- **Contractions (#916)**: Contractions typed without the apostrophe are promoted in suggestions, and invalid words can now be corrected by case only.
- **Suggestion removal and blocking (#918)**: Removing a suggestion hides only that dictionary word from predictions, capitalized variants are no longer blocked, and your personal dictionary overrides blocks. A toast confirms removal from the suggestion strip.

### Fixed
- **Translucent accent colors (#909, #913)**: Fixed a crash when picking the action key icon color with a translucent accent.
- **Duplicate suggestions (#914)**: Title case duplicates of words typed in lowercase are dropped from suggestions.

### Maintenance
- **Docs (#906, #907)**: README redesigned to be compact and easier to scan, with a More LeanBitLab apps section.

### Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.7-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or file import | Optional (AI/plugins) | View Release | - |
| **`2-LeanType_4.3.7-offline-release.apk`** | **Offline** | Local LLM plugin | Browser download + file import | Zero Internet (no permission) | None | - |
| **`1-LeanType_4.3.7-standard-lite-release.apk`** | **Lite** (English-only) | Cloud AI | Standard + app language import (Android 11+) | Optional (AI/plugins) | View Release | - |
| **`2-LeanType_4.3.7-offline-lite-release.apk`** | **Offline Lite** (English-only) | Local LLM plugin | Offline + app language import (Android 11+) | Zero Internet (no permission) | None | - |
