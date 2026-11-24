"""Bounds and codec checks for the recovered binary reader (no ROM required)."""
import struct
import unittest
from mode10 import etc1, fastlz, name_hash, parse


def record(name, kind, data):
    return struct.pack('<II', (len(data) << 4) | kind, name_hash(name)) + data + bytes((-len(data)) % 4)


class Mode10Tests(unittest.TestCase):
    def test_known_hashes_and_nested_records(self):
        self.assertEqual(name_hash('Root'), 0x7c8bed89)
        self.assertEqual(name_hash('home_nav_bg.png'), 0x7ac752cd)
        leaf = record('Name', 2, b'x\0\0\0')
        root = parse(b'!01m\x02\0\0\0' + record('Root', 1, leaf))
        self.assertEqual(bytes(root.children[0].payload), b'x\0\0\0')
        self.assertEqual(root.children[0].offset, 16)

    def test_dense_weather_vectors_have_implicit_frames(self):
        from decode_navbar import value
        payload = (record('Size', 2, struct.pack('<I', 2))
                   + record('StartFrame', 2, struct.pack('<I', 7))
                   + record('Samples', 2, struct.pack('<6f', 10, 20, 30, 40, 50, 60)))
        root = parse(b'!01m\x02\0\0\0' + record('Root', 1, record('Position', 1, payload)))
        track = value('Position', root.children[0])
        self.assertEqual(track['samples'], [
            {'frame': 7, 'value': [10.0, 20.0, 30.0]},
            {'frame': 8, 'value': [40.0, 50.0, 60.0]}])

    def test_truncated_and_unknown_records_are_rejected(self):
        valid = b'!01m\x02\0\0\0' + record('Root', 1, record('Name', 2, b'x\0'))
        for bad in (b'', valid[:-1], valid + b'\0', b'!01m\x02\0\0\0' + record('Root', 3, b'')):
            with self.subTest(size=len(bad)), self.assertRaises(ValueError):
                parse(bad)

    def test_nesting_is_bounded(self):
        nested = b''
        for _ in range(132):
            nested = record('Root', 1, nested)
        with self.assertRaises(ValueError):
            parse(b'!01m\x02\0\0\0' + nested)

    def test_fastlz_level_one_overlap(self):
        self.assertEqual(fastlz(b'\x02abc\x20\x02', 6), b'abcabc')
        self.assertEqual(fastlz(b'\x00a\x60\x00', 6), b'aaaaaa')

    def test_fastlz_level_two_extension_and_far_distance(self):
        self.assertEqual(fastlz(b'\x20a\xe0\xff\x01\x00', 266), b'a' * 266)
        literals = b'\x3f' + b'a' * 32 + (b'\x1f' + b'a' * 32) * 255
        self.assertEqual(fastlz(literals + b'\x3f\xff\x00\x00', 8195), b'a' * 8195)

    def test_fastlz_bad_bounds(self):
        for stream, size in ((b'', 1), (b'\x03ab', 4), (b'\x00a\x20\xff', 4),
                             (b'\x00a\x60\x00', 2), (b'\x00a', 2), (b'\x40a', 1)):
            with self.subTest(stream=stream), self.assertRaises(ValueError):
                fastlz(stream, size)

    def test_etc1_constant_blocks_and_crop(self):
        black = bytes.fromhex('00000002ffffffff')
        white = bytes.fromhex('ffffff0000000000')
        self.assertEqual(etc1(black, 4, 4), bytes(48))
        self.assertEqual(etc1(white, 4, 4), bytes([255]) * 48)
        self.assertEqual(etc1(white, 2, 1), bytes([255]) * 6)

    def test_etc1_invalid_data(self):
        for data, width, height in ((b'', 4, 4), (bytes(8), 0, 4),
                                    (bytes.fromhex('0700000200000000'), 4, 4)):
            with self.subTest(data=data), self.assertRaises(ValueError):
                etc1(data, width, height)


if __name__ == '__main__':
    unittest.main()
