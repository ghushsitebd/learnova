package com.learnova.app

import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.VertexBuffer
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.EntityManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural mobile road surface driven by the same RoadSpline used by the
 * vehicle and camera. The mesh is a sliding window, so the world can continue
 * indefinitely without allocating an infinite road.
 *
 * The material is reused from the authored GLB road when one of the supported
 * road node names is present. This keeps the APK small and preserves the asset's
 * PBR material instead of adding a second runtime shader/compiler.
 */
internal class ProceduralRoadMesh(
    private val engine: Engine,
    private val scene: Scene,
    private val asset: FilamentAsset
) {
    private companion object {
        const val ROAD_WIDTH = 7.2f
        const val SAMPLE_STEP = 3.25
        const val BEHIND = 80.0
        const val VERTEX_COUNT = 2 * 127
        const val INDEX_COUNT = 6 * 126
        const val VERTEX_STRIDE = 9 * 4
    }

    private var entity = 0
    private var vertexBuffer: VertexBuffer? = null
    private var indexBuffer: IndexBuffer? = null
    private var lastCenter = Double.NaN
    private var sourceRoadEntity = 0
    private var renderOriginDistance = 0.0

    fun build(): Boolean {
        if (entity != 0) return true

        val material = findRoadMaterial() ?: return false

        val vb = VertexBuffer.Builder()
            .vertexCount(VERTEX_COUNT)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, VERTEX_STRIDE)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, VERTEX_STRIDE)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 28, VERTEX_STRIDE)
            .build(engine)

        val ib = IndexBuffer.Builder()
            .indexCount(INDEX_COUNT)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)

        val indices = ByteBuffer.allocate(INDEX_COUNT * 2)
            .order(ByteOrder.nativeOrder())
        for (i in 0 until 126) {
            val a = (i * 2)
            val b = a + 1
            val c = a + 2
            val d = a + 3
            indices.putShort(a.toShort())
            indices.putShort(c.toShort())
            indices.putShort(b.toShort())
            indices.putShort(b.toShort())
            indices.putShort(c.toShort())
            indices.putShort(d.toShort())
        }
        indices.flip()
        ib.setBuffer(engine, indices)

        entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .material(0, material)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
            .culling(false)
            .receiveShadows(true)
            .castShadows(false)
            .build(engine, entity)

        if (sourceRoadEntity != 0) scene.removeEntity(sourceRoadEntity)
        scene.addEntity(entity)
        vertexBuffer = vb
        indexBuffer = ib

        update(0.0)
        return true
    }

    fun update(centerDistance: Double) {
        if (entity == 0 || vertexBuffer == null) return
        if (!lastCenter.isNaN() && kotlin.math.abs(centerDistance - lastCenter) < 4.0) return

        val start = centerDistance - BEHIND
        val data = ByteBuffer.allocate(VERTEX_COUNT * VERTEX_STRIDE)
            .order(ByteOrder.nativeOrder())

        for (i in 0 until 127) {
            val distance = start + i * SAMPLE_STEP
            val sample = RoadSpline.sampleRelative(distance, centerDistance)
            val yaw = sample.yaw.toDouble()
            val bank = sample.bank.toDouble()

            val half = ROAD_WIDTH * 0.5
            val lateralX = cos(yaw) * half
            val lateralZ = -sin(yaw) * half

            val leftX = sample.x + lateralX
            val leftZ = sample.z + lateralZ
            val rightX = sample.x - lateralX
            val rightZ = sample.z - lateralZ

            val bankLift = sin(bank) * half

            // Keep the rendered road on the same vertical spline used by
            // the vehicle and camera. This prevents visual separation on crests
            // and dips while preserving the existing lightweight sliding mesh.
            val centerY = sample.y
            putVertex(data, leftX.toFloat(), (centerY + bankLift).toFloat(), leftZ.toFloat(), yaw, bank, 0.0f, distance.toFloat() / 8.0f)
            putVertex(data, rightX.toFloat(), (centerY - bankLift).toFloat(), rightZ.toFloat(), yaw, bank, 1.0f, distance.toFloat() / 8.0f)
        }

        data.flip()
        vertexBuffer?.setBufferAt(engine, 0, data)
        lastCenter = centerDistance
    }

    private fun putVertex(
        data: ByteBuffer,
        x: Float,
        y: Float,
        z: Float,
        yaw: Double,
        bank: Double,
        u: Float,
        v: Float
    ) {
        data.putFloat(x)
        data.putFloat(y)
        data.putFloat(z)

        // Quaternion for the road's tangent frame. The authored road material
        // can consume TANGENTS even when it does not use a normal map.
        val halfYaw = yaw * 0.5
        val halfBank = bank * 0.5
        val sy = sin(halfYaw)
        val cy = cos(halfYaw)
        val sz = sin(halfBank)
        val cz = cos(halfBank)

        val qx = cy * sz
        val qy = sy * sz
        val qz = sy * cz
        val qw = cy * cz

        data.putFloat(qx.toFloat())
        data.putFloat(qy.toFloat())
        data.putFloat(qz.toFloat())
        data.putFloat(qw.toFloat())

        data.putFloat(u)
        data.putFloat(v)
    }

    private fun findRoadMaterial(): com.google.android.filament.MaterialInstance? {
        val names = arrayOf("Road", "RoadMesh", "RoadSurface", "road", "road_mesh")
        for (name in names) {
            val roadEntity = asset.getFirstEntityByName(name)
            if (roadEntity == 0) continue
            val rm = engine.renderableManager
            if (!rm.hasComponent(roadEntity)) continue
            val instance = rm.getInstance(roadEntity)
            if (rm.getPrimitiveCount(instance) <= 0) continue
            sourceRoadEntity = roadEntity
            return rm.getMaterialInstanceAt(instance, 0)
        }
        return null
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
        lastCenter = Double.NaN
        sourceRoadEntity = 0
        renderOriginDistance = 0.0
    }
}
