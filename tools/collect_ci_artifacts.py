#!/usr/bin/env python3
"""Stage only the distributable APK/AAR outputs, with installation notes and hashes."""
import argparse
import hashlib
import shutil
from pathlib import Path

from build_shared_framework import MODULES


SHARED_NOTES = '''Sense shared debug APK set (Android 15+)

Install these APKs from this same build together:
  adb install -r sense-framework-debug.apk
  adb install -r sense-services-debug.apk
Then install launcher, weather, phone, contacts, messages and lockscreen APKs.

distribution.json records the framework version and signing-certificate hash.
Do not mix the standalone and shared variants of the same app.
SHA256SUMS verifies the files in this download.
'''
STANDALONE_NOTES = '''Sense standalone debug APKs (Android 15+)

Each of the six user-facing apps includes its UI framework; install the apps
you want with adb install -r APP-debug.apk.
Install sense-services-debug.apk as well for shared weather data between apps.
The separate sense-framework APK is not required for this variant.
Do not mix the standalone and shared variants of the same app.
SHA256SUMS verifies the files in this download.
'''
FRAMEWORK_NOTES = '''Sense framework debug AAR

This is an Android library for project dependencies, not an installable APK.
Use the shared-debug download if you want the install-once framework APK.
SHA256SUMS verifies the files in this download.
'''


def collect(root, output):
    groups = {
        'shared-debug': ([root/'build/outputs/shared-debug'/f'{module}-debug.apk'
                          for module in ('sense-framework', 'sense-services', *MODULES)]
                         + [root/'build/outputs/shared-debug/distribution.json'], SHARED_NOTES),
        'standalone-debug': ([root/module/f'build/outputs/apk/debug/{module}-debug.apk'
                              for module in (*MODULES, 'sense-services')], STANDALONE_NOTES),
        'framework-debug': ([root/'sense-framework/build/outputs/aar/sense-framework-debug.aar'], FRAMEWORK_NOTES),
    }
    missing = [str(path) for sources, _ in groups.values() for path in sources if not path.is_file()]
    if missing:
        raise FileNotFoundError('Missing build outputs:\n'+'\n'.join(missing))
    if output.exists() and any(output.iterdir()):
        raise ValueError('Artifact output directory must be empty: '+str(output))
    for name, (sources, notes) in groups.items():
        destination = output/name
        destination.mkdir(parents=True, exist_ok=True)
        for source in sources:
            shutil.copyfile(source, destination/source.name)
        (destination/'README.txt').write_text(notes)
        manifest = []
        for path in sorted(destination.iterdir()):
            manifest.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name+'\n')
        (destination/'SHA256SUMS').write_text(''.join(manifest))
    return groups.keys()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,default=Path('build/ci-artifacts'))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    for name in collect(root, args.output):
        print(args.output/name)


if __name__ == '__main__':
    main()
