"""Checks that extracted resources are complete and byte-exact without a ROM."""
import json
from pathlib import Path
import tempfile
import unittest

from htc_assets import MODULES, inventory, verify


class HtcAssetManifestTest(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory();self.addCleanup(self.temporary.cleanup)
        self.root=Path(self.temporary.name)
        for module in MODULES:
            path=self.root/module/'res/raw/fixture.bin';path.parent.mkdir(parents=True)
            path.write_bytes(b'\x00npTc\r\n'+module.encode())
        self.manifest={'format':1,'source':{'rom':'fixture.zip','sha256':'0'*64},'files':inventory(self.root)}
        self.write_manifest()

    def write_manifest(self):
        (self.root/'manifest.json').write_text(json.dumps(self.manifest))

    def test_valid_bundle(self):
        self.assertEqual(len(verify(self.root)['files']),len(MODULES))

    def test_corruption_and_missing_files(self):
        path=self.root/'launcher/res/raw/fixture.bin';original=path.read_bytes()
        path.write_bytes(original+b'bad')
        with self.assertRaisesRegex(ValueError,'mismatch'):verify(self.root)
        path.unlink()
        with self.assertRaises(ValueError):verify(self.root)

    def test_unlisted_resource(self):
        (self.root/'weather/res/raw/extra.bin').write_bytes(b'extra')
        with self.assertRaisesRegex(ValueError,'extra='):verify(self.root)

    def test_invalid_and_duplicate_manifest_paths(self):
        self.manifest['files'].append(self.manifest['files'][0]);self.write_manifest()
        with self.assertRaisesRegex(ValueError,'Duplicate'):verify(self.root)
        self.manifest['files'][-1]={'path':'../escape','size':0,'sha256':'0'*64};self.write_manifest()
        with self.assertRaisesRegex(ValueError,'Invalid resource path'):verify(self.root)


class CheckedInAssetTest(unittest.TestCase):
    def test_actual_resources_without_rom(self):
        root=Path(__file__).resolve().parents[1]/'assets/htc'
        manifest=verify(root)
        self.assertGreater(len(manifest['files']),1000)
        patches=[root/r['path'] for r in manifest['files'] if r['path'].startswith('sense-framework/res/') and r['path'].endswith('.png')]
        self.assertGreaterEqual(sum(b'npTc' in path.read_bytes() for path in patches),27)
