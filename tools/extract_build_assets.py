#!/usr/bin/env python3
"""Explicitly regenerate build-ready resources from the supplied Pyramid ROM."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

from htc_assets import inventory, sha256, verify

PREPARERS={
    'launcher':'prepare_dock_assets.py',
    'sense-framework':'prepare_sense_framework.py',
    'weather':'prepare_weather_app.py',
    'phone':'prepare_phone_app.py',
    'contacts':'prepare_contacts_app.py',
    'messages':'prepare_messages_app.py',
    'lockscreen':'prepare_lockscreen.py',
}


def extract(rom,output,replace=False):
    rom=Path(rom).resolve();output=Path(output).resolve()
    if not rom.is_file():raise FileNotFoundError('ROM not found: '+str(rom))
    if output.exists() and not replace:raise ValueError('Asset directory exists; use --replace to regenerate it')
    if output.exists() and not (output/'manifest.json').is_file():
        raise ValueError('Refusing to replace a directory without an HTC asset manifest')
    output.parent.mkdir(parents=True,exist_ok=True)
    tools=Path(__file__).resolve().parent
    with tempfile.TemporaryDirectory(prefix='.htc-extract-',dir=output.parent) as temporary:
        temporary=Path(temporary);bundle=temporary/'bundle';bundle.mkdir()
        for module,script in PREPARERS.items():
            resources=temporary/'extracted'/module/'res'
            resources.mkdir(parents=True)
            subprocess.run([sys.executable,str(tools/script),'--rom',str(rom),'--output',str(resources)],check=True)
            # Only Android resources enter the bundle, not decoded APKs or research dumps.
            target=bundle/module/'res';target.mkdir(parents=True)
            for directory in sorted(resources.iterdir()):
                if directory.is_dir():shutil.copytree(directory,target/directory.name)
        manifest={'format':1,'source':{'rom':rom.name,'sha256':sha256(rom)},'files':inventory(bundle)}
        (bundle/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
        verify(bundle)
        # Finish all extraction/validation before replacing a known generated bundle.
        if output.exists():
            backup=temporary/'previous';output.rename(backup)
            try:bundle.rename(output)
            except OSError:
                backup.rename(output);raise
        else:bundle.rename(output)
    return manifest


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom',type=Path,default=Path('res/3.33.401.153.zip'))
    parser.add_argument('--output',type=Path,default=Path('assets/htc'))
    parser.add_argument('--replace',action='store_true')
    args=parser.parse_args()
    manifest=extract(args.rom,args.output,args.replace)
    print(f"Extracted {len(manifest['files'])} resources to {args.output}")


if __name__=='__main__':main()
