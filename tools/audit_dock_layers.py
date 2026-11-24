#!/usr/bin/env python3
"""Audit the modern Mode10 navbar and surviving older 2D dock resources."""
import argparse
import collections
import hashlib
import io
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import zipfile
import zlib
from mode10 import integer, name_hash, parse, properties, text, walk


def palette_alpha(data):
    """Read the alpha plane of the observed 8-bit indexed PNG, without changing it."""
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError('Not a PNG')
    cursor, packed, alpha, header = 8, bytearray(), bytes([255]) * 256, None
    while cursor < len(data):
        length, = struct.unpack_from('>I', data, cursor)
        kind = data[cursor + 4:cursor + 8]
        payload = data[cursor + 8:cursor + 8 + length]
        if len(payload) != length or cursor + 12 + length > len(data):
            raise ValueError('Truncated PNG chunk')
        if kind == b'IHDR':
            header = struct.unpack('>IIBBBBB', payload)
        elif kind == b'tRNS':
            alpha = payload + bytes([255]) * (256 - len(payload))
        elif kind == b'IDAT':
            packed.extend(payload)
        cursor += length + 12
    if header is None:
        raise ValueError('Missing PNG header')
    width, height, depth, color, compression, filtering, interlace = header
    if depth != 8 or color != 3 or compression or filtering or interlace or width * height > 4_000_000:
        raise ValueError('Only bounded non-interlaced indexed PNG8 is supported')
    size = height * (width + 1)
    decoder = zlib.decompressobj()
    rows = decoder.decompress(packed, size + 1)
    if len(rows) != size or not decoder.eof or decoder.unused_data:
        raise ValueError('Unexpected PNG scanline size')
    output, previous = bytearray(), bytearray(width)
    cursor = 0
    for _ in range(height):
        kind = rows[cursor]
        row = bytearray(rows[cursor + 1:cursor + 1 + width])
        cursor += width + 1
        if kind > 4:
            raise ValueError('Invalid PNG filter')
        for x in range(width):
            left = row[x - 1] if x else 0
            above = previous[x]
            upper_left = previous[x - 1] if x else 0
            estimate = left + above - upper_left
            distances = [abs(estimate - v) for v in (left, above, upper_left)]
            predictor = (left, above, upper_left)[distances.index(min(distances))]
            adjustment = (0, left, above, (left + above) // 2, predictor)[kind]
            row[x] = (row[x] + adjustment) & 255
        output.extend(alpha[index] for index in row)
        previous = row
    return width, height, bytes(output)


def resource_table(dump):
    result = {}
    for match in re.finditer(r'^    resource (0x[0-9a-f]+) ([^\s]+)[^\n]*\n(.*?)(?=^    resource |\Z)', dump, re.M | re.S):
        ident, name, configs = match.groups()
        result[name] = {'id': ident, 'configurations': configs.rstrip().splitlines()}
    return result


COMPARISON = """<!doctype html>
<html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>HTC dock layer investigation</title>
<style>
body {font:16px/1.5 system-ui,sans-serif;background:#182026;color:#edf1f3;max-width:1060px;margin:40px auto;padding:0 24px}
h1 {font-size:26px} h2 {font-size:18px} .columns {display:grid;grid-template-columns:230px 1fr;gap:32px}
.photo {max-width:220px;width:100%} .checker {background:repeating-conic-gradient(#536169 0 25%,#36434b 0 50%) 0 / 24px 24px}
.asset {width:100%;display:block} .stage {position:relative;aspect-ratio:540/135;overflow:hidden;margin:18px 0}
.stage img {position:absolute;bottom:0;left:0;width:100%;height:auto}
label {display:block;margin:8px 0} small {color:#c4cdd2} code {color:#c3e698}
@media(max-width:700px){.columns{grid-template-columns:1fr}.photo{max-width:180px}}
</style>
<h1>Recovered arc artwork and the Mode10 background</h1>
<p>The ROM contains a curved PNG and an older three-button layout. A static binding that puts this PNG over the decoded Mode10 dock has not been found.</p>
<div class="columns"><section><h2>Your reference photo</h2><img class="photo" src="reference-photo.jpg" alt="HTC Sensation reference showing the curved three-button dock"></section>
<section><h2>Original curved drawable</h2><div class="checker"><img class="asset" src="common_mainnav_scroller3.png" alt="Recovered translucent arch with transparent upper corners"></div>
<p><code>common_mainnav_scroller3.png</code> · 480 × 84. Nonzero alpha starts at y=25 on the left, y=0 at the center, and y=24 on the right.</p>
<h2>Try the two layers</h2>
<label><input id="base" type="checkbox" checked> Show decoded straight Mode10 background</label>
<label><input id="arc" type="checkbox" checked> Place recovered curved PNG above it</label>
<div class="stage checker"><img id="base-image" src="mode10-background.png" alt="Decoded straight navbar background"><img id="arc-image" src="common_mainnav_scroller3.png" alt="Recovered curved drawable above background"></div>
<small>Diagnostic composition, not a decoded Sense frame. Both assets are scaled to the same width and bottom-aligned; original cross-system placement is unknown. An ordinary source-over layer cannot erase the base bar’s corners. Hide the straight layer to inspect the arc’s own silhouette.</small>
<p>The older <code>button_bar.xml</code> defines Apps / Phone / Personalize controls, but its eight shared panel/background resources resolve to the transparent <code>zero_dummy_asset</code>.</p>
</section></div>
<script>
for (const name of ['base','arc']) {
 const control=document.getElementById(name), picture=document.getElementById(name+'-image');
 control.addEventListener('change',()=>{picture.hidden=!control.checked;});
}
</script></html>
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom', type=Path, default=Path('res/3.33.401.153.zip'))
    parser.add_argument('--output', type=Path, default=Path('references/3.33.401.153/dock-layer-audit'))
    parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', '.toolchain/android-sdk')))
    args = parser.parse_args()
    out = args.output
    out.mkdir(parents=True, exist_ok=True)
    aapt = args.sdk / 'build-tools/35.0.0/aapt2'
    records = {}
    with zipfile.ZipFile(args.rom) as rom:
        runtime = rom.read('system/lib/libmode10.so')
        names = {name_hash(s.decode()): s.decode() for s in re.findall(rb'[A-Za-z_][A-Za-z0-9_]{2,80}(?=\x00)', runtime)}
        for apk_name, source in [('Rosie', 'system/app/Rosie.apk'), ('HTC', 'system/framework/com.htc.resources.apk')]:
            blob = rom.read(source)
            target = out / (apk_name + '.apk')
            target.write_bytes(blob)
            dump = subprocess.check_output([str(aapt), 'dump', 'resources', str(target)], text=True)
            (out / (apk_name + '-resources.txt')).write_text(dump)
            records[apk_name] = {'source': source, 'sha256': hashlib.sha256(blob).hexdigest(), 'table': resource_table(dump)}
    arc_path = 'res/drawable-hdpi/common_mainnav_scroller3.png'
    with zipfile.ZipFile(out / 'Rosie.apk') as rosie:
        for source in [arc_path, 'res/drawable-hdpi/common_mainnav_scroller1.png',
                       'res/drawable-port-hdpi/personalize_rest.png', 'res/drawable-hdpi/icon_p_phone_rest.png']:
            (out / Path(source).name).write_bytes(rosie.read(source))
        arc = rosie.read(arc_path)
        width, height, coverage = palette_alpha(arc)
        first_rows = []
        for x in (0, width // 4, width // 2, 3 * width // 4, width - 1):
            first = next((y for y in range(height) if coverage[y * width + x] > 0), None)
            first_rows.append({'x': x, 'first_nonzero_alpha_y': first})
        xmls = ['res/layout-port/button_bar.xml', 'res/layout-port/scrollbar.xml']
        xmls += ['res/drawable-port/' + n + '.xml' for n in ('bb_btn_left', 'bb_btn_middle', 'bb_btn_right', 'bb_btn_right_personalize_src')]
        for source in xmls:
            dump = subprocess.check_output([str(aapt), 'dump', 'xmltree', str(out / 'Rosie.apk'), '--file', source], text=True)
            (out / (Path(source).stem + '-xmltree.txt')).write_text(dump)
        classes, libraries, mask_fields, arc_hits = collections.Counter(), [], [], []
        scene_count = 0
        for source in rosie.namelist():
            if not source.endswith('.m10'):
                continue
            scene_count += 1
            root = parse(rosie.read(source))
            for section in root.children:
                label = names.get(section.tag, '')
                if label in ('MeshLibrary', 'MaterialLibrary') and len(section.payload):
                    libraries.append({'scene': source, 'library': label, 'bytes': len(section.payload)})
            for record in walk(root):
                if record.tag != name_hash('Node') or record.kind != 1:
                    continue
                props = properties(record)
                kind = next(integer(c) for c in record.children if c.tag == name_hash('Type'))
                classes[names.get(kind, f'{kind:08x}')] += 1
                node_name = text(props[name_hash('Name')]) if name_hash('Name') in props else None
                for key, value in props.items():
                    label = names.get(key, '')
                    if any(s in label.lower() for s in ('mask', 'clip', 'shader', 'effect', 'material')):
                        mask_fields.append({'scene': source, 'node': node_name, 'property': label, 'offset': value.offset})
                texture = props.get(name_hash('DiffuseMap'))
                if texture and texture.kind == 2 and integer(texture) == name_hash('common_mainnav_scroller3.png'):
                    arc_hits.append({'scene': source, 'node': node_name})
        # An XML reference search does not detect Java/runtime-created bindings.
        arc_xml_hits = [n for n in rosie.namelist() if n.endswith('.xml') and struct.pack('<I', 0x7f02003d) in rosie.read(n)]
    with zipfile.ZipFile(out / 'HTC.apk') as htc:
        dummy_source = 'res/drawable-hdpi/zero_dummy_asset.9.png'
        dummy = htc.read(dummy_source)
        (out / 'zero_dummy_asset.9.png').write_bytes(dummy)
        dummy_dimensions = list(struct.unpack_from('>II', dummy, 16))
    aliases = {name: entry for name, entry in records['HTC']['table'].items() if name.startswith('drawable/common_app_rosie_')}
    report = {
        'sources': {name: {k: v for k, v in record.items() if k != 'table'} for name, record in records.items()},
        'arc_drawable': {'source': 'system/app/Rosie.apk!/' + arc_path, 'id': '0x7f02003d',
                         'sha256': hashlib.sha256(arc).hexdigest(), 'size': [width, height],
                         'alpha_range': [min(coverage), max(coverage)], 'top_edge_samples': first_rows,
                         'xml_resource_id_hits': arc_xml_hits, 'm10_diffuse_map_hits': arc_hits},
        'legacy_button_bar': {'layout': xmls[0], 'framework_drawables': aliases,
                              'dummy_dimensions': dummy_dimensions, 'dummy_sha256': hashlib.sha256(dummy).hexdigest()},
        'mode10': {'scenes': scene_count, 'node_types': dict(classes), 'nonempty_mesh_material_libraries': libraries,
                   'recognized_mask_clip_shader_effect_material_fields': mask_fields},
        'limits': 'No original Sense runtime was executed. No matching static M10/XML binding proves neither absence nor presence of a runtime-created layer. The surviving curved drawable does not by itself establish a complete dock composition.'}

    reference = Path('res/HTC-Sensation-4G-4.jpg')
    if reference.is_file():
        (out / 'reference-photo.jpg').write_bytes(reference.read_bytes())
        report['reference_photo'] = {'source': str(reference), 'sha256': hashlib.sha256(reference.read_bytes()).hexdigest()}
    # Compare original files in the browser; no artwork is modified or synthesized.
    flat = args.output.parent / 'navbar-decoded/home_nav_bg.png'
    if flat.is_file():
        (out / 'mode10-background.png').write_bytes(flat.read_bytes())
        (out / 'comparison.html').write_text(COMPARISON)

    (out / 'layers.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Recovered arc:', report['arc_drawable'])
    print('Legacy panel references:', len(aliases), '; dummy size:', dummy_dimensions)
    print('Mode10:', report['mode10'])
    print('Report:', out / 'layers.json')


if __name__ == '__main__':
    main()
