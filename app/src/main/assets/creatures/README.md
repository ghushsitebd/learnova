# Learnova creature assets

Release CI fetches these verified animated GLB files into
`app/src/main/assets/creatures/` from pinned GitHub-hosted sources.

The runtime does **not** assume that the logical wildlife catalog has a binary
asset. Only species that are both supported by the near-field controller and
present in the packaged asset inventory are promoted to the authored 3D
near-field path.

| File | Animal | Asset pipeline |
|---|---|---|
| `deer.glb` | Deer | pinned CC0/Quaternius source |
| `fox.glb` | Fox | pinned CC0/Quaternius source |
| `horse.glb` | Horse | pinned CC0/Quaternius source |
| `wolf.glb` | Wolf | pinned CC0/Quaternius source |
| `cow.glb` | Cow | pinned GLB source |
| `boar.glb` | Boar | pinned CC0/Quaternius source |
| `rabbit.glb` | Rabbit | pinned CC0/Quaternius source |
| `stag.glb` | Stag | pinned CC0/Quaternius source |

The fetch/validation pipeline verifies GLB v2 structure, meshes, skins and
non-zero skeletal animation tracks before the assets are accepted by CI.
