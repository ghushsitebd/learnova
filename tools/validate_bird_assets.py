#!/usr/bin/env python3
"""Validate Learnova's generated rigid-mesh bird GLBs and wing animations."""
from pathlib import Path
import json
import struct
import sys

ROOT = Path("app/src/main/assets/creatures")
SPECIES = ("parrot", "falcon", "owl", "peacock", "ostrich")

def inspect(path):
    data = path.read_bytes()
    if len(data) < 20 or data[:4] != b"glTF":
        raise ValueError("invalid GLB header")
    version, declared = struct.unpack_from("<II", data, 4)
    if version != 2 or declared != len(data):
        raise ValueError("GLB version/declared length mismatch")
    offset = 12
    document = None
    binary_length = 0
    while offset + 8 <= len(data):
        length, kind = struct.unpack_from("<II", data, offset)
        offset += 8
        end = offset + length
        if end > len(data):
            raise ValueError("chunk extends beyond GLB")
        chunk = data[offset:end]
        offset = end
        if kind == 0x4E4F534A and document is None:
            document = json.loads(chunk.rstrip(b" \t\r\n\x00").decode("utf-8"))
        elif kind == 0x004E4942:
            binary_length = length
    if document is None or not binary_length:
        raise ValueError("missing JSON or BIN chunk")
    if document.get("asset", {}).get("generator") != "Learnova procedural animated bird builder":
        raise ValueError("unexpected asset generator")
    nodes = document.get("nodes", [])
    meshes = document.get("meshes", [])
    animations = document.get("animations", [])
    accessors = document.get("accessors", [])
    views = document.get("bufferViews", [])
    if not meshes or not animations:
        raise ValueError("bird must contain mesh geometry and animation")
    animated = set()
    max_time = 0.0
    for animation in animations:
        if animation.get("name") != "WingFlap":
            raise ValueError("missing WingFlap animation")
        for channel in animation.get("channels", []):
            target = channel.get("target", {})
            node = target.get("node")
            if not isinstance(node, int) or not 0 <= node < len(nodes):
                raise ValueError("animation targets invalid node")
            if target.get("path") != "rotation" or "mesh" not in nodes[node]:
                raise ValueError("wing animation must rotate a visible mesh node")
            sampler = animation["samplers"][channel["sampler"]]
            output = accessors[sampler["output"]]
            if output.get("type") != "VEC4" or output.get("count") != 5:
                raise ValueError("wing rotation must contain five quaternion keys")
            animated.add(node)
            input_accessor = accessors[sampler["input"]]
            max_time = max(max_time, float(input_accessor.get("max", [0])[-1]))
    if len(animated) != 2 or max_time < 1.9:
        raise ValueError("both wings must animate over a two-second loop")
    for view in views:
        if view.get("buffer", 0) != 0 or view.get("byteOffset", 0) + view.get("byteLength", 0) > binary_length:
            raise ValueError("bufferView points outside embedded binary buffer")
    return len(meshes), len(animated), max_time

def main():
    failures = []
    for species in SPECIES:
        try:
            path = ROOT / (species + ".glb")
            meshes, wings, duration = inspect(path)
            print(f"OK bird {species}: meshes={meshes}, animated_wings={wings}, loop={duration:.1f}s")
        except (OSError, ValueError, KeyError, IndexError, TypeError, struct.error, json.JSONDecodeError) as exc:
            failures.append(f"{species}: {exc}")
    if failures:
        for failure in failures:
            print("::error::" + failure)
        sys.exit(1)
    print(f"Validated {len(SPECIES)} animated bird GLBs.")
if __name__ == "__main__":
    main()
