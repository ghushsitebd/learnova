package com.learnova.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderQualityPolicyTest {

    @Test
    fun memoryClassSelectsExpectedStartupTier() {
        assertEquals(0, RenderQualityPolicy.initialLevel(128, false))
        assertEquals(1, RenderQualityPolicy.initialLevel(192, false))
        assertEquals(2, RenderQualityPolicy.initialLevel(256, false))
        assertEquals(3, RenderQualityPolicy.initialLevel(512, false))
    }

    @Test
    fun lowRamDeviceIsCappedAtConservativeQuality() {
        assertEquals(0, RenderQualityPolicy.initialLevel(128, true))
        assertEquals(1, RenderQualityPolicy.initialLevel(192, true))
        assertEquals(1, RenderQualityPolicy.initialLevel(256, true))
        assertEquals(1, RenderQualityPolicy.initialLevel(1024, true))
    }

    @Test
    fun memoryTierBoundariesAreStable() {
        assertEquals(0, RenderQualityPolicy.initialLevel(191, false))
        assertEquals(1, RenderQualityPolicy.initialLevel(255, false))
        assertEquals(2, RenderQualityPolicy.initialLevel(511, false))
    }
}
