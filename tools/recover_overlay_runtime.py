#!/usr/bin/env python3
"""Reproduce Pyramid overlay camera evidence without executing stock ARM code."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import shutil
import struct
import subprocess
import zipfile

from inspect_weather_playback import elf_offset
from mode10 import name_hash, parse, properties, text, walk
from trace_weather_camera import verify_bindings


def word(data, address):
    return struct.unpack_from('<I', data, elf_offset(data, address))[0]


def relocation(data, address):
    """Resolve ELF32 SHT_REL symbol names, including zero imported vtable words."""
    start = struct.unpack_from('<I', data, 32)[0]
    stride, count = struct.unpack_from('<HH', data, 46)
    sections = [struct.unpack_from('<10I', data, start+i*stride) for i in range(count)]
    for section in sections:
        if section[1] != 9:
            continue
        for offset in range(section[4], section[4]+section[5], section[9]):
            target, info = struct.unpack_from('<II', data, offset)
            if target != address:
                continue
            symbols = sections[section[6]]
            strings = sections[symbols[6]]
            symbol = symbols[4]+(info >> 8)*symbols[9]
            name = struct.unpack_from('<I', data, symbol)[0]+strings[4]
            return {'type': info & 255, 'symbol': data[name:data.index(0, name)].decode()}
    return None


def recover(binaries, scene):
    base, gles = binaries['libmode10.so'], binaries['libmode10GLES20.so']
    registrations = []
    # Literal-load address, PC-relative ADD instruction, property object offset.
    for literal, instruction, offset, expected in [
        (0x793a4, 0x79280, 0x130, 'Pivot'),
        (0x793a8, 0x79296, 0x138, 'PivotPosition'),
        (0x793ac, 0x792aa, 0x164, 'PivotRotation'),
        (0x793b0, 0x792be, 0x194, 'PivotScale'),
        (0x793b4, 0x792d2, 0x1c0, 'TwoDimension')]:
        address = (word(base, literal)+instruction+4) & 0xffffffff
        index = elf_offset(base, address)
        actual = base[index:base.index(0, index)].decode()
        if actual != expected:
            raise ValueError(f'Unexpected property at {literal:#x}: {actual}')
        registrations.append({'name': actual, 'object_offset': hex(offset),
                              'literal_address': hex(literal), 'add_instruction': hex(instruction)})
    bindings = verify_bindings(gles)
    initialize_literals = {name: struct.unpack_from('<f', gles, elf_offset(gles, address))[0]
                           for name, address in [('film_gate_mm', 0x314d8), ('focal_length_mm', 0x314dc)]}
    if initialize_literals != {'film_gate_mm': 36.0, 'focal_length_mm': 50.0}:
        raise ValueError('Unexpected initial surface camera literals')
    bindings['initialize_literals'] = initialize_literals
    visibility = word(binaries['libmode10fx.so'], 0x153710+0x88)
    if visibility != 0xade69:
        raise ValueError('Unexpected FxScene load visibility binding')
    bindings['scene_load_boolean'] = {'function': 'FxNodeControl::SetVisibility(bool)',
                                      'thumb_pointer': hex(visibility)}
    for offset, pointer, name in [(0xa8, 0x28685, 'PushCameraDepth'),
                                  (0xac, 0x28695, 'PopCameraDepth'),
                                  (0xb0, 0x286a5, 'SetCameraDepth')]:
        actual = word(gles, 0x5d148+offset)
        if actual != pointer:
            raise ValueError(f'Unexpected {name} binding')
        bindings[name] = hex(actual)
    imported = {}
    for offset, expected in [(0x7c, 'InternalSelect'), (0xe4, 'PolicyRender')]:
        address = 0x5db68+offset
        entry = relocation(gles, address)
        if not entry or expected not in entry['symbol'] or entry['type'] != 2:
            raise ValueError(f'Unexpected imported Timeline slot {offset:#x}')
        imported[hex(offset)] = {'raw_word': hex(word(gles, address)), **entry}
    flags = []
    for node in walk(parse(scene)):
        if node.tag != name_hash('Node'):
            continue
        props = properties(node)
        if name_hash('Name') not in props:
            continue
        item = {'name': text(props[name_hash('Name')]), 'offset': node.offset}
        for name in ('Pivot', 'TwoDimension'):
            if name_hash(name) in props:
                payload = bytes(props[name_hash(name)].payload)
                if payload not in (b'\0', b'\1'):
                    raise ValueError('Unexpected boolean encoding')
                item[name] = payload == b'\1'
        if len(item) > 2:
            flags.append(item)
    return {'method': 'Static ARM Thumb trace; no original runtime execution',
            'sha256': {name: hashlib.sha256(data).hexdigest() for name, data in binaries.items()},
            'scene_sha256': hashlib.sha256(scene).hexdigest(),
            'property_registrations': registrations, 'bindings': bindings,
            'timeline_imported_slots': imported, 'scene_flags': flags,
            'camera_distance_examples': [{'width': w, 'distance': 50*w/36} for w in (540,1080,1440)]}


RANGES = {
    'libmode10.so': [('transform-properties', 0x791ec, 0x793d0),
                     ('quaternion-matrix', 0x77e48, 0x77f0c),
                     ('native-matrix-math', 0xb44a0, 0xb4510),
                     ('pivot-transform', 0x78728, 0x78920),
                     ('transform-render', 0x79748, 0x79790),
                     ('timeline-policy', 0x77744, 0x77960)],
    'libmode10GLES20.so': [('camera-depth', 0x28684, 0x286dc),
                          ('project-coordinate', 0x2a37c, 0x2a45c),
                          ('camera-view-matrix', 0x1bca0, 0x1bd24),
                          ('camera-update', 0x1c530, 0x1c898),
                          ('camera-projection', 0x1cf04, 0x1d244),
                          ('device-view-matrix', 0x2c458, 0x2c790),
                          ('device-initial-state', 0x2c7c4, 0x2c820),
                          ('framebuffer-view-adjustment', 0x4cf38, 0x4cf60),
                          ('projection-selection', 0x2a268, 0x2a354),
                          ('timeline-pre-render', 0x4b1f0, 0x4b3f4),
                          ('device-camera-initialize', 0x311d4, 0x31274),
                          ('initialize-literals', 0x314d8, 0x314e0)],
    'libmode10fx.so': [('view-create-scene', 0xc18a0, 0xc1918),
                       ('setup-view', 0xabd10, 0xabfbc),
                       ('scene-load-visibility', 0xba0ea, 0xba106),
                       ('view-render', 0xc1804, 0xc18a0),
                       ('scene-render', 0xb9dac, 0xb9df0)]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom', type=Path, default=Path('res/3.33.401.153.zip'))
    parser.add_argument('--output', type=Path, default=Path('.toolchain/htc-overlay-recovery'))
    args = parser.parse_args()
    objdump = shutil.which('llvm-objdump')
    if not objdump:
        parser.error('llvm-objdump is required')
    with zipfile.ZipFile(args.rom) as rom:
        binaries = {name: rom.read('system/lib/'+name) for name in RANGES}
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/HtcWeatherWallpaper.apk'))) as apk:
            scene = apk.read('assets/Port/weather_overlay.m10')
    report = recover(binaries, scene)
    args.output.mkdir(parents=True, exist_ok=True)
    for name, ranges in RANGES.items():
        path = args.output/name
        path.write_bytes(binaries[name])
        for label, start, end in ranges:
            result = subprocess.run([objdump, '-d', '--demangle', '--triple=thumbv7-linux-gnueabi',
                                     '--start-address='+hex(start), '--stop-address='+hex(end), str(path)],
                                    check=True, capture_output=True, text=True)
            (args.output/(label+'.txt')).write_text(result.stdout)
    destination = args.output/'recovery.json'
    destination.write_text(json.dumps(report, indent=2)+'\n')
    print(destination)


if __name__ == '__main__':
    main()
