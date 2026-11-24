"""Validate the recovered local-ROM flap contract (skips when ROM is absent)."""
import math
from pathlib import Path
import unittest
from extract_clock_flip import extract


@unittest.skipUnless(Path('res/3.33.401.153.zip').exists(), 'Local ROM required')
class ClockFlipTest(unittest.TestCase):
    def test_flaps_share_hinge_crossing_and_finish_flat(self):
        data = extract(Path('res/3.33.401.153.zip'))
        top, bottom = data['top'], data['bottom']
        self.assertEqual([s['frame'] for s in top], list(range(11)))
        self.assertEqual([s['frame'] for s in bottom], list(range(11)))
        for track, start, end in ((top, 0, -180), (bottom, 180, 0)):
            self.assertAlmostEqual(track[0]['degrees'], start, places=4)
            self.assertAlmostEqual(track[-1]['degrees'], end, places=4)
            self.assertTrue(all(a['degrees'] >= b['degrees'] for a,b in zip(track,track[1:])))
            self.assertTrue(all(math.isfinite(s['degrees']) for s in track))
        self.assertAlmostEqual(top[5]['degrees'], -90, places=4)
        self.assertAlmostEqual(bottom[5]['degrees'], 90, places=4)
        self.assertNotAlmostEqual(bottom[1]['degrees']-bottom[0]['degrees'],
                                  bottom[5]['degrees']-bottom[4]['degrees'], places=2)
