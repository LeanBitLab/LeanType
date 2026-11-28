### 💖 Support Our Work

Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive. If LeanType saves you time every day, consider backing us on [GitHub Sponsors](https://github.com/sponsors/LeanBitLab) or [Open Collective](https://opencollective.com/leanbitlab-org) or simply share it with a friend!

## LeanType 4.3.9

### Added
- **Text expander sorting (#962)**: Sort shortcuts by oldest, last added or alphabetical. The bulk editor is restyled to match the app and lists shortcuts in the chosen order.
- **Language switch on space long press (#790)**: Long-press space can now switch the language. Choose input method, switch language or none in Settings > Preferences.
- **Cursor swipe distance (#942)**: A new setting in Settings > Gestures & swipes sets how far you swipe to move the cursor.
- **Learned words screen (#948)**: Settings > Languages & Layouts > Dictionaries > Learned words (also under Settings > Suggestions) lists the words learned from typing for your current languages, most used first. Search, delete single words, or use the trash can in the top bar to clear them all. Your personal dictionary and blocked words are kept.
- **Better settings search (#902, #901)**: Search finds settings by related words and by parts of words.
- **Hold physical keys for popup characters (#969)**: With a physical keyboard, holding a key shows its popup characters in the suggestion bar, following "Number of suggestions", and picking one replaces the typed character. It is off by default, in Settings > Advanced > Physical keyboards and experimental. It has not been tested with a real physical keyboard yet, so please try it and report back.

### Changed
- **Toolbar folds for swipe suggestions (#870)**: The toolbar folds away to show the suggestions of a swiped word.
- **Clear learned words (#948)**: The separate Clear learned words entries in Dictionaries and Suggestions are replaced by the trash can on the Learned words screen.
- **Auto-add learned words (#975)**: The setting "Add words to personal dictionary" is renamed "Auto-add learned words", with a description that says learned words go to the device's personal dictionary.
- **Clipboard popup**: Long-pressing a clipboard item no longer dims the list. The action popup has the same rounded corners as the clipboard cards and a faint outline.
- **Battery**: The camera, microphone and voice plugin are stopped when no longer needed.

### Fixed
- **Capital I (#961)**: A typed "i" keeps the dictionary's "I" instead of the lowercase form.
- **Swipe learning (#946)**: LeanType learns from a swiped word that you delete and retype.
- **Delete swipe (#951, #950)**: It needs more finger travel for the first words and keeps the space before a word.
- **Offline AI (#949)**: Only one generation runs at a time.
- **AI cancel key (#890)**: The cancel X shows on the key that started the request.
- **Held delete in slow apps (#972)**: Holding delete no longer keeps deleting after you release it in slow apps such as browsers. Apps that keep up still get full speed.
- **Settings screens (beta 4)**: Language and other settings screens showed no title and a cut-off page in beta 4. They display correctly again.
- **Settings reload**: Settings reload when the field's IME options change.
- **Apostrophes in learned words (#983)**: Apostrophes are no longer saved as commas, so "I'm" is not learned as "I,m". Words already learned with a comma stay until you delete them in Learned words.
- **Floating mode**: If an app closes the keyboard right after you switch to floating, floating mode is now kept, so the keyboard should come back floating. This has not been confirmed on a device.
- **Undone corrections**: If you undo more than one correction for the same word, all of them are remembered. Picking one of them by hand only clears that one.

### Choose Your Flavor

| Flavor | Primary Focus | AI Engine | Plugins Setup | Internet | Release Updater | Size |
|:---|:---|:---|:---|:---|:---|:---:|
| **`1-LeanType_4.3.9-standard-release.apk`** | **Recommended** | Cloud AI | In-app download or file import | Optional (AI/plugins) | View Release | - |
| **`2-LeanType_4.3.9-offline-release.apk`** | **Offline** | Local LLM plugin | Browser download + file import | Zero Internet (no permission) | None | - |
| **`1-LeanType_4.3.9-standard-lite-release.apk`** | **Lite** (English-only) | Cloud AI | Standard + app language import (Android 11+) | Optional (AI/plugins) | View Release | - |
| **`2-LeanType_4.3.9-offline-lite-release.apk`** | **Offline Lite** (English-only) | Local LLM plugin | Offline + app language import (Android 11+) | Zero Internet (no permission) | None | - |
