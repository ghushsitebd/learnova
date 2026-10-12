#!/usr/bin/env python3
"""Static regression audit for Learnova's streamed 3D world systems.

This does not replace device screenshots or performance measurements. It guards
core world-system contracts so a refactor cannot silently remove biomes,
long-view terrain, village/market geometry, water, or held-drive physics.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/learnova/app"

FILES = {
    "director": APP / "WorldDirector.kt",
    "terrain": APP / "LearnovaTerrainMesh.kt",
    "village_market": APP / "VillageMarketWorld.kt",
    "water": APP / "WaterSurfaceWorld.kt",
    "shoreline": APP / "ShorelineWorld.kt",
    "road": APP / "RoadSpline.kt",
    "drive": APP / "VehicleDriveDynamics.kt",
    "creatures": APP / "NearFieldCreatureWorld.kt",
    "creature_controller": APP / "CreatureGlbController.kt",
    "railway": APP / "DistantRailwayWorld.kt",
}

errors = []
source = {}
for key, path in FILES.items():
    if not path.is_file():
        errors.append(f"Missing world system source: {path.relative_to(ROOT)}")
        continue
    source[key] = path.read_text(encoding="utf-8")

def require(key, pattern, explanation, flags=0):
    text = source.get(key, "")
    if not re.search(pattern, text, flags):
        errors.append(f"{key}: {explanation}")

# Every authored chapter biome must remain available to deterministic world selection.
biomes = ("FOREST", "RIVER", "MOUNTAIN", "DESERT", "PLATEAU", "MARKET", "VILLAGE", "COAST")
for biome in biomes:
    require("director", rf"\b{biome}\b", f"biome {biome} missing")

require("director", r"CHAPTER_LENGTH\s*=\s*96\.0", "chapter length changed without updating the world contract")
require("director", r"fun atmosphere\(distance:\s*Double\)", "continuous atmosphere transition missing")
require("terrain", r"FAR_OUTER\s*=\s*320\.0", "far-horizon terrain must extend to 320 metres")
require("terrain", r"FAR_SAMPLE_COUNT\s*=\s*121", "far-horizon LOD sampling contract missing")
require("terrain", r"fun updateFarHorizon\(", "far-horizon terrain update missing")
require("terrain", r"WorldDirector\.Biome\.MOUNTAIN", "biome-shaped distant terrain missing")
require("village_market", r"MAX_BOXES\s*=\s*420", "bounded village/market geometry budget missing")
require("village_market", r"WorldDirector\.Biome\.MARKET", "market stall generation missing")
require("village_market", r"WorldDirector\.Biome\.VILLAGE", "village house generation missing")
require("water", r"class WaterSurfaceWorld", "water surface renderer missing")
require("shoreline", r"class ShorelineWorld", "shoreline renderer missing")
require("road", r"fun sampleRelative\(", "road-relative world anchoring missing")
require("drive", r"requestedMotion:\s*Boolean", "held-drive motion authority missing")
require("drive", r"profile\.coastDeceleration", "release-to-coast slowdown missing")
require("drive", r"automaticRoadTargetSpeed", "automatic curve-aware speed control missing")
require("creatures", r"Array\(8\)\s*\{\s*CreatureGlbController", "bounded eight-slot near-field creature pool missing")
require("creatures", r"it in packagedSpecies", "unpackaged animals must not be promoted to authored GLB presentation")
require("creatures", r"val travelYaw = if \(travelSign >= 0\.0\).*?val roadsideYaw = if \(side > 0\.0\).*?val facing = sample\.yaw \+ travelYaw \+ roadsideYaw", "wildlife heading must apply roadside orientation in both travel directions", flags=re.S)
require("creature_controller", r"runCatching \{ resourceLoader\?\.destroy\(\) \}\s*resourceLoader = null", "idle creature slots must release GLB resource-loader memory", flags=re.S)
require("creature_controller", r"private val transformMatrix = FloatArray\(16\)", "reusable transform matrix must avoid per-update allocations")
require("creature_controller", r"engine\.transformManager\.setTransform\(transform, matrix\)", "reused creature transform must be applied")
require("creature_controller", r"fun animate\(\)", "creature skeletal animation must have a render-cadence update path")
require("creatures", r"creatures\[index\]\.animate\(\).*?if \(now - lastUpdateNanos < UPDATE_NANOS\) return", "animate wildlife every render update while throttling world placement", flags=re.S)
require("railway", r"class DistantRailwayWorld", "distant railway world layer missing")

if errors:
    for error in errors:
        print(f"::error::{error}")
    sys.exit(1)

print("Learnova 3D world-system contracts passed:")
print("- 8 deterministic biomes and blended atmosphere")
print("- 320 m low-LOD horizon and bounded village/market geometry")
print("- road-anchored water/shoreline and automatic held-drive dynamics")
print("- packaged-asset-gated wildlife and distant railway layer")
print("Note: static contracts do not prove visual quality, device FPS, or every asset's uniqueness.")
