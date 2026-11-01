"""Package the release APK and reject tags that disagree with Android's versionName."""

import json
import os
from pathlib import Path
import re
import shutil


def prepare(root: Path, ref: str) -> Path:
    output = root / "app/build/outputs/apk/release"
    metadata = json.loads((output / "output-metadata.json").read_text(encoding="utf-8"))
    elements = metadata["elements"]
    if len(elements) != 1:
        raise ValueError("Expected exactly one release APK")
    apk = elements[0]
    version = apk["versionName"]
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", version):
        raise ValueError("versionName must use letters, numbers, dots, underscores or hyphens")
    if ref.startswith("refs/tags/") and ref != f"refs/tags/v{version}":
        raise ValueError(f"Tag must be v{version} to match app/build.gradle.kts versionName")
    if metadata["applicationId"] != "com.justinlin099.xperiarecentsrootless":
        raise ValueError("Unexpected applicationId")
    source = (output / apk["outputFile"]).resolve()
    if source.parent != output.resolve() or source.suffix != ".apk":
        raise ValueError("Unexpected APK output path")
    dist = root / "dist"
    dist.mkdir(exist_ok=True)
    destination = dist / f"Xperia-Clear-All-{version}.apk"
    shutil.copyfile(source, destination)
    print(f"Prepared {destination.name} (versionCode {apk['versionCode']})")
    return destination


if __name__ == "__main__":
    prepare(Path.cwd(), os.environ.get("GITHUB_REF", ""))
