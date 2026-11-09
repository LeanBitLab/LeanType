#!/bin/python

import os
import subprocess
import sys
import zipfile
from urllib.request import urlretrieve


# git diff should be empty, and there should be no errors
def check_git():
    result = subprocess.run(["git", "diff", "--name-only"], capture_output=True)
    if result.returncode != 0 or len(result.stdout) != 0:
        cont = input("uncommitted changes found, continue? [y/N] ")
        if cont != "y":
            sys.exit()


# verify that all required translation strings are populated
def check_translations():
    result = subprocess.run([sys.executable, "tools/check_missing_translations.py", "--summary"], capture_output=True, text=True)
    if result.returncode != 0:
        print("Warning: Missing translation strings found:")
        print(result.stdout)
    else:
        print("Translations check passed.")


# git diff to make sure default strings are the same
def check_default_values_diff():
    result = subprocess.run(["git", "diff", "--name-only", "app/src/main/res/values"], capture_output=True)
    if result.returncode != 0 or len(result.stdout) != 0:
        raise ValueError("default strings changed after translation import, something is wrong")


def read_dicts_readme() -> list[str]:
    dicts_readme_file = "../dictionaries/README.md"
    if os.path.isfile(dicts_readme_file):
        f = open(dicts_readme_file)
        lines = f.readlines()
        f.close()
        return lines
    readme_url = "https://codeberg.org/Helium314/aosp-dictionaries/raw/branch/main/README.md"
    tmp_readme = "dicts_readme_tmp.md"
    urlretrieve(readme_url, tmp_readme)
    f = open(tmp_readme)
    lines = f.readlines()
    f.close()
    os.remove(tmp_readme)
    return lines


# generate a list of dictionaries available in the dictionaries repository at (https://codeberg.org/Helium314/aosp-dictionaries
# for convenient linking when adding dictionaries in HeliBoard.
def update_dict_list():
    lines = read_dicts_readme()
    mode = 0
    dicts = []
    for line in lines:
        line = line.strip()
        if line.startswith("#"):
            mode = 0
        if line.startswith("# Dictionaries"):
            mode = 1
        if mode == 0 or not line.startswith("|") or line.startswith("| Language |") or line.startswith("| --- |"):
            continue
        split = [s.strip() for s in line.split("|")]
        if len(split) < 8:
            continue
        type_col = split[2]
        if "(" not in type_col or ")" not in type_col:
            continue
        url = type_col.split("(")[-1].split(")")[0]
        if "/raw/branch/main/" in url:
            rel_path = url.split("/raw/branch/main/")[-1]
        elif "/src/branch/main/" in url:
            rel_path = url.split("/src/branch/main/")[-1]
        else:
            rel_path = url.split("codeberg.org/Helium314/aosp-dictionaries/")[-1]

        filename = rel_path.split("/")[-1]
        dict_name = filename.split(".dict")[0]
        dict_type = dict_name.split("_")[0]
        rest = dict_name[len(dict_type) + 1:]

        kind_col = split[3].lower()
        source_col = split[7]

        if "__" in rest:
            loc_str, suffix = rest.split("__", 1)
            kind = "exp" if "experimental" in suffix else ("legacy" if "legacy" in suffix else suffix)
        else:
            loc_str = rest
            if "UNICODE LICENSE V3 (CLDR)" in source_col or "cldr" in rel_path:
                kind = "cldr"
            elif kind_col == "experimental" or kind_col == "yes":
                kind = "exp"
            elif kind_col == "legacy":
                kind = "legacy"
            else:
                kind = ""

        if "_" in loc_str:
            sp = loc_str.split("_")
            locale = sp[0] + "_" + "_".join(s.upper() for s in sp[1:])
        else:
            locale = loc_str

        dicts.append(f"{dict_type},{locale},{kind},{rel_path}\n")
    target_file = "app/src/main/assets/dictionaries_in_dict_repo.csv"
    with open(target_file, 'w') as f:
        f.writelines(dicts)


# check whether there is a changelog file for current version and print result and version code
def check_changelog():
    changelog_dir = "fastlane/metadata/android/en-US/changelogs"
    assert os.path.isdir(changelog_dir)
    filenames = []
    for file in os.scandir(changelog_dir):
        filenames.append(file.name)
    filenames.sort()
    changelog_version = filenames[-1].replace(".txt", "")
    version = ""
    with open("app/build.gradle.kts") as f:
        for line in f:
            line = line.lstrip()
            if line.startswith("versionCode"):
                version = line.split(" ")[2].rstrip()
                break
    if changelog_version == version:
        print("changelog for", version, "exists")
    else:
        print("changelog for", version, "does not exist")


# update khipro mapping json, see discussion at the bottom of https://github.com/Helium314/HeliBoard/pull/2134
def update_khipro_mappings():
    source = "https://raw.githubusercontent.com/KhiproTeam/Khipro-Mappings/refs/heads/main/output/touchscreen.json"
    target = "app/src/main/assets/khipro-mappings.json"
    urlretrieve(source, target)


def main():
    if os.getcwd().endswith("tools"):
        os.chdir("../")
    check_git()
    check_translations()
    check_default_values_diff()
    update_dict_list()
    update_khipro_mappings()
    check_changelog()


if __name__ == "__main__":
    main()
