#!/usr/bin/env python3
"""Fetch verified animated CC0 animal sources and normalize them to GLB."""

from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path("app/src/main/assets/creatures")
FOLDER_URL = "https://drive.google.com/drive/folders/1uJ3N5HfB7jKTseJUNQr3N4YaN0UuEtHk?usp=sharing"
SPECIES = {"deer": "Deer", "fox": "Fox", "horse": "Horse", "wolf": "Wolf"}


def install_tools() -> None:
    subprocess.run(
        [sys.executable, "-m", "pip", "install", "--disable-pip-version-check",
         "--no-input", "gdown==5.2.0"],
        check=True,
    )
    # Use npx instead of a global npm install so CI does not depend on the runner's
    # global npm prefix or filesystem permissions. The exact CLI version remains pinned.
    subprocess.run(
        ["npx", "--yes", "@gltf-transform/cli@4.2.0", "--version"],
        check=True,
    )


def main() -> None:
    install_tools()
    ROOT.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="learnova-quaternius-") as tmp:
        download_root = Path(tmp) / "pack"
        subprocess.run(
            [sys.executable, "-m", "gdown", "--folder", FOLDER_URL, "-O", str(download_root)],
            check=True,
        )

        sources = list(download_root.rglob("*"))
        for key, display_name in SPECIES.items():
            matches = [
                p for p in sources
                if p.is_file()
                and p.suffix.lower() in {".gltf", ".glb"}
                and p.stem.lower() == display_name.lower()
            ]
            if not matches:
                raise RuntimeError(f"{display_name}: animated glTF/GLB source was not found")

            source = matches[0]
            target = ROOT / f"{key}.glb"

            if source.suffix.lower() == ".glb":
                data = source.read_bytes()
                if len(data) < 32 or data[:4] != b"glTF":
                    raise RuntimeError(f"{display_name}: invalid GLB")
                target.write_bytes(data)
            else:
                subprocess.run(
                    ["npx", "--yes", "@gltf-transform/cli@4.2.0", "copy", str(source), str(target)],
                    check=True,
                )
                data = target.read_bytes()
                if len(data) < 32 or data[:4] != b"glTF":
                    raise RuntimeError(
                        f"{display_name}: glTF conversion did not produce a valid GLB"
                    )

            print(f"Normalized animated {display_name}: {len(data)} bytes")

    print(f"Fetched and normalized {len(SPECIES)} animated CC0 creature GLBs.")


if __name__ == "__main__":
    main()
