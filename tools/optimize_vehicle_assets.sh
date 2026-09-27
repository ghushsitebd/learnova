#!/usr/bin/env bash
set -euo pipefail

# Learnova vehicle asset pipeline.
# Input:  raw vehicle GLB files in assets/vehicles/raw/
# Output: runtime GLBs in assets/vehicles/
# -kn is intentional: wheel/vehicle named nodes must survive optimization.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAW="$ROOT/app/src/main/assets/vehicles/raw"
OUT="$ROOT/app/src/main/assets/vehicles"
GLTFPACK="${GLTFPACK_BIN:-gltfpack}"

if ! command -v "$GLTFPACK" >/dev/null 2>&1; then
  echo "gltfpack is required. Set GLTFPACK_BIN or install gltfpack first." >&2
  exit 2
fi

mkdir -p "$OUT"

shopt -s nullglob
inputs=( "$RAW"/*.glb )
if (${#inputs[@]} == 0); then
  echo "No raw vehicle GLBs found in $RAW; nothing to optimize."
  exit 0
fi

for input in "${inputs[@]}"; do
  key="$(basename "$input" .glb)"
  output="$OUT/$key.glb"
  tmp="$OUT/.$key.optimized.glb"

  "$GLTFPACK" -i "$input" -o "$tmp" -cc -tc -kn

  test -s "$tmp"
  mv "$tmp" "$output"
  echo "Optimized: $key"
done
