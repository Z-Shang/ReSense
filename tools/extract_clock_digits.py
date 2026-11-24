#!/usr/bin/env python3
"""Recover the clock's shaded bitmap numerals from the supplied ROM."""
import io
import zipfile
from mode10 import parse, integer, name_hash
from extract_weather_catalog import texture_records, decode_texture
from decode_navbar import geometry


def extract(rom_path, output):
    with zipfile.ZipFile(rom_path) as rom:
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/fusion.apk'))) as apk:
            shared = parse(apk.read('assets/Default/drawable/shared.m10'))
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/HtcClock3DWidget.apk'))) as apk:
            nodes = geometry(parse(apk.read('assets/Port/clock_weather.m10')))
    bindings = {n['properties']['DiffuseMap']['value_u32'] for n in nodes
                if 'DiffuseMap' in n['properties']}
    records = {integer(p[name_hash('ID')]): (r, p) for r, p in texture_records(shared)}
    report = {'source': 'fusion.apk!/assets/Default/drawable/shared.m10',
              'widgetWidth': 530, 'glyphWidth': 99, 'advance': 84,
              'note': 'Upper/lower refer to the upright glyph; source bitmaps require vertical reflection in Canvas.',
              'digits': []}
    for digit in range(10):
        for half, base, height in [('top', 1459749318, 79), ('bottom', 516154905, 83)]:
            material = base + digit * 1185921
            if material not in bindings:
                raise ValueError('Digit texture is not referenced by the clock scene')
            record, props = records[material]
            resource = f'sense_clock_digit_{digit}_{half}'
            info = decode_texture(record, props, output / (resource + '.png'))
            if (info['width'], info['height']) != (99, height):
                raise ValueError('Unexpected clock numeral dimensions')
            report['digits'].append(dict(info, digit=digit, half=half, resource=resource, material=material))
    return report
