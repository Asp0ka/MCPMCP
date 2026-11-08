"""Provision pinned Gradle from the official GitHub asset, with SHA-256."""
import hashlib
import os
from pathlib import Path
import subprocess
import zipfile

VERSION = "8.8"
SHA256 = "a4b4158601f8636cdeeab09bd76afb640030bb5b144aafe261a5e8af027dc612"
ASSET = "https://api.github.com/repos/gradle/gradle-distributions/releases/assets/171230046?download=1"

def main():
    root = Path(os.environ["RUNNER_TEMP"]) / "mcpmcp-gradle"
    root.mkdir(parents=True, exist_ok=True)
    archive = root / "gradle-8.8-bin.zip"
    for attempt in range(4):
        result = subprocess.run(["curl", "--fail", "--location", "--silent", "--show-error",
            "--connect-timeout", "15", "--max-time", "240", "--retry", "2",
            "--continue-at", "-", "--header", "Accept: application/octet-stream",
            ASSET, "--output", str(archive)], check=False)
        if result.returncode == 0:
            break
        print("Gradle asset download interrupted; resuming verified archive", flush=True)
    else:
        raise SystemExit("Official Gradle asset download failed")
    actual = hashlib.sha256(archive.read_bytes()).hexdigest()
    if actual != SHA256:
        archive.unlink()
        raise SystemExit("Gradle archive SHA-256 mismatch")
    with zipfile.ZipFile(archive) as bundle:
        if bundle.testzip() is not None:
            raise SystemExit("Corrupt Gradle ZIP")
        for name in bundle.namelist():
            target = (root / name).resolve()
            if not target.is_relative_to(root.resolve()):
                raise SystemExit("Unsafe Gradle ZIP path")
        bundle.extractall(root)
    executable = root / ("gradle-" + VERSION) / "bin" / "gradle.bat"
    if not executable.is_file():
        raise SystemExit("Missing Gradle executable")
    with open(os.environ["GITHUB_PATH"], "a", encoding="utf-8") as path_file:
        print(executable.parent, file=path_file)
    print("Gradle 8.8 official distribution verified: " + actual, flush=True)

if __name__ == "__main__":
    main()
