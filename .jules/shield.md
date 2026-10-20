## 2024-10-24 - Surrogate pairs and Character.isLetter in WordTokenizer
**Learning:** `Character.isLetter(char)` does not reliably handle emojis or characters outside the BMP (Basic Multilingual Plane) that are encoded as 2-character surrogate pairs. When iterating string arrays or performing substring lookups on indexes, accessing a single `char` of a 2-char pair (e.g. `word[apostropheIdx - 1]`) to evaluate `Character.isLetter` will erroneously return false, as it only evaluates the surrogate half rather than the complete character.
**Action:** When testing or validating components involving boundary lookups around punctuation (like in `isContraction` or split logic), always include unit tests with Unicode characters outside the BMP (such as Deseret capital letters like `\uD801\uDC37` or emojis) to document the runtime behavior and protect against regressions, regardless of if the current implementation has limitations.

## 2025-02-15 - Unit tests for CapsModeUtils
**Learning:** Testing string utility logic like CapsModeUtils can still require the Robolectric framework since it often depends on Android-specific classes like `android.text.TextUtils` under the hood. Furthermore, checking combinations of logical states inside complex state machines like text capitalizer logic requires detailed parameter combinations, especially regarding abbreviation marker flags and German rule configurations.
**Action:** When adding tests for classes that wrap around Android framework components such as `TextUtils`, `InputType`, or `Locale` classes, explicitly use `@RunWith(RobolectricTestRunner::class)` even for seemingly pure string logic utilities, to prevent missing framework components runtime exceptions.

## 2025-02-17 - Unit tests for ScriptUtils
**Learning:** Testing string utility logic involving scripts requires careful consideration of unsupported or invalid fallback scripts. For instance, testing `Locale("ja").script()` reveals that `ScriptUtils` gracefully falls back to `SCRIPT_LATIN` rather than breaking, even though Japanese is unsupported.
**Action:** When creating tests for locale scripts or handling unknown/fallback contexts, include assertions to verify that invalid configurations gracefully fallback or throw exactly the expected exception without leaking framework level crashes.

## 2025-02-17 - Unit tests for InputTypeUtils
**Learning:** Utilities that parse input type flags often depend on standard constants in `InputType` and `EditorInfo`. Furthermore, checking states often requires understanding package quirks loaded through `AppQuirksManager`. Running these tests efficiently without manually setting up the entire mock context is possible by relying on Robolectric, as it correctly handles `android.view.inputmethod.EditorInfo` instantiation and flag masking for text parsing.
**Action:** When adding unit tests for utilities handling input frameworks (such as `InputTypeUtils`), explicitly use `@RunWith(RobolectricTestRunner::class)` to handle dependencies on `EditorInfo` and framework constants, and ensure you cover edge cases involving bits masked out by classes (e.g. `TYPE_MASK_CLASS` vs `TYPE_MASK_VARIATION`).

## 2025-02-18 - Unit tests for TextRange
**Learning:** Testing classes that manipulate or read from Android `Spanned` texts and `SuggestionSpan` objects (such as `TextRange.getSuggestionSpansAtWord()`) requires dependencies from the Android Framework. These dependencies handle bounds tracking, flag masking, and class resolutions dynamically within the spannable object.
**Action:** When adding tests for text and span manipulation logic, use `@RunWith(RobolectricTestRunner::class)` to provide a complete and functional implementation of the `Spanned` interface and avoid `NoClassDefFoundError` or `NullPointerException` errors during span retrieval operations.

## 2025-02-18 - Unit tests for CombinedFormatUtils
**Learning:** Utilities formatting internal configurations (like dictionaries and probability info mapping to files or logging systems) might combine multiple attributes where edge cases (such as missing dict IDs, missing ngram info, or empty shortcuts) aren't immediately clear from the method signature. Testing `CombinedFormatUtils` proves that when writing string-manipulation utility tests, testing all internal property flags correctly formats the output exactly as specified by dictionary protocol constants.
**Action:** When adding tests for utility formatters or string builders used for file protocols or dictionary formats, always add comprehensive branch coverage for flag values (e.g., `not_a_word=true`, `historicalInfo`, and absence of ID keys) as boundary values, and avoid mock frameworks by using concrete instances of domain objects like `WordProperty` and `ProbabilityInfo` where possible.

## 2025-02-18 - Unit tests for LocaleUtils
**Learning:** Testing string utility logic for formatting configuration and tracking variables often depends on core frameworks. LocaleUtils modifies global device Context configurations based on language, which requires a complete runtime to correctly set and interact with `android.content.res.Configuration` and Context. Using Robolectric provides a functional implementation of these properties.
**Action:** When testing classes operating on Android Context extensions and locale configurations, use `@RunWith(RobolectricTestRunner::class)` to bypass null or unimplemented pointer errors and seamlessly interact with resource contexts without extensive mocking.

## 2025-02-18 - Unit tests for LanguageDetector
**Learning:** Testing string utility logic for language detection requires broad Unicode support. Testing these cases with Robolectric is useful because `LanguageDetector` depends on Android classes such as `android.view.textclassifier.TextClassificationManager` for fallback capabilities.
**Action:** When adding tests for string utility logic involving language detection that uses Android framework features, use `@RunWith(RobolectricTestRunner::class)` to safely handle any dependencies on framework components like `TextClassificationManager`.

## 2025-02-18 - Unit tests for ExecutorUtils
**Learning:** Testing pure concurrency and task management utilities like `ExecutorUtils` which manage thread lifecycles, runnables, and interrupt exceptions requires carefully structured deterministic logic in test suites to prevent flakiness. Testing the `RunnableChain` inner component proves that standard Junit assertions combined with stateful counters and checking standard thread interruption flags reliably verify custom thread orchestration without external system or timing dependencies.
**Action:** When adding unit tests for concurrency utilities and thread managers (like ExecutorUtils chaining logic), avoid using non-deterministic sleeps or complex mocks. Instead, rely on deterministic sequence flags, counters, and verifying correct handling of standard `Thread.interrupted()` signals to ensure fast, stable test executions.

## 2026-10-17 - Unit tests for NgramContextUtils
**Learning:** Testing string utility logic that handles boundary tracking for punctuation contexts, word connectors, and sentence terminators depends on `SpacingAndPunctuations`. `SpacingAndPunctuations` accesses string arrays using a standard Android `Resources` object. Thus, a valid Android configuration and initialized resources are required.
**Action:** When adding tests for Ngram processing logic or utility methods that depend on `SpacingAndPunctuations`, use `@RunWith(RobolectricTestRunner::class)` to safely handle any dependencies on framework components such as Context and Resources for configuration arrays.

