"""Validate shared asset integrity and resource ownership across app generators."""
import hashlib,io,json,tempfile,unittest,zipfile
from pathlib import Path
from prepare_sense_framework import prepare

@unittest.skipUnless(Path('res/3.33.401.153.zip').exists(),'Local HTC ROM required')
class SenseFrameworkTest(unittest.TestCase):
    def test_original_bytes_and_compiled_ninepatch_chunks(self):
        with tempfile.TemporaryDirectory() as directory:
            output=Path(directory);prepare('res/3.33.401.153.zip',output)
            report=json.loads((output/'raw/sense_framework_provenance.json').read_text())
            self.assertGreater(len(report),30)
            with zipfile.ZipFile('res/3.33.401.153.zip') as rom:
                apks={}
                for item in report:
                    package,path=item['source'].split('!/')
                    if package not in apks:
                        source='system/framework/' if package=='com.htc.resources.apk' else 'system/app/'
                        apks[package]=zipfile.ZipFile(io.BytesIO(rom.read(source+package)))
                    data=(output/'drawable-hdpi'/item['resource']).read_bytes()
                    self.assertEqual(data,apks[package].read(path))
                    self.assertEqual(hashlib.sha256(data).hexdigest(),item['sha256'])
                    if '.9.png' in path:self.assertIn(b'npTc',data,'Compiled nine-patch metadata was lost')
                for apk in apks.values():apk.close()
            resources={item['resource'] for item in report}
            for required in ['sense_common_checkbox_on.png','sense_common_radiobutton_on.png','sense_common_tab_button_left_selected.png','sense_common_photo_frame.png','sense_common_popupmenu.png','sense_people_default_photo.png']:
                self.assertIn(required,resources)

    def test_no_app_resources_or_permissions_in_library(self):
        import re,xml.etree.ElementTree as ET
        root=Path('sense-framework/src/main')
        for path in root.rglob('*.java'):
            self.assertIsNone(re.search(r'dev\.sense\.(launcher|weather|phone|contacts)\b',path.read_text()),str(path))
        manifest=ET.parse(root/'AndroidManifest.xml').getroot()
        self.assertEqual(list(manifest),[],'UI library must not add activities, services or permissions')

if __name__=='__main__':unittest.main()
