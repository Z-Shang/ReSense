#!/usr/bin/env python3
"""Build a local catalogue of every original clock weather scene and its textures."""
import hashlib
import io
import json
from pathlib import Path
import struct
import zipfile
from decode_navbar import geometry
from inspect_weather_playback import scene_timing
from mode10 import etc1,fastlz,integer,name_hash,parse,png,properties,walk,text


HTC_NAMES={1:'Sunny',2:'Mostly Sunny',3:'Partly Sunny',4:'Intermittent Clouds',5:'Hazy Sunshine',6:'Mostly Cloudy',7:'Cloudy',8:'Dreary',11:'Fog',12:'Showers',13:'Mostly Cloudy with Showers',14:'Partly Sunny with Showers',15:'Thunderstorms',16:'Mostly Cloudy with Thunder Showers',17:'Partly Sunny with Thunder Showers',18:'Rain',19:'Flurries',20:'Mostly Cloudy with Flurries',21:'Partly Sunny with Flurries',22:'Snow',23:'Mostly Cloudy with Snow',24:'Ice',25:'Sleet',26:'Freezing Rain',29:'Rain and Snow Mixed',30:'Hot',31:'Cold',32:'Windy',33:'Clear',34:'Mostly Clear',35:'Partly Cloudy',36:'Intermittent Clouds',37:'Hazy',38:'Mostly Cloudy',39:'Partly Cloudy with Showers',40:'Mostly Cloudy with Showers',41:'Partly Cloudy with Thunder Showers',42:'Mostly Cloudy with Thunder Showers',43:'Partly Cloudy with Flurries',44:'Mostly Cloudy with Flurries'}


def texture_records(root):
    for record in walk(root):
        if record.tag!=name_hash('Type')or record.kind!=1:continue
        try:props=properties(record)
        except ValueError:continue
        if name_hash('Pixels')in props and name_hash('ID')in props:yield record,props


def decode_texture(record,props,output,flip_y=False):
    width,height,fmt=(integer(props[name_hash(k)])for k in ('Width','Height','Format'))
    if not(0<width<=4096 and 0<height<=4096):raise ValueError('Invalid texture dimensions')
    pw,ph,offset,plane=struct.unpack('<4I',props[name_hash('Levels')].payload)
    if offset or pw<width or ph<height or pw>4096 or ph>4096:raise ValueError('Unsupported texture extent')
    size=next(integer(c)for c in record.children if c.tag==name_hash('DataDecompressedSize'))
    raw=fastlz(props[name_hash('Pixels')].payload,size)
    rgba=bytearray()
    if fmt==14:
        if len(raw)!=plane*2:raise ValueError('Invalid dual ETC1 texture')
        rgb,alpha=etc1(raw[:plane],pw,ph),etc1(raw[plane:],pw,ph)
        for y in range(height):
            for x in range(width):
                i=(y*pw+x)*3;rgba.extend((*rgb[i:i+3],alpha[i]))
    elif fmt==13:
        if len(raw)!=plane or plane!=((pw+3)//4)*((ph+3)//4)*8:raise ValueError('Invalid opaque ETC1 texture')
        rgb=etc1(raw,pw,ph)
        for y in range(height):
            for x in range(width):
                i=(y*pw+x)*3;rgba.extend((*rgb[i:i+3],255))
    elif fmt==6:
        if len(raw)!=pw*ph*4:raise ValueError('Invalid RGBA8888 texture')
        for y in range(height):rgba.extend(raw[y*pw*4:(y*pw+width)*4])
    else:raise ValueError(f'Unsupported texture format {fmt}')
    if flip_y:rgba=b"".join(rgba[y*width*4:(y+1)*width*4] for y in range(height-1,-1,-1))
    png(output,width,height,rgba,alpha=True)
    return {'width':width,'height':height,'format':fmt,'pixel_sha256':hashlib.sha256(rgba).hexdigest()}


def track(prop):
    if 'value'in prop:return {'v':prop['value']}
    if 'samples'in prop:return {'s':[[s['frame'],*s['value']]for s in prop['samples']]}
    raise ValueError('Unresolved animation property')


def extract(rom_path,res):
    res=Path(res);raw=res/'raw';images=res/'drawable-nodpi';raw.mkdir(parents=True,exist_ok=True);images.mkdir(parents=True,exist_ok=True)
    roots={};scene_bytes={}
    with zipfile.ZipFile(rom_path)as rom:
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/HtcClock3DWidget.apk')))as apk:
            widget_bytes=apk.read('assets/Port/clock_weather.m10')
            for name in sorted(apk.namelist()):
                if name.startswith('assets/Port/s_')and name.endswith('.m10'):
                    code=int(Path(name).stem[2:]);scene_bytes[code]=apk.read(name);roots[code]=parse(scene_bytes[code])
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/fusion.apk')))as apk:shared=parse(apk.read('assets/Default/drawable/shared.m10'))
    widget_nodes=geometry(parse(widget_bytes))
    widget=next(n for n in widget_nodes if n['name']=='Rosie_Workspace')
    weather=next(n for n in widget_nodes if n['name']=='Forecast_Weather_state')
    def initial(prop):
        return prop['value'] if 'value'in prop else prop['samples'][0]['value']
    geometry_report={'source':'HtcClock3DWidget.apk!/assets/Port/clock_weather.m10',
                     'sha256':hashlib.sha256(widget_bytes).hexdigest(),
                     'widgetDimensions':initial(widget['properties']['Dimensions']),
                     'weatherDimensions':initial(weather['properties']['Dimensions']),
                     'weatherPosition':initial(weather['properties']['Position']),
                     'weatherScale':initial(weather['properties']['Scale']),
                     'weatherPivot':initial(weather['properties']['PivotPosition'])}
    (raw/'htc_weather_widget_geometry.json').write_text(json.dumps(geometry_report,indent=2)+'\n')
    records={integer(p[name_hash('ID')]):(r,p,'fusion.apk!/assets/Default/drawable/shared.m10')for r,p in texture_records(shared)}
    embedded={}
    for code,root in roots.items():
        embedded[code]={integer(p[name_hash('ID')]):(r,p,f'HtcClock3DWidget.apk!/assets/Port/s_{code:02d}.m10')for r,p in texture_records(root)}
    decoded={};scenes=[]
    fields=('Position','Rotation','Scale','PivotPosition','PivotRotation','PivotScale','Opacity')
    for code,root in roots.items():
        original=geometry(root);by_offset={n['offset']:i for i,n in enumerate(original)}
        node_records={r.offset:r for r in walk(root)if r.tag==name_hash('Node')}
        nodes=[]
        for i,node in enumerate(original):
            props=node['properties'];full=properties(node_records[node['offset']])
            item={'name':node['name'],'parent':by_offset.get(node['parent_offset'],-1),
                  'tracks':{k:track(props[k])for k in fields if k in props}}
            for key in ('Layer','LayerParent'):
                value=integer(full[name_hash(key)])if name_hash(key)in full else 0xffffffff
                item[key]=-1 if value==0xffffffff else value
            item['inFrame']=struct.unpack('<f',full[name_hash('InFrame')].payload)[0]
            item['duration']=struct.unpack('<f',full[name_hash('Duration')].payload)[0]
            item['visible']=bytes(full[name_hash('Visible')].payload)!=b'\0'if name_hash('Visible')in full else True
            if 'DiffuseMap'in props:
                material=props['DiffuseMap']['value_u32'];record,p,source=embedded[code].get(material,records.get(material,(None,None,None)))
                if record is None:raise ValueError(f'Missing material {material} in {code}')
                resource=f'htc_weather_tex_{material:08x}'
                if resource not in decoded:
                    info=decode_texture(record,p,images/(resource+'.png'));info.update({'resource':resource,'id':material,'source':source,'offset':record.offset});decoded[resource]=info
                item['texture']=resource
                vertices=props.get('Vertices',{}).get('value')
                if not vertices or len(vertices)!=4:raise ValueError('Non-quad weather sprite')
                item['vertices']=vertices
            nodes.append(item)
        # LayerParent references a sibling layer, separate from the scene graph parent.
        for i,node in enumerate(nodes):
            parent_layer=node['LayerParent'];node['transformParent']=-1
            if parent_layer>=0:
                match=[j for j,n in enumerate(nodes)if n['parent']==node['parent']and n['Layer']==parent_layer]
                if len(match)!=1 or match[0]==i:raise ValueError('Invalid layer parent')
                node['transformParent']=match[0]
        timelines=scene_timing(scene_bytes[code]);timing=next((t for t in timelines if any(m['name']=='In'for m in t['markers'])),None)
        if timing is None:raise ValueError(f'No entrance marker in {code}')
        entrance=next(m for m in timing['markers']if m['name']=='In')
        draw_order=[]
        def visit(parent):
            children=[i for i,n in enumerate(nodes)if n['parent']==parent]
            children.sort(key=lambda i:nodes[i]['Layer'],reverse=True)
            for index in children:
                if 'texture'in nodes[index]:draw_order.append(index)
                visit(index)
        visit(-1)
        scene={'code':code,'nodes':nodes,'drawOrder':draw_order,'fps':timing['fields']['FramesPerSecond']['value'],
               'speed':timing['fields']['PlaybackSpeed']['value'],'entrance':entrance,'timelines':timelines}
        resource=f'htc_weather_scene_{code:02d}';(raw/(resource+'.json')).write_text(json.dumps(scene,separators=(',',':'))+'\n')
        scenes.append({'code':code,'label':HTC_NAMES[code],'resource':resource,'node_count':len(nodes),'sprites':len(draw_order),'source_sha256':hashlib.sha256(scene_bytes[code]).hexdigest()})
    report={'source':'HtcClock3DWidget.apk!/assets/Port/s_*.m10','scenes':scenes,'textures':list(decoded.values()),
            'notes':'All source conditions preserved. WMO mapping is a semantic approximation; camera remains adapted.'}
    (raw/'htc_weather_catalog.json').write_text(json.dumps(report,separators=(',',':'))+'\n')
    (res.parent/'weather-catalog-provenance.json').write_text(json.dumps(report,indent=2)+'\n')
    return report


if __name__=='__main__':
    import argparse
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--rom',default='res/3.33.401.153.zip');p.add_argument('--output',default='.toolchain/weather-catalog/res')
    args=p.parse_args();report=extract(args.rom,args.output)
    print(f"Recovered {len(report['scenes'])} scenes and {len(report['textures'])} textures")
