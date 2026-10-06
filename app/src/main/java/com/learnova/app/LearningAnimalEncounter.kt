package com.learnova.app

import com.google.android.filament.*
import com.google.android.filament.gltfio.FilamentAsset
import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/** A focused learning encounter: the named animal appears in the lesson world. */
internal class LearningAnimalEncounter(
    private val context: Context,
    private val engine: Engine,
    private val scene: Scene,
    private val asset: FilamentAsset
) {
    private val authoredGlb = CreatureGlbController(context, engine, scene)
    private var authoredVisible = false
    private var entity = 0
    private var vb: VertexBuffer? = null
    private var ib: IndexBuffer? = null
    private var material: MaterialInstance? = null
    private var active = false
    private var activeAnimal = "cat"
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
        if (entity == 0) return
        val key = animal.trim().lowercase()
        val supported = setOf(
            "cat", "dog", "elephant", "fish", "lion", "tiger",
            "rabbit", "parrot", "whale", "yak", "zebra",
            "fox", "deer", "horse", "wolf", "camel", "bear", "giraffe", "frog", "bird",
            "gazelle", "falcon"
        )
        if (key !in supported) return
        // Every encounter gets a fresh authored-GLB lifecycle so a completed
        // animal cannot block the next real asset from appearing.
        authoredGlb.hide()
        authoredVisible = false
        activeAnimal = key
        applyAnimalStyle(key)
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
        val t = ((now - start) / 14.8).coerceIn(0.0, 1.0)
        if (t >= 1.0) {
            active = false
            authoredGlb.hide()
            authoredVisible = false
            hide()
            return
        }

        val road = RoadSpline.sampleRelative(center, worldDistance)
        val isFlying = activeAnimal in setOf("bird", "parrot", "falcon")
        val isAquatic = activeAnimal in setOf("fish", "whale")
        // Three-part encounter: approach from the roadside, pause briefly for
        // recognition, then return. The pause gives the child a readable learning moment
        // without freezing the whole driving world.
        val phase = smooth(t)
        val walkPhase = when {
            t < 0.34 -> smooth(t / 0.34)
            t < 0.72 -> 1.0
            else -> smooth((1.0 - t) / 0.28)
        }
        val pause = t in 0.34..0.72
        val wave = sin(t * Math.PI * 2.0)

        // Keep the encounter lightweight but make movement match the animal's habitat:
        // land animals cross the roadside, birds fly above it, and aquatic animals
        // glide along the world-side water corridor rather than crossing the road.
        val (x, y, z, yaw) = when {
            isFlying -> {
                val forward = (phase - 0.5) * 10.0
                val lateral = sin(t.toDouble() * Math.PI) * 2.0
                Quad(
                    road.x + cos(road.yaw) * forward + sin(road.yaw) * lateral,
                    road.y + 2.6 + wave * 0.22,
                    road.z - sin(road.yaw) * forward + cos(road.yaw) * lateral,
                    road.yaw + if (wave >= 0.0) 0.18 else -0.18
                )
            }
            isAquatic -> {
                val forward = (phase - 0.5) * 11.0
                val waterSide = 7.0
                Quad(
                    road.x + cos(road.yaw) * forward + sin(road.yaw) * waterSide,
                    road.y - 0.20 + wave * 0.08,
                    road.z - sin(road.yaw) * forward + cos(road.yaw) * waterSide,
                    road.yaw.toDouble()
                )
            }
            else -> {
                val across = -1.0 + 2.0 * walkPhase
                val side = if (pause) 5.3 else 6.2
                Quad(
                    road.x + cos(road.yaw) * across * side,
                    road.y + 0.03,
                    road.z - sin(road.yaw) * across * side,
                    road.yaw + if (across < 0.0) 1.57 else -1.57
                )
            }
        }

        // Prefer a bundled authored GLB for the near-field encounter. If the
        // binary asset is not present, retain the verified lightweight fallback.
        if (!authoredVisible) {
            authoredVisible = authoredGlb.show(
                activeAnimal, x, y, z, yaw, animalScale(activeAnimal)
            )
        } else {
            // Keep the real GLB on the same animated path as the encounter,
            // rather than leaving it frozen at its initial spawn position.
            authoredGlb.move(x, y, z, yaw, animalScale(activeAnimal))
        }
        if (!authoredVisible) {
            drawAnimal(x, y, z, yaw, animalScale(activeAnimal) + 0.04 * sin(t * Math.PI * 4))
        }
    }

    private fun drawAnimal(x: Double, y: Double, z: Double, yaw: Double, scale: Double) {
        val v = ByteBuffer.allocate(40 * 36).order(ByteOrder.nativeOrder())
        val i = ByteBuffer.allocate(180 * 2).order(ByteOrder.nativeOrder())
        // Species-aware silhouette: keep the fixed 40-vertex budget, but vary
        // proportions so each learning animal reads differently at a distance.
        val aquatic = activeAnimal in setOf("fish", "whale")
        val flying = activeAnimal in setOf("bird", "parrot", "falcon")
        val bodyW = when (activeAnimal) {
            "elephant", "bear", "yak" -> 0.40
            "rabbit", "frog" -> 0.23
            "fish", "whale" -> 0.34
            else -> 0.30
        } * scale
        val bodyH = when (activeAnimal) {
            "giraffe" -> 0.56
            "elephant", "bear", "yak" -> 0.48
            "fish", "whale" -> 0.24
            else -> 0.38
        } * scale
        val bodyD = when (activeAnimal) {
            "fish", "whale" -> 0.92
            "elephant", "bear", "yak" -> 0.82
            else -> 0.72
        } * scale
        box(v,i,x,y+bodyH,z,yaw,bodyW,bodyH,bodyD)
        val headForward = if (aquatic) 0.58 else 0.62
        val headY = if (flying) 0.74 else if (activeAnimal == "giraffe") 1.00 else 0.70
        box(v,i,x+cos(yaw)*headForward*scale,y+headY*scale,z-sin(yaw)*headForward*scale,
            yaw,bodyW*0.88,0.28*scale,0.30*scale)
        // Ears/horns/crest are represented by the third volume where appropriate.
        val headTop = when (activeAnimal) {
            "elephant" -> 0.92
            "giraffe" -> 1.32
            "rabbit" -> 1.02
            "tiger", "lion", "bear", "fox" -> 0.96
            else -> 0.90
        }
        val headDetailW = when (activeAnimal) {
            "elephant", "rabbit" -> 0.16
            "giraffe" -> 0.10
            else -> 0.08
        } * scale
        box(v,i,x+cos(yaw)*0.77*scale,y+headTop*scale,z-sin(yaw)*0.77*scale,
            yaw,headDetailW,0.12*scale,0.10*scale)
        val tailYaw = if (activeAnimal in setOf("fish","whale")) yaw else yaw + 0.45
        box(v,i,x+cos(tailYaw)*-0.48*scale,y+0.55*scale,z-sin(tailYaw)*-0.48*scale,
            tailYaw,0.055*scale,0.055*scale,0.55*scale)
        while (v.position() < 40 * 36) { v.putFloat(0f); v.putFloat(-5000f); v.putFloat(0f); repeat(6) { v.putFloat(0f) } }
        while (i.position() < 180 * 2) i.putShort(0)
        v.flip(); i.flip()
        vb!!.setBufferAt(engine,0,v); ib!!.setBuffer(engine,i)
    }

    private fun animalScale(key: String): Double = when (key) {
        "elephant", "whale" -> 1.55
        "lion", "tiger", "zebra", "yak", "camel", "giraffe" -> 1.18
        "rabbit", "frog", "bird", "falcon" -> 0.72
        else -> 0.92
    }

    private fun applyAnimalStyle(key: String) {
        val rgb = when (key) {
            "elephant" -> floatArrayOf(0.42f, 0.46f, 0.50f)
            "fish", "whale" -> floatArrayOf(0.12f, 0.42f, 0.72f)
            "lion", "giraffe", "camel", "gazelle" -> floatArrayOf(0.72f, 0.50f, 0.20f)
            "tiger", "fox" -> floatArrayOf(0.78f, 0.28f, 0.10f)
            "zebra" -> floatArrayOf(0.72f, 0.72f, 0.68f)
            "bear", "yak" -> floatArrayOf(0.20f, 0.14f, 0.10f)
            "rabbit" -> floatArrayOf(0.74f, 0.62f, 0.58f)
            "parrot", "bird", "falcon" -> floatArrayOf(0.16f, 0.50f, 0.24f)
            "frog" -> floatArrayOf(0.16f, 0.56f, 0.18f)
            else -> floatArrayOf(0.48f, 0.28f, 0.16f)
        }
        try { material?.setParameter("baseColor", rgb[0], rgb[1], rgb[2], 1f) } catch (_: Throwable) {}
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
    private data class Quad(val x: Double, val y: Double, val z: Double, val yaw: Double)

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
        authoredGlb.destroy()
        if(entity!=0){scene.removeEntity(entity);engine.renderableManager.destroy(entity);EntityManager.get().destroy(entity);entity=0}
        vb?.let(engine::destroyVertexBuffer);ib?.let(engine::destroyIndexBuffer);material?.let(engine::destroyMaterialInstance)
        vb=null;ib=null;material=null
    }
}
