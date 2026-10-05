package com.learnova.app

import com.google.android.filament.*
import com.google.android.filament.gltfio.FilamentAsset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/** Lightweight permanent animal friend that stays with the selected vehicle. */
internal class VehicleFriendWorld(
    private val engine: Engine,
    private val scene: Scene,
    private val asset: FilamentAsset
) {
    private var bodyEntity=0
    private var headEntity=0
    private var earEntity=0
    private var armEntity=0
    private var bodyVB:VertexBuffer?=null
    private var headVB:VertexBuffer?=null
    private var earVB:VertexBuffer?=null
    private var armVB:VertexBuffer?=null
    private var bodyIB:IndexBuffer?=null
    private var headIB:IndexBuffer?=null
    private var earIB:IndexBuffer?=null
    private var armIB:IndexBuffer?=null
    private var bodyMat:MaterialInstance?=null
    private var headMat:MaterialInstance?=null
    private var earMat:MaterialInstance?=null
    private var armMat:MaterialInstance?=null
    private var friend="Fox"
    private var driving=false
    private var outside=false
    private var t=0.0
    private var lastCenter=Double.NaN
    private var built=false

    fun build():Boolean {
        if(built) return true
        val src=findMaterial() ?: return false
        bodyMat=dup(src,"LearnovaFriendBody") ?: return false
        headMat=dup(src,"LearnovaFriendHead") ?: return false
        earMat=dup(src,"LearnovaFriendEar") ?: return false
        armMat=dup(src,"LearnovaFriendArm") ?: return false
        paint(bodyMat,0.95f,0.48f,0.10f)
        paint(headMat,0.95f,0.48f,0.10f)
        paint(earMat,0.25f,0.10f,0.035f)
        paint(armMat,0.95f,0.48f,0.10f)
        bodyVB=vb(8); headVB=vb(8); earVB=vb(16); armVB=vb(8)
        bodyIB=ib(36); headIB=ib(36); earIB=ib(72); armIB=ib(36)
        bodyEntity=entity(bodyVB!!,bodyIB!!,bodyMat!!)
        headEntity=entity(headVB!!,headIB!!,headMat!!)
        earEntity=entity(earVB!!,earIB!!,earMat!!)
        armEntity=entity(armVB!!,armIB!!,armMat!!)
        built=true
        update(0.0)
        return true
    }

    fun setFriend(name:String) {
        friend=name
        val lower=name.lowercase()
        val palette=when {
            lower.contains("bear") -> floatArrayOf(.30f,.16f,.08f)
            lower.contains("monkey") -> floatArrayOf(.62f,.34f,.16f)
            lower.contains("rabbit") -> floatArrayOf(.92f,.86f,.82f)
            lower.contains("panda") -> floatArrayOf(.12f,.12f,.12f)
            else -> floatArrayOf(.95f,.48f,.10f)
        }
        paint(bodyMat,palette[0],palette[1],palette[2])
        paint(headMat,palette[0],palette[1],palette[2])
        paint(armMat,palette[0],palette[1],palette[2])
    }

    fun setDriving(value:Boolean) { driving=value }
    fun setOutside(value:Boolean) { outside=value }

    fun update(center:Double) {
        if(!built) return
        t += 1.0/60.0
        if(!lastCenter.isNaN() && kotlin.math.abs(center-lastCenter)<0.12 && (t*60.0).toInt()%2!=0) return
        lastCenter=center
        val s=RoadSpline.sampleRelative(center,center)
        val phase=if(driving) t*7.0 else t*2.0
        val bob=if(driving) sin(phase)*.035 else sin(phase)*.018
        val wave=if(driving) (sin(t*3.6)*.10).coerceIn(-.10,.10) else sin(t*1.8)*.03
        val side=0.92
        val x=s.x+cos(s.yaw)*walkSide
        val z=s.z-sin(s.yaw)*walkSide
        val walkPhase = sin(t * 1.7) * 1.8
        val walkSide = if (outside) side + walkPhase else side
        val hiddenY=if(outside) s.y + bob else s.y + bob
        val scale=when {
            friend.lowercase().contains("bear") -> 1.12
            friend.lowercase().contains("rabbit") -> .92
            friend.lowercase().contains("monkey") -> .98
            else -> 1.0
        }

        val bv=buf(); val bi=idx()
        val hv=buf(); val hi=idx()
        val ev=buf(2); val ei=idx(2)
        val av=buf(); val ai=idx()
        box(bv,bi,x,hiddenY+0.48*scale,z,s.yaw.toDouble(),.20*scale,.48*scale,.18*scale)
        box(hv,hi,x,hiddenY+1.04*scale,z,s.yaw.toDouble(),.27*scale,.28*scale,.24*scale)
        box(ev,ei,x-.18*scale,hiddenY+1.34*scale,z,s.yaw.toDouble(),.09*scale,.18*scale,.08*scale)
        box(ev,ei,x+.18*scale,hiddenY+1.34*scale,z,s.yaw.toDouble(),.09*scale,.18*scale,.08*scale)
        box(av,ai,x+sin(s.yaw)*(.28*scale+wave),hiddenY+.68*scale,z+cos(s.yaw)*(.28*scale+wave),s.yaw.toDouble(),w=.075*scale,h=.34*scale,dep=.075*scale)
        bv.flip();bi.flip();hv.flip();hi.flip();ev.flip();ei.flip();av.flip();ai.flip()
        bodyVB!!.setBufferAt(engine,0,bv); bodyIB!!.setBuffer(engine,bi)
        headVB!!.setBufferAt(engine,0,hv); headIB!!.setBuffer(engine,hi)
        earVB!!.setBufferAt(engine,0,ev); earIB!!.setBuffer(engine,ei)
        armVB!!.setBufferAt(engine,0,av); armIB!!.setBuffer(engine,ai)
    }

    private fun buf(parts:Int=1)=ByteBuffer.allocate(parts*8*36).order(ByteOrder.nativeOrder())
    private fun idx(parts:Int=1)=ByteBuffer.allocate(parts*36*2).order(ByteOrder.nativeOrder())

    private fun box(v:ByteBuffer,i:ByteBuffer,x:Double,y:Double,z:Double,yaw:Double,w:Double,h:Double,dep:Double) {
        val base=v.position()/36; val cy=cos(yaw); val sy=sin(yaw)
        val c=arrayOf(
            doubleArrayOf(-w,0.0,-dep),doubleArrayOf(w,0.0,-dep),
            doubleArrayOf(w,0.0,dep),doubleArrayOf(-w,0.0,dep),
            doubleArrayOf(-w,h,-dep),doubleArrayOf(w,h,-dep),
            doubleArrayOf(w,h,dep),doubleArrayOf(-w,h,dep)
        )
        for(p in c) put(v,(x+p[0]*cy-p[2]*sy).toFloat(),(y+p[1]).toFloat(),(z+p[0]*sy+p[2]*cy).toFloat(),yaw.toFloat())
        intArrayOf(0,1,2,0,2,3,4,6,5,4,7,6,0,4,5,0,5,1,1,5,6,1,6,2,2,6,7,2,7,3,4,0,3,4,3,7).forEach{ i.putShort((base+it).toShort()) }
    }

    private fun put(b:ByteBuffer,x:Float,y:Float,z:Float,yaw:Float){
        b.putFloat(x);b.putFloat(y);b.putFloat(z);b.putFloat(0f);b.putFloat(0f);b.putFloat(0f);b.putFloat(1f);b.putFloat(0f);b.putFloat(0f)
    }
    private fun vb(n:Int)=VertexBuffer.Builder().vertexCount(n).bufferCount(1)
        .attribute(VertexBuffer.VertexAttribute.POSITION,0,VertexBuffer.AttributeType.FLOAT3,0,36)
        .attribute(VertexBuffer.VertexAttribute.TANGENTS,0,VertexBuffer.AttributeType.FLOAT4,12,36)
        .attribute(VertexBuffer.VertexAttribute.UV0,0,VertexBuffer.AttributeType.FLOAT2,28,36).build(engine)
    private fun ib(n:Int)=IndexBuffer.Builder().indexCount(n).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
    private fun entity(v:VertexBuffer,i:IndexBuffer,m:MaterialInstance):Int {
        val e=EntityManager.get().create()
        RenderableManager.Builder(1).material(0,m).geometry(0,RenderableManager.PrimitiveType.TRIANGLES,v,i).culling(false).receiveShadows(true).build(engine,e)
        scene.addEntity(e); return e
    }
    private fun dup(s:MaterialInstance,n:String)=try{MaterialInstance.duplicate(s,n)}catch(_:Throwable){null}
    private fun paint(m:MaterialInstance?,r:Float,g:Float,b:Float){try{m?.setParameter("baseColor",r,g,b,1f)}catch(_:Throwable){};try{m?.setParameter("metallic",0f);m?.setParameter("roughness",.82f);m?.setParameter("reflectance",.35f)}catch(_:Throwable){}}
    private fun findMaterial():MaterialInstance? {
        val rm=engine.renderableManager
        for(n in arrayOf("Ground","Terrain","Grass","Landscape","ground","terrain")){
            val e=asset.getFirstEntityByName(n)
            if(e!=0&&rm.hasComponent(e)){val x=rm.getInstance(e);if(rm.getPrimitiveCount(x)>0)return rm.getMaterialInstanceAt(x,0)}
        }
        return null
    }
    fun destroy(){
        listOf(bodyEntity,headEntity,earEntity,armEntity).forEach{if(it!=0){scene.removeEntity(it);engine.renderableManager.destroy(it);EntityManager.get().destroy(it)}}
        listOf(bodyVB,headVB,earVB,armVB).forEach{it?.let(engine::destroyVertexBuffer)}
        listOf(bodyIB,headIB,earIB,armIB).forEach{it?.let(engine::destroyIndexBuffer)}
        listOf(bodyMat,headMat,earMat,armMat).forEach{it?.let(engine::destroyMaterialInstance)}
        bodyEntity=0;headEntity=0;earEntity=0;armEntity=0;built=false
    }
}
