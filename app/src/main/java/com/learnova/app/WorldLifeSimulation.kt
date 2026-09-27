package com.learnova.app

import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.VertexBuffer
import com.google.android.filament.gltfio.FilamentAsset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/**
 * Distant living-world population.
 *
 * A single combined renderable keeps draw calls low while deterministic agents
 * move independently around the road corridor. Near-field authored assets can
 * replace these silhouettes later without changing the simulation contract.
 */
internal class WorldLifeSimulation(
    private val engine: Engine,
    private val scene: Scene,
    private val asset: FilamentAsset
) {
    private companion object {
        const val BEHIND = 80.0
        const val AHEAD = 600.0
        const val STEP = 8.0
        const val MAX_AGENTS = 112
        const val NEAR_RADIUS = 85.0
        const val MID_RADIUS = 260.0
        const val VERTICES_PER_AGENT = 8
        const val INDICES_PER_AGENT = 36
        const val STRIDE = 36
    }

    private var entity = 0
    private var vb: VertexBuffer? = null
    private var ib: IndexBuffer? = null
    private var material: com.google.android.filament.MaterialInstance? = null
    private var lastCenter = Double.NaN
    private var lastAnimationNanos = 0L
    // Reuse the population buffers instead of allocating large direct ByteBuffers
    // every simulation tick. This keeps GC pressure low on memory-constrained phones.
    private val vertexBufferData = ByteBuffer.allocate(MAX_AGENTS * VERTICES_PER_AGENT * STRIDE).order(ByteOrder.nativeOrder())
    private val indexBufferData = ByteBuffer.allocate(MAX_AGENTS * INDICES_PER_AGENT * 2).order(ByteOrder.nativeOrder())

    fun build(): Boolean {
        if (entity != 0) return true
        material = findMaterial() ?: return false
        vb = VertexBuffer.Builder()
            .vertexCount(MAX_AGENTS * VERTICES_PER_AGENT)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 28, STRIDE)
            .build(engine)
        ib = IndexBuffer.Builder()
            .indexCount(MAX_AGENTS * INDICES_PER_AGENT)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .material(0, material!!)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb!!, ib!!)
            .culling(false)
            .receiveShadows(true)
            .castShadows(false)
            .build(engine, entity)
        scene.addEntity(entity)
        update(0.0)
        return true
    }

    fun update(centerDistance: Double) {
        if (entity == 0 || vb == null || ib == null) return
        val now = System.nanoTime()
        val animationTime = now * 1.0e-9
        // Keep the living world animated even while the child pauses the vehicle.
        // Rebuild at a bounded cadence so pedestrians/animals move naturally without
        // turning the combined population mesh into a per-frame allocation hotspot.
        if (!lastCenter.isNaN() && kotlin.math.abs(centerDistance - lastCenter) < 9.0 &&
            now - lastAnimationNanos < 140_000_000L) return

        val vertices = vertexBufferData.apply { clear() }
        val indices = indexBufferData.apply { clear() }
        var vCount = 0
        var iCount = 0
        var d = kotlin.math.floor((centerDistance - BEHIND) / STEP) * STEP

        while (d <= centerDistance + AHEAD && vCount + 8 <= MAX_AGENTS * 8) {
            val seed = stableSeed(d)
            // Give traffic a small independent longitudinal velocity. The
            // offset is deterministic and bounded, so spacing never explodes,
            // but nearby vehicles visibly gain/lose distance like real traffic.
            val trafficPhase = phaseFromSeed(seed)
            val trafficMotion = sin((centerDistance * (0.012 + ((seed ushr 36) % 7L) * 0.001)) +
                animationTime * (0.32 + ((seed ushr 39) % 30L) / 100.0) + trafficPhase * 6.283) *
                (2.0 + ((seed ushr 39) % 30L) / 10.0)
            val agentDistance = d + trafficMotion
            val sample = RoadSpline.sampleRelative(agentDistance, centerDistance)
            // Keep nearby traffic in believable lanes while allowing distant
            // activity to spread wider into the landscape.
            val distanceFromPlayer = kotlin.math.abs(agentDistance - centerDistance)
            val baseLane = when {
                distanceFromPlayer < NEAR_RADIUS -> when ((seed ushr 3) % 5L) {
                    0L -> -6.2
                    1L -> 6.2
                    2L -> -12.0
                    3L -> 12.0
                    else -> 18.0
                }
                distanceFromPlayer < MID_RADIUS -> when ((seed ushr 3) % 6L) {
                    0L -> -6.2
                    1L -> 6.2
                    2L -> -12.0
                    3L -> 12.0
                    4L -> -18.0
                    else -> 18.0
                }
                else -> when ((seed ushr 3) % 7L) {
                    0L -> -6.2
                    1L -> 6.2
                    2L -> -12.0
                    3L -> 12.0
                    5L -> -20.0
                    else -> 20.0
                }
            }
            val side = if ((seed and 1L) == 0L) -1.0 else 1.0
            // Traffic only performs a lane correction when it has enough
            // longitudinal separation. This avoids the arcade-like "weaving"
            // look and leaves a stable safety gap around the player.
            val correctionBand = if (distanceFromPlayer < NEAR_RADIUS) 1.15 else 1.8
            val laneShiftAllowed = distanceFromPlayer < MID_RADIUS &&
                (seed ushr 12) % 5L == 0L &&
                kotlin.math.abs(trafficMotion) > 0.9
            val laneShift = if (laneShiftAllowed) {
                sin(centerDistance * 0.014 + trafficPhase * 6.283) * correctionBand
            } else 0.0
            val lane = baseLane + laneShift
            val biome = WorldDirector.profile(agentDistance).biome
            val isBusyBiome = biome == WorldDirector.Biome.MARKET || biome == WorldDirector.Biome.VILLAGE
            // People and wildlife belong to the landscape, not the carriageway.
            // Give them deterministic roadside corridors so they read as walkers
            // and animals inhabiting the world rather than traffic-shaped blocks.
            val isPedestrian = isBusyBiome && ((seed ushr 4) % 9L >= 6L)
            val isWildlife = biome == WorldDirector.Biome.FOREST && ((seed ushr 4) % 8L >= 5L)
            val roadsideOffset = when {
                isPedestrian -> 27.0 + ((seed ushr 44) % 13L) / 10.0
                isWildlife -> 34.0 + ((seed ushr 44) % 25L) / 10.0
                else -> kotlin.math.abs(lane)
            }
            val x = sample.x + cos(sample.yaw) * roadsideOffset * side
            val z = sample.z - sin(sample.yaw) * roadsideOffset * side
            val hasVisibleMotion = kotlin.math.abs(trafficMotion) > 0.75
            // Activity density falls with distance: nearby life gets enough
            // motion to read as living, while far silhouettes remain sparse enough
            // to preserve the long-distance landscape and GPU budget.
            val moving = when {
                distanceFromPlayer < 42.0 && isBusyBiome -> (seed ushr 9) % 7L != 0L
                distanceFromPlayer < NEAR_RADIUS && isBusyBiome -> (seed ushr 9) % 5L != 0L
                distanceFromPlayer < NEAR_RADIUS -> hasVisibleMotion && (seed ushr 9) % 6L != 0L
                distanceFromPlayer < MID_RADIUS -> (seed ushr 9) % 4L != 0L
                else -> (seed ushr 9) % 3L != 0L
            }
            val phase = ((seed ushr 17) % 1000L) / 1000.0
            // Deterministic local time gives each agent its own speed phase,
            // avoiding the synchronized "all objects slide together" look.
            val localTime = centerDistance * (0.028 + ((seed ushr 27) % 9L) * 0.001) +
                animationTime * (0.55 + ((seed ushr 31) % 35L) / 100.0)
            val baseDrift = if (moving) sin(localTime + phase * 6.283) * (1.15 + ((seed ushr 30) % 80L) / 100.0) else 0.0
            // Walkers use a slower, shorter gait: lateral sway + subtle body
            // bounce makes them read as living people instead of sliding blocks.
            val walkPhase = centerDistance * (if (isWildlife) 0.11 else 0.22) + phase * 6.283
            val pedestrianSway = if (isPedestrian) sin(walkPhase) * 0.48 else 0.0
            val pedestrianDrift = if (isPedestrian && moving) sin(walkPhase * 0.5) * 0.55 else 0.0
            val wildlifeDrift = if (isWildlife && moving) sin(walkPhase) * 1.1 else 0.0
            val drift = when {
                isPedestrian -> pedestrianDrift
                isWildlife -> wildlifeDrift
                else -> baseDrift
            }
            val px = x + cos(sample.yaw) * drift + sin(sample.yaw) * pedestrianSway
            val pz = z - sin(sample.yaw) * drift + cos(sample.yaw) * pedestrianSway
            // Population families vary by biome so each environment has a
            // different rhythm instead of repeating the same roadside objects.
            val family = when (biome) {
                WorldDirector.Biome.FOREST -> ((seed ushr 4) % 8L).toInt()
                WorldDirector.Biome.RIVER, WorldDirector.Biome.COAST -> ((seed ushr 4) % 7L).toInt()
                WorldDirector.Biome.MOUNTAIN, WorldDirector.Biome.PLATEAU -> ((seed ushr 4) % 7L).toInt()
                WorldDirector.Biome.DESERT -> ((seed ushr 4) % 5L).toInt()
                WorldDirector.Biome.MARKET, WorldDirector.Biome.VILLAGE -> ((seed ushr 4) % 9L).toInt()
            }
            // Market/village can contain narrow human-scale walkers.
            val kind = if ((biome == WorldDirector.Biome.MARKET || biome == WorldDirector.Biome.VILLAGE) && family >= 6) {
                6 + (family - 6)
            } else {
                family.coerceAtMost(5)
            }
            val scale = 0.72 + ((seed ushr 21) % 52L) / 100.0

            val height = when (kind) {
                0, 1 -> 1.15 * scale
                2 -> 2.2 * scale
                3 -> 1.55 * scale
                4 -> 1.0 * scale
                5 -> 1.35 * scale
                6 -> 1.62 * scale
                else -> 1.48 * scale
            }
            val width = when (kind) {
                0, 1 -> 1.15 * scale
                2 -> 0.75 * scale
                3 -> 1.5 * scale
                4 -> 0.9 * scale
                5 -> 1.25 * scale
                6 -> 0.42 * scale
                else -> 0.34 * scale
            }

            // Far agents are deliberately simplified: silhouette + motion cues
            // carry the perception of a populated world while keeping GPU cost low.
            val bodyBounce = when {
                isPedestrian -> kotlin.math.abs(sin(walkPhase)) * 0.045
                isWildlife -> kotlin.math.abs(sin(walkPhase)) * 0.075
                else -> 0.0
            }
            val bodyYaw = when {
                isPedestrian -> sample.yaw.toDouble() + sin(walkPhase) * 0.08
                isWildlife -> sample.yaw.toDouble() + sin(walkPhase) * 0.16
                else -> sample.yaw.toDouble()
            }
            val groundY = sample.y.toDouble() +
                kotlin.math.sin(sample.bank.toDouble()) * kotlin.math.abs(roadsideOffset) * 0.35
            addAgent(vertices, indices, px, groundY + 0.05 + bodyBounce, pz, bodyYaw, width, height, kind)
            vCount += 8
            iCount += 36
            d += STEP
        }

        while (vCount < MAX_AGENTS * 8) {
            repeat(8) { putVertex(vertices, 0f, -1000f, 0f, 0f, 0f, 0f, 0f) }
            vCount += 8
        }
        while (iCount < MAX_AGENTS * 36) {
            val base = (iCount / 36) * 8
            val faces = intArrayOf(0,1,2,0,2,3,4,6,5,4,7,6,0,4,5,0,5,1,1,5,6,1,6,2,2,6,7,2,7,3,4,0,3,4,3,7)
            for (f in faces) indices.putShort((base + f).toShort())
            iCount += 36
        }
        vertices.flip(); indices.flip()
        vb!!.setBufferAt(engine, 0, vertices)
        ib!!.setBuffer(engine, indices)
        lastCenter = centerDistance
        lastAnimationNanos = now
    }

    private fun addAgent(
        vertices: ByteBuffer, indices: ByteBuffer,
        x: Double, y: Double, z: Double, yaw: Double,
        width: Double, height: Double, kind: Int
    ) {
        val base = vertices.position() / STRIDE
        val depth = width * when (kind) {
            2, 5 -> 1.8
            else -> 1.25
        }
        val bodyY = y
        val cx = cos(yaw)
        val cz = -sin(yaw)
        val sx = sin(yaw)
        val sz = cos(yaw)

        val topScale = when (kind) {
            0 -> 0.58 // small car
            1 -> 0.72 // van/bus
            2 -> 0.42 // tree/large animal silhouette
            3 -> 0.62 // truck
            4 -> 0.52 // animal
            else -> 0.70
        }

        val corners = arrayOf(
            doubleArrayOf(-width,0.0,-depth), doubleArrayOf(width,0.0,-depth),
            doubleArrayOf(width,0.0,depth), doubleArrayOf(-width,0.0,depth),
            doubleArrayOf(-width*topScale,height,-depth*0.72),
            doubleArrayOf(width*topScale,height,-depth*0.72),
            doubleArrayOf(width*topScale,height,depth*0.72),
            doubleArrayOf(-width*topScale,height,depth*0.72)
        )
        for (c in corners) {
            val lx = c[0]; val lz = c[2]
            putVertex(
                vertices,
                (x + lx*cx - lz*sx).toFloat(),
                (bodyY + c[1]).toFloat(),
                (z + lx*cz + lz*sz).toFloat(),
                yaw.toFloat(), 0f,
                ((lx + width) / (2.0*width)).toFloat(),
                (c[1] / height.coerceAtLeast(0.01)).toFloat()
            )
        }
        val faces = intArrayOf(0,1,2,0,2,3,4,6,5,4,7,6,0,4,5,0,5,1,1,5,6,1,6,2,2,6,7,2,7,3,4,0,3,4,3,7)
        for (f in faces) indices.putShort((base + f).toShort())
    }

    private fun putVertex(data: ByteBuffer, x: Float, y: Float, z: Float, yaw: Float, bank: Float, u: Float, v: Float) {
        data.putFloat(x); data.putFloat(y); data.putFloat(z)
        val hy = yaw * 0.5f
        val hb = bank * 0.5f
        data.putFloat(sin(hb)); data.putFloat(0f); data.putFloat(sin(hy)); data.putFloat(cos(hy) * cos(hb))
        data.putFloat(u); data.putFloat(v)
    }

    private fun findMaterial(): com.google.android.filament.MaterialInstance? {
        val names = arrayOf("Ground","Terrain","Grass","Landscape","ground","terrain","grass")
        val rm = engine.renderableManager
        for (name in names) {
            val e = asset.getFirstEntityByName(name)
            if (e == 0 || !rm.hasComponent(e)) continue
            val i = rm.getInstance(e)
            if (rm.getPrimitiveCount(i) > 0) return rm.getMaterialInstanceAt(i,0)
        }
        return null
    }

    private fun phaseFromSeed(seed: Long): Double =
        ((seed ushr 17) % 1000L) / 1000.0

    private fun stableSeed(distance: Double): Long {
        var x = java.lang.Double.doubleToLongBits(kotlin.math.floor(distance / STEP) * STEP)
        x = x xor (x ushr 33); x *= -49064778989728563L
        x = x xor (x ushr 33); x *= -4265267296055464877L
        return x xor (x ushr 33)
    }

    fun destroy() {
        if (entity != 0) {
            scene.removeEntity(entity)
            engine.renderableManager.destroy(entity)
            EntityManager.get().destroy(entity)
            entity = 0
        }
        vb?.let { engine.destroyVertexBuffer(it) }
        ib?.let { engine.destroyIndexBuffer(it) }
        vb = null; ib = null; material = null; lastCenter = Double.NaN
        lastAnimationNanos = 0L
    }
}
