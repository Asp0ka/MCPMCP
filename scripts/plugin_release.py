"""Shared, fail-closed contracts for the fixed-instance adapter release."""
from pathlib import Path
import hashlib
import io
import json
import os
import re
import stat
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACK = Path('D:/Games/mc/Cheat/versions/神秘启旅')
EXPECTED = {'mcpmcp.'+x for x in ('curios','ars_nouveau','avaritia','botania','ironfurnaces','irons_spellbooks','jei','malum','refinedstorage','sophisticatedbackpacks','sophisticatedstorage','traveloptics','wizards_reborn')}
# Explicit current reobfuscated builds, never a search for any matching old JAR.
SPECS = [
 ('curios','curios-1.20.1-forge','distribution/plugin.json','build/libs/adapter.jar'),
 ('jei','jei-1.20.1-forge','distribution/plugin.json','build/libs/adapter.jar'),
 ('jei','jei-legacy-1.20.1-forge','distribution/plugin.json','build/libs/adapter.jar'),
 ('refinedstorage','refinedstorage-1.20.1-forge','distribution/plugin.json','build/libs/adapter.jar'),
 ('malum','malum-wizards-1.20.1-forge','distribution/malum/plugin.json','malum/build/libs/adapter.jar'),
 ('wizards_reborn','malum-wizards-1.20.1-forge','distribution/wizards_reborn/plugin.json','wizards_reborn/build/libs/adapter.jar'),
 ('botania','botania-avaritia-1.20.1-forge','dist/MCPMCP-Plugin/botania-*/plugin.json','botania/build/libs/adapter.jar'),
 ('avaritia','botania-avaritia-1.20.1-forge','dist/MCPMCP-Plugin/avaritia-*/plugin.json','avaritia/build/libs/adapter.jar'),
 ('sophisticatedstorage','sophisticated-1.20.1-forge','distribution/sophisticatedstorage-*/manifest.json','build/libs/storage-adapter.jar'),
 ('sophisticatedbackpacks','sophisticated-1.20.1-forge','distribution/sophisticatedbackpacks-*/manifest.json','build/libs/backpack-adapter.jar'),
 ('ironfurnaces','sophisticated-1.20.1-forge','distribution/ironfurnaces-*/manifest.json','build/libs/furnace-adapter.jar'),
 ('ars_nouveau','ars-irons-1.20.1-forge','distribution/ars_nouveau-*/plugin.json','build/libs/ars_nouveau-adapter.jar'),
 ('irons_spellbooks','ars-irons-1.20.1-forge','distribution/irons_spellbooks-*/plugin.json','build/libs/irons_spellbooks-adapter.jar'),
 ('traveloptics','ars-irons-1.20.1-forge','distribution/traveloptics-*/plugin.json','build/libs/traveloptics-adapter.jar'),
]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def require_ids(rows, expected=EXPECTED):
    ids=[r['id'] for r in rows]
    if len(ids)!=len(set(ids)) or set(ids)!=set(expected):
        raise ValueError('Exact plugin set required: missing='+str(sorted(set(expected)-set(ids)))+' duplicate/unexpected='+str(ids))


def source_hash(root):
    excluded={'build','.gradle','distribution','dist','stage','evidence','__pycache__','.git'}
    h=hashlib.sha256()
    for p in sorted(Path(root).rglob('*')):
        rel=p.relative_to(root)
        if not p.is_file() or excluded.intersection(rel.parts) or p.suffix not in {'.java','.gradle','.py','.json','.properties'}:
            continue
        if p.suffix=='.json' and not p.name.startswith('targets'):
            continue
        h.update(rel.as_posix().encode());h.update(b'\0');h.update(p.read_bytes());h.update(b'\0')
    return h.hexdigest()


def contained(root, path):
    root=Path(os.path.abspath(root));path=Path(os.path.abspath(path))
    if not path.is_relative_to(root):
        raise ValueError('Path escapes fixed root: '+str(path))
    for p in [root,*root.parents]:
        if p.exists() and (p.is_symlink() or getattr(p,'is_junction',lambda:False)() or getattr(p.lstat(),'st_file_attributes',0) & getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',1024)):
            raise ValueError('Linked root/ancestor: '+str(p))
    current=root
    for part in path.relative_to(root).parts:
        current=current/part
        if current.exists() or current.is_symlink():
            attr=getattr(current.lstat(),'st_file_attributes',0)
            if current.is_symlink() or getattr(current,'is_junction',lambda:False)() or attr & getattr(stat,'FILE_ATTRIBUTE_REPARSE_POINT',1024):
                raise ValueError('Linked/reparse target: '+str(current))
    if not path.resolve().is_relative_to(root.resolve()):
        raise ValueError('Resolved path escapes root')
    return path.resolve()


def process_targets(cmd, cwd, pack):
    values=[]
    if any(arg.startswith('@') for arg in cmd):raise ValueError('Cannot exclude Java argument-file gameDir')
    for i,arg in enumerate(cmd):
        if arg=='--gameDir':
            if i+1>=len(cmd):raise ValueError('Unparseable Java gameDir')
            values.append(cmd[i+1])
        elif arg.startswith('--gameDir='):values.append(arg.split('=',1)[1])
    if len(values)>1:raise ValueError('Ambiguous gameDir')
    if values:
        if not values[0].strip():raise ValueError('Empty Java gameDir')
        p=Path(values[0]);p=p if p.is_absolute() else Path(cwd)/p
        return p.resolve()==Path(pack).resolve()
    return Path(cwd).resolve().is_relative_to(Path(pack).resolve())


def ensure_stopped(pack=PACK):
    import psutil
    for p in psutil.process_iter():
        try:
            name=p.name().lower()
            if 'java' not in name:continue
            cmd=p.cmdline();cwd=p.cwd()
            if process_targets(cmd,cwd,pack):raise ValueError('Exit target game first: PID '+str(p.pid))
            # An unidentified Java process cannot be safely excluded.
            if not any(x=='--gameDir' or x.startswith('--gameDir=') for x in cmd) and not cmd:
                raise ValueError('Cannot identify Java process: '+str(p.pid))
        except psutil.NoSuchProcess:
            continue
        except psutil.AccessDenied as e:
            raise ValueError('Cannot exclude protected process from target instance') from e


def safe_name(name):
    if '\\' in name or ':' in name or name.startswith('/') or '..' in name.split('/'):
        raise ValueError('Unsafe archive path: '+name)


def validate_jar(data):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        if z.testzip() is not None:raise ValueError('Corrupt adapter JAR')
        names=z.namelist()
        if len(names)!=len(set(names)):raise ValueError('Duplicate JAR entries')
        for n in names:
            safe_name(n)
            if n.lower().endswith('.jar'):raise ValueError('Nested runtime dependency JAR')
        service=z.read('META-INF/services/dev.mcpmcp.api.McpmcpPlugin').decode('utf-8')
        providers=[x.split('#',1)[0].strip() for x in service.splitlines()]
        providers=[x for x in providers if x]
        if len(providers)!=1 or not re.fullmatch(r'dev\.mcpmcp\.[A-Za-z0-9_.$]+',providers[0]):raise ValueError('Expected one adapter provider')
        if providers[0].replace('.','/')+'.class' not in names:raise ValueError('Provider class missing')
        packages=[providers[0].rsplit('.',1)[0].replace('.','/')+'/']
        if providers[0] in {'dev.mcpmcp.malum.MalumProvider','dev.mcpmcp.wizards.WizardsProvider','dev.mcpmcp.wizards_reborn.WizardsProvider'}:packages.append('dev/mcpmcp/magic/')
        if any(n.endswith('.class') and not n.startswith(tuple(packages)) for n in names):raise ValueError('Class outside adapter packages')
        if any(n.endswith('.class') and (not n.startswith('dev/mcpmcp/') or n.startswith(('dev/mcpmcp/api/','dev/mcpmcp/core/'))) for n in names):
            raise ValueError('Bundled runtime dependency class')
        return providers[0]


def write_archive(out, contents):
    for name in contents:safe_name(name)
    out=Path(out);out.parent.mkdir(parents=True,exist_ok=True);tmp=out.with_suffix(out.suffix+'.new')
    try:
        with zipfile.ZipFile(tmp,'w',zipfile.ZIP_DEFLATED) as z:
            for n,b in sorted(contents.items()):z.writestr(n,b)
        with zipfile.ZipFile(tmp) as z:
            if z.testzip() is not None or set(z.namelist())!=set(contents):raise ValueError('ZIP verification failed')
            for n,b in contents.items():
                if digest(z.read(n))!=digest(b):raise ValueError('ZIP hash mismatch')
        tmp.replace(out)
    finally:
        tmp.unlink(missing_ok=True)


def catalog(root=ROOT):
    rows=[]
    for mod,project,pattern,build in SPECS:
        base=root/'adapters'/project;matches=list(base.glob(pattern))
        if len(matches)!=1:raise ValueError('Missing/ambiguous manifest: '+project+'/'+pattern)
        mf=matches[0];contained(root,mf);data=mf.read_bytes();m=json.loads(data)
        if m.get('id')!='mcpmcp.'+mod or m.get('mod_id')!=mod or m.get('api')!=1 or m.get('minecraft')!='1.20.1' or m.get('loader')!='forge' or not m.get('target_version') or not re.fullmatch('[0-9a-f]{64}',m.get('target_sha256','')):
            raise ValueError('Invalid manifest: '+str(mf))
        jar=base/build;contained(root,jar)
        if not jar.is_file():raise ValueError('Missing current build: '+str(jar))
        provider=validate_jar(jar.read_bytes())
        distributed=mf.parent/'adapter.jar'
        if mod!='jei' and (not distributed.is_file() or digest(distributed.read_bytes())!=digest(jar.read_bytes())):
            raise ValueError('Distribution differs from explicit current build: '+mod)
        guide=mf.parent/'GUIDE.md'
        if not guide.is_file():guide=base/'GUIDE.md'
        if not guide.is_file():raise ValueError('Missing required GUIDE: '+str(base))
        contained(root,guide)
        rows.append({'id':m['id'],'mod_id':mod,'project':project,'canonical':mod+'-'+m['target_sha256'][:32],
                     'manifest':m,'manifest_path':mf,'jar_path':jar,'guide_path':guide,
                     'adapter_sha256':digest(jar.read_bytes()),'manifest_sha256':digest(data),'guide_sha256':digest(guide.read_bytes()),'provider':provider,
                     'source_sha256':source_hash(base)})
    if len({x['canonical'] for x in rows})!=len(rows):raise ValueError('Canonical plugin folders collide')
    return rows


def mod_inventory(pack=PACK):
    from install_plugins import mod_meta
    result={}
    for p in sorted((pack/'mods').glob('*.jar')):
        contained(pack,p)
        try:entries=mod_meta(p)
        except (KeyError,zipfile.BadZipFile,UnicodeDecodeError):continue
        for mod,version in entries:
            result.setdefault(mod,[]).append({'path':p,'version':version,'sha256':digest(p.read_bytes())})
    return result


def select(rows, mods):
    chosen=[]
    for pid in sorted(EXPECTED):
        variants=[r for r in rows if r['id']==pid]
        if not variants:raise ValueError('Missing plugin: '+pid)
        found=mods.get(variants[0]['mod_id'],[])
        if len(found)!=1:raise ValueError('Missing/ambiguous target mod: '+pid)
        match=[r for r in variants if r['manifest']['target_version']==found[0]['version'] and r['manifest']['target_sha256']==found[0]['sha256']]
        if len(match)!=1:raise ValueError('No exact version/hash variant: '+pid)
        chosen.append(dict(match[0],mod_path=found[0]['path']))
    require_ids(chosen)
    return chosen


def public_row(row):
    out={k:v for k,v in row.items() if not isinstance(v,Path)}
    for k,v in row.items():
        if isinstance(v,Path):out[k]=v.relative_to(ROOT).as_posix() if v.is_relative_to(ROOT) else str(v)
    return out


def receipt_matches(rows, root=ROOT):
    receipt=json.loads((root/'.local/plugin-build-receipt.json').read_text())
    keyed={r['canonical']:r for r in receipt['artifacts']}
    if set(keyed)!={r['canonical'] for r in rows}:raise ValueError('Build receipt set mismatch')
    for row in rows:
        old=keyed[row['canonical']]
        for key in ('adapter_sha256','source_sha256','manifest_sha256','guide_sha256'):
            if old[key]!=row[key]:raise ValueError('Build receipt drift: '+row['id']+' '+key)
    return receipt


def check_untouched(rows,previous,selected):
    old={r['canonical']:r for r in previous['artifacts']}
    for row in rows:
        if row['project'] in selected:continue
        prior=old.get(row['canonical'])
        if prior is None:raise ValueError('No prior build receipt for untouched project: '+row['project'])
        for key in ('adapter_sha256','source_sha256','manifest_sha256','guide_sha256'):
            if prior[key]!=row[key]:raise ValueError('Untouched project drift: '+row['id']+' '+key)


def build_all(projects=None):
    """Build all projects, or selected projects with strict unchanged receipts."""
    java=Path(os.environ.get('JAVA_HOME','D:/Games/mc/mc-info-mcp/tooling/jdk17/jdk-17.0.21+9'))
    gradle=Path('D:/Games/mc/mc-info-mcp/tooling/gradle-8.8/bin/gradle.bat')
    env=dict(os.environ,JAVA_HOME=str(java),GRADLE_USER_HOME='D:/Games/mc/mc-info-mcp/.gradle-user-home',JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8')
    known={s[1] for s in SPECS};selected=set(projects or known)
    if not selected.issubset(known):raise ValueError('Unknown build project')
    previous=json.loads((ROOT/'.local/plugin-build-receipt.json').read_bytes()) if projects else None
    logs=dict(previous.get('build_logs_sha256',{})) if previous else {}
    before={p:source_hash(ROOT/'adapters'/p) for p in selected}
    for project in sorted(before):
        log=ROOT/'.local'/('release-build-'+project+'.log')
        extra={'ars-irons-1.20.1-forge':['stageDistribution'], 'sophisticated-1.20.1-forge':[], 'botania-avaritia-1.20.1-forge':['packageAdapters'], 'malum-wizards-1.20.1-forge':['stageDistribution']}.get(project,[])
        cmd=[str(gradle),'-p',str(ROOT/'adapters'/project),'clean','build',*extra,'--offline','-Dnet.minecraftforge.gradle.check.certs=false','--console=plain']
        with log.open('wb') as stream:
            result=subprocess.run(cmd,cwd=ROOT,env=env,stdout=stream,stderr=subprocess.STDOUT)
        if result.returncode:raise ValueError('Build failed; see '+str(log))
        if project=='sophisticated-1.20.1-forge':
            with log.open('ab') as stream:
                staged=subprocess.run([sys.executable,'scripts/package_distribution.py'],cwd=ROOT/'adapters'/project,env=env,stdout=stream,stderr=subprocess.STDOUT)
            if staged.returncode:raise ValueError('Distribution refresh failed: '+project)
            with log.open('ab') as stream:
                checked=subprocess.run([str(gradle),'-p',str(ROOT/'adapters'/project),'distributionTest','--offline','-Dnet.minecraftforge.gradle.check.certs=false','--console=plain'],cwd=ROOT,env=env,stdout=stream,stderr=subprocess.STDOUT)
            if checked.returncode:raise ValueError('Distribution verification failed: '+project)
        logs[project]=digest(log.read_bytes())
        if source_hash(ROOT/'adapters'/project)!=before[project]:raise ValueError('Source changed during build: '+project)
        print('Built '+project,flush=True)
    rows=catalog()
    if previous:check_untouched(rows,previous,selected)
    receipt={'source_base_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
           'source_identity':'per-project SHA256 of source/build definitions; excludes generated outputs',
           'build_logs_sha256':logs,'artifacts':[public_row(r) for r in rows],
           'rebuilt_projects':sorted(selected),'untouched_projects_verified_against_prior_receipt':bool(previous),
           'prior_receipt_sha256':digest((ROOT/'.local/plugin-build-receipt.json').read_bytes()) if previous else None}
    (ROOT/'.local/plugin-build-receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'built_variants':len(rows),'receipt':'.local/plugin-build-receipt.json'}))

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('--project',action='append');args=parser.parse_args()
    build_all(args.project)
