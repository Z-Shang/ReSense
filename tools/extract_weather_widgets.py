#!/usr/bin/env python3
"""Recover Rosie's original standalone Weather widgets, previews and Mode10 scenes."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import zipfile


def extract(rom,output):
    output=Path(output);output.mkdir(parents=True,exist_ok=True);entries=[]
    source='system/app/HtcWeather3DWidget.apk'
    with zipfile.ZipFile(rom) as archive:data=archive.read(source)
    apk_path=output/'HtcWeather3DWidget.apk';apk_path.write_bytes(data)
    with zipfile.ZipFile(io.BytesIO(data)) as apk:
        for name in apk.namelist():
            if not (name.startswith('assets/') or name.startswith('res/')):continue
            if name.endswith('/'):continue
            raw=apk.read(name);target=output/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
            entries.append({'file':name,'source':source+'!/'+name,'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()})
    aapt=Path('.toolchain/android-sdk/build-tools/35.0.0/aapt')
    if aapt.is_file():
        for name,args in [('provider.txt',['xmltree',str(apk_path),'res/xml/weather_widget.xml']),('manifest.txt',['xmltree',str(apk_path),'AndroidManifest.xml']),('resources.txt',['resources',str(apk_path)])]:
            result=subprocess.run([str(aapt),'dump',*args],check=True,capture_output=True,text=True);(output/name).write_text(result.stdout)
    from decode_navbar import geometry
    from mode10 import parse
    from inspect_weather_playback import scene_timing
    for size in ['2x1','2x2','4x4']:
        path=output/'assets/Port'/f'Weather_{size}.m10';raw=path.read_bytes()
        report={'source':source+'!/'+str(path.relative_to(output)),'sha256':hashlib.sha256(raw).hexdigest(),'nodes':geometry(parse(raw)),'timing':scene_timing(raw)}
        (output/f'layout-{size}.json').write_text(json.dumps(report,indent=2)+'\n')
    (output/'provenance.json').write_text(json.dumps({'rom':str(rom),'apkSource':source,'apkSha256':hashlib.sha256(data).hexdigest(),'assets':entries},indent=2)+'\n')
    print(f'Recovered original APK and {len(entries)} assets into {output}')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--rom',default='res/3.33.401.153.zip');parser.add_argument('--output',default='references/3.33.401.153/weather-widgets');args=parser.parse_args();extract(args.rom,args.output)
