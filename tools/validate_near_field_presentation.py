#!/usr/bin/env python3
"""Deterministic source-level contract checks for the near-field creature presentation layer."""
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
require(world, r"const val MAX_DISTANCE\s*=\s*58\.0", "near-field maximum distance")
require(world, r"if \(candidate == null\).*?creature\.hide\(\).*?activeSpecies = null", "out-of-range hide")
require(world, r"creature\.show\(species, candidate\.x, candidate\.y, candidate\.z, candidate\.yaw, candidate\.scale\)", "first presentation")
require(world, r"creature\.move\(candidate\.x, candidate\.y, candidate\.z, candidate\.yaw, candidate\.scale\)", "continuous presentation movement")
require(controller, r"assetLoader\.createAsset\(ByteBuffer\.wrap\(bytes\)\)", "authored GLB loading")
require(controller, r"resourceLoader!!\.loadResources\(asset\)", "GLB resource upload")
require(controller, r"activeAnimator\s*=\s*asset\.instance\?\.animator\?\.takeIf", "animation binding")
require(controller, r"animator\.applyAnimation\((?:activeAnimationIndex|selected),", "animation playback")
require(controller, r"scene\.addEntities\(asset\.entities\)", "scene attachment")
require(controller, r"scene\.removeEntities\(asset\.entities\)", "scene cleanup")
require(view, r"nearFieldCreatureWorld\?\.update\(vehicleDistance\)", "render-loop integration")

print("Near-field presentation contracts verified.")
print("- bounded roadside range: 22m..58m")
print("- authored GLB load + resource upload")
print("- animation playback + per-frame movement")
print("- deterministic hide/cleanup")
print("- Learnova3DView render-loop integration")
