#!/usr/bin/env python3
"""Recover Sense ring-lock artwork and scene provenance from the local Pyramid ROM."""
import argparse,hashlib,io,json,struct,zipfile
from pathlib import Path
from mode10 import parse,walk,properties,name_hash,integer,fastlz,etc1,png
from decode_navbar import geometry
from inspect_weather_playback import scene_timing

NAMES=['lockscreen_navbar.png','lockscreen_ring.png','lockscreen_ring_high.png','lockscreen_ring_cover.png','lockscreen_ring_wave.png','lockscreen_shortcutpanel_b.png','lockscreen_time_shadow_qhd.png']
def prepare(rom,output):
    out=Path(output);draw=out/'drawable-nodpi';draw.mkdir(parents=True,exist_ok=True);raw=out/'raw';raw.mkdir(exist_ok=True);font=out/'font';font.mkdir(exist_ok=True)
    with zipfile.ZipFile(rom)as source:
        with zipfile.ZipFile(io.BytesIO(source.read('system/app/fusion.apk')))as apk:shared=apk.read('assets/Default/drawable/shared.m10')
        with zipfile.ZipFile(io.BytesIO(source.read('system/app/Idlescreen_Base.apk')))as apk:
            scenes={name:apk.read('assets/Port/base/'+name+'.m10')for name in ['Lock_ring_motion','Lockscreen_shortcut','Lockscreen_time','Lockscreen_container']}
        wallpaper=source.read('system/customize/resource/htc_wallpaper_01.jpg');(draw/'ls_wallpaper.jpg').write_bytes(wallpaper)
        face=source.read('system/fonts/HelveticaNeueLTPro35Th.ttf');(font/'ls_clock.ttf').write_bytes(face)
        copied=[]
        for package,path,target in [('HtcDialer','res/drawable-hdpi/dialer.png','ls_phone.png'),('Mms','res/drawable-hdpi/messages.png','ls_messages.png'),('HTCCamera','res/drawable-hdpi/ic_launcher_camera.png','ls_camera.png'),('Browser','res/mipmap-hdpi/ic_launcher_browser.png','ls_browser.png')]:
            try:
                with zipfile.ZipFile(io.BytesIO(source.read('system/app/'+package+'.apk')))as apk:
                    if path not in apk.namelist():
                        choices=[n for n in apk.namelist()if n.startswith('res/drawable-hdpi/')and n.endswith('.png')and any(k in n.rsplit('/',1)[-1]for k in ['icon_launcher','ic_launcher','icon_browser','icon_camera'])]
                        if len(choices)!=1:continue
                        path=choices[0]
                    data=apk.read(path);(draw/target).write_bytes(data);copied.append(dict(file=target,source=package+'.apk!/'+path,sha256=hashlib.sha256(data).hexdigest()))
            except KeyError:continue
    ids={name_hash(n):n for n in NAMES};assets=[]
    for record in walk(parse(shared)):
        if record.tag!=name_hash('Type')or record.kind!=1:continue
        p=properties(record)
        if name_hash('ID')not in p or integer(p[name_hash('ID')])not in ids:continue
        name=ids[integer(p[name_hash('ID')])];w,h,fmt=(integer(p[name_hash(k)])for k in ['Width','Height','Format'])
        if fmt!=14:raise ValueError('Unsupported lock-screen texture')
        pw,ph,offset,size=struct.unpack('<4I',p[name_hash('Levels')].payload)
        if offset or not(0<w<=pw<=4096 and 0<h<=ph<=4096):raise ValueError('Invalid texture extent')
        expanded=next(integer(c)for c in record.children if c.tag==name_hash('DataDecompressedSize'));data=fastlz(p[name_hash('Pixels')].payload,expanded)
        if len(data)!=size*2:raise ValueError('Invalid color/alpha planes')
        color,alpha=etc1(data[:size],pw,ph),etc1(data[size:],pw,ph);rgba=bytearray()
        for y in range(h):
            for x in range(w):
                i=(y*pw+x)*3;rgba.extend((*color[i:i+3],alpha[i]))
        target='ls_'+name.removeprefix('lockscreen_');png(draw/target,w,h,rgba,alpha=True);assets.append(dict(file=target,source='fusion.apk!/assets/Default/drawable/shared.m10',material=name_hash(name),size=[w,h]))
    if len(assets)!=len(NAMES):raise ValueError('Missing lock-screen texture')
    timing=scene_timing(scenes['Lock_ring_motion']);ring=next(t for t in timing if t['name']=='Lock_Ring_motion');fps=ring['fields']['FramesPerSecond']['value']
    report=dict(width=540,height=960,fps=fps,assets=assets+copied,sharedSha256=hashlib.sha256(shared).hexdigest(),wallpaperSha256=hashlib.sha256(wallpaper).hexdigest(),fontSha256=hashlib.sha256(face).hexdigest(),sources={n:hashlib.sha256(d).hexdigest()for n,d in scenes.items()},timelines=timing,
        geometry={name:geometry(parse(data))for name,data in scenes.items()})
    (raw/'ls_recovery.json').write_text(json.dumps(report,indent=2)+'\n');print(f'Recovered {len(assets)} Mode10 lock-screen textures and {len(copied)} shortcut icons')
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--rom',required=True);p.add_argument('--output',required=True);a=p.parse_args();prepare(a.rom,a.output)
