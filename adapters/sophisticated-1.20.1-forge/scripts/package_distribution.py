from pathlib import Path
import json, hashlib, zipfile, shutil

ROOT = Path(__file__).resolve().parents[1]
targets = json.loads((ROOT/'evidence/targets.json').read_text(encoding='utf-8'))
providers = {'storage': ('sophisticatedstorage', 'StoragePlugin'), 'backpack': ('sophisticatedbackpacks', 'BackpackPlugin'), 'furnace': ('ironfurnaces', 'FurnacePlugin')}
summary = []
for key, (mod, provider) in providers.items():
    target = targets[mod]
    jar = ROOT/'build/libs'/f'{key}-adapter.jar'
    with zipfile.ZipFile(jar) as archive:
        service = archive.read('META-INF/services/dev.mcpmcp.api.McpmcpPlugin').decode().split()
        assert service == ['dev.mcpmcp.sophisticated.'+provider], (jar, service)
        classes = [n for n in archive.namelist() if n.startswith('dev/mcpmcp/sophisticated/') and n.endswith('Plugin.class')]
        assert classes == ['dev/mcpmcp/sophisticated/'+provider+'.class'], classes
        assert not any(n.startswith(('net/minecraft/', 'net/p3pp3rf1y/', 'ironfurnaces/', 'dev/mcpmcp/api/', 'dev/mcpmcp/core/')) for n in archive.namelist()), 'bundled host/target classes'
        live = archive.read('dev/mcpmcp/sophisticated/LiveAccess.class')
        assert b'm_91087_' in live and b'm_150399_' not in live, 'Minecraft getInstance must be SRG-reobfuscated'
    folder = ROOT/'distribution'/(mod+'-'+target['sha256'][:32])
    folder.mkdir(parents=True, exist_ok=True)
    manifest = {'api':1, 'id':'mcpmcp.'+mod, 'mod_id':mod, 'minecraft':'1.20.1', 'loader':'forge', 'target_version':target['version'], 'target_sha256':target['sha256']}
    (folder/'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+chr(10), encoding='utf-8')
    shutil.copy2(jar, folder/'adapter.jar')
    shutil.copy2(ROOT/'GUIDE.md', folder/'GUIDE.md')
    summary.append({'mod_id':mod, 'path':str(folder.relative_to(ROOT)), 'adapter_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(), 'size':jar.stat().st_size, 'provider':service[0], 'target_sha256':target['sha256'], 'target_version':target['version']})
(ROOT/'evidence/distribution.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+chr(10),encoding='utf-8')
print(json.dumps({'status':'PASS','independent_jars':len(summary),'artifacts':summary},ensure_ascii=False,indent=2))
