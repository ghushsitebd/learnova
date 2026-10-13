#!/usr/bin/env python3
"""Regression tests for deterministic launch-batch vehicle and dinosaur GLBs."""

from __future__ import annotations

import hashlib
import importlib.util
import json
import struct
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GENERATOR_PATH = ROOT / "tools" / "generate_vehicle_prototypes.py"
SPEC = importlib.util.spec_from_file_location("learnova_vehicle_generator", GENERATOR_PATH)
GENERATOR = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(GENERATOR)

DINOSAURS = (
    (97, "T-Rex Explorer Mount", "dinosaur_mount"),
    (98, "Triceratops Trail Mount", "dinosaur_mount"),
    (99, "Stegosaurus Forest Mount", "dinosaur_mount"),
    (100, "Parasaurolophus Adventure Mount", "dinosaur_mount"),
)


def read_glb(path: Path) -> tuple[dict, bytes]:
    raw = path.read_bytes()
    if len(raw) < 20 or raw[:4] != b"glTF":
        raise AssertionError(f"{path.name}: missing GLB header")
    version, declared_length = struct.unpack_from("<II", raw, 4)
    if version != 2 or declared_length != len(raw):
        raise AssertionError(f"{path.name}: invalid GLB version/length")
    json_length, json_type = struct.unpack_from("<II", raw, 12)
    if json_type != GENERATOR.JSON_CHUNK:
        raise AssertionError(f"{path.name}: JSON chunk is not first")
    json_start = 20
    document = json.loads(raw[json_start:json_start + json_length].decode("utf-8").rstrip(" \\t\\r\\n\\0"))
    binary_header = json_start + json_length
    if binary_header + 8 > len(raw):
        raise AssertionError(f"{path.name}: missing binary chunk header")
    binary_length, binary_type = struct.unpack_from("<II", raw, binary_header)
    binary_start = binary_header + 8
    if binary_type != GENERATOR.BIN_CHUNK or binary_start + binary_length != len(raw):
        raise AssertionError(f"{path.name}: invalid binary chunk boundary")
    return document, raw[binary_start:binary_start + binary_length]


class VehiclePrototypeTests(unittest.TestCase):
    def test_first_batch_dinosaur_mounts_are_distinct_and_rider_ready(self):
        with tempfile.TemporaryDirectory(prefix="learnova-dino-test-") as directory:
            output = Path(directory)
            hashes = set()
            for vehicle_id, name, kind in DINOSAURS:
                first = output / f"{vehicle_id:03d}-first.glb"
                second = output / f"{vehicle_id:03d}-repeat.glb"
                GENERATOR.make_glb(first, vehicle_id, name, kind)
                GENERATOR.make_glb(second, vehicle_id, name, kind)
                first_bytes = first.read_bytes()
                self.assertEqual(hashlib.sha256(first_bytes).digest(), hashlib.sha256(second.read_bytes()).digest(),
                                 f"{name} generation must be deterministic")
                digest = hashlib.sha256(first_bytes).hexdigest()
                self.assertNotIn(digest, hashes, f"{name} must not duplicate another mount")
                hashes.add(digest)

                document, binary = read_glb(first)
                extras = document["asset"].get("extras", {})
                self.assertEqual(document["asset"]["version"], "2.0")
                self.assertEqual(extras.get("vehicleId"), vehicle_id)
                self.assertEqual(extras.get("vehicleType"), "dinosaur_mount")
                self.assertEqual(extras.get("expectedWheelCount"), 0)
                self.assertEqual(extras.get("wheelMeshCount"), 0)
                self.assertGreaterEqual(len(document.get("meshes", [])), 20)
                self.assertGreaterEqual(len(document.get("nodes", [])), 21)
                self.assertEqual(document["scenes"][0]["nodes"], [len(document["nodes"]) - 1])
                self.assertEqual(document["nodes"][-1]["name"], "VehicleRoot")
                self.assertEqual(len(document["nodes"][-1]["children"]), len(document["nodes"]) - 1)
                self.assertGreater(len(binary), 1024)

                materials = document.get("materials", [])
                self.assertGreaterEqual(len(materials), 10)
                for material in materials:
                    pbr = material.get("pbrMetallicRoughness")
                    self.assertIsInstance(pbr, dict)
                    self.assertEqual(len(pbr.get("baseColorFactor", [])), 4)
                    self.assertGreaterEqual(pbr.get("roughnessFactor", -1), 0)
                    self.assertLessEqual(pbr.get("roughnessFactor", 2), 1)

    def test_complete_launch_batch_catalog_and_generated_models(self):
        """Exercise all 100 launch slots, not only the four dinosaur mounts."""
        catalog_text = GENERATOR.CATALOG.read_text(encoding="utf-8")
        entries = [
            (int(vehicle_id), name, kind, key)
            for vehicle_id, name, kind, key in GENERATOR.RE.findall(catalog_text)
            if int(vehicle_id) <= GENERATOR.VEHICLE_COUNT
        ]
        self.assertEqual(len(entries), 100, "launch catalog must define exactly 100 initial slots")
        self.assertEqual([entry[0] for entry in entries], list(range(1, 101)))
        self.assertEqual(len({entry[3] for entry in entries}), 100, "asset keys must be unique")

        with tempfile.TemporaryDirectory(prefix="learnova-launch-batch-") as directory:
            output = Path(directory)
            digests = set()
            for vehicle_id, name, kind, key in entries:
                path = output / f"{key}.glb"
                GENERATOR.make_glb(path, vehicle_id, name, kind)
                document, binary = read_glb(path)
                extras = document["asset"].get("extras", {})
                self.assertEqual(extras.get("vehicleId"), vehicle_id, key)
                self.assertEqual(extras.get("vehicleName"), name, key)
                self.assertEqual(extras.get("vehicleType"), kind, key)
                self.assertGreater(len(binary), 1024, key)
                digest = hashlib.sha256(path.read_bytes()).digest()
                self.assertNotIn(digest, digests, f"duplicate generated model: {key}")
                digests.add(digest)

                wheel_count = extras.get("wheelMeshCount")
                self.assertEqual(wheel_count, extras.get("expectedWheelCount"), key)
                if kind == "dinosaur_mount":
                    self.assertEqual(wheel_count, 0, key)
                    self.assertGreaterEqual(len(document.get("meshes", [])), 20, key)
                else:
                    self.assertGreater(wheel_count, 0, key)

    def test_garage_is_limited_to_the_first_100_launch_vehicles(self):
        """Future catalog entries must not leak into the 100-vehicle launch garage."""
        garage_path = ROOT / "app" / "src" / "main" / "java" / "com" / "learnova" / "app" / "VehicleGarageView.kt"
        garage = garage_path.read_text(encoding="utf-8")
        self.assertIn("private val launchVehicles = VehicleCatalog.all.take(100)", garage)
        self.assertIn("private fun pageSizeForWidth(width: Float)", garage)
        self.assertIn("ceil(launchVehicles.size / pageSizeForWidth(width).toDouble())", garage)
        self.assertIn("drawCard(c, launchVehicles[index]", garage)
        self.assertIn("onSelected(launchVehicles[selectedId - 1])", garage)
        self.assertNotIn("VehicleCatalog.all[index]", garage)
        self.assertNotIn("VehicleCatalog.all[selectedId - 1]", garage)

    def test_vehicle_dimensions_stay_positive_and_plausible(self):
        for vehicle_id, name, kind in DINOSAURS:
            length, width, height, wheel = GENERATOR.dims(kind, name, vehicle_id)
            self.assertGreater(length, 0)
            self.assertGreater(width, 0)
            self.assertGreater(height, 0)
            self.assertEqual(wheel, 0.18)
            self.assertLess(length, 6)
            self.assertLess(width, 3)
            self.assertLess(height, 4)


if __name__ == "__main__":
    unittest.main(verbosity=2)
