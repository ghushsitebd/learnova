#!/usr/bin/env python3
"""Fetch the verified animated creature GLBs from immutable GitHub-hosted sources.

Google Drive is intentionally not used here: its public-download quota can fail a
CI run even when the requested asset is valid. The source repositories below pin
the exact commit and the assets are the Quaternius CC0 variants already identified
for the near-field runtime.
"""
from pathlib import Path
import subprocess
import tempfile
import urllib.request

ROOT = Path("app/src/main/assets/creatures")

SOURCES = {
    "deer": (
        "Deer",
        "https://media.githubusercontent.com/media/StateDev08/War-of-the-Kindom-Mobile/"
        "9b5a2827ed8f2b7adf657ddf7e47cb026bab0b39/client/assets/models/quaternius/animals/deer.glb",
    ),
    "fox": (
        "Fox",
        "https://media.githubusercontent.com/media/StateDev08/War-of-the-Kindom-Mobile/"
        "9b5a2827ed8f2b7adf657ddf7e47cb026bab0b39/client/assets/models/quaternius/animals/fox.glb",
    ),
    "horse": (
        "Horse",
        "https://media.githubusercontent.com/media/fayipon/racehorse/"
        "4a741208154f49b252a5f6d9be6d4ea5dc322f43/godot/assets/quaternius/horse.glb",
    ),
    "wolf": (
        "Wolf",
        "https://media.githubusercontent.com/media/StateDev08/War-of-the-Kindom-Mobile/"
        "9b5a2827ed8f2b7adf657ddf7e47cb026bab0b39/client/assets/models/quaternius/animals/wolf.glb",
    ),
}


def download_source(url: str, target: Path) -> None:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "Learnova-CI/1.0"},
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        with target.open("wb") as output:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                output.write(chunk)

    if not target.is_file() or target.stat().st_size < 32:
        raise RuntimeError(f"Downloaded asset is missing or too small: {url}")


def validate_glb(path: Path, display_name: str) -> None:
    data = path.read_bytes()
    if data[:4] != b"glTF":
        raise RuntimeError(f"{display_name}: source is not a valid GLB")
    version = int.from_bytes(data[4:8], "little")
    declared_length = int.from_bytes(data[8:12], "little")
    if version != 2:
        raise RuntimeError(f"{display_name}: unsupported GLB version {version}")
    if declared_length != len(data):
        raise RuntimeError(
            f"{display_name}: GLB length header {declared_length} != actual {len(data)}"
        )


def install_tools() -> None:
    subprocess.run(
        ["npx", "--yes", "@gltf-transform/cli@4.2.0", "--version"],
        check=True,
    )


def main() -> None:
    install_tools()
    ROOT.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="learnova-creatures-") as tmp:
        tmp_root = Path(tmp)
        for key, (display_name, url) in SOURCES.items():
            source = tmp_root / f"{display_name}.glb"
            target = ROOT / f"{key}.glb"

            download_source(url, source)
            validate_glb(source, display_name)

            subprocess.run(
                [
                    "npx",
                    "--yes",
                    "@gltf-transform/cli@4.2.0",
                    "copy",
                    str(source),
                    str(target),
                ],
                check=True,
            )
            validate_glb(target, display_name)
            print(f"Normalized animated {display_name}: {target.stat().st_size} bytes")

    print(f"Fetched and normalized {len(SOURCES)} verified animated CC0 creature GLBs.")


if __name__ == "__main__":
    main()
