#!/usr/bin/env python3
"""Extract stock Messages PNGs verbatim, including compiled npTc chunks.

Only artwork is recovered; this does not install or run the legacy APK.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile

PACKAGE = 'system/app/Mms.apk'


def prepare(rom, output, contact_sheet=None):
    """Write Android resources and a deterministic source/hash manifest."""
    output = Path(output)
    report = []
    with zipfile.ZipFile(rom) as source:
        with zipfile.ZipFile(io.BytesIO(source.read(PACKAGE))) as apk:
            names = sorted(n for n in apk.namelist()
                           if Path(n).parent.as_posix() == 'res/drawable-hdpi'
                           and n.endswith('.png'))
            if not names:
                raise ValueError('Mms.apk contains no drawable-hdpi PNGs')
            assets = []
            targets = set()
            for name in names:
                filename = 'msg_' + Path(name).name.replace('.9.png', '.png')
                if filename in targets:
                    raise ValueError(f'Resource name collision: {filename}')
                targets.add(filename)
                data = apk.read(name)
                assets.append((filename, data))
                report.append(dict(source=PACKAGE + '!/' + name,
                                   resource=filename,
                                   sha256=hashlib.sha256(data).hexdigest()))
    for filename, data in assets:
        target = output / 'drawable-hdpi' / filename
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    target = output / 'raw/messages_provenance.json'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    if contact_sheet:
        make_contact_sheet(assets, contact_sheet)
    print(f'Recovered {len(report)} original Messages images')
    return report


def make_contact_sheet(assets, destination):
    """Render previews separately; Pillow never rewrites recovered resources."""
    from PIL import Image, ImageDraw
    columns, width, height = 4, 310, 150
    sheet = Image.new('RGB', (columns * width,
                             ((len(assets) + columns - 1) // columns) * height), '#f2f2f2')
    draw = ImageDraw.Draw(sheet)
    for index, (name, data) in enumerate(assets):
        x, y = index % columns * width, index // columns * height
        art = Image.open(io.BytesIO(data)).convert('RGBA')
        dimensions = art.size
        art.thumbnail((width - 20, height - 50))
        sheet.paste(art, (x + (width - art.width) // 2, y + 8), art)
        # Split long labels rather than truncate resource names.
        label = name.removeprefix('msg_').removesuffix('.png')
        draw.text((x + 8, y + height - 42), label[:40], fill='black')
        draw.text((x + 8, y + height - 28), label[40:], fill='black')
        draw.text((x + 8, y + height - 14), f'{dimensions[0]} x {dimensions[1]} px', fill='#555555')
    target = Path(destination)
    target.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(target)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom', required=True)
    parser.add_argument('--output', required=True, help='Destination Android res directory')
    parser.add_argument('--contact-sheet', help='Optional PNG preview (requires Pillow)')
    args = parser.parse_args()
    prepare(args.rom, args.output, args.contact_sheet)
