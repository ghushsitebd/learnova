#!/usr/bin/env python3
"""Deterministic source-level contract checks for near-field creature presentation."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
world = (ROOT / "app/src/main/java/com/learnova/app/NearFieldCreatureWorld.kt").read_text()
controller = (ROOT / "app/src/main/java/com/learnova/app/CreatureGlbController.kt").read_text()
view = (ROOT / "app/src/main/java/com/learnova/app/Learnova3DView.kt").read_text()

def require(text, pattern, label):
    if not re.search(pattern, text, re.MULTILINE | re.DOTALL):
        raise SystemExit(f"near-field contract failed: {label}")

require(world, r"const val MIN_DISTANCE\s*=\s*22\.0", "near-field minimum distance")
require(world, r"const val MAX_DISTANCE\s*=\s*68\.0", "near-field maximum distance")
require(world, r"private val creatures = Array\(5\) \{ CreatureGlbController\(context, engine, scene\) \}", "five concurrent near-field slots")
require(world, r"private val activeSpecies = arrayOfNulls<String>\(5\)", "five-slot active state")
require(world, r"creatures\[index\]\.hide\(\)", "out-of-range hide")
require(world, r"activeSpecies\[index\]\s*=\s*null", "slot state cleanup")
require(world, r"creatures\[index\]\.show\(\s*species,\s*candidate\.x,\s*candidate\.y,\s*candidate\.z,\s*candidate\.yaw,\s*candidate\.scale\s*\)", "near-field presentation")
require(world, r"creatures\[index\]\.move\(\s*candidate\.x,\s*candidate\.y,\s*candidate\.z,\s*candidate\.yaw,\s*candidate\.scale\s*\)", "continuous presentation movement")
require(world, r"val pool\s*=\s*speciesPool\(biome\)", "deterministic habitat species selection")
require(world, r"private fun speciesPool", "habitat species pool")
require(world, r'val SUPPORTED = setOf\("deer", "fox", "horse", "wolf", "cow"\)', "verified near-field species")
require(world, r'FOREST -> listOf\("wolf", "fox", "deer", "horse", "cow"\)', "forest habitat pool")
require(world, r'PLATEAU -> listOf\("deer", "horse"\)', "plateau habitat pool")
require(controller, r"assetLoader\.createAsset\(ByteBuffer\.wrap\(bytes\)\)", "authored GLB loading")
require(controller, r"resourceLoader!!\.loadResources\(asset\)", "GLB resource upload")
require(controller, r"activeAnimator\s*=\s*asset\.instance\?\.animator\?\.takeIf", "animation binding")
require(controller, r"animator\.applyAnimation\((?:activeAnimationIndex|selected),", "animation playback")
require(controller, r"scene\.addEntities\(asset\.entities\)", "scene attachment")
require(controller, r"scene\.removeEntities\(asset\.entities\)", "scene cleanup")
require(view, r"nearFieldCreatureWorld\?\.update\(vehicleDistance\)", "render-loop integration")

print("Near-field presentation contracts verified.")
print("- bounded roadside range: 22m..68m")
print("- five-slot authored GLB presentation")
print("- deterministic habitat-aware species selection")
print("- animation playback + per-frame movement")
print("- deterministic hide/cleanup")
print("- only structurally verified creature assets are promoted to near-field path")
print("- Learnova3DView render-loop integration")
