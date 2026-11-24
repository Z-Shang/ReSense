import hashlib,io,json,struct,tempfile,unittest,zipfile
from pathlib import Path
import xml.etree.ElementTree as ET
from prepare_lockscreen import prepare

@unittest.skipUnless(Path('res/3.33.401.153.zip').exists(),'Local ROM required')
class LockAssets(unittest.TestCase):
    def test_recovery(self):
        with tempfile.TemporaryDirectory()as temporary:
            out=Path(temporary);prepare('res/3.33.401.153.zip',out);report=json.loads((out/'raw/ls_recovery.json').read_text());self.assertAlmostEqual(report['fps'],29.97,places=2)
            navbar=(out/'drawable-nodpi/ls_navbar.png').read_bytes();self.assertEqual(struct.unpack('>II',navbar[16:24]),(540,94));self.assertEqual(navbar[25],6,'Navbar alpha plane missing')
            self.assertEqual(navbar,Path('references/3.33.401.153/lockscreen/lockscreen_navbar.png').read_bytes())
            ring=(out/'drawable-nodpi/ls_ring.png').read_bytes();self.assertEqual(struct.unpack('>II',ring[16:24]),(271,270));self.assertEqual(ring[25],6,'Ring alpha plane missing')
            with zipfile.ZipFile('res/3.33.401.153.zip')as rom:
                self.assertEqual(hashlib.sha256((out/'font/ls_clock.ttf').read_bytes()).hexdigest(),report['fontSha256'])
                self.assertEqual((out/'drawable-nodpi/ls_wallpaper.jpg').read_bytes(),rom.read('system/customize/resource/htc_wallpaper_01.jpg'))
                copied=[a for a in report['assets']if 'sha256'in a];self.assertEqual(len(copied),4)
                for item in copied:
                    package,path=item['source'].split('!/')
                    with zipfile.ZipFile(io.BytesIO(rom.read('system/app/'+package)))as apk:self.assertEqual((out/'drawable-nodpi'/item['file']).read_bytes(),apk.read(path))
    def test_modern_keyguard_contract(self):
        manifest=ET.parse('lockscreen/src/main/AndroidManifest.xml').getroot();ns='{http://schemas.android.com/apk/res/android}'
        permissions={p.get(ns+'name')for p in manifest.findall('uses-permission')};self.assertIn('android.permission.SYSTEM_ALERT_WINDOW',permissions);self.assertNotIn('android.permission.DISABLE_KEYGUARD',permissions);self.assertNotIn('android.permission.USE_FULL_SCREEN_INTENT',permissions)
        activity=next(a for a in manifest.find('application').findall('activity')if a.get(ns+'name')=='.LockScreenActivity')
        self.assertEqual(activity.get(ns+'showWhenLocked'),'true');self.assertEqual(activity.get(ns+'excludeFromRecents'),'true')
        service=next(s for s in manifest.find('application').findall('service')if s.get(ns+'name')=='.LockScreenTile');self.assertEqual(service.get(ns+'permission'),'android.permission.BIND_QUICK_SETTINGS_TILE')
if __name__=='__main__':unittest.main()
