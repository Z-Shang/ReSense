"""Inventory and verify the build-ready HTC resource bundle without the ROM."""
import hashlib
import json
from pathlib import Path, PurePosixPath

MODULES=('launcher','sense-framework','weather','phone','contacts','messages','lockscreen')


def sha256(path):
    digest=hashlib.sha256()
    with Path(path).open('rb') as source:
        for chunk in iter(lambda:source.read(1024*1024),b''):digest.update(chunk)
    return digest.hexdigest()


def inventory(root):
    records=[]
    for module in MODULES:
        resources=root/module/'res'
        if not resources.is_dir():raise ValueError('Missing HTC resources: '+str(resources))
        files=sorted(p for p in resources.rglob('*') if p.is_file())
        if not files:raise ValueError('Empty HTC resources: '+str(resources))
        for path in files:
            if path.is_symlink():raise ValueError('Resource symlinks are unsupported: '+str(path))
            records.append({'path':path.relative_to(root).as_posix(),'size':path.stat().st_size,'sha256':sha256(path)})
    return records


def verify(root):
    root=Path(root)
    manifest=json.loads((root/'manifest.json').read_text())
    if manifest.get('format')!=1:raise ValueError('Unsupported HTC asset manifest format')
    expected={}
    for record in manifest['files']:
        name=record['path'];path=PurePosixPath(name)
        if len(path.parts)!=4 or path.parts[0] not in MODULES or path.parts[1]!='res' or '..' in path.parts or path.is_absolute():
            raise ValueError('Invalid resource path: '+name)
        if name in expected:raise ValueError('Duplicate resource path: '+name)
        expected[name]=record
    actual={record['path']:record for record in inventory(root)}
    if actual.keys()!=expected.keys():
        raise ValueError('HTC inventory differs; missing='+str(sorted(expected.keys()-actual.keys()))+
                         ', extra='+str(sorted(actual.keys()-expected.keys())))
    for name,record in actual.items():
        if record!=expected[name]:raise ValueError('HTC asset checksum/size mismatch: '+name)
    return manifest
