"""Validate embedded APKs byte-for-byte, order, and bootstrap independence."""
import hashlib
import json
import os
import re
import subprocess
import unittest
import zipfile
from pathlib import Path
from prepare_suite_payload import MODULES, ROOT

class SuitePayloadTest(unittest.TestCase):
    def test_built_suite(self):
        with zipfile.ZipFile(ROOT/'build/outputs/bundle-debug/sense-suite-debug.apk') as apk:
            manifest = json.loads(apk.read('assets/suite/manifest.json'))
            self.assertEqual(manifest['format'], 1)
            self.assertEqual([item['module'] for item in manifest['packages']], list(MODULES))
            self.assertEqual(len([n for n in apk.namelist() if n.endswith('.apk')]), 8)
            for item in manifest['packages']:
                data = apk.read('assets/suite/'+item['file'])
                self.assertEqual(len(data), item['size'])
                self.assertEqual(hashlib.sha256(data).hexdigest(), item['sha256'])
                self.assertEqual(data, (ROOT/'build/outputs/shared-debug'/item['file']).read_bytes())
            self.assertFalse(any(n.endswith('.keystore') or n.endswith('.zip') for n in apk.namelist()))
            self.assertNotIn(b'uses-static-library', apk.read('AndroidManifest.xml').replace(b'\x00', b''))

    def test_compiled_update_versions(self):
        from build_shared_framework import VERSION, APP_VERSION_CODE, APP_VERSION_NAME
        aapt=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0/aapt2'
        summary=json.loads((ROOT/'build/outputs/shared-debug/distribution.json').read_text())
        self.assertEqual(summary['version'],VERSION)
        self.assertEqual(summary['appVersionCode'],APP_VERSION_CODE)
        self.assertEqual(summary['appVersionName'],APP_VERSION_NAME)
        paths=[(ROOT/'build/outputs/shared-debug'/f'{module}-debug.apk',module) for module in MODULES]
        paths.append((ROOT/'build/outputs/bundle-debug/sense-suite-debug.apk','installer'))
        for path,module in paths:
            badging=subprocess.check_output([str(aapt),'dump','badging',str(path)],text=True)
            expected=VERSION if module=='sense-framework' else APP_VERSION_CODE
            self.assertEqual(int(re.search(r"versionCode='(\d+)'",badging)[1]),expected,module)
            if module!='sense-framework':self.assertIn("versionName='"+APP_VERSION_NAME+"'",badging,module)
            xml=subprocess.check_output([str(aapt),'dump','xmltree',str(path),'--file','AndroidManifest.xml'],text=True)
            if module=='installer':self.assertNotIn('E: uses-static-library',xml)
            else:
                self.assertIn('E: static-library' if module=='sense-framework' else 'E: uses-static-library',xml)
                self.assertEqual(int(re.search(r':version\([^)]*\)=(\d+)',xml)[1]),VERSION,module)

if __name__ == '__main__': unittest.main()
