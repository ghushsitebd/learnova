#!/usr/bin/env python3
"""Regression gates for Learnova's generated wildlife and world-scene catalog.

This checks catalog integrity, not the existence or visual quality of 3D models.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app/src/main/java/com/learnova/app/WorldCatalog.kt"
text = CATALOG.read_text(encoding="utf-8")
errors = []

def fail(message):
    errors.append(message)

animals_match = re.search(r"private val baseAnimals = listOf\((.*?)\n    \)", text, re.S)
habitats_match = re.search(r"private val habitats = listOf\((.*?)\n    \)", text, re.S)
scenes_match = re.search(r"val scenes = listOf\((.*?)\n    \)", text, re.S)
if not animals_match or not habitats_match or not scenes_match:
    print("::error::World catalog structure could not be parsed.")
    sys.exit(1)

def quoted_values(block):
    return re.findall(r'"([^"]+)"', block)

animals = quoted_values(animals_match.group(1))
habitats = quoted_values(habitats_match.group(1))
scene_rows = re.findall(r'WorldScene\(\s*(\d+)\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)', scenes_match.group(1))
scene_ids = [int(row[0]) for row in scene_rows]
scene_names = [row[1] for row in scene_rows]

for name in sorted({x for x in animals if animals.count(x) > 1}):
    fail(f"Duplicate base animal species: {name}")
for name in sorted({x for x in habitats if habitats.count(x) > 1}):
    fail(f"Duplicate habitat: {name}")
if len(animals) < 100:
    fail(f"Expected at least 100 distinct base animal species, found {len(animals)}")
if len(habitats) < 10:
    fail(f"Expected at least 10 habitats, found {len(habitats)}")
if len(set(scene_ids)) != len(scene_ids):
    fail("World scene IDs must be unique")
if len(set(scene_names)) != len(scene_names):
    fail("World scene names must be unique")
if scene_ids != list(range(1, len(scene_ids) + 1)):
    fail("World scene IDs must be contiguous and start at 1")
if len(scene_rows) < 20:
    fail(f"Expected at least 20 authored world scenes, found {len(scene_rows)}")

required_species = {"Lion", "Elephant", "Bengal Tiger", "Triceratops", "Stegosaurus", "Brachiosaurus", "Velociraptor", "Tyrannosaurus"}
missing = sorted(required_species - set(animals))
if missing:
    fail("Required learning species missing: " + ", ".join(missing))

for required in ("Dinosaur Valley", "Arabic Learning Garden", "Quran Learning Garden", "Village Path", "Modern City"):
    if required not in scene_names:
        fail(f"Required world scene missing: {required}")

if "ArrayList<AnimalEntry>(baseAnimals.size * habitats.size)" not in text:
    fail("Generated animal/habitat catalog capacity contract missing")
if 'group = if (animal in listOf("Dinosaur","Triceratops","Stegosaurus","Brachiosaurus","Velociraptor","Tyrannosaurus","Mammoth")) "Prehistoric" else "Wildlife"' not in text:
    fail("Prehistoric species grouping contract changed; review before updating the audit")

if errors:
    for error in errors:
        print(f"::error::{error}")
    sys.exit(1)

print("Learnova world catalog contracts passed:")
print(f"- {len(animals)} unique base species × {len(habitats)} habitats = {len(animals) * len(habitats)} generated learning contexts")
print(f"- {len(scene_rows)} unique world scenes with contiguous IDs")
print("- required wildlife, dinosaur and Islamic-learning scene entries are present")
print("Note: catalog contexts are not equivalent to distinct animated 3D assets.")
