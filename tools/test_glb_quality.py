#!/usr/bin/env python3
"""Structural and geometry-quality gate for all 100 generated Learnova launch GLBs.

This validates the generated files as glTF 2.0 containers, including binary
ranges, accessor bounds, finite vertex data, index ranges, material references,
and normal-vector quality. It is not a substitute for visual review in-engine.
"""
from __future__ import annotations

import importlib.util
import json
import math
import re
import struct
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GENERATOR_PATH = ROOT / "tools" / "generate_vehicle_prototypes.py"
SPEC = importlib.util.spec_from_file_location("learnova_glb_generator", GENERATOR_PATH)
GENERATOR = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(GENERATOR)

COMPONENT_BYTES = {5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4}
TYPE_WIDTH = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT2": 4, "MAT3": 9, "MAT4": 16}
FLOAT_COMPONENT = 5126


def load_glb(path: Path) -> tuple[dict, bytes]:
    raw = path.read_bytes()
    assert len(raw) >= 20 and raw[:4] == b"glTF", f"{path.name}: bad GLB magic/header"
    version, declared_length = struct.unpack_from("<II", raw, 4)
    assert version == 2, f"{path.name}: expected glTF 2.0"
    assert declared_length == len(raw), f"{path.name}: declared file length mismatch"
    json_length, json_type = struct.unpack_from("<II", raw, 12)
    assert json_type == GENERATOR.JSON_CHUNK, f"{path.name}: JSON chunk must be first"
    json_end = 20 + json_length
    assert json_end + 8 <= len(raw), f"{path.name}: missing binary chunk"
    document = json.loads(raw[20:json_end].decode("utf-8").rstrip(" \\t\\r\\n\\0"))
    binary_length, binary_type = struct.unpack_from("<II", raw, json_end)
    binary_start = json_end + 8
    assert binary_type == GENERATOR.BIN_CHUNK, f"{path.name}: missing BIN chunk"
    assert binary_start + binary_length == len(raw), f"{path.name}: BIN chunk boundary mismatch"
    return document, raw[binary_start:binary_start + binary_length]


def validate(path: Path, expected_id: int, expected_name: str, expected_kind: str) -> None:
    doc, binary = load_glb(path)
    assert doc.get("asset", {}).get("version") == "2.0", f"{path.name}: missing glTF 2.0 asset version"
    assert doc.get("scene", 0) < len(doc.get("scenes", [])), f"{path.name}: invalid default scene"
    scenes, nodes = doc.get("scenes", []), doc.get("nodes", [])
    meshes, accessors = doc.get("meshes", []), doc.get("accessors", [])
    views, materials = doc.get("bufferViews", []), doc.get("materials", [])
    assert scenes and nodes and meshes and accessors and views, f"{path.name}: required scene data absent"
    assert len(doc.get("buffers", [])) == 1, f"{path.name}: expected one embedded buffer"
    assert doc["buffers"][0].get("byteLength", -1) <= len(binary), f"{path.name}: buffer exceeds BIN chunk"
    extras = doc["asset"].get("extras", {})
    assert extras.get("vehicleId") == expected_id, f"{path.name}: vehicle ID mismatch"
    assert extras.get("vehicleName") == expected_name, f"{path.name}: vehicle name mismatch"
    assert extras.get("vehicleType") == expected_kind, f"{path.name}: vehicle type mismatch"
    assert len(meshes) >= 8, f"{path.name}: suspiciously few mesh parts"
    assert len(materials) >= 8, f"{path.name}: missing PBR material variety"

    for view_index, view in enumerate(views):
        offset = view.get("byteOffset", 0)
        length = view.get("byteLength", -1)
        assert offset >= 0 and length >= 0 and offset + length <= len(binary), (
            f"{path.name}: bufferView {view_index} exceeds binary data"
        )
        assert offset % 4 == 0, f"{path.name}: bufferView {view_index} is not 4-byte aligned"

    for accessor_index, accessor in enumerate(accessors):
        ctype = accessor.get("componentType")
        kind = accessor.get("type")
        count = accessor.get("count", 0)
        assert ctype in COMPONENT_BYTES and kind in TYPE_WIDTH and count > 0, (
            f"{path.name}: accessor {accessor_index} has invalid type/count"
        )
        view_index = accessor.get("bufferView")
        assert isinstance(view_index, int) and 0 <= view_index < len(views), (
            f"{path.name}: accessor {accessor_index} has invalid bufferView"
        )
        view = views[view_index]
        offset = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
        stride = view.get("byteStride", COMPONENT_BYTES[ctype] * TYPE_WIDTH[kind])
        element_size = COMPONENT_BYTES[ctype] * TYPE_WIDTH[kind]
        assert stride >= element_size, f"{path.name}: accessor {accessor_index} has short stride"
        end = offset + (count - 1) * stride + element_size
        assert offset >= view.get("byteOffset", 0) and end <= view.get("byteOffset", 0) + view["byteLength"], (
            f"{path.name}: accessor {accessor_index} reads outside its bufferView"
        )
        if ctype == FLOAT_COMPONENT:
            fmt = "<" + "f" * TYPE_WIDTH[kind]
            for element in range(count):
                values = struct.unpack_from(fmt, binary, offset + element * stride)
                assert all(math.isfinite(value) for value in values), (
                    f"{path.name}: accessor {accessor_index} contains NaN/Infinity"
                )
            if kind == "VEC3" and accessor.get("min") is not None:
                actual_min = [math.inf] * 3
                actual_max = [-math.inf] * 3
                for element in range(count):
                    values = struct.unpack_from("<fff", binary, offset + element * stride)
                    for axis, value in enumerate(values):
                        actual_min[axis] = min(actual_min[axis], value)
                        actual_max[axis] = max(actual_max[axis], value)
                for axis in range(3):
                    assert math.isclose(actual_min[axis], accessor["min"][axis], abs_tol=1e-4), (
                        f"{path.name}: accessor {accessor_index} min bound incorrect"
                    )
                    assert math.isclose(actual_max[axis], accessor["max"][axis], abs_tol=1e-4), (
                        f"{path.name}: accessor {accessor_index} max bound incorrect"
                    )

    primitive_count = 0
    for mesh_index, mesh in enumerate(meshes):
        assert mesh.get("primitives"), f"{path.name}: mesh {mesh_index} has no primitives"
        for primitive in mesh["primitives"]:
            primitive_count += 1
            attrs = primitive.get("attributes", {})
            assert "POSITION" in attrs and "NORMAL" in attrs, f"{path.name}: missing position/normal attributes"
            pos_i, normal_i = attrs["POSITION"], attrs["NORMAL"]
            assert 0 <= pos_i < len(accessors) and 0 <= normal_i < len(accessors), (
                f"{path.name}: primitive accessor reference out of range"
            )
            positions, normals = accessors[pos_i], accessors[normal_i]
            assert positions["type"] == "VEC3" and normals["type"] == "VEC3", (
                f"{path.name}: positions/normals must be VEC3"
            )
            assert positions["count"] == normals["count"], f"{path.name}: position/normal count mismatch"
            material_i = primitive.get("material")
            assert isinstance(material_i, int) and 0 <= material_i < len(materials), (
                f"{path.name}: primitive references missing material"
            )
            assert materials[material_i].get("pbrMetallicRoughness"), (
                f"{path.name}: material {material_i} is not PBR-configured"
            )
            indices_i = primitive.get("indices")
            assert isinstance(indices_i, int) and 0 <= indices_i < len(accessors), (
                f"{path.name}: primitive has no valid index accessor"
            )
            indices = accessors[indices_i]
            assert indices["type"] == "SCALAR" and indices["componentType"] in (5121, 5123, 5125), (
                f"{path.name}: index accessor must use an unsigned integer scalar"
            )
            assert indices["count"] % 3 == 0, f"{path.name}: triangle index count is not divisible by three"
            view = views[indices["bufferView"]]
            offset = view.get("byteOffset", 0) + indices.get("byteOffset", 0)
            width = COMPONENT_BYTES[indices["componentType"]]
            fmt = {5121: "B", 5123: "H", 5125: "I"}[indices["componentType"]]
            max_index = max(struct.unpack_from("<" + fmt, binary, offset + i * width)[0]
                            for i in range(indices["count"]))
            assert max_index < positions["count"], f"{path.name}: triangle index exceeds vertex count"
    assert primitive_count == len(meshes), f"{path.name}: unexpected primitive topology"

    for node_index, node in enumerate(nodes):
        mesh_index = node.get("mesh")
        if mesh_index is not None:
            assert 0 <= mesh_index < len(meshes), f"{path.name}: node {node_index} references missing mesh"
        for child in node.get("children", []):
            assert isinstance(child, int) and 0 <= child < len(nodes), f"{path.name}: invalid child node"
    root = nodes[scenes[doc.get("scene", 0)]["nodes"][0]]
    assert root.get("name") == "VehicleRoot", f"{path.name}: scene root is not VehicleRoot"


def main() -> int:
    catalog = GENERATOR.CATALOG.read_text(encoding="utf-8")
    entries = [
        (int(vehicle_id), name, kind, key)
        for vehicle_id, name, kind, key in GENERATOR.RE.findall(catalog)
        if int(vehicle_id) <= GENERATOR.VEHICLE_COUNT
    ]
    assert len(entries) == 100, f"expected 100 launch slots, got {len(entries)}"
    assert [entry[0] for entry in entries] == list(range(1, 101)), "launch IDs must be contiguous 1..100"
    assert len({entry[3] for entry in entries}) == 100, "launch asset keys must be unique"

    with tempfile.TemporaryDirectory(prefix="learnova-glb-quality-") as tmp:
        out = Path(tmp)
        for vehicle_id, name, kind, key in entries:
            path = out / f"{key}.glb"
            GENERATOR.make_glb(path, vehicle_id, name, kind)
            validate(path, vehicle_id, name, kind)
    print("GLB quality gate passed: 100/100 launch models; scene, binary ranges, finite geometry, "
          "accessors, triangle indices, PBR materials, and node references validated.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
