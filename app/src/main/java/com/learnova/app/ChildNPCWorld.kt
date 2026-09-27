package com.learnova.app

import com.google.android.filament.*
import com.google.android.filament.gltfio.FilamentAsset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/** Asset-light cute child NPCs streamed around villages, markets and forest learning stops. */
internal class ChildNPCWorld(
    private val engine: Engine,
    private val scene: Scene,
    private val asset: FilamentAsset
) {
    private companion object {
        const val N = 8
        const val STEP = 24.0
        const val BACK = 16.0
        const val AHEAD = 170.0
        const val STRIDE = 36
        const val PARTS = N * 6
    }

    private var skinEntity=0; private var clothesEntity=0; private var hairEntity=0
    private var skinVB:VertexBuffer?=null; private var skinIB:IndexBuffer?=null
    private var clothesVB:VertexBuffer?=null; private var clothesIB:IndexBuffer?=null
    private var hairVB:VertexBuffer?=null; private var hairIB:IndexBuffer?=null
    private var skin:MaterialInstance?=null; private var clothes:MaterialInstance?=null; private var hair:MaterialInstance?=null
    private var last=Double.NaN

    fun build():Boolean {
        if(skinEntity!=0) return true
        val src=findMaterial() ?: return false
        skin=dup(src,"LearnovaChildSkin") ?: return false
        clothes=dup(src,"LearnovaChildClothes") ?: return false
        hair=dup(src,"LearnovaChildHair") ?: return false
        paint(skin,0.78f,0.48f,0.32f,1f); paint(clothes,0.12f,0.46f,0.92f,1f); paint(hair,0.045f,0.025f,0.015f,1f)
        skinVB=vb(PARTS*8); skinIB=ib(PARTS*36); clothesVB=vb(PARTS*8); clothesIB=ib(PARTS*36); hairVB=vb(PARTS*8); hairIB=ib(PARTS*36)
        skinEntity=entity(skinVB!!,skinIB!!,skin!!); clothesEntity=entity(clothesVB!!,clothesIB!!,clothes!!); hairEntity=entity(hairVB!!,hairIB!!,hair!!)
        update(0.0); return true
    }

    fun update(center:Double) {
        if(skinEntity==0 || (!last.isNaN() && kotlin.math.abs(center-last)<5.0)) return
        val sv=buf(PARTS*8); val si=idx(PARTS*36); val cv=buf(PARTS*8); val ci=idx(PARTS*36); val hv=buf(PARTS*8); val hi=idx(PARTS*36)
        var d=kotlin.math.floor((center-BACK)/STEP)*STEP; var n=0
        while(d<=center+AHEAD && n<N) {
            val seed=seed(d); val b=WorldDirector.profile(d).biome
            val active=b==WorldDirector.Biome.VILLAGE || b==WorldDirector.Biome.MARKET || b==WorldDirector.Biome.FOREST || b==WorldDirector.Biome.RIVER || b==WorldDirector.Biome.COAST
            if(active) {
                val s=RoadSpline.sampleRelative(d,center); val side=if(seed and 1L==0L)-1.0 else 1.0
                val off=if(b==WorldDirector.Biome.VILLAGE||b==WorldDirector.Biome.MARKET) 7.0 else 9.0+((seed ushr 7)%35)/10.0
                val x=s.x+cos(s.yaw)*side*off; val z=s.z-sin(s.yaw)*side*off; val y=s.y+sin(s.bank)*side*off+sin(d*.07)*.07
                val q=.78+((seed ushr 13)%35)/100.0; val walk=sin(center*.18+n)
                box(sv,si,x,y+1.22*q,z,s.yaw.toDouble(),.25*q,.27*q,.25*q)
                box(sv,si,x-sin(s.yaw.toDouble())*.28*q,y+.73*q,z-cos(s.yaw.toDouble())*.28*q,s.yaw.toDouble(),.085*q,.20*q,.11*q+maxOf(0.0,walk)*.025*q)
                box(sv,si,x+sin(s.yaw.toDouble())*.28*q,y+.73*q,z+cos(s.yaw.toDouble())*.28*q,s.yaw.toDouble(),.085*q,.20*q,.11*q)
                box(cv,ci,x,y+.80*q,z,s.yaw.toDouble(),.23*q,.34*q,.14*q)
                box(cv,ci,x-.075*q,y+.38*q,z+walk*.045*q,s.yaw.toDouble(),.07*q,.34*q,.095*q)
                box(cv,ci,x+.075*q,y+.38*q,z-walk*.045*q,s.yaw.toDouble(),.07*q,.34*q,.095*q)
                box(hv,hi,x,y+1.49*q,z,s.yaw.toDouble(),.29*q,.09*q,.29*q)
                n++
            }
            d+=STEP
        }
        hide(sv,si); hide(cv,ci); hide(hv,hi); sv.flip();si.flip();cv.flip();ci.flip();hv.flip();hi.flip()
        skinVB!!.setBufferAt(engine,0,sv);skinIB!!.setBuffer(engine,si);clothesVB!!.setBufferAt(engine,0,cv);clothesIB!!.setBuffer(engine,ci);hairVB!!.setBufferAt(engine,0,hv);hairIB!!.setBuffer(engine,hi)
        last=center
    }

    private fun hide(v:ByteBuffer,i:ByteBuffer){
        val used=v.position()/STRIDE; var p=i.position()/2/36
        while(p<PARTS){ val base=p*8; repeat(12){i.putShort(base.toShort());i.putShort(base.toShort());i.putShort(base.toShort())}; repeat(8){put(v,0f,-5000f,0f,0f,0f,0f,0f)}; p++ }
    }

    private fun box(v:ByteBuffer,i:ByteBuffer,x:Double,y:Double,z:Double,yaw:Double,w:Double,h:Double,dep:Double){
        val base=v.position()/STRIDE; val cy=cos(yaw);val sy=sin(yaw)
        val c=arrayOf(doubleArrayOf(-w,0.0,-dep),doubleArrayOf(w,0.0,-dep),doubleArrayOf(w,0.0,dep),doubleArrayOf(-w,0.0,dep),doubleArrayOf(-w,h,-dep),doubleArrayOf(w,h,-dep),doubleArrayOf(w,h,dep),doubleArrayOf(-w,h,dep))
        for(p in c) put(v,(x+p[0]*cy-p[2]*sy).toFloat(),(y+p[1]).toFloat(),(z+p[0]*sy+p[2]*cy).toFloat(),yaw.toFloat(),0f,0f,0f)
        intArrayOf(0,1,2,0,2,3,4,6,5,4,7,6,0,4,5,0,5,1,1,5,6,1,6,2,2,6,7,2,7,3,4,0,3,4,3,7).forEach{ i.putShort((base+it).toShort()) }
    }

    private fun vb(n:Int)=VertexBuffer.Builder().vertexCount(n).bufferCount(1)
        .attribute(VertexBuffer.VertexAttribute.POSITION,0,VertexBuffer.AttributeType.FLOAT3,0,STRIDE)
        .attribute(VertexBuffer.VertexAttribute.TANGENTS,0,VertexBuffer.AttributeType.FLOAT4,12,STRIDE)
        .attribute(VertexBuffer.VertexAttribute.UV0,0,VertexBuffer.AttributeType.FLOAT2,28,STRIDE).build(engine)
    private fun ib(n:Int)=IndexBuffer.Builder().indexCount(n).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
    private fun buf(n:Int)=ByteBuffer.allocate(n*STRIDE).order(ByteOrder.nativeOrder())
    private fun idx(n:Int)=ByteBuffer.allocate(n*2).order(ByteOrder.nativeOrder())
    private fun entity(v:VertexBuffer,i:IndexBuffer,m:MaterialInstance):Int{val e=EntityManager.get().create();RenderableManager.Builder(1).material(0,m).geometry(0,RenderableManager.PrimitiveType.TRIANGLES,v,i).culling(false).receiveShadows(true).build(engine,e);scene.addEntity(e);return e}
    private fun dup(s:MaterialInstance,n:String)=try{MaterialInstance.duplicate(s,n)}catch(_:Throwable){null}
    private fun paint(m:MaterialInstance?,r:Float,g:Float,b:Float,a:Float){try{m?.setParameter("baseColor",r,g,b,a)}catch(_:Throwable){};try{m?.setParameter("metallic",0f);m?.setParameter("roughness",.84f);m?.setParameter("reflectance",.35f)}catch(_:Throwable){}}
    private fun put(b:ByteBuffer,x:Float,y:Float,z:Float,yaw:Float,bank:Float,u:Float,v:Float){b.putFloat(x);b.putFloat(y);b.putFloat(z);b.putFloat(0f);b.putFloat(0f);b.putFloat(0f);b.putFloat(1f);b.putFloat(u);b.putFloat(v)}
    private fun seed(d:Double):Long{var x=java.lang.Double.doubleToLongBits(kotlin.math.floor(d/STEP)*STEP);x=x xor(x ushr 33);x*=-49064778989728563L;x=x xor(x ushr 33);x*=-4265267296055464877L;return x xor(x ushr 33)}
    private fun findMaterial():MaterialInstance?{val rm=engine.renderableManager;for(n in arrayOf("Ground","Terrain","Grass","Landscape","ground","terrain")){val e=asset.getFirstEntityByName(n);if(e!=0&&rm.hasComponent(e)){val x=rm.getInstance(e);if(rm.getPrimitiveCount(x)>0)return rm.getMaterialInstanceAt(x,0)}};return null}
    fun destroy(){listOf(skinEntity,clothesEntity,hairEntity).forEach{if(it!=0){scene.removeEntity(it);engine.renderableManager.destroy(it);EntityManager.get().destroy(it)}};listOf(skinVB,clothesVB,hairVB).forEach{it?.let(engine::destroyVertexBuffer)};listOf(skinIB,clothesIB,hairIB).forEach{it?.let(engine::destroyIndexBuffer)};listOf(skin,clothes,hair).forEach{it?.let(engine::destroyMaterialInstance)};skinEntity=0;clothesEntity=0;hairEntity=0}
}