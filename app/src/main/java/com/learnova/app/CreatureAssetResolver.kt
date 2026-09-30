package com.learnova.app

/**
 * Runtime contract for authored creature assets.
 *
 * The catalog can contain 1,000+ creature records while only the near-field
 * species that are actually encountered need authored GLB assets. Keeping the
 * mapping in one resolver lets the renderer switch from the current distant
 * fallback geometry to real PBR/animated assets without changing world logic.
 *
 * Asset files are intentionally optional at this stage: a missing authored
 * asset falls back to the existing lightweight population renderer.
 */
internal object CreatureAssetResolver {
    private const val ROOT = "creatures"

    private val authoredSpecies = setOf(
        "lion",
        "tiger",
        "elephant",
        "leopard",
        "bear",
        "fox",
        "monkey",
        "deer",
        "horse",
        "camel",
        "wolf",
        "zebra",
        "giraffe",
        "penguin",
        "dolphin"
    )

    fun assetPath(species: String): String? {
        val key = species.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return if (key in authoredSpecies) "$ROOT/$key.glb" else null
    }

    fun hasAuthoredAsset(species: String): Boolean = assetPath(species) != null
}
