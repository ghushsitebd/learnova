package com.learnova.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveFrameQualityTest {

    @Test
    fun sustainedSlowFramesLowerQualityOneLevelPerWindow() {
        val controller = AdaptiveFrameQuality(initialLevel = 3)
        repeat(45) { controller.observe(24.0) }
        assertEquals(2, controller.level())
        repeat(45) { controller.observe(24.0) }
        assertEquals(1, controller.level())
    }

    @Test
    fun fastStableWindowRestoresOnlyOneQualityLevel() {
        val controller = AdaptiveFrameQuality(initialLevel = 1)
        repeat(45) { controller.observe(10.0) }
        assertEquals(2, controller.level())
    }

    @Test
    fun qualityNeverExceedsSupportedBounds() {
        val low = AdaptiveFrameQuality(initialLevel = -20)
        val high = AdaptiveFrameQuality(initialLevel = 20)
        assertEquals(0, low.level())
        assertEquals(3, high.level())
        repeat(180) { low.observe(8.0) }
        repeat(180) { high.observe(30.0) }
        assertEquals(3, low.level())
        assertEquals(0, high.level())
    }

    @Test
    fun fiveSlowFramesDoNotTriggerDowngradeButSixDo() {
        val controller = AdaptiveFrameQuality(initialLevel = 2)
        repeat(5) { controller.observe(30.0) }
        repeat(40) { controller.observe(10.0) }
        assertEquals(2, controller.level())

        repeat(6) { controller.observe(30.0) }
        repeat(39) { controller.observe(10.0) }
        assertEquals(1, controller.level())
    }

    @Test
    fun invalidFrameMeasurementsAreTreatedAsSlow() {
        val controller = AdaptiveFrameQuality(initialLevel = 2)
        repeat(3) { controller.observe(Double.NaN) }
        repeat(3) { controller.observe(-1.0) }
        repeat(39) { controller.observe(10.0) }
        assertEquals(1, controller.level())
    }

    @Test
    fun mixedAndBorderlineFramesDoNotPromoteQuality() {
        val controller = AdaptiveFrameQuality(initialLevel = 1)
        repeat(44) { controller.observe(12.0) }
        controller.observe(16.0)
        assertEquals(1, controller.level())
    }
}
