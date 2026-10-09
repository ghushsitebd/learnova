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

    fun load(definition: VehicleDefinition): ByteArray? =
        load(definition.assetKey, definition.type)

    fun load(assetKey: String): ByteArray? {
        val definition = VehicleCatalog.all.firstOrNull { it.assetKey == assetKey }
        return load(assetKey, definition?.type.orEmpty())
    }

    private fun load(assetKey: String, vehicleType: String): ByteArray? {
        // Prefer the dedicated model. For the remaining 500-slot catalog, use
        // a deterministic class fallback among the verified real GLBs already
        // shipped with the app. This keeps every selection renderable while the
        // larger authored-asset library is expanded incrementally.
        val fallbackKey = VehicleFallbackSelector.fallbackKey(vehicleType, assetKey)

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


/**
 * Chooses the closest available verified class model for an unprovisioned slot.
 * This is deliberately explicit: a 500-entry catalog is not 500 unique GLBs.
 */
internal object VehicleFallbackSelector {
    fun fallbackKey(vehicleType: String, assetKey: String): String {
        val type = vehicleType.lowercase()
        val key = assetKey.lowercase()

        return when {
            key.contains("scooter") || key.contains("motorcycle") ||
                key.contains("bicycle") || key.contains("tricycle") ||
                type in setOf("motorcycle", "cycle", "three_wheeler") ->
                "vehicle_068_buggy"

            type in setOf("truck", "bus", "construction", "emergency", "commercial", "airport", "farm", "van") ||
                key.contains("truck") || key.contains("bus") ||
                key.contains("construction") || key.contains("ambulance") ||
                key.contains("tractor") || key.contains("farm") ->
                "vehicle_040_box_truck"

            type in setOf("offroad", "suv", "pickup", "safari") ||
                key.contains("buggy") || key.contains("offroad") ||
                key.contains("suv") || key.contains("pickup") ||
                key.contains("safari") || key.contains("dune") || key.contains("jeep") ->
                "vehicle_068_buggy"

            type in setOf("concept", "luxury", "sport") ||
                key.contains("concept") || key.contains("2050") ||
                key.contains("electric") || key.contains("hydrogen") ||
                key.contains("sport") || key.contains("luxury") ||
                key.contains("hypercar") || key.contains("supercar") ||
                key.contains("roadster") || key.contains("coupe") ->
                "vehicle_100_2050_vision"

            else -> "vehicle_001_city_car"
        }
    }
}
