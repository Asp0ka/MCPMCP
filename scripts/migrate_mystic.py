"""Back up and replace only the old MCP mod in the explicitly authorized pack.
No recipe/scripts/other mods/Java trust settings are changed.
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import secrets
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TARGET = Path('D:/Games/mc/Cheat/versions/神秘启旅')


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    jar = ROOT / 'versions/1.20.1-forge/build/libs/mcpmcp-1.20.1-forge-0.1.2.jar'
    if not jar.is_file():
        raise SystemExit('Build MCPMCP first')
    with zipfile.ZipFile(jar) as z:
        if b'mcpmcp' not in z.read('META-INF/mods.toml'):
            raise SystemExit('Wrong MCPMCP artifact')
    old = sorted((TARGET / 'mods').glob('mc-info-mcp-*.jar'))
    existing = TARGET / 'mods' / jar.name
    plan = {'instance': str(TARGET), 'old_mods': [p.name for p in old],
            'replacement': jar.name, 'sha256': sha(jar), 'apply': args.apply}
    if not args.apply:
        print(json.dumps(plan, ensure_ascii=False)); return
    import psutil
    for process in psutil.process_iter(['name', 'cmdline']):
        try:
            cmd = process.info['cmdline'] or []
            if 'java' in (process.info['name'] or '').lower() and '--gameDir' in cmd:
                game = Path(cmd[cmd.index('--gameDir') + 1]).resolve()
                if game == TARGET.resolve():
                    raise SystemExit('Close the target game before replacement')
        except psutil.Error:
            continue
    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    backup = TARGET / '.mcpmcp-migration-backup' / stamp
    backup.mkdir(parents=True, exist_ok=False)
    records = []
    for p in old + ([existing] if existing.exists() else []):
        dest = backup / 'mods' / p.name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(p, dest)
        assert sha(p) == sha(dest)
        records.append({'path': str(p.relative_to(TARGET)), 'sha256': sha(p)})
    for name in ['mcinfo.json', 'mcinfo-packet-adapters.json', 'mcpmcp.json']:
        p = TARGET / 'config' / name
        if p.exists():
            dest = backup / 'config' / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(p, dest)
            assert sha(p) == sha(dest)
            records.append({'path': str(p.relative_to(TARGET)), 'sha256': sha(p)})
    # Backups contain credentials and remain local; never included in distribution.
    cfg_path = TARGET / 'config/mcpmcp.json'
    config_created = not cfg_path.exists()
    if config_created:
        cfg = {'mode': 'NORMAL', 'transport': 'MIXED', 'token': secrets.token_hex(32),
               'debug': False, 'trustJavaPlugins': False, 'port': 5008,
               'scanBudget': 4096, 'pluginOverrides': {}}
        cfg_path.write_text(json.dumps(cfg, indent=2) + '\n', encoding='utf-8')
    staged = existing.with_suffix('.jar.new')
    shutil.copy2(jar, staged)
    assert sha(staged) == sha(jar)
    staged.replace(existing)
    for p in old:
        p.unlink()
    assert existing.exists() and sha(existing) == sha(jar)
    assert not list((TARGET / 'mods').glob('mc-info-mcp-*.jar'))
    plan.update(backup=str(backup), backed_up=records, configuration_created=config_created,
                status='file_replacement_verified', runtime_validation='pending',
                java_trust='unchanged or false for newly created configuration')
    (backup / 'migration.json').write_text(json.dumps(plan, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    evidence = ROOT / '.local/mystic-migration.json'
    evidence.write_text(json.dumps(plan, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(plan, ensure_ascii=False))


if __name__ == '__main__':
    main()
