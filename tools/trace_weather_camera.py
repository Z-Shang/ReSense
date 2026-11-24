#!/usr/bin/env python3
"""Produce reproducible native camera call-site evidence from the stock ROM.

This traces runtime implementation statically. It does not execute the ARM libraries.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import struct
import subprocess
import zipfile
from inspect_weather_playback import elf_offset

LIBRARIES=('libmode10.so','libmode10GLES20.so','libmode10fx.so')
RANGES={
    'libmode10GLES20.so':[
        ('active-camera',0x28664,0x2867c),('viewport-setup',0x2a518,0x2a69c),
        ('orthographic-camera',0x28fec,0x290c0),('centering-flags',0x4a170,0x4a184),
        ('timeline-constructor',0x4a240,0x4a27c),('centralized-viewport',0x4a2ac,0x4a3a8),
        ('timeline-render',0x4c5d8,0x4ca80)],
    'libmode10.so': [('setup-distance',0x44b1c,0x44c5c),
                      ('setup-millimeter',0x44c5c,0x44ddc),('inherited-camera',0x758a8,0x75914)],
    'libmode10fx.so': [('cell-container-postload',0x10214c,0x102180),
                       ('scene-container-centering',0xfa424,0xfa510)]}


def verify_bindings(gles):
    vtable=0x5d140+8
    active=struct.unpack_from('<I',gles,elf_offset(gles,vtable+0x50))[0]
    setter=struct.unpack_from('<I',gles,elf_offset(gles,0x5db60+8+0xd4))[0]
    if active!=0x28665 or setter!=0x4a171:raise ValueError('Unexpected camera vtable bindings')
    project=struct.unpack_from('<I',gles,elf_offset(gles,vtable+0x2c))[0]
    viewport=struct.unpack_from('<I',gles,elf_offset(gles,vtable+0x24))[0]
    if project!=0x2a37d or viewport!=0x2a519:raise ValueError('Unexpected graphics viewport bindings')
    focal=struct.unpack_from('<f',gles,elf_offset(gles,0x2a694))[0]
    film=struct.unpack_from('<f',gles,elf_offset(gles,0x2a690))[0]
    return {'graphics_vtable_offset_0x50':{'function':'GetActiveCamera','thumb_pointer':hex(active)},
            'graphics_vtable_offset_0x2c':{'function':'ProjectCoordinate','thumb_pointer':hex(project)},
            'graphics_vtable_offset_0x24':{'function':'SetViewport','thumb_pointer':hex(viewport)},
            'timeline_vtable_offset_0xd4':{'function':'centering flags setter','thumb_pointer':hex(setter)},
            'viewport_setup_literals':{'focal_length_mm':focal,'film_gate_constant_mm':film}}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom',type=Path,default=Path('res/3.33.401.153.zip'))
    parser.add_argument('--output',type=Path,default=Path('.toolchain/camera-runtime-trace'))
    args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=True)
    objdump=shutil.which('llvm-objdump')
    if not objdump:parser.error('llvm-objdump is required for ARM Thumb disassembly')
    binaries={}
    with zipfile.ZipFile(args.rom)as rom:
        for name in LIBRARIES:
            binaries[name]=rom.read('system/lib/'+name)
    report={'method':'Static trace of runtime implementation, not an observed ARM execution',
            'libraries':{},'bindings':verify_bindings(binaries['libmode10GLES20.so']),
            'camera_value_capture_performed':False}
    for name,data in binaries.items():
        path=args.output/name;path.write_bytes(data)
        report['libraries'][name]={'sha256':hashlib.sha256(data).hexdigest(),'ranges':[]}
        for label,start,end in RANGES[name]:
            dest=args.output/(label+'.txt')
            result=subprocess.run([objdump,'-d','--demangle','--triple=thumbv7-linux-gnueabi',
                                   '--start-address='+hex(start),'--stop-address='+hex(end),str(path)],
                                  check=True,capture_output=True,text=True)
            dest.write_text(result.stdout)
            report['libraries'][name]['ranges'].append({'label':label,'start':hex(start),'end':hex(end),'file':dest.name})
    (args.output/'trace.json').write_text(json.dumps(report,indent=2)+'\n')
    print(args.output/'trace.json')


if __name__=='__main__':main()
