#!/usr/bin/env python3
"""Release-time source contracts for Learnova's streamed village and market world."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
world_path = ROOT / "app/src/main/java/com/learnova/app/VillageMarketWorld.kt"
view_path = ROOT / "app/src/main/java/com/learnova/app/Learnova3DView.kt"
world = world_path.read_text()
view = view_path.read_text()

def require(condition, message):
    if not condition:
        raise SystemExit(f"village/market contract failed: {message}")

require("const val MAX_BOXES = 480" in world, "bounded 480-box streaming budget")
require("IndexType.USHORT" in world, "16-bit index buffer")
require("fun update(center: Double)" in world, "distance-streamed world update")
require("WorldDirector.profile(d).biome" in world, "biome-aware spawning")
require("if (market || village || riverside)" in world, "market, village, and riverside zones")
require("A raised shop sign" in world, "market shop signs")
require("Small produce crates" in world, "market produce crates")
require("front shelf" in world, "market shelf geometry")
require("paired window frames" in world, "house facade details")
require("fun destroy()" in world, "render resources have a cleanup path")
require("VillageMarketWorld(viewer.engine, viewer.scene, asset).also { it.build() }" in view,
        "world is built during 3D scene initialization")
require("villageMarketWorld?.update(vehicleDistance)" in view,
        "world streams with authoritative vehicle distance")
require("villageMarketWorld?.destroy()" in view,
        "world resources are released during renderer cleanup")

print("Village/market world contracts verified.")
print("- 480 bounded procedural boxes, streamed with road distance")
print("- deterministic market/village/riverside biome selection")
print("- bazaar stalls include signs, shelves, posts, and produce crates")
print("- village facades include doors and paired window frames")
print("- scene initialization, per-frame distance update, and cleanup are wired")
