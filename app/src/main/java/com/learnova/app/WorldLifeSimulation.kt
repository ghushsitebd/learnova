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
        const val STEP = 12.0
        const val MAX_AGENTS = 112
        const val VERTICES_PER_AGENT = 8
        const val INDICES_PER_AGENT = 36
        const val STRIDE = 36
    }

    private var entity = 0
    private var vb: VertexBuffer? = null
    private var ib: IndexBuffer? = null
    private var material: com.google.android.filament.MaterialInstance? = null
    private var lastCenter = Double.NaN

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
        if (!lastCenter.isNaN() && kotlin.math.abs(centerDistance - lastCenter) < 9.0) return

        val vertices = ByteBuffer.allocate(MAX_AGENTS * VERTICES_PER_AGENT * STRIDE).order(ByteOrder.nativeOrder())
        val indices = ByteBuffer.allocate(MAX_AGENTS * INDICES_PER_AGENT * 2).order(ByteOrder.nativeOrder())
        var vCount = 0
        var iCount = 0
        var d = kotlin.math.floor((centerDistance - BEHIND) / STEP) * STEP

        while (d <= centerDistance + AHEAD && vCount + 8 <= MAX_AGENTS * 8) {
            val seed = stableSeed(d)
            val sample = RoadSpline.sampleRelative(d, centerDistance)
            val lane = when ((seed ushr 3) % 7L) {
                0L -> -6.2
                1L -> 6.2
                2L -> -12.0
                3L -> 12.0
                5L -> -20.0
                else -> 20.0
            }
            val side = if ((seed and 1L) == 0L) -1.0 else 1.0
            val x = sample.x + cos(sample.yaw) * lane * side
            val z = sample.z - sin(sample.yaw) * lane * side
            val moving = (seed ushr 9) % 4L != 0L
            val phase = ((seed ushr 17) % 1000L) / 1000.0
            val biome = WorldDirector.profile(d).biome
            val baseDrift = if (moving) sin(centerDistance * 0.035 + phase * 6.283) * 1.8 else 0.0
            // Walkers use a slower, shorter gait: lateral sway + subtle body
            // bounce makes them read as living people instead of sliding blocks.
            val isPedestrian = (biome == WorldDirector.Biome.MARKET || biome == WorldDirector.Biome.VILLAGE) &&
                ((seed ushr 4) % 9L >= 6L)
            val walkPhase = centerDistance * 0.22 + phase * 6.283
            val pedestrianSway = if (isPedestrian) sin(walkPhase) * 0.48 else 0.0
            val pedestrianDrift = if (isPedestrian && moving) sin(walkPhase * 0.5) * 0.55 else 0.0
            val drift = if (isPedestrian) pedestrianDrift else baseDrift
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
            val bodyBounce = if (isPedestrian) kotlin.math.abs(sin(walkPhase)) * 0.045 else 0.0
            val bodyYaw = if (isPedestrian) sample.yaw.toDouble() + sin(walkPhase) * 0.08 else sample.yaw.toDouble()
            addAgent(vertices, indices, px, sample.y.toDouble() + 0.05 + bodyBounce, pz, bodyYaw, width, height, kind)
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
    }
}
