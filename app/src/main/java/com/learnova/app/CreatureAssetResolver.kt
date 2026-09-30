package com.learnova.app

import android.content.Context
import java.io.FileNotFoundException
import java.util.zip.GZIPInputStream

/**
 * Resolves authored near-field creature assets.
 *
 * The world may contain 1,000+ catalog records, but only species that are
 * close enough to the vehicle need a real PBR/animated GLB. Missing assets
 * intentionally return null so the existing distant population renderer stays
 * safe and performant.
 *
 * Supported representations:
 *   assets/creatures/<species>.glb
 *   assets/creatures/<species>.glb.gz
 */
internal class CreatureAssetResolver(private val context: Context) {

    companion object {
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

        fun hasAuthoredContract(species: String): Boolean = assetPath(species) != null
    }

    fun load(species: String): ByteArray? {
        val key = species.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        if (key !in authoredSpecies) return null

        val candidates = listOf(
            "$ROOT/$key.glb",
            "$ROOT/$key.glb.gz"
        )
        for (path in candidates) {
            try {
                context.assets.open(path).use { input ->
                    return if (path.endsWith(".gz")) {
                        GZIPInputStream(input).use { it.readBytes() }
                    } else {
                        input.readBytes()
                    }
                }
            } catch (_: FileNotFoundException) {
                // The authored asset is not bundled yet; keep the distant fallback.
            }
        }
        return null
    }
}
