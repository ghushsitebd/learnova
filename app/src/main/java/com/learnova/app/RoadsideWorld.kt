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
        const val BEHIND = 24.0
        const val AHEAD = 220.0
        const val STEP = 10.0
        const val MAX_PROPS = 150
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
            val biome = transitionBiome(d, stableSeed(d))
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
                    val groundY = roadsideGroundHeight(
                    d,
                    sample.bank.toDouble(),
                    side * sideSign,
                    biome,
                    sample.y.toDouble()
                )
                val propBank = terrainSlopeAt(
                    d,
                    side * sideSign,
                    biome,
                    sample.bank.toDouble()
                )
                addProp(
                    vertices, indices, x, groundY, z,
                    yaw.toDouble(), w, h, type, propBank
                )
                vertexCount += 8
                indexCount += 36
            }
            d += STEP
        }

        while (vertexCount < MAX_PROPS * 8) {
            repeat(8) { putVertex(vertices, 0f, -1000f, 0f, 0f, 0f, 0f, 0f) }
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

    /**
     * Matches the streamed terrain micro-topography at a prop's actual lateral
     * position. Props therefore sit on the land instead of floating at the road
     * centerline when the verge rises, falls, banks, or changes biome.
     */
    private fun roadsideGroundHeight(
        distance: Double,
        bank: Double,
        lateral: Double,
        biome: WorldDirector.Biome,
        roadY: Double
    ): Double {
        val bankLift = kotlin.math.sin(bank) * lateral
        val terrainShape = when (biome) {
            WorldDirector.Biome.FOREST ->
                kotlin.math.sin(distance * 0.075) * 0.16 + kotlin.math.cos(distance * 0.021) * 0.24
            WorldDirector.Biome.RIVER ->
                kotlin.math.sin(distance * 0.060) * 0.07 + kotlin.math.cos(distance * 0.018) * 0.10
            WorldDirector.Biome.MOUNTAIN ->
                kotlin.math.sin(distance * 0.040) * 0.42 + kotlin.math.cos(distance * 0.013) * 0.30
            WorldDirector.Biome.DESERT ->
                kotlin.math.sin(distance * 0.055) * 0.28 + kotlin.math.cos(distance * 0.019) * 0.16
            WorldDirector.Biome.PLATEAU ->
                kotlin.math.sin(distance * 0.035) * 0.24 + kotlin.math.cos(distance * 0.012) * 0.18
            WorldDirector.Biome.MARKET ->
                kotlin.math.sin(distance * 0.090) * 0.055 + kotlin.math.cos(distance * 0.025) * 0.08
            WorldDirector.Biome.VILLAGE ->
                kotlin.math.sin(distance * 0.065) * 0.12 + kotlin.math.cos(distance * 0.017) * 0.18
            WorldDirector.Biome.COAST ->
                kotlin.math.sin(distance * 0.050) * 0.10 + kotlin.math.cos(distance * 0.015) * 0.14
        }
        val sideShape = kotlin.math.sin(distance * 0.11) * 0.05 *
            if (lateral < 0.0) -1.0 else 1.0
        return roadY + bankLift + terrainShape + sideShape
    }

    private fun addProp(
        vertices: ByteBuffer,
        indices: ByteBuffer,
        x: Double, y: Double, z: Double, yaw: Double,
        width: Double, height: Double, type: Int, bank: Double
    ) {
        val base = currentVertex(vertices)
        val depth = width * when (type) { 3 -> 1.8; 4,5 -> 1.5; else -> 0.9 }
        val halfW = width
        val halfD = depth
        val ground = y + when (type) { 3 -> 0.02; else -> 0.0 }
        val cx = cos(yaw); val cz = -sin(yaw)
        val sx = sin(yaw); val sz = cos(yaw)
        // Props follow the local verge slope instead of standing perfectly
        // vertical on a banked/uneven shoulder.
        // Roll the prop across the local verge so its base follows the same
        // bank as the terrain. The longitudinal axis remains aligned to the road.
        val bankSlope = kotlin.math.tan(bank).coerceIn(-0.25, 0.25)

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
            doubleArrayOf(-halfW,0.0,-halfD), doubleArrayOf(halfW,0.0,-halfD),
            doubleArrayOf(halfW,0.0,halfD), doubleArrayOf(-halfW,0.0,halfD),
            doubleArrayOf(-halfW * topScale,1.0,-halfD * topDepthScale),
            doubleArrayOf(halfW * topScale,1.0,-halfD * topDepthScale),
            doubleArrayOf(halfW * topScale,1.0,halfD * topDepthScale),
            doubleArrayOf(-halfW * topScale,1.0,halfD * topDepthScale)
        )
        for (c in corners) {
            val lx = c[0]; val lz = c[2]
            val slopedY = c[1] * height + lx * bankSlope

            putVertex(vertices,
                (x + lx*cx - lz*sx).toFloat(),
                (ground + slopedY).toFloat(),
                (z + lx*sz + lz*cz).toFloat(),
                yaw.toFloat(), 0f, ((c[0]+halfW)/(2*halfW)).toFloat(), c[2].toFloat())
        }
        val faces = intArrayOf(0,1,2,0,2,3,4,6,5,4,7,6,0,4,5,0,5,1,1,5,6,1,6,2,2,6,7,2,7,3,4,0,3,4,3,7)
        for (i in faces) indices.putShort((base+i).toShort())
    }
    private fun terrainSlopeAt(
        distance: Double,
        lateral: Double,
        biome: WorldDirector.Biome,
        bank: Double
    ): Double {
        val slope = when (biome) {
            WorldDirector.Biome.MOUNTAIN -> sin(distance * 0.040) * 0.10
            WorldDirector.Biome.DESERT -> sin(distance * 0.055) * 0.06
            WorldDirector.Biome.PLATEAU -> sin(distance * 0.035) * 0.05
            WorldDirector.Biome.FOREST -> sin(distance * 0.075) * 0.035
            WorldDirector.Biome.VILLAGE -> sin(distance * 0.065) * 0.025
            WorldDirector.Biome.MARKET -> sin(distance * 0.090) * 0.015
            WorldDirector.Biome.RIVER -> sin(distance * 0.060) * 0.018
            WorldDirector.Biome.COAST -> sin(distance * 0.050) * 0.020
        }
        return (bank * 0.55 + slope * if (lateral < 0.0) -1.0 else 1.0).coerceIn(-0.12, 0.12)
    }

    private fun currentVertex(vertices: ByteBuffer): Int = vertices.position() / VERTEX_STRIDE

    private fun putVertex(data: ByteBuffer, x: Float, y: Float, z: Float, yaw: Float, bank: Float, u: Float, v: Float) {
        data.putFloat(x); data.putFloat(y); data.putFloat(z)
        val hy = yaw * 0.5f; val hb = bank * 0.5f
        data.putFloat(sin(hb)); data.putFloat(0f); data.putFloat(sin(hy)); data.putFloat(cos(hy) * cos(hb))
        data.putFloat(u); data.putFloat(v)
    }

    /**
     * Softens visual biome boundaries in the roadside stream.
     *
     * The world director remains deterministic, but real landscapes transition
     * gradually. In the final 18 m of a chapter, a small deterministic subset
     * of props can belong to the next biome, creating a natural ecotone.
     */
    private fun transitionBiome(distance: Double, seed: Long): WorldDirector.Biome {
        val safe = kotlin.math.max(0.0, distance)
        val chapterLength = 96.0
        val fraction = safe - kotlin.math.floor(safe / chapterLength) * chapterLength
        if (fraction < 78.0) return WorldDirector.profile(safe).biome

        val current = WorldDirector.profile(safe).biome
        val next = WorldDirector.profile(
            safe + (chapterLength - fraction) + 0.25
        ).biome
        if (current == next) return current

        val t = ((fraction - 78.0) / 18.0).coerceIn(0.0, 1.0)
        val chance = ((seed ushr 16) and 1023L) / 1023.0
        return if (chance < t * 0.58) next else current
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
