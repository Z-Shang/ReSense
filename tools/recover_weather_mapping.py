#!/usr/bin/env python3
"""Reference math for the recovered stock direct-overlay path, not live ARM execution.

Reads meshes/tracks directly from the ROM; makes no changes to application assets.
Only the wiper/streak ancestry is evaluated, with unsupported transforms rejected.
"""
import argparse
import hashlib
import io
import json
import math
from pathlib import Path
import struct
import zipfile

from audit_weather_framing_typography import multiply, sample
from decode_navbar import geometry
from extract_weather_catalog import track
from mode10 import integer, name_hash, parse, properties, walk


def camera(width, height, near=0.1, far=10000.0):
    if width <= 0 or height <= 0 or not 0 < near < far:
        raise ValueError('Invalid surface or clipping planes')
    distance = 50.0*width/36.0
    view = [[1,0,0,-width/2], [0,1,0,-height/2], [0,0,1,-distance], [0,0,0,1]]
    projection = [[2*distance/width,0,0,0], [0,-2*distance/height,0,0],
                  [0,0,-(far+near)/(far-near),-2*far*near/(far-near)], [0,0,-1,0]]
    return {'distance': distance, 'vertical_fov_degrees': -math.degrees(2*math.atan(height/(2*distance))),
            'near': near, 'far': far, 'view': view, 'projection': projection}


def project(point, width, height):
    """Column-vector equivalent of HTC's V/P, divide and viewport operations.

    GPU raster placement uses the GL viewport. The CPU ProjectCoordinate helper
    truncates half extents for odd dimensions; reports below use even stock sizes.
    """
    state = camera(width, height)
    matrix = multiply(state['projection'], state['view'])
    vector = [*point, 1]
    clip = [sum(row[k]*vector[k] for k in range(4)) for row in matrix]
    if clip[3] <= 0 or not -clip[3] <= clip[2] <= clip[3]:
        return None
    return [width/2*(1+clip[0]/clip[3]), height/2*(1-clip[1]/clip[3])]


def local_matrix(props, frame):
    def vector(name):
        return sample(track(props[name]), frame, name in ('Rotation', 'PivotRotation'))
    position, scale = vector('Position'), vector('Scale')
    x,y,z,w = vector('Rotation')
    pivot = vector('PivotPosition') if props['pivot_enabled'] else [0,0,0]
    # Non-default pivot transforms need their own matrix, rather than silently
    # applying the simplified model used by our original overlay implementation.
    if vector('PivotRotation') != [0,0,0,1] or vector('PivotScale') != [1,1,1]:
        raise ValueError('Non-default pivot rotation/scale on inspected ancestry')
    matrix = [[1-2*y*y-2*z*z,2*x*y-2*z*w,2*x*z+2*y*w,0],
              [2*x*y+2*z*w,1-2*x*x-2*z*z,2*y*z-2*x*w,0],
              [2*x*z-2*y*w,2*y*z+2*x*w,1-2*x*x-2*y*y,0], [0,0,0,1]]
    for row in range(3):
        for col in range(3):
            matrix[row][col] *= scale[col]
        matrix[row][3] = position[row]+sum(matrix[row][col]*pivot[col] for col in range(3))
    return matrix


def inspect_scene(data):
    root = parse(data)
    nodes = geometry(root)
    records = {r.offset: properties(r) for r in walk(root) if r.tag == name_hash('Node')}
    by_offset = {n['offset']: n for n in nodes}
    selected = {}
    # Offsets distinguish these exact nodes from duplicate resource names.
    for label, offset in [('wiper',673568), ('streaks',675900)]:
        node = by_offset[offset]
        ancestors = []
        while node:
            node = dict(node)
            raw = records[node['offset']]
            node['properties'] = dict(node['properties'])
            node['properties']['pivot_enabled'] = bytes(raw[name_hash('Pivot')].payload) == b'\1'
            if bytes(raw[name_hash('TwoDimension')].payload) != b'\1':
                raise ValueError('Inspected ancestry is not entirely TwoDimension')
            if name_hash('Frozen') in raw and bytes(raw[name_hash('Frozen')].payload) != b'\0':
                raise ValueError('Unexpected framebuffer/frozen path')
            if integer(raw[name_hash('LayerParent')]) != 0xffffffff:
                raise ValueError('Layer-linked ancestry needs separate evaluation')
            ancestors.append(node)
            node = by_offset.get(node['parent_offset'])
        selected[label] = list(reversed(ancestors))
    return selected, records


def world_points(ancestors, frame):
    world = [[float(i==j) for j in range(4)] for i in range(4)]
    for node in ancestors:
        # Both selected chains start at frame zero; fail if their timing changes.
        world = multiply(world, local_matrix(node['properties'], frame))
    vertices = ancestors[-1]['properties']['Vertices']['value']
    return [[sum(world[row][k]*[*vertex[:3],1][k] for k in range(4)) for row in range(3)]
            for vertex in vertices]


def bounds(points):
    if any(p is None for p in points):
        raise ValueError('Inspected quad crosses clipping planes')
    return [min(p[0] for p in points), min(p[1] for p in points),
            max(p[0] for p in points), max(p[1] for p in points)]


def recover(data):
    selected, records = inspect_scene(data)
    ancestry = {}
    for label, chain in selected.items():
        ancestry[label] = [{'name': n['name'], 'offset': n['offset']} for n in chain]
        for node in chain:
            raw = records[node['offset']]
            for name in ('InFrame','TimelineInFrame'):
                if name_hash(name) in raw and struct.unpack('<f', raw[name_hash(name)].payload)[0] not in (0,-1):
                    raise ValueError('Unsupported time offset on inspected ancestry')
    streak_world = world_points(selected['streaks'], 70)
    wiper_frames = {frame: world_points(selected['wiper'], frame) for frame in range(45,105)}
    if any(abs(p[2]) > 1e-6 for points in [streak_world,*wiper_frames.values()] for p in points):
        raise ValueError('Wiper/streak plane no longer at Z=0')
    stock = [540,960]
    raw = bounds([project(p,*stock) for p in streak_world])
    wiper = max((bounds([project(p,*stock) for p in points])[3], frame) for frame,points in wiper_frames.items())
    comparisons = []
    for width,height in [(540,960),(1080,1920),(1080,2400),(1440,3200)]:
        fit = min(width/540,height/960)
        def adapted(point):
            native = project(point,540,960)
            return [width/2+(native[0]-270)*fit,height/2+(native[1]-480)*fit]
        comparisons.append({'surface': [width,height],
                            'raw_stock_units_streak_bounds': bounds([project(p,width,height) for p in streak_world]),
                            'modern_contain_adapter_streak_bounds': bounds([adapted(p) for p in streak_world]),
                            'modern_contain_adapter_wiper_max_y': height/2+(wiper[0]-480)*fit})
    return {'method': 'Static direct-path derivation and reference math; no live HTC execution',
            'source_sha256': hashlib.sha256(data).hexdigest(),
            'matrix_convention': 'Column-vector equivalent; native Matrix4Multiply(a,b) yields b*a in this convention',
            'camera_stock': camera(*stock), 'ancestry': ancestry,
            'stock_streak_bounds': raw, 'stock_wiper_max_y': wiper[0], 'stock_wiper_max_frame': wiper[1],
            'streak_world_vertices': streak_world, 'wiper_frame_55_world_vertices': wiper_frames[55],
            'surface_comparisons': comparisons,
            'scope': 'Unfrozen overlay, default centered camera, no explicit scene camera or view adjustment. Modern contain fitting is our adapter, not stock behavior.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom',type=Path,default=Path('res/3.33.401.153.zip'))
    parser.add_argument('--output',type=Path,default=Path('references/3.33.401.153/weather-screen-mapping.json'))
    args = parser.parse_args()
    with zipfile.ZipFile(args.rom) as rom:
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/HtcWeatherWallpaper.apk'))) as apk:
            report = recover(apk.read('assets/Port/weather_overlay.m10'))
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,indent=2)+'\n')
    print(args.output)


if __name__ == '__main__':
    main()
