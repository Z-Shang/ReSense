"""Package original standalone Weather Mode10 layouts/scenes and only their shared texture dependencies."""
import hashlib
import io
import json
from pathlib import Path
import struct
import zipfile
import re
from mode10 import parse, walk, properties, integer, text, name_hash

def prepare(rom_path, output):
    output=Path(output);raw=output/'raw';images=output/'drawable-nodpi';raw.mkdir(parents=True,exist_ok=True);images.mkdir(parents=True,exist_ok=True)
    needed=set();provenance=[];metadata={}
    with zipfile.ZipFile(rom_path) as rom:
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/HtcWeather3DWidget.apk'))) as apk:
            for entry in apk.namelist():
                if not entry.startswith('assets/Port/') or not entry.endswith('.m10'):continue
                data=apk.read(entry);stem=Path(entry).stem
                resource='sense_weather_widget_'+('layout_'+stem.removeprefix('Weather_') if stem.startswith('Weather_') else 'condition_'+stem)
                (raw/(resource+'.m10')).write_bytes(data);provenance.append({'source':'HtcWeather3DWidget.apk!/'+entry,'resource':resource,'sha256':hashlib.sha256(data).hexdigest()})
                root=parse(data)
                labels=[]
                for node in walk(root):
                    if node.kind!=1 or node.tag!=name_hash('Node'):continue
                    p=properties(node)
                    if name_hash('DiffuseMap')in p:
                        value=integer(p[name_hash('DiffuseMap')])
                        if value:needed.add(value)
                    if stem.startswith('Weather_'):
                        name=text(p[name_hash('Name')])
                        if name in ('common_panel.9.png','common_divider_double.9.png'):needed.add(name_hash(name))
                        # Recovered text controls refer to a named font and an explicit pixel height.
                        if 0x333f0989 in p:
                            font=text(p[0x333f0989]);pixels=re.search(r'(\d+)A$',font)
                            labels.append({'offset':node.offset,'name':name,'font':font,'height':int(pixels.group(1))if pixels else struct.unpack('<f',p[0x42312f46].payload)[0],
                                           'color':list(struct.unpack('<4f',p[name_hash('Color')].payload))if name_hash('Color')in p else [1,1,1,1],
                                           'string':text(p[name_hash('String')])if name_hash('String')in p else '',
                                           'size':list(struct.unpack('<2f',p[name_hash('Size')].payload))if name_hash('Size')in p else [0,0]})
                if stem.startswith('Weather_'):metadata[stem.removeprefix('Weather_')]={'labels':labels}
            for size in ('2x1','2x2','4x4'):
                (images/f'sense_weather_widget_preview_{size}.png').write_bytes(apk.read(f'res/drawable-hdpi/preview_weather_{size}.png'))
            for entry in apk.namelist():
                if Path(entry).name.startswith('weather_forecast_') and entry.endswith('.png'):
                    (images/('sense_widget_'+Path(entry).name)).write_bytes(apk.read(entry))
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/fusion.apk'))) as fusion:
            data=fusion.read('assets/Default/drawable/shared.m10');records=[];found=set()
            for node in walk(parse(data)):
                if node.kind!=1 or node.tag!=name_hash('Type'):continue
                try:p=properties(node)
                except ValueError:continue
                if name_hash('Pixels')not in p or name_hash('ID')not in p:continue
                id=integer(p[name_hash('ID')])
                if id in needed:
                    found.add(id);records.append(data[node.offset:node.offset+8+((len(node.payload)+3)&~3)])
            # Some dependencies are embedded in the individual scene documents; their IDs stay local.
            payload=b''.join(records);shared=b'!01m\x02\0\0\0'+struct.pack('<II',(len(payload)<<4)|1,name_hash('Root'))+payload
            (raw/'sense_weather_widget_shared.m10').write_bytes(shared)
        for entry in ('system/fonts/DroidSans.ttf','system/fonts/HelveticaNeueLTPro35Th.ttf'):
            if entry in rom.namelist():
                (raw/('sense_widget_'+Path(entry).name.lower())).write_bytes(rom.read(entry))
    (raw/'sense_weather_widget_metadata.json').write_text(json.dumps(metadata,indent=2)+'\n')
    (raw/'sense_weather_widget_provenance.json').write_text(json.dumps(provenance,indent=2)+'\n')
    print(f'Prepared {len(provenance)} original widget scenes and {len(found)} shared textures')

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--rom',required=True);parser.add_argument('--output',required=True);args=parser.parse_args();prepare(args.rom,args.output)
