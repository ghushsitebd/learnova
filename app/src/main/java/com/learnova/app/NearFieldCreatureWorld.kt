package com.learnova.app

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.Scene
import kotlin.math.cos
import kotlin.math.sin

/**
 * Near-field wildlife layer.
 *
 * Distant life remains the low-cost population mesh. When a supported authored
 * animal enters the readable roadside range, this layer swaps in one real
 * animated GLB and moves it with the same road coordinate system. This keeps
 * the world visually credible without attempting to keep hundreds of GLBs resident.
 */
internal class NearFieldCreatureWorld(
    context: Context,
    engine: Engine,
    scene: Scene
) {
    private val creature = CreatureGlbController(context, engine, scene)
    private var activeSpecies: String? = null
    private var lastUpdateNanos = 0L
    private var lastCenter = Double.NaN

    private companion object {
        const val MIN_DISTANCE = 22.0
        const val MAX_DISTANCE = 58.0
        const val UPDATE_NANOS = 100_000_000L
        val SUPPORTED = setOf("deer", "fox", "horse", "wolf")
    }

    fun build(): Boolean = true

    fun update(centerDistance: Double) {
        val now = System.nanoTime()
        if (now - lastUpdateNanos < UPDATE_NANOS) return
        lastUpdateNanos = now

        val candidate = candidate(centerDistance)
        if (candidate == null) {
            if (activeSpecies != null) {
                creature.hide()
                activeSpecies = null
            }
            lastCenter = centerDistance
            return
        }

        val species = candidate.species
        if (species != activeSpecies) {
            creature.hide()
            activeSpecies = null
            if (creature.show(species, candidate.x, candidate.y, candidate.z, candidate.yaw, candidate.scale)) {
                activeSpecies = species
            }
        } else {
            creature.move(candidate.x, candidate.y, candidate.z, candidate.yaw, candidate.scale)
        }
        lastCenter = centerDistance
    }

    fun destroy() {
        creature.destroy()
        activeSpecies = null
    }

    private data class Candidate(
        val species: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Double,
        val scale: Double
    )

    private fun candidate(centerDistance: Double): Candidate? {
        val distanceBand = kotlin.math.floor(centerDistance / 72.0).toLong()
        val seed = stableSeed(distanceBand)
        val biome = WorldDirector.profile(centerDistance + 30.0).biome

        val species = when (biome) {
            WorldDirector.Biome.FOREST -> when ((seed ushr 4) % 3L) {
                0L -> "deer"
                1L -> "fox"
                else -> "wolf"
            }
            WorldDirector.Biome.MOUNTAIN -> if ((seed and 1L) == 0L) "wolf" else "fox"
            WorldDirector.Biome.VILLAGE, WorldDirector.Biome.MARKET -> "horse"
            WorldDirector.Biome.PLATEAU -> if ((seed and 1L) == 0L) "horse" else "deer"
            else -> null
        } ?: return null

        if (species !in SUPPORTED) return null

        val spawnDistance = MIN_DISTANCE + ((seed ushr 12) % 37L).toDouble()
        val side = if ((seed and 1L) == 0L) 1.0 else -1.0
        val sample = RoadSpline.sampleRelative(centerDistance + spawnDistance, centerDistance)
        val lateral = when (species) {
            "horse" -> 7.0
            "deer" -> 10.0
            "fox" -> 12.0
            else -> 14.0
        } * side

        val x = sample.x + sin(sample.yaw) * lateral
        val z = sample.z + cos(sample.yaw) * lateral
        val facing = sample.yaw + if (side > 0.0) -1.5708 else 1.5708
        val scale = when (species) {
            "horse" -> 1.10
            "deer" -> 1.00
            "wolf" -> 0.92
            else -> 0.86
        }

        return Candidate(species, x, sample.y, z, facing, scale)
    }

    private fun stableSeed(value: Long): Long {
        var x = value + -7046029254386353131L
        x = (x xor (x ushr 30)) * -4658895280553007687L
        x = (x xor (x ushr 27)) * -7723592293110705685L
        return x xor (x ushr 31)
    }
}
