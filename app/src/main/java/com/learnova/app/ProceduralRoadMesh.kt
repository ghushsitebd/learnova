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
        const val ROAD_WIDTH = 12.8f
        const val SAMPLE_STEP = 3.25
        const val BEHIND = 80.0
        const val SAMPLE_COUNT = 191
        const val VERTEX_COUNT = 2 * SAMPLE_COUNT
        const val INDEX_COUNT = 6 * (SAMPLE_COUNT - 1)
        const val VERTEX_STRIDE = 9 * 4
    }

    private var entity = 0
    private var vertexBuffer: VertexBuffer? = null
    private var indexBuffer: IndexBuffer? = null
    private var lastCenter = Double.NaN
    private var sourceRoadEntity = 0
    private var shoulderEntity = 0
    private var shoulderVertexBuffer: VertexBuffer? = null
    private var shoulderIndexBuffer: IndexBuffer? = null
    private var shoulderMaterial: com.google.android.filament.MaterialInstance? = null

    // Reused CPU staging buffers keep the sliding road window allocation-free
    // during normal driving. This is important on low-memory Android phones.
    private val roadVertexData = ByteBuffer.allocate(VERTEX_COUNT * VERTEX_STRIDE)
        .order(ByteOrder.nativeOrder())
    private val shoulderVertexData = ByteBuffer.allocate(VERTEX_COUNT * 2 * VERTEX_STRIDE)
        .order(ByteOrder.nativeOrder())

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
        for (i in 0 until SAMPLE_COUNT - 1) {
            val a = i * 2
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

        shoulderMaterial = findShoulderMaterial()
        buildShoulders()
        update(0.0)
        return true
    }

    fun update(centerDistance: Double) {
        if (entity == 0 || vertexBuffer == null) return
        if (!lastCenter.isNaN() && kotlin.math.abs(centerDistance - lastCenter) < 4.0) return

        val start = centerDistance - BEHIND
        val data = roadVertexData.apply { clear() }

        for (i in 0 until SAMPLE_COUNT) {
            val distance = start + i * SAMPLE_STEP
            val sample = RoadSpline.sampleRelative(distance, centerDistance)
            val yaw = sample.yaw.toDouble()
            val bank = sample.bank.toDouble()

            val roadWidth = when (WorldDirector.profile(distance).biome) {
                WorldDirector.Biome.MARKET, WorldDirector.Biome.VILLAGE -> 7.8f
                WorldDirector.Biome.MOUNTAIN -> 6.8f
                WorldDirector.Biome.DESERT -> 7.4f
                WorldDirector.Biome.RIVER, WorldDirector.Biome.COAST -> 7.5f
                else -> ROAD_WIDTH
            }
            val half = roadWidth * 0.5f
            val lateralX = cos(yaw) * half
            val lateralZ = -sin(yaw) * half

            val leftX = sample.x + lateralX
            val leftZ = sample.z + lateralZ
            val rightX = sample.x - lateralX
            val rightZ = sample.z - lateralZ

            val bankLift = sin(bank) * half

            val centerY = sample.y
            // Real asphalt is crowned: the lane centre sits slightly higher than
            // both edges so rain sheds away from the driving line instead of forming
            // a perfectly flat mathematical strip. Keep the crown subtle enough for
            // child-friendly driving while preserving the authored road material.
            val crownDrop = when (WorldDirector.profile(distance).biome) {
                WorldDirector.Biome.MARKET, WorldDirector.Biome.VILLAGE -> 0.030
                WorldDirector.Biome.MOUNTAIN -> 0.042
                WorldDirector.Biome.RIVER, WorldDirector.Biome.COAST -> 0.050
                else -> 0.040
            }
            val edgeY = centerY - crownDrop
            putVertex(data, leftX.toFloat(), (edgeY + bankLift).toFloat(), leftZ.toFloat(), yaw, bank, 0.0f, distance.toFloat() / 8.0f)
            putVertex(data, rightX.toFloat(), (edgeY - bankLift).toFloat(), rightZ.toFloat(), yaw, bank, 1.0f, distance.toFloat() / 8.0f)
        }

        data.flip()
        vertexBuffer?.setBufferAt(engine, 0, data)
        updateShoulders(centerDistance)
        lastCenter = centerDistance
    }

    private fun buildShoulders() {
        val material = shoulderMaterial ?: return
        if (shoulderEntity != 0) return

        val vb = VertexBuffer.Builder()
            .vertexCount(VERTEX_COUNT * 2)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, VERTEX_STRIDE)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, VERTEX_STRIDE)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 28, VERTEX_STRIDE)
            .build(engine)

        val ib = IndexBuffer.Builder()
            .indexCount(INDEX_COUNT * 2)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)

        val indices = ByteBuffer.allocate(INDEX_COUNT * 4)
            .order(ByteOrder.nativeOrder())
        for (i in 0 until 126) {
            val a = i * 4
            val b = a + 1
            val c = a + 4
            val d = a + 5
            indices.putShort(a.toShort()); indices.putShort(c.toShort()); indices.putShort(b.toShort())
            indices.putShort(b.toShort()); indices.putShort(c.toShort()); indices.putShort(d.toShort())

            val off = 254
            val e = off + a
            val f = off + b
            val g = off + c
            val h = off + d
            indices.putShort(e.toShort()); indices.putShort(f.toShort()); indices.putShort(g.toShort())
            indices.putShort(f.toShort()); indices.putShort(h.toShort()); indices.putShort(g.toShort())
        }
        indices.flip()
        ib.setBuffer(engine, indices)

        shoulderEntity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .material(0, material)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
            .culling(false)
            .receiveShadows(true)
            .castShadows(false)
            .build(engine, shoulderEntity)
        scene.addEntity(shoulderEntity)
        shoulderVertexBuffer = vb
        shoulderIndexBuffer = ib
    }

    private fun updateShoulders(centerDistance: Double) {
        if (shoulderEntity == 0 || shoulderVertexBuffer == null) return
        val data = shoulderVertexData.apply { clear() }

        for (i in 0 until 127) {
            val distance = centerDistance - BEHIND + i * SAMPLE_STEP
            val sample = RoadSpline.sampleRelative(distance, centerDistance)
            val yaw = sample.yaw.toDouble()
            val halfRoad = ROAD_WIDTH * 0.5
            // Real roads do not have a perfectly uniform verge. Widen the
            // usable shoulder slightly in settlements and flatten it near water,
            // while keeping the driving lane unchanged.
            val biome = WorldDirector.profile(distance).biome
            val shoulder = when (biome) {
                WorldDirector.Biome.MARKET, WorldDirector.Biome.VILLAGE -> 3.0
                WorldDirector.Biome.RIVER, WorldDirector.Biome.COAST -> 2.7
                WorldDirector.Biome.MOUNTAIN -> 2.2
                WorldDirector.Biome.DESERT -> 2.5
                else -> 2.4
            }
            val lx = cos(yaw)
            val lz = -sin(yaw)
            val outerOffset = halfRoad + shoulder
            val outerLeftX = sample.x + lx * outerOffset
            val outerLeftZ = sample.z + lz * outerOffset
            val innerLeftX = sample.x + lx * halfRoad
            val innerLeftZ = sample.z + lz * halfRoad
            val innerRightX = sample.x - lx * halfRoad
            val innerRightZ = sample.z - lz * halfRoad
            val outerRightX = sample.x - lx * outerOffset
            val outerRightZ = sample.z - lz * outerOffset

            // Carry the road's cross-slope into the verge. A real road does not
            // suddenly become flat at the edge; this also keeps the shoulder from
            // visually floating on banked curves.
            val innerLift = sin(sample.bank.toDouble()) * halfRoad
            val outerLift = sin(sample.bank.toDouble()) * outerOffset
            // A shallow drainage fall makes the edge read like a real roadside
            // verge instead of a mathematically flat green strip.
            val drainage = when (biome) {
                WorldDirector.Biome.RIVER, WorldDirector.Biome.COAST -> 0.055
                WorldDirector.Biome.MARKET, WorldDirector.Biome.VILLAGE -> 0.035
                else -> 0.045
            }

            putVertex(data, outerLeftX.toFloat(), (sample.y + outerLift - drainage).toFloat(), outerLeftZ.toFloat(), yaw, sample.bank.toDouble(), 0f, distance.toFloat() / 8f)
            putVertex(data, innerLeftX.toFloat(), (sample.y + innerLift - 0.012).toFloat(), innerLeftZ.toFloat(), yaw, sample.bank.toDouble(), 1f, distance.toFloat() / 8f)
            putVertex(data, innerRightX.toFloat(), (sample.y - innerLift - 0.012).toFloat(), innerRightZ.toFloat(), yaw, sample.bank.toDouble(), 0f, distance.toFloat() / 8f)
            putVertex(data, outerRightX.toFloat(), (sample.y - outerLift - 0.018).toFloat(), outerRightZ.toFloat(), yaw, sample.bank.toDouble(), 1f, distance.toFloat() / 8f)
        }
        data.flip()
        shoulderVertexBuffer?.setBufferAt(engine, 0, data)
    }

    private fun findShoulderMaterial(): com.google.android.filament.MaterialInstance? {
        val names = arrayOf(
            "Ground", "Terrain", "Grass", "Landscape", "ground", "terrain", "grass"
        )
        val rm = engine.renderableManager
        for (name in names) {
            val entity = asset.getFirstEntityByName(name)
            if (entity == 0 || !rm.hasComponent(entity)) continue
            val instance = rm.getInstance(entity)
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
        v: Float
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
        if (shoulderEntity != 0) {
            scene.removeEntity(shoulderEntity)
            engine.renderableManager.destroy(shoulderEntity)
            EntityManager.get().destroy(shoulderEntity)
            shoulderEntity = 0
        }
        vertexBuffer?.let { engine.destroyVertexBuffer(it) }
        shoulderVertexBuffer?.let { engine.destroyVertexBuffer(it) }
        indexBuffer?.let { engine.destroyIndexBuffer(it) }
        shoulderIndexBuffer?.let { engine.destroyIndexBuffer(it) }
        vertexBuffer = null
        indexBuffer = null
        shoulderVertexBuffer = null
        shoulderIndexBuffer = null
        shoulderMaterial = null
        lastCenter = Double.NaN
        sourceRoadEntity = 0
    }
}
