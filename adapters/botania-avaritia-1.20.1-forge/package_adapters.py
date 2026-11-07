from pathlib import Path
import hashlib
import json
import shutil
import zipfile
from verify_targets import ROOT, TARGETS, metadata

def main():
    records = []
    for mod, target in TARGETS.items():
        metadata(mod)
        source = ROOT / mod / 'build/libs/adapter.jar'
        with zipfile.ZipFile(source) as jar:
            raw_manifest = jar.read('META-INF/MANIFEST.MF').decode().replace(chr(13), '').replace(chr(10) + ' ', '')
            attrs = dict(line.split(': ', 1) for line in raw_manifest.splitlines() if ': ' in line)
            assert attrs['Target-SHA256'] == target['sha256']
            assert attrs['Target-Mod-Version'] == target['version']
            assert attrs['Target-Implementation-Version'] == target['implementation_version']
            service = jar.read('META-INF/services/dev.mcpmcp.api.McpmcpPlugin').decode().strip().splitlines()
            assert service == ['dev.mcpmcp.magic.' + mod.capitalize() + 'Provider'], service
            assert not any(n.startswith(('net/minecraft/', 'dev/mcpmcp/core/', 'dev/mcpmcp/api/')) for n in jar.namelist())
            assert b'm_91087_' in jar.read('dev/mcpmcp/magic/LiveBackend.class'), 'reobfuscation did not rename Minecraft.getInstance'
        folder = mod + '-' + target['sha256'][:32]
        dest = ROOT / 'dist/MCPMCP-Plugin' / folder
        dest.mkdir(parents=True, exist_ok=True)
        manifest = {'api': 1, 'id': 'mcpmcp.' + mod, 'mod_id': mod, 'minecraft': '1.20.1', 'loader': 'forge', 'target_version': target['version'], 'target_sha256': target['sha256'], 'actions': []}
        (dest / 'plugin.json').write_text(json.dumps(manifest, indent=2) + chr(10), encoding='utf-8')
        shutil.copy2(source, dest / 'adapter.jar')
        shutil.copy2(ROOT / 'GUIDE.md', dest / 'GUIDE.md')
        digest = hashlib.sha256((dest / 'adapter.jar').read_bytes()).hexdigest()
        assert (dest / 'adapter.jar').read_bytes() == source.read_bytes()
        records.append({'mod_id': mod, 'adapter': str(dest / 'adapter.jar'), 'adapter_sha256': digest, 'target_sha256': target['sha256'], 'target_mod_version': target['version'], 'target_implementation_version': target['implementation_version'], 'service_providers': service, 'reobfuscated': True})
    (ROOT / 'evidence/artifacts.json').write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(records, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    main()
