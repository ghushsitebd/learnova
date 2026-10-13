package com.learnova.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldDirectorTest {

    @Test
    fun chapterOrderCyclesThroughAllEightWorldBiomes() {
        val expected = listOf(
            WorldDirector.Biome.FOREST,
            WorldDirector.Biome.RIVER,
            WorldDirector.Biome.MOUNTAIN,
            WorldDirector.Biome.DESERT,
            WorldDirector.Biome.PLATEAU,
            WorldDirector.Biome.MARKET,
            WorldDirector.Biome.VILLAGE,
            WorldDirector.Biome.COAST
        )

        expected.forEachIndexed { index, biome ->
            assertEquals(biome, WorldDirector.profile(index * 96.0).biome)
        }
    }

    @Test
    fun negativeDistanceIsClampedToTheFirstChapter() {
        assertEquals(WorldDirector.profile(0.0), WorldDirector.profile(-1000.0))
        assertEquals(WorldDirector.Biome.FOREST, WorldDirector.profile(-0.01).biome)
    }

    @Test
    fun biomeSelectionIsDeterministicAcrossRepeatedQueries() {
        val distances = listOf(0.0, 12.0, 95.9, 96.0, 255.0, 768.0, 4096.0)
        distances.forEach { distance ->
            assertEquals(WorldDirector.profile(distance), WorldDirector.profile(distance))
        }
    }

    @Test
    fun atmosphereStartsAtCurrentProfileAndEndsAtNextProfile() {
        val current = WorldDirector.profile(0.0)
        val start = WorldDirector.atmosphere(0.0)
        val end = WorldDirector.atmosphere(96.0)
        val next = WorldDirector.profile(96.0)

        assertEquals(current.skyR, start.skyR, 0.0001f)
        assertEquals(current.skyG, start.skyG, 0.0001f)
        assertEquals(current.skyB, start.skyB, 0.0001f)
        assertEquals(next.skyR, end.skyR, 0.0001f)
        assertEquals(next.skyG, end.skyG, 0.0001f)
        assertEquals(next.skyB, end.skyB, 0.0001f)
        assertEquals(next.biome, end.biome)
    }

    @Test
    fun atmosphereTransitionIsContinuousAtChapterBoundary() {
        val before = WorldDirector.atmosphere(95.999)
        val boundary = WorldDirector.atmosphere(96.0)

        assertTrue(kotlin.math.abs(before.skyR - boundary.skyR) < 0.001f)
        assertTrue(kotlin.math.abs(before.skyG - boundary.skyG) < 0.001f)
        assertTrue(kotlin.math.abs(before.skyB - boundary.skyB) < 0.001f)
        assertTrue(kotlin.math.abs(before.exposure - boundary.exposure) < 0.01f)
    }

    @Test
    fun atmosphereColorsStayInValidRange() {
        for (distance in 0..10_000 step 7) {
            val profile = WorldDirector.atmosphere(distance.toDouble())
            assertTrue(profile.skyR in 0.0f..1.0f)
            assertTrue(profile.skyG in 0.0f..1.0f)
            assertTrue(profile.skyB in 0.0f..1.0f)
            assertTrue(profile.skyA in 0.0f..1.0f)
        }
    }
}
