#!/usr/bin/env python3
"""Generate local Android resources from the user's ROM; keep artwork out of source."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import zipfile
import io
import hashlib
from extract_fullscreen_weather import extract as extract_fullscreen_weather
from extract_search import extract as extract_search
from extract_music import extract as extract_music
from extract_leap import extract as extract_leap
from extract_dock_motion import extract as extract_motion
from extract_clock_digits import extract as extract_digits
from extract_clock_flip import extract
from extract_weather_catalog import extract as extract_catalog
from extract_dock_icons import extract as extract_dock_icons
from extract_weather_assets import extract as extract_weather
from prepare_weather_widgets import prepare as prepare_weather_widgets


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    for stale in args.output.glob("drawable-hdpi/sense_settings_*.png"): stale.unlink()
    (args.output/"raw/sense_settings_provenance.json").unlink(missing_ok=True)
    decoded = args.output.parent / 'decoded'
    subprocess.run([sys.executable, str(Path(__file__).with_name('decode_navbar.py')),
                    '--rom', str(args.rom), '--output', str(decoded)], check=True,
                   stdout=subprocess.DEVNULL)
    raw = args.output / 'raw'
    raw.mkdir(parents=True, exist_ok=True)
    (raw / 'sense_clock_flip.json').write_text(json.dumps(extract(args.rom), indent=2) + '\n')
    (raw / 'sense_dock_motion.json').write_text(json.dumps(extract_motion(args.rom), indent=2) + '\n')
    extract_catalog(args.rom, args.output)
    prepare_weather_widgets(args.rom, args.output)
    extract_fullscreen_weather(args.rom, args.output)
    drawables = args.output / 'drawable-nodpi'
    drawables.mkdir(parents=True, exist_ok=True)
    (args.output.parent / 'dock-icons-provenance.json').write_text(
        json.dumps(extract_dock_icons(args.rom, drawables), indent=2) + '\n')
    (raw / 'sense_clock_digits.json').write_text(json.dumps(extract_digits(args.rom, drawables), indent=2) + '\n')
    weather_report = extract_weather(args.rom, drawables)
    (raw / 'sense_weather_animation.json').write_bytes((drawables.parent / 'weather-animation-tracks.json').read_bytes())
    (args.output.parent / "weather-provenance.json").write_text(json.dumps(weather_report, indent=2) + "\n")
    background = (decoded / 'home_nav_bg.png').read_bytes()
    source = 'res/drawable-hdpi/common_mainnav_scroller3.png'
    with zipfile.ZipFile(args.rom) as rom:
        with zipfile.ZipFile(io.BytesIO(rom.read('system/app/Rosie.apk'))) as rosie:
            curve = rosie.read(source)
    (raw / 'sense_search.json').write_text(json.dumps(extract_search(args.rom, args.output)) + '\n')
    (raw / 'sense_music.json').write_text(json.dumps(extract_music(args.rom, drawables)) + '\n')
    (raw / 'sense_leap.json').write_text(json.dumps(extract_leap(args.rom, drawables)) + '\n')
    (drawables / 'sense_dock_background.png').write_bytes(background)
    (drawables / 'sense_dock_curve.png').write_bytes(curve)
    (args.output.parent / 'provenance.json').write_text(json.dumps({
        'background': {'source': 'fusion.apk!/assets/Default/drawable/shared.m10:home_nav_bg.png',
                       'sha256': hashlib.sha256(background).hexdigest()},
        'mask': {'source': 'Rosie.apk!/' + source, 'sha256': hashlib.sha256(curve).hexdigest()},
        'composition': 'Clean Mode10 shading reversed vertically; full dock and controls bent through a shared mesh fitted to the recovered arc.'
    }, indent=2) + '\n')
    print('Prepared ROM dock background and curve mask in', drawables)


if __name__ == '__main__':
    main()
