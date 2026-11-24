from pathlib import Path
import math
import unittest
from extract_dock_motion import extract


@unittest.skipUnless(Path('res/3.33.401.153.zip').exists(),'Local ROM required')
class DockMotionTest(unittest.TestCase):
    def test_recovered_duration_channels_and_endpoints(self):
        data=extract('res/3.33.401.153.zip')
        self.assertEqual(len(data['motions']),6)
        for motion in data['motions'].values():
            self.assertEqual(motion['frameCount'],11)
            self.assertEqual(motion['durationMs'],334)
            for samples in motion['channels'].values():
                self.assertEqual(len(samples),11)
                self.assertEqual(samples[0],0)
                self.assertEqual(samples[-1],1)
                self.assertTrue(all(math.isfinite(x) for x in samples))
                self.assertTrue(all(a<=b+.00001 for a,b in zip(samples,samples[1:])))
        off=data['motions']['slide_off']
        self.assertAlmostEqual(off['sourceExtents']['body'],91,places=3)
        self.assertAlmostEqual(off['sourceExtents']['rail'],96,places=3)
        self.assertGreater(off['channels']['body'][5],.8)
        self.assertNotEqual(off['channels']['body'],off['channels']['rail'])
