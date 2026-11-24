"""Recover the stock HTC music widget variants artwork and source geometry."""
import hashlib,io,struct,zipfile
from pathlib import Path
from mode10 import *
from decode_navbar import geometry

TARGETS={
 'music_icon_next.png':'sense_music_next','music_icon_prev.png':'sense_music_previous',
 'music_icon_play_rest.png':'sense_music_play','music_icon_pause_rest.png':'sense_music_pause',
 'music_btn_rest.png':'sense_music_controls','music_4x2_album.png':'sense_music_album_frame',
 'music_4x2_album_revert.png':'sense_music_reflection','music_album_shadow.png':'sense_music_shadow',
 'common_panel.9.png':'sense_music_panel',
 'music_album.png':'sense_music_large_frame','music_album_revert.png':'sense_music_large_reflection',
 'music_album_bk.png':'sense_music_large_panel','music_icon_shuffle_on.png':'sense_music_shuffle',
 'music_icon_repeat_on.png':'sense_music_repeat','common_divider_double.9.png':'sense_music_divider'}

def extract(rom_path,output):
    output=Path(output);output.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(rom_path) as rom:
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/com.htc.MusicWidget.apk'))) as apk:
            scenes={style:apk.read('assets/Port/Music_'+style+'.m10') for style in ['4x1','4x2','4x4']}
            data=scenes['4x2']
            for name,resource in [('music_4x2_nocover.png','sense_music_no_cover.png'),('preview_music_4x2.png','sense_music_preview.png'),('preview_music_4x1.png','sense_music_preview_small.png'),('preview_music_4x4.png','sense_music_preview_large.png'),('albumart_mp_unknown.png','sense_music_large_no_cover.png')]:
                (output/resource).write_bytes(apk.read('res/drawable-hdpi/'+name))
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/fusion.apk'))) as apk:shared=apk.read('assets/Default/drawable/shared.m10')
    layouts={style:geometry(parse(scene)) for style,scene in scenes.items()}
    nodes=layouts['4x2'];wanted={}
    for layout in layouts.values():
        for n in layout:
            if n['name'] in TARGETS:wanted[n['properties']['DiffuseMap']['value_u32']]=(n['name'],TARGETS[n['name']])
    found={}
    for source in [*scenes.values(),shared]:
        for record in walk(parse(source)):
            if record.tag!=name_hash('Type') or record.kind!=1:continue
            try:props=properties(record)
            except ValueError:continue
            if name_hash('ID') not in props or name_hash('Pixels') not in props:continue
            material=integer(props[name_hash('ID')])
            if material not in wanted or material in found:continue
            original,resource=wanted[material]
            width,height,fmt=(integer(props[name_hash(k)]) for k in ('Width','Height','Format'))
            if fmt!=14:raise ValueError(f'Unsupported music texture {original}: {fmt}')
            pw,ph,offset,size=struct.unpack('<4I',props[name_hash('Levels')].payload)
            if offset or not(0<width<=pw<=4096 and 0<height<=ph<=4096):raise ValueError('Invalid music texture')
            expanded=next(integer(c) for c in record.children if c.tag==name_hash('DataDecompressedSize'))
            raw=fastlz(props[name_hash('Pixels')].payload,expanded)
            if len(raw)!=size*2:raise ValueError('Invalid music planes')
            color,alpha=etc1(raw[:size],pw,ph),etc1(raw[size:],pw,ph);rgba=bytearray()
            for y in range(height):
                for x in range(width):
                    i=(y*pw+x)*3;rgba.extend((*color[i:i+3],alpha[i]))
            png(output/(resource+'.png'),width,height,rgba,alpha=True)
            found[material]={'original':original,'resource':resource,'width':width,'height':height}
    if len(found)!=len(TARGETS):raise ValueError('Missing music textures: '+str(set(TARGETS)-{v['original'] for v in found.values()}))
    return {'source':'com.htc.MusicWidget.apk!/assets/Port/Music_4x2.m10','sha256':hashlib.sha256(data).hexdigest(),
            'dimensions':[530,344],'textures':list(found.values()),
            'variants':{style:{'dimensions':next(n['properties']['Dimensions']['value'] for n in layout if n['name']=='_Music'),'sha256':hashlib.sha256(scenes[style]).hexdigest()} for style,layout in layouts.items()},
            'geometry':[{k:n[k] for k in ['name','parent_offset','offset','properties']} for n in nodes]}
