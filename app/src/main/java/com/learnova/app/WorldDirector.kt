package com.learnova.app

/**
 * Lightweight world-direction layer for Learnova.
 *
 * It changes the visual character of the journey without loading a large library
 * of environment models. Distances are deterministic, so the same lesson route
 * produces the same biome sequence on every device.
 */
internal object WorldDirector {

    enum class Biome {
        FOREST, RIVER, MOUNTAIN, DESERT, PLATEAU, MARKET, VILLAGE, COAST
    }

    data class Profile(
        val biome: Biome,
        val cameraHeight: Double,
        val lookAheadLift: Double,
        val exposure: Float,
        val fovBias: Double,
        // Linear-ish skybox colors chosen per biome so the distant world does not
        // look like one repeated test scene. The renderer interpolates only at
        // chapter boundaries, avoiding per-frame material allocation.
        val skyR: Float,
        val skyG: Float,
        val skyB: Float,
        val skyA: Float = 1.0f
    )

    private const val CHAPTER_LENGTH = 96.0

    fun profile(distance: Double): Profile {
        val chapter = kotlin.math.floor(kotlin.math.max(0.0, distance) / CHAPTER_LENGTH).toInt()
        return when (chapter % 8) {
            0 -> Profile(Biome.FOREST, 2.82, 1.02, 14.0f, 0.0, 0.29f, 0.55f, 0.82f)
            1 -> Profile(Biome.RIVER, 2.96, 1.06, 14.1f, 0.8, 0.24f, 0.60f, 0.86f)
            2 -> Profile(Biome.MOUNTAIN, 3.12, 1.10, 13.8f, -0.4, 0.31f, 0.43f, 0.64f)
            3 -> Profile(Biome.DESERT, 2.86, 1.02, 14.3f, 0.6, 0.72f, 0.48f, 0.30f)
            4 -> Profile(Biome.PLATEAU, 3.00, 1.08, 14.0f, -0.2, 0.34f, 0.52f, 0.73f)
            5 -> Profile(Biome.MARKET, 2.78, 1.00, 14.2f, 1.0, 0.38f, 0.57f, 0.78f)
            6 -> Profile(Biome.VILLAGE, 2.84, 1.02, 14.1f, 0.4, 0.30f, 0.58f, 0.79f)
            else -> Profile(Biome.COAST, 2.92, 1.05, 14.2f, 1.2, 0.25f, 0.62f, 0.88f)
        }
    }
}
