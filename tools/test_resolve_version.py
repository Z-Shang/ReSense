"""Version ordering, dirty-tree identity and source-archive overrides."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from resolve_version import resolve

SHA='a123456789abcdef0123456789abcdef0123456789'

class VersionTest(unittest.TestCase):
    def git(self,root,*args):
        return {('rev-parse','--is-shallow-repository'):b'false',('rev-parse','HEAD'):SHA.encode(),
                ('rev-list','--count','HEAD'):b'23',('status','--porcelain','--untracked-files=normal'):b''}[args]
    def test_clean_commit_and_numeric_update_order(self):
        with patch('resolve_version.git',side_effect=self.git):
            version=resolve(environment={})
        self.assertEqual(version['senseVersionName'],SHA[:12]);self.assertEqual(version['senseVersionCode'],'23');self.assertEqual(version['senseDirty'],'false')
        def later(root,*args):return b'24' if args==('rev-list','--count','HEAD') else self.git(root,*args)
        with patch('resolve_version.git',side_effect=later):self.assertGreater(int(resolve(environment={})['senseVersionCode']),int(version['senseVersionCode']))
    def test_dirty_content_changes_identity_without_randomizing_update_code(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);file=root/'source.txt';file.write_text('first')
            def dirty(root,*args):
                if args[0]=='status':return b' M source.txt'
                if args[0]=='ls-files':return b'source.txt\0'
                return self.git(root,*args)
            with patch('resolve_version.git',side_effect=dirty):
                first=resolve(root,{})
                self.assertEqual(first,resolve(root,{}))
                file.write_text('second');second=resolve(root,{})
            self.assertNotEqual(first['senseVersionName'],second['senseVersionName']);self.assertEqual(first['senseVersionCode'],second['senseVersionCode']);self.assertIn('-dirty.',first['senseVersionName'])
    def test_shallow_checkouts_fail_instead_of_resetting_update_codes(self):
        with patch('resolve_version.git',return_value=b'true'):
            with self.assertRaisesRegex(ValueError,'full history'):resolve(environment={})
    def test_archive_overrides_require_both_values_and_valid_codes(self):
        with patch('resolve_version.git',side_effect=AssertionError('Should not need Git')):
            result=resolve(environment={'SENSE_VERSION_CODE':'50','SENSE_VERSION_NAME':'archive-build'})
            self.assertEqual(result['senseVersionCode'],'50')
            for env in ({'SENSE_VERSION_CODE':'50'},{'SENSE_VERSION_NAME':'name'},{'SENSE_VERSION_CODE':'0','SENSE_VERSION_NAME':'name'},{'SENSE_VERSION_CODE':'-1','SENSE_VERSION_NAME':'name'}):
                with self.assertRaises(ValueError):resolve(environment=env)

if __name__=='__main__':unittest.main()
