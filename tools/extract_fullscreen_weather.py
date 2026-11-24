#!/usr/bin/env python3
"""Recover the stock home weather overlay, its seven sub-scenes and exact mapping."""
import hashlib,io,json,struct,zipfile
from pathlib import Path
from mode10 import parse,walk,properties,name_hash,integer,png
from decode_navbar import geometry
from extract_weather_catalog import texture_records,decode_texture,track
from inspect_weather_playback import scene_timing

GROUPS=['01_Sun_cloud_light','02_Party_cloudy','03_Rain','04_ICE / snow','05_Sun','06_Thunder','07_Windy']
LABELS=['Sunlight and light clouds','Clouds','Rain and wiper','Ice and snow','Sunlight','Thunderstorm','Wind']

def condition_map(dex):
    header=struct.pack('<HHI',0x300,4,54);candidates=[];start=0
    while (offset:=dex.find(header,start))>=0:
        start=offset+1
        if offset+8+54*4>len(dex):continue
        values=list(struct.unpack_from('<54i',dex,offset+8))
        if all(0<=v<=7 for v in values) and set(values)==set(range(8)):candidates.append((offset,values))
    if len(candidates)!=1:raise ValueError('Expected one stock condition-to-overlay table')
    return candidates[0]

def extract(rom_path,output):
    output=Path(output);raw=output/'raw';images=output/'drawable-nodpi'
    raw.mkdir(parents=True,exist_ok=True);images.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(rom_path)as rom:
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/HtcWeatherWallpaper.apk')))as apk:
            data=apk.read('assets/Port/weather_overlay.m10');dex=apk.read('classes.dex')
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/fusion.apk')))as fusion:shared=fusion.read('assets/Default/drawable/shared.m10')
    root=parse(data);shared_root=parse(shared)
    material={}
    for tree,source in [(root,'HtcWeatherWallpaper.apk!/assets/Port/weather_overlay.m10'),(shared_root,'fusion.apk!/assets/Default/drawable/shared.m10')]:
        for record,props in texture_records(tree):material.setdefault(integer(props[name_hash('ID')]),(record,props,source))
    original=geometry(root);index={n['offset']:i for i,n in enumerate(original)}
    records={n.offset:n for n in walk(root)if n.tag==name_hash('Node')}
    groups={n['name']:i for i,n in enumerate(original)if n['parent_offset']==original[0]['offset']}
    timelines={t['node_offset']:t for t in scene_timing(data)}
    decoded={};scenes=[]
    fields=['Position','Rotation','Scale','PivotPosition','Opacity']
    for effect,name in enumerate(GROUPS,1):
        selected={0,groups[name]}
        for i,node in enumerate(original):
            if node['parent_offset'] is not None and index[node['parent_offset']]in selected and index[node['parent_offset']]!=0:selected.add(i)
        source_indices=sorted(selected);remap={old:new for new,old in enumerate(source_indices)};nodes=[]
        for old in source_indices:
            node=original[old];props=node['properties'];full=properties(records[node['offset']])
            parent=index.get(node['parent_offset'],-1)
            item={'name':node['name'],'parent':remap.get(parent,-1),'transformParent':-1,
                  'tracks':{k:track(props[k])for k in fields if k in props},
                  'inFrame':struct.unpack('<f',full[name_hash('InFrame')].payload)[0],
                  'duration':struct.unpack('<f',full[name_hash('Duration')].payload)[0],
                  'visible':bytes(full[name_hash('Visible')].payload)!=b'\0',
                  'childStart':struct.unpack('<f',full[name_hash('TimelineInFrame')].payload)[0]if name_hash('TimelineInFrame')in full else 0,
                  'Layer':integer(full[name_hash('Layer')]),'LayerParent':integer(full[name_hash('LayerParent')])}
            if'DiffuseMap'in props:
                ident=props['DiffuseMap']['value_u32'];resource=f'htc_overlay_tex_{ident:08x}'
                if ident not in decoded:
                    if ident==0:
                        png(images/(resource+'.png'),1,1,b'\xff\xff\xff\xff',alpha=True)
                        info={'width':1,'height':1,'source':'White Solid 1: untextured white material'}
                    else:
                        record,p,source=material[ident];info=decode_texture(record,p,images/(resource+'.png'));info['source']=source
                    decoded[ident]={'resource':resource,'id':ident,**info}
                item['texture']=resource;item['vertices']=props['Vertices']['value']
                if len(item['vertices'])!=4:raise ValueError('Expected overlay quad')
            nodes.append(item)
        for i,node in enumerate(nodes):
            if node['LayerParent']!=0xffffffff:
                matches=[j for j,n in enumerate(nodes)if n['parent']==node['parent']and n['Layer']==node['LayerParent']]
                if len(matches)!=1 or matches[0]==i:raise ValueError('Invalid overlay layer parent')
                node['transformParent']=matches[0]
        order=[]
        def visit(parent):
            children=sorted([i for i,n in enumerate(nodes)if n['parent']==parent],key=lambda i:nodes[i]['Layer'],reverse=True)
            for i in children:
                if'texture'in nodes[i]:order.append(i)
                visit(i)
        visit(-1)
        timing=timelines[original[groups[name]]['offset']];marker=next(m for m in timing['markers']if m['name']=='start')
        scene={'code':effect,'width':540,'height':960,'fps':timing['fields']['FramesPerSecond']['value'],'speed':timing['fields']['PlaybackSpeed']['value'],
               'entrance':marker,'timelines':[timelines[n['offset']]for n in original if n['offset']in timelines and index[n['offset']]in selected],
               'nodes':nodes,'drawOrder':order}
        resource=f'htc_overlay_scene_{effect:02d}';(raw/(resource+'.json')).write_text(json.dumps(scene,separators=(',',':'))+'\n')
        scenes.append({'effect':effect,'label':LABELS[effect-1],'resource':resource,'nodes':len(nodes),'sprites':len(order)})
    offset,mapping=condition_map(dex)
    report={'source':'HtcWeatherWallpaper.apk!/assets/Port/weather_overlay.m10','sha256':hashlib.sha256(data).hexdigest(),
            'width':540,'height':960,'mapping':mapping,'mapping_dex_offset':offset,'mapping_dex_sha256':hashlib.sha256(dex).hexdigest(),'scenes':scenes,'textures':list(decoded.values())}
    (raw/'htc_overlay_catalog.json').write_text(json.dumps(report,separators=(',',':'))+'\n')
    return report
if __name__=='__main__':
    import argparse
    p=argparse.ArgumentParser();p.add_argument('--rom',required=True);p.add_argument('--output',required=True);a=p.parse_args();print(json.dumps(extract(a.rom,a.output)['scenes'],indent=2))
