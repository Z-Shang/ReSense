#!/usr/bin/env python3
"""Recover normalized dock/header keyframes from the supplied Rosie scene."""
import hashlib
import io
import zipfile
from decode_navbar import geometry
from inspect_weather_playback import scene_timing
from mode10 import parse


def extract(rom_path):
    path='assets/Port/scenes/Rosie_navbar.m10'
    with zipfile.ZipFile(rom_path) as rom:
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/Rosie.apk'))) as apk:data=apk.read(path)
    nodes=geometry(parse(data))
    root=next(t for t in scene_timing(data) if t['name']=='__Rosie_NavBar')
    markers={m['name']:m for m in root['markers']}
    def values(name,field,start,count,axis):
        prop=next(n for n in nodes if n['name']==name)['properties'][field]
        if 'value' in prop:return [prop['value'][axis]]*count
        samples=prop['samples'];result=[]
        for frame in range(start,start+count):
            previous=next((s for s in reversed(samples) if s['frame']<=frame),samples[0])
            result.append(previous['value'][axis])
        return result
    def normalize(samples):
        extent=samples[-1]-samples[0]
        if abs(extent)<.00001:raise ValueError('No animation extent')
        return [round((value-samples[0])/extent,7) for value in samples]
    motions={}
    for name in ['slide_off','slide_on','edit_header_on','edit_header_off','remove_header_on','remove_header_off']:
        marker=markers[name];start=marker['start'];count=marker['frame_count']
        if count!=11:raise ValueError('Unexpected dock animation length')
        if name.startswith('slide_'):
            body=values('timeline.NavBar_phone_btn','Position',start,count,1)
            rail=values('timeline.NavBar_scroller','Position',start,count,1)
            channels={'body':normalize(body),'rail':normalize(rail)}
            extents={'body':body[-1]-body[0],'rail':rail[-1]-rail[0]}
        else:
            node='edit_header' if name.startswith('edit_') else 'remove_header'
            position=values(node,'Position',start,count,1);opacity=values(node,'Opacity',start,count,0)
            channels={'position':normalize(position),'opacity':normalize(opacity)}
            extents={'position':position[-1]-position[0],'opacity':opacity[-1]-opacity[0]}
        motions[name]={'startFrame':start,'frameCount':count,'durationMs':round((count-1)*1000/root['fields']['FramesPerSecond']['value']), 'channels':channels,'sourceExtents':extents}
    return {'source':'Rosie.apk!/'+path,'sha256':hashlib.sha256(data).hexdigest(),
            'fps':root['fields']['FramesPerSecond']['value'],'sourceWidth':540,
            'note':'Interpolate between recovered samples at display refresh. Zero-length rearrange markers are state selections, not tweens.', 'motions':motions}
