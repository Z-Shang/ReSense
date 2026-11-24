#!/usr/bin/env python3
"""Resolve the clock's s_03 Mode10 materials to original default Fusion textures."""
import hashlib
import io
import json
from pathlib import Path
import struct
import zipfile
from decode_navbar import geometry
from inspect_weather_playback import scene_timing
from mode10 import etc1, fastlz, integer, name_hash, parse, png, properties, walk


def extract(rom_path, output):
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(rom_path) as rom:
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/HtcClock3DWidget.apk'))) as apk:
            scene = apk.read('assets/Port/s_03.m10')
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/fusion.apk'))) as apk:
            shared = apk.read('assets/Default/drawable/shared.m10')
    nodes = geometry(parse(scene))
    (output.parent / 'weather-animation-tracks.json').write_text(json.dumps({
        'source': 'HtcClock3DWidget.apk!/assets/Port/s_03.m10',
        'sha256': hashlib.sha256(scene).hexdigest(),
        'timing': 'Scene timeline metadata recovered; inclusive end scheduling may differ by one frame.',
        'timelines': scene_timing(scene),
        'nodes': nodes}, indent=2) + '\n')
    ids = {n['properties']['DiffuseMap']['value_u32'] for n in nodes if 'DiffuseMap' in n['properties']}
    names = {821437838: 'sense_weather_sun', 1131629602: 'sense_weather_flare',
             1433276943: 'sense_weather_cloud04', 1435648785: 'sense_weather_cloud05'}
    found = []
    for record in walk(parse(shared)):
        if record.tag != name_hash('Type') or record.kind != 1:
            continue
        props = properties(record)
        if name_hash('ID') not in props:
            continue
        texture_id = integer(props[name_hash('ID')])
        if texture_id not in ids:
            continue
        width, height, fmt = (integer(props[name_hash(k)]) for k in ('Width', 'Height', 'Format'))
        if fmt != 14 or len(props[name_hash('Levels')].payload) != 16:
            raise ValueError('Unsupported weather texture format')
        pw, ph, offset, plane = struct.unpack('<4I', props[name_hash('Levels')].payload)
        if pw < width or ph < height or offset or pw > 4096 or ph > 4096:
            raise ValueError('Invalid weather texture bounds')
        size = next(integer(c) for c in record.children if c.tag == name_hash('DataDecompressedSize'))
        raw = fastlz(props[name_hash('Pixels')].payload, size)
        if len(raw) != plane * 2:
            raise ValueError('Expected color and alpha ETC1 planes')
        color, alpha = etc1(raw[:plane], pw, ph), etc1(raw[plane:], pw, ph)
        rgba = bytearray()
        for y in range(height):
            for x in range(width):
                i = (y * pw + x) * 3
                rgba.extend((*color[i:i+3], alpha[i]))
        filename = names[texture_id] + '.png'
        png(output / filename, width, height, rgba, alpha=True)
        found.append({'file': filename, 'id': texture_id, 'offset': record.offset,
                      'width': width, 'height': height})
    if len(found) != len(ids):
        raise ValueError('Unresolved weather materials')
    return {'scene': 'HtcClock3DWidget.apk!/assets/Port/s_03.m10',
            'scene_sha256': hashlib.sha256(scene).hexdigest(),
            'textures': 'fusion.apk!/assets/Default/drawable/shared.m10',
            'format': 'FastLZ compressed dual ETC1, second plane red channel supplies alpha',
            'assets': found,
            'rendering': 'Original textures; modern static composition, not Fusion scene playback.'}
