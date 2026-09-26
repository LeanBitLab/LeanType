#!/usr/bin/env python3
import os
import re
import sys

def extract_flavor(text):
    """Extracts the specific flavor name from an APK filename or markdown row."""
    lower = text.lower()
    if 'offlinelite' in lower:
        return 'offlinelite'
    if 'standard' in lower:
        return 'standard'
    if 'offline' in lower:
        return 'offline'
    return None

def get_apk_sizes(project_root):
    """Scans app/build/outputs/apk for built APK files and returns a map of filename -> formatted size."""
    apk_dir = os.path.join(project_root, 'app', 'build', 'outputs', 'apk')
    apk_sizes = {}
    if not os.path.exists(apk_dir):
        return apk_sizes
        
    for root, _, files in os.walk(apk_dir):
        for file in files:
            if file.endswith('.apk'):
                full_path = os.path.join(root, file)
                size_bytes = os.path.getsize(full_path)
                if size_bytes >= 1024 * 1024:
                    size_str = f"{size_bytes / (1024 * 1024):.1f} MB"
                else:
                    size_str = f"{size_bytes / 1024:.1f} KB"
                apk_sizes[file] = size_str
                print(f"Found APK: {file} ({size_str})")
    return apk_sizes

def inject_apk_sizes(content, apk_sizes):
    """Dynamically adds or populates a Size column in Markdown flavor/download tables."""
    if not apk_sizes:
        return content

    lines = content.splitlines()
    in_table = False
    size_col_idx = -1
    inserted_size_col = False
    table_modified = False
    new_lines = []
    
    for line in lines:
        stripped = line.strip()
        if stripped.startswith('|') and stripped.endswith('|'):
            cells = [c.strip() for c in stripped.split('|')[1:-1]]
            
            # Check if this is the header row
            if not in_table and any(c.lower() in ('flavor', 'file', 'apk', 'flavor / file') for c in cells):
                in_table = True
                size_col_idx = -1
                for idx, c in enumerate(cells):
                    if c.lower() == 'size':
                        size_col_idx = idx
                        break
                
                if size_col_idx >= 0:
                    inserted_size_col = False
                    new_lines.append(line)
                else:
                    inserted_size_col = True
                    size_col_idx = 1
                    new_cells = [cells[0], "Size"] + cells[1:]
                    new_lines.append("| " + " | ".join(new_cells) + " |")
                table_modified = True
                continue
                
            # Check if this is the delimiter row
            if in_table and all(set(c).issubset(set('-: ')) and len(c) > 0 for c in cells):
                if inserted_size_col:
                    new_cells = [cells[0], ":---:"] + cells[1:]
                    new_lines.append("|" + "|".join(new_cells) + "|")
                else:
                    new_lines.append(line)
                continue
                
            # Body rows
            if in_table:
                matched_size = "-"
                # 1. Exact filename match
                for apk_name, size_str in apk_sizes.items():
                    if apk_name in stripped:
                        matched_size = size_str
                        break
                # 2. Flavor-specific fallback match (preferring release builds)
                if matched_size == "-":
                    row_flavor = extract_flavor(stripped)
                    if row_flavor:
                        candidates = [apk for apk in apk_sizes if extract_flavor(apk) == row_flavor]
                        if candidates:
                            candidates.sort(key=lambda x: ('release' in x.lower(), x), reverse=True)
                            matched_size = apk_sizes[candidates[0]]
                
                if inserted_size_col:
                    new_cells = [cells[0], matched_size] + cells[1:]
                else:
                    new_cells = list(cells)
                    if 0 <= size_col_idx < len(new_cells):
                        if not new_cells[size_col_idx] or new_cells[size_col_idx] == "-":
                            new_cells[size_col_idx] = matched_size
                new_lines.append("| " + " | ".join(new_cells) + " |")
                continue
        else:
            in_table = False
            size_col_idx = -1
            inserted_size_col = False
            new_lines.append(line)
            
    result = "\n".join(new_lines)
    
    # Fallback: If no table was present, append an artifact summary section
    if not table_modified and apk_sizes:
        artifacts_section = "\n\n### 📦 Build Artifact Sizes\n\n| File | Size |\n|:---|:---:|\n"
        for name in sorted(apk_sizes.keys()):
            artifacts_section += f"| `{name}` | {apk_sizes[name]} |\n"
        result += artifacts_section
        
    return result

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(os.path.dirname(script_dir))
    
    # 1. Try to get version from tag name (via CLI arg or GitHub Actions env)
    ref_name = sys.argv[1] if len(sys.argv) > 1 else os.environ.get('GITHUB_REF_NAME')
    version_name = None
    is_beta = False

    if ref_name:
        if ref_name.startswith('beta-'):
            is_beta = True
            print(f"Detected beta build tag: {ref_name}")
        elif ref_name.startswith('v'):
            version_name = ref_name[1:]
            print(f"Detected version name from GITHUB_REF_NAME: {version_name}")

    # 2. Fall back to build.gradle.kts if not determined from tag
    if not version_name:
        gradle_path = os.path.join(project_root, 'app', 'build.gradle.kts')
        if os.path.exists(gradle_path):
            with open(gradle_path, 'r', encoding='utf-8') as f:
                gradle_content = f.read()
            version_name_match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle_content)
            if version_name_match:
                version_name = version_name_match.group(1)
                print(f"Parsed version name from build.gradle.kts: {version_name}")

    if not version_name:
        print("Error: Could not determine version name")
        return

    # 3. Locate the existing release notes file
    releasenote_dir = os.path.join(project_root, 'docs', 'releasenote')
    source_path = None
    clean_version = re.sub(r'[-_]?beta.*$', '', version_name, flags=re.IGNORECASE)

    if is_beta:
        build_match = re.search(r'-(\d+)$', ref_name or '') or re.search(r'beta[-._]?(\d+)', ref_name or '', re.IGNORECASE)
        build_num = build_match.group(1) if build_match else "2"
        build_suffix = f"beta{build_num}"
        candidates = [
            os.path.join(releasenote_dir, f'release_notes_v{clean_version}-{build_suffix}.md'),
            os.path.join(releasenote_dir, f'release_notes_v{clean_version}-beta.md'),
            os.path.join(releasenote_dir, f'release_notes_{clean_version}-{build_suffix}.md'),
            os.path.join(releasenote_dir, f'release_notes_v{version_name}.md'),
            os.path.join(releasenote_dir, 'release_notes_beta.md'),
            os.path.join(releasenote_dir, f'release_notes_v{clean_version}.md')
        ]
        for candidate in candidates:
            if os.path.exists(candidate):
                source_path = candidate
                break
    else:
        candidates = [
            os.path.join(releasenote_dir, f'release_notes_v{version_name}.md')
        ]
        if '-' in version_name:
            base_version = version_name.split('-')[0]
            candidates.append(os.path.join(releasenote_dir, f'release_notes_v{base_version}.md'))
        for candidate in candidates:
            if os.path.exists(candidate):
                source_path = candidate
                break

    temp_path = os.path.join(releasenote_dir, 'release_notes_temp.md')

    if not source_path or not os.path.exists(source_path):
        print(f"Error: Release note file not found in candidates")
        # Write a fallback file so the build/release step doesn't fail
        with open(temp_path, 'w', encoding='utf-8') as df:
            df.write(f"Release notes for version {version_name}")
        return

    # 4. Read release note template
    with open(source_path, 'r', encoding='utf-8') as sf:
        content = sf.read()

    # Dynamically inject build number into beta header if applicable
    if is_beta:
        header_build = build_num if 'build_num' in locals() and build_num else "2"
        content = re.sub(
            r'## 🧪 LeanType [^\n]+ Beta.*',
            f'## 🧪 LeanType {clean_version} Beta (Build {header_build})',
            content
        )

    # 5. Scan built APK sizes and dynamically inject into release notes
    apk_sizes = get_apk_sizes(project_root)
    updated_content = inject_apk_sizes(content, apk_sizes)

    # 6. Write to release_notes_temp.md for GitHub Release publication
    with open(temp_path, 'w', encoding='utf-8') as df:
        df.write(updated_content)

    print(f"Successfully generated {temp_path} with dynamic APK sizes from {source_path}")

if __name__ == '__main__':
    # ponytail: copy pre-generated release notes file to temp.md with dynamic APK sizes
    main()
