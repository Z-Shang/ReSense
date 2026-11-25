#!/usr/bin/env python3
"""Resolve reproducible Git-based Android versions; emit Gradle properties."""
import hashlib
import os
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[1]


def git(root,*args):
    try:return subprocess.check_output(['git','-C',str(root),*args],stderr=subprocess.PIPE)
    except (OSError,subprocess.CalledProcessError) as error:
        raise ValueError('Git metadata unavailable. Set both SENSE_VERSION_CODE and SENSE_VERSION_NAME for source archives.') from error


def resolve(root=ROOT,environment=None):
    root=Path(root);environment=os.environ if environment is None else environment
    code=environment.get('SENSE_VERSION_CODE');name=environment.get('SENSE_VERSION_NAME')
    if code is not None or name is not None:
        if code is None or name is None:raise ValueError('Set SENSE_VERSION_CODE and SENSE_VERSION_NAME together')
        if not code.isdigit() or not 0<int(code)<=2100000000:raise ValueError('SENSE_VERSION_CODE must be an Android version code between 1 and 2100000000')
        if not name or name!=name.strip() or any(c in name for c in '\r\n\\'):raise ValueError('SENSE_VERSION_NAME must be a nonempty single-line value without backslashes')
        return {'senseVersionCode':str(int(code)),'senseVersionName':name,'senseVersionSource':'override'}
    if git(root,'rev-parse','--is-shallow-repository').strip()==b'true':
        raise ValueError('Git version codes require full history. Fetch with --unshallow or use explicit version overrides.')
    commit=git(root,'rev-parse','HEAD').decode().strip()
    count=int(git(root,'rev-list','--count','HEAD'))
    if not 0<count<=2100000000:raise ValueError('Git commit count is outside Android version-code bounds')
    dirty=bool(git(root,'status','--porcelain','--untracked-files=normal').strip())
    version=commit[:12]
    metadata={'senseVersionCode':str(count),'senseVersionName':version,'senseCommit':commit,
              'senseDirty':str(dirty).lower(),'senseVersionSource':'git'}
    if dirty:
        digest=hashlib.sha256()
        paths=sorted(set(p for p in git(root,'ls-files','--cached','--others','--exclude-standard','-z').split(b'\0') if p))
        for name in paths:
            path=root/os.fsdecode(name);digest.update(name+b'\0')
            if path.is_symlink():digest.update(b'link\0'+os.fsencode(os.readlink(path)))
            elif path.is_file():
                digest.update(str(path.stat().st_mode&0o111).encode()+b'\0')
                with path.open('rb') as source:
                    for chunk in iter(lambda:source.read(1024*1024),b''):digest.update(chunk)
            else:digest.update(b'deleted')
            digest.update(b'\0')
        fingerprint=digest.hexdigest();metadata['senseSourceDigest']=fingerprint
        metadata['senseVersionName']+='-dirty.'+fingerprint[:8]
    return metadata


if __name__=='__main__':
    try:
        print(''.join(key+'='+value+'\n' for key,value in sorted(resolve().items())),end='')
    except ValueError as error:raise SystemExit(str(error))
