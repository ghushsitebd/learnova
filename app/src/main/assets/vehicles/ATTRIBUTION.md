# Vehicle asset provenance

- `vehicle_001_city_car.glb`: ToyCar from KhronosGroup glTF-Sample-Assets.
  License: CC0 1.0 Universal.
  Source: https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/ToyCar

- `vehicle_040_box_truck.glb`: CesiumMilkTruck from KhronosGroup glTF-Sample-Assets.
  License: CC-BY-4.0 with Cesium trademark/legal-mark notice.
  Source: https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/CesiumMilkTruck

These are real authored GLB assets used as the first production asset lane.
The remaining garage catalog entries retain the verified renderer fallback until
dedicated assets are supplied.

## Scale-aware prototype batch (garage slots 005–099)

The CI build runs `tools/generate_vehicle_prototypes.py` after fetching the four upstream GLBs. It creates original, low-poly GLB prototypes for any missing slot among the first 100 garage entries. Prototype dimensions are class-based estimates in meters and vary deterministically by entry; they are distinct geometry, not photorealistic or exact branded vehicle replicas. These prototypes are generated during CI and packaged in the APK/AAB; generated binaries are not committed to the repository.

The generator code is original Learnova project code. Upstream assets remain separately attributed above. The first-100 coverage gate validates that each catalog entry has a GLB file and that each file passes GLB container checks. This is an initial playable vehicle-model tranche, not completion of all 500 dedicated models.
