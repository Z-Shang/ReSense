import tempfile,unittest
from pathlib import Path
from extract_search import extract

@unittest.skipUnless(Path('res/3.33.401.153.zip').exists(),'Local ROM required')
class SearchAssetsTest(unittest.TestCase):
    def test_original_search_artwork(self):
        with tempfile.TemporaryDirectory() as output:
            data=extract('res/3.33.401.153.zip',output)
            self.assertEqual(data['fieldHeightDp'],42)
            self.assertEqual(len(data['assets']),5)
            for item in data['assets']:
                image=(Path(output)/item['resource']).read_bytes()
                self.assertTrue(image.startswith(b'\x89PNG\r\n\x1a\n'))
                if 'icon' not in item['resource']:self.assertIn(b'npTc',image)
