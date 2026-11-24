#!/usr/bin/env python3
"""Extract clock flap X rotations from the user's Mode10 scene, without assuming FPS."""
import hashlib
import io
import math
import zipfile
from decode_navbar import geometry
from mode10 import parse


def extract(rom_path):
    scene = 'assets/Port/clock_weather.m10'
    with zipfile.ZipFile(rom_path) as rom:
        apk = rom.read('system/app/HtcClock3DWidget.apk')
    with zipfile.ZipFile(io.BytesIO(apk)) as apk:
        data = apk.read(scene)
    nodes = geometry(parse(data))
    by_offset = {n['offset']: n for n in nodes}
    result = {'source': 'HtcClock3DWidget.apk!/' + scene,
              'sha256': hashlib.sha256(data).hexdigest(),
              'timing': 'Frame indices normalized per flap; runtime duration chosen by launcher.'}
    for node in nodes:
        if node['name'] != 'tile':
            continue
        parent = by_offset.get(node['parent_offset'])
        if not parent or parent['name'] not in ('top_0', 'bottom_0'):
            continue
        chain, ancestor = [], parent
        while ancestor:
            chain.append(ancestor['name'])
            ancestor = by_offset.get(ancestor['parent_offset'])
        if 'Flip_Minute' not in chain:
            continue
        key = 'top' if parent['name'] == 'top_0' else 'bottom'
        if key in result:
            continue
        samples = node['properties']['Rotation']['samples']
        result[key] = [{'frame': s['frame'], 'degrees': round(math.degrees(
            2 * math.atan2(s['value'][0], s['value'][3])), 6)} for s in samples]
        if len(result[key]) != 11:
            raise ValueError('Unexpected clock flap sample count')
    if 'top' not in result or 'bottom' not in result:
        raise ValueError('Clock flap rotations missing')
    return result
