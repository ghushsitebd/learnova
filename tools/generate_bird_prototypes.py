#!/usr/bin/env python3
"""Build five original animated bird GLBs for Learnova's near-field wildlife.

The models are deterministic, self-contained glTF 2.0 binary assets. They are
procedural launch prototypes (not scans or photorealistic source models); each
has a species-specific silhouette/palette and a looping wing-flap animation.
"""
from __future__ import annotations
import json, math, struct
from pathlib import Path

OUT = Path("app/src/main/assets/creatures")
SPECIES = {
    "parrot": ((0.10,0.62,0.22,1), (0.92,0.12,0.08,1), (1.0,0.55,0.04,1), 3, "parrot"),
    "falcon": ((0.34,0.25,0.17,1), (0.18,0.16,0.14,1), (0.92,0.65,0.12,1), 2, "falcon"),
    "owl": ((0.48,0.30,0.16,1), (0.82,0.72,0.48,1), (0.92,0.58,0.08,1), 2, "owl"),
    "peacock": ((0.02,0.40,0.48,1), (0.05,0.22,0.34,1), (0.08,0.72,0.48,1), 7, "peacock"),
    "ostrich": ((0.40,0.38,0.34,1), (0.22,0.20,0.18,1), (0.88,0.68,0.50,1), 2, "ostrich"),
}
class GLB:
    def __init__(self):
        self.bin=bytearray(); self.views=[]; self.accessors=[]; self.meshes=[]; self.nodes=[]
    def align(self):
        while len(self.bin)%4: self.bin.append(0)
    def mesh(self, name, vertices, faces, material):
        positions=[]; normals=[]; indices=[]
        for face in faces:
            a,b,c=[vertices[i] for i in face]
            u=[b[i]-a[i] for i in range(3)]; v=[c[i]-a[i] for i in range(3)]
            n=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
            length=math.sqrt(sum(x*x for x in n)) or 1
            n=[x/length for x in n]
            base=len(positions)//3
            for p in (a,b,c): positions.extend(p); normals.extend(n)
            indices.extend((base,base+1,base+2))
        self.align(); po=len(self.bin); self.bin.extend(struct.pack("<"+"f"*len(positions),*positions)); pl=len(self.bin)-po
        self.align(); no=len(self.bin); self.bin.extend(struct.pack("<"+"f"*len(normals),*normals)); nl=len(self.bin)-no
        self.align(); io=len(self.bin); self.bin.extend(struct.pack("<"+"H"*len(indices),*indices)); il=len(self.bin)-io
        pv=len(self.views); self.views.append({"buffer":0,"byteOffset":po,"byteLength":pl,"target":34962})
        nv=len(self.views); self.views.append({"buffer":0,"byteOffset":no,"byteLength":nl,"target":34962})
        iv=len(self.views); self.views.append({"buffer":0,"byteOffset":io,"byteLength":il,"target":34963})
        xyz=list(zip(positions[0::3],positions[1::3],positions[2::3]))
        pa=len(self.accessors); self.accessors.append({"bufferView":pv,"componentType":5126,"count":len(positions)//3,"type":"VEC3","min":[min(p[i] for p in xyz) for i in range(3)],"max":[max(p[i] for p in xyz) for i in range(3)]})
        na=len(self.accessors); self.accessors.append({"bufferView":nv,"componentType":5126,"count":len(normals)//3,"type":"VEC3"})
        ia=len(self.accessors); self.accessors.append({"bufferView":iv,"componentType":5123,"count":len(indices),"type":"SCALAR","min":[min(indices)],"max":[max(indices)]})
        mi=len(self.meshes); self.meshes.append({"name":name,"primitives":[{"attributes":{"POSITION":pa,"NORMAL":na},"indices":ia,"material":material}]})
        return mi
    def ellipsoid(self, name, center, radii, material, segments=16, rings=10):
        verts=[]
        for r in range(rings+1):
            phi=math.pi*r/rings
            for s in range(segments+1):
                theta=2*math.pi*s/segments
                verts.append((center[0]+radii[0]*math.sin(phi)*math.cos(theta),
                              center[1]+radii[1]*math.cos(phi),
                              center[2]+radii[2]*math.sin(phi)*math.sin(theta)))
        faces=[]; stride=segments+1
        for r in range(rings):
            for s in range(segments):
                a=r*stride+s; b=a+stride
                faces.extend(((a,b,a+1),(a+1,b,b+1)))
        return self.mesh(name,verts,faces,material)
    def add_node(self, name, mesh, translation=(0,0,0), scale=(1,1,1), children=None):
        n={"name":name,"mesh":mesh,"translation":list(translation),"scale":list(scale)}
        if children is not None: n["children"]=children
        self.nodes.append(n); return len(self.nodes)-1

def build(species, body, accent, beak, tail_count, style):
    g=GLB()
    mats=[]
    for name,color,rough in (("feathers",body,.72),("accent",accent,.68),("beak",beak,.48),
                             ("eye",(0.012,.009,.006,1),.25),("white",(0.94,.91,.82,1),.55)):
        mats.append({"name":name,"pbrMetallicRoughness":{"baseColorFactor":list(color),"metallicFactor":0.0,"roughnessFactor":rough}})
    def part(name, center, radii, mat, seg=16):
        m=g.ellipsoid(name,center,radii,mat,seg,10)
        return g.add_node(name,m,translation=center)
    children=[]
    children.append(part(species+"_body",(0,0,0),(.46,.58,.36),0,20))
    if style=="ostrich":
        children.append(part("long_neck",(0,.55,0),(.13,.48,.13),0))
        children.append(part("small_head",(.04,.96,0),(.19,.18,.18),1))
        for z in (-.12,.12): children.append(part("leg",(0,-.62,z),(.07,.42,.07),1))
    else:
        children.append(part("head",(.22,.48,0),(.27,.27,.25),1))
    # Eyes on both sides; owl gets large facial disks.
    for side in (-1,1):
        if style=="owl": children.append(part("facial_disc",(.30,.48,side*.19),(.18,.19,.07),4))
        children.append(part("eye",(.34,.50,side*.235),(.055,.065,.035),3,12))
        if style=="owl": children.append(part("pupil",(.36,.50,side*.265),(.025,.035,.018),3,10))
    children.append(part("beak",(.48,.36,0),(.16,.10,.12),2,12))
    # Tail feathers form a fan for peacock, a narrow tail for the other species.
    for i in range(tail_count):
        spread=(i-(tail_count-1)/2)
        z=spread*(.12 if style=="peacock" else .07)
        length=.62 if style=="peacock" else .42
        children.append(part("tail_feather_"+str(i),(-.48,.02,z),(.12,.10,length*.45),1 if style=="peacock" else 0,12))
        if style=="peacock":
            children.append(part("tail_eye_"+str(i),(-.50,.08,z+(.15 if z>=0 else -.15)),(.06,.07,.055),2,10))
    # Animated wings are separate pivot nodes; geometry is mirrored on the Z axis.
    wing_mesh=g.ellipsoid("wing_mesh",(-.13,0,.42),(.42,.075,.36),1 if style in ("parrot","peacock") else 0,18,8)
    left=g.add_node("left_wing",wing_mesh,translation=(-.02,.18,-.25),scale=(1,1,-1))
    right=g.add_node("right_wing",wing_mesh,translation=(-.02,.18,.25))
    children.extend((left,right))
    # Owl tufts / parrot crest / peacock crest.
    if style in ("owl","parrot","peacock"):
        for side in (-1,1):
            children.append(part("crest",(0,.76,side*.13),(.07,.20,.07),1,10))
    root=len(g.nodes); g.nodes.append({"name":"BirdRoot","children":children})
    # Wing flap animation: 2-second looping quaternion curve.
    g.align(); time_offset=len(g.bin); times=[0.0,.5,1.0,1.5,2.0]
    g.bin.extend(struct.pack("<"+"f"*len(times),*times)); time_len=len(g.bin)-time_offset
    tv=len(g.views); g.views.append({"buffer":0,"byteOffset":time_offset,"byteLength":time_len})
    ta=len(g.accessors); g.accessors.append({"bufferView":tv,"componentType":5126,"count":len(times),"type":"SCALAR","min":[0.0],"max":[2.0]})
    angle=[0.0,.62,0.0,-.62,0.0]
    def quat(a): return (math.sin(a/2),0,0,math.cos(a/2))
    samplers=[]; channels=[]
    for node in (left,right):
        values=[quat(a if node==left else -a) for a in angle]
        g.align(); off=len(g.bin); flat=[v for q in values for v in q]
        g.bin.extend(struct.pack("<"+"f"*len(flat),*flat)); ln=len(g.bin)-off
        vv=len(g.views); g.views.append({"buffer":0,"byteOffset":off,"byteLength":ln})
        aa=len(g.accessors); g.accessors.append({"bufferView":vv,"componentType":5126,"count":5,"type":"VEC4"})
        samplers.append({"input":ta,"output":aa,"interpolation":"LINEAR"})
        channels.append({"sampler":len(samplers)-1,"target":{"node":node,"path":"rotation"}})
    doc={"asset":{"version":"2.0","generator":"Learnova procedural animated bird builder","extras":{"species":species,"animation":"looping_wing_flap"}},
         "scene":0,"scenes":[{"nodes":[root]}],"nodes":g.nodes,"meshes":g.meshes,"materials":mats,
         "buffers":[{"byteLength":len(g.bin)}],"bufferViews":g.views,"accessors":g.accessors,
         "animations":[{"name":"WingFlap","samplers":samplers,"channels":channels}]}
    js=json.dumps(doc,separators=(",",":")).encode(); js+=b" "*((4-len(js)%4)%4)
    binary=bytes(g.bin); binary+=b"\0"*((4-len(binary)%4)%4)
    total=12+8+len(js)+8+len(binary)
    data=b"glTF"+struct.pack("<II",2,total)+struct.pack("<II",len(js),0x4E4F534A)+js+struct.pack("<II",len(binary),0x004E4942)+binary
    OUT.mkdir(parents=True,exist_ok=True); path=OUT/(species+".glb"); path.write_bytes(data)
    print(f"Generated {species}: {len(data)} bytes, animated wings")

def main():
    for species, values in SPECIES.items(): build(species,*values)
if __name__=="__main__": main()
