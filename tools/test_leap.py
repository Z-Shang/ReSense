import math
import tempfile
import unittest
from pathlib import Path
from extract_leap import extract

@unittest.skipUnless(Path('res/3.33.401.153.zip').exists(),'Local ROM required')
class LeapTest(unittest.TestCase):
    def test_original_slots_and_tracks(self):
        with tempfile.TemporaryDirectory() as out:
            data=extract('res/3.33.401.153.zip',out)
            self.assertEqual([s['center'] for s in data['slots']],[[184,252],[355,252],[102,504],[272,504],[444,504],[184,757],[355,757]])
            self.assertEqual(data['durationMs'],501)
            self.assertTrue((Path(out)/'sense_leap_frame.png').is_file())
            for slot in data['slots']:
                self.assertEqual(len(slot['tracks']),7)
                for track in slot['tracks']:
                    self.assertEqual(len(track['Position']),16)
                    self.assertAlmostEqual(track['Position'][-1][2],-2000,places=2)
                    for sample in track['Position']+track['Rotation']:
                        self.assertTrue(all(math.isfinite(x) for x in sample))
            self.assertEqual({m['name'] for m in data['rearrangeMarkers']},{'Drag_start','Drag_end','Drop','Add_page'})
