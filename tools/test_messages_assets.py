"""Check stock SMS artwork provenance, icon and compiled bubble stretch metadata."""
import hashlib,io,json,tempfile,unittest,zipfile
from pathlib import Path
from prepare_messages_app import prepare

@unittest.skipUnless(Path('res/3.33.401.153.zip').exists(),'Local HTC ROM required')
class MessagesAssetsTest(unittest.TestCase):
    def test_assets_preserve_original_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            output=Path(directory);report=prepare('res/3.33.401.153.zip',output)
            with zipfile.ZipFile('res/3.33.401.153.zip') as rom,zipfile.ZipFile(io.BytesIO(rom.read('system/app/Mms.apk'))) as apk:
                self.assertEqual(len(report),124)
                for item in report:
                    data=(output/'drawable-hdpi'/item['resource']).read_bytes();self.assertEqual(data,apk.read(item['source'].split('!/',1)[1]));self.assertEqual(hashlib.sha256(data).hexdigest(),item['sha256'])
                for name in ['message_conversation_me','message_conversation_others']:
                    self.assertIn(b'npTc',(output/'drawable-hdpi'/('msg_'+name+'.png')).read_bytes())
                self.assertEqual((output/'drawable-hdpi/msg_messages.png').read_bytes(),apk.read('res/drawable-hdpi/messages.png'))
            widget_metadata=(output/'raw/msg_widget_provenance.json').read_bytes()
            widgets=json.loads(widget_metadata)
            self.assertEqual([s['name'] for s in widgets['styles']],['card','list','summary'])
            self.assertTrue(all(s['span']==[4,4] for s in widgets['styles']))
            self.assertEqual(len(widgets['scenes']),6)
            with zipfile.ZipFile('res/3.33.401.153.zip') as rom,zipfile.ZipFile(io.BytesIO(rom.read('system/app/com.htc.htcmsgwidgets3d.apk'))) as apk:
                for scene in widgets['scenes']:
                    data=(output/'raw'/scene['resource']).read_bytes()
                    self.assertEqual(data,apk.read(scene['source'].split('!/',1)[1]))
                    self.assertEqual(hashlib.sha256(data).hexdigest(),scene['sha256'])
                for style in ('card','list','summary'):
                    self.assertEqual((output/f'drawable-nodpi/msg_widget_preview_{style}.png').read_bytes(),apk.read(f'res/drawable-normal-hdpi/preview_messages_{style}.png'))
            original=(output/'raw/messages_provenance.json').read_bytes();prepare('res/3.33.401.153.zip',output);self.assertEqual(original,(output/'raw/messages_provenance.json').read_bytes());self.assertEqual(widget_metadata,(output/'raw/msg_widget_provenance.json').read_bytes())
if __name__=='__main__':unittest.main()
