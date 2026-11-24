#!/usr/bin/env python3
"""Verify the committed HTC resources; no ROM, SDK or extraction tools required."""
import argparse
from pathlib import Path
from htc_assets import verify


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assets',type=Path,default=Path(__file__).resolve().parents[1]/'assets/htc')
    args=parser.parse_args()
    try:manifest=verify(args.assets)
    except (OSError,ValueError,KeyError) as error:parser.exit(1,'Invalid HTC asset bundle: '+str(error)+'\n')
    files=manifest['files']
    print(f"Verified {len(files)} HTC resources ({sum(f['size'] for f in files)/1024/1024:.2f} MiB)")


if __name__=='__main__':main()
