package com.learnova.app

import android.content.Context
import android.util.Base64
import java.io.FileNotFoundException
import java.util.zip.GZIPInputStream
import java.util.LinkedHashMap

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
        // Authored GLBs are optional but repeatedly reused by near-field encounters.
        // Cache decoded bytes so a lesson transition does not repeatedly perform
        // AssetManager I/O + Base64 decoding on the render thread.
        private const val MAX_CACHE_BYTES = 16 * 1024 * 1024
        private val cacheLock = Any()
        private val decodedCache = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {}
        private var cachedBytes = 0

        fun assetPath(species: String): String? {
            val key = normalize(species)
            return "$ROOT/$key.glb"
        }

        fun hasAuthoredContract(species: String): Boolean = assetPath(species) != null

        private fun normalize(species: String): String = species.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }

    fun load(species: String): ByteArray? {
        val key = normalize(species)
        if (key.isBlank()) return null
        synchronized(cacheLock) {
            decodedCache[key]?.let { return it }
        }

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
                    val bytes = when {
                        path.endsWith(".gz") -> {
                            GZIPInputStream(input).use { it.readBytes() }
                        }
                        path.endsWith(".b64") -> {
                            val encoded = input.bufferedReader(Charsets.US_ASCII).use { it.readText() }
                            Base64.decode(encoded, Base64.DEFAULT)
                        }
                        else -> input.readBytes()
                    }
                    // Fail closed: only pass genuine GLB containers to Filament.
                    if (bytes.size < 20 ||
                        bytes[0] != 0x67.toByte() ||
                        bytes[1] != 0x6C.toByte() ||
                        bytes[2] != 0x54.toByte() ||
                        bytes[3] != 0x46.toByte()) {
                        continue
                    }
                    synchronized(cacheLock) {
                        if (bytes.size <= MAX_CACHE_BYTES) {
                            decodedCache.remove(key)?.let { cachedBytes -= it.size }
                            decodedCache[key] = bytes
                            cachedBytes += bytes.size
                            val iterator = decodedCache.entries.iterator()
                            while (cachedBytes > MAX_CACHE_BYTES && iterator.hasNext()) {
                                val entry = iterator.next()
                                if (entry.key == key && decodedCache.size > 1) continue
                                cachedBytes -= entry.value.size
                                iterator.remove()
                            }
                        }
                        return decodedCache[key] ?: bytes
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
