package com.learnova.app

/**
 * Runtime rendering policy for a vehicle asset.
 *
 * The policy is deliberately data-driven: the same renderer can handle
 * lightweight and high-detail vehicles without changing gameplay code.
 */
internal data class VehicleRenderProfile(
    val lod0Distance: Float = 22f,
    val lod1Distance: Float = 55f,
    val lod2Distance: Float = 110f,
    val shadowDistance: Float = 65f,
    val enableWheelAnimation: Boolean = true,
    val enableBodyAnimation: Boolean = false
) {
    init {
        require(lod0Distance > 0f)
        require(lod1Distance > lod0Distance)
        require(lod2Distance > lod1Distance)
        require(shadowDistance > 0f)
    }

    companion object {
        fun forType(type: String): VehicleRenderProfile = when (type) {
            "motorcycle", "cycle", "three_wheeler" ->
                VehicleRenderProfile(
                    lod0Distance = 18f,
                    lod1Distance = 45f,
                    lod2Distance = 90f,
                    shadowDistance = 55f
                )

            "truck", "bus", "emergency", "construction" ->
                VehicleRenderProfile(
                    lod0Distance = 26f,
                    lod1Distance = 70f,
                    lod2Distance = 135f,
                    shadowDistance = 80f
                )

            "concept" ->
                VehicleRenderProfile(
                    lod0Distance = 24f,
                    lod1Distance = 60f,
                    lod2Distance = 120f,
                    shadowDistance = 70f
                )

            else -> VehicleRenderProfile()
        }
    }
}
