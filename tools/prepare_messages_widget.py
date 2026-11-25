#!/usr/bin/env python3
"""Recover the three stock Messages widgets and their Mode10 texture dependencies."""
import io,json,zipfile,hashlib
from pathlib import Path
from mode10 import parse,integer,name_hash
from decode_navbar import geometry
from extract_weather_catalog import texture_records,decode_texture

PACKAGE='system/app/com.htc.htcmsgwidgets3d.apk'
ALIASES={'header':1615986153,'panel':1908676829,'footer':542553615,'buttons':1144128466,
         'list':929405028,'add':2135223121,'message_icon':1664860969,'card':1096924214,
         'reply':1875445672,'badge':186423896}

def prepare(rom,output):
    output=Path(output);images=output/'drawable-nodpi';raw=output/'raw';values=output/'values'
    for path in (images,raw,values):path.mkdir(parents=True,exist_ok=True)
    needed=set();records={};scenes=[];files=[]
    with zipfile.ZipFile(rom) as source:
        with zipfile.ZipFile(io.BytesIO(source.read(PACKAGE))) as apk:
            for entry in sorted(n for n in apk.namelist() if n.endswith('.m10')):
                data=apk.read(entry);root=parse(data);layout=[]
                for node in geometry(root):
                    p=node['properties'];item={'name':node['name']}
                    for key in ('Position','Size','DiffuseMap'):
                        if key in p:item[key]=p[key].get('value',p[key].get('value_u32'))
                    if 'DiffuseMap' in p:needed.add(p['DiffuseMap']['value_u32'])
                    layout.append(item)
                resource='msg_widget_'+Path(entry).stem.lower()+'.m10';(raw/resource).write_bytes(data)
                scenes.append({'source':PACKAGE+'!/'+entry,'resource':resource,'sha256':hashlib.sha256(data).hexdigest(),'nodes':layout})
                for record,props in texture_records(root):records.setdefault(integer(props[name_hash('ID')]),(record,props,PACKAGE+'!/'+entry))
            for style in ('card','list','summary'):
                entry='res/drawable-normal-hdpi/preview_messages_'+style+'.png';data=apk.read(entry)
                resource='msg_widget_preview_'+style+'.png';(images/resource).write_bytes(data)
                files.append({'source':PACKAGE+'!/'+entry,'resource':resource,'sha256':hashlib.sha256(data).hexdigest()})
            for name in ('msg_default_photo','icon_indicator_mms','icon_indicator_sms'):
                entry='res/drawable/'+name+'.png';data=apk.read(entry);resource='msg_widget_'+name+'.png';(images/resource).write_bytes(data)
                files.append({'source':PACKAGE+'!/'+entry,'resource':resource,'sha256':hashlib.sha256(data).hexdigest()})
        with zipfile.ZipFile(io.BytesIO(source.read('system/app/fusion.apk'))) as fusion:
            entry='assets/Default/drawable/shared.m10'
            for record,props in texture_records(parse(fusion.read(entry))):records.setdefault(integer(props[name_hash('ID')]),(record,props,'system/app/fusion.apk!/'+entry))
    for ident in sorted(needed):
        if not ident:continue
        if ident not in records:raise ValueError('Missing widget texture '+hex(ident))
        record,props,source=records[ident];resource=f'msg_widget_tex_{ident:08x}.png'
        info=decode_texture(record,props,images/resource,flip_y=True);files.append({'source':source,'textureId':ident,'flippedY':True,'resource':resource,**info})
    (values/'messages_widget_assets.xml').write_text('<resources>\n'+''.join(f'    <item type="drawable" name="msg_widget_{name}">@drawable/msg_widget_tex_{ident:08x}</item>\n' for name,ident in ALIASES.items())+'</resources>\n')
    (raw/'msg_widget_provenance.json').write_text(json.dumps({'styles':[{'name':s,'span':[4,4]} for s in ('card','list','summary')],'scenes':scenes,'files':files},indent=2)+'\n')
    print(f'Recovered {len(scenes)} Messages scenes and {len(files)} widget images')

if __name__=='__main__':
    import argparse
    p=argparse.ArgumentParser();p.add_argument('--rom',required=True);p.add_argument('--output',required=True);a=p.parse_args();prepare(a.rom,a.output)
