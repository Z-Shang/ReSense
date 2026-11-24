from pathlib import Path
import struct
import tempfile
import unittest
from extract_dock_icons import extract
from mode10 import name_hash


@unittest.skipUnless(Path('res/3.33.401.153.zip').exists(),'Local ROM required')
class DockIconsTest(unittest.TestCase):
    def test_dock_material_bindings_and_png_dimensions(self):
        with tempfile.TemporaryDirectory()as directory:
            report=extract('res/3.33.401.153.zip',directory)
            self.assertEqual(len(report['assets']),7)
            for asset in report['assets']:
                data=(Path(directory)/asset['file']).read_bytes()
                self.assertEqual(data[:8],b'\x89PNG\r\n\x1a\n')
                if asset['file'].startswith('sense_remove_'):
                    self.assertEqual(struct.unpack('>II',data[16:24]),(40,36))
                if 'material'in asset:
                    self.assertEqual(asset['material'],name_hash(asset['original']))
                    self.assertEqual(list(struct.unpack('>II',data[16:24])),asset['size'])
                    self.assertEqual(data[25],6)
