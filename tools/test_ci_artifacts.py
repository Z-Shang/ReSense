"""Check artifact completeness, checksums and exclusion of unrelated local files."""
import hashlib
from pathlib import Path
import tempfile
import unittest

from collect_ci_artifacts import collect, MODULES


class ArtifactCollectionTest(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root=Path(self.temporary.name)/'repo'
        for module in ('sense-framework','sense-services',*MODULES):
            self.put(f'build/outputs/shared-debug/{module}-debug.apk',module.encode())
        for module in (*MODULES,'sense-services'):
            self.put(f'{module}/build/outputs/apk/debug/{module}-debug.apk',module.encode())
        self.put('build/outputs/shared-debug/distribution.json',b'{"version":1}')
        self.put('sense-framework/build/outputs/aar/sense-framework-debug.aar',b'library')
        self.output=Path(self.temporary.name)/'artifacts'

    def put(self,name,data):
        path=self.root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)

    def test_expected_outputs_and_verifiable_hashes(self):
        self.put('build/outputs/shared-debug/launcher-debug-androidTest.apk',b'exclude-test-apk')
        self.put('build/outputs/shared-debug/debug.keystore',b'exclude-key')
        self.put('res/3.33.401.153.zip',b'exclude-rom')
        collect(self.root,self.output)
        self.assertEqual(len(list((self.output/'shared-debug').glob('*.apk'))),8)
        self.assertEqual(len(list((self.output/'standalone-debug').glob('*.apk'))),7)
        self.assertEqual(len(list((self.output/'framework-debug').glob('*.aar'))),1)
        self.assertFalse(list(self.output.rglob('*.keystore')))
        self.assertFalse(list(self.output.rglob('*.zip')))
        self.assertFalse(list(self.output.rglob('*androidTest*')))
        for manifest in self.output.glob('*/SHA256SUMS'):
            rows=manifest.read_text().splitlines()
            self.assertEqual(len(rows),len(list(manifest.parent.iterdir()))-1)
            for row in rows:
                digest,name=row.split('  ',1)
                self.assertEqual(hashlib.sha256((manifest.parent/name).read_bytes()).hexdigest(),digest)

    def test_missing_output_fails_before_creating_downloads(self):
        (self.root/'build/outputs/shared-debug/launcher-debug.apk').unlink()
        with self.assertRaises(FileNotFoundError):collect(self.root,self.output)
        self.assertFalse(self.output.exists())

    def test_existing_downloads_are_not_silently_overwritten(self):
        collect(self.root,self.output)
        with self.assertRaises(ValueError):collect(self.root,self.output)
