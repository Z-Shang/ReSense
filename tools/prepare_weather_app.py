#!/usr/bin/env python3
"""Recover Weather.apk artwork and clock weather scenes for the native weather app."""
import argparse,hashlib,io,json,zipfile
from pathlib import Path
from extract_weather_catalog import extract

def prepare(rom,output):
    output=Path(output);report=[]
    for stale in output.glob("drawable-hdpi/wx_htc_*.png"): stale.unlink()
    for stale in output.glob("drawable-*/wx_common_tab_button_*.png"): stale.unlink()
    extract(rom,output)
    with zipfile.ZipFile(rom) as source,zipfile.ZipFile(io.BytesIO(source.read('system/app/Weather.apk'))) as apk:
        for name in apk.namelist():
            if not name.startswith(('res/drawable/','res/drawable-hdpi/')) or not name.endswith('.png'):continue
            if name.rsplit('/',1)[-1].startswith('common_tab_button_'):continue
            if 'accuweather' in name:continue  # Data attribution belongs to the actual provider.
            directory,filename=name.removeprefix('res/').split('/')
            directory='drawable-mdpi' if directory=='drawable' else directory
            # APK nine-patches already contain npTc metadata: retain compiled chunks.
            filename='wx_'+filename.replace('.9.png','.png')
            data=apk.read(name);target=output/directory/filename;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
            report.append(dict(source='Weather.apk!/'+name,resource=directory+'/'+filename,sha256=hashlib.sha256(data).hexdigest()))
    with zipfile.ZipFile(rom) as source,zipfile.ZipFile(io.BytesIO(source.read('system/framework/com.htc.resources.apk'))) as apk:
        for stale in output.glob('drawable-hdpi/wx_tab_*.png'):stale.unlink()
        for name in apk.namelist():
            if name.startswith('res/drawable-hdpi/common_m_weather_') and name.endswith('.png'):filename='wx_condition_'+name.rsplit('_',1)[-1]
            else:continue
            data=apk.read(name)
            target=output/'drawable-hdpi'/filename;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
            report.append(dict(source='com.htc.resources.apk!/'+name,resource='drawable-hdpi/'+filename,sha256=hashlib.sha256(data).hexdigest()))
    with zipfile.ZipFile(rom) as source:
        for name in sorted(source.namelist()):
            if name.startswith('system/media/weather/') and name.endswith('.mp4'):
                data=source.read(name);filename='wx_video_'+Path(name).name.removeprefix('weather_')
                target=output/'raw'/filename;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
                report.append(dict(source=name,resource='raw/'+filename,sha256=hashlib.sha256(data).hexdigest()))
        with zipfile.ZipFile(io.BytesIO(source.read('system/app/WeatherVideo.apk'))) as apk:
            for name in apk.namelist():
                if name.startswith('res/drawable/weather_') and name.endswith('.jpg'):
                    filename='wx_video_'+Path(name).name.removeprefix('weather_')
                    target=output/'drawable-nodpi'/filename;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(apk.read(name))
                    report.append(dict(source='WeatherVideo.apk!/'+name,resource='drawable-nodpi/'+filename,sha256=hashlib.sha256(apk.read(name)).hexdigest()))
    (output/'raw/weather_app_provenance.json').write_text(json.dumps(report,indent=2)+'\n')
    print(f'Recovered {len(report)} Weather.apk images and all original condition scenes')
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--rom',required=True);p.add_argument('--output',required=True);args=p.parse_args();prepare(args.rom,args.output)
