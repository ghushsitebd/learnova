package com.learnova.app

import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.VertexBuffer
import com.google.android.filament.gltfio.FilamentAsset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

internal class WaterSurfaceWorld(
    private val engine: Engine,
    private val scene: Scene,
    private val asset: FilamentAsset
) {
    private companion object {
        const val SAMPLE_COUNT = 49
        const val STEP = 5.0
        const val BEHIND = 35.0
        const val STRIDE = 9 * 4
        const val VERTICES = SAMPLE_COUNT * 2
        const val INDICES = (SAMPLE_COUNT - 1) * 6
    }

    private var entity = 0
    private var vb: VertexBuffer? = null
    private var ib: IndexBuffer? = null
    private var material: MaterialInstance? = null
    private var lastCenter = Double.NaN

    fun build(): Boolean {
        if (entity != 0) return true
        val source = findMaterial() ?: return false
        material = try {
            MaterialInstance.duplicate(source, "LearnovaWater")
        } catch (_: Throwable) {
            return false
        }

        try { material?.setParameter("baseColor", 0.18f, 0.46f, 0.62f, 1.0f) } catch (_: Throwable) {}
        try { material?.setParameter("metallic", 0.0f) } catch (_: Throwable) {}
        try { material?.setParameter("roughness", 0.16f) } catch (_: Throwable) {}
        try { material?.setParameter("reflectance", 0.35f) } catch (_: Throwable) {}

        vb = VertexBuffer.Builder()
            .vertexCount(VERTICES).bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 28, STRIDE)
            .build(engine)

        ib = IndexBuffer.Builder()
            .indexCount(INDICES)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)

        val indices = ByteBuffer.allocate(INDICES * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until SAMPLE_COUNT - 1) {
            val a = i * 2
            indices.putShort(a.toShort()); indices.putShort((a + 2).toShort()); indices.putShort((a + 1).toShort())
            indices.putShort((a + 1).toShort()); indices.putShort((a + 2).toShort()); indices.putShort((a + 3).toShort())
        }
        indices.flip()
        ib!!.setBuffer(engine, indices)

        entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .material(0, material!!)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb!!, ib!!)
            .culling(false).receiveShadows(true).castShadows(false)
            .build(engine, entity)
        scene.addEntity(entity)
        update(0.0)
        return true
    }

    fun update(centerDistance: Double) {
        if (entity == 0 || vb == null || ib == null) return
        if (!lastCenter.isNaN() && kotlin.math.abs(centerDistance - lastCenter) < 4.0) return

        val data = ByteBuffer.allocate(VERTICES * STRIDE).order(ByteOrder.nativeOrder())
        val start = kotlin.math.floor((centerDistance - BEHIND) / STEP) * STEP

        for (i in 0 until SAMPLE_COUNT) {
            val d = start + i * STEP
            val sample = RoadSpline.sampleRelative(d, centerDistance)
            val biome = WorldDirector.profile(d).biome
            if (biome != WorldDirector.Biome.RIVER && biome != WorldDirector.Biome.COAST) {
                putVertex(data, 0f, -5000f, 0f, sample.yaw.toDouble(), 0f, 0f, 0f)
                putVertex(data, 0f, -5000f, 0f, sample.yaw.toDouble(), 0f, 1f, 0f)
                continue
            }

            val seed = stableSeed(d)
            val side = if ((seed and 1L) == 0L) -1.0 else 1.0
            val centerOffset = if (biome == WorldDirector.Biome.COAST) 34.0 else 27.0
            val width = if (biome == WorldDirector.Biome.COAST) 30.0 else 18.0
            val yaw = sample.yaw.toDouble()
            val lx = cos(yaw)
            val lz = -sin(yaw)
            val cx = sample.x + lx * centerOffset * side
            val cz = sample.z + lz * centerOffset * side
            val half = width * 0.5
            val leftX = cx + lx * half * side
            val leftZ = cz + lz * half * side
            val rightX = cx - lx * half * side
            val rightZ = cz - lz * half * side
            val wave = sin(d * 0.22) * 0.035 + sin(d * 0.071 + 1.4) * 0.022
            val waterY = sample.y - if (biome == WorldDirector.Biome.COAST) 0.45 else 0.72

            putVertex(data, leftX.toFloat(), (waterY + wave).toFloat(), leftZ.toFloat(), yaw, 0.0, 0f, d.toFloat() * 0.05f)
            putVertex(data, rightX.toFloat(), (waterY + wave).toFloat(), rightZ.toFloat(), yaw, 0.0, 1f, d.toFloat() * 0.05f)
        }
        data.flip()
        vb!!.setBufferAt(engine, 0, data)
        lastCenter = centerDistance
    }

    private fun findMaterial(): MaterialInstance? {
        val names = arrayOf("Water", "River", "Ocean", "Ground", "Terrain", "Grass", "Landscape", "ground", "terrain")
        val rm = engine.renderableManager
        for (name in names) {
            val e = asset.getFirstEntityByName(name)
            if (e == 0 || !rm.hasComponent(e)) continue
            val instance = rm.getInstance(e)
            if (rm.getPrimitiveCount(instance) > 0) return rm.getMaterialInstanceAt(instance, 0)
        }
        return null
    }

    private fun putVertex(data: ByteBuffer, x: Float, y: Float, z: Float, yaw: Double, bank: Double, u: Float, v: Float) {
        data.putFloat(x); data.putFloat(y); data.putFloat(z)
        val hy = yaw * 0.5; val hb = bank * 0.5
        val sy = sin(hy); val cy = cos(hy); val sz = sin(hb); val cz = cos(hb)
        data.putFloat((cy * sz).toFloat()); data.putFloat((sy * sz).toFloat())
        data.putFloat((sy * cz).toFloat()); data.putFloat((cy * cz).toFloat())
        data.putFloat(u); data.putFloat(v)
    }

    private fun stableSeed(distance: Double): Long {
        var x = kotlin.math.floor(distance / 24.0).toLong() * -7046029254386353131L
        x = x xor (x ushr 30)
        x *= -4658895280553007687L
        x = x xor (x ushr 27)
        return x
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
        material?.let { engine.destroyMaterialInstance(it) }
        vb = null; ib = null; material = null; lastCenter = Double.NaN
    }
}