package com.learnova.app

import android.content.Context
import java.io.FileNotFoundException
import java.util.zip.GZIPInputStream

/**
 * Resolves a garage vehicle into renderable GLB bytes without loading the whole
 * garage into memory. Dedicated vehicle assets are optional: missing assets fall
 * back to a verified real vehicle class model so selection never crashes the game
 * or collapses every unprovisioned slot onto one generic mesh.
 *
 * Supported asset paths:
 *   assets/vehicles/<assetKey>.glb
 *   assets/vehicles/<assetKey>.glb.gz
 */
internal class VehicleAssetResolver(private val context: Context) {

    fun load(assetKey: String): ByteArray? {
        // Prefer the dedicated model. For the remaining 500-slot catalog, use
        // a deterministic class fallback among the verified real GLBs already
        // shipped with the app. This keeps every selection renderable while the
        // larger authored-asset library is expanded incrementally.
        val fallbackKey = when {
            assetKey.contains("truck") ||
                assetKey.contains("bus") ||
                assetKey.contains("construction") ||
                assetKey.contains("emergency") ||
                assetKey.contains("commercial") ||
                assetKey.contains("service") ->
                "vehicle_040_box_truck"

            assetKey.contains("motorcycle") ||
                assetKey.contains("cycle") ||
                assetKey.contains("buggy") ||
                assetKey.contains("offroad") ||
                assetKey.contains("three_wheeler") ->
                "vehicle_068_buggy"

            else -> "vehicle_001_city_car"
        }

        val keys = listOf(assetKey, fallbackKey).distinct()
        val candidates = keys.flatMap { key ->
            listOf(
                "vehicles/$key.glb",
                "vehicles/$key.glb.gz"
            )
        }

        for (path in candidates) {
            try {
                context.assets.open(path).use { input ->
                    val bytes = if (path.endsWith(".gz")) {
                        GZIPInputStream(input).use { it.readBytes() }
                    } else {
                        input.readBytes()
                    }
                    // A GLB must begin with the binary glTF magic "glTF".
                    // Reject corrupt/placeholder payloads before they reach Filament.
                    if (bytes.size < 20 ||
                        bytes[0] != 0x67.toByte() ||
                        bytes[1] != 0x6C.toByte() ||
                        bytes[2] != 0x54.toByte() ||
                        bytes[3] != 0x46.toByte()) {
                        continue
                    }
                    return bytes
                }
            } catch (_: FileNotFoundException) {
                // Try the next representation.
            } catch (_: java.io.IOException) {
                // Treat a damaged compressed asset as unavailable; never crash world startup.
            }
        }
        return null
    }
}
