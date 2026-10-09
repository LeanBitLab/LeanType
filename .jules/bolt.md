## 2024-05-18 - Language Detector Optimization
**Learning:** `LanguageDetector.detect` uses `Regex("[^\\p{L}]+")` inside `detectByHeuristics`, creating a new Regex instance on every call. This method is called via `ProofreadHelper` which is likely triggered on user typing or interaction. Pre-compiling the Regex as a top-level constant avoids the overhead of regex compilation for every detection call.
**Action:** Always check for repeated Regex instantiations in frequently called string processing or detection methods and extract them to top-level or companion object properties.
## 2024-05-18 - StringUtils Whitespace Split Optimization
**Learning:** `StringUtils.splitOnWhitespace` creates a `Regex("\\s+")` inline every time it is called. Since this is an extension function used across settings and text processing, this causes unnecessary allocations.
**Action:** Abstract frequent inline regex usages into `private val` properties at the object or file level to avoid repeated regex compilation.

## 2024-05-18 - Pre-compile Regex in hot dictionary loops
**Learning:** Instantiating `Regex` objects inside dictionary processing loops (like adding words in `UserBinaryDictionary` or `AppsBinaryDictionary`) causes unnecessary allocations and compilation overhead on every item iteration.
**Action:** Always pre-compile `Regex` objects as `private val` properties in the class or companion object to eliminate per-iteration compilation and allocation pressure.

## 2024-05-18 - Pre-compile Regex in model importers
**Learning:** `TranslationModelImporter` and `HandwritingModelImporter` create `Regex` instances inside `detectLanguageCode` and `detectLanguageTag` methods, sometimes inside a loop parsing zip entries. This causes unnecessary overhead on every detection call.
**Action:** Extract frequently used `Regex` instances into `companion object`s as `private val`s to ensure they are compiled only once, reducing execution time and object allocation.
## 2024-05-18 - Pre-compile Regex in SpellCheckerSession
**Learning:** `AndroidWordLevelSpellCheckerSession` previously instantiated new `Regex` objects for every quote matching and punctuation splitting operation during `onGetSuggestionsInternal`, which is a highly invoked method on the spellchecker hot-path.
**Action:** Always extract frequently instantiated `Regex` objects from hot paths (like text replacement or splitting) into `companion object` private constants (e.g. `periodRegex`, `quotesStartRegex`) to reduce allocation and regex compilation overhead per keystroke.
