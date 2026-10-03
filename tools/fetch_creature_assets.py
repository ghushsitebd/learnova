#!/usr/bin/env python3
"""Fetch only the verified animated CC0 creature sources required by CI.

The upstream Google Drive folder contains many unrelated files. Downloading the
whole folder made CI fail when any unrelated file hit Drive's download quota.
Use the exact glTF file IDs instead, then normalize each source to a self-contained
GLB.
"""
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path("app/src/main/assets/creatures")

# Quaternius animated glTF sources from the published CC0 pack.
# Keep this list limited to assets actually promoted by the near-field runtime.
SOURCES = {
    "deer": ("Deer", "1iGpXKrqYGyZCPGHPPSuDAoKnOXLhXJ0q"),
    "fox": ("Fox", "1z-CWoUC2vJxrqgGFTYlMaywpE1ooV-bA"),
    "horse": ("Horse", "1hbtY8kxnXiPdwYGV7yWRgU0jl_-Q-LG"),
    "wolf": ("Wolf", "1lFQoQ9ln2Z2wGuFFWObj9i5jHqUl_ftG"),
}


def install_tools() -> None:
    subprocess.run(
        [sys.executable, "-m", "pip", "install", "--disable-pip-version-check",
         "--no-input", "gdown==5.2.0"],
        check=True,
    )
    subprocess.run(
        ["npx", "--yes", "@gltf-transform/cli@4.2.0", "--version"],
        check=True,
    )


def download_source(file_id: str, target: Path) -> None:
    # Retry transient Google Drive responses without ever downloading the full pack.
    last = None
    for attempt in range(3):
        try:
            subprocess.run(
                [sys.executable, "-m", "gdown", "--id", file_id, "-O", str(target),
                 "--fuzzy"],
                check=True,
            )
            if target.is_file() and target.stat().st_size > 0:
                return
        except subprocess.CalledProcessError as exc:
            last = exc
    raise RuntimeError(f"Google Drive download failed after retries: {file_id}") from last


def main() -> None:
    install_tools()
    ROOT.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="learnova-creatures-") as tmp:
        tmp_root = Path(tmp)
        for key, (display_name, file_id) in SOURCES.items():
            source = tmp_root / f"{display_name}.gltf"
            target = ROOT / f"{key}.glb"
            download_source(file_id, source)

            # The source is explicitly the animated glTF variant. Convert it to
            # one self-contained GLB so Android packaging/runtime has no external
            # buffers or textures to resolve.
            subprocess.run(
                ["npx", "--yes", "@gltf-transform/cli@4.2.0",
                 "copy", str(source), str(target)],
                check=True,
            )
            data = target.read_bytes()
            if len(data) < 32 or data[:4] != b"glTF":
                raise RuntimeError(
                    f"{display_name}: glTF conversion did not produce a valid GLB"
                )
            print(f"Normalized animated {display_name}: {len(data)} bytes")

    print(f"Fetched and normalized {len(SOURCES)} verified animated CC0 creature GLBs.")


if __name__ == "__main__":
    main()
