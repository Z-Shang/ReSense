from pathlib import Path
import struct
import unittest
from inspect_weather_playback import inspect, marker_table


class MarkerBoundsTest(unittest.TestCase):
    def test_truncated_markers_rejected(self):
        for data in (b'',struct.pack('<I',1),struct.pack('<4I',1,0,31,200)):
            with self.assertRaises(ValueError):marker_table(data)


@unittest.skipUnless(Path('res/3.33.401.153.zip').exists(),'Local ROM required')
class WeatherPlaybackTest(unittest.TestCase):
    def test_recovered_scene_timing_and_native_defaults(self):
        report=inspect('res/3.33.401.153.zip')
        self.assertEqual(len(report['timelines']),3)
        for timeline in report['timelines']:
            self.assertEqual(timeline['fields']['FramesPerSecond']['value'],30)
            self.assertEqual(timeline['fields']['PlaybackSpeed']['value'],1)
            self.assertEqual([(m['name'],m['start'],m['last_frame']) for m in timeline['markers']],
                             [('In',0,30),('Rest',30,40),('Out',40,60)])
        self.assertFalse(report['camera']['active_widget_camera_confirmed'])
        defaults=report['camera']['defaults']
        self.assertEqual(defaults['VerticalFOV']['default'],45)
        self.assertAlmostEqual(defaults['NearClip']['default'],.1,places=6)
        self.assertEqual(defaults['FarClip']['default'],10000)

    def test_native_renderer_vtable_bindings_and_viewport_constants(self):
        import zipfile
        from trace_weather_camera import verify_bindings
        with zipfile.ZipFile('res/3.33.401.153.zip') as rom:
            report=verify_bindings(rom.read('system/lib/libmode10GLES20.so'))
        self.assertEqual(report['graphics_vtable_offset_0x50']['thumb_pointer'],'0x28665')
        self.assertEqual(report['timeline_vtable_offset_0xd4']['thumb_pointer'],'0x4a171')
        self.assertEqual(report['viewport_setup_literals'],
                         {'focal_length_mm':50,'film_gate_constant_mm':36})
