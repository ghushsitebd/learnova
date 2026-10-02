package com.learnova.app

import android.content.Context

/**
 * Runtime truth for real near-field creature assets.
 *
 * A catalog entry is not considered a real 3D creature until a valid packaged
 * GLB/GLB.GZ/GLB.B64 asset exists. This keeps the 1,000+ logical catalog
 * independent from the actual authored asset inventory.
 */
internal object CreatureAssetCoverage {
    private const val ROOT = "creatures"

    fun packagedSpecies(context: Context): Set<String> =
        context.assets.list(ROOT)
            .orEmpty()
            .asSequence()
            .mapNotNull { file ->
                when {
                    file.endsWith(".glb.b64") -> file.removeSuffix(".glb.b64")
                    file.endsWith(".glb.gz") -> file.removeSuffix(".glb.gz")
                    file.endsWith(".glb") -> file.removeSuffix(".glb")
                    else -> null
                }
            }
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

    fun isPackaged(context: Context, species: String): Boolean =
        normalize(species) in packagedSpecies(context)

    private fun normalize(species: String): String =
        species.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}
