package com.learnova.app

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.Scene
import kotlin.math.cos
import kotlin.math.sin

/**
 * Near-field wildlife layer.
 *
 * Only verified packaged creature keys are eligible for real GLB presentation.
 * The logical 1,000+ catalog remains independent from the authored binary pack.
 */
internal class NearFieldCreatureWorld(
    context: Context,
    engine: Engine,
    scene: Scene
) {
    private val creature = CreatureGlbController(context, engine, scene)
    private val packagedSpecies = CreatureAssetCoverage.packagedSpecies(context)
    private var activeSpecies: String? = null
    private var lastUpdateNanos = 0L
    private var lastCenter = Double.NaN

    private companion object {
        const val MIN_DISTANCE = 22.0
        const val MAX_DISTANCE = 58.0
        const val UPDATE_NANOS = 100_000_000L

        // These are the real animated GLBs currently supplied by the production
        // asset pipeline. A species is promoted only when its packaged binary is
        // actually present in the release bundle.
        val PROMOTABLE = setOf("lion", "deer", "fox", "horse", "wolf")
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

        val habitat = when (biome) {
            WorldDirector.Biome.FOREST -> listOf("lion", "deer", "fox", "wolf")
            WorldDirector.Biome.VILLAGE,
            WorldDirector.Biome.MARKET -> listOf("horse")
            else -> emptyList()
        }

        // Never substitute a placeholder: only an actually packaged binary can
        // become a readable near-field creature.
        val available = habitat.filter { it in PROMOTABLE && it in packagedSpecies }
        if (available.isEmpty()) return null

        val species = available[(seed ushr 8).mod(available.size.toLong()).toInt()]
        val spawnDistance = MIN_DISTANCE + ((seed ushr 12) % 37L).toDouble()
        val side = if ((seed and 1L) == 0L) 1.0 else -1.0
        val sample = RoadSpline.sampleRelative(centerDistance + spawnDistance, centerDistance)
        val lateral = when (species) {
            "lion" -> 11.0
            "deer" -> 12.0
            "fox" -> 10.0
            "wolf" -> 11.0
            "horse" -> 12.5
            else -> 11.0
        } * side

        val x = sample.x + sin(sample.yaw) * lateral
        val z = sample.z + cos(sample.yaw) * lateral
        val facing = sample.yaw + if (side > 0.0) -1.5708 else 1.5708
        val scale = when (species) {
            "lion" -> 1.18
            "deer" -> 1.05
            "fox" -> 0.78
            "wolf" -> 0.95
            "horse" -> 1.10
            else -> 1.0
        }

        return Candidate(species, x, sample.y, z, facing, scale)
    }

    private fun stableSeed(value: Long): Long {
        var x = value * 31L + 17L
        x = x xor (x / 3L)
        x = x * 1103515245L + 12345L
        return x
    }
}
