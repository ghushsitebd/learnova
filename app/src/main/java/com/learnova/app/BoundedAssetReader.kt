package com.learnova.app

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads an asset with a hard byte limit, including streams that expand during
 * decompression. The limit is enforced before bytes are appended to the output.
 */
internal object BoundedAssetReader {
    fun read(input: InputStream, maxBytes: Int): ByteArray {
        require(maxBytes > 0) { "maxBytes must be positive" }
        val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (count > maxBytes - total) {
                throw IOException("Asset exceeds the " + maxBytes + "-byte safety limit")
            }
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray()
    }
}
