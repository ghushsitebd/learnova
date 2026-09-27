package com.learnova.app

import com.google.android.filament.*
import com.google.android.filament.gltfio.FilamentAsset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/** Low-cost wet shoreline ribbon that masks the hard water/terrain boundary. */
internal class ShorelineWorld(
    private val engine: Engine,
    private val scene: Scene,
    private val asset: FilamentAsset
) {
    private companion object { const val N=49; const val STEP=5.0; const val STRIDE=36; const val V=N*2; const val I=(N-1)*6 }
    private var entity=0; private var vb:VertexBuffer?=null; private var ib:IndexBuffer?=null; private var mat:MaterialInstance?=null; private var last=Double.NaN

    fun build():Boolean {
        if(entity!=0)return true
        val src=findMaterial()?:return false
        mat=try{MaterialInstance.duplicate(src,"LearnovaWetShore")}catch(_:Throwable){return false}
        try{mat?.setParameter("baseColor",0.16f,0.20f,0.12f,1f)}catch(_:Throwable){}
        try{mat?.setParameter("metallic",0f)}catch(_:Throwable){}
        try{mat?.setParameter("roughness",0.82f)}catch(_:Throwable){}
        try{mat?.setParameter("reflectance",0.35f)}catch(_:Throwable){}
        vb=VertexBuffer.Builder().vertexCount(V).bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION,0,VertexBuffer.AttributeType.FLOAT3,0,STRIDE)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS,0,VertexBuffer.AttributeType.FLOAT4,12,STRIDE)
            .attribute(VertexBuffer.VertexAttribute.UV0,0,VertexBuffer.AttributeType.FLOAT2,28,STRIDE).build(engine)
        ib=IndexBuffer.Builder().indexCount(I).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
        val b=ByteBuffer.allocate(I*2).order(ByteOrder.nativeOrder())
        for(i in 0 until N-1){val a=i*2;b.putShort(a.toShort());b.putShort((a+2).toShort());b.putShort((a+1).toShort());b.putShort((a+1).toShort());b.putShort((a+2).toShort());b.putShort((a+3).toShort())};b.flip();ib!!.setBuffer(engine,b)
        entity=EntityManager.get().create()
        RenderableManager.Builder(1).material(0,mat!!).geometry(0,RenderableManager.PrimitiveType.TRIANGLES,vb!!,ib!!).culling(false).receiveShadows(true).castShadows(false).build(engine,entity)
        scene.addEntity(entity);update(0.0);return true
    }

    fun update(center:Double){
        if(entity==0||vb==null||ib==null)return
        if(!last.isNaN()&&kotlin.math.abs(center-last)<4)return
        val d=ByteBuffer.allocate(V*STRIDE).order(ByteOrder.nativeOrder());val start=kotlin.math.floor((center-35)/STEP)*STEP
        for(i in 0 until N){val dist=start+i*STEP;val s=RoadSpline.sampleRelative(dist,center);val biome=WorldDirector.profile(dist).biome
            if(biome!=WorldDirector.Biome.RIVER&&biome!=WorldDirector.Biome.COAST){put(d,0f,-5000f,0f,0f);put(d,0f,-5000f,0f,1f);continue}
            val side=if((stable(dist) and 1L)==0L)-1.0 else 1.0;val off=if(biome==WorldDirector.Biome.COAST)20.0 else 15.0;val width=if(biome==WorldDirector.Biome.COAST)8.0 else 5.0
            val yaw=s.yaw.toDouble();val lx=cos(yaw);val lz=-sin(yaw);val cx=s.x+lx*off*side;val cz=s.z+lz*off*side;val near=off-width*0.5;val far=off+width*0.5
            val y=s.y-0.55;put(d,(s.x+lx*near*side).toFloat(),(y+0.015).toFloat(),(s.z+lz*near*side).toFloat(),0f);put(d,(s.x+lx*far*side).toFloat(),(y+0.01).toFloat(),(s.z+lz*far*side).toFloat(),1f)
        };d.flip();vb!!.setBufferAt(engine,0,d);last=center
    }
    private fun findMaterial():MaterialInstance?{val rm=engine.renderableManager;for(n in arrayOf("Ground","Terrain","Grass","Landscape","ground","terrain")){val e=asset.getFirstEntityByName(n);if(e!=0&&rm.hasComponent(e)){val x=rm.getInstance(e);if(rm.getPrimitiveCount(x)>0)return rm.getMaterialInstanceAt(x,0)}};return null}
    private fun put(b:ByteBuffer,x:Float,y:Float,z:Float,u:Float){b.putFloat(x);b.putFloat(y);b.putFloat(z);b.putFloat(0f);b.putFloat(0f);b.putFloat(0f);b.putFloat(1f);b.putFloat(u);b.putFloat(0f)}
    private fun stable(d:Double):Long{kotlin.math.floor(d/24).toLong().let{return it* -7046029254386353131L}.also{};return kotlin.math.floor(d/24).toLong()*-7046029254386353131L}
    fun destroy(){if(entity!=0){scene.removeEntity(entity);engine.renderableManager.destroy(entity);EntityManager.get().destroy(entity);entity=0};vb?.let{engine.destroyVertexBuffer(it)};ib?.let{engine.destroyIndexBuffer(it)};mat?.let{engine.destroyMaterialInstance(it)};vb=null;ib=null;mat=null;last=Double.NaN}
}