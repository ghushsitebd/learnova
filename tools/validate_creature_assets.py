#!/usr/bin/env python3
"""Validate downloaded Learnova creature GLBs as real animated glTF 2.0 assets."""

from pathlib import Path
import base64
import json
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/creatures"
SPECIES = ("deer", "fox", "horse", "wolf", "cow", "boar", "rabbit", "stag", "lion")


def read_glb_json(path: Path):
    data = path.read_bytes()
    return read_glb_json_bytes(data)


def read_glb_json_bytes(data: bytes):
    if len(data) < 20 or data[:4] != b"glTF":
        raise ValueError("invalid GLB header")
    version, declared = struct.unpack_from("<II", data, 4)
    if version != 2:
        raise ValueError(f"unsupported GLB version {version}")
    if declared != len(data):
        raise ValueError(f"declared length {declared} != actual {len(data)}")
    offset = 12
    json_chunk = None
    while offset + 8 <= len(data):
        length, kind = struct.unpack_from("<II", data, offset)
        offset += 8
        end = offset + length
        if end > len(data):
            raise ValueError("chunk exceeds GLB length")
        chunk = data[offset:end]
        offset = end
        if kind == 0x4E4F534A:
            json_chunk = chunk
            break
    if json_chunk is None:
        raise ValueError("missing JSON chunk")
    return json.loads(json_chunk.rstrip(b" \t\r\n\x00").decode("utf-8"))


def validate(path: Path):
    doc = read_glb_json(path)
    if doc.get("asset", {}).get("version") != "2.0":
        raise ValueError("glTF asset.version must be 2.0")

    nodes = doc.get("nodes", [])
    skins = doc.get("skins", [])
    animations = doc.get("animations", [])
    meshes = doc.get("meshes", [])
    if not meshes:
        raise ValueError("no meshes")
    if not animations:
        raise ValueError("no skeletal animation tracks")
    if not skins:
        raise ValueError("no skin/skeleton")

    animated_nodes = set()
    max_time = 0.0
    accessors = doc.get("accessors", [])

    for animation in animations:
        channels = animation.get("channels", [])
        samplers = animation.get("samplers", [])
        if not channels or not samplers:
            raise ValueError("animation has no channels/samplers")

        for channel in channels:
            target = channel.get("target", {})
            node = target.get("node")
            path_name = target.get("path")
            if not isinstance(node, int) or not 0 <= node < len(nodes):
                raise ValueError("animation targets invalid node")
            if path_name not in {"translation", "rotation", "scale", "weights"}:
                raise ValueError(f"unsupported animation target path: {path_name}")

            animated_nodes.add(node)
            sampler_index = channel.get("sampler")
            if not isinstance(sampler_index, int) or not 0 <= sampler_index < len(samplers):
                raise ValueError("animation references invalid sampler")

            accessor = samplers[sampler_index].get("input")
            if not isinstance(accessor, int) or not 0 <= accessor < len(accessors):
                raise ValueError("animation input accessor is invalid")

            maximum = accessors[accessor].get("max", [0.0])
            if maximum:
                max_time = max(max_time, float(maximum[-1]))

    if not animated_nodes:
        raise ValueError("animation contains no targeted nodes")
    if max_time <= 0.0:
        raise ValueError("animation duration is zero")

    skin_joints = set()
    for skin in skins:
        joints = skin.get("joints", [])
        if not joints:
            raise ValueError("skin has no joints")
        skin_joints.update(joints)

    if not (skin_joints & animated_nodes):
        raise ValueError("no animated node belongs to a skin joint set")

    return len(animations), len(animated_nodes), len(skin_joints), max_time


def main():
    failures = []
    checked = 0

    for species in SPECIES:
        path = ASSETS / f"{species}.glb"
        try:
            if species == "lion":
                b64_path = ASSETS / "lion.glb.b64"
                if not b64_path.is_file():
                    raise ValueError("missing lion.glb.b64")
                encoded = "".join(b64_path.read_text(encoding="ascii").split())
                data = base64.b64decode(encoded, validate=True)
                temp = ASSETS / ".lion-validation.glb"
                temp.write_bytes(data)
                try:
                    doc = read_glb_json_bytes(data)
                    if doc.get("asset", {}).get("version") != "2.0":
                        raise ValueError("glTF asset.version must be 2.0")
                    animations = len(doc.get("animations", []))
                    if animations == 0:
                        raise ValueError("no skeletal animation tracks")
                    # Reuse the full structural validator without requiring the
                    # repository to materialize the large binary as a tracked file.
                    animations, nodes, joints, duration = validate(temp)
                finally:
                    temp.unlink(missing_ok=True)
            else:
                raise ValueError("missing downloaded GLB")
            checked += 1
            print(
                f"OK creature {species}: animations={animations}, "
                f"animated_nodes={nodes}, joints={joints}, duration={duration:.3f}s"
            )
        except (OSError, ValueError, json.JSONDecodeError, struct.error, base64.binascii.Error) as exc:
            failures.append(f"{species}: {exc}")

    if failures:
        for failure in failures:
            print(f"::error::{failure}")
        sys.exit(1)

    print(f"Validated {checked}/{len(SPECIES)} real animated creature assets.")


if __name__ == "__main__":
    main()
