# LeanType Features Guide

LeanType integrates with AI providers to offer advanced proofreading and translation capabilities directly within the keyboard. This guide explains how to set up the supported providers.

## Index

| Section | Description |
| :--- | :--- |
| 🆕 **[Summary of New Features](#summary-of-new-features)** | Quick overview of what's new. |
| 🤖 **[Supported AI Providers](#supported-ai-providers)** | Start here to choose your AI backend. |
| ⚡ **[Groq](#1-groq)** | Ultra-fast cloud inference. |
| 🌟 **[Google Gemini](#2-google-gemini)** | Reliable general-purpose AI. |
| ⚙️ **[HF/OpenAI-compatible](#3-hfopenai-compatible-generic-provider)** | Use Mistral, DeepSeek, OpenRouter, etc. |
| 🧠 **[Custom AI Keys](#4-custom-ai-keys--keywords)** | Configure custom prompts and personas. |
| 🛡️ **[Offline Proofreading](#5-offline-proofreading-privacy-focused)** | Privacy-first, on-device AI. |

## Summary of New Features

| Feature | Function | Settings Location |
| :--- | :--- | :--- |
| **Multi-Provider AI** | Uses Gemini, Groq, or OpenAI to proofread/rewrite text. | `AI Integration > Set AI Provider` |
| **Offline Proofreading** | Private, on-device AI for grammar (requires downloads). | `AI Integration > Offline Proofreading` |
| **Custom AI Keys** | 10 toolbar keys with customizable prompts (supports hashtags). | `AI Integration > Custom Keys` |
| **AI Translation** | Translates selected text via your configured AI provider. | Toolbar > Translate Icon |
| **Touchpad Mode** | Swipe up on Spacebar to control cursor like a touchpad. | `Gesture typing > Vertical spacebar swipe` |
| **Split Suggestions** | Separates suggestions from toolbar for quicker access. | `Appearance > Split toolbar & suggestions` |
| **Build Variants** | Choose Standard, Offline, or Offline Lite versions. | GitHub Releases |
| **Clear Incognito** | Shows a clear "Hat & Glasses" icon when Incognito is active. | *Automatic (when Incognito)* |
| **Clipboard Search** | Search through your clipboard history directly. | Clipboard Toolbar > Search Icon |
| **Dictionary Import** | Import personal words from Google Gboard/other keyboards. | `Text correction > Dictionary > Import` |
| **Force Auto-Caps** | Toggle to ensure automatic capitalization works reliably. | `Text correction > Auto-capitalization` |
| **Emoji Search** | Search for emojis by name. | `Emoji Key > Search Icon` |
| **Screenshot Suggestion** | Suggests recently taken screenshots for quick sharing. | `Text correction > Suggest recent screenshots` |
| **Screenshot on Clipboard** | Automatically saves taken screenshots to your clipboard history. | *Automatic (when enabled)* |
| **Clipboard Undo** | Undo swipe-to-delete on clipboard items with a timed undo bar. | *Automatic (on swipe delete)* |

---

## Emoji Search

*   **Functionality**: Search for emojis by keyword/name directly from the emoji palette.
*   **Requirement**: You must load an **Emoji Main Dictionary**.
*   **How to Setup**:
    1.  Go to **Settings > Text correction > Dictionary**.
    2.  Tap on your language (e.g., **English**).
    3.  Ensure the **Emoji English** (or relevant emoji dict) is available/downloaded.
    4.  *Note*: Basic emoji search requires this dictionary to map words to emoji characters.

---

## Screenshot Suggestion

*   **Functionality**: When you take a screenshot, LeanType detects it and shows a suggestion in the suggestion strip for quick sharing. Tapping the suggestion inserts the screenshot as an image into the current input field (if the app supports image insertion).
*   **Permissions**: Requires **storage/media access** (`READ_MEDIA_IMAGES` on Android 13+, or `READ_EXTERNAL_STORAGE` on older versions). The keyboard will prompt for permission when this feature is first enabled.
*   **How to Enable** (Disabled by default):
    1.  Go to **Settings > Text correction**.
    2.  Enable **Suggest recent screenshots**.
*   **Behavior**:
    *   Screenshots taken within the last **4 minutes** are detected.
    *   A thumbnail preview is shown in the suggestion strip.
    *   Tapping the suggestion inserts the image; tapping the close button dismisses it.

---

## Screenshot on Clipboard

*   **Functionality**: Taken screenshots are automatically saved to your clipboard history for later use.
*   **Requirement**: Clipboard history must be enabled.
*   **How to Enable**: Ensure **Suggest recent screenshots** is enabled in Text correction.

---

## Clipboard Item Delete Undo

*   **Functionality**: When you swipe to delete an item from your clipboard history, a brief undo bar appears, allowing you to restore the deleted item.
*   **Behavior**: Provides a safety net against accidental deletions of important clipboard content.

---

## Supported AI Providers

| Provider | Privacy | Setup | Free Tier | Best For |
| :--- | :--- | :--- | :--- | :--- |
| **Groq** | 🟡 Average | 🟢 Easy | High | **Speed** |
| **Google Gemini** | 🔴 Low | 🟢 Easy | Generous | General Purpose |
| **HF/OpenAI-compatible** | ⚙️ *Varies* | 🟡 Medium | *Varies* | **Fully Customizable** |
| **Offline (ONNX)** | 🟢 **Best** | 🟡 Medium | ∞ Unlimited | **Privacy** |

> [!TIP]
> The **HF/OpenAI-compatible** option is fully customizable—you can change the API endpoint, token, and model to use *any* OpenAI-compatible service (OpenRouter, Mistral, DeepSeek, HuggingFace, etc.).

**Privacy Links:**
- [Groq Privacy Policy](https://groq.com/privacy-policy)
- [Google Gemini API Terms](https://ai.google.dev/gemini-api/terms)
- [OpenRouter Privacy](https://openrouter.ai/privacy)
- [HuggingFace Privacy](https://huggingface.co/privacy)

> [!IMPORTANT]
> **Sensitive Data**: **Do not process sensitive information** (passwords, credit card numbers, private addresses) using the AI Proofreading or Translation features.
>
> **Offline Version Guarantee**: The **Offline Version** physically excludes all network code at build time. It is impossible for it to connect to the internet, making it safe for all data.

---

## 1. Groq

Groq is a cloud API provider that uses custom LPUs (Language Processing Units) to deliver **extremely fast** inference speeds, making it feel almost instant compared to other cloud providers.

### Setup
1.  **Get API Key**: Visit [Groq Console](https://console.groq.com/keys) and create a key (starts with `gsk_`).
2.  **Configure in LeanType**:
    *   **Provider**: Select **Groq**.
    *   **API Token**: Paste your Groq API Key.

### Available Models
| Model ID | Context | Description |
| :--- | :--- | :--- |
| `llama-3.3-70b-versatile` | 128k | **Best Overall**. High intelligence. |
| `llama-3.1-8b-instant` | 128k | **Fastest**. Quick grammar fixes. |
| `meta-llama/llama-4-scout-17b-16e-instruct` | 128k | New scout model. Good balance. |
| `meta-llama/llama-4-maverick-17b-128e-instruct`| 128k | New maverick model. Better reasoning. |
| `qwen/qwen3-32b` | 128k | Good speed and logic. |
| `openai/gpt-oss-120b` | 128k | Large OSS GPT model. |
| `openai/gpt-oss-20b`  | 128k | Fast OSS GPT model. |
| `groq/compound`| 128k | Deep-thinking internal model. |
| `groq/compound-mini` | 128k | Optimized internal model. |
| `moonshotai/kimi-k2-instruct` | 128k | Lightweight reasoning model. |
| `moonshotai/kimi-k2-instruct-0905` | 128k | Specialized kimi instruction model. |
| `canopylabs/orpheus-v1-english` | 128k | CanopyLabs English priority model. |
| `canopylabs/orpheus-arabic-saudi` | 128k | CanopyLabs Arabic dialect model. |
| `allam-2-7b` | 128k | Efficient 7B general use model. |

---

## 2. Google Gemini

### Get an API Key
1.  Go to [Google AI Studio](https://aistudio.google.com/app/apikey).
2.  Click **Create API Key**.
3.  Copy the key.

### Configuration
1.  Go to **Settings > AI Integration > Gemini API Key**.
2.  Paste your API key.
3.  Select a model.

### Available Models
| Model ID | Description |
| :--- | :--- |
| `gemini-2.5-flash` | **Fastest**. Great for quick tasks. |
| `gemini-2.5-pro` | High intelligence. Best overall quality. |
| `gemini-flash-latest` | Fast and capable (latest flash). |
| `gemini-flash-lite-latest` | Lightweight flash variant. |
| `gemini-pro-latest` | Latest pro model. |
| `gemini-3.1-pro-preview` | Next-gen pro preview. |
| `gemini-3.1-pro-preview-customtools` | Pro preview with custom tools support. |
| `gemini-3-pro-preview` | Gemini 3 pro preview. |
| `gemini-3-flash-preview` | Gemini 3 flash preview. |
| `deep-research-pro-preview-12-2025` | Deep research specialized model. |
| `gemma-3-27b-it` | Large Gemma model. High quality. |
| `gemma-3-12b-it` | Mid-size Gemma model. Good balance. |
| `gemma-3-4b-it` | Compact Gemma model. Fast. |
| `gemma-3-1b-it` | Smallest Gemma model. Ultra-light. |
| `gemma-3n-e4b-it` | Efficient Gemma variant (4B). |
| `gemma-3n-e2b-it` | Efficient Gemma variant (2B). |

---

## 3. HF/OpenAI-compatible (Generic Provider)

This provider supports any service using the standard OpenAI Chat Completion API format.

### A. HuggingFace Inference API

#### Setup
1.  **Get Token**: Go to [HuggingFace Settings](https://huggingface.co/settings/tokens) and create a 'Read' token.
    *   *Note*: HuggingFace may require you to add a valid payment method to your account to "unlock" the Inference API, even for the free tier (to prevent abuse).
2.  **Configure in LeanType**:
    *   **Provider**: Select **HF/OpenAI-compatible**.
    *   **API Token**: Paste your HF Access Token.
    *   **API Endpoint**: `https://api-inference.huggingface.co/models/<USER>/<MODEL>/v1/chat/completions`
    *   **Model Name**: `<USER>/<MODEL>` (e.g., `meta-llama/Meta-Llama-3-8B-Instruct`).

### B. OpenRouter / Other Providers

1.  **API Endpoint**: Enter the provider's completion URL.
    *   *OpenRouter*: `https://openrouter.ai/api/v1/chat/completions`
    *   *DeepSeek*: `https://api.deepseek.com/chat/completions`
    *   *Mistral*: `https://api.mistral.ai/v1/chat/completions`
    *   *OpenAI*: `https://api.openai.com/v1/chat/completions` (Default)
2.  **API Token**: Enter your API Key from that provider.
3.  **Model Name**: Enter the exact model ID from the provider's documentation (e.g., `deepseek-chat`, `mistral-large-latest`, `gpt-4o-mini`).

---

## 4. Custom AI Keys & Keywords

You can assign custom prompts to 10 specific keys in the toolbar. These keys can "act" differently based on the keywords (hashtags) you include in your prompt.

### How to Use
1.  Go to **Settings > AI Integration > Custom Keys**.
2.  Tap a key (1-10) to configure it.
3.  Enter your instructions. You can use the following hashtags to control the AI's behavior and output format.

### AI Persona Keywords
Add these to your prompt to enforce a specific role.

| Keyword | Persona / Behavior | System Instruction Added |
| :--- | :--- | :--- |
| `#editor` | **Text Editor** | "Output ONLY the edited text. Do not add any conversational filler." |
| `#outputonly` | **Strict Output** | "Output ONLY the result. Do not add introductions or explanations." |
| `#proofread` | **Proofreader** | "Fix grammar and spelling errors. Output ONLY the fixed text." |
| `#paraphrase` | **Rewriter** | "Rewrite using different words while keeping the meaning." |
| `#summarize` | **Summarizer** | "Provide a concise summary." |
| `#expand` | **Writer** | "Expand on the text with more details." |
| `#toneshift` | **Tone Adjuster** | "Adjust the tone as requested." |
| `#generate` | **Content Generator** | "You are a creative content generator. Output ONLY content." |

### Input Handling Keywords
Control how the result is inserted.

| Keyword | Behavior | Use Case |
| :--- | :--- | :--- |
| **(Default)** | **Replace**: The AI output replaces the selected text or the entire text field content. | Proofreading, rewriting, summarizing. |
| `#append` | **Append**: The AI output is added to the **end** of the text field (or selection) instead of replacing it. | Generating replies, continuing a story, adding a sign-off. |
| `#showthought` | **Show Thinking**: Preserves the "thinking" process (e.g., `<think>...</think>`) from reasoning models like Qwen. | Debugging reasoning, seeing the AI's thought process. |

### Examples

**1. Standard Proofreading (Replace)**
> Prompt: `Fix grammar #proofread`
> *Result: Replaces your text with the corrected version.*

**2. Generate a Reply (Append)**
> Prompt: `Generate a polite decline to this invitation #generate #append`
> *Result: Keeps the original invitation text and adds your polite decline at the end.*

**3. Strict Rewriting**
> Prompt: `Rewrite this to be more professional #editor`
> *Result: Replaces text with professional version, guaranteeing no "Here is the text:" prefixes.*

**4. Tone Shift**
> Prompt: `Make this sound more enthusiastic #toneshift`
> *Result: Rewrites the text with high energy and exclamation points, keeping the core message.*

> [!TIP]
> **Hashtags are Optional**: You can write purely custom prompts (e.g., "Translate to French"). However, without a hashtag like `#outputonly` or `#editor`, the AI might act like a chatbot (e.g., responding with "Sure! Here is the translation: ..."). Using these keywords automatically injects strict system instructions to ensure you get *only* the result you want.

---

## Privacy
*   **Data**: Text is sent directly from your device to the chosen API provider. No intermediate servers are used.

## 5. Offline Proofreading (Privacy Focused)

**Note**: This feature is only available in the "Offline" build flavor of LeanType.

Offline proofreading runs entirely on your device using the ONNX Runtime engine. No data leaves your device.

> [!NOTE]
> **Status: Beta / Experimental**
> This feature is in a test phase. The engine is designed to be compatible with various T5-based ONNX models (Basic, Quantized, KV-Cache). We encourage you to experiment with different models to find the best balance of speed and accuracy for your device.

### Setup Instructions

1.  **Download Model Files**: Download the **Encoder**, **Decoder**, and **Tokenizer** for your chosen model from the table below.
2.  **Configure App**:
    *   Go to **Settings > Advanced**.
    *   **Encoder Model**: Select the downloaded `.onnx` encoder file.
    *   **Decoder Model**: Select the downloaded `.onnx` decoder file.
    *   **Tokenizer**: Select the `tokenizer.json` file.
    *   **System Instruction**: Enter the text specified in the "System Instruction" column for your model (leave empty if specified).

### Recommended Models

| Model & Purpose | Performance / Size | System Instruction | Download Links (Direct) |
| :--- | :--- | :--- | :--- |
| **Visheratin T5 Tiny**<br>*(Grammar Correction Only)* | ⚡ **Fastest**<br>~35 MB<br>Low RAM usage | **Empty**<br>(Leave blank) | • [Encoder](https://huggingface.co/visheratin/t5-efficient-tiny-grammar-correction/resolve/main/encoder_model_quant.onnx)<br>• [Decoder](https://huggingface.co/visheratin/t5-efficient-tiny-grammar-correction/resolve/main/init_decoder_quant.onnx)<br>• [Tokenizer](https://huggingface.co/visheratin/t5-efficient-tiny-grammar-correction/tree/main) |
| **Flan-T5 Small**<br>*(Translation & General)* | 🐢 **Slower**<br>~300 MB<br>Higher accuracy | **Required**<br>`fix grammar: `<br>or<br>`translate English to Spanish: ` | • [Encoder](https://huggingface.co/Xenova/flan-t5-small/resolve/main/onnx/encoder_model_quantized.onnx)<br>• [Decoder](https://huggingface.co/Xenova/flan-t5-small/resolve/main/onnx/decoder_model_quantized.onnx)<br>• [Tokenizer](https://huggingface.co/Xenova/flan-t5-small/tree/main) |

*Note: For Flan-T5, the quantized models linked above are standard recommendations. Users have also reported success with `bnb4` quantized variants if available.*
