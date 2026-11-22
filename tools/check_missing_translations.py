#!/usr/bin/env python3
"""
Tool: check_missing_translations.py
Description: Analyzes Android string resources across locales and identifies
missing translation keys compared against the base values/strings.xml.
"""

import os
import sys
import argparse
import json
import xml.etree.ElementTree as ET
from typing import Dict, List, Tuple, Optional, Set

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "res")
BASE_STRINGS = os.path.join(RES_DIR, "values", "strings.xml")

# Comprehensive mapping of locale folder names to human-readable names
LANG_NAMES: Dict[str, str] = {
    "af": "Afrikaans",
    "am": "Amharic",
    "ar": "Arabic",
    "as": "Assamese",
    "ast-rES": "Asturian",
    "az": "Azerbaijani",
    "bal": "Balochi",
    "be": "Belarusian",
    "bg": "Bulgarian",
    "bn": "Bengali",
    "b+sr+Latn": "Serbian (Latin)",
    "bs": "Bosnian",
    "ca": "Catalan",
    "cs": "Czech",
    "cy": "Welsh",
    "da": "Danish",
    "de": "German",
    "dv": "Dhivehi",
    "el": "Greek",
    "en-rAU": "English (Australia)",
    "en-rCA": "English (Canada)",
    "en-rGB": "English (UK)",
    "en-rIN": "English (India)",
    "es": "Spanish (Spain)",
    "es-rUS": "Spanish (US / Latin America)",
    "et": "Estonian",
    "eu": "Basque",
    "fa": "Persian",
    "fi": "Finnish",
    "fil": "Filipino",
    "fr": "French",
    "fr-rCA": "French (Canada)",
    "gd": "Scottish Gaelic",
    "gl": "Galician",
    "gu": "Gujarati",
    "hi": "Hindi",
    "hr": "Croatian",
    "hu": "Hungarian",
    "hy": "Armenian",
    "in": "Indonesian",
    "is": "Icelandic",
    "it": "Italian",
    "iw": "Hebrew",
    "ja": "Japanese",
    "jpr": "Judeo-Persian",
    "ka": "Georgian",
    "kab": "Kabyle",
    "kk": "Kazakh",
    "km": "Khmer",
    "kn": "Kannada",
    "ko": "Korean",
    "kw": "Cornish",
    "ky": "Kyrgyz",
    "lb": "Luxembourgish",
    "lo": "Lao",
    "lt": "Lithuanian",
    "lv": "Latvian",
    "mk": "Macedonian",
    "ml": "Malayalam",
    "mn": "Mongolian",
    "mr": "Marathi",
    "ms": "Malay",
    "my": "Burmese",
    "nb": "Norwegian Bokmål",
    "ne": "Nepali",
    "nl": "Dutch",
    "or": "Odia",
    "ota": "Ottoman Turkish",
    "pa": "Punjabi",
    "pa-rPK": "Punjabi (Pakistan)",
    "pl": "Polish",
    "pt": "Portuguese (Standard)",
    "pt-rBR": "Portuguese (Brazil)",
    "pt-rPT": "Portuguese (Portugal)",
    "ro": "Romanian",
    "ru": "Russian",
    "sc-rIT": "Sardinian",
    "si": "Sinhala",
    "sk": "Slovak",
    "sl": "Slovenian",
    "sq": "Albanian",
    "sr": "Serbian (Cyrillic)",
    "sv": "Swedish",
    "sw": "Swahili",
    "ta": "Tamil",
    "te": "Telugu",
    "tg": "Tajik",
    "th": "Thai",
    "tl": "Tagalog",
    "tr": "Turkish",
    "uk": "Ukrainian",
    "ur": "Urdu",
    "uz": "Uzbek",
    "vi": "Vietnamese",
    "zh-rCN": "Simplified Chinese",
    "zh-rHK": "Traditional Chinese (Hong Kong)",
    "zh-rTW": "Traditional Chinese (Taiwan)",
    "zu": "Zulu",
}

# The target languages recently mass translated in v4.2.7
RECENT_MASS_TRANSLATED = [
    "values-ar",
    "values-lt",
    "values-ro",
    "values-in",
    "values-uk",
    "values-es",
    "values-es-rUS",
    "values-pt-rPT",
    "values-pt-rBR",
    "values-pt",
    "values-pl",
    "values-nl",
    "values-cs",
    "values-et",
    "values-hr",
    "values-hu",
    "values-iw",
    "values-zh-rCN",
    "values-ru",
    "values-hi",
    "values-tr",
    "values-de",
    "values-fr",
    "values-it",
    "values-el",
    "values-ur",
    "values-be",
    "values-bg",
]


def load_base_strings() -> Dict[str, str]:
    """Parse translatable strings from the base values/strings.xml."""
    if not os.path.exists(BASE_STRINGS):
        sys.exit(f"Error: Base strings file not found at {BASE_STRINGS}")

    tree = ET.parse(BASE_STRINGS)
    root = tree.getroot()

    base_strings: Dict[str, str] = {}
    for elem in root.findall("string"):
        name = elem.attrib.get("name")
        translatable = elem.attrib.get("translatable", "true").lower()
        if name and translatable != "false":
            # Extract plain text content or empty string
            base_strings[name] = elem.text or ""

    return base_strings


def get_target_strings(folder_name: str) -> Optional[Set[str]]:
    """Parse string key names from a specific values folder."""
    target_path = os.path.join(RES_DIR, folder_name, "strings.xml")
    if not os.path.exists(target_path):
        return None

    try:
        tree = ET.parse(target_path)
        root = tree.getroot()
        keys = set()
        for elem in root.findall("string"):
            name = elem.attrib.get("name")
            if name:
                keys.add(name)
        return keys
    except Exception as e:
        print(f"Warning: Failed to parse XML for {folder_name}: {e}", file=sys.stderr)
        return None


def render_progress_bar(percentage: float, width: int = 14) -> str:
    """Render a text progress bar."""
    filled = int(round(width * percentage / 100.0))
    filled = max(0, min(width, filled))
    bar = "█" * filled + "░" * (width - filled)
    return f"[{bar}]"


def normalize_locale_name(name: str) -> str:
    """Normalize input (e.g. 'lt', 'values-lt') into a values folder name."""
    clean = name.strip()
    if clean.startswith("values-"):
        return clean
    if clean == "values":
        return clean
    return f"values-{clean}"


def analyze_languages(
    locales: List[str],
    base_strings: Dict[str, str]
) -> List[Dict]:
    """Perform translation analysis for the provided list of locale folder names."""
    results = []
    total_base = len(base_strings)

    for folder in locales:
        lang_code = folder.replace("values-", "")
        lang_name = LANG_NAMES.get(lang_code, lang_code)
        target_keys = get_target_strings(folder)

        if target_keys is None:
            results.append({
                "folder": folder,
                "code": lang_code,
                "name": lang_name,
                "exists": False,
                "total_base": total_base,
                "translated_count": 0,
                "missing_count": total_base,
                "percentage": 0.0,
                "missing_keys": list(base_strings.keys()),
            })
            continue

        missing_keys = [k for k in base_strings if k not in target_keys]
        translated_count = total_base - len(missing_keys)
        pct = (translated_count / total_base * 100.0) if total_base > 0 else 100.0

        results.append({
            "folder": folder,
            "code": lang_code,
            "name": lang_name,
            "exists": True,
            "total_base": total_base,
            "translated_count": translated_count,
            "missing_count": len(missing_keys),
            "percentage": pct,
            "missing_keys": missing_keys,
        })

    return results


def print_table(results: List[Dict], details: bool = False, base_strings: Optional[Dict[str, str]] = None):
    """Print results formatted as an aligned terminal table."""
    print("=" * 86)
    print(f"{'Locale Folder':<16} {'Language Name':<28} {'Progress':<18} {'Missing':<9} {'Coverage'}")
    print("-" * 86)

    for r in results:
        if not r["exists"]:
            status = f"{'FILE MISSING':<18} {r['missing_count']:<9} (0.0%)"
            print(f"{r['folder']:<16} {r['name']:<28} {status}")
            continue

        bar = render_progress_bar(r["percentage"], width=12)
        pct_str = f"{r['percentage']:5.1f}%"
        prog_str = f"{bar} {pct_str}"
        missing_str = f"{r['missing_count']} left" if r['missing_count'] > 0 else "0 (done)"

        print(f"{r['folder']:<16} {r['name']:<28} {prog_str:<20} {missing_str:<11} {r['translated_count']}/{r['total_base']}")

    print("=" * 86)

    if details and base_strings:
        print("\n### Detailed Missing Strings Breakdown:")
        for r in results:
            if r["missing_count"] == 0:
                continue
            print(f"\n[{r['folder']}] {r['name']} - {r['missing_count']} missing string(s):")
            for k in r["missing_keys"]:
                en_val = base_strings.get(k, "").replace("\n", " ").strip()
                if len(en_val) > 75:
                    en_val = en_val[:72] + "..."
                print(f"  * {k:<38} -> \"{en_val}\"")


def print_markdown(results: List[Dict], details: bool = False, base_strings: Optional[Dict[str, str]] = None):
    """Print results in Markdown format."""
    print("| Locale Folder | Language | Progress | Missing Strings | Translated / Total |")
    print("| :--- | :--- | :---: | :---: | :---: |")

    for r in results:
        if not r["exists"]:
            print(f"| `{r['folder']}` | {r['name']} | 0.0% | **{r['missing_count']} (File Missing)** | 0 / {r['total_base']} |")
            continue

        pct = f"{r['percentage']:.1f}%"
        missing_fmt = f"**{r['missing_count']}**" if r['missing_count'] > 0 else "0 (Complete)"
        print(f"| `{r['folder']}` | {r['name']} | {pct} | {missing_fmt} | {r['translated_count']} / {r['total_base']} |")

    if details and base_strings:
        print("\n### Missing Strings Details\n")
        for r in results:
            if r["missing_count"] == 0:
                continue
            print(f"<details><summary><b>{r['name']} ({r['folder']}) - {r['missing_count']} missing</b></summary>\n")
            print("| Key | English Reference |")
            print("| :--- | :--- |")
            for k in r["missing_keys"]:
                en_val = base_strings.get(k, "").replace("\n", " ").replace("|", "\\|").strip()
                print(f"| `{k}` | {en_val} |")
            print("\n</details>\n")


def main():
    parser = argparse.ArgumentParser(
        description="Check missing translations across locale folders in LeanType."
    )
    parser.add_argument(
        "locales",
        nargs="*",
        help="Optional specific language codes or folder names to check (e.g. lt, values-ro, uk)."
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="Scan all values-* locale folders present in app/src/main/res."
    )
    parser.add_argument(
        "--recent",
        action="store_true",
        help="Check the recent mass-translated languages from v4.2.7 (default behavior)."
    )
    parser.add_argument(
        "-d", "--details",
        action="store_true",
        help="Display the exact keys and English text for each missing string."
    )
    parser.add_argument(
        "--missing-only",
        action="store_true",
        help="Only display languages that have missing strings (hides 100% complete ones)."
    )
    parser.add_argument(
        "--format",
        choices=["table", "markdown", "json"],
        default="table",
        help="Output format (default: table)."
    )
    parser.add_argument(
        "--export-keys",
        metavar="FILE",
        help="Export all unique missing string keys across the scanned languages to a text file."
    )

    args = parser.parse_args()

    base_strings = load_base_strings()

    # Determine target folders to inspect
    if args.locales:
        targets = [normalize_locale_name(l) for l in args.locales]
    elif args.all:
        all_dirs = sorted([d for d in os.listdir(RES_DIR) if d.startswith("values-") and d != "values-night"])
        targets = all_dirs
    else:
        # Default to the recent mass-translated set
        targets = RECENT_MASS_TRANSLATED

    results = analyze_languages(targets, base_strings)

    if args.missing_only:
        results = [r for r in results if r["missing_count"] > 0]

    # Handle output formatting
    if args.format == "json":
        output_data = {
            "total_base_strings": len(base_strings),
            "locales": results
        }
        print(json.dumps(output_data, indent=2, ensure_ascii=False))
    elif args.format == "markdown":
        print_markdown(results, details=args.details, base_strings=base_strings)
    else:
        print(f"\nLeanType Translation Coverage Analysis (Base: {len(base_strings)} translatable strings)")
        print_table(results, details=args.details, base_strings=base_strings)

    if args.export_keys:
        unique_keys = sorted(set(k for r in results for k in r["missing_keys"]))
        with open(args.export_keys, "w", encoding="utf-8") as f:
            for k in unique_keys:
                f.write(f"{k}\n")
        print(f"\nExported {len(unique_keys)} unique missing keys to {args.export_keys}")


if __name__ == "__main__":
    main()
