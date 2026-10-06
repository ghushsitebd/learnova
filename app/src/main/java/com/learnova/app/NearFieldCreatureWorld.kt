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
 * animals enter readable roadside range, this layer streams up to eight real
 * animated GLBs at once. Eight bounded slots add visible wildlife variety
 * while keeping resident authored assets bounded for mobile rendering.
 */
internal class NearFieldCreatureWorld(
    context: Context,
    engine: Engine,
    scene: Scene
) {
    private val creatures = Array(8) { CreatureGlbController(context, engine, scene) }
    private val packagedSpecies = CreatureAssetCoverage.packagedSpecies(context)
    private val activeSpecies = arrayOfNulls<String>(8)
    private var lastUpdateNanos = 0L

    private companion object {
        const val MIN_DISTANCE = 20.0
        const val MAX_DISTANCE = 78.0
        const val UPDATE_NANOS = 100_000_000L
        const val SLOT_COUNT = 8

        // Only authored binaries are promoted into the near-field presentation.
        // Unsupported catalog entries remain on the lightweight distant-life path.
        val SUPPORTED = setOf(
            "lion", "tiger", "elephant", "leopard", "bear", "monkey", "giraffe", "zebra",
            "parrot", "falcon", "owl", "peacock", "ostrich", "buffalo", "gazelle", "stag",
            "boar", "frog", "snake", "crab", "shark", "whale", "seal", "penguin",
            "dolphin", "turtle", "fish", "duck", "horse", "donkey", "goat", "sheep",
            "cow", "chicken", "deer", "fox", "rabbit", "wolf", "camel"
        )
    }

    fun build(): Boolean = true

    fun update(centerDistance: Double) {
        val now = System.nanoTime()
        if (now - lastUpdateNanos < UPDATE_NANOS) return
        lastUpdateNanos = now

        for (index in 0 until SLOT_COUNT) {
            val candidate = candidate(centerDistance, index, now)
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

    private fun candidate(centerDistance: Double, slot: Int, nowNanos: Long): Candidate? {
        val distanceBand = kotlin.math.floor(centerDistance / 72.0).toLong()
        val seed = stableSeed(distanceBand)
        val biome = WorldDirector.profile(centerDistance + 30.0).biome
        val pool = speciesPool(biome)
            .asSequence()
            .filter { it in SUPPORTED && it in packagedSpecies }
            .distinct()
            .toList()
        if (pool.isEmpty()) return null

        // Never instantiate the same authored species in multiple near-field
        // slots at once. This prevents duplicate GLB residency and guarantees
        // that each additional verified asset increases visible wildlife variety.
        val uniquePool = pool.filter { species ->
            species == activeSpecies[slot] || activeSpecies.indexOf(species) < 0
        }
        if (uniquePool.isEmpty()) return null

        val speciesIndex = ((seed ushr (3 + slot)) + slot.toLong() * 3L) % uniquePool.size
        val species = uniquePool[speciesIndex.toInt()]

        val baseDistance = MIN_DISTANCE + slot * 9.0
        val available = (MAX_DISTANCE - baseDistance).toInt().coerceAtLeast(0)
        val variation = if (available == 0) 0L else (seed ushr (12 + slot)) % (available + 1).toLong()
        val spawnDistance = baseDistance + variation

        // Keep authored animals on opposite roadside sides when possible.
        val side = if (((seed + slot.toLong()) and 1L) == 0L) 1.0 else -1.0
        val phase = stablePhase(seed, slot, species)
        val elapsedSeconds = nowNanos * 1e-9
        val motionSpeed = when (species) {
            "horse" -> 0.34
            "deer" -> 0.28
            "cow" -> 0.16
            "wolf" -> 0.24
            "fox" -> 0.20
            else -> 0.22
        }
        // Smooth, bounded wandering keeps movement readable and naturally returns
        // the animal toward its roadside area instead of teleporting.
        val walkOffset = sin(elapsedSeconds * motionSpeed + phase) * 4.0
        val sample = RoadSpline.sampleRelative(
            centerDistance + spawnDistance + walkOffset,
            centerDistance
        )
        val lateral = when (species) {
            "horse" -> 13.0
            "wolf" -> 12.0
            "fox" -> 11.0
            "deer" -> 14.0
            "cow" -> 15.0
            else -> 12.0
        } * side

        val lateralWander = sin(elapsedSeconds * motionSpeed * 0.72 + phase * 1.37) * 1.6
        // RoadSpline uses +Z as forward. The roadside normal is therefore
        // (cos(yaw), -sin(yaw)); using the tangent here would place animals
        // along the carriageway instead of beside it at near-field range.
        val roadsideOffset = lateral + lateralWander
        val x = sample.x + cos(sample.yaw) * roadsideOffset
        val z = sample.z - sin(sample.yaw) * roadsideOffset
        val travelSign = cos(elapsedSeconds * motionSpeed + phase)
        val facing = sample.yaw +
            if (travelSign >= 0.0) 0.0 else kotlin.math.PI +
            if (side > 0.0) -1.5708 else 1.5708
        val scale = when (species) {
            "horse" -> 1.18
            "wolf" -> 1.10
            "fox" -> 0.92
            "deer" -> 1.05
            "cow" -> 1.22
            else -> 0.86
        }

        return Candidate(species, x, sample.y, z, facing, scale)
    }

    private fun stablePhase(seed: Long, slot: Int, species: String): Double {
        val speciesHash = species.fold(0L) { acc, ch -> acc * 33L + ch.code.toLong() }
        val mixed = stableSeed(seed xor (slot.toLong() * 97L) xor speciesHash)
        return ((mixed ushr 11) % 6283L) / 1000.0
    }

    private fun speciesPool(biome: WorldDirector.Biome): List<String> = when (biome) {
        WorldDirector.Biome.FOREST -> listOf("lion", "tiger", "leopard", "bear", "monkey", "wolf", "fox", "deer", "boar", "stag", "rabbit")
        WorldDirector.Biome.MOUNTAIN -> listOf("eagle", "falcon", "bear", "wolf", "horse", "stag", "goat")
        WorldDirector.Biome.PLATEAU -> listOf("giraffe", "zebra", "deer", "horse", "stag", "rabbit", "ostrich")
        WorldDirector.Biome.VILLAGE -> listOf("horse", "cow", "goat", "sheep", "chicken", "donkey", "rabbit", "buffalo")
        WorldDirector.Biome.DESERT -> listOf("camel", "gazelle", "ostrich", "fox")
        WorldDirector.Biome.RIVER -> listOf("dolphin", "turtle", "duck", "fish", "frog")
        WorldDirector.Biome.COAST -> listOf("dolphin", "whale", "seal", "shark", "turtle", "crab")
        WorldDirector.Biome.MARKET -> listOf("horse", "donkey", "goat", "cow", "chicken")
        else -> listOf("deer", "fox", "rabbit", "bird")
    }

    private fun stableSeed(value: Long): Long {
        // Keep the seed arithmetic inside Kotlin Long's literal range on every compiler.
        var x = value * 31L + 17L
        x = x xor (x / 3L)
        x = x * 1103515245L + 12345L
        return x
    }
}
