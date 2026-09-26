# Translation Task: uk (values-uk) - Part 3 of 3

Translate the following Android XML string resources for LeanType Keyboard into uk.

## Strict Translation Guidelines:
1. XML FORMATTING & ESCAPING:
   - Output ONLY raw `<string name="...">...</string>` elements.
   - Escape all double quotes INSIDE the string text as \" (e.g. \"text\", not "text").
   - Escape all single apostrophes/quotes as \' (e.g. don\'t, c\'est, пам\'ять).
   - Encode ampersands as &amp; (never use raw &).
   - Do NOT skip any keys, including category headers (e.g. settings_screen_*, settings_category_*).
2. PRESERVE TECHNICAL TERMS & PLACEHOLDERS:
   - "LeanType" must NEVER be translated or altered.
   - "AI" must STRICTLY remain the Latin letters "AI" (never translate to KI, IA, ИИ, ШІ, Yapay Zeka, Kunstmatige Intelligentie, etc.).
   - Preserve technical acronyms and brands in Latin alphabet: RAM, GGUF, LLM, Whisper, FUTO, Groq, Gemini, OpenAI, Hugging Face, GitHub Sponsors, Open Collective, DND, Bluetooth, OCR, API, APK, URL, HTTP, HTTPS, SSL.
   - Preserve all format specifiers exactly as they are (%s, %d, %1$s, %1$d).

```xml
<string name="app_quirks_force_direct_commit_summary">Bypass composing spans and commit characters directly to input connection (fixes broken text watchers)</string>
<string name="app_quirks_allow_symbol_composing">Allow Symbol Composing</string>
<string name="app_quirks_allow_symbol_composing_summary">Keep symbols like underscores inside composing words instead of committing immediately (fixes Tasker variable fields)</string>
<string name="app_quirks_disable_auto_space">Disable Auto-Spacing</string>
<string name="app_quirks_disable_auto_space_summary">Never insert automatic spaces before or after punctuation and symbols</string>
<string name="app_quirks_badge_direct_commit">Direct Commit</string>
<string name="app_quirks_badge_symbol_composing">Symbol Composing</string>
<string name="app_quirks_badge_no_auto_space">No Auto-Space</string>
<string name="app_quirks_allow_type_null">Allow on Non-Editable Fields</string>
<string name="app_quirks_allow_type_null_summary">Keep keyboard available even when the app specifies no editable text field (InputType.TYPE_NULL)</string>
<string name="app_quirks_badge_allow_type_null">TYPE_NULL</string>
<string name="app_quirks_autocorrect_title">Auto-Correction</string>
<string name="app_quirks_autocorrect_default">Follow Global Settings</string>
<string name="app_quirks_autocorrect_enable">Always Enabled</string>
<string name="app_quirks_autocorrect_disable">Always Disabled</string>
<string name="app_quirks_badge_autocorrect_on">Auto-Correct ON</string>
<string name="app_quirks_badge_autocorrect_off">Auto-Correct OFF</string>
```
