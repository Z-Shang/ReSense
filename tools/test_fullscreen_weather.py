from pathlib import Path
import io,json,math,tempfile,unittest,zipfile
from extract_fullscreen_weather import extract
from decode_navbar import geometry
from extract_weather_catalog import track as encode_track
from mode10 import parse

@unittest.skipUnless(Path('res/3.33.401.153.zip').exists(),'Local ROM required')
class FullscreenWeatherTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory();cls.output=Path(cls.temp.name)/'res';cls.report=extract('res/3.33.401.153.zip',cls.output)
    @classmethod
    def tearDownClass(cls):cls.temp.cleanup()
    def test_stock_mapping_and_complete_materials(self):
        r=self.report;self.assertEqual([r['width'],r['height']],[540,960]);self.assertEqual(len(r['mapping']),54)
        self.assertEqual([r['mapping'][c-1]for c in [1,15,18,22,32,33,34]],[5,6,3,4,7,0,0])
        self.assertEqual(len(r['textures']),33)
        for texture in r['textures']:self.assertTrue((self.output/'drawable-nodpi'/(texture['resource']+'.png')).read_bytes().startswith(b'\x89PNG'))
    def test_effect_isolation_timing_tracks_and_rain_wiper(self):
        resources={t['resource']for t in self.report['textures']}
        for entry in self.report['scenes']:
            s=json.loads((self.output/'raw'/(entry['resource']+'.json')).read_text());nodes=s['nodes']
            self.assertEqual(s['fps'],30);self.assertEqual(s['entrance']['last_frame'],150)
            self.assertEqual(sum(n['parent']==0 for n in nodes),1)
            for i,node in enumerate(nodes):
                for field in ['parent','transformParent']:
                    seen={i};parent=node[field]
                    while parent>=0:
                        self.assertLess(parent,len(nodes));self.assertNotIn(parent,seen);seen.add(parent);parent=nodes[parent][field]
                if'texture'in node:self.assertIn(node['texture'],resources)
                for track in node['tracks'].values():
                    samples=track.get('s',[track.get('v')]);self.assertTrue(all(math.isfinite(v)for sample in samples for v in sample))
            if entry['effect']==3:
                self.assertTrue(any(n['name']=='b_rain_wiper.png'for n in nodes))
                self.assertTrue(any(n['name']=='Rain_shower'and n['childStart']==100 for n in nodes))
                self.assertEqual({n.get('coverageRole')for n in nodes if 'texture'in n}, {'wiper','streaks','wet','falling'})
                self.assertEqual(sum(n.get('coverageRole')=='wiper' for n in nodes),1)
                self.assertEqual(sum(n.get('coverageRole')=='streaks' for n in nodes),1)
                self.assertFalse(nodes[0]['pivotEnabled'])
                with zipfile.ZipFile('res/3.33.401.153.zip') as rom:
                    with zipfile.ZipFile(io.BytesIO(rom.read('system/app/HtcWeatherWallpaper.apk'))) as apk:
                        original=geometry(parse(apk.read('assets/Port/weather_overlay.m10')))
                for role,name in [('wiper','b_rain_wiper.png'),('streaks','b_raindrop_streaks.png')]:
                    actual=next(n for n in nodes if n.get('coverageRole')==role)
                    source=next(n for n in original if n['name']==name)['properties']
                    for field in ['Position','Rotation','Scale','PivotPosition','Opacity']:
                        self.assertEqual(actual['tracks'][field],encode_track(source[field]))
            else:self.assertFalse(any('coverageRole' in n for n in nodes))
