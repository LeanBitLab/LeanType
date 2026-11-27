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
import shutil
import subprocess
import sys


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
        # CI builds release APKs for both beta and stable tags
        expected_flavor = "-release.apk"
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
        if "beta" in version.lower():
            # the badge shows the latest stable release (set by update-badges.yml), the beta version
            # is shown on the Get Pre-release button instead
            print("  [OK] docs/badges/download.svg follows the latest stable release (not checked for betas)")
        elif expected_badge_text in badge_content:
            print(f"  [OK] docs/badges/download.svg matches {expected_badge_text}")
        else:
            print(f"  [FAIL] docs/badges/download.svg does not contain '{expected_badge_text}'")
            all_passed = False
    else:
        print("  [FAIL] docs/badges/download.svg not found")
        all_passed = False

    # 6. Pdoc/release_posts_v<version>.md (stable releases only, betas get no social posts)
    is_beta = "beta" in version.lower()
    post_path = os.path.join(project_root, "Pdoc", f"release_posts_v{version}.md")
    check_args = ["--notes", rn_path] if os.path.exists(rn_path) else []
    if is_beta:
        print("  [OK] Social release posts not needed for betas")
    elif os.path.exists(post_path):
        with open(post_path, "r", encoding="utf-8") as f:
            post_content = f.read()
        print("  [OK] Social release posts document exists")

        if "Downloads: You can find all the links in the sidebar." in post_content:
            print("  [OK] Reddit post contains sidebar downloads sentence")
        else:
            print("  [WARN] Reddit post may be missing standard sidebar downloads notice")
        check_args += ["--posts", post_path]
    else:
        print(f"  [FAIL] Social release posts not found at {post_path}")
        all_passed = False

    # Notes and posts format (sections, emojis, post lengths): shared local checker,
    # also used by the other LeanBitLab apps
    checker = shutil.which("release-text-check")
    if checker and check_args:
        res = subprocess.run([checker, *check_args], capture_output=True, text=True)
        for line in res.stdout.splitlines():
            if line.strip().startswith("["):
                print("  " + line.strip())
        if res.returncode != 0:
            all_passed = False
    elif not checker:
        print("  [WARN] release-text-check not found, notes and posts format not checked")

    # Check for em dashes across all release files
    files_to_check = [rn_path, cl_path, updates_path, badge_path] + ([] if is_beta else [post_path])
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
