"""Recover Leap's seven-slot geometry and per-selected-page position/quaternion tracks."""
import hashlib, io, struct, zipfile
from pathlib import Path
from decode_navbar import geometry
from inspect_weather_playback import scene_timing
from mode10 import parse,walk,properties,name_hash,integer,fastlz,etc1,png


def extract(rom_path,output):
    with zipfile.ZipFile(rom_path) as rom:
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/Rosie.apk'))) as apk:
            data=apk.read('assets/Port/scenes/Rosie_leap.m10')
            rearrange=apk.read('assets/Port/scenes/Rosie_leap_rearrange.m10')
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/fusion.apk'))) as apk:
            shared=apk.read('assets/Default/drawable/shared.m10')
    nodes=geometry(parse(data));by_name={n['name']:n for n in nodes}
    def at(prop,frame):
        if 'value' in prop:return prop['value']
        return next((s['value'] for s in reversed(prop['samples']) if s['frame']<=frame),prop['samples'][0]['value'])
    timeline=next(t for t in scene_timing(data) if t['name']=='Leap_view')
    slots=[]
    for slot in range(7):
        hit=by_name[f'hitbox.leap_view_panel_{slot:02d}']['properties']
        panel=by_name[f'comp.leap_view_panel_{slot:02d}']['properties']
        tracks=[]
        for selected in range(7):
            marker=next(m for m in timeline['markers'] if m['name']==f'Main_{selected:02d}_IN')
            # The adjacent OUT marker starts at the settled overview pose.
            frames=range(marker['start'],marker['start']+marker['frame_count']+1)
            tracks.append({field:[at(panel[field],f) for f in frames] for field in ['Position','Rotation']})
        slots.append({'center':at(hit['Position'],0)[:2],'size':[154,230], 'tracks':tracks})
    bindings={name_hash(n):('sense_leap_frame' if 'frame' in n else 'sense_leap_panel') for n in ['leap_view_frame.9.png','leap_view_panel.png']}
    output=Path(output);output.mkdir(parents=True,exist_ok=True)
    found=[]
    for record in walk(parse(shared)):
        if record.tag!=name_hash('Type') or record.kind!=1:continue
        props=properties(record)
        if name_hash('ID') not in props:continue
        resource=bindings.get(integer(props[name_hash('ID')]))
        if resource is None or name_hash('Pixels') not in props:continue
        width,height,fmt=(integer(props[name_hash(k)]) for k in ('Width','Height','Format'))
        if fmt!=14:raise ValueError('Unsupported Leap texture')
        pw,ph,offset,size=struct.unpack('<4I',props[name_hash('Levels')].payload)
        if offset or not(0<width<=pw<=4096 and 0<height<=ph<=4096):raise ValueError('Invalid Leap texture')
        expanded=next(integer(c) for c in record.children if c.tag==name_hash('DataDecompressedSize'))
        raw=fastlz(props[name_hash('Pixels')].payload,expanded)
        if len(raw)!=size*2:raise ValueError('Invalid Leap planes')
        color,alpha=etc1(raw[:size],pw,ph),etc1(raw[size:],pw,ph)
        rgba=bytearray()
        for y in range(height):
            for x in range(width):
                i=(y*pw+x)*3;rgba.extend((*color[i:i+3],alpha[i]))
        png(output/(resource+'.png'),width,height,rgba,alpha=True);found.append(resource)
    if len(found)!=2:raise ValueError('Missing Leap panel/frame textures')
    rearrange_nodes=geometry(parse(rearrange))
    drag=next(n for n in rearrange_nodes if n['name']=='drag.leap_view_panel_00')['properties']['Position']
    move=[at(drag,f)[0] for f in range(11)]
    move=[(v-move[0])/(move[-1]-move[0]) for v in move]
    popup=next(n for n in rearrange_nodes if n['name']=='timeline.leap_view_panel_00')['properties']
    drag_scale=[at(popup['Scale'],f)[0] for f in range(6)]
    drag_opacity=[at(popup['Opacity'],f)[0] for f in range(6)]
    return {'reorderCurve':move,'reorderDurationMs':334,'dragScale':drag_scale,'dragOpacity':drag_opacity,
            'source' :'Rosie.apk!/assets/Port/scenes/Rosie_leap.m10','sha256':hashlib.sha256(data).hexdigest(),
            'rearrangeSha256':hashlib.sha256(rearrange).hexdigest(),'width':540,'height':960,
            'fps':timeline['fields']['FramesPerSecond']['value'],'durationMs':round(15*1000/timeline['fields']['FramesPerSecond']['value']),
            'slots':slots,'rearrangeMarkers':next(t['markers'] for t in scene_timing(rearrange) if t['name']=='timeline.homepage')}
