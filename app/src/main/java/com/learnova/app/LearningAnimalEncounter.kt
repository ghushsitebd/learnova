package com.learnova.app

import com.google.android.filament.*
import com.google.android.filament.gltfio.FilamentAsset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/** A focused learning encounter: the named animal appears in the lesson world. */
internal class LearningAnimalEncounter(
    private val engine: Engine,
    private val scene: Scene,
    private val asset: FilamentAsset
) {
    private var entity = 0
    private var vb: VertexBuffer? = null
    private var ib: IndexBuffer? = null
    private var material: MaterialInstance? = null
    private var active = false
    private var start = 0.0
    private var center = 0.0
    private var lastNow = 0L

    fun build(): Boolean {
        if (entity != 0) return true
        val source = findMaterial() ?: return false
        material = try { MaterialInstance.duplicate(source, "LearnovaLearningAnimal") } catch (_: Throwable) { return false }
        try {
            material?.setParameter("baseColor", 0.48f, 0.28f, 0.16f, 1f)
            material?.setParameter("metallic", 0f)
            material?.setParameter("roughness", 0.9f)
        } catch (_: Throwable) {}
        vb = VertexBuffer.Builder()
            .vertexCount(40).bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION,0,VertexBuffer.AttributeType.FLOAT3,0,36)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS,0,VertexBuffer.AttributeType.FLOAT4,12,36)
            .attribute(VertexBuffer.VertexAttribute.UV0,0,VertexBuffer.AttributeType.FLOAT2,28,36)
            .build(engine)
        ib = IndexBuffer.Builder().indexCount(180).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
        entity = EntityManager.get().create()
        RenderableManager.Builder(1).material(0, material!!)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb!!, ib!!)
            .culling(false).receiveShadows(true).castShadows(false).build(engine, entity)
        scene.addEntity(entity)
        hide()
        return true
    }

    /** Triggered when the child hears the matching word, e.g. C -> Cat. */
    fun trigger(animal: String, worldDistance: Double) {
        if (!animal.equals("cat", true) || entity == 0) return
        center = worldDistance
        start = System.nanoTime() * 1e-9
        active = true
        lastNow = 0L
    }

    fun update(worldDistance: Double) {
        if (!active || entity == 0 || vb == null || ib == null) return
        val now = System.nanoTime() * 1e-9
        if (lastNow != 0L && now - lastNow < 1.0 / 45.0) return
        lastNow = (now * 1e9).toLong()
        val t = ((now - start) / 3.8).coerceIn(0.0, 1.0)
        if (t >= 1.0) { active = false; hide(); return }

        val road = RoadSpline.sampleRelative(center, worldDistance)
        // The cat crosses in front of the vehicle, never spawning on the road center.
        val across = -1.0 + 2.0 * smooth(t)
        val side = 6.2
        val x = road.x + cos(road.yaw) * across * side
        val z = road.z - sin(road.yaw) * across * side
        val y = road.y + 0.03
        val yaw = road.yaw + if (across < 0) 1.57 else -1.57
        drawCat(x, y, z, yaw, 0.92 + 0.04 * sin(t * Math.PI * 4))
        vb!!.setBufferAt(engine, 0, vertexData())
        ib!!.setBuffer(engine, indexData())
    }

    private fun drawCat(x: Double, y: Double, z: Double, yaw: Double, scale: Double) {
        val v = ByteBuffer.allocate(40 * 36).order(ByteOrder.nativeOrder())
        val i = ByteBuffer.allocate(180 * 2).order(ByteOrder.nativeOrder())
        box(v,i,x,y+0.38*scale,z,yaw,0.30*scale,0.38*scale,0.72*scale)
        box(v,i,x+cos(yaw)*0.62*scale,y+0.70*scale,z-sin(yaw)*0.62*scale,yaw,0.27*scale,0.28*scale,0.30*scale)
        box(v,i,x+cos(yaw)*0.77*scale,y+0.98*scale,z-sin(yaw)*0.77*scale,yaw,0.08*scale,0.16*scale,0.09*scale)
        box(v,i,x+cos(yaw)*0.56*scale,y+0.55*scale,z-sin(yaw)*0.56*scale,yaw+0.45,0.055*scale,0.055*scale,0.55*scale)
        v.flip(); i.flip()
        vb!!.setBufferAt(engine,0,v); ib!!.setBuffer(engine,i)
    }

    private fun box(v: ByteBuffer,i: ByteBuffer,x:Double,y:Double,z:Double,yaw:Double,w:Double,h:Double,d:Double) {
        val base=v.position()/36; val c=cos(yaw); val s=sin(yaw)
        val p=arrayOf(
            doubleArrayOf(-w,0.0,-d),doubleArrayOf(w,0.0,-d),doubleArrayOf(w,0.0,d),doubleArrayOf(-w,0.0,d),
            doubleArrayOf(-w,h,-d),doubleArrayOf(w,h,-d),doubleArrayOf(w,h,d),doubleArrayOf(-w,h,d)
        )
        for(q in p) { v.putFloat((x+q[0]*c-q[2]*s).toFloat());v.putFloat((y+q[1]).toFloat());v.putFloat((z+q[0]*s+q[2]*c).toFloat());v.putFloat(0f);v.putFloat(0f);v.putFloat(0f);v.putFloat(1f);v.putFloat(0f);v.putFloat(0f) }
        intArrayOf(0,1,2,0,2,3,4,6,5,4,7,6,0,4,5,0,5,1,1,5,6,1,6,7,2,6,7,2,7,3,4,0,3,4,3,7).forEach{i.putShort((base+it).toShort())}
    }

    private fun vertexData(): ByteBuffer = ByteBuffer.allocate(40 * 36).order(ByteOrder.nativeOrder())
    private fun indexData(): ByteBuffer = ByteBuffer.allocate(180 * 2).order(ByteOrder.nativeOrder())
    private fun hide() { 
        val v=vertexData(); val i=indexData()
        repeat(40){v.putFloat(0f);v.putFloat(-5000f);v.putFloat(0f);repeat(6){v.putFloat(0f)}}
        repeat(180){i.putShort(0)}
        v.flip();i.flip();vb?.setBufferAt(engine,0,v);ib?.setBuffer(engine,i)
    }
    private fun smooth(t:Double)=t*t*(3.0-2.0*t)
    private fun findMaterial():MaterialInstance? {
        val rm=engine.renderableManager
        for(n in arrayOf("Ground","Terrain","Grass","Landscape","ground","terrain")) {
            val e=asset.getFirstEntityByName(n)
            if(e!=0 && rm.hasComponent(e)) { val x=rm.getInstance(e); if(rm.getPrimitiveCount(x)>0) return rm.getMaterialInstanceAt(x,0) }
        }
        return null
    }
    fun destroy() {
        if(entity!=0){scene.removeEntity(entity);engine.renderableManager.destroy(entity);EntityManager.get().destroy(entity);entity=0}
        vb?.let(engine::destroyVertexBuffer);ib?.let(engine::destroyIndexBuffer);material?.let(engine::destroyMaterialInstance)
        vb=null;ib=null;material=null
    }
}
