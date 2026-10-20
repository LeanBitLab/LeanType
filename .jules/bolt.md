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
## 2024-05-18 - Pre-compile Regex in VoiceInputManager
**Learning:** `VoiceInputManager.syncRecognizedText` instantiates `Regex("[,.?!;:]")` inside a frequent method call, increasing allocation pressure on voice typing hot paths.
**Action:** Extract repeated `Regex` instantiations in input hot paths (such as `syncRecognizedText`) into companion object private constants to reduce memory allocation and compilation overhead.
## 2024-05-18 - Pre-compile Regex in OCR formatting
**Learning:** `OcrTextFormatter.formatLines` instantiated `Regex` for bullet removal inline every time it was called when `stripBullets` was true. This causes unnecessary allocation and compilation overhead when parsing OCR lines.
**Action:** Extract frequently used formatting regexes (such as `bulletRegex`) into `object` or companion object private constants (`private val BULLET_REGEX`) to optimize regex reuse and eliminate unnecessary compilation per text parsing operation.
## 2024-05-18 - Optimize Diacritic Stripping in Suggest
**Learning:** In `Suggest.isSameIgnoringAccents`, `val DIACRITIC_REGEX = "\\p{M}+".toRegex()` was being used in combination with `Normalizer.normalize` and `replace` to strip accents. This created string allocations via `Normalizer.normalize` and `Regex.replace` even for plain ASCII strings that lack accents, which is a hot path during typing and suggestion processing.
**Action:** Replace `Regex.replace` with a manual character loop that avoids `Normalizer.normalize` entirely for pure ASCII strings and strips `Character.NON_SPACING_MARK` without regex allocation when normalizing.
## 2024-05-18 - Optimize Regex pattern detection in DictionaryFacilitatorImpl
**Learning:** `DictionaryFacilitatorImpl.isRegexPattern` used chained `String.contains()` and `String.startsWith()` calls, which internally execute full loops over the string (or delegate to regex). Replacing these chained calls with a single manual O(N) iteration reduces overhead and prevents repeatedly traversing the string.
**Action:** Replace chained string detection methods (`contains`, `startsWith`, `endsWith`) with a single manual loop over the string's indices when checking multiple characters in performance hot paths.
