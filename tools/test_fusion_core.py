#!/usr/bin/env python3
"""Compile platform-independent runtime and compare ROM node counts with the research decoder."""
from pathlib import Path
import hashlib
import struct
import zipfile
import io
import subprocess
from mode10 import parse, walk, name_hash, properties, integer, fastlz, etc1

ROOT = Path(__file__).resolve().parents[1]

def main():
    output = ROOT / '.toolchain/fusion-tests'
    output.mkdir(parents=True, exist_ok=True)
    scenes = sorted((ROOT / 'references/3.33.401.153/weather-widgets/assets').rglob('*.m10'))
    if not scenes:
        raise SystemExit('Run tools/extract_weather_widgets.py first')
    corpus = output / 'corpus.tsv'
    corpus.write_text(''.join(f'{p}\t{sum(r.tag == name_hash("Node") and r.kind == 1 for r in walk(parse(p.read_bytes())))}\n' for p in scenes))
    # Differential texture fixtures are byte-exact Type records, wrapped in a Root.
    with zipfile.ZipFile(ROOT / 'res/3.33.401.153.zip') as rom:
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/fusion.apk'))) as apk:
            shared = apk.read('assets/Default/drawable/shared.m10')
    rows = []; counts = {6: 0, 14: 0}
    for record in walk(parse(shared)):
        if record.kind != 1 or record.tag != name_hash('Type'): continue
        p = properties(record)
        if name_hash('Pixels') not in p: continue
        fmt = integer(p[name_hash('Format')])
        if fmt not in counts or counts[fmt] >= 3: continue
        width, height = [integer(p[name_hash(k)]) for k in ('Width', 'Height')]
        pw, ph, offset, plane = struct.unpack('<4I', p[name_hash('Levels')].payload)
        if offset or width * height > 512 * 512: continue
        size = integer(next(c for c in record.children if c.tag == name_hash('DataDecompressedSize')))
        raw = fastlz(p[name_hash('Pixels')].payload, size)
        if fmt == 6:
            rgba = b''.join(raw[y*pw*4:(y*pw+width)*4] for y in range(height))
        else:
            color, alpha = etc1(raw[:plane], pw, ph), etc1(raw[plane:], pw, ph)
            rgba = bytes(v for y in range(height) for x in range(width) for v in (*color[(y*pw+x)*3:(y*pw+x)*3+3], alpha[(y*pw+x)*3]))
        encoded = shared[record.offset:record.offset+8+((len(record.payload)+3)&~3)]
        fixture = output / f'texture-{fmt}-{counts[fmt]}.m10'
        fixture.write_bytes(b'!01m\x02\0\0\0' + struct.pack('<II', (len(encoded)<<4)|1, name_hash('Root')) + encoded)
        rows.append(f'{fixture}\t{hashlib.sha256(rgba).hexdigest()}\n'); counts[fmt] += 1
    if not all(counts.values()): raise AssertionError(f'Missing texture format coverage: {counts}')
    textures = output / 'textures.tsv'; textures.write_text(''.join(rows))
    source = ROOT / 'sense-framework/src/main/java/dev/sense/framework/fusion'
    subprocess.run([str(ROOT / '.toolchain/jdk17/bin/javac'), '-d', str(output),
                    *[str(p) for p in sorted(source.glob('*.java')) if not any(s in p.read_text() for s in ['import android.', 'import javax.microedition.'])],
                    str(ROOT / 'tools/FusionCoreTest.java'), str(ROOT / 'tools/FusionTextureTest.java')], check=True)
    subprocess.run([str(ROOT / '.toolchain/jdk17/bin/java'), '-cp', str(output), 'FusionCoreTest', str(corpus)], check=True)
    subprocess.run([str(ROOT / '.toolchain/jdk17/bin/java'), '-cp', str(output), 'FusionTextureTest', str(textures)], check=True)

if __name__ == '__main__':
    main()
