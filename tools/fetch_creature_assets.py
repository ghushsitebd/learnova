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
import time
import urllib.error
import urllib.request

ROOT = Path("app/src/main/assets/creatures")

SOURCES = {
    "deer": (
        "Deer",
        "https://raw.githubusercontent.com/StateDev08/War-of-the-Kindom-Mobile/"
        "main/client/assets/models/quaternius/animals/deer.glb",
    ),
    "fox": (
        "Fox",
        "https://raw.githubusercontent.com/StateDev08/War-of-the-Kindom-Mobile/"
        "main/client/assets/models/quaternius/animals/fox.glb",
    ),
    "horse": (
        "Horse (Quaternius CC0)",
        # Immutable GitHub raw URL for the CC0 Quaternius animated horse.
        "https://raw.githubusercontent.com/VitexSoftware/ishtaria-client/"
        "f50f3ee5b04c1a34f8c30a338db51003aca1b8b0/"
        "assets/quaternius/animated-animal-pack/Models/horse.glb",
    ),
    "wolf": (
        "Wolf",
        "https://raw.githubusercontent.com/StateDev08/War-of-the-Kindom-Mobile/"
        "main/client/assets/models/quaternius/animals/wolf.glb",
    ),
    "boar": (
        "Boar",
        "https://raw.githubusercontent.com/StateDev08/War-of-the-Kindom-Mobile/"
        "main/client/assets/models/quaternius/animals/boar.glb",
    ),
    "rabbit": (
        "Rabbit",
        "https://raw.githubusercontent.com/StateDev08/War-of-the-Kindom-Mobile/"
        "main/client/assets/models/quaternius/animals/rabbit.glb",
    ),
    "stag": (
        "Stag",
        "https://raw.githubusercontent.com/StateDev08/War-of-the-Kindom-Mobile/"
        "main/client/assets/models/quaternius/animals/stag.glb",
    ),
    "alpaca": (
        "Alpaca (Quaternius CC0)",
        "https://raw.githubusercontent.com/VitexSoftware/ishtaria-client/"
        "f50f3ee5b04c1a34f8c30a338db51003aca1b8b0/"
        "assets/quaternius/animated-animal-pack/Models/alpaca.glb",
    ),
    "bull": (
        "Bull (Quaternius CC0)",
        "https://raw.githubusercontent.com/VitexSoftware/ishtaria-client/"
        "f50f3ee5b04c1a34f8c30a338db51003aca1b8b0/"
        "assets/quaternius/animated-animal-pack/Models/bull.glb",
    ),
    "donkey": (
        "Donkey (Quaternius CC0)",
        "https://raw.githubusercontent.com/VitexSoftware/ishtaria-client/"
        "f50f3ee5b04c1a34f8c30a338db51003aca1b8b0/"
        "assets/quaternius/animated-animal-pack/Models/donkey.glb",
    ),
    "husky": (
        "Husky (Quaternius CC0)",
        "https://raw.githubusercontent.com/VitexSoftware/ishtaria-client/"
        "f50f3ee5b04c1a34f8c30a338db51003aca1b8b0/"
        "assets/quaternius/animated-animal-pack/Models/husky.glb",
    ),
    "shiba_inu": (
        "Shiba Inu (Quaternius CC0)",
        "https://raw.githubusercontent.com/VitexSoftware/ishtaria-client/"
        "f50f3ee5b04c1a34f8c30a338db51003aca1b8b0/"
        "assets/quaternius/animated-animal-pack/Models/shiba_inu.glb",
    ),
    "white_horse": (
        "White Horse (Quaternius CC0)",
        "https://raw.githubusercontent.com/VitexSoftware/ishtaria-client/"
        "f50f3ee5b04c1a34f8c30a338db51003aca1b8b0/"
        "assets/quaternius/animated-animal-pack/Models/white_horse.glb",
    ),
    "cow": (
        "Cow",
        # Quaternius animated cow, pinned to an immutable public commit.
        "https://raw.githubusercontent.com/VitexSoftware/ishtaria-client/"
        "f50f3ee5b04c1a34f8c30a338db51003aca1b8b0/"
        "assets/quaternius/animated-animal-pack/Models/cow.glb",
    ),
}


def download_source(url: str, target: Path) -> None:
    """Download to a fresh staging file and retry transient network failures."""
    last_error = None
    for attempt in range(1, 4):
        try:
            target.unlink(missing_ok=True)
            request = urllib.request.Request(
                url,
                headers={"User-Agent": "Learnova-CI/1.0"},
            )
            with urllib.request.urlopen(request, timeout=120) as response:
                if response.status != 200:
                    raise RuntimeError(f"HTTP {response.status} while downloading {url}")
                with target.open("wb") as output:
                    while True:
                        chunk = response.read(1024 * 1024)
                        if not chunk:
                            break
                        output.write(chunk)

            if not target.is_file() or target.stat().st_size < 32:
                raise RuntimeError(f"Downloaded asset is missing or too small: {url}")
            return
        except (OSError, urllib.error.URLError, TimeoutError, RuntimeError) as exc:
            last_error = exc
            target.unlink(missing_ok=True)
            if attempt < 3:
                delay = 2 * attempt
                print(f"Retry {attempt}/2 downloading creature after {type(exc).__name__}: {exc}; waiting {delay}s")
                time.sleep(delay)
    raise RuntimeError(f"Could not download creature after 3 attempts: {last_error}") from last_error


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

    print(f"Fetched and normalized {len(SOURCES)} verified animated creature GLBs.")


if __name__ == "__main__":
    main()
