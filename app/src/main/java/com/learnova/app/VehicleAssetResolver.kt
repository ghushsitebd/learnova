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
            // Heavy vehicles keep a distinct silhouette from passenger cars.
            assetKey.contains("truck") ||
                assetKey.contains("bus") ||
                assetKey.contains("construction") ||
                assetKey.contains("emergency") ||
                assetKey.contains("commercial") ||
                assetKey.contains("service") ||
                assetKey.contains("airport") ||
                assetKey.contains("farm") ||
                assetKey.contains("tractor") ->
                "vehicle_040_box_truck"

            // Small, rugged and two-wheel vehicle families use the compact buggy
            // silhouette rather than incorrectly displaying a sedan.
            assetKey.contains("motorcycle") ||
                assetKey.contains("cycle") ||
                assetKey.contains("buggy") ||
                assetKey.contains("offroad") ||
                assetKey.contains("three_wheeler") ||
                assetKey.contains("suv") ||
                assetKey.contains("pickup") ||
                assetKey.contains("safari") ||
                assetKey.contains("dune") ||
                assetKey.contains("jeep") ->
                "vehicle_068_buggy"

            // Futuristic, electric, luxury and performance cars use the authored
            // CarConcept GLB; this is a class fallback, not a unique model per slot.
            assetKey.contains("concept") ||
                assetKey.contains("2050") ||
                assetKey.contains("electric") ||
                assetKey.contains("hydrogen") ||
                assetKey.contains("sport") ||
                assetKey.contains("luxury") ||
                assetKey.contains("hypercar") ||
                assetKey.contains("supercar") ||
                assetKey.contains("roadster") ||
                assetKey.contains("coupe") ->
                "vehicle_100_2050_vision"

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
