#!/usr/bin/env python3
"""Combine emulator coverage measurements with the source thunder-flash geometry."""
import argparse
import json
from pathlib import Path

from audit_weather_framing_typography import evaluate, projected


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--renders',type=Path,default=Path('.toolchain/weather-coverage-audit/files/weather-coverage-audit.json'))
    parser.add_argument('--scenes',type=Path,default=Path('assets/htc/launcher/res/raw'))
    parser.add_argument('--output',type=Path,default=Path('references/3.33.401.153/other-weather-coverage-audit.json'))
    args=parser.parse_args()
    renders=json.loads(args.renders.read_text())
    if len(renders['cases'])!=18 or {r['effect'] for r in renders['cases']}!={1,2,4,5,6,7}:
        raise ValueError('Expected six effects at three viewport sizes')
    scene=json.loads((args.scenes/'htc_overlay_scene_06.json').read_text())
    index=next(i for i,n in enumerate(scene['nodes']) if n['name']=='White Solid 1')
    alpha,frame=max((evaluate(scene,f)[1][index],f) for f in range(150))
    flash={'node':'White Solid 1','peak_integer_frame':frame,'peak_opacity':alpha,
           'stock':projected(scene,index,frame,540,960),
           'tall':projected(scene,index,frame,1080,2400),
           'landscape':projected(scene,index,frame,2400,1080)}
    report={'method':'Actual Android Canvas samples plus reconstructed current-renderer mesh bounds',
            'rendered_pose_count':sum(r['sampleCount'] for r in renders['cases']),
            'measurements':renders,'flash_geometry':flash,
            'limits':'Sparse frame/pixel samples, not a complete motion or original-runtime fidelity test. Peak fractions do not imply full spatial coverage.'}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,indent=2)+'\n')
    print(args.output)


if __name__=='__main__':main()
