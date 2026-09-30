#!/usr/bin/env python3
"""Fetch pinned, redistributable CC0 creature GLBs into the Android assets tree."""

from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path("app/src/main/assets/creatures")
ASSETS = {
    "lion.glb": "https://cdn.3dassets.dev/assets/30234/v1/model.glb",
    "tiger.glb": "https://cdn.3dassets.dev/assets/30235/v1/model.glb",
    "elephant.glb": "https://cdn.3dassets.dev/assets/30210/v1/model.glb",
    "deer.glb": "https://cdn.3dassets.dev/assets/30203/v1/model.glb",
}

def main() -> None:
    ROOT.mkdir(parents=True, exist_ok=True)
    for filename, url in ASSETS.items():
        target = ROOT / filename
        request = Request(url, headers={"User-Agent": "Learnova-CI/1.0"})
        with urlopen(request, timeout=60) as response:
            data = response.read()
        if len(data) < 32 or data[:4] != b"glTF":
            raise RuntimeError(f"{filename}: downloaded file is not a valid GLB")
        target.write_bytes(data)
        print(f"Fetched {filename}: {len(data)} bytes")
    print(f"Fetched {len(ASSETS)} CC0 creature GLBs into {ROOT}")

if __name__ == "__main__":
    main()
