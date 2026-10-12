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

/** Streamed low-cost 3D houses and bazaar stalls anchored to road distance. */
internal class VillageMarketWorld(
    private val engine: Engine,
    private val scene: Scene,
    private val asset: FilamentAsset
) {
    private companion object {
        const val MAX_BOXES = 480
        const val STRIDE = 36
        val FACES = intArrayOf(0,1,2,0,2,3,4,6,5,4,7,6,0,4,5,0,5,1,1,5,6,1,6,2,2,6,7,2,7,3,4,0,3,4,3,7)
    }
    private var entity = 0
    private var vb: VertexBuffer? = null
    private var ib: IndexBuffer? = null
    private var material: com.google.android.filament.MaterialInstance? = null
    private var lastCenter = Double.NaN
    private val vertices = ByteBuffer.allocate(MAX_BOXES * 8 * STRIDE).order(ByteOrder.nativeOrder())
    private val indices = ByteBuffer.allocate(MAX_BOXES * 36 * 2).order(ByteOrder.nativeOrder())

    fun build(): Boolean {
        if (entity != 0) return true
        material = findMaterial() ?: return false
        vb = VertexBuffer.Builder().vertexCount(MAX_BOXES * 8).bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION,0,VertexBuffer.AttributeType.FLOAT3,0,STRIDE)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS,0,VertexBuffer.AttributeType.FLOAT4,12,STRIDE)
            .attribute(VertexBuffer.VertexAttribute.UV0,0,VertexBuffer.AttributeType.FLOAT2,28,STRIDE).build(engine)
        ib = IndexBuffer.Builder().indexCount(MAX_BOXES * 36)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
        entity = EntityManager.get().create()
        RenderableManager.Builder(1).material(0,material!!)
            .geometry(0,RenderableManager.PrimitiveType.TRIANGLES,vb!!,ib!!)
            .culling(false).receiveShadows(true).castShadows(false).build(engine,entity)
        scene.addEntity(entity)
        update(0.0)
        return true
    }

    fun update(center: Double) {
        if (entity == 0 || vb == null || ib == null) return
        if (!lastCenter.isNaN() && kotlin.math.abs(center-lastCenter) < 6.0) return
        vertices.clear(); indices.clear()
        var count = 0
        fun box(d: Double, lateral: Double, w: Double, h: Double, depth: Double, yOffset: Double = 0.0, top: Double = 1.0) {
            if (count >= MAX_BOXES) return
            val p = RoadSpline.sampleRelative(d,center)
            val yaw = p.yaw.toDouble(); val cx=cos(yaw); val sz=-sin(yaw); val sx=sin(yaw); val cz=cos(yaw)
            val ground=p.y.toDouble()+sin(p.bank.toDouble())*lateral+yOffset
            val hw=w*0.5; val hd=depth*0.5
            val corners=arrayOf(
                doubleArrayOf(-hw,0.0,-hd),doubleArrayOf(hw,0.0,-hd),doubleArrayOf(hw,0.0,hd),doubleArrayOf(-hw,0.0,hd),
                doubleArrayOf(-hw*top,h,-hd*top),doubleArrayOf(hw*top,h,-hd*top),doubleArrayOf(hw*top,h,hd*top),doubleArrayOf(-hw*top,h,hd*top)
            )
            val base=count*8
            for (v in corners) {
                val lx=v[0]; val lz=v[2]; val hy=(yaw*0.5).toFloat()
                vertices.putFloat((p.x+cx*lateral+cx*lz+sx*lx).toFloat())
                vertices.putFloat((ground+v[1]).toFloat())
                vertices.putFloat((p.z+sz*lateral+sz*lz+cz*lx).toFloat())
                vertices.putFloat(0f); vertices.putFloat(0f); vertices.putFloat(sin(hy)); vertices.putFloat(cos(hy))
                vertices.putFloat(((lx/w)+0.5).toFloat()); vertices.putFloat((v[1]/h).toFloat())
            }
            FACES.forEach { indices.putShort((base+it).toShort()) }; count++
        }

        var d=floor((center-18.0)/12.0)*12.0
        while (d <= center+250.0 && count < MAX_BOXES-10) {
            val biome=WorldDirector.profile(d).biome
            val seed=seed(d); val market=biome==WorldDirector.Biome.MARKET
            val village=biome==WorldDirector.Biome.VILLAGE
            val riverside=(biome==WorldDirector.Biome.RIVER || biome==WorldDirector.Biome.COAST) && (seed and 3L)==0L
            if (market || village || riverside) for (side in intArrayOf(-1,1)) {
                if (count >= MAX_BOXES-10) break
                val centerSide=side*(if (market) 10.5+(seed ushr 5)%3L else 13.5+(seed ushr 6)%5L)
                if (market) {
                    // Counter, canopy and posts establish the silhouette of a roadside bazaar stall.
                    box(d,centerSide,3.2,1.05,2.6)
                    box(d,centerSide,3.9,0.22,3.2,2.1)
                    box(d,centerSide-side*1.55,0.14,1.9,0.14,0.02)
                    box(d,centerSide+side*1.55,0.14,1.9,0.14,0.02)
                    box(d,centerSide-side*0.72,0.10,2.0,0.10,0.02)
                    box(d,centerSide+side*0.72,0.10,2.0,0.10,0.02)
                    // A raised shop sign and front shelf make each stall read as a market.
                    box(d,centerSide,1.55,0.30,0.14,1.58)
                    box(d,centerSide,2.15,0.12,0.46,1.18)
                    // Small produce crates are placed at the two ends of the counter.
                    box(d,centerSide-side*1.08,0.46,0.42,0.46,0.56)
                    box(d,centerSide+side*1.08,0.46,0.42,0.46,0.56)
                    // A narrow rear shelf and its supports add depth without extra draw calls.
                    box(d,centerSide+side*1.05,1.75,0.10,0.22,1.42)
                    box(d,centerSide+side*1.05-side*0.78,0.10,0.42,0.10,1.02)
                    box(d,centerSide+side*1.05+side*0.78,0.10,0.42,0.10,1.02)
                } else {
                    val w=if ((seed and 7L)==0L) 4.6 else 3.7
                    val h=if ((seed and 15L)==0L) 3.0 else 2.35
                    box(d,centerSide,w,h,4.0,top=0.97)
                    box(d,centerSide,w*1.18,0.42,4.55,h,0.78)
                    val front=centerSide-side*(w*0.5+0.08)
                    // Door, lintel and paired window frames break up the plain house facade.
                    box(d,front,0.72,1.45,0.14,0.03)
                    box(d,front,0.54,0.52,0.12,1.45)
                    box(d,front-side*0.85,0.55,0.52,0.12,1.45)
                    box(d,front-side*0.45,1.55,0.14,0.95,1.82)
                    box(d,front-side*0.88,0.62,0.58,0.12,1.18)
                    box(d,front-side*0.88,0.10,0.70,0.12,1.16)
                    box(d,front-side*0.88-side*0.31,0.10,0.70,0.12,1.16)
                    box(d,front+side*0.88,0.62,0.58,0.12,1.18)
                    box(d,front+side*0.88,0.10,0.70,0.12,1.16)
                    box(d,front+side*0.88+side*0.31,0.10,0.70,0.12,1.16)
                }
            }
            if (village && (seed and 7L)==0L && count < MAX_BOXES-10) {
                val side=if ((seed and 8L)==0L) -1.0 else 1.0
                box(d,side*20.0,4.6,3.8,4.8)
                box(d,side*20.0,1.0,3.0,1.0,3.8,0.8)
                box(d,side*20.0,1.8,0.45,1.8,6.7,0.7)
            }
            d+=12.0
        }
        while (count < MAX_BOXES) {
            repeat(8) { vertices.putFloat(0f); vertices.putFloat(-1000f); vertices.putFloat(0f); vertices.putFloat(0f); vertices.putFloat(0f); vertices.putFloat(0f); vertices.putFloat(1f); vertices.putFloat(0f); vertices.putFloat(0f) }
            val base=count*8; FACES.forEach { indices.putShort((base+it).toShort()) }; count++
        }
        vertices.flip(); indices.flip()
        vb!!.setBufferAt(engine,0,vertices); ib!!.setBuffer(engine,indices); lastCenter=center
    }

    private fun findMaterial(): com.google.android.filament.MaterialInstance? {
        val rm=engine.renderableManager
        for (name in arrayOf("Building","Wall","House","Wood","Roof","Ground","Terrain","Grass","ground","terrain")) {
            val e=asset.getFirstEntityByName(name)
            if (e!=0 && rm.hasComponent(e)) {
                val instance=rm.getInstance(e)
                if (rm.getPrimitiveCount(instance)>0) return rm.getMaterialInstanceAt(instance,0)
            }
        }
        return null
    }
    private fun seed(d: Double): Long {
        var x=java.lang.Double.doubleToLongBits(d)
        x=x xor (x ushr 33); x *= -49064778989728563L
        x=x xor (x ushr 33); x *= -4265267296055464877L
        return x xor (x ushr 33)
    }
    fun destroy() {
        if (entity!=0) { scene.removeEntity(entity); engine.renderableManager.destroy(entity); EntityManager.get().destroy(entity); entity=0 }
        vb?.let { engine.destroyVertexBuffer(it) }; ib?.let { engine.destroyIndexBuffer(it) }
        vb=null; ib=null; material=null; lastCenter=Double.NaN
    }
}
