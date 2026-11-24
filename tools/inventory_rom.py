#!/usr/bin/env python3
"""Inventory and extract an explicit set of local HTC reference resources."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile

PACKAGES = {
    'system/app/Rosie.apk', 'system/app/HtcClock3DWidget.apk',
    'system/app/HtcWeather3DWidget.apk', 'system/app/Weather.apk',
    'system/app/WorldClock.apk', 'system/framework/framework-res.apk',
    'system/framework/com.htc.resources.apk',
    'system/framework/com.htc.android.rosie.jar',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('rom', type=Path)
    parser.add_argument('--output', type=Path, default=Path('references/3.33.401.153'))
    args = parser.parse_args()
    output = args.output
    output.mkdir(parents=True, exist_ok=True)
    with args.rom.open('rb') as source:
        rom_hash = hashlib.file_digest(source, 'sha256').hexdigest()
    report = {'rom': args.rom.name, 'sha256': rom_hash, 'properties': {}, 'files': []}
    with zipfile.ZipFile(args.rom) as rom:
        props = rom.read('system/build.prop').decode('utf-8', errors='replace')
        for line in props.splitlines():
            if '=' in line and not line.lstrip().startswith('#'):
                key, value = line.split('=', 1)
                report['properties'][key.strip()] = value.strip()
        for info in rom.infolist():
            name = info.filename
            selected = (name in PACKAGES or name == 'system/build.prop'
                        or (name.startswith('system/fonts/') and not info.is_dir())
                        or (name.startswith('system/customize/resource/')
                            and name.rsplit('/', 1)[-1].startswith('htc_wallpaper_')
                            and name.endswith('.jpg')))
            if not selected:
                continue
            # Never use arbitrary archive paths as extraction destinations.
            parts = name.split('/')
            if any(part in ('', '.', '..') for part in parts) or '\\' in name:
                raise ValueError('Unsafe archive path: ' + name)
            data = rom.read(info)
            destination = output.joinpath(*parts)
            resolved = destination.resolve()
            if not resolved.is_relative_to(output.resolve()):
                raise ValueError('Destination escapes output: ' + name)
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(data)
            entry = {'source': name, 'size': len(data),
                     'sha256': hashlib.sha256(data).hexdigest()}
            if name.endswith(('.apk', '.jar')):
                with zipfile.ZipFile(io.BytesIO(data)) as package:
                    entry['has_dex'] = 'classes.dex' in package.namelist()
                    entry['resources'] = [
                        {'path': item.filename, 'size': item.file_size,
                         'crc32': f'{item.CRC:08x}'}
                        for item in package.infolist()
                        if not item.is_dir() and item.filename.startswith(('res/', 'assets/'))
                    ]
            report['files'].append(entry)
    (output / 'inventory.json').write_text(json.dumps(report, indent=2) + '\n')
    print(f"Extracted {len(report['files'])} reference files to {output}")
    print(f"ROM SHA-256: {rom_hash}")
    print('Build:', report['properties'].get('ro.product.version'))
    print('Sense property:', report['properties'].get('ro.build.sense.version'))


if __name__ == '__main__':
    main()
