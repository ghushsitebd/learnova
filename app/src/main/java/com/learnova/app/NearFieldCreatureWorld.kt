package com.learnova.app

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.Scene
import kotlin.math.cos
import kotlin.math.sin

/**
 * Near-field wildlife layer.
 *
 * Distant life remains the low-cost population mesh. When verified authored
 * animals enter readable roadside range, this layer streams up to two real
 * animated GLBs at once. Two bounded slots add visible wildlife variety while
 * keeping resident authored assets small enough for mobile rendering.
 */
internal class NearFieldCreatureWorld(
    context: Context,
    engine: Engine,
    scene: Scene
) {
    private val creatures = arrayOf(
        CreatureGlbController(context, engine, scene),
        CreatureGlbController(context, engine, scene)
    )
    private val packagedSpecies = CreatureAssetCoverage.packagedSpecies(context)
    private val activeSpecies = arrayOfNulls<String>(2)
    private var lastUpdateNanos = 0L

    private companion object {
        const val MIN_DISTANCE = 22.0
        const val MAX_DISTANCE = 58.0
        const val UPDATE_NANOS = 100_000_000L
        const val SLOT_COUNT = 2

        // Only authored binaries are promoted into the near-field presentation.
        // Unsupported catalog entries remain on the lightweight distant-life path.
        val SUPPORTED = setOf("deer", "fox", "horse", "wolf")
    }

    fun build(): Boolean = true

    fun update(centerDistance: Double) {
        val now = System.nanoTime()
        if (now - lastUpdateNanos < UPDATE_NANOS) return
        lastUpdateNanos = now

        for (index in 0 until SLOT_COUNT) {
            val candidate = candidate(centerDistance, index)
            if (candidate == null) {
                if (activeSpecies[index] != null) {
                    creatures[index].hide()
                    activeSpecies[index] = null
                }
                continue
            }

            val species = candidate.species
            if (species != activeSpecies[index]) {
                creatures[index].hide()
                activeSpecies[index] = null
                if (creatures[index].show(
                        species,
                        candidate.x,
                        candidate.y,
                        candidate.z,
                        candidate.yaw,
                        candidate.scale
                    )
                ) {
                    activeSpecies[index] = species
                }
            } else {
                creatures[index].move(
                    candidate.x,
                    candidate.y,
                    candidate.z,
                    candidate.yaw,
                    candidate.scale
                )
            }
        }
    }

    fun destroy() {
        for (index in 0 until SLOT_COUNT) {
            creatures[index].destroy()
            activeSpecies[index] = null
        }
    }

    private data class Candidate(
        val species: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Double,
        val scale: Double
    )

    private fun candidate(centerDistance: Double, slot: Int): Candidate? {
        val distanceBand = kotlin.math.floor(centerDistance / 72.0).toLong()
        val seed = stableSeed(distanceBand)
        val biome = WorldDirector.profile(centerDistance + 30.0).biome
        val pool = speciesPool(biome)
        if (pool.isEmpty()) return null

        val speciesIndex = ((seed ushr (3 + slot)) + slot.toLong()) % pool.size
        val species = pool[speciesIndex.toInt()]
        if (species !in SUPPORTED || species !in packagedSpecies) return null

        val baseDistance = MIN_DISTANCE + slot * 18.0
        val available = (MAX_DISTANCE - baseDistance).toInt().coerceAtLeast(0)
        val variation = if (available == 0) 0L else (seed ushr (12 + slot)) % (available + 1).toLong()
        val spawnDistance = baseDistance + variation

        // Keep the two authored animals on opposite roadside sides when possible.
        val side = if (((seed + slot.toLong()) and 1L) == 0L) 1.0 else -1.0
        val sample = RoadSpline.sampleRelative(centerDistance + spawnDistance, centerDistance)
        val lateral = when (species) {
            "horse" -> 13.0
            "wolf" -> 12.0
            "fox" -> 11.0
            "deer" -> 14.0
            else -> 12.0
        } * side

        val x = sample.x + sin(sample.yaw) * lateral
        val z = sample.z + cos(sample.yaw) * lateral
        val facing = sample.yaw + if (side > 0.0) -1.5708 else 1.5708
        val scale = when (species) {
            "horse" -> 1.18
            "wolf" -> 1.10
            "fox" -> 0.92
            "deer" -> 1.05
            else -> 0.86
        }

        return Candidate(species, x, sample.y, z, facing, scale)
    }

    private fun speciesPool(biome: WorldDirector.Biome): List<String> = when (biome) {
        WorldDirector.Biome.FOREST -> listOf("wolf", "fox", "deer", "horse")
        WorldDirector.Biome.MOUNTAIN -> listOf("wolf", "horse")
        WorldDirector.Biome.PLATEAU -> listOf("deer", "horse")
        WorldDirector.Biome.VILLAGE -> listOf("horse", "deer")
        else -> emptyList()
    }

    private fun stableSeed(value: Long): Long {
        // Keep the seed arithmetic inside Kotlin Long's literal range on every compiler.
        var x = value * 31L + 17L
        x = x xor (x / 3L)
        x = x * 1103515245L + 12345L
        return x
    }
}
