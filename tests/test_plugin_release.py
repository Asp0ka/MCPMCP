"""Release guard regression tests; no real game or client files are used."""
import sys
import tempfile
import unittest
import zipfile
import io
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import plugin_release as r


class ReleaseTest(unittest.TestCase):
    def test_curios_is_a_required_exact_target(self):
        self.assertIn('mcpmcp.curios', r.EXPECTED)
        specs=[x for x in r.SPECS if x[0]=='curios']
        self.assertEqual(len(specs),1)
        self.assertEqual(specs[0][1],'curios-1.20.1-forge')

    def test_incremental_receipt_rejects_stale_untouched_build(self):
        current=[{'canonical':'old','id':'mcpmcp.fixture','project':'existing','adapter_sha256':'a','source_sha256':'b','manifest_sha256':'c','guide_sha256':'d'},
                 {'canonical':'new','id':'mcpmcp.curios','project':'curios-1.20.1-forge','adapter_sha256':'n','source_sha256':'n','manifest_sha256':'n','guide_sha256':'n'}]
        previous={'artifacts':[current[0]]}
        r.check_untouched(current,previous,{'curios-1.20.1-forge'})
        for key in ('adapter_sha256','source_sha256','manifest_sha256','guide_sha256'):
            with self.assertRaises(ValueError):r.check_untouched([current[0]|{key:'changed'},current[1]],previous,{'curios-1.20.1-forge'})
        with self.assertRaises(ValueError):r.check_untouched(current,{'artifacts':[]},{'curios-1.20.1-forge'})

    def test_curios_release_requires_its_own_final_binary_proof(self):
        from package_plugins import combine_acceptance
        baseline={'batch':'baseline','verified':True,'checks':{'all_plugins_loaded':True,'policy_restored':True,'empty_cursor_before_leave':True,'no_recovery':True,'iron_effect':True,'travel_effect':True,'furnace_transfer':True,'storage_roundtrip':True,'backpack_contract':True,'nbt_stale_native_rejected':True,'nbt_stale_packet_rejected':True,'jei_focus_preserved':True},'installed_hashes':{'mcpmcp.jei':'old'}}
        rows=[{'id':'mcpmcp.jei','adapter_sha256':'old'},{'id':'mcpmcp.curios','adapter_sha256':'new'}]
        curios={'batch':'curios','verified':True,'checks':{k:True for k in ['all_plugins_loaded','read_slots','equip','unequip','swap','cosmetic','render','stale_identity_rejected','invalid_slot_rejected','cursor_rejected','menu_preserved','closed_state_restored','policy_restored','empty_cursor','no_recovery']},'installed_hashes':{'mcpmcp.jei':'old','mcpmcp.curios':'new'}}
        self.assertTrue(combine_acceptance(rows,baseline,curios)['verified'])
        for bad in [curios|{'verified':False},curios|{'checks':{}},curios|{'installed_hashes':{'mcpmcp.curios':'different'}}]:
            with self.assertRaises(ValueError):combine_acceptance(rows,baseline,bad)
        with self.assertRaises(ValueError):combine_acceptance([rows[0]|{'adapter_sha256':'changed'},rows[1]],baseline,curios)

    def test_exact_ids_required(self):
        with self.assertRaises(ValueError):
            r.require_ids([], {'mcpmcp.jei'})
        with self.assertRaises(ValueError):
            r.require_ids([{'id': 'arbitrary'}], {'mcpmcp.jei'})
        with self.assertRaises(ValueError):
            r.require_ids([{'id':'mcpmcp.jei'}, {'id':'mcpmcp.jei'}], {'mcpmcp.jei'})
        r.require_ids([{'id':'mcpmcp.jei'}], {'mcpmcp.jei'})

    def test_supported_process_forms(self):
        pack = Path(tempfile.gettempdir()) / 'fixture-game'
        self.assertTrue(r.process_targets(['java','--gameDir',str(pack)], Path.cwd(), pack))
        self.assertTrue(r.process_targets(['java','--gameDir='+str(pack)], Path.cwd(), pack))
        self.assertTrue(r.process_targets(['java'], pack, pack))
        self.assertTrue(r.process_targets(['java'], pack/'native', pack))
        with self.assertRaises(ValueError):r.process_targets(['java','@unknown-args.txt'],pack.parent,pack)
        self.assertTrue(r.process_targets(['java','--gameDir','fixture-game'], pack.parent, pack))
        self.assertFalse(r.process_targets(['java','--gameDir','other'], pack.parent, pack))
        with self.assertRaises(ValueError):
            r.process_targets(['java','--gameDir'], pack, pack)

    def test_no_path_escape_or_link(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.assertEqual(r.contained(root, root/'ok'), (root/'ok').resolve())
            with self.assertRaises(ValueError):
                r.contained(root, root/'..'/'outside')
            target=root/'real';target.mkdir()
            try:
                (root/'link').symlink_to(target, target_is_directory=True)
            except OSError:
                return
            with self.assertRaises(ValueError):
                r.contained(root,root/'link'/'adapter.jar')

    def test_jar_provider_and_namespace(self):
        def jar(entries):
            out=io.BytesIO()
            with zipfile.ZipFile(out,'w') as z:
                for n,b in entries.items():z.writestr(n,b)
            return out.getvalue()
        service='META-INF/services/dev.mcpmcp.api.McpmcpPlugin'
        good={service:'  # comment\ndev.mcpmcp.fixture.Provider # one provider\n', 'dev/mcpmcp/fixture/Provider.class':b'x'}
        self.assertEqual(r.validate_jar(jar(good)), 'dev.mcpmcp.fixture.Provider')
        for extra in [{service:'other.Provider'}, {'net/minecraft/Bad.class':b'x'}, {'other/Dependency.class':b'x'}, {'../escape':b'x'}]:
            with self.assertRaises(ValueError):r.validate_jar(jar(good|extra))

    def test_archive_preflight_and_verified_replace(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest=Path(tmp)/'out.zip';dest.write_bytes(b'previous')
            with self.assertRaises(ValueError):r.write_archive(dest,{'../escape':b'x'})
            self.assertEqual(dest.read_bytes(),b'previous')
            r.write_archive(dest,{'safe/entry':b'data'})
            with zipfile.ZipFile(dest) as z:self.assertEqual(z.read('safe/entry'),b'data')

    def test_selector_rejects_missing_wrong_and_ambiguous_targets(self):
        rows=[];mods={}
        for pid in sorted(r.EXPECTED):
            mod=pid.split('.',1)[1]
            rows.append({'id':pid,'mod_id':mod,'manifest':{'target_version':'1','target_sha256':'a'*64}})
            mods[mod]=[{'version':'1','sha256':'a'*64,'path':Path('fixture.jar')}]
        self.assertEqual(len(r.select(rows,mods)),len(r.EXPECTED))
        for bad in [[],rows[:-1]]:
            with self.assertRaises(ValueError):r.select(bad,mods)
        first=rows[0]['mod_id']
        for found in [[],[{'version':'2','sha256':'a'*64}],mods[first]*2]:
            with self.assertRaises(ValueError):r.select(rows,mods|{first:found})

    def test_build_receipt_rejects_any_source_or_jar_drift(self):
        import json
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'.local').mkdir()
            row={'canonical':'fixture','id':'mcpmcp.fixture','adapter_sha256':'a','source_sha256':'b','manifest_sha256':'c','guide_sha256':'d'}
            (root/'.local/plugin-build-receipt.json').write_text(json.dumps({'artifacts':[row]}))
            r.receipt_matches([row],root)
            for key in ['adapter_sha256','source_sha256','manifest_sha256','guide_sha256']:
                with self.assertRaises(ValueError):r.receipt_matches([row|{key:'changed'}],root)
            with self.assertRaises(ValueError):r.receipt_matches([],root)

    def test_missing_manifest_and_current_build_are_errors(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):r.catalog(Path(tmp))

    def test_install_rollback_and_preserve_support_files(self):
        import install_plugins as installer
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);pack=root/'fixture';pack.mkdir()
            src=root/'source';src.mkdir()
            for name in ['plugin.json','adapter.jar','GUIDE.md']:(src/name).write_bytes(('new '+name).encode())
            row={'canonical':'fixture-hash','id':'mcpmcp.fixture','manifest_path':src/'plugin.json','jar_path':src/'adapter.jar','guide_path':src/'GUIDE.md'}
            dest=pack/'MCPMCP-Plugin/fixture-hash';dest.mkdir(parents=True)
            for name in ['plugin.json','adapter.jar','GUIDE.md']:(dest/name).write_bytes(('old '+name).encode())
            (dest/'user-notes.txt').write_text('keep')
            original=Path.replace
            def fail_once(path,target):
                if Path(target).name=='adapter.jar':raise OSError('injected replacement failure')
                return original(path,target)
            with patch.object(installer,'ROOT',root),patch.object(installer,'ensure_stopped'),patch.object(installer,'receipt_matches'),patch.object(installer,'catalog',return_value=[]),patch.object(installer,'verify_frozen'),patch.object(Path,'replace',fail_once):
                with self.assertRaises(OSError):installer.apply_install([row],pack)
            for name in ['plugin.json','adapter.jar','GUIDE.md']:self.assertEqual((dest/name).read_bytes(),('old '+name).encode())
            self.assertEqual((dest/'user-notes.txt').read_text(),'keep')
            self.assertFalse(list(dest.glob('*.new')))
            # Full preflight failure must not replace even the first file.
            with patch.object(installer,'ROOT',root),patch.object(installer,'ensure_stopped'),patch.object(installer,'receipt_matches'),patch.object(installer,'catalog',return_value=[]),patch.object(installer,'verify_frozen',side_effect=ValueError('source drift')):
                with self.assertRaises(ValueError):installer.apply_install([row],pack)
            self.assertEqual((dest/'plugin.json').read_bytes(),b'old plugin.json')

    def test_source_identity_tracks_uncommitted_code(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);p=root/'src/Main.java';p.parent.mkdir();p.write_text('one')
            before=r.source_hash(root);p.write_text('two')
            self.assertNotEqual(before,r.source_hash(root))
            (root/'build').mkdir();(root/'build/noise.java').write_text('generated')
            before=r.source_hash(root);(root/'build/noise.java').write_text('more')
            self.assertEqual(before,r.source_hash(root))

if __name__ == '__main__':unittest.main()
