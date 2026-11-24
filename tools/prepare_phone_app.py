#!/usr/bin/env python3
"""Extract original HTC phone artwork without altering compiled nine-patches."""
import argparse, hashlib, io, json, zipfile
from pathlib import Path

def prepare(rom, output):
    output = Path(output)
    report = []
    for name in ["people_people_home_defaultphoto.png","people_search_people.png"]: (output/"drawable-hdpi"/name).unlink(missing_ok=True)
    for stale in output.glob("drawable-hdpi/htc_*.png"): stale.unlink()
    with zipfile.ZipFile(rom) as source:
        for package, prefix in [('HtcDialer', 'dial'), ('Phone', 'call')]:
            with zipfile.ZipFile(io.BytesIO(source.read(f'system/app/{package}.apk'))) as apk:
                for name in apk.namelist():
                    if not name.startswith('res/drawable-hdpi/') or not name.endswith('.png'):
                        continue
                    filename = prefix + '_' + name.rsplit('/', 1)[1].replace('.9.png', '.png')
                    data = apk.read(name)
                    target = output / 'drawable-hdpi' / filename
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(data)
                    report.append(dict(source=package + '.apk!/' + name, resource=filename, sha256=hashlib.sha256(data).hexdigest()))
    target = output / 'raw/phone_provenance.json'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, indent=2) + '\n')
    print(f'Recovered {len(report)} original dialer and phone images')

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--rom', required=True)
    p.add_argument('--output', required=True)
    args = p.parse_args()
    prepare(args.rom, args.output)
