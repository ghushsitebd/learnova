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
 * Distant railway presentation.
 *
 * The railway stays well outside the driving lane and is streamed in the same
 * distance space as the road. Rails, sleepers, embankment and a moving multi-car
 * train are generated as one renderable, so the child can see a believable train
 * passing in the distance without adding a heavy train asset to every level.
 */
internal class DistantRailwayWorld(
    private val engine: Engine,
    private val scene: Scene,
    private val asset: FilamentAsset
) {
    private companion object {
        const val LATERAL = 34.0
        const val TRACK_BEHIND = 70.0
        const val TRACK_AHEAD = 360.0
        const val STEP = 12.0
        const val MAX_BOXES = 72
        const val STRIDE = 36
    }

    private var entity = 0
    private var vb: VertexBuffer? = null
    private var ib: IndexBuffer? = null
    private var material: com.google.android.filament.MaterialInstance? = null
    private var lastCenter = Double.NaN
    private var trainTime = 0.0

    private val vertices = ByteBuffer.allocate(MAX_BOXES * 8 * STRIDE).order(ByteOrder.nativeOrder())
    private val indices = ByteBuffer.allocate(MAX_BOXES * 36 * 2).order(ByteOrder.nativeOrder())

    fun build(): Boolean {
        if (entity != 0) return true
        material = findMaterial() ?: return false
        vb = VertexBuffer.Builder()
            .vertexCount(MAX_BOXES * 8)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 28, STRIDE)
            .build(engine)
        ib = IndexBuffer.Builder()
            .indexCount(MAX_BOXES * 36)
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
        update(0.0, 0.0)
        return true
    }

    fun update(centerDistance: Double, deltaSeconds: Double = 1.0 / 60.0) {
        if (entity == 0 || vb == null || ib == null) return
        trainTime += deltaSeconds.coerceIn(0.0, 0.1)
        if (!lastCenter.isNaN() && kotlin.math.abs(centerDistance - lastCenter) < 4.0) {
            // Keep the train animated even when the road streaming window does not move.
            writeGeometry(centerDistance)
            return
        }
        writeGeometry(centerDistance)
        lastCenter = centerDistance
    }

    private fun writeGeometry(centerDistance: Double) {
        vertices.clear()
        indices.clear()
        var boxCount = 0

        fun addBox(distance: Double, lateral: Double, width: Double, height: Double, depth: Double) {
            if (boxCount >= MAX_BOXES) return
            val sample = RoadSpline.sampleRelative(distance, centerDistance)
            val yaw = sample.yaw.toDouble()
            val cx = cos(yaw)
            val sz = -sin(yaw)
            val sx = sin(yaw)
            val cz = cos(yaw)
            val ground = sample.y.toDouble() + 0.02
            val base = boxCount * 8
            val halfW = width * 0.5
            val halfD = depth * 0.5
            val corners = arrayOf(
                doubleArrayOf(-halfW, 0.0, -halfD), doubleArrayOf(halfW, 0.0, -halfD),
                doubleArrayOf(halfW, 0.0, halfD), doubleArrayOf(-halfW, 0.0, halfD),
                doubleArrayOf(-halfW, height, -halfD), doubleArrayOf(halfW, height, -halfD),
                doubleArrayOf(halfW, height, halfD), doubleArrayOf(-halfW, height, halfD)
            )
            for (p in corners) {
                val lx = p[0]
                val lz = p[2]
                putVertex(
                    (sample.x + cx * lateral + cx * lz + sx * lx).toFloat(),
                    (ground + p[1] + kotlin.math.sin(sample.bank.toDouble()) * lateral).toFloat(),
                    (sample.z + sz * lateral + sz * lz + cz * lx).toFloat(),
                    yaw.toFloat(),
                    ((p[0] / width) + 0.5).toFloat(),
                    (p[1] / height).toFloat()
                )
            }
            val faces = intArrayOf(0,1,2,0,2,3,4,6,5,4,7,6,0,4,5,0,5,1,1,5,6,1,6,2,2,6,7,2,7,3,4,0,3,4,3,7)
            for (i in faces) indices.putShort((base + i).toShort())
            boxCount++
        }

        // Two rails and regular sleepers make the railway readable even at distance.
        var d = kotlin.math.floor((centerDistance - TRACK_BEHIND) / STEP) * STEP
        while (d <= centerDistance + TRACK_AHEAD && boxCount + 3 < MAX_BOXES) {
            addBox(d, LATERAL - 0.85, 0.16, 0.16, 12.0)
            addBox(d, LATERAL + 0.85, 0.16, 0.16, 12.0)
            addBox(d, LATERAL, 0.22, 0.12, 1.6)
            d += STEP
        }

        // A slow, long-distance passenger train repeatedly crosses the visible window.
        val loop = 300.0
        val trainHead = centerDistance - 110.0 + ((trainTime * 7.5) % loop)
        for (car in 0..5) {
            addBox(
                trainHead - car * 7.0,
                LATERAL,
                if (car == 0) 3.0 else 2.8,
                if (car == 0) 3.1 else 2.7,
                6.4
            )
        }

        while (boxCount < MAX_BOXES) {
            repeat(8) { putVertex(0f, -1000f, 0f, 0f, 0f, 0f) }
            repeat(36) { indices.putShort(((boxCount * 8) + (it % 8)).toShort()) }
            boxCount++
        }
        vertices.flip()
        indices.flip()
        vb!!.setBufferAt(engine, 0, vertices)
        ib!!.setBuffer(engine, indices)
    }

    private fun putVertex(x: Float, y: Float, z: Float, yaw: Float, u: Float, v: Float) {
        vertices.putFloat(x); vertices.putFloat(y); vertices.putFloat(z)
        val h = yaw * 0.5f
        vertices.putFloat(0f); vertices.putFloat(0f); vertices.putFloat(sin(h)); vertices.putFloat(cos(h))
        vertices.putFloat(u); vertices.putFloat(v)
    }

    private fun findMaterial(): com.google.android.filament.MaterialInstance? {
        val names = arrayOf("Ground", "Terrain", "Grass", "Landscape", "ground", "terrain", "grass")
        val rm = engine.renderableManager
        for (name in names) {
            val e = asset.getFirstEntityByName(name)
            if (e == 0 || !rm.hasComponent(e)) continue
            val i = rm.getInstance(e)
            if (rm.getPrimitiveCount(i) > 0) return rm.getMaterialInstanceAt(i, 0)
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
        vb?.let(engine::destroyVertexBuffer)
        ib?.let(engine::destroyIndexBuffer)
        vb = null
        ib = null
        material = null
        lastCenter = Double.NaN
    }
}
