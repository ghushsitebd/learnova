package com.learnova.app

import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.VertexBuffer
import com.google.android.filament.gltfio.FilamentAsset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/** Real 3D roadside learning marker; the lightweight HUD supplies the multilingual text. */
internal class LearningSignWorld(
    private val engine: Engine,
    private val scene: Scene,
    private val asset: FilamentAsset
) {
    private var entity = 0
    private var vb: VertexBuffer? = null
    private var ib: IndexBuffer? = null
    private var material: MaterialInstance? = null
    private var visible = false
    private var targetDistance = 0.0
    private var lastDistance = Double.NaN

    fun build(): Boolean {
        if (entity != 0) return true
        val source = findMaterial() ?: return false
        material = try { MaterialInstance.duplicate(source, "LearnovaLearningSign") } catch (_: Throwable) { return false }
        try {
            material?.setParameter("baseColor", 0.08f, 0.30f, 0.18f, 1f)
            material?.setParameter("metallic", 0f)
            material?.setParameter("roughness", 0.72f)
        } catch (_: Throwable) {}
        vb = VertexBuffer.Builder()
            .vertexCount(24).bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 36)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, 36)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 28, 36)
            .build(engine)
        ib = IndexBuffer.Builder().indexCount(108).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
        entity = EntityManager.get().create()
        RenderableManager.Builder(1).material(0, material!!)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb!!, ib!!)
            .culling(false).receiveShadows(true).castShadows(false).build(engine, entity)
        scene.addEntity(entity)
        hide()
        return true
    }

    fun show(worldDistance: Double) {
        targetDistance = worldDistance + 12.0
        visible = true
        lastDistance = Double.NaN
    }

    fun hide() {
        visible = false
        writeHiddenGeometry()
    }

    fun update(vehicleDistance: Double) {
        if (entity == 0 || vb == null || ib == null || !visible) return
        if (!lastDistance.isNaN() && kotlin.math.abs(vehicleDistance - lastDistance) < 0.5) return
        val road = RoadSpline.sampleRelative(targetDistance, vehicleDistance)
        val side = 5.6
        val x = road.x + cos(road.yaw) * side
        val z = road.z - sin(road.yaw) * side
        val v = ByteBuffer.allocate(24 * 36).order(ByteOrder.nativeOrder())
        val i = ByteBuffer.allocate(108 * 2).order(ByteOrder.nativeOrder())
        box(v, i, x, road.y + 1.55, z, road.yaw, 1.65, 1.05, 0.10)
        box(v, i, x - 0.85 * cos(road.yaw), road.y + 0.775, z + 0.85 * sin(road.yaw), road.yaw, 0.08, 1.55, 0.08)
        box(v, i, x + 0.85 * cos(road.yaw), road.y + 0.775, z - 0.85 * sin(road.yaw), road.yaw, 0.08, 1.55, 0.08)
        v.flip(); i.flip()
        vb!!.setBufferAt(engine, 0, v); ib!!.setBuffer(engine, i)
        lastDistance = vehicleDistance
    }

    private fun box(v: ByteBuffer, i: ByteBuffer, x: Double, y: Double, z: Double, yaw: Double, halfW: Double, h: Double, halfD: Double) {
        val base = v.position() / 36
        val c = cos(yaw); val s = sin(yaw)
        val p = arrayOf(
            doubleArrayOf(-halfW,0.0,-halfD), doubleArrayOf(halfW,0.0,-halfD),
            doubleArrayOf(halfW,0.0,halfD), doubleArrayOf(-halfW,0.0,halfD),
            doubleArrayOf(-halfW,h,-halfD), doubleArrayOf(halfW,h,-halfD),
            doubleArrayOf(halfW,h,halfD), doubleArrayOf(-halfW,h,halfD)
        )
        for (q in p) {
            v.putFloat((x + q[0]*c - q[2]*s).toFloat()); v.putFloat((y + q[1]).toFloat())
            v.putFloat((z + q[0]*s + q[2]*c).toFloat())
            v.putFloat(0f); v.putFloat(0f); v.putFloat(0f); v.putFloat(1f); v.putFloat(0f); v.putFloat(0f)
        }
        intArrayOf(0,1,2,0,2,3,4,6,5,4,7,6,0,4,5,0,5,1,1,5,6,1,6,2,2,6,7,2,7,3,4,0,3,4,3,7)
            .forEach { i.putShort((base + it).toShort()) }
    }

    private fun writeHiddenGeometry() {
        val v = ByteBuffer.allocate(24 * 36).order(ByteOrder.nativeOrder())
        val i = ByteBuffer.allocate(108 * 2).order(ByteOrder.nativeOrder())
        repeat(24) {
            v.putFloat(0f); v.putFloat(-5000f); v.putFloat(0f)
            v.putFloat(0f); v.putFloat(0f); v.putFloat(0f); v.putFloat(1f); v.putFloat(0f); v.putFloat(0f)
        }
        repeat(108) { i.putShort(0) }
        v.flip(); i.flip(); vb?.setBufferAt(engine, 0, v); ib?.setBuffer(engine, i)
    }

    private fun findMaterial(): MaterialInstance? {
        val rm = engine.renderableManager
        for (name in arrayOf("Ground","Terrain","Grass","Landscape","ground","terrain")) {
            val e = asset.getFirstEntityByName(name)
            if (e != 0 && rm.hasComponent(e)) {
                val instance = rm.getInstance(e)
                if (rm.getPrimitiveCount(instance) > 0) return rm.getMaterialInstanceAt(instance, 0)
            }
        }
        return null
    }

    fun destroy() {
        if (entity != 0) {
            scene.removeEntity(entity); engine.renderableManager.destroy(entity); EntityManager.get().destroy(entity); entity = 0
        }
        vb?.let(engine::destroyVertexBuffer); ib?.let(engine::destroyIndexBuffer); material?.let(engine::destroyMaterialInstance)
        vb = null; ib = null; material = null
    }
}