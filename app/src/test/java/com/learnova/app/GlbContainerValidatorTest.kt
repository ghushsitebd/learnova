package com.learnova.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlbContainerValidatorTest {
    @Test fun acceptsMinimalWellFormedGlbV2() {
        assertTrue(GlbContainerValidator.isValid(minimalGlb()))
    }

    @Test fun rejectsNonGlbMagic() {
        val bytes = minimalGlb()
        bytes[0] = 0
        assertFalse(GlbContainerValidator.isValid(bytes))
    }

    @Test fun rejectsUnsupportedVersion() {
        val bytes = minimalGlb()
        putUInt32(bytes, 4, 1)
        assertFalse(GlbContainerValidator.isValid(bytes))
    }

    @Test fun rejectsDeclaredLengthMismatch() {
        val bytes = minimalGlb()
        putUInt32(bytes, 8, bytes.size + 4)
        assertFalse(GlbContainerValidator.isValid(bytes))
    }

    @Test fun rejectsTruncatedChunkHeader() {
        assertFalse(GlbContainerValidator.isValid(ByteArray(19)))
    }

    @Test fun rejectsChunkThatExtendsPastContainer() {
        val bytes = minimalGlb()
        putUInt32(bytes, 12, 8)
        assertFalse(GlbContainerValidator.isValid(bytes))
    }

    @Test fun rejectsNonAlignedChunkLength() {
        val bytes = minimalGlb()
        putUInt32(bytes, 12, 3)
        assertFalse(GlbContainerValidator.isValid(bytes))
    }

    @Test fun requiresJsonAsFirstChunk() {
        val bytes = minimalGlb()
        putUInt32(bytes, 16, 0x004E4942)
        assertFalse(GlbContainerValidator.isValid(bytes))
    }

    private fun minimalGlb(): ByteArray {
        // 12-byte GLB header + 8-byte JSON chunk header + "{}  " payload.
        val bytes = ByteArray(24)
        bytes[0] = 'g'.code.toByte()
        bytes[1] = 'l'.code.toByte()
        bytes[2] = 'T'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        putUInt32(bytes, 4, 2)
        putUInt32(bytes, 8, bytes.size)
        putUInt32(bytes, 12, 4)
        putUInt32(bytes, 16, 0x4E4F534A)
        bytes[20] = '{'.code.toByte()
        bytes[21] = '}'.code.toByte()
        bytes[22] = ' '.code.toByte()
        bytes[23] = ' '.code.toByte()
        return bytes
    }

    private fun putUInt32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
