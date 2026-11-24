#!/usr/bin/env python3
"""Recover Rosie navbar geometry and its default Fusion texture from a local ROM."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import struct
import zipfile
from mode10 import etc1, fastlz, integer, name_hash, parse, png, properties, text, walk

VECTOR_FIELDS = {'Position': 3, 'Rotation': 4, 'Scale': 3, 'PivotPosition': 3,
                 'PivotRotation': 4, 'PivotScale': 3, 'Opacity': 1, 'DiffuseColor': 4}


def value(name, record):
    result = {'offset': record.offset, 'payload_offset': record.offset + 8,
              'length': len(record.payload), 'tag': f'{record.tag:08x}', 'kind': record.kind}
    count = VECTOR_FIELDS.get(name)
    if record.kind == 1:
        fields = {c.tag: c for c in record.children}
        result['animated'] = True
        if count and name_hash('Samples') in fields and name_hash('Size') in fields:
            sample_count = integer(fields[name_hash('Size')]) & 0x7fffffff
            samples = fields[name_hash('Samples')]
            stride = (2 + count) * 4
            if len(samples.payload) == sample_count * stride:
                result['samples'] = [
                    {'frame': row[0], 'interpolation_code': row[1], 'value': list(row[2:])}
                    for row in struct.iter_unpack('<II' + 'f' * count, samples.payload)]
            elif len(samples.payload) == sample_count * count * 4:
                start = integer(fields[name_hash('StartFrame')]) if name_hash('StartFrame') in fields else 0
                result['sample_encoding'] = 'dense vectors, implicit consecutive frames'
                result['samples'] = [
                    {'frame': start + index, 'value': list(row)}
                    for index, row in enumerate(struct.iter_unpack('<' + 'f' * count, samples.payload))]
        return result
    if name == 'Name':
        result['value'] = text(record)
    elif count and len(record.payload) == count * 4:
        result['value'] = list(struct.unpack('<' + 'f' * count, record.payload))
    elif name in ('Size', 'OpticalSize', 'Dimensions') and len(record.payload) == 8:
        result['value'] = list(struct.unpack('<II', record.payload))
    elif name == 'Vertices' and len(record.payload) % 20 == 0:
        result['layout'] = 'x, y, z, u, v; inferred from the quad/UV values'
        result['value'] = [list(row) for row in struct.iter_unpack('<5f', record.payload)]
    elif len(record.payload) == 4:
        result['value_u32'] = integer(record)
    else:
        result['raw_hex'] = bytes(record.payload).hex()
    return result


def geometry(root):
    result = []
    names = ('Name', 'Position', 'Rotation', 'Scale', 'PivotPosition', 'PivotRotation',
             'PivotScale', 'Size', 'OpticalSize', 'Dimensions', 'Vertices', 'Opacity', 'DiffuseMap', 'BlendMode')

    def visit(record, parent=None):
        if record.tag == name_hash('Node') and record.kind == 1:
            props = properties(record)
            name = text(props[name_hash('Name')]) if name_hash('Name') in props else None
            kind = next((integer(c) for c in record.children if c.tag == name_hash('Type')), None)
            result.append({'offset': record.offset, 'name': name, 'parent_offset': parent,
                           'type_hash': f'{kind:08x}' if kind is not None else None,
                           'properties': {n: value(n, props[name_hash(n)]) for n in names if name_hash(n) in props}})
            parent = record.offset
        for child in record.children:
            visit(child, parent)

    visit(root)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom', type=Path, default=Path('res/3.33.401.153.zip'))
    parser.add_argument('--output', type=Path, default=Path('references/3.33.401.153/navbar-decoded'))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    scene_path = 'assets/Port/scenes/Rosie_navbar.m10'
    texture_path = 'assets/Default/drawable/shared.m10'
    with zipfile.ZipFile(args.rom) as rom:
        rosie = rom.read('system/app/Rosie.apk')
        fusion = rom.read('system/app/fusion.apk')
    with zipfile.ZipFile(io.BytesIO(rosie)) as apk:
        scene = apk.read(scene_path)
    with zipfile.ZipFile(io.BytesIO(fusion)) as apk:
        shared = apk.read(texture_path)
    scene_root, texture_root = parse(scene), parse(shared)
    nodes = geometry(scene_root)
    background = next(n for n in nodes if n['name'] == 'home_nav_bg.png')
    texture_id = background['properties']['DiffuseMap']['value_u32']
    if texture_id != name_hash('home_nav_bg.png'):
        raise ValueError('Background texture ID does not match name hash')
    texture = None
    for record in walk(texture_root):
        if record.tag == name_hash('Type') and record.kind == 1:
            props = properties(record)
            if name_hash('ID') in props and integer(props[name_hash('ID')]) == texture_id:
                if texture is not None:
                    raise ValueError('Ambiguous texture ID')
                texture = record
    if texture is None:
        raise ValueError('No matching default texture')
    props = properties(texture)
    width, height = (integer(props[name_hash(n)]) for n in ('Width', 'Height'))
    fmt = integer(props[name_hash('Format')])
    levels = props[name_hash('Levels')]
    if fmt != 14 or len(levels.payload) != 16:
        raise ValueError('Only the observed single-level dual-ETC1 texture is supported')
    padded_width, padded_height, offset, plane_size = struct.unpack('<4I', levels.payload)
    if padded_width < width or padded_height < height or offset != 0:
        raise ValueError('Unsupported texture extents')
    decoded_size = next(integer(c) for c in texture.children if c.tag == name_hash('DataDecompressedSize'))
    packed = props[name_hash('Pixels')]
    raw = fastlz(packed.payload, decoded_size)
    if len(raw) != plane_size * 2:
        raise ValueError('Expected two equal ETC1 planes')
    color = etc1(raw[:plane_size], padded_width, padded_height)
    alpha = etc1(raw[plane_size:], padded_width, padded_height)
    rgb, mask, rgba = bytearray(), bytearray(), bytearray()
    for y in range(height):
        for x in range(width):
            i = (y * padded_width + x) * 3
            pixel, coverage = color[i:i + 3], alpha[i]
            rgb.extend(pixel)
            mask.extend(alpha[i:i + 3])
            rgba.extend((*pixel, coverage))
    png(args.output / 'home_nav_bg.png', width, height, rgba, alpha=True)
    png(args.output / 'home_nav_bg-color.png', width, height, rgb)
    png(args.output / 'home_nav_bg-alpha.png', width, height, mask)
    (args.output / 'home_nav_bg-planes.etc1').write_bytes(raw)
    report = {
        'method': 'Partial structural decoder, not a complete Mode10 renderer. Local coordinates; interpolation codes preserved without evaluation.',
        'scene': {'source': 'system/app/Rosie.apk!/' + scene_path, 'sha256': hashlib.sha256(scene).hexdigest(),
                  'records': sum(1 for _ in walk(scene_root)), 'nodes': nodes},
        'texture': {'source': 'system/app/fusion.apk!/' + texture_path, 'sha256': hashlib.sha256(shared).hexdigest(),
                    'record_offset': texture.offset, 'id': f'{texture_id:08x}',
                    'format': fmt, 'interpretation': 'FastLZ stream, then ETC1 RGB plane followed by ETC1 alpha plane (red channel)',
                    'pixels_record_offset': packed.offset, 'compressed_bytes': len(packed.payload), 'decoded_bytes': len(raw),
                    'size': [width, height], 'levels': [padded_width, padded_height, offset, plane_size],
                    'alpha_range': [min(rgba[3::4]), max(rgba[3::4])],
                    'alpha_channel_max_difference': max(abs(alpha[i] - alpha[i + c]) for i in range(0, len(alpha), 3) for c in (1, 2)),
                    'sha256_rgba': hashlib.sha256(rgba).hexdigest()},
        'conclusion': 'Background vertices form a rectangle. The decoded default texture is straight, with nonzero alpha across its entire extent; no curved silhouette was recovered from this background.'}
    (args.output / 'navbar.json').write_text(json.dumps(report, indent=2, allow_nan=False) + '\n')
    vertices = background['properties']['Vertices']['value']
    points = ' '.join(f'{v[0]},{v[1]}' for v in vertices)
    (args.output / 'navbar-geometry.svg').write_text(
        '<svg xmlns="http://www.w3.org/2000/svg" width="640" height="180" viewBox="-50 -45 640 180">'
        '<rect x="-50" y="-45" width="640" height="180" fill="#20292c"/>'
        '<polygon points="' + points + '" fill="none" stroke="#a8d957" stroke-width="2"/>'
        '<path d="M0 0 L540 90" fill="none" stroke="#6e8e39"/>'
        '<text x="0" y="-16" fill="white" font-family="sans-serif" font-size="14">Decoded local quad: 540 × 90; no curved edge</text></svg>\n')
    print('Decoded', len(nodes), 'nodes;', report['scene']['records'], 'bounded scene records')
    print('Background quad:', vertices)
    print('Texture:', report['texture'])
    print('Artifacts:', args.output)


if __name__ == '__main__':
    main()
