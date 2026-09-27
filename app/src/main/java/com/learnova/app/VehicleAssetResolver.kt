package com.learnova.app

import android.content.Context
import java.io.FileNotFoundException
import java.util.zip.GZIPInputStream

/**
 * Resolves a garage vehicle into renderable GLB bytes without loading the whole
 * garage into memory. Real vehicle assets are optional: missing assets fall back
 * to Learnova's verified base vehicle so selection never crashes the game.
 *
 * Supported asset paths:
 *   assets/vehicles/<assetKey>.glb
 *   assets/vehicles/<assetKey>.glb.gz
 */
internal class VehicleAssetResolver(private val context: Context) {

    fun load(assetKey: String): ByteArray? {
        val candidates = listOf(
            "vehicles/$assetKey.glb",
            "vehicles/$assetKey.glb.gz"
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
                // Try the next representation.
            }
        }
        return null
    }
}
