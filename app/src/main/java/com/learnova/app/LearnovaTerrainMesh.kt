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
 * Lightweight terrain ribbon surrounding the procedural road.
 *
 * It reuses the GLB's existing ground/terrain material, so Learnova gains a
 * continuous natural landscape without embedding a large environment pack.
 * The ribbon is regenerated as a sliding window and uses deterministic
 * low-frequency height variation to avoid a perfectly flat roadside.
 */
internal class LearnovaTerrainMesh(
    private val engine: Engine,
    private val scene: Scene,
    private val asset: FilamentAsset
) {
    private companion object {
        const val SAMPLE_COUNT = 127
        const val STEP = 3.25
        const val BEHIND = 80.0
        const val INNER = 6.0
        const val OUTER = 34.0
        const val VERTICES = SAMPLE_COUNT * 4
        const val INDICES = (SAMPLE_COUNT - 1) * 12
        const val STRIDE = 9 * 4
    }

    private var entity = 0
    private var vertexBuffer: VertexBuffer? = null
    private var indexBuffer: IndexBuffer? = null
    private var material: com.google.android.filament.MaterialInstance? = null
    private var lastCenter = Double.NaN

    fun build(): Boolean {
        if (entity != 0) return true
        material = findTerrainMaterial() ?: return false

        val vb = VertexBuffer.Builder()
            .vertexCount(VERTICES)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 28, STRIDE)
            .build(engine)

        val ib = IndexBuffer.Builder()
            .indexCount(INDICES)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)

        val indices = ByteBuffer.allocate(INDICES * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until SAMPLE_COUNT - 1) {
            val base = i * 4
            val next = base + 4

            // Left ribbon: inner -> outer.
            indices.putShort(base.toShort())
            indices.putShort(next.toShort())
            indices.putShort((base + 1).toShort())
            indices.putShort((base + 1).toShort())
            indices.putShort(next.toShort())
            indices.putShort((next + 1).toShort())

            // Right ribbon: inner -> outer.
            val r = base + 2
            val rn = next + 2
            indices.putShort(r.toShort())
            indices.putShort((r + 1).toShort())
            indices.putShort(rn.toShort())
            indices.putShort((r + 1).toShort())
            indices.putShort(rn.toShort())
            indices.putShort((rn + 1).toShort())
        }
        indices.flip()
        ib.setBuffer(engine, indices)

        entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .material(0, material!!)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
            .culling(false)
            .receiveShadows(true)
            .castShadows(false)
            .build(engine, entity)
        scene.addEntity(entity)

        vertexBuffer = vb
        indexBuffer = ib
        update(0.0)
        return true
    }

    fun update(centerDistance: Double) {
        val vb = vertexBuffer ?: return
        if (entity == 0) return
        if (!lastCenter.isNaN() && kotlin.math.abs(centerDistance - lastCenter) < 4.0) return

        val data = ByteBuffer.allocate(VERTICES * STRIDE).order(ByteOrder.nativeOrder())
        val start = centerDistance - BEHIND

        for (i in 0 until SAMPLE_COUNT) {
            val distance = start + i * STEP
            val sample = RoadSpline.sampleRelative(distance, centerDistance)
            val yaw = sample.yaw.toDouble()
            val bank = sample.bank.toDouble()
            val leftX = cos(yaw)
            val leftZ = -sin(yaw)

            val innerLeftX = sample.x + leftX * INNER
            val innerLeftZ = sample.z + leftZ * INNER
            val outerLeftX = sample.x + leftX * OUTER
            val outerLeftZ = sample.z + leftZ * OUTER
            val innerRightX = sample.x - leftX * INNER
            val innerRightZ = sample.z - leftZ * INNER
            val outerRightX = sample.x - leftX * OUTER
            val outerRightZ = sample.z - leftZ * OUTER

            val wave = sin(distance * 0.075) * 0.13 + cos(distance * 0.021) * 0.20
            val cross = sin(distance * 0.11) * 0.05
            val leftBankInner = sin(bank) * INNER
            val leftBankOuter = sin(bank) * OUTER

            putVertex(data, innerLeftX, sample.y + leftBankInner - 0.03 + wave, innerLeftZ, yaw, bank, 0f, distance / 8.0)
            putVertex(data, outerLeftX, sample.y + leftBankOuter + wave + cross, outerLeftZ, yaw, bank, 1f, distance / 8.0)
            putVertex(data, innerRightX, sample.y - leftBankInner - 0.03 + wave, innerRightZ, yaw, bank, 0f, distance / 8.0)
            putVertex(data, outerRightX, sample.y - leftBankOuter + wave - cross, outerRightZ, yaw, bank, 1f, distance / 8.0)
        }

        data.flip()
        vb.setBufferAt(engine, 0, data)
        lastCenter = centerDistance
    }

    private fun findTerrainMaterial(): com.google.android.filament.MaterialInstance? {
        val names = arrayOf(
            "Ground", "Terrain", "Grass", "Landscape",
            "ground", "terrain", "grass"
        )
        val rm = engine.renderableManager
        for (name in names) {
            val e = asset.getFirstEntityByName(name)
            if (e == 0 || !rm.hasComponent(e)) continue
            val instance = rm.getInstance(e)
            if (rm.getPrimitiveCount(instance) > 0) {
                return rm.getMaterialInstanceAt(instance, 0)
            }
        }
        return null
    }

    private fun putVertex(
        data: ByteBuffer,
        x: Float,
        y: Float,
        z: Float,
        yaw: Double,
        bank: Double,
        u: Float,
        v: Double
    ) {
        data.putFloat(x)
        data.putFloat(y)
        data.putFloat(z)

        val halfYaw = yaw * 0.5
        val halfBank = bank * 0.5
        val sy = sin(halfYaw)
        val cy = cos(halfYaw)
        val sz = sin(halfBank)
        val cz = cos(halfBank)
        data.putFloat((cy * sz).toFloat())
        data.putFloat((sy * sz).toFloat())
        data.putFloat((sy * cz).toFloat())
        data.putFloat((cy * cz).toFloat())
        data.putFloat(u)
        data.putFloat(v.toFloat())
    }

    fun destroy() {
        if (entity != 0) {
            scene.removeEntity(entity)
            engine.renderableManager.destroy(entity)
            EntityManager.get().destroy(entity)
            entity = 0
        }
        vertexBuffer?.let { engine.destroyVertexBuffer(it) }
        indexBuffer?.let { engine.destroyIndexBuffer(it) }
        vertexBuffer = null
        indexBuffer = null
        material = null
        lastCenter = Double.NaN
    }
}
