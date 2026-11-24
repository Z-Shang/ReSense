from pathlib import Path
import json
import math
import tempfile
import unittest
from extract_weather_catalog import extract


@unittest.skipUnless(Path('res/3.33.401.153.zip').exists(),'Local ROM required')
class WeatherCatalogTest(unittest.TestCase):
    def test_all_original_scenes_have_complete_materials_timing_and_parent_links(self):
        with tempfile.TemporaryDirectory()as directory:
            root=Path(directory)/'res';report=extract('res/3.33.401.153.zip',root)
            embedding=json.loads((root/'raw'/'htc_weather_widget_geometry.json').read_text())
            self.assertEqual(embedding['widgetDimensions'],[530,344])
            self.assertEqual(embedding['weatherDimensions'],[480,480])
            self.assertAlmostEqual(embedding['weatherScale'][0],.56,places=5)
            self.assertAlmostEqual(480*embedding['weatherScale'][0]/530,.5071698,places=5)
            self.assertEqual(len(report['scenes']),40)
            self.assertEqual(len(report['textures']),45)
            self.assertEqual({t['format']for t in report['textures']},{6,14})
            resources={t['resource']for t in report['textures']}
            deferred=0
            for entry in report['scenes']:
                scene=json.loads((root/'raw'/(entry['resource']+'.json')).read_text());nodes=scene['nodes']
                self.assertGreater(scene['fps'],0);self.assertGreater(scene['speed'],0)
                self.assertGreaterEqual(scene['entrance']['last_frame'],scene['entrance']['start'])
                for i,node in enumerate(nodes):
                    if node['inFrame']>0:deferred+=1
                    for parentKey in ['parent','transformParent']:
                        visited={i};index=node[parentKey]
                        while index>=0:
                            self.assertLess(index,len(nodes));self.assertNotIn(index,visited)
                            visited.add(index);index=nodes[index][parentKey]
                    if node['transformParent']>=0:
                        self.assertEqual(nodes[node['transformParent']]['parent'],node['parent'])
                    if 'texture'in node:self.assertIn(node['texture'],resources)
                    for track in node['tracks'].values():
                        values=track.get('s',[track.get('v')])
                        self.assertTrue(all(math.isfinite(v)for row in values for v in row))
                        if 's'in track:self.assertEqual([r[0]for r in values],sorted(r[0]for r in values))
            self.assertGreater(deferred,0)
