package com.learnova.app

import android.content.Context
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

/**
 * Resolves a garage vehicle into renderable GLB bytes without loading the whole
 * garage into memory. Dedicated vehicle assets are optional: missing assets fall
 * back to a verified real vehicle class model so selection never crashes the game.
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
        val fallbackKey = VehicleFallbackSelector.fallbackKey(vehicleType, assetKey)
        val candidates = listOf(assetKey, fallbackKey).distinct().flatMap { key ->
            listOf("vehicles/$key.glb", "vehicles/$key.glb.gz")
        }

        for (path in candidates) {
            try {
                context.assets.open(path).use { input ->
                    val bytes = if (path.endsWith(".gz")) {
                        GZIPInputStream(input).use { BoundedAssetReader.read(it, MAX_ASSET_BYTES) }
                    } else {
                        BoundedAssetReader.read(input, MAX_ASSET_BYTES)
                    }
                    // Reject malformed containers before passing bytes to native Filament.
                    if (isValidGlb(bytes)) return bytes
                }
            } catch (_: FileNotFoundException) {
                // Try the next representation.
            } catch (_: IOException) {
                // Treat a damaged or truncated asset as unavailable; never crash world startup.
            } catch (_: SecurityException) {
                // A blocked asset is equivalent to an unavailable asset.
            }
        }
        return null
    }

    /**
     * Validates the GLB v2 container envelope and chunk boundaries.
     * This does not replace CI's deeper JSON/mesh/texture checks, but prevents
     * truncated or incorrectly-sized payloads from reaching the native renderer.
     */
    internal fun isValidGlb(bytes: ByteArray): Boolean {
        if (bytes.size < GLB_HEADER_SIZE + GLB_CHUNK_HEADER_SIZE) return false
        if (bytes[0] != 'g'.code.toByte() ||
            bytes[1] != 'l'.code.toByte() ||
            bytes[2] != 'T'.code.toByte() ||
            bytes[3] != 'F'.code.toByte()) return false

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val version = buffer.getInt(4)
        val declaredLength = buffer.getInt(8)
        if (version != GLB_VERSION || declaredLength != bytes.size) return false

        var offset = GLB_HEADER_SIZE
        var chunkIndex = 0
        while (offset < declaredLength) {
            if (declaredLength - offset < GLB_CHUNK_HEADER_SIZE) return false
            val chunkLength = buffer.getInt(offset)
            val chunkType = buffer.getInt(offset + 4)
            if (chunkLength <= 0 || chunkLength % 4 != 0) return false
            val dataStart = offset.toLong() + GLB_CHUNK_HEADER_SIZE
            val dataEnd = dataStart + chunkLength
            if (dataEnd > declaredLength.toLong()) return false
            if (chunkIndex == 0 && chunkType != GLB_JSON_CHUNK) return false
            offset = dataEnd.toInt()
            chunkIndex++
        }
        return offset == declaredLength && chunkIndex > 0
    }

    private companion object {
        const val GLB_HEADER_SIZE = 12
        const val GLB_CHUNK_HEADER_SIZE = 8
        const val MAX_ASSET_BYTES = 64 * 1024 * 1024
        const val GLB_VERSION = 2
        const val GLB_JSON_CHUNK = 0x4E4F534A
    }
}

/**
 * Chooses the closest available verified class model for an unprovisioned slot.
 * A 500-entry catalog is not 500 unique GLB models.
 */
internal object VehicleFallbackSelector {
    fun fallbackKey(vehicleType: String, assetKey: String): String {
        val type = vehicleType.lowercase()
        val key = assetKey.lowercase()

        // Prefer a same-class model when a 101–500 slot has no dedicated GLB.
        // This keeps the garage selection useful instead of showing a buggy for
        // every bike or a box truck for buses, farm machinery, and vans.
        return when {
            type == "dinosaur_mount" || key.contains("dinosaur") || key.contains("trex") ||
                key.contains("triceratops") || key.contains("stegosaurus") ||
                key.contains("parasaurolophus") ->
                "vehicle_097_trex_mount"

            type == "cycle" || (type != "three_wheeler" && (key.contains("bicycle") || key.contains("tricycle"))) ->
                if (key.contains("tricycle")) "vehicle_058_tricycle" else "vehicle_059_bicycle"

            type == "motorcycle" || key.contains("motorcycle") ||
                key.contains("scooter") || key.contains("bike") ->
                if (key.contains("scooter")) "vehicle_052_scooter" else "vehicle_051_motorcycle"

            type == "three_wheeler" || key.contains("rickshaw") ->
                "vehicle_061_auto_rickshaw"

            // Keep dedicated airport and emergency classes ahead of broad
            // substring matches such as "van" or "truck" in their asset keys.
            type == "airport" || key.contains("airport") ->
                "vehicle_082_airport_shuttle"

            type == "emergency" || key.contains("ambulance") || key.contains("fire") ||
                key.contains("rescue") || key.contains("police") ->
                when {
                    key.contains("ambulance") -> "vehicle_035_ambulance"
                    key.contains("fire") -> "vehicle_044_fire_truck"
                    else -> "vehicle_033_police"
                }

            type == "bus" || key.contains("bus") || key.contains("shuttle") ->
                if (key.contains("school")) "vehicle_048_school_bus" else "vehicle_046_city_bus"

            type == "farm" || (type != "truck" && (key.contains("tractor") || key.contains("farm"))) ->
                "vehicle_077_small_tractor"

            type == "construction" || (type != "truck" && (key.contains("loader") || key.contains("hauler"))) ->
                "vehicle_078_loader"

            type == "truck" || key.contains("truck") ->
                "vehicle_040_box_truck"

            type == "van" || type == "commercial" || key.contains("van") ->
                "vehicle_026_minivan"

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
                "vehicle_091_aero_taxi"

            else -> "vehicle_001_city_car"
        }
    }
}
