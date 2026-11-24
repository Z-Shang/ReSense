#!/usr/bin/env python3
"""Recover Mode10 dock glyphs and Rosie's original phone artwork from the local ROM."""
import hashlib
import io
import struct
import zipfile
from pathlib import Path
from decode_navbar import geometry
from mode10 import etc1, fastlz, integer, name_hash, parse, png, properties, walk


def extract(rom_path, output):
    output=Path(output);output.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(rom_path)as rom:
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/Rosie.apk')))as apk:
            scene=apk.read('assets/Port/scenes/Rosie_navbar.m10')
            phone_path='res/drawable-hdpi/icon_p_phone_rest.png'
            phone=apk.read(phone_path)
            remove_icons={state:apk.read('res/drawable-port-hdpi/icon_remove_panel_'+state+'.png') for state in ('out','over')}
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/fusion.apk')))as apk:
            shared=apk.read('assets/Default/drawable/shared.m10')
    targets={'home_icon_all_apps.png':'sense_dock_apps','home_icon_settings.png':'sense_dock_settings',
             'home_icon_add_page.png':'sense_dock_add_page','home_icon_trash_rest.png':'sense_dock_trash'}
    bindings={}
    for node in geometry(parse(scene)):
        if node['name']in targets:
            bindings[node['properties']['DiffuseMap']['value_u32']]=(node['name'],targets[node['name']])
    if len(bindings)!=len(targets):raise ValueError('Missing dock icon material')
    assets=[]
    for record in walk(parse(shared)):
        if record.tag!=name_hash('Type')or record.kind!=1:continue
        props=properties(record)
        if name_hash('ID')not in props:continue
        material=integer(props[name_hash('ID')])
        if material not in bindings:continue
        original,resource=bindings[material]
        width,height,fmt=(integer(props[name_hash(k)])for k in ('Width','Height','Format'))
        if fmt!=14 or len(props[name_hash('Levels')].payload)!=16:raise ValueError('Unsupported icon texture')
        pw,ph,offset,size=struct.unpack('<4I',props[name_hash('Levels')].payload)
        if offset or not(0<width<=pw<=4096 and 0<height<=ph<=4096):raise ValueError('Invalid icon extents')
        expanded=next(integer(c)for c in record.children if c.tag==name_hash('DataDecompressedSize'))
        raw=fastlz(props[name_hash('Pixels')].payload,expanded)
        if len(raw)!=size*2:raise ValueError('Invalid icon texture planes')
        color,alpha=etc1(raw[:size],pw,ph),etc1(raw[size:],pw,ph)
        rgba=bytearray()
        for y in range(height):
            for x in range(width):
                i=(y*pw+x)*3;rgba.extend((*color[i:i+3],alpha[i]))
        file=resource+'.png';png(output/file,width,height,rgba,alpha=True)
        assets.append({'file':file,'original':original,'material':material,'record_offset':record.offset,
                       'size':[width,height],'source':'fusion.apk!/assets/Default/drawable/shared.m10'})
    if len(assets)!=len(targets):raise ValueError('Dock icons unresolved')
    (output/'sense_dock_phone.png').write_bytes(phone)
    assets.append({'file':'sense_dock_phone.png','source':'Rosie.apk!/'+phone_path,'sha256':hashlib.sha256(phone).hexdigest()})
    for state,data in remove_icons.items():
        file='sense_remove_'+state+'.png';(output/file).write_bytes(data)
        assets.append({'file':file,'source':'Rosie.apk!/res/drawable-port-hdpi/icon_remove_panel_'+state+'.png','sha256':hashlib.sha256(data).hexdigest()})
    return {'scene':'Rosie.apk!/assets/Port/scenes/Rosie_navbar.m10',
            'scene_sha256':hashlib.sha256(scene).hexdigest(),'assets':assets}
