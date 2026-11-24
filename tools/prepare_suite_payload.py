#!/usr/bin/env python3
"""Embed only the dependency-ordered shared APK set in the bootstrap installer."""
import hashlib
import json
import shutil
from pathlib import Path

MODULES = ('sense-framework', 'sense-services', 'launcher', 'weather', 'phone', 'contacts', 'messages', 'lockscreen')
ROOT = Path(__file__).resolve().parents[1]

def prepare(root=ROOT):
    source = root/'build/outputs/shared-debug'
    files = [source/f'{module}-debug.apk' for module in MODULES]
    for path in files:
        if not path.is_file():
            raise FileNotFoundError(path)
    output = root/'installer/build/generated/suite/assets/suite'
    output.mkdir(parents=True, exist_ok=True)
    for path in output.iterdir():
        if path.is_file():
            path.unlink()
    entries = []
    for module, path in zip(MODULES, files):
        shutil.copyfile(path, output/path.name)
        entries.append(dict(module=module, file=path.name, size=path.stat().st_size,
                            sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
    (output/'manifest.json').write_text(json.dumps(dict(format=1, packages=entries), indent=2)+'\n')
    shutil.copyfile(source/'distribution.json', output/'distribution.json')
    print(f'Bundled {len(entries)} shared APKs in {output}')

if __name__ == '__main__':
    prepare()
