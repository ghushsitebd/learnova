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
import kotlin.math.floor
import kotlin.math.sin

/**
 * Optional PBR road-paint mesh: continuous edge lines and a dashed two-way
 * centre divider. It uses a paint material already authored in the world GLB;
 * if none exists, it safely disables itself rather than drawing grass/asphalt
 * and pretending that is paint.
 */
internal class LaneMarkingMesh(
    private val engine: Engine,
    private val scene: Scene,
    private val asset: FilamentAsset
) {
    private companion object {
        const val STEP = 3.25
        const val BEHIND = 80.0
        const val SAMPLE_COUNT = 191
        const val MAX_QUADS = (SAMPLE_COUNT - 1) * 3
        const val VERTICES = MAX_QUADS * 4
        const val INDICES = MAX_QUADS * 6
        const val STRIDE = 9 * 4
        const val EDGE_WIDTH = 0.10
        const val CENTRE_WIDTH = 0.12
        const val DASH_PERIOD = 8.0
        const val DASH_LENGTH = 3.0
    }

    private var entity = 0
    private var vertexBuffer: VertexBuffer? = null
    private var indexBuffer: IndexBuffer? = null
    private var material: com.google.android.filament.MaterialInstance? = null
    private var lastCenter = Double.NaN
    private val vertices = ByteBuffer.allocate(VERTICES * STRIDE).order(ByteOrder.nativeOrder())
    private val indices = ByteBuffer.allocate(INDICES * 2).order(ByteOrder.nativeOrder())

    fun build(): Boolean {
        if (entity != 0) return true
        material = findPaintMaterial() ?: return false
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
        entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .material(0, material!!)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
            .culling(false)
            .receiveShadows(false)
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
        val ib = indexBuffer ?: return
        if (entity == 0) return
        if (!lastCenter.isNaN() && kotlin.math.abs(centerDistance - lastCenter) < 4.0) return

        vertices.clear()
        indices.clear()
        var quadCount = 0
        val start = centerDistance - BEHIND

        fun addQuad(
            d0: Double, side0: Double, y0: Double,
            d1: Double, side1: Double, y1: Double,
            halfWidth: Double
        ) {
            if (quadCount >= MAX_QUADS) return
            val s0 = RoadSpline.sampleRelative(d0, centerDistance)
            val s1 = RoadSpline.sampleRelative(d1, centerDistance)
            val yaw0 = s0.yaw.toDouble()
            val yaw1 = s1.yaw.toDouble()
            val leftX0 = s0.x + cos(yaw0) * (side0 + halfWidth)
            val leftZ0 = s0.z - sin(yaw0) * (side0 + halfWidth)
            val rightX0 = s0.x + cos(yaw0) * (side0 - halfWidth)
            val rightZ0 = s0.z - sin(yaw0) * (side0 - halfWidth)
            val leftX1 = s1.x + cos(yaw1) * (side1 + halfWidth)
            val leftZ1 = s1.z - sin(yaw1) * (side1 + halfWidth)
            val rightX1 = s1.x + cos(yaw1) * (side1 - halfWidth)
            val rightZ1 = s1.z - sin(yaw1) * (side1 - halfWidth)
            val lift0 = sin(s0.bank.toDouble()) * side0
            val lift1 = sin(s1.bank.toDouble()) * side1
            putVertex(vertices, leftX0, y0 + lift0, leftZ0, yaw0, s0.bank.toDouble(), 0f, d0.toFloat() / 4f)
            putVertex(vertices, rightX0, y0 + lift0, rightZ0, yaw0, s0.bank.toDouble(), 1f, d0.toFloat() / 4f)
            putVertex(vertices, leftX1, y1 + lift1, leftZ1, yaw1, s1.bank.toDouble(), 0f, d1.toFloat() / 4f)
            putVertex(vertices, rightX1, y1 + lift1, rightZ1, yaw1, s1.bank.toDouble(), 1f, d1.toFloat() / 4f)
            val base = quadCount * 4
            indices.putShort(base.toShort()); indices.putShort((base + 2).toShort()); indices.putShort((base + 1).toShort())
            indices.putShort((base + 1).toShort()); indices.putShort((base + 2).toShort()); indices.putShort((base + 3).toShort())
            quadCount++
        }

        for (i in 0 until SAMPLE_COUNT - 1) {
            val d0 = start + i * STEP
            val d1 = d0 + STEP
            val mid = (d0 + d1) * 0.5
            val biome = WorldDirector.profile(mid).biome
            val width = when (biome) {
                WorldDirector.Biome.MARKET, WorldDirector.Biome.VILLAGE -> 7.8
                WorldDirector.Biome.MOUNTAIN -> 6.8
                WorldDirector.Biome.DESERT -> 7.4
                WorldDirector.Biome.RIVER, WorldDirector.Biome.COAST -> 7.5
                else -> 7.2
            }
            val half = width * 0.5
            val s0 = RoadSpline.sampleRelative(d0, centerDistance)
            val s1 = RoadSpline.sampleRelative(d1, centerDistance)
            val edgeY0 = s0.y - 0.04 + 0.012
            val edgeY1 = s1.y - 0.04 + 0.012
            // Solid shoulder-side edge lines help communicate the road boundary.
            addQuad(d0, -(half - 0.14), edgeY0, d1, -(half - 0.14), edgeY1, EDGE_WIDTH * 0.5)
            addQuad(d0, half - 0.14, edgeY0, d1, half - 0.14, edgeY1, EDGE_WIDTH * 0.5)

            // Dash placement is tied to world distance, not the sliding mesh origin,
            // so stripes do not visibly jump when the render window advances.
            val phase = ((d0 % DASH_PERIOD) + DASH_PERIOD) % DASH_PERIOD
            if (phase < DASH_LENGTH) {
                val dashEnd = minOf(d1, d0 + DASH_LENGTH - phase)
                if (dashEnd > d0 + 0.05) {
                    val centreEnd = RoadSpline.sampleRelative(dashEnd, centerDistance)
                    addQuad(d0, 0.0, s0.y + 0.010, dashEnd, 0.0, centreEnd.y + 0.010, CENTRE_WIDTH * 0.5)
                }
            }
        }

        // Keep fixed-size GPU buffers fully initialized. Unused triangles collapse
        // to vertex zero and therefore cost no visible geometry.
        while (quadCount < MAX_QUADS) {
            // Each vertex is 9 floats (POSITION float3 + TANGENTS float4 + UV0 float2).
            // Initialize all four vertices, not just 14 floats, so the fixed-size
            // staging buffer is fully populated before it is uploaded to Filament.
            repeat(4) {
                vertices.putFloat(0f) // position x
                vertices.putFloat(0f) // position y
                vertices.putFloat(0f) // position z
                vertices.putFloat(0f) // tangent x
                vertices.putFloat(0f) // tangent y
                vertices.putFloat(0f) // tangent z
                vertices.putFloat(1f) // tangent w
                vertices.putFloat(0f) // uv u
                vertices.putFloat(0f) // uv v
            }
            val base = quadCount * 4
            repeat(6) { indices.putShort(base.toShort()) }
            quadCount++
        }
        vertices.flip()
        indices.flip()
        vb.setBufferAt(engine, 0, vertices)
        ib.setBuffer(engine, indices)
        lastCenter = centerDistance
    }

    private fun findPaintMaterial(): com.google.android.filament.MaterialInstance? {
        val names = arrayOf(
            "RoadMarking", "RoadMarkings", "LaneMarking", "LaneLine",
            "RoadLine", "WhitePaint", "RoadPaint", "Marking",
            "road_marking", "lane_line", "road_line"
        )
        val rm = engine.renderableManager
        for (name in names) {
            val candidate = asset.getFirstEntityByName(name)
            if (candidate == 0 || !rm.hasComponent(candidate)) continue
            val instance = rm.getInstance(candidate)
            if (rm.getPrimitiveCount(instance) > 0) {
                return rm.getMaterialInstanceAt(instance, 0)
            }
        }
        return null
    }

    private fun putVertex(
        data: ByteBuffer, x: Double, y: Double, z: Double,
        yaw: Double, bank: Double, u: Float, v: Float
    ) {
        data.putFloat(x.toFloat()); data.putFloat(y.toFloat()); data.putFloat(z.toFloat())
        val hy = yaw * 0.5
        val hb = bank * 0.5
        val sy = sin(hy); val cy = cos(hy); val sb = sin(hb); val cb = cos(hb)
        data.putFloat((cy * sb).toFloat())
        data.putFloat((sy * sb).toFloat())
        data.putFloat((sy * cb).toFloat())
        data.putFloat((cy * cb).toFloat())
        data.putFloat(u); data.putFloat(v)
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
