#!/usr/bin/env python3
"""Read-only ROM/current-renderer audit. Writes evidence, never changes app assets or rendering."""
import argparse
import bisect
import hashlib
import io
import json
import math
from pathlib import Path
import re
import struct
import zipfile
from mode10 import parse, walk, properties, name_hash, integer, text


def sample(track, frame, quaternion=False):
    if 'v' in track:
        return track['v'][:]
    rows=track['s'];right=bisect.bisect_left([r[0] for r in rows],frame)
    b=min(len(rows)-1,right);a=max(0,b-1)
    t=0 if rows[b][0]==rows[a][0] else max(0,min(1,(frame-rows[a][0])/(rows[b][0]-rows[a][0])))
    first,second=rows[a][1:],rows[b][1:]
    sign=-1 if quaternion and sum(x*y for x,y in zip(first,second))<0 else 1
    value=[x+(sign*y-x)*t for x,y in zip(first,second)]
    if quaternion:
        length=math.sqrt(sum(x*x for x in value));value=[x/length for x in value] if length>=1e-5 else [0,0,0,1]
    return value


def multiply(a,b):
    return [[sum(a[y][k]*b[k][x] for k in range(4)) for x in range(4)] for y in range(4)]


def evaluate(scene,frame):
    nodes=scene['nodes'];times={};chains={};worlds={};alphas={}
    def time(i):
        if i not in times:
            n=nodes[i];parent=n['parent']
            times[i]=(frame if parent<0 else time(parent)+nodes[parent].get('childStart',0))-max(0,n['inFrame'])
        return times[i]
    def chain(i):
        if i in chains:return chains[i]
        n=nodes[i];t=time(i);tracks=n['tracks']
        p=sample(tracks['Position'],t);x,y,z,w=sample(tracks['Rotation'],t,True);s=sample(tracks['Scale'],t);pivot=sample(tracks['PivotPosition'],t)
        m=[[1-2*y*y-2*z*z,2*x*y-2*z*w,2*x*z+2*y*w,0],
           [2*x*y+2*z*w,1-2*x*x-2*z*z,2*y*z-2*x*w,0],
           [2*x*z-2*y*w,2*y*z+2*x*w,1-2*x*x-2*y*y,0],[0,0,0,1]]
        for row in range(3):
            for col in range(3):m[row][col]*=s[col]
            m[row][3]=p[row]+sum(m[row][col]*pivot[col] for col in range(3))
        parent=n['transformParent'];chains[i]=m if parent<0 else multiply(chain(parent),m)
        return chains[i]
    def world(i):
        if i in worlds:return worlds[i]
        n=nodes[i];t=time(i);parent=n['parent'];m=chain(i)
        alpha=sample(n['tracks']['Opacity'],t)[0] if n['visible'] and t>=0 and (n['duration']<0 or t<n['duration']) else 0
        if parent>=0:m=multiply(world(parent),m);alpha*=alphas[parent]
        worlds[i]=m;alphas[i]=alpha;return m
    for i in range(len(nodes)):world(i)
    return worlds,alphas


def projected(scene,index,frame,width,height,mesh=None):
    matrices,alphas=evaluate(scene,frame);node=scene['nodes'][index];m=matrices[index]
    fit=min(width/540,height/960);points=[]
    for vertex in node['vertices'] if mesh is None else mesh:
        x,y,z=vertex[:3];v=[x,y,z,1];world=[sum(m[row][k]*v[k] for k in range(4)) for row in range(3)]
        perspective=1000/max(100,1000-world[2])
        points.append([width/2+(world[0]-270)*fit*perspective,height/2+(world[1]-480)*fit*perspective])
    return {'alpha':alphas[index],'bounds':[min(p[0] for p in points),min(p[1] for p in points),max(p[0] for p in points),max(p[1] for p in points)]}


def labels(data,native_names):
    document=parse(data);result=[];fonts={}
    for record in walk(document):
        if record.kind!=1 or record.tag!=name_hash('Type'):continue
        # Font descriptors have inherited/name-only properties, unlike scene nodes.
        p={integer(c.children[0]):c.children[1] for c in record.children if c.kind==1 and c.tag==name_hash('Property') and len(c.children)==2}
        identity=p.get(name_hash('ID'));height=p.get(name_hash('Height'))
        if identity is not None and height is not None:
            try:fonts[text(identity)]=integer(height)
            except (ValueError,UnicodeError):pass
    for record in walk(document):
        if record.kind!=1 or record.tag!=name_hash('Node'):continue
        p=properties(record)
        if name_hash('FontID')not in p:continue
        font=text(p[name_hash('FontID')]);nominal=re.search(r'(\d+)A$',font)
        item={'offset':record.offset,'name':text(p[name_hash('Name')]),'font_id':font,
              'font_height':fonts.get(font,int(nominal.group(1)) if nominal else None),
              'line_height':struct.unpack('<f',p[name_hash('LineHeight')].payload)[0],
              'horizontal_code':integer(p[name_hash('HorzAlignment')]),'vertical_code':integer(p[name_hash('VertAlignment')]),
              'size':list(struct.unpack('<2f',p[name_hash('Size')].payload)),
              'shadow':bool(bytes(p[name_hash('Shadow')].payload)[0]) if name_hash('Shadow')in p else None,
              'line_height_mode':integer(p[name_hash('LineHeightMode')]) if name_hash('LineHeightMode')in p else None,
              'max_line_count':integer(p[name_hash('MaxLineCount')]) if name_hash('MaxLineCount')in p else None}
        result.append(item)
    evidence={name:hex(name_hash(name)) for name in ['FontID','LineHeight','HorzAlignment','VertAlignment','Shadow','ShadowDirection','ShadowOpacity','LineHeightMode'] if name in native_names}
    return {'sha256':hashlib.sha256(data).hexdigest(),'property_names_present_in_native_library':evidence,'labels':result}


def audit(rom,generated):
    with zipfile.ZipFile(rom)as archive:
        native=archive.read('system/lib/libmode10.so');native_names={s.decode() for s in re.findall(rb'[A-Za-z][A-Za-z0-9_]{2,60}',native)}
        with zipfile.ZipFile(io.BytesIO(archive.read('system/app/HtcWeather3DWidget.apk')))as apk:
            typography={style:labels(apk.read('assets/Port/Weather_'+style+'.m10'),native_names)for style in ['2x1','2x2','4x4']}
        with zipfile.ZipFile(io.BytesIO(archive.read('system/app/HtcClock3DWidget.apk')))as apk:
            typography['clock_weather']=labels(apk.read('assets/Port/clock_weather.m10'),native_names)
    scene=json.loads((generated/'raw/htc_overlay_scene_03.json').read_text())
    wiper=next(i for i,n in enumerate(scene['nodes'])if n['name']=='b_rain_wiper.png')
    streaks=next(i for i,n in enumerate(scene['nodes'])if n['name']=='b_raindrop_streaks.png')
    framing=[]
    for width,height in [(540,960),(1080,2400),(1440,3200)]:
        scale=min(width/540,height/960);native_height=960*scale
        cases=[]
        for frame in [45,55,60,70,75,90,100]:
            water=projected(scene,streaks,frame,width,height)
            mesh=scene['nodes'][streaks]['vertices'];fade=[[0,320*.85,0],[244,320*.85,0]]
            cases.append({'frame':frame,'wiper':projected(scene,wiper,frame,width,height),'streaks':water,
                          'fade_start_y':projected(scene,streaks,frame,width,height,fade)['bounds'][1]})
        framing.append({'viewport':[width,height],'fitted_scene':[0,(height-native_height)/2,width,(height+native_height)/2],
                        'cases':cases,'maximum_wiper_bottom':max(projected(scene,wiper,f,width,height)['bounds'][3] for f in range(45,105))})
    fonts={}
    with zipfile.ZipFile(rom)as archive:
        for name in ['DroidSans.ttf','HelveticaNeueLTPro35Th.ttf']:
            data=archive.read('system/fonts/'+name);current=(generated/'raw'/('sense_widget_'+name.lower())).read_bytes()
            fonts[name]={'rom_sha256':hashlib.sha256(data).hexdigest(),'packaged_sha256':hashlib.sha256(current).hexdigest(),'byte_exact':data==current}
    return {'method':'Static reproduction of current renderer math; not an executed original HTC runtime or a pixel coverage measurement.',
            'production_code_changed':False,'font_files':fonts,'framing':framing,'typography':typography}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--rom',type=Path,default=Path('res/3.33.401.153.zip'));parser.add_argument('--generated',type=Path,default=Path('launcher/build/generated/senseDock/res'));parser.add_argument('--output',type=Path,default=Path('references/3.33.401.153/weather-framing-typography-audit.json'));args=parser.parse_args()
    report=audit(args.rom,args.generated);args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(report,indent=2)+'\n')
    for row in report['framing']:
        case=next(c for c in row['cases']if c['frame']==75)
        print(row['viewport'],'fitted',row['fitted_scene'],'water bottom',round(case['streaks']['bounds'][3],1),'fade starts',round(case['fade_start_y'],1),'maximum wiper bottom',round(row['maximum_wiper_bottom'],1))
    print(args.output)
