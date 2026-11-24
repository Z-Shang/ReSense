"""Check byte-exact scene packaging and complete material dependencies against the supplied ROM."""
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from mode10 import parse,walk,properties,integer,name_hash
from prepare_weather_widgets import prepare

class WeatherWidgetAssetsTest(unittest.TestCase):
    def test_original_scenes_previews_fonts_and_materials(self):
        rom=Path('res/3.33.401.153.zip')
        if not rom.exists():self.skipTest('Local ROM required')
        with tempfile.TemporaryDirectory() as directory:
            output=Path(directory);prepare(rom,output);raw=output/'raw'
            provenance=json.loads((raw/'sense_weather_widget_provenance.json').read_text());self.assertEqual(len(provenance),83)
            with zipfile.ZipFile(rom) as archive:
                with zipfile.ZipFile(io.BytesIO(archive.read('system/app/HtcWeather3DWidget.apk'))) as apk:
                    for item in provenance:
                        source=apk.read(item['source'].split('!/',1)[1]);actual=(raw/(item['resource']+'.m10')).read_bytes()
                        self.assertEqual(source,actual);self.assertEqual(hashlib.sha256(actual).hexdigest(),item['sha256'])
                    for size in ('2x1','2x2','4x4'):
                        self.assertEqual((output/'drawable-nodpi'/f'sense_weather_widget_preview_{size}.png').read_bytes(),apk.read(f'res/drawable-hdpi/preview_weather_{size}.png'))
                self.assertEqual((raw/'sense_widget_helveticaneueltpro35th.ttf').read_bytes(),archive.read('system/fonts/HelveticaNeueLTPro35Th.ttf'))
            def ids(document):
                found=set()
                for record in walk(document):
                    if record.kind!=1 or record.tag!=name_hash('Type'):continue
                    try:p=properties(record)
                    except ValueError:continue
                    if name_hash('Pixels')in p and name_hash('ID')in p:found.add(integer(p[name_hash('ID')]))
                return found
            shared=ids(parse((raw/'sense_weather_widget_shared.m10').read_bytes()))
            for path in raw.glob('*.m10'):
                document=parse(path.read_bytes());available=shared|ids(document)
                for record in walk(document):
                    if record.kind!=1 or record.tag!=name_hash('Node'):continue
                    p=properties(record);material=p.get(name_hash('DiffuseMap'))
                    if material is not None and integer(material)!=0:self.assertIn(integer(material),available,path.name)
            metadata=json.loads((raw/'sense_weather_widget_metadata.json').read_text())
            self.assertEqual(next(l for l in metadata['4x4']['labels']if l['name']=='CityTemp')['height'],200)
            self.assertEqual(next(l for l in metadata['4x4']['labels']if l['name']=='CityName')['height'],28)

if __name__=='__main__':unittest.main()
