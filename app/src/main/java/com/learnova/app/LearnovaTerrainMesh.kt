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
        const val SAMPLE_COUNT = 191
        const val STEP = 3.25
        const val BEHIND = 80.0
        const val INNER = 6.0
        const val OUTER = 34.0
        const val VERTICES = SAMPLE_COUNT * 4
        const val INDICES = (SAMPLE_COUNT - 1) * 12
        const val STRIDE = 9 * 4
        // Low-frequency outer terrain LOD extends the visible 3D landscape
        // without multiplying the detail density of the near driving corridor.
        const val FAR_SAMPLE_COUNT = 73
        const val FAR_STEP = 8.0
        const val FAR_INNER = 34.0
        const val FAR_OUTER = 112.0
        const val FAR_VERTICES = FAR_SAMPLE_COUNT * 4
        const val FAR_INDICES = (FAR_SAMPLE_COUNT - 1) * 12
    }

    private var entity = 0
    private var vertexBuffer: VertexBuffer? = null
    private var indexBuffer: IndexBuffer? = null
    private var farEntity = 0
    private var farVertexBuffer: VertexBuffer? = null
    private var farIndexBuffer: IndexBuffer? = null
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
        buildFarHorizon()
        update(0.0)
        return true
    }

    /**
     * Low-LOD outer landscape. The near ribbon carries the detailed road-side
     * surface; this larger ring prevents the world from ending abruptly in the
     * middle distance and keeps mountains/forest terrain visually connected.
     */
    private fun buildFarHorizon() {
        val mat = material ?: return
        if (farEntity != 0) return

        val vb = VertexBuffer.Builder()
            .vertexCount(FAR_VERTICES)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 28, STRIDE)
            .build(engine)

        val ib = IndexBuffer.Builder()
            .indexCount(FAR_INDICES)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)

        val indices = ByteBuffer.allocate(FAR_INDICES * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until FAR_SAMPLE_COUNT - 1) {
            val base = i * 4
            val next = base + 4
            indices.putShort(base.toShort()); indices.putShort(next.toShort()); indices.putShort((base + 1).toShort())
            indices.putShort((base + 1).toShort()); indices.putShort(next.toShort()); indices.putShort((next + 1).toShort())

            val r = base + 2
            val rn = next + 2
            indices.putShort(r.toShort()); indices.putShort((r + 1).toShort()); indices.putShort(rn.toShort())
            indices.putShort((r + 1).toShort()); indices.putShort((rn + 1).toShort()); indices.putShort(rn.toShort())
        }
        indices.flip()
        ib.setBuffer(engine, indices)

        farEntity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .material(0, mat)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
            .culling(false)
            .receiveShadows(true)
            .castShadows(false)
            .build(engine, farEntity)
        scene.addEntity(farEntity)

        farVertexBuffer = vb
        farIndexBuffer = ib
    }

    private fun updateFarHorizon(centerDistance: Double) {
        val vb = farVertexBuffer ?: return
        if (farEntity == 0) return

        val data = ByteBuffer.allocate(FAR_VERTICES * STRIDE).order(ByteOrder.nativeOrder())
        val start = centerDistance
        for (i in 0 until FAR_SAMPLE_COUNT) {
            val distance = start + i * FAR_STEP
            val sample = RoadSpline.sampleRelative(distance, centerDistance)
            val yaw = sample.yaw.toDouble()
            val bank = sample.bank.toDouble()
            val lx = cos(yaw)
            val lz = -sin(yaw)
            val biome = WorldDirector.profile(distance).biome

            val shape = when (biome) {
                WorldDirector.Biome.FOREST -> sin(distance * 0.025) * 0.75 + cos(distance * 0.008) * 1.10
                WorldDirector.Biome.MOUNTAIN -> sin(distance * 0.016) * 1.65 + cos(distance * 0.006) * 1.20
                WorldDirector.Biome.DESERT -> sin(distance * 0.020) * 0.90 + cos(distance * 0.007) * 0.65
                WorldDirector.Biome.PLATEAU -> sin(distance * 0.014) * 0.70 + cos(distance * 0.005) * 0.55
                WorldDirector.Biome.RIVER, WorldDirector.Biome.COAST -> sin(distance * 0.018) * 0.35 + cos(distance * 0.006) * 0.45
                else -> sin(distance * 0.022) * 0.45 + cos(distance * 0.007) * 0.35
            }

            val innerLeftX = sample.x + lx * FAR_INNER
            val innerLeftZ = sample.z + lz * FAR_INNER
            val outerLeftX = sample.x + lx * FAR_OUTER
            val outerLeftZ = sample.z + lz * FAR_OUTER
            val innerRightX = sample.x - lx * FAR_INNER
            val innerRightZ = sample.z - lz * FAR_INNER
            val outerRightX = sample.x - lx * FAR_OUTER
            val outerRightZ = sample.z - lz * FAR_OUTER

            val innerLift = sin(bank) * FAR_INNER
            val outerLift = sin(bank) * FAR_OUTER

            putVertex(data, innerLeftX.toFloat(), (sample.y + innerLift + shape).toFloat(), innerLeftZ.toFloat(), yaw, bank, 0f, distance / 32.0)
            putVertex(data, outerLeftX.toFloat(), (sample.y + outerLift + shape).toFloat(), outerLeftZ.toFloat(), yaw, bank, 1f, distance / 32.0)
            putVertex(data, innerRightX.toFloat(), (sample.y - innerLift + shape).toFloat(), innerRightZ.toFloat(), yaw, bank, 0f, distance / 32.0)
            putVertex(data, outerRightX.toFloat(), (sample.y - outerLift + shape).toFloat(), outerRightZ.toFloat(), yaw, bank, 1f, distance / 32.0)
        }
        data.flip()
        vb.setBufferAt(engine, 0, data)
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

            // Biome-aware micro-topography keeps the landscape from looking like
            // one flat repeating plane. The road remains the authoritative surface;
            // only the roadside landform changes by world region.
            val biome = WorldDirector.profile(distance).biome
            val terrainShape = when (biome) {
                WorldDirector.Biome.FOREST ->
                    sin(distance * 0.075) * 0.16 + cos(distance * 0.021) * 0.24
                WorldDirector.Biome.RIVER ->
                    sin(distance * 0.060) * 0.07 + cos(distance * 0.018) * 0.10
                WorldDirector.Biome.MOUNTAIN ->
                    sin(distance * 0.040) * 0.42 + cos(distance * 0.013) * 0.30
                WorldDirector.Biome.DESERT ->
                    sin(distance * 0.055) * 0.28 + cos(distance * 0.019) * 0.16
                WorldDirector.Biome.PLATEAU ->
                    sin(distance * 0.035) * 0.24 + cos(distance * 0.012) * 0.18
                WorldDirector.Biome.MARKET ->
                    sin(distance * 0.090) * 0.055 + cos(distance * 0.025) * 0.08
                WorldDirector.Biome.VILLAGE ->
                    sin(distance * 0.065) * 0.12 + cos(distance * 0.017) * 0.18
                WorldDirector.Biome.COAST ->
                    sin(distance * 0.050) * 0.10 + cos(distance * 0.015) * 0.14
            }
            val sideShape = sin(distance * 0.11) * 0.05
            val leftBankInner = sin(bank) * INNER
            val leftBankOuter = sin(bank) * OUTER

            putVertex(data, innerLeftX.toFloat(), (sample.y + leftBankInner - 0.03 + terrainShape).toFloat(), innerLeftZ.toFloat(), yaw, bank, 0f, distance / 8.0)
            putVertex(data, outerLeftX.toFloat(), (sample.y + leftBankOuter + terrainShape + sideShape).toFloat(), outerLeftZ.toFloat(), yaw, bank, 1f, distance / 8.0)
            putVertex(data, innerRightX.toFloat(), (sample.y - leftBankInner - 0.03 + terrainShape).toFloat(), innerRightZ.toFloat(), yaw, bank, 0f, distance / 8.0)
            putVertex(data, outerRightX.toFloat(), (sample.y - leftBankOuter + terrainShape - sideShape).toFloat(), outerRightZ.toFloat(), yaw, bank, 1f, distance / 8.0)
        }

        data.flip()
        vb.setBufferAt(engine, 0, data)
        updateFarHorizon(centerDistance)
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
        if (farEntity != 0) {
            scene.removeEntity(farEntity)
            engine.renderableManager.destroy(farEntity)
            EntityManager.get().destroy(farEntity)
            farEntity = 0
        }
        farVertexBuffer?.let { engine.destroyVertexBuffer(it) }
        farIndexBuffer?.let { engine.destroyIndexBuffer(it) }
        farVertexBuffer = null
        farIndexBuffer = null

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
