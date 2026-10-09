#!/usr/bin/env python3
"""Fetch and validate Learnova's small real-vehicle GLB assets.

Downloads are retried and staged to a temporary file so an interrupted
network transfer can never replace a previously valid asset with a partial
one. The garage catalog remains larger than the currently authored GLB set;
unavailable dedicated models use the renderer's safe base-vehicle fallback.
"""
from pathlib import Path
import json
import struct
import time
import urllib.error
import urllib.request

ROOT = Path("app/src/main/assets/vehicles")
MAX_ATTEMPTS = 4
RETRY_DELAY_SECONDS = 2
CHUNK_HEADER_SIZE = 8

SOURCES = {
    "vehicle_001_city_car": (
        "ToyCar",
        "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/ToyCar/glTF-Binary/ToyCar.glb",
    ),
    "vehicle_040_box_truck": (
        "CesiumMilkTruck",
        "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/CesiumMilkTruck/glTF-Binary/CesiumMilkTruck.glb",
    ),
    "vehicle_068_buggy": (
        "Buggy",
        "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/2.0/Buggy/glTF-Binary/Buggy.glb",
    ),
    # CarConcept is a distinct full vehicle model from Khronos glTF Sample Assets.
    # Upstream license: CC-BY-4.0; attribution is retained here for release compliance.
    "vehicle_100_2050_vision": (
        "CarConcept (CC-BY-4.0)",
        "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/CarConcept/glTF-Binary/CarConcept.glb",
    ),
}


def fetch(url: str, staging_path: Path) -> None:
    """Download one asset to a staging path; never touch the final asset here."""
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "Learnova-CI/1.0", "Accept": "model/gltf-binary,application/octet-stream"},
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        if response.status != 200:
            raise RuntimeError(f"HTTP {response.status} while downloading {url}")
        with staging_path.open("wb") as output:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                output.write(chunk)
            output.flush()


def validate_glb(path: Path) -> None:
    """Validate GLB v2 header and all declared chunk boundaries."""
    actual_length = path.stat().st_size
    if actual_length < 20:
        raise RuntimeError(f"{path}: GLB is too short ({actual_length} bytes)")

    with path.open("rb") as stream:
        header = stream.read(12)
        if len(header) != 12 or header[:4] != b"glTF":
            raise RuntimeError(f"{path}: invalid GLB magic/header")
        version, declared_length = struct.unpack_from("<II", header, 4)
        if version != 2:
            raise RuntimeError(f"{path}: unsupported GLB version {version}")
        if declared_length != actual_length:
            raise RuntimeError(
                f"{path}: declared length {declared_length} != actual {actual_length}"
            )

        offset = 12
        chunk_index = 0
        json_chunk_seen = False
        while offset < declared_length:
            if declared_length - offset < CHUNK_HEADER_SIZE:
                raise RuntimeError(f"{path}: truncated chunk header at byte {offset}")
            stream.seek(offset)
            chunk_header = stream.read(CHUNK_HEADER_SIZE)
            chunk_length, chunk_type = struct.unpack("<II", chunk_header)
            data_start = offset + CHUNK_HEADER_SIZE
            data_end = data_start + chunk_length
            if chunk_length == 0 or chunk_length % 4 != 0:
                raise RuntimeError(f"{path}: invalid chunk length {chunk_length}")
            if data_end > declared_length:
                raise RuntimeError(f"{path}: chunk extends past declared GLB length")

            if chunk_index == 0:
                if chunk_type != 0x4E4F534A:  # JSON
                    raise RuntimeError(f"{path}: first GLB chunk is not JSON")
                stream.seek(data_start)
                try:
                    json.loads(stream.read(chunk_length).decode("utf-8").rstrip(" \\t\\r\\n\\0"))
                except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                    raise RuntimeError(f"{path}: malformed GLB JSON chunk: {exc}") from exc
                json_chunk_seen = True

            offset = data_end
            chunk_index += 1

        if offset != declared_length or not json_chunk_seen:
            raise RuntimeError(f"{path}: incomplete GLB chunk table")


def fetch_with_retry(url: str, target: Path) -> None:
    """Retry transient failures; atomically promote only a validated download."""
    staging_path = target.with_name(target.name + ".part")
    last_error = None
    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            staging_path.unlink(missing_ok=True)
            fetch(url, staging_path)
            validate_glb(staging_path)
            staging_path.replace(target)
            return
        except (OSError, urllib.error.URLError, TimeoutError, RuntimeError) as exc:
            last_error = exc
            staging_path.unlink(missing_ok=True)
            if attempt < MAX_ATTEMPTS:
                delay = RETRY_DELAY_SECONDS * attempt
                print(
                    f"Retry {attempt}/{MAX_ATTEMPTS - 1} for {target.name} "
                    f"after {type(exc).__name__}: {exc}; waiting {delay}s"
                )
                time.sleep(delay)
    raise RuntimeError(
        f"Could not fetch a valid asset for {target.name} after {MAX_ATTEMPTS} attempts: "
        f"{last_error}"
    ) from last_error


def main() -> None:
    ROOT.mkdir(parents=True, exist_ok=True)
    for key, (label, url) in SOURCES.items():
        target = ROOT / f"{key}.glb"
        fetch_with_retry(url, target)
        print(f"OK vehicle {label}: {target} ({target.stat().st_size} bytes)")
    print(f"Fetched and validated {len(SOURCES)} dedicated vehicle GLBs.")


if __name__ == "__main__":
    main()
