#!/usr/bin/env python3
"""Extract scene references and UTF-16 string candidates; not an M10 decoder."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import zipfile


def inspect(data):
    if len(data) < 8 or data[:4] != b'!01m':
        raise ValueError('Unrecognized Mode10 header')
    strings = []
    for match in re.finditer(rb'(?:[\x20-\x7e]\x00){4,}', data):
        strings.append({'offset': match.start(), 'text': match.group().decode('utf-16le')})
    return {'size': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
            'magic': data[:4].decode('ascii'),
            'header_word_at_4': struct.unpack_from('<I', data, 4)[0],
            'string_candidates': strings}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--references', type=Path, default=Path('references/3.33.401.153'))
    args = parser.parse_args()
    output = args.references / 'mode10'
    output.mkdir(parents=True, exist_ok=True)
    report = {'method': 'Header inspection and printable UTF-16LE runs. Strings may include adjacent binary bytes; no scene structure, textures or animation tracks decoded.', 'packages': []}
    for package in ('Rosie', 'HtcClock3DWidget', 'HtcWeather3DWidget'):
        apk = args.references / 'system/app' / (package + '.apk')
        entry = {'apk': str(apk.relative_to(args.references)), 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'scenes': [], 'previews': []}
        with zipfile.ZipFile(apk) as archive:
            for name in archive.namelist():
                scene = name.endswith('.m10')
                preview = name.startswith('res/drawable') and '/preview_' in name and name.endswith(('.png', '.jpg'))
                if not (scene or preview):
                    continue
                parts = name.split('/')
                if any(p in ('', '.', '..') for p in parts) or '\\' in name:
                    raise ValueError('Unsafe path: ' + name)
                data = archive.read(name)
                destination = output.joinpath(package, *parts)
                if not destination.resolve().is_relative_to(output.resolve()):
                    raise ValueError('Path escapes output')
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(data)
                record = {'source': name, 'extracted': str(destination.relative_to(output))}
                if scene:
                    record.update(inspect(data))
                    entry['scenes'].append(record)
                else:
                    record.update(size=len(data), sha256=hashlib.sha256(data).hexdigest())
                    entry['previews'].append(record)
        report['packages'].append(entry)
    (output / 'scenes.json').write_text(json.dumps(report, indent=2) + '\n')
    for package in report['packages']:
        print(package['apk'], len(package['scenes']), 'scenes,', len(package['previews']), 'previews')
    print('Report:', output / 'scenes.json')


if __name__ == '__main__':
    main()
