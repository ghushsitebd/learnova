#!/usr/bin/env python3
"""Fetch small, license-verified real vehicle GLBs for the Learnova garage.

The 100-slot catalog remains data-driven; these authored models establish the
first real-asset lane while the remaining slots continue to use the safe base
vehicle fallback until dedicated models are supplied.
"""
from pathlib import Path
import struct
import urllib.request

ROOT = Path("app/src/main/assets/vehicles")

SOURCES = {
    "vehicle_001_city_car": (
        "ToyCar",
        "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/ToyCar/glTF-Binary/ToyCar.glb",
    ),
    "vehicle_040_box_truck": (
        "CesiumMilkTruck",
        "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/CesiumMilkTruck/glTF-Binary/CesiumMilkTruck.glb",
    ),
}

def fetch(url: str, target: Path) -> None:
    req = urllib.request.Request(url, headers={"User-Agent": "Learnova-CI/1.0"})
    with urllib.request.urlopen(req, timeout=120) as response, target.open("wb") as out:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            out.write(chunk)

def validate_glb(path: Path) -> None:
    data = path.read_bytes()
    if len(data) < 20 or data[:4] != b"glTF":
        raise RuntimeError(f"{path}: invalid GLB header")
    version, declared = struct.unpack_from("<II", data, 4)
    if version != 2:
        raise RuntimeError(f"{path}: unsupported GLB version {version}")
    if declared != len(data):
        raise RuntimeError(f"{path}: declared length {declared} != actual {len(data)}")

def main() -> None:
    ROOT.mkdir(parents=True, exist_ok=True)
    for key, (label, url) in SOURCES.items():
        target = ROOT / f"{key}.glb"
        fetch(url, target)
        validate_glb(target)
        print(f"OK vehicle {label}: {target} ({target.stat().st_size} bytes)")
    print(f"Fetched {len(SOURCES)} verified real vehicle GLBs.")

if __name__ == "__main__":
    main()
