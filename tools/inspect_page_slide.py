#!/usr/bin/env python3
"""Report bounded Mode10 page rotation samples from the user's local ROM.

Does not infer Fusion timeline timing or claim to evaluate the scene hierarchy.
"""
import argparse
import hashlib
import io
import json
import math
from pathlib import Path
import zipfile
from decode_navbar import geometry
from mode10 import parse


def report(rom):
    scene = 'assets/Port/scenes/Rosie_page_slide_main.m10'
    with zipfile.ZipFile(rom) as archive:
        apk = archive.read('system/app/Rosie.apk')
    with zipfile.ZipFile(io.BytesIO(apk)) as archive:
        data = archive.read(scene)
    tracks = []
    for node in geometry(parse(data)):
        if not (node['name'] or '').startswith('Page_template_'):
            continue
        samples = node['properties'].get('Rotation', {}).get('samples', [])
        tracks.append({'name': node['name'], 'offset': node['offset'],
                       'rotation_samples': len(samples),
                       'selected_yaw_degrees': [
                           {'raw_frame': s['frame'],
                            'yaw': round(math.degrees(2 * math.atan2(s['value'][1], s['value'][3])), 4)}
                           for s in samples if s['frame'] in (0, 60, 120, 200)]})
    return {'scene': scene, 'sha256': hashlib.sha256(data).hexdigest(),
            'caveat': 'Quaternion yaw only; raw frame fields do not establish runtime duration or easing.',
            'tracks': tracks}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom', type=Path, default=Path('res/3.33.401.153.zip'))
    parser.add_argument('--output', type=Path, default=Path('.toolchain/page-slide-report.json'))
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report(args.rom), indent=2) + '\n')
    print(args.output)
