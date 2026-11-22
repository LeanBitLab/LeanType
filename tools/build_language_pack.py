#!/usr/bin/env python3
"""Build a language pack (resource-only APK with translated UI strings) for one app APK.

The pack reuses the resource IDs of the given app APK (aapt2 --stable-ids), so it only works
with that exact build. Its versionName holds the CRC32 of the app's resources.arsc and the locale;
the app checks both before loading a pack (LanguagePacks.kt). Packs are published in a release
tagged <versionName>-<crc>, which the script prints.

Usage:
  tools/build_language_pack.py --apk app.apk --locale de [--locale pt-rBR ...] [--out-dir DIR]
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STRING_FILES = re.compile(r"^strings.*\.xml$")


def sdk_dir() -> Path:
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(var):
            return Path(os.environ[var])
    return Path.home() / "Android" / "Sdk"


def latest(path: Path) -> Path:
    versions = sorted((p for p in path.iterdir() if p.is_dir()), key=lambda p: [int(x) if x.isdigit() else 0 for x in re.split(r"[.-]", p.name.removeprefix("android-"))])
    if not versions:
        sys.exit(f"nothing found in {path}")
    return versions[-1]


def run(*args: str) -> str:
    result = subprocess.run(args, capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"{' '.join(args[:2])} failed:\n{result.stderr.strip()}")
    return result.stdout


def read_apk(aapt2: str, apk: str) -> tuple[str, str, str, str, list[str]]:
    """Returns package name, versionCode, versionName, resources.arsc CRC and stable-id lines of the app APK."""
    badging = run(aapt2, "dump", "badging", apk)
    m = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", badging)
    if not m:
        sys.exit("could not read package info from the APK")
    package, version_code, version_name = m.groups()
    ids = [f"{package}:{rtype}/{name} = {rid}"
           for rid, rtype, name in re.findall(r"^\s+resource (0x[0-9a-f]{8}) ([\w-]+)/(\S+)", run(aapt2, "dump", "resources", apk), re.M)]
    with zipfile.ZipFile(apk) as z:
        crc = f"{z.getinfo('resources.arsc').CRC:08x}"
    return package, version_code, version_name, crc, ids


def build_pack(aapt2: str, android_jar: Path, res: Path, locale: str, package: str, version_code: str,
               version_name: str, crc: str, ids_file: Path, out: Path, work: Path) -> None:
    locale_dir = res / f"values-{locale}"
    files = sorted(p for p in locale_dir.glob("*.xml") if STRING_FILES.match(p.name)) if locale_dir.is_dir() else []
    if not files:
        sys.exit(f"no strings*.xml in {locale_dir}")
    flat_dir = work / f"flat-{locale}"
    flat_dir.mkdir()
    for f in files:
        run(aapt2, "compile", "--legacy", "-o", str(flat_dir), str(f))  # --legacy like AGP
    flats = sorted(str(p) for p in flat_dir.iterdir())
    manifest = work / f"AndroidManifest-{locale}.xml"
    manifest.write_text(
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    package="{package}" android:versionCode="{version_code}" android:versionName="{version_name};res={crc};locale={locale}">\n'
        '    <uses-sdk android:minSdkVersion="30" />\n'
        '    <application android:hasCode="false" />\n'
        '</manifest>\n')
    # an overlay: its strings have no default value, which aapt2 would otherwise remove
    run(aapt2, "link", "--no-resource-removal", "-o", str(out), "-I", str(android_jar), "--manifest", str(manifest),
        "--stable-ids", str(ids_file), *flats)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--apk", required=True, help="app APK the pack is built for")
    parser.add_argument("--locale", action="append", required=True, help="values-<locale> folder suffix, e.g. de or pt-rBR")
    parser.add_argument("--res", default=str(ROOT / "app/src/main/res"), help="res folder with the strings")
    parser.add_argument("--out-dir", default=".", help="where to write <locale>.apk")
    args = parser.parse_args()

    sdk = sdk_dir()
    aapt2 = str(latest(sdk / "build-tools") / "aapt2")
    android_jar = latest(sdk / "platforms") / "android.jar"
    package, version_code, version_name, crc, ids = read_apk(aapt2, args.apk)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix="langpack-"))
    try:
        ids_file = work / "stable-ids.txt"
        ids_file.write_text("\n".join(ids) + "\n")
        for locale in args.locale:
            out = out_dir / f"{locale}.apk"
            build_pack(aapt2, android_jar, Path(args.res), locale, package, version_code, version_name, crc, ids_file, out, work)
            print(f"{out} ({out.stat().st_size // 1024} KB) for {package} {version_name} ({version_code})")
        print(f"release tag: {version_name}-{crc}")
    finally:
        shutil.rmtree(work)


if __name__ == "__main__":
    main()
