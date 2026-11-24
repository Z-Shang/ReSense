"""Validate embedded APKs byte-for-byte, order, and bootstrap independence."""
import hashlib
import json
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

if __name__ == '__main__': unittest.main()
