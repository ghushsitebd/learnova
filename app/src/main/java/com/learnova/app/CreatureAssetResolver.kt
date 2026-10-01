package com.learnova.app

import android.content.Context
import android.util.Base64
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
 *   assets/creatures/<species>.glb.b64
 *
 * The .b64 form is used for large binary assets that are committed as text so
 * the repository remains portable across the release pipeline.
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
            val key = normalize(species)
            return if (key in authoredSpecies) "$ROOT/$key.glb" else null
        }

        fun hasAuthoredContract(species: String): Boolean = assetPath(species) != null

        private fun normalize(species: String): String = species.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    fun load(species: String): ByteArray? {
        val key = normalize(species)
        if (key !in authoredSpecies) return null

        // Prefer binary assets, then compressed binary, then repository-safe
        // base64 assets. This makes the runtime compatible with both the current
        // lion.glb.b64 packaging and future native GLB files.
        val candidates = listOf(
            "$ROOT/$key.glb",
            "$ROOT/$key.glb.gz",
            "$ROOT/$key.glb.b64"
        )

        for (path in candidates) {
            try {
                context.assets.open(path).use { input ->
                    return when {
                        path.endsWith(".gz") -> {
                            GZIPInputStream(input).use { it.readBytes() }
                        }
                        path.endsWith(".b64") -> {
                            val encoded = input.bufferedReader(Charsets.US_ASCII).use { it.readText() }
                            Base64.decode(encoded, Base64.DEFAULT)
                        }
                        else -> input.readBytes()
                    }
                }
            } catch (_: FileNotFoundException) {
                // Try the next representation; missing authored assets must not
                // break world startup or distant-life rendering.
            } catch (_: IllegalArgumentException) {
                // Invalid base64 is treated like a missing optional asset.
            }
        }
        return null
    }
}
