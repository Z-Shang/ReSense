#!/usr/bin/env python3
"""Checks for the static HTC screen-mapping reference model."""
import io
import math
from pathlib import Path
import unittest
import zipfile

from recover_weather_mapping import camera, local_matrix, project, recover


class WeatherMappingTest(unittest.TestCase):
    def assertPoint(self, actual, expected):
        self.assertIsNotNone(actual)
        for a,b in zip(actual,expected):
            self.assertAlmostEqual(a,b,places=8)

    def test_surface_plane_identity_across_sizes(self):
        for width,height in [(540,960),(1080,2400),(1440,3200)]:
            for x,y in [(0,0),(width,height),(width/2,height/2),(37.5,721.25),(-30,-32)]:
                self.assertPoint(project([x,y,0],width,height),[x,y])

    def test_positive_z_approaches_camera(self):
        state = camera(540,960)
        self.assertEqual(state['distance'],750)
        self.assertLess(state['vertical_fov_degrees'],0)
        self.assertPoint(project([300,500,375],540,960),[330,520])
        self.assertPoint(project([300,500,-750],540,960),[285,490])

    def test_perspective_matrix_matches_closed_form(self):
        for width,height in [(540,960),(1080,2400)]:
            distance = 50*width/36
            for z in [-200,0,200]:
                factor = distance/(distance-z)
                self.assertPoint(project([43,107,z],width,height),
                                 [width/2+(43-width/2)*factor,height/2+(107-height/2)*factor])

    def test_clipping_does_not_clamp_behind_camera(self):
        distance = camera(540,960)['distance']
        self.assertIsNone(project([270,480,distance+1],540,960))
        self.assertIsNone(project([270,480,distance-0.05],540,960))
        self.assertIsNone(project([270,480,distance-10001],540,960))

    def test_pivot_precedes_scale_rotation_and_position(self):
        props = {'Position': {'value':[10,20,0]},'Scale': {'value':[2,3,1]},
                 'Rotation': {'value':[0,0,math.sqrt(.5),math.sqrt(.5)]},
                 'PivotPosition': {'value':[-4,-5,0]}, 'PivotRotation': {'value':[0,0,0,1]},
                 'PivotScale': {'value':[1,1,1]},'pivot_enabled': True}
        matrix = local_matrix(props,0)
        self.assertPoint([matrix[0][3],matrix[1][3]],[25,12])
        props['pivot_enabled'] = False
        matrix = local_matrix(props,0)
        self.assertPoint([matrix[0][3],matrix[1][3]],[10,20])

    def test_invalid_surfaces(self):
        for width,height in [(0,960),(-1,960),(540,0)]:
            with self.assertRaises(ValueError):
                camera(width,height)

    def test_original_rom_geometry_bounds(self):
        with zipfile.ZipFile(Path('res/3.33.401.153.zip')) as rom:
            with zipfile.ZipFile(io.BytesIO(rom.read('system/app/HtcWeatherWallpaper.apk'))) as apk:
                report = recover(apk.read('assets/Port/weather_overlay.m10'))
        self.assertAlmostEqual(report['stock_streak_bounds'][3],753.4425354003906)
        self.assertAlmostEqual(report['stock_wiper_max_y'],684.1932980660987)
        self.assertEqual(report['stock_wiper_max_frame'],73)
        # Larger raw surfaces do not implicitly rescale source geometry.
        for example in report['surface_comparisons']:
            self.assertPoint(example['raw_stock_units_streak_bounds'],report['stock_streak_bounds'])


if __name__ == '__main__':
    unittest.main()
