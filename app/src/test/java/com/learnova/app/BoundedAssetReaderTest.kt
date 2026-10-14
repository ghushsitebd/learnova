package com.learnova.app

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BoundedAssetReaderTest {
    @Test
    fun readsAssetsExactlyAtConfiguredLimit() {
        val expected = ByteArray(8192) { (it % 251).toByte() }
        val actual = BoundedAssetReader.read(ByteArrayInputStream(expected), expected.size)
        assertArrayEquals(expected, actual)
    }

    @Test
    fun rejectsAssetOneByteOverConfiguredLimit() {
        val input = ByteArrayInputStream(ByteArray(1025) { 7 })
        try {
            BoundedAssetReader.read(input, 1024)
            fail("Expected an oversized asset to be rejected")
        } catch (expected: IOException) {
            assertEquals("Asset exceeds the 1024-byte safety limit", expected.message)
        }
    }

    @Test
    fun handlesSmallReadsWithoutTruncatingData() {
        val expected = byteArrayOf(1, 2, 3, 4, 5, 6, 7)
        val input = object : java.io.InputStream() {
            private var offset = 0
            override fun read(): Int = if (offset >= expected.size) -1 else expected[offset++].toInt() and 0xff
            override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                if (offset >= expected.size) return -1
                val count = minOf(2, len, expected.size - offset)
                System.arraycopy(expected, offset, buffer, off, count)
                offset += count
                return count
            }
        }
        assertArrayEquals(expected, BoundedAssetReader.read(input, 16))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveLimit() {
        BoundedAssetReader.read(ByteArrayInputStream(byteArrayOf(1)), 0)
    }
}
