#!/usr/bin/env python3
"""Generate scale-aware GLB models for the first 100 launch garage slots.

Four authored upstream GLBs are fetched first and are never overwritten. This
script fills only missing slots with original procedural prototypes. Dimensions
are expressed in meters; these are prototype models, not claims of photorealism
or exact replicas of trademarked vehicles.
"""
from __future__ import annotations

import json
import math
import re
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app/src/main/java/com/learnova/app/VehicleCatalog.kt"
OUT = ROOT / "app/src/main/assets/vehicles"
VEHICLE_COUNT = 100
JSON_CHUNK = 0x4E4F534A
BIN_CHUNK = 0x004E4942

PALETTE = [
    (0.80, 0.12, 0.10, 1.0), (0.08, 0.28, 0.72, 1.0),
    (0.06, 0.48, 0.34, 1.0), (0.92, 0.52, 0.08, 1.0),
    (0.62, 0.12, 0.68, 1.0), (0.08, 0.56, 0.68, 1.0),
    (0.78, 0.78, 0.78, 1.0), (0.16, 0.18, 0.21, 1.0),
    (0.82, 0.72, 0.48, 1.0), (0.72, 0.18, 0.35, 1.0),
]
GLASS = (0.12, 0.30, 0.38, 1.0)
RUBBER = (0.035, 0.04, 0.045, 1.0)
TRIM = (0.68, 0.72, 0.75, 1.0)
LIGHT = (1.0, 0.83, 0.48, 1.0)
RE = re.compile(r'VehicleDefinition\(\s*(\d+)\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"')


def dims(kind: str, name: str, vehicle_id: int):
    n = name.lower()
    if kind == "dinosaur_mount":
        length, width, height, wheel = 3.8, 1.55, 2.35, 0.18
    elif kind == "bus" or "bus" in n:
        length, width, height, wheel = 10.2, 2.50, 3.15, 0.48
    elif kind == "truck" or kind in {"commercial", "construction", "farm", "airport"}:
        length, width, height, wheel = 6.4, 2.35, 2.75, 0.48
        if "tractor" in n or "loader" in n or "hauler" in n:
            length, height = 5.2, 3.0
    elif kind in {"motorcycle", "cycle"} or any(x in n for x in ("bike", "bicycle", "tricycle", "scooter")):
        length, width, height, wheel = (2.05, 0.78, 1.35, 0.34) if kind != "cycle" else (1.82, 0.62, 1.18, 0.36)
    elif kind == "three_wheeler":
        length, width, height, wheel = 2.75, 1.34, 1.82, 0.34
    elif kind in {"concept", "electric", "luxury", "sport"}:
        length, width, height, wheel = 4.65, 1.92, 1.42, 0.36
    elif kind in {"offroad", "suv", "pickup", "safari", "rescue"}:
        length, width, height, wheel = 4.85, 2.02, 1.95, 0.42
    elif kind in {"van", "service", "emergency", "micro"}:
        length, width, height, wheel = 4.45, 1.88, 1.90, 0.36
    else:
        length, width, height, wheel = 4.25, 1.82, 1.48, 0.34
    # Small deterministic proportions differentiate each prototype while
    # keeping each class inside plausible real-world dimensions.
    length *= 0.94 + ((vehicle_id * 7) % 13) * 0.01
    width *= 0.96 + ((vehicle_id * 5) % 7) * 0.01
    height *= 0.95 + ((vehicle_id * 3) % 9) * 0.012
    return length, width, height, wheel


def cube(cx, cy, cz, sx, sy, sz):
    x0, x1 = cx - sx / 2, cx + sx / 2
    y0, y1 = cy - sy / 2, cy + sy / 2
    z0, z1 = cz - sz / 2, cz + sz / 2
    faces = [
        (((x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)), (0,0,1)),
        (((x1,y0,z0),(x0,y0,z0),(x0,y1,z0),(x1,y1,z0)), (0,0,-1)),
        (((x1,y0,z1),(x1,y0,z0),(x1,y1,z0),(x1,y1,z1)), (1,0,0)),
        (((x0,y0,z0),(x0,y0,z1),(x0,y1,z1),(x0,y1,z0)), (-1,0,0)),
        (((x0,y1,z1),(x1,y1,z1),(x1,y1,z0),(x0,y1,z0)), (0,1,0)),
        (((x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)), (0,-1,0)),
    ]
    p, n, ix = [], [], []
    for corners, normal in faces:
        base = len(p) // 3
        for v in corners:
            p.extend(v); n.extend(normal)
        ix.extend((base,base+1,base+2,base,base+2,base+3))
    return p, n, ix



def tapered_box(cx, cy, cz, sx, sy, sz, top_sx_ratio=0.92, top_sz_ratio=0.94):
    """Flat-shaded beveled/tapered body prism for less toy-like silhouettes."""
    bottom = [
        (cx-sx/2, cy-sy/2, cz-sz/2), (cx+sx/2, cy-sy/2, cz-sz/2),
        (cx+sx/2, cy-sy/2, cz+sz/2), (cx-sx/2, cy-sy/2, cz+sz/2),
    ]
    top_sx, top_sz = sx*top_sx_ratio, sz*top_sz_ratio
    top = [
        (cx-top_sx/2, cy+sy/2, cz-top_sz/2), (cx+top_sx/2, cy+sy/2, cz-top_sz/2),
        (cx+top_sx/2, cy+sy/2, cz+top_sz/2), (cx-top_sx/2, cy+sy/2, cz+top_sz/2),
    ]
    faces = [
        [bottom[0], bottom[3], bottom[2], bottom[1]],
        [top[0], top[1], top[2], top[3]],
        [bottom[0], bottom[1], top[1], top[0]],
        [bottom[1], bottom[2], top[2], top[1]],
        [bottom[2], bottom[3], top[3], top[2]],
        [bottom[3], bottom[0], top[0], top[3]],
    ]
    p, n, ix = [], [], []
    center = (cx, cy, cz)
    for corners in faces:
        a, b, d = corners[0], corners[1], corners[2]
        u = (b[0]-a[0], b[1]-a[1], b[2]-a[2])
        v = (d[0]-a[0], d[1]-a[1], d[2]-a[2])
        normal = (u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0])
        face_center = tuple(sum(point[i] for point in corners)/4 for i in range(3))
        outward = tuple(face_center[i]-center[i] for i in range(3))
        if sum(normal[i]*outward[i] for i in range(3)) < 0:
            corners = list(reversed(corners))
            normal = tuple(-value for value in normal)
        magnitude = math.sqrt(sum(value*value for value in normal)) or 1.0
        normal = tuple(value/magnitude for value in normal)
        base = len(p)//3
        for point in corners:
            p.extend(point)
            n.extend(normal)
        ix.extend((base, base+1, base+2, base, base+2, base+3))
    return p, n, ix


def wheel(cx, cy, cz, radius, depth, segments=20):
    p, n, ix = [], [], []
    # Cylinder axis is Z, so the wheel face points out from the vehicle sides.
    for i in range(segments):
        a0 = 2 * math.pi * i / segments
        a1 = 2 * math.pi * (i + 1) / segments
        base = len(p) // 3
        for z, a in ((cz-depth/2,a0),(cz+depth/2,a0),(cz+depth/2,a1),(cz-depth/2,a1)):
            p.extend((cx + radius*math.cos(a), cy + radius*math.sin(a), z))
            n.extend((math.cos(a), math.sin(a), 0))
        ix.extend((base,base+1,base+2,base,base+2,base+3))
    return p, n, ix


def ellipsoid(cx, cy, cz, rx, ry, rz, segments=12, rings=8):
    """Compact UV sphere with scaled normals for dinosaur bodies and riders."""
    positions, normals, indices = [], [], []
    for ring in range(rings + 1):
        phi = math.pi * ring / rings
        for segment in range(segments + 1):
            theta = 2.0 * math.pi * segment / segments
            nx = math.sin(phi) * math.cos(theta)
            ny = math.cos(phi)
            nz = math.sin(phi) * math.sin(theta)
            positions.extend((cx + rx * nx, cy + ry * ny, cz + rz * nz))
            vx, vy, vz = nx / max(rx, 1e-5), ny / max(ry, 1e-5), nz / max(rz, 1e-5)
            norm = math.sqrt(vx*vx + vy*vy + vz*vz) or 1.0
            normals.extend((vx/norm, vy/norm, vz/norm))
    stride = segments + 1
    for ring in range(rings):
        for segment in range(segments):
            a = ring * stride + segment
            b = a + stride
            indices.extend((a, a+1, b, a+1, b+1, b))
    return positions, normals, indices


def make_glb(path: Path, vehicle_id: int, name: str, kind: str):
    length, width, height, radius = dims(kind, name, vehicle_id)
    body_y = radius * 1.15 + max(0.28, height * 0.22)
    body_h = max(0.42, height * 0.38)
    body_len = length * (0.78 if kind not in {"bus", "truck"} else 0.88)
    body_w = width * 0.94
    paint = PALETTE[(vehicle_id * 3 + vehicle_id // 7) % len(PALETTE)]
    parts = []

    def add(shape, material):
        p, n, ix = shape
        parts.append((p, n, ix, material))

    if kind == "dinosaur_mount":
        # Distinct prehistoric mounts: body, four legs, tail, head, saddle and seated rider.
        lower_name = name.lower()
        paint = {
            "trex": (0.36, 0.49, 0.22, 1.0),
            "triceratops": (0.56, 0.39, 0.22, 1.0),
            "stegosaurus": (0.20, 0.48, 0.40, 1.0),
            "parasaurolophus": (0.70, 0.47, 0.16, 1.0),
        }.get("trex" if "trex" in lower_name else
              "triceratops" if "triceratops" in lower_name else
              "stegosaurus" if "stegosaurus" in lower_name else "parasaurolophus",
              PALETTE[2])
        add(ellipsoid(-0.08, 0.98, 0, 0.88, 0.56, 0.57), 0)
        add(ellipsoid(-0.02, 0.69, 0, 0.67, 0.24, 0.43), 1)
        add(ellipsoid(-0.54, 1.05, 0, 0.48, 0.49, 0.50), 0)
        for lx in (-0.52, 0.43):
            for side in (-1, 1):
                add(ellipsoid(lx, 0.43, side*0.36, 0.22, 0.43, 0.20), 0)
                add(ellipsoid(lx + 0.05, 0.13, side*0.39, 0.27, 0.13, 0.23), 1)
        add(ellipsoid(-1.08, 0.97, 0, 0.55, 0.29, 0.30), 0)
        add(ellipsoid(-1.52, 0.91, 0, 0.43, 0.18, 0.19), 0)
        add(ellipsoid(0.61, 1.26, 0, 0.31, 0.42, 0.34), 0)
        add(ellipsoid(0.88, 1.51, 0, 0.39, 0.30, 0.34), 0)
        add(ellipsoid(1.14, 1.39, 0, 0.32, 0.18, 0.28), 1)
        for side in (-1, 1):
            add(ellipsoid(0.92, 1.59, side*0.29, 0.065, 0.07, 0.045, 10, 6), 9)
        if "triceratops" in lower_name:
            add(ellipsoid(0.76, 1.76, 0, 0.34, 0.13, 0.30), 1)
            for side in (-1, 1):
                add(tapered_box(0.92, 1.83, side*0.18, 0.10, 0.29, 0.10, 0.28, 0.28), 1)
                add(tapered_box(1.23, 1.49, side*0.13, 0.09, 0.20, 0.09, 0.25, 0.25), 1)
        elif "stegosaurus" in lower_name:
            for plate_index, px in enumerate((-0.72, -0.43, -0.14, 0.15, 0.42)):
                add(ellipsoid(px, 1.53 + (plate_index % 2)*0.05, 0, 0.14, 0.29, 0.07), 1)
            add(ellipsoid(1.05, 1.49, 0, 0.28, 0.18, 0.26), 0)
        elif "parasaurolophus" in lower_name:
            add(ellipsoid(0.83, 1.80, 0, 0.18, 0.32, 0.15), 1)
            add(ellipsoid(1.02, 1.98, 0, 0.30, 0.11, 0.12), 1)
        else:
            for side in (-1, 1):
                add(ellipsoid(0.47, 0.96, side*0.38, 0.12, 0.25, 0.11), 0)
                add(ellipsoid(0.55, 0.76, side*0.39, 0.12, 0.09, 0.10), 1)
        add(ellipsoid(-0.10, 1.48, 0, 0.48, 0.13, 0.48), 4)
        add(ellipsoid(-0.08, 1.68, 0, 0.18, 0.25, 0.17), 8)
        add(ellipsoid(-0.02, 1.99, 0, 0.16, 0.17, 0.16), 7)
        add(ellipsoid(-0.02, 2.14, 0, 0.17, 0.065, 0.17), 8)
        for side in (-1, 1):
            add(ellipsoid(0.04, 1.48, side*0.28, 0.20, 0.11, 0.10), 8)
            add(ellipsoid(0.11, 1.67, side*0.19, 0.08, 0.20, 0.08), 7)
    else:
        add(tapered_box(0, body_y, 0, body_len, body_h, body_w, 0.97, 0.96), 0)
        # A bus/truck cargo body is taller; passenger vehicles get a sloped-looking
        # smaller cabin placed on the chassis. Low-poly forms keep mobile draw cost low.
        cabin_h = max(0.42, height - (body_y + body_h * 0.52) - radius * 0.65)
        cabin_len = length * (0.38 if kind in {"bus", "truck", "commercial", "airport"} else 0.42)
        cabin_w = width * (0.86 if kind in {"bus", "truck"} else 0.78)
        cabin_x = -length * 0.04 if kind in {"bus", "truck", "van"} else -length * 0.02
        cabin_y = body_y + body_h * 0.48 + cabin_h * 0.5
        add(tapered_box(cabin_x, cabin_y, 0, cabin_len, cabin_h, cabin_w, 0.78, 0.86), 1)
        # Individual glazing panes and pillars make vehicle classes readable at game distance.
        glass_y = cabin_y + cabin_h * 0.06
        glass_h = max(0.16, cabin_h * 0.46)
        pane_count = 6 if kind == "bus" or "bus" in name.lower() else (4 if kind in {"van", "commercial"} else 2)
        pane_gap = min(0.055, cabin_len * 0.035)
        pane_len = (cabin_len * 0.76 - pane_gap * (pane_count - 1)) / pane_count
        pane_start = cabin_x - (pane_len * pane_count + pane_gap * (pane_count - 1)) / 2 + pane_len / 2
        for side in (-1, 1):
            for pane in range(pane_count):
                px = pane_start + pane * (pane_len + pane_gap)
                add(cube(px, glass_y, side * (cabin_w/2 + 0.012), pane_len, glass_h, 0.022), 2)
        # Broad angled-looking front windscreen, rear glass, and front/rear bumpers.
        add(cube(length * 0.19, glass_y, 0, max(0.16, cabin_len*0.20), glass_h*0.82, cabin_w*0.90), 2)
        add(cube(-length * 0.22, glass_y, 0, max(0.12, cabin_len*0.12), glass_h*0.70, cabin_w*0.84), 2)
        add(cube(length*0.465, body_y - body_h*0.18, 0, 0.13, 0.13, width*0.98), 4)
        add(cube(-length*0.465, body_y - body_h*0.18, 0, 0.12, 0.12, width*0.98), 4)
    
        # Wheels: two inline wheels for bikes; four road wheels for cars and utility vehicles.
        is_bike = kind in {"motorcycle", "cycle"} or any(x in name.lower() for x in ("bike", "bicycle", "tricycle", "scooter"))
        wheel_xs = (-length * 0.36, length * 0.36) if is_bike else (-length * 0.32, length * 0.32)
        wheel_radius = radius
        wheel_tracks = (0,) if is_bike else (-width * 0.46, width * 0.46)
        for x in wheel_xs:
            for z in wheel_tracks:
                # Farm tractors use visibly larger rear drive wheels; heavy vehicles
                # get a little more tire mass without changing their catalog scale.
                tire_radius = wheel_radius
                if kind == "farm" and x < 0:
                    tire_radius *= 1.32
                elif kind in {"truck", "bus", "construction"}:
                    tire_radius *= 1.06
                add(wheel(x, tire_radius, z, tire_radius, max(0.12, width*0.095), segments=20), 3)
                add(wheel(x, tire_radius, z + (0.008 if z >= 0 else -0.008), tire_radius*0.48,
                          max(0.125, width*0.098), segments=20), 4)
    
        # Grille, layered lamp clusters, mirrors, door handles, side sill and trim.
        add(cube(length*0.462, body_y + body_h*0.02, 0, 0.035, body_h*0.30, width*0.28), 4)
        for side in (-1, 1):
            add(cube(cabin_x + cabin_len*0.24, cabin_y - cabin_h*0.02,
                     side*(cabin_w/2 + 0.075), 0.16, 0.075, 0.13), 4)
            add(cube(cabin_x + cabin_len*0.22, cabin_y + cabin_h*0.02,
                     side*(cabin_w/2 + 0.14), 0.10, 0.075, 0.10), 2)
            if not is_bike:
                add(cube(0, body_y - body_h*0.36, side*(width*0.475), body_len*0.72, 0.055, 0.035), 4)
                # Door handles provide a small but useful realism cue in close-up views.
                for door_x in (-length*0.08, length*0.12):
                    add(cube(door_x, body_y + body_h*0.22, side*(body_w*0.475 + 0.012), 0.12, 0.025, 0.018), 4)
    
        # Distinct utility silhouettes: pickup bed, roof rails, and tall commercial fronts.
        if "pickup" in name.lower() or "farm" in name.lower():
            add(cube(-length*0.25, body_y + body_h*0.62, 0, length*0.27, 0.10, body_w*0.80), 1)
            for side in (-1, 1):
                add(cube(-length*0.25, body_y + body_h*0.83, side*body_w*0.39,
                         length*0.28, 0.34, 0.055), 0)
        if kind in {"suv", "offroad", "rescue", "safari"} or any(x in name.lower() for x in ("jeep", "safari", "rescue")):
            for side in (-1, 1):
                add(cube(cabin_x, cabin_y + cabin_h*0.52, side*cabin_w*0.43,
                         cabin_len*0.92, 0.045, 0.045), 4)
        # Functional class cues: emergency beacons/stripes, taxi roof sign,
        # bus entrance doors, and exposed utility exhausts improve silhouette reads.
        lower_name = name.lower()
        if kind == "emergency" or any(word in lower_name for word in ("ambulance", "police", "fire", "rescue")):
            beacon_color = 5 if ("ambulance" in lower_name or "police" in lower_name) else 6
            add(cube(cabin_x, cabin_y + cabin_h*0.56, 0, cabin_len*0.42, 0.075, cabin_w*0.48), beacon_color)
            for side in (-1, 1):
                add(cube(length*0.02, body_y + body_h*0.04, side*(body_w*0.49),
                         body_len*0.48, 0.11, 0.025), 5 if "police" in lower_name else 6)
        if "taxi" in lower_name:
            add(tapered_box(cabin_x, cabin_y + cabin_h*0.60, 0, 0.28, 0.11, 0.22, 0.92, 0.92), 5)
        if kind == "bus" or "bus" in lower_name:
            add(cube(length*0.12, body_y + body_h*0.05, width*0.478, 0.075, body_h*0.72, 0.025), 4)
        if kind in {"farm", "construction"} or any(word in lower_name for word in ("tractor", "loader", "hauler")):
            add(cube(-length*0.16, cabin_y + cabin_h*0.40, -width*0.30, 0.075, cabin_h*0.95, 0.075), 4)
            add(cube(-length*0.18, cabin_y + cabin_h*0.88, -width*0.30, 0.28, 0.07, 0.07), 4)
    
        # Headlights, rear lamps and indicator blocks.
        for z in (-width*0.34, width*0.34):
            add(cube(length*0.458, body_y + body_h*0.18, z, 0.075, 0.12, 0.14), 5)
            add(cube(length*0.462, body_y + body_h*0.05, z, 0.035, 0.055, 0.09), 4)
            add(cube(-length*0.458, body_y + body_h*0.18, z, 0.07, 0.11, 0.13), 6)
    
    
    materials = [
        {"name":"paint", "pbrMetallicRoughness":{"baseColorFactor":paint,"metallicFactor":0.28,"roughnessFactor":0.32}},
        {"name":"roof", "pbrMetallicRoughness":{"baseColorFactor":[paint[0]*0.82,paint[1]*0.82,paint[2]*0.82,1],"metallicFactor":0.2,"roughnessFactor":0.4}},
        {"name":"glass", "pbrMetallicRoughness":{"baseColorFactor":GLASS,"metallicFactor":0.15,"roughnessFactor":0.18}},
        {"name":"rubber", "pbrMetallicRoughness":{"baseColorFactor":RUBBER,"metallicFactor":0.0,"roughnessFactor":0.92}},
        {"name":"wheel_hub", "pbrMetallicRoughness":{"baseColorFactor":TRIM,"metallicFactor":0.72,"roughnessFactor":0.28}},
        {"name":"headlight", "pbrMetallicRoughness":{"baseColorFactor":LIGHT,"metallicFactor":0.05,"roughnessFactor":0.2}},
        {"name":"tail_light", "pbrMetallicRoughness":{"baseColorFactor":[0.85,0.025,0.02,1],"metallicFactor":0.0,"roughnessFactor":0.25}},
        {"name":"explorer_skin", "pbrMetallicRoughness":{"baseColorFactor":[0.82,0.57,0.36,1],"metallicFactor":0.0,"roughnessFactor":0.8}},
        {"name":"explorer_clothes", "pbrMetallicRoughness":{"baseColorFactor":[0.96,0.62,0.10,1],"metallicFactor":0.0,"roughnessFactor":0.7}},
        {"name":"dinosaur_eye", "pbrMetallicRoughness":{"baseColorFactor":[0.025,0.018,0.012,1],"metallicFactor":0.0,"roughnessFactor":0.45}},
    ]
    binary = bytearray()
    views, accessors, meshes, nodes = [], [], [], []

    def align4():
        while len(binary) % 4:
            binary.append(0)

    for idx, (positions, normals, indices, material) in enumerate(parts):
        align4(); pos_offset = len(binary)
        binary.extend(struct.pack("<" + "f"*len(positions), *positions))
        pos_length = len(binary)-pos_offset
        align4(); norm_offset = len(binary)
        binary.extend(struct.pack("<" + "f"*len(normals), *normals))
        norm_length = len(binary)-norm_offset
        align4(); ind_offset = len(binary)
        binary.extend(struct.pack("<" + "H"*len(indices), *indices))
        ind_length = len(binary)-ind_offset
        pv = len(views); views.append({"buffer":0,"byteOffset":pos_offset,"byteLength":pos_length,"target":34962})
        nv = len(views); views.append({"buffer":0,"byteOffset":norm_offset,"byteLength":norm_length,"target":34962})
        iv = len(views); views.append({"buffer":0,"byteOffset":ind_offset,"byteLength":ind_length,"target":34963})
        xyz = list(zip(positions[0::3], positions[1::3], positions[2::3]))
        pa = len(accessors); accessors.append({"bufferView":pv,"componentType":5126,"count":len(positions)//3,"type":"VEC3","min":[min(v[i] for v in xyz) for i in range(3)],"max":[max(v[i] for v in xyz) for i in range(3)]})
        na = len(accessors); accessors.append({"bufferView":nv,"componentType":5126,"count":len(normals)//3,"type":"VEC3"})
        ia = len(accessors); accessors.append({"bufferView":iv,"componentType":5123,"count":len(indices),"type":"SCALAR","min":[min(indices)],"max":[max(indices)]})
        meshes.append({"name":f"{name}_part_{idx}","primitives":[{"attributes":{"POSITION":pa,"NORMAL":na},"indices":ia,"material":material,"mode":4}]})
        nodes.append({"name":f"{name}_part_{idx}","mesh":idx})

    # Put every generated part beneath one named root. The runtime applies the
    # authoritative road/chassis transform to VehicleRoot; a flat scene would leave
    # these procedural prototypes stationary while the world moves around them.
    part_node_count = len(nodes)
    nodes.append({"name":"VehicleRoot","children":list(range(part_node_count))})
    document = {
        "asset":{"version":"2.0","generator":"Learnova scale-aware vehicle prototype generator"},
        "scene":0,"scenes":[{"nodes":[part_node_count]}],
        "nodes":nodes,"meshes":meshes,"materials":materials,
        "buffers":[{"byteLength":len(binary)}],"bufferViews":views,"accessors":accessors,
    }
    json_bytes = json.dumps(document,separators=(",",":"),ensure_ascii=False).encode("utf-8")
    json_bytes += b" " * ((4 - len(json_bytes) % 4) % 4)
    binary_bytes = bytes(binary)
    binary_bytes += b"\0" * ((4 - len(binary_bytes) % 4) % 4)
    total = 12 + 8 + len(json_bytes) + 8 + len(binary_bytes)
    glb = (b"glTF" + struct.pack("<II",2,total) +
           struct.pack("<II",len(json_bytes),JSON_CHUNK) + json_bytes +
           struct.pack("<II",len(binary_bytes),BIN_CHUNK) + binary_bytes)
    path.write_bytes(glb)
    return length, width, height, len(glb)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    catalog = CATALOG.read_text(encoding="utf-8")
    entries = [(int(i), name, kind, key) for i,name,kind,key in RE.findall(catalog) if int(i) <= VEHICLE_COUNT]
    if len(entries) != VEHICLE_COUNT:
        raise SystemExit(f"Expected {VEHICLE_COUNT} catalog entries, found {len(entries)}")
    made, preserved = 0, 0
    for vehicle_id, name, kind, key in entries:
        target = OUT / f"{key}.glb"
        if target.is_file() and target.stat().st_size > 20 and kind != "dinosaur_mount":
            preserved += 1
            continue
        length, width, height, size = make_glb(target, vehicle_id, name, kind)
        print(f"GENERATED {vehicle_id:03d} {key}: {length:.2f}m x {width:.2f}m x {height:.2f}m ({size} bytes)")
        made += 1
    print(f"Vehicle model batch complete: generated={made}, preserved_upstream={preserved}, slots={VEHICLE_COUNT}")
    if made + preserved != VEHICLE_COUNT:
        raise SystemExit("Vehicle model batch count mismatch")


if __name__ == "__main__":
    main()
