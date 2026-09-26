#!/usr/bin/env python3
import os
import sys
import re
import argparse
import xml.etree.ElementTree as ET

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "res")

def import_translation(lang_code, file_paths):
    folder = lang_code if lang_code.startswith("values-") else f"values-{lang_code}"
    target_path = os.path.join(RES_DIR, folder, "strings.xml")

    if not os.path.exists(target_path):
        print(f"Error: Target {target_path} does not exist.")
        sys.exit(1)

    target_tree = ET.parse(target_path)
    existing_keys = set(e.attrib["name"] for e in target_tree.getroot() if e.tag == "string")

    all_raw = ""
    for path in file_paths:
        if not os.path.exists(path):
            print(f"Warning: File {path} not found, skipping.")
            continue
        with open(path, "r", encoding="utf-8") as f:
            all_raw += f.read() + "\n"

    pattern = re.compile(r'<string\s+name="([^"]+)">([\s\S]*?)</string>')
    matches = pattern.findall(all_raw)

    print(f"Found {len(matches)} string entries in provided translation input(s)")

    to_inject = []
    seen = set()

    for key, val in matches:
        if key in existing_keys or key in seen:
            continue
        seen.add(key)

        v = val.strip()
        # Ensure ampersands are encoded as &amp;
        v = re.sub(r'&(?!(?:amp|lt|gt|quot|apos|#\d+|#x[0-9a-fA-F]+);)', '&amp;', v)
        # Normalize escaped single quotes: \'
        v = v.replace("\\'", "'").replace("'", "\\'")
        # Normalize escaped double quotes: \"
        v = v.replace('\\"', '"').replace('"', '\\"')

        to_inject.append(f'    <string name="{key}">{v}</string>')

    print(f"Injecting {len(to_inject)} new unique strings into {folder}/strings.xml")

    if not to_inject:
        print("Nothing new to inject.")
        return

    with open(target_path, "r", encoding="utf-8") as f:
        current_xml = f.read()

    injection = "\n" + "\n".join(to_inject) + "\n</resources>\n"
    updated_xml = current_xml.replace("</resources>", injection.strip() + "\n")

    with open(target_path, "w", encoding="utf-8") as f:
        f.write(updated_xml)

    # Validate XML
    ET.parse(target_path)
    print(f"Successfully updated and validated {folder}/strings.xml!")

def main():
    parser = argparse.ArgumentParser(description="Import translated XML strings into a target language strings.xml.")
    parser.add_argument("language", help="Language code or folder name (e.g. uk, values-uk)")
    parser.add_argument("files", nargs="+", help="One or more text/XML/MD files containing translated <string> elements")

    args = parser.parse_args()
    import_translation(args.language, args.files)

if __name__ == "__main__":
    main()
