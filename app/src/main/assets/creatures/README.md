# Learnova creature assets

Release CI fetches these verified animated GLB files into
`app/src/main/assets/creatures/` from the GitHub-hosted sources defined by the CI fetch script.

The runtime does **not** assume that the logical wildlife catalog has a binary
asset. Only species that are both supported by the near-field controller and
present in the packaged asset inventory are promoted to the authored 3D
near-field path.

| File | Animal | Asset pipeline |
|---|---|---|
| `deer.glb` | Deer | CI-defined source |
| `fox.glb` | Fox | CI-defined source |
| `horse.glb` | Horse | CI-defined source |
| `wolf.glb` | Wolf | CI-defined source |
| `cow.glb` | Cow | CI-defined source |
| `boar.glb` | Boar | CI-defined source |
| `rabbit.glb` | Rabbit | CI-defined source |
| `stag.glb` | Stag | CI-defined source |
| `alpaca.glb` | Alpaca | Pinned Quaternius animated-animal pack |
| `bull.glb` | Bull | Pinned Quaternius animated-animal pack |
| `donkey.glb` | Donkey | Pinned Quaternius animated-animal pack |
| `husky.glb` | Husky | Pinned Quaternius animated-animal pack |
| `shiba_inu.glb` | Shiba Inu | Pinned Quaternius animated-animal pack |
| `white_horse.glb` | White horse | Pinned Quaternius animated-animal pack |

The fetch/validation pipeline verifies GLB v2 structure, meshes, skins and
non-zero skeletal animation tracks before the assets are accepted by CI. The
additional animal sources are pinned to commit `f50f3ee5b04c1a34f8c30a338db51003aca1b8b0`
so the downloaded content does not drift with upstream changes.
