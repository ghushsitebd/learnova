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

The fetch/validation pipeline verifies GLB v2 structure, meshes, skins and
non-zero skeletal animation tracks before the assets are accepted by CI.
