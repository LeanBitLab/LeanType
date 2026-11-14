#!/usr/bin/env python3
"""
Release Synchronization & Social Post Verification Tool for LeanType.
Validates all 6 release locations, character limits, em dash invariants, and social post constraints.

Usage:
    python3 tools/verify_release_sync.py [version] [versionCode]
Example:
    python3 tools/verify_release_sync.py 4.3.2 4320
"""

import os
import re
import sys


def twitter_weighted_length(text: str) -> int:
    """Calculates Twitter/X weighted character count."""
    count = 0
    # Standard URL length in Twitter is 23 characters
    url_regex = re.compile(r'https?://\S+')
    urls = url_regex.findall(text)
    clean_text = url_regex.sub('', text)

    for char in clean_text:
        if ord(char) > 0x10FF:
            count += 2
        else:
            count += 1
    count += len(urls) * 23
    return count


def verify_release(project_root: str, version: str, version_code: str) -> bool:
    all_passed = True
    print(f"=== Verifying LeanType Release Synchronization for v{version} (code {version_code}) ===")

    # 1. app/build.gradle.kts
    clean_version = re.sub(r'[-_]?beta.*$', '', version, flags=re.IGNORECASE)
    gradle_path = os.path.join(project_root, "app", "build.gradle.kts")
    if os.path.exists(gradle_path):
        with open(gradle_path, "r", encoding="utf-8") as f:
            gradle_content = f.read()
        if f'versionCode = {version_code}' in gradle_content and (f'versionName = "{version}"' in gradle_content or f'versionName = "{clean_version}"' in gradle_content):
            print("  [OK] app/build.gradle.kts matches version and code")
        else:
            print(f"  [FAIL] app/build.gradle.kts does not match versionCode={version_code} or versionName={version}")
            all_passed = False
    else:
        print("  [FAIL] app/build.gradle.kts not found")
        all_passed = False

    # 2. docs/releasenote/release_notes_v<version>.md
    is_beta = "beta" in version.lower()
    rn_path = os.path.join(project_root, "docs", "releasenote", f"release_notes_v{version}.md")
    if os.path.exists(rn_path):
        with open(rn_path, "r", encoding="utf-8") as f:
            rn_content = f.read()
        print("  [OK] Release notes document exists")

        # Check Support Our Work section
        expected_support = "Keep LeanType fast and independent. As a community-funded, open-source project, we rely on users like you to stay alive."
        if expected_support in rn_content:
            print("  [OK] Release notes contains approved Support Our Work text")
        else:
            print("  [WARN] Release notes may not contain the standard improved Support Our Work section")

        # Check flavor table APK naming
        expected_flavor = "-debug.apk" if is_beta else "-release.apk"
        if expected_flavor in rn_content:
            print(f"  [OK] Release notes flavor table uses expected '{expected_flavor}' filenames")
        else:
            print(f"  [FAIL] Release notes flavor table should use '{expected_flavor}' filenames")
            all_passed = False
    else:
        print(f"  [FAIL] Release notes not found at {rn_path}")
        all_passed = False

    # 3. fastlane/metadata/android/en-US/changelogs/<versionCode>.txt (Strict <= 500 chars for F-Droid)
    cl_path = os.path.join(project_root, "fastlane", "metadata", "android", "en-US", "changelogs", f"{version_code}.txt")
    if os.path.exists(cl_path):
        with open(cl_path, "r", encoding="utf-8") as f:
            cl_content = f.read()
        cl_len = len(cl_content)
        if cl_len <= 500:
            print(f"  [OK] Fastlane changelog is {cl_len} chars (strictly <= 500 chars for F-Droid)")
        else:
            print(f"  [FAIL] Fastlane changelog is {cl_len} chars (EXCEEDS 500 char F-Droid limit, will be truncated!)")
            all_passed = False
    else:
        print(f"  [FAIL] Fastlane changelog not found at {cl_path}")
        all_passed = False

    # 4. app/src/main/java/helium314/keyboard/settings/screens/UpdatesScreen.kt
    updates_path = os.path.join(project_root, "app", "src", "main", "java", "helium314", "keyboard", "settings", "screens", "UpdatesScreen.kt")
    if os.path.exists(updates_path):
        with open(updates_path, "r", encoding="utf-8") as f:
            updates_content = f.read()
        if "currentChangelogItems = listOf(" in updates_content:
            print("  [OK] UpdatesScreen.kt currentChangelogItems is configured")
        else:
            print("  [FAIL] UpdatesScreen.kt missing currentChangelogItems")
            all_passed = False
    else:
        print("  [FAIL] UpdatesScreen.kt not found")
        all_passed = False

    # 5. docs/badges/download.svg
    badge_path = os.path.join(project_root, "docs", "badges", "download.svg")
    if os.path.exists(badge_path):
        with open(badge_path, "r", encoding="utf-8") as f:
            badge_content = f.read()
        clean_version = re.sub(r'[-_]?beta.*$', '', version, flags=re.IGNORECASE)
        expected_badge_text = f"v{clean_version}"
        if expected_badge_text in badge_content:
            print(f"  [OK] docs/badges/download.svg matches {expected_badge_text}")
        else:
            print(f"  [FAIL] docs/badges/download.svg does not contain '{expected_badge_text}'")
            all_passed = False
    else:
        print("  [FAIL] docs/badges/download.svg not found")
        all_passed = False

    # 6. Pdoc/release_posts_v<version>.md
    post_path = os.path.join(project_root, "Pdoc", f"release_posts_v{version}.md")
    if os.path.exists(post_path):
        with open(post_path, "r", encoding="utf-8") as f:
            post_content = f.read()
        print("  [OK] Social release posts document exists")

        # Validate Reddit section
        reddit_match = re.search(r"## 1\. Reddit Post.*?```markdown(.*?)```", post_content, re.DOTALL)
        if reddit_match:
            reddit_text = reddit_match.group(1)
            urls = re.findall(r"https?://\S+|www\.\S+", reddit_text)
            if urls:
                print(f"  [FAIL] Reddit post contains URLs: {urls} (Reddit post MUST NOT have links)")
                all_passed = False
            else:
                print("  [OK] Reddit post has zero links")

            if "Downloads: You can find all the links in the sidebar." in reddit_text:
                print("  [OK] Reddit post contains sidebar downloads sentence")
            else:
                print("  [WARN] Reddit post may be missing standard sidebar downloads notice")
        else:
            print("  [FAIL] Reddit post block not found in social posts")
            all_passed = False

        # Validate Telegram section
        if "## 2. Telegram Post" in post_content and "https://github.com/LeanBitLab/LeanType/releases/tag/" in post_content:
            print("  [OK] Telegram post is configured with release links")
        else:
            print("  [FAIL] Telegram post missing or lacking release links")
            all_passed = False

        # Validate X / Twitter section
        x_match = re.search(r"### Option A: Single Tweet.*?```text(.*?)```", post_content, re.DOTALL)
        if x_match:
            x_text = x_match.group(1).strip()
            x_len = twitter_weighted_length(x_text)
            if x_len <= 280:
                print(f"  [OK] X (Twitter) single tweet is {x_len} weighted characters (<= 280)")
            else:
                print(f"  [FAIL] X (Twitter) single tweet exceeds 280 chars: {x_len}")
                all_passed = False
        else:
            print("  [WARN] X (Twitter) single tweet block not found")

        # Validate Open Collective section
        if "## 4. Open Collective Updates Page" in post_content:
            print("  [OK] Open Collective update post is configured")
        else:
            print("  [FAIL] Open Collective update post missing")
            all_passed = False
    else:
        print(f"  [FAIL] Social release posts not found at {post_path}")
        all_passed = False

    # Check for em dashes across all release files
    files_to_check = [rn_path, cl_path, updates_path, badge_path, post_path]
    em_dash_found = False
    for p in files_to_check:
        if os.path.exists(p):
            with open(p, "r", encoding="utf-8") as f:
                content = f.read()
            if "\u2014" in content:
                print(f"  [FAIL] Em dash (Unicode U+2014) found in {os.path.relpath(p, project_root)}")
                em_dash_found = True
                all_passed = False

    if not em_dash_found:
        print("  [OK] Zero em dashes found across all release files")

    print("\n" + ("=== ALL RELEASE CHECKS PASSED ===" if all_passed else "=== RELEASE VERIFICATION FAILED ==="))
    return all_passed


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)

    version = sys.argv[1] if len(sys.argv) > 1 else "4.3.2"
    version_code = sys.argv[2] if len(sys.argv) > 2 else "4320"

    success = verify_release(project_root, version, version_code)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
