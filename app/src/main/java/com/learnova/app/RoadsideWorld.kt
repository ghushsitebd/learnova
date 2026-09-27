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
 * Asset-light roadside streaming layer.
 *
 * It generates deterministic low-poly silhouettes around the driving corridor and
 * reuses the authored terrain material. This is deliberately a single combined
 * renderable: dozens of visible props cost one draw call and no external texture
 * pack is added to the APK. Production GLBs can replace individual prop families
 * later without changing the streaming contract.
 */
internal class RoadsideWorld(
    private val engine: Engine,
    private val scene: Scene,
    private val asset: FilamentAsset
) {
    private companion object {
        const val BEHIND = 18.0
        const val AHEAD = 155.0
        const val STEP = 9.0
        const val MAX_PROPS = 120
        const val VERTEX_STRIDE = 36
    }

    private var entity = 0
    private var vb: VertexBuffer? = null
    private var ib: IndexBuffer? = null
    private var material: com.google.android.filament.MaterialInstance? = null
    private var lastCenter = Double.NaN

    fun build(): Boolean {
        if (entity != 0) return true
        material = findMaterial() ?: return false
        val maxVertices = MAX_PROPS * 8
        val maxIndices = MAX_PROPS * 36
        vb = VertexBuffer.Builder()
            .vertexCount(maxVertices)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, VERTEX_STRIDE)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, VERTEX_STRIDE)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 28, VERTEX_STRIDE)
            .build(engine)
        ib = IndexBuffer.Builder()
            .indexCount(maxIndices)
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
        if (!lastCenter.isNaN() && kotlin.math.abs(centerDistance - lastCenter) < 7.0) return

        val vertices = ByteBuffer.allocate(MAX_PROPS * 8 * VERTEX_STRIDE).order(ByteOrder.nativeOrder())
        val indices = ByteBuffer.allocate(MAX_PROPS * 36 * 2).order(ByteOrder.nativeOrder())
        var vertexCount = 0
        var indexCount = 0

        var d = kotlin.math.floor((centerDistance - BEHIND) / STEP) * STEP
        while (d <= centerDistance + AHEAD && vertexCount + 8 <= MAX_PROPS * 8) {
            val sample = RoadSpline.sampleRelative(d, centerDistance)
            val biome = WorldDirector.profile(d).biome
            val seed = stableSeed(d)
            val yaw = sample.yaw
            val sideSign = if ((seed and 1L) == 0L) -1.0 else 1.0
            val side = when (biome) {
                WorldDirector.Biome.RIVER, WorldDirector.Biome.COAST -> 9.0 + (seed % 8L)
                WorldDirector.Biome.MARKET, WorldDirector.Biome.VILLAGE -> 7.5 + (seed % 10L)
                else -> 9.0 + (seed % 15L)
            }
            val x = sample.x + cos(yaw) * side * sideSign
            val z = sample.z - sin(yaw) * side * sideSign

            val type = ((seed ushr 4) % 6L).toInt()
            val allow = when (biome) {
                WorldDirector.Biome.FOREST -> type <= 3
                WorldDirector.Biome.MOUNTAIN, WorldDirector.Biome.PLATEAU -> type <= 2
                WorldDirector.Biome.DESERT -> type == 0 || type == 4
                WorldDirector.Biome.RIVER, WorldDirector.Biome.COAST -> type == 0 || type == 4 || type == 5
                WorldDirector.Biome.MARKET, WorldDirector.Biome.VILLAGE -> type >= 2
            }

            if (allow) {
                val scale = 0.85 + ((seed ushr 8) % 90L) / 100.0
                val h = when (biome) {
                    WorldDirector.Biome.FOREST -> 3.0 * scale
                    WorldDirector.Biome.MOUNTAIN -> 2.2 * scale
                    WorldDirector.Biome.DESERT -> 1.5 * scale
                    WorldDirector.Biome.MARKET, WorldDirector.Biome.VILLAGE -> 2.5 * scale
                    else -> 2.0 * scale
                }
                val w = when (type) {
                    0 -> 0.65 * scale
                    1, 2 -> 1.05 * scale
                    3 -> 1.35 * scale
                    else -> 1.6 * scale
                }
                addProp(vertices, indices, x, sample.y, z, yaw, w, h, type)
                vertexCount += 8
                indexCount += 36
            }
            d += STEP
        }

        while (vertexCount < MAX_PROPS * 8) {
            repeat(8) { putVertex(vertices, 0f, -1000f, 0f, 0f, 0f, 0f) }
            vertexCount += 8
        }
        while (indexCount < MAX_PROPS * 36) {
            val base = (indexCount / 36) * 8
            val faces = intArrayOf(0,1,2, 0,2,3, 4,6,5, 4,7,6, 0,4,5, 0,5,1, 1,5,6, 1,6,2, 2,6,7, 2,7,3, 4,0,3, 4,3,7)
            for (i in faces) indices.putShort((base + i).toShort())
            indexCount += 36
        }
        vertices.flip(); indices.flip()
        vb!!.setBufferAt(engine, 0, vertices)
        ib!!.setBuffer(engine, indices)
        lastCenter = centerDistance
    }

    private fun addProp(
        vertices: ByteBuffer,
        indices: ByteBuffer,
        x: Double, y: Double, z: Double, yaw: Double,
        width: Double, height: Double, type: Int
    ) {
        val base = currentVertex(vertices)
        val depth = width * when (type) { 3 -> 1.8; 4,5 -> 1.5; else -> 0.9 }
        val halfW = width
        val halfD = depth
        val ground = y + when (type) { 3 -> 0.02; else -> 0.0 }
        val top = ground + height

        val cx = cos(yaw); val cz = -sin(yaw)
        val sx = sin(yaw); val sz = cos(yaw)

        // Give each roadside family a recognisable silhouette while retaining
        // one fixed 8-vertex/36-index budget. Trees taper toward the crown,
        // rocks use an uneven shoulder, and buildings stay broad and vertical.
        val topScale = when (type) {
            0 -> 0.42
            1 -> 0.62
            2 -> 0.78
            3 -> 0.88
            else -> 0.96
        }
        val topDepthScale = when (type) {
            0 -> 0.50
            1 -> 0.68
            2 -> 0.82
            else -> 0.94
        }
        val corners = arrayOf(
            floatArrayOf(-halfW,0.0,-halfD), floatArrayOf(halfW,0.0,-halfD),
            floatArrayOf(halfW,0.0,halfD), floatArrayOf(-halfW,0.0,halfD),
            floatArrayOf(-halfW * topScale,1.0,-halfD * topDepthScale),
            floatArrayOf(halfW * topScale,1.0,-halfD * topDepthScale),
            floatArrayOf(halfW * topScale,1.0,halfD * topDepthScale),
            floatArrayOf(-halfW * topScale,1.0,halfD * topDepthScale)
        )
        for (c in corners) {
            val lx = c[0].toDouble(); val lz = c[2].toDouble()
            putVertex(vertices,
                (x + lx*cx - lz*sx).toFloat(),
                (ground + c[1]*height).toFloat(),
                (z + lx*cz + lz*sz).toFloat(),
                yaw.toFloat(), 0f, (c[0]+halfW).toFloat()/(2*halfW), c[2].toFloat())
        }
        val faces = intArrayOf(0,1,2,0,2,3,4,6,5,4,7,6,0,4,5,0,5,1,1,5,6,1,6,2,2,6,7,2,7,3,4,0,3,4,3,7)
        for (i in faces) indices.putShort((base+i).toShort())
    }

    private fun currentVertex(vertices: ByteBuffer): Int = vertices.position() / VERTEX_STRIDE

    private fun putVertex(data: ByteBuffer, x: Float, y: Float, z: Float, yaw: Float, bank: Float, u: Float, v: Float) {
        data.putFloat(x); data.putFloat(y); data.putFloat(z)
        val hy = yaw * 0.5f; val hb = bank * 0.5f
        data.putFloat(sin(hb)); data.putFloat(0f); data.putFloat(sin(hy)); data.putFloat(cos(hy) * cos(hb))
        data.putFloat(u); data.putFloat(v)
    }

    private fun stableSeed(distance: Double): Long {
        var x = java.lang.Double.doubleToLongBits(distance)
        x = x xor (x ushr 33); x *= -49064778989728563L
        x = x xor (x ushr 33); x *= -4265267296055464877L
        return x xor (x ushr 33)
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
