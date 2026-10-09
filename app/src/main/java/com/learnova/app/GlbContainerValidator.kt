package com.learnova.app

/**
 * Defensive, allocation-free validation of a binary glTF 2.0 container.
 *
 * This is a runtime guard, not a replacement for the release-time full asset
 * audit: it rejects truncated or malformed container boundaries before native
 * Filament code sees the bytes.
 */
internal object GlbContainerValidator {
    private const val HEADER_SIZE = 12
    private const val CHUNK_HEADER_SIZE = 8
    private const val GLB_VERSION_2 = 2
    private const val JSON_CHUNK_TYPE = 0x4E4F534A

    fun isValid(bytes: ByteArray): Boolean {
        if (bytes.size < HEADER_SIZE + CHUNK_HEADER_SIZE) return false
        if (bytes[0] != 0x67.toByte() ||
            bytes[1] != 0x6C.toByte() ||
            bytes[2] != 0x54.toByte() ||
            bytes[3] != 0x46.toByte()) return false

        val version = uint32(bytes, 4) ?: return false
        val declaredLength = uint32(bytes, 8) ?: return false
        if (version != GLB_VERSION_2.toLong()) return false
        if (declaredLength != bytes.size.toLong()) return false

        var offset = HEADER_SIZE.toLong()
        var chunkIndex = 0
        while (offset < declaredLength) {
            if (declaredLength - offset < CHUNK_HEADER_SIZE) return false

            val headerOffset = offset.toInt()
            val chunkLength = uint32(bytes, headerOffset) ?: return false
            val chunkType = uint32(bytes, headerOffset + 4) ?: return false

            if (chunkLength == 0L || chunkLength % 4L != 0L) return false
            if (chunkIndex == 0 && chunkType != JSON_CHUNK_TYPE.toLong()) return false

            val chunkEnd = offset + CHUNK_HEADER_SIZE.toLong() + chunkLength
            if (chunkEnd < offset || chunkEnd > declaredLength) return false
            offset = chunkEnd
            chunkIndex++
        }

        return offset == declaredLength && chunkIndex > 0
    }

    private fun uint32(bytes: ByteArray, offset: Int): Long? {
        if (offset < 0 || offset.toLong() + 4L > bytes.size.toLong()) return null
        return (bytes[offset].toLong() and 0xFFL) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFFL) shl 24)
    }
}
