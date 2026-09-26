#!/usr/bin/env python3
import os
import sys
import argparse
import xml.etree.ElementTree as ET

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "res")
BASE_STRINGS = os.path.join(RES_DIR, "values", "strings.xml")
OUTPUT_DIR = os.path.join(REPO_ROOT, "docs", "i18n_prompts")

PROMPT_TEMPLATE = """# Translation Task: {lang_title} ({folder}) - Part {part_num} of {total_parts}

Translate the following Android XML string resources for LeanType Keyboard into {lang_title}.

## Strict Translation Guidelines:
1. XML FORMATTING & ESCAPING:
   - Output ONLY raw `<string name="...">...</string>` elements.
   - Escape all double quotes INSIDE the string text as \\" (e.g. \\"text\\", not "text").
   - Escape all single apostrophes/quotes as \\' (e.g. don\\'t, c\\'est, пам\\'ять).
   - Encode ampersands as &amp; (never use raw &).
   - Do NOT skip any keys, including category headers (e.g. settings_screen_*, settings_category_*).
2. PRESERVE TECHNICAL TERMS & PLACEHOLDERS:
   - "LeanType" must NEVER be translated or altered.
   - "AI" must STRICTLY remain the Latin letters "AI" (never translate to KI, IA, ИИ, ШІ, Yapay Zeka, Kunstmatige Intelligentie, etc.).
   - Preserve technical acronyms and brands in Latin alphabet: RAM, GGUF, LLM, Whisper, FUTO, Groq, Gemini, OpenAI, Hugging Face, GitHub Sponsors, Open Collective, DND, Bluetooth, OCR, API, APK, URL, HTTP, HTTPS, SSL.
   - Preserve all format specifiers exactly as they are (%s, %d, %1$s, %1$d).

```xml
{xml_content}
```
"""

def get_missing_strings(target_folder):
    target_path = os.path.join(RES_DIR, target_folder, "strings.xml")
    if not os.path.exists(target_path):
        return None

    base_tree = ET.parse(BASE_STRINGS)
    base_elems = {e.attrib["name"]: (e.text or "") for e in base_tree.getroot() if e.tag == "string"}

    target_tree = ET.parse(target_path)
    target_keys = set(e.attrib["name"] for e in target_tree.getroot() if e.tag == "string")

    missing = []
    for k in base_elems:
        if k not in target_keys:
            missing.append((k, base_elems[k]))
    return missing

def export_language(lang_code, chunk_size=300):
    folder = lang_code if lang_code.startswith("values-") else f"values-{lang_code}"
    missing = get_missing_strings(folder)
    if missing is None:
        print(f"Error: {folder} not found in {RES_DIR}")
        return

    lang_name = folder.replace("values-", "")
    print(f"[{folder}] Total missing strings: {len(missing)}")

    if len(missing) == 0:
        print(f"[{folder}] Already 100% translated!")
        return

    target_out_dir = os.path.join(OUTPUT_DIR, lang_name)
    os.makedirs(target_out_dir, exist_ok=True)

    # Split into balanced parts of at most ~320 strings each
    num_parts = max(1, (len(missing) + 319) // 320)
    actual_chunk_size = (len(missing) + num_parts - 1) // num_parts
    chunks = [missing[i:i + actual_chunk_size] for i in range(0, len(missing), actual_chunk_size)]
    total_parts = len(chunks)

    created_files = []
    for idx, chunk in enumerate(chunks, 1):
        xml_lines = []
        for k, v in chunk:
            clean_v = v.replace("&", "&amp;") if "&amp;" not in v else v
            xml_lines.append(f'<string name="{k}">{clean_v}</string>')

        xml_block = "\n".join(xml_lines)
        md_content = PROMPT_TEMPLATE.format(
            lang_title=lang_name,
            folder=folder,
            part_num=idx,
            total_parts=total_parts,
            xml_content=xml_block
        )

        out_file = os.path.join(target_out_dir, f"{lang_name}_part{idx}.md")
        with open(out_file, "w", encoding="utf-8") as f:
            f.write(md_content)
        created_files.append((out_file, len(chunk)))

    print(f"[{folder}] Created {total_parts} prompt file(s) in {target_out_dir}:")
    for f, count in created_files:
        print(f"  - {os.path.basename(f)} ({count} strings)")

def main():
    parser = argparse.ArgumentParser(description="Export missing i18n strings as structured markdown prompts for external LLMs.")
    parser.add_argument("language", nargs="?", help="Language code or folder name (e.g. uk, values-uk, zh-rCN)")
    parser.add_argument("--all", action="store_true", help="Export prompts for all languages with missing translations")
    parser.add_argument("--chunk-size", type=int, default=300, help="Number of strings per prompt file (default: 300)")

    args = parser.parse_args()

    if args.all:
        dirs = sorted([d for d in os.listdir(RES_DIR) if d.startswith("values-")])
        for d in dirs:
            export_language(d, chunk_size=args.chunk_size)
    elif args.language:
        export_language(args.language, chunk_size=args.chunk_size)
    else:
        parser.print_help()

if __name__ == "__main__":
    main()
