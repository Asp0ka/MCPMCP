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
    from package_plugins import main as package_optional
    package_optional()
    plugin_packages = json.loads((DIST / 'plugin-packages.json').read_text(encoding='utf-8'))
    source = subprocess.check_output(['git', 'archive', '--format=zip', 'HEAD'], cwd=ROOT)
    with zipfile.ZipFile(io.BytesIO(source)) as tracked:
        for name in tracked.namelist():
            parts = name.lower().split('/')
            if any(x in {'.local', '.gradle', 'saves', 'config'} for x in parts) or name.endswith('mcpmcp.json'):
                raise ValueError('Forbidden tracked client data: ' + name)
            if name.endswith('.jar') and name != 'gradle/wrapper/gradle-wrapper.jar':
                if not name.startswith('adapters/') or not name.endswith('/adapter.jar'):
                    raise ValueError('Unexpected dependency JAR: ' + name)
                from plugin_release import validate_jar
                validate_jar(tracked.read(name))
    jar = JAR.read_bytes()
    manifest = {'name': 'MCP Minecraft Plus', 'version': VERSION, 'minecraft': '1.20.1',
                'forge': '47.4.21', 'git_commit': commit, 'jar_sha256': digest(jar),
                'sources_sha256': digest(source), 'requires': ['Java 17', 'Forge 47.4.21+'],
                'optional': ['Python 3.10+ for legacy stdio bridge only', 'compatible Baritone for movement', 'exact-version optional adapters'], 'jei_required': False,
                'plugin_packages': plugin_packages['bundles']}
    install = '''# MCPMCP 0.1.2 / Minecraft 1.20.1 Forge

1. Copy mods/mcpmcp-1.20.1-forge-0.1.2.jar into the intended instance mods folder.
2. Start that instance normally with Java 17 and compatible Forge. Baritone is optional for movement; JEI/REI are not required.
3. Connect an MCP Streamable HTTP client to the running instance endpoint http://127.0.0.1:<actual-port>/mcp with the local config token as Bearer authentication. Default ports: 5210 through 5220. See config/mcpmcp-runtime.json for the selected endpoint. Python is not required for direct HTTP; the optional legacy stdio bridge remains: python <absolute>/bridge/mcp_server.py --config <instance>/config/mcpmcp.json
4. Configure saved startup preferences in Mods > MCP Minecraft Plus > Config > MCP Server. Manual start/stop affects this game session only and never writes config. Load skills/mcpmcp/SKILL.md; read get_status/list_capabilities/describe_action before actions.
5. Optional adapters: exit the game first, select exact target versions/full hashes from the included plugin packages. The 14-plugin package targets the tested instance; the JEI-variants package contains two mutually exclusive alternatives, never install both. Read their INSTALL.md and docs/plugin-acceptance-progress.md.
6. Read docs/acceptance.md for verified scope and limits. Never copy the generated local token into chat or launch arguments.

sources.zip contains the exact committed source snapshot. The full Git repository remains at the development workspace. manifest.json and SHA256SUMS identify artifacts. No game, mod dependency, account credential, client config or save is bundled.
'''
    DIST.mkdir(exist_ok=True)
    final_out = DIST / f'MCPMCP-{VERSION}-1.20.1-forge.zip'
    out = final_out.with_suffix('.zip.new')
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as bundle:
        bundle.writestr('mods/' + JAR.name, jar)
        bundle.writestr('sources.zip', source)
        for optional in plugin_packages['bundles']:
            data = (DIST / optional['file']).read_bytes()
            if digest(data) != optional['sha256']:
                raise ValueError('Plugin package changed during packaging')
            bundle.writestr('optional/' + optional['file'], data)
        bundle.writestr('manifest.json', json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
        bundle.writestr('INSTALL.md', install)
        bundle.writestr('SHA256SUMS', digest(jar) + '  mods/' + JAR.name + '\n' + digest(source) + '  sources.zip\n' + ''.join(p['sha256'] + '  optional/' + p['file'] + '\n' for p in plugin_packages['bundles']))
        with zipfile.ZipFile(io.BytesIO(source)) as tracked:
            for name in tracked.namelist():
                if not name.endswith('/') and name.startswith(('bridge/', 'docs/', 'skills/', 'examples/')):
                    bundle.writestr(name, tracked.read(name))
        build_log = ROOT / 'docs/evidence/builtin-mcp-0.1.2/gradle-build.txt'
        if build_log.is_file():
            bundle.write(build_log, 'docs/evidence/build.log')
    with zipfile.ZipFile(out) as bundle:
        if bundle.testzip() is not None:
            raise SystemExit('Archive integrity failed')
        assert digest(bundle.read('mods/' + JAR.name)) == manifest['jar_sha256']
        assert digest(bundle.read('sources.zip')) == manifest['sources_sha256']
        for optional in plugin_packages['bundles']:
            assert digest(bundle.read('optional/' + optional['file'])) == optional['sha256']
        assert not any('mcpmcp.json' in n or '.gradle/' in n or '.local/' in n for n in bundle.namelist())
    out.replace(final_out)
    out = final_out
    print(json.dumps({'archive': str(out), 'bytes': out.stat().st_size,
                      'sha256': digest(out.read_bytes()), 'git_commit': commit,
                      'jar_sha256': manifest['jar_sha256'], 'verified': True}, ensure_ascii=False))


if __name__ == '__main__':
    main()
