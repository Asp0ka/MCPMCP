"""Validate and stage Java plugins into the authorized 神秘启旅 instance.

Each candidate directory must contain plugin.json + adapter.jar. The declared
target hash and version are checked against the real mod JAR before copying.
"""
from pathlib import Path
import hashlib
import json
import re
import shutil
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACK = Path('D:/Games/mc/Cheat/versions/神秘启旅')
ADAPTERS = ROOT / 'adapters'
INSTALLED = PACK / 'MCPMCP-Plugin'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def mod_meta(jar):
    import tomllib
    with zipfile.ZipFile(jar) as z:
        data = tomllib.loads(z.read('META-INF/mods.toml').decode('utf-8'))
        manifest = z.read('META-INF/MANIFEST.MF').decode('utf-8', 'replace')
    blocks = data.get('mods', [])
    ids = [b.get('modId', '') for b in blocks if b.get('modId')]
    version = blocks[0].get('version', '') if blocks else ''
    if '${file.jarVersion}' in version:
        m = re.search(r'Implementation-Version:\s*(\S+)', manifest)
        version = m.group(1) if m else ''
    return ids, version


def candidates():
    for adapter in ADAPTERS.iterdir():
        if not adapter.is_dir():
            continue
        for manifest in list(adapter.rglob('plugin.json')) + list(adapter.rglob('manifest.json')):
            folder = manifest.parent
            if (folder / 'adapter.jar').is_file():
                yield adapter, folder, manifest


def main():
    apply = '--apply' in sys.argv
    mods = {}
    for jar in (PACK / 'mods').glob('*.jar'):
        try:
            ids, version = mod_meta(jar)
        except (KeyError, zipfile.BadZipFile, UnicodeDecodeError):
            continue
        for mod_id in ids:
            mods.setdefault(mod_id, []).append((jar, version))
    plan = []
    for adapter, folder, manifest_path in sorted(candidates()):
        manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
        problems = []
        if manifest.get('api') != 1:
            problems.append('api')
        mod_id = manifest.get('mod_id', '')
        target = manifest.get('target_sha256', '')
        expected = manifest.get('target_version', '')
        found = mods.get(mod_id, [])
        if len(found) != 1:
            problems.append(f'mod_candidates={len(found)}')
            actual_hash = actual_version = None
        else:
            jar, actual_version = found[0]
            actual_hash = sha(jar)
            if actual_hash != target:
                problems.append('hash_mismatch')
            if expected != actual_version:
                problems.append(f'version_mismatch:{expected}!={actual_version}')
        row = {'id': manifest.get('id'), 'mod_id': mod_id, 'from': str(folder),
               'target_version': expected, 'actual_version': actual_version if len(found) == 1 else None,
               'target_sha256': target, 'actual_sha256': actual_hash if len(found) == 1 else None,
               'problems': problems, 'adapter_sha256': sha(folder / 'adapter.jar')}
        plan.append(row)
        if problems:
            continue
        dest = INSTALLED / folder.name
        row['to'] = str(dest)
        if apply:
            if dest.exists():
                shutil.rmtree(dest)
            dest.mkdir(parents=True)
            shutil.copy2(manifest_path, dest / 'plugin.json')
            shutil.copy2(folder / 'adapter.jar', dest / 'adapter.jar')
            guide = folder / 'GUIDE.md'
            if guide.is_file():
                shutil.copy2(guide, dest / 'GUIDE.md')
            row['installed_adapter_sha256'] = sha(dest / 'adapter.jar')
    (ROOT / '.local/plugin-install-plan.json').write_text(
        json.dumps(plan, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(plan, ensure_ascii=False, indent=2))
    if any(r['problems'] for r in plan):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
