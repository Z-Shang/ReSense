import tempfile,unittest
from pathlib import Path
from extract_music import extract

@unittest.skipUnless(Path('res/3.33.401.153.zip').exists(),'Local ROM required')
class MusicAssetsTest(unittest.TestCase):
    def test_original_widget_resources(self):
        with tempfile.TemporaryDirectory() as output:
            data=extract('res/3.33.401.153.zip',output)
            self.assertEqual(data['dimensions'],[530,344])
            textures={t['resource']:t for t in data['textures']}
            self.assertEqual(len(textures),15)
            self.assertEqual({k:v['dimensions'] for k,v in data['variants'].items()},{'4x1':[530,142],'4x2':[530,344],'4x4':[530,748]})
            self.assertEqual((textures['sense_music_large_frame']['width'],textures['sense_music_large_frame']['height']),(348,331))
            for name in ['sense_music_preview_small.png','sense_music_preview_large.png']:
                self.assertTrue((Path(output)/name).is_file())
            for name in ['sense_music_play','sense_music_pause','sense_music_next','sense_music_previous']:
                self.assertEqual((textures[name]['width'],textures[name]['height']),(44,44))
            self.assertEqual((textures['sense_music_album_frame']['width'],textures['sense_music_album_frame']['height']),(214,202))
            self.assertNotEqual((Path(output)/'sense_music_play.png').read_bytes(),(Path(output)/'sense_music_pause.png').read_bytes())
            self.assertTrue((Path(output)/'sense_music_preview.png').is_file())
