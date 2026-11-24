#!/usr/bin/env python3
"""Recover scene timing and native camera defaults; do not equate defaults with active camera."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import struct
import zipfile
from mode10 import integer, name_hash, parse, properties, text, walk


def marker_table(payload):
    data=bytes(payload)
    if len(data)<4: raise ValueError('Truncated marker count')
    count=struct.unpack_from('<I',data)[0]
    if count>1024: raise ValueError('Excessive marker count')
    offset=4
    markers=[]
    for _ in range(count):
        if offset+12>len(data): raise ValueError('Truncated marker')
        start,length,chars=struct.unpack_from('<III',data,offset);offset+=12
        if chars>1024 or offset+chars*2>len(data): raise ValueError('Invalid marker name')
        name=data[offset:offset+chars*2].decode('utf-16le');offset+=chars*2
        markers.append({'name':name,'start':start,'frame_count':length,
                        'last_frame':start+max(0,length-1)})
    if offset!=len(data): raise ValueError('Unparsed marker bytes')
    return markers


def scene_timing(scene):
    timelines=[]
    for record in walk(parse(scene)):
        if record.tag!=name_hash('Node'):continue
        props=properties(record)
        if name_hash('FramesPerSecond') not in props:continue
        fields={}
        for key in ('FramesPerSecond','PlaybackSpeed','TimelineInFrame','TimelineDuration'):
            value=props.get(name_hash(key))
            if value is not None:
                fields[key]={'value':struct.unpack('<f',value.payload)[0],'value_offset':value.offset+8}
        markers=props.get(name_hash('Markers'))
        timelines.append({'name':text(props[name_hash('Name')]),'node_offset':record.offset,
                          'fields':fields,'markers':marker_table(markers.payload) if markers else [],
                          'marker_value_offset':markers.offset+8 if markers else None})
    return timelines


def elf_offset(data,address):
    if data[:6]!=b'\x7fELF\x01\x01':raise ValueError('Expected little-endian ELF32')
    phoff=struct.unpack_from('<I',data,28)[0]
    size,count=struct.unpack_from('<HH',data,42)
    for i in range(count):
        kind,offset,base,_,length,*_=struct.unpack_from('<8I',data,phoff+i*size)
        if kind==1 and base<=address<base+length:return offset+address-base
    raise ValueError('Unmapped native address')


def camera_defaults(data):
    # Verified constructor literal loads and RegisterProperties PC-relative names in this exact ROM.
    names=[('HorizontalFOV',0x42c60,0x42bf2,0x44ff4),
           ('VerticalFOV',0x42c64,0x42c08,0x44ff8),
           ('NearClip',0x42c68,0x42c1e,0x44ffc),
           ('FarClip',0x42c6c,0x42c34,0x45000)]
    result={}
    for name,pool,pc,literal in names:
        address=(pc+struct.unpack_from('<I',data,elf_offset(data,pool))[0])&0xffffffff
        offset=elf_offset(data,address)
        if data[offset:offset+len(name)+1]!=name.encode()+b'\0':raise ValueError('Native camera layout differs')
        value=struct.unpack_from('<f',data,elf_offset(data,literal))[0]
        result[name]={'default':value,'literal_virtual_address':hex(literal),
                      'property_name_virtual_address':hex(address)}
    result['Viewport']={'default':[0,0,800,480],'literal_virtual_addresses':['0x45004','0x45008']}
    result['Ortho']={'default':False,'constructor_instruction':'0x44f08 movs r3, #0; stored for property at +0x2a0'}
    return {'source':'system/lib/libmode10.so','sha256':hashlib.sha256(data).hexdigest(),
            'constructor_virtual_address':'0x44ef8','defaults':result,
            'active_widget_camera_confirmed':False,
            'caveat':'GraphicsDeviceGL2/TimelineGL2 reconfigure cameras at runtime; these constructor defaults do not identify the effective weather camera.'}


def inspect(rom):
    with zipfile.ZipFile(rom)as archive:
        with zipfile.ZipFile(io.BytesIO(archive.read('system/app/HtcClock3DWidget.apk')))as apk:
            scene=apk.read('assets/Port/s_03.m10')
        camera=archive.read('system/lib/libmode10.so')
    return {'scene':'HtcClock3DWidget.apk!/assets/Port/s_03.m10',
            'sha256':hashlib.sha256(scene).hexdigest(),
            'timelines':scene_timing(scene),'camera':camera_defaults(camera),
            'timing_caveat':'Frame-coordinate spans / FPS give elapsed sample time; inclusive marker end scheduling may add one frame.'}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom',type=Path,default=Path('res/3.33.401.153.zip'))
    parser.add_argument('--output',type=Path,default=Path('.toolchain/camera-timing/recovery.json'))
    args=parser.parse_args();args.output.parent.mkdir(parents=True,exist_ok=True)
    report=inspect(args.rom);args.output.write_text(json.dumps(report,indent=2)+'\n')
    print(args.output)
