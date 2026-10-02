#!/usr/bin/env python3
"""Fetch verified animated CC0 quadruped GLBs from Quaternius."""

from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path("app/src/main/assets/creatures")
FOLDER_URL = "https://drive.google.com/drive/folders/1uJ3N5HfB7jKTseJUNQr3N4YaN0UuEtHk?usp=sharing"
SPECIES = {
    "deer": "Deer",
    "fox": "Fox",
    "horse": "Horse",
    "wolf": "Wolf",
}


def install_gdown() -> None:
    subprocess.run(
        [
            sys.executable,
            "-m",
            "pip",
            "install",
            "--disable-pip-version-check",
            "--no-input",
            "gdown==5.2.0",
        ],
        check=True,
    )


def main() -> None:
    install_gdown()
    ROOT.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="learnova-quaternius-") as tmp:
        download_root = Path(tmp) / "pack"
        subprocess.run(
            [
                sys.executable,
                "-m",
                "gdown",
                "--folder",
                FOLDER_URL,
                "-O",
                str(download_root),
            ],
            check=True,
        )

        candidates = [
            p for p in download_root.rglob("*")
            if p.is_file() and p.suffix.lower() == ".glb"
        ]

        for key, display_name in SPECIES.items():
            matches = [
                p for p in candidates
                if p.stem.lower() == display_name.lower()
            ]
            if not matches:
                raise RuntimeError(
                    f"{display_name}: animated GLB was not found in the CC0 pack"
                )

            source = matches[0]
            data = source.read_bytes()
            if len(data) < 32 or data[:4] != b"glTF":
                raise RuntimeError(
                    f"{display_name}: downloaded file is not a valid GLB"
                )

            target = ROOT / f"{key}.glb"
            target.write_bytes(data)
            print(f"Fetched animated {display_name}: {len(data)} bytes")

    print(
        f"Fetched {len(SPECIES)} verified animated CC0 creature GLBs into {ROOT}"
    )


if __name__ == "__main__":
    main()
