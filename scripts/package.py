"""Package the built, tracked deliverable without caches or client credentials."""
import hashlib
import io
import json
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'dist'
VERSION = '0.1.2'
JAR = ROOT / f'versions/1.20.1-forge/build/libs/mcpmcp-1.20.1-forge-{VERSION}.jar'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    if not JAR.is_file():
        raise SystemExit('Build the Forge artifact before packaging')
    status = subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True)
    if status.strip():
        raise SystemExit('Commit tracked source changes before packaging')
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    source = subprocess.check_output(['git', 'archive', '--format=zip', 'HEAD'], cwd=ROOT)
    jar = JAR.read_bytes()
    manifest = {'name': 'MCP Minecraft Plus', 'version': VERSION, 'minecraft': '1.20.1',
                'forge': '47.4.28', 'git_commit': commit, 'jar_sha256': digest(jar),
                'sources_sha256': digest(source), 'requires': ['Java 17', 'Forge 47.4.28', 'Python 3.10+ for MCP'],
                'optional': ['compatible Baritone for movement'], 'jei_required': False}
    install = '''# MCPMCP 0.1.2 / Minecraft 1.20.1 Forge

1. Copy mods/mcpmcp-1.20.1-forge-0.1.2.jar into the intended instance mods folder.
2. Start that instance normally with Java 17 and compatible Forge. Baritone is optional for movement; JEI/REI are not required.
3. Configure an MCP stdio server: python <absolute>/bridge/mcp_server.py --config <instance>/config/mcpmcp.json
4. Load skills/mcpmcp/SKILL.md; read get_status/list_capabilities/describe_action before actions.
5. Read docs/acceptance.md for verified scope and limits. Never copy the generated local token into chat or launch arguments.

sources.zip contains the exact committed source snapshot. The full Git repository remains at the development workspace. manifest.json and SHA256SUMS identify artifacts. No game, mod dependency, account credential, client config or save is bundled.
'''
    DIST.mkdir(exist_ok=True)
    out = DIST / f'MCPMCP-{VERSION}-1.20.1-forge.zip'
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as bundle:
        bundle.writestr('mods/' + JAR.name, jar)
        bundle.writestr('sources.zip', source)
        bundle.writestr('manifest.json', json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
        bundle.writestr('INSTALL.md', install)
        bundle.writestr('SHA256SUMS', digest(jar) + '  mods/' + JAR.name + '\n' + digest(source) + '  sources.zip\n')
        with zipfile.ZipFile(io.BytesIO(source)) as tracked:
            for name in tracked.namelist():
                if not name.endswith('/') and name.startswith(('bridge/', 'docs/', 'skills/', 'examples/')):
                    bundle.writestr(name, tracked.read(name))
        build_log = ROOT / 'docs/evidence/native-chat-0.1.2/build-output.txt'
        if build_log.is_file():
            bundle.write(build_log, 'docs/evidence/build.log')
    with zipfile.ZipFile(out) as bundle:
        if bundle.testzip() is not None:
            raise SystemExit('Archive integrity failed')
        assert digest(bundle.read('mods/' + JAR.name)) == manifest['jar_sha256']
        assert digest(bundle.read('sources.zip')) == manifest['sources_sha256']
        assert not any('mcpmcp.json' in n or '.gradle/' in n or '.local/' in n for n in bundle.namelist())
    print(json.dumps({'archive': str(out), 'bytes': out.stat().st_size,
                      'sha256': digest(out.read_bytes()), 'git_commit': commit,
                      'jar_sha256': manifest['jar_sha256'], 'verified': True}, ensure_ascii=False))


if __name__ == '__main__':
    main()
