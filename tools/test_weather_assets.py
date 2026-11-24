"""Integration check of material resolution and original weather alpha planes."""
from pathlib import Path
import struct
import tempfile
import unittest
import zlib
from extract_weather_assets import extract


@unittest.skipUnless(Path('res/3.33.401.153.zip').exists(), 'Local ROM required')
class WeatherAssetsTest(unittest.TestCase):
    def test_materials_resolve_to_transparent_rgba_images(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory) / 'textures'
            report = extract('res/3.33.401.153.zip', directory)
            self.assertEqual(len(report['assets']), 4)
            self.assertEqual(len({a['id'] for a in report['assets']}), 4)
            for asset in report['assets']:
                data = (Path(directory) / asset['file']).read_bytes()
                self.assertEqual(data[:8], b'\x89PNG\r\n\x1a\n')
                width, height, depth, color = struct.unpack('>IIBB', data[16:26])
                self.assertEqual((width, height), (asset['width'], asset['height']))
                self.assertEqual((depth, color), (8, 6))
                offset, compressed = 8, bytearray()
                while offset < len(data):
                    length = struct.unpack('>I', data[offset:offset+4])[0]
                    if data[offset+4:offset+8] == b'IDAT':
                        compressed.extend(data[offset+8:offset+8+length])
                    offset += length + 12
                rows = zlib.decompress(compressed)
                self.assertEqual(len(rows), height * (1 + width * 4))
                alpha = []
                for y in range(height):
                    row = rows[y*(1+width*4):(y+1)*(1+width*4)]
                    self.assertEqual(row[0], 0)
                    alpha.extend(row[4::4])
                self.assertLess(min(alpha), 10)
                self.assertGreater(max(alpha), 150)
                self.assertTrue(any(10 < a < 150 for a in alpha))
