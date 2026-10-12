#!/usr/bin/env python3
"""Fail-closed checks for Learnova's first 100-vehicle launch garage."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ui = (ROOT / "app/src/main/java/com/learnova/app/LearnovaCustomizeView.kt").read_text()
activity = (ROOT / "app/src/main/java/com/learnova/app/MainActivity.kt").read_text()

def require(condition, message):
    if not condition:
        raise SystemExit(f"customization contract failed: {message}")

require("private val launchVehicles = VehicleCatalog.all.take(100)" in ui,
        "garage must expose only the 100 launch vehicle slots")
require("selectedVehicleId = id.coerceIn(1, launchVehicles.size)" in ui,
        "restored selection must clamp to the verified launch batch")
require("launchVehicles.size / pageSize.toDouble()" in ui,
        "pagination must use launch batch size")
require("index in launchVehicles.indices" in ui,
        "taps must not select unverified catalog entries")
require("onVehicleSelected(launchVehicles[selectedVehicleId - 1])" in ui,
        "apply must return a vehicle from the launch batch")
require('onEnvironmentSelected(selectedEnvironment)' in ui,
        "apply must preserve the selected environment")
require('putString("environment_preset", environment)' in activity,
        "environment selection must persist between sessions")
require('WorldDirector.selectEnvironment(prefs.getString("environment_preset", null))' in activity,
        "saved environment must be restored at startup")
require("setSelectedEnvironment(progress.getString(\"environment_preset\", \"Forest\"))" in activity,
        "customization screen must display the saved environment")
print("Customization contracts verified: 100 launch vehicles, bounded paging/selection, persisted environment.")
