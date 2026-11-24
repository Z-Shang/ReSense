#!/usr/bin/env python3
"""Recover HTC People artwork, retaining compiled nine-patch metadata."""
import argparse,hashlib,io,json,zipfile
from pathlib import Path

def prepare(rom,output):
    output=Path(output);report=[]
    for name in ["people_people_home_defaultphoto.png","people_search_people.png"]: (output/"drawable-hdpi"/name).unlink(missing_ok=True)
    for stale in output.glob("drawable-hdpi/htc_*.png"): stale.unlink()
    with zipfile.ZipFile(rom) as source:
        for package,prefix in [('system/app/HtcContacts.apk','people')]:
            with zipfile.ZipFile(io.BytesIO(source.read(package))) as apk:
                for name in apk.namelist():
                    if not name.startswith('res/drawable-hdpi/') or not name.endswith('.png'):continue
                    filename=name.rsplit('/',1)[1]
                    if filename in {'people_home_defaultphoto.png','search_people.png'}:continue
                    filename=prefix+'_'+filename.replace('.9.png','.png');data=apk.read(name)
                    target=output/'drawable-hdpi'/filename;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
                    report.append(dict(source=package+'!/'+name,resource=filename,sha256=hashlib.sha256(data).hexdigest()))
    target=output/'raw/people_provenance.json';target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(report,indent=2)+'\n')
    print(f'Recovered {len(report)} HTC People and framework images')
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--rom',required=True);parser.add_argument('--output',required=True);args=parser.parse_args();prepare(args.rom,args.output)
