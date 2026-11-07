"""Fixed-instance exact-set installer; --apply needs a fresh build receipt.
Freeze/stage all inputs, recheck before replacing known files, retain backups
and restore changed files on exceptions. User support files are retained.
"""
from pathlib import Path
import datetime,json,re,sys,tempfile,zipfile
from plugin_release import (ROOT,PACK,EXPECTED,catalog,select,mod_inventory,
    public_row,receipt_matches,contained,ensure_stopped,digest,require_ids)

def mod_meta(jar):
    import tomllib
    with zipfile.ZipFile(jar) as z:
        data=tomllib.loads(z.read('META-INF/mods.toml').decode('utf-8'))
        mf=z.read('META-INF/MANIFEST.MF').decode('utf-8','replace') if 'META-INF/MANIFEST.MF' in z.namelist() else ''
    impl=re.search(r'^Implementation-Version:\s*(\S+)',mf,re.M)
    return [(b['modId'],impl.group(1) if b.get('version')=='${file.jarVersion}' and impl else str(b.get('version','')))
            for b in data.get('mods',[]) if b.get('modId')]

def plan_install(root=ROOT,pack=PACK):
    contained(pack,pack/'mods');contained(pack,pack/'MCPMCP-Plugin')
    rows=select(catalog(root),mod_inventory(pack));require_ids(rows)
    folders={r['canonical'] for r in rows};installed=pack/'MCPMCP-Plugin'
    if installed.exists():
        for d in installed.iterdir():
            contained(pack,d);mf=d/'plugin.json'
            if mf.is_file() and json.loads(mf.read_bytes()).get('id') in EXPECTED and d.name not in folders:
                raise ValueError('Plugin ID in noncanonical folder: '+str(d))
    for row in rows:
        for name in ('adapter.jar','plugin.json','GUIDE.md'):contained(pack,installed/row['canonical']/name)
    return rows

def files_for(row):
    return {n:row[k].read_bytes() for n,k in [('plugin.json','manifest_path'),('adapter.jar','jar_path'),('GUIDE.md','guide_path')]}

def verify_frozen(rows,frozen,pack):
    fresh=plan_install(pack=pack)
    if [r['canonical'] for r in fresh]!=[r['canonical'] for r in rows]:raise ValueError('Target changed during staging')
    for row in fresh:
        if files_for(row)!=frozen[row['canonical']]:raise ValueError('Source changed during staging')
    receipt_matches(catalog())

def apply_install(rows,pack=PACK):
    ensure_stopped(pack);receipt_matches(catalog())
    frozen={r['canonical']:files_for(r) for r in rows}
    local=ROOT/'.local';local.mkdir(exist_ok=True)
    backup=local/'plugin-backups'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    changed=[];previous={}
    with tempfile.TemporaryDirectory(prefix='plugin-stage-',dir=local) as tmp:
        stage=Path(tmp)
        for folder,files in frozen.items():
            for name,data in files.items():
                p=stage/folder/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
                if p.read_bytes()!=data:raise ValueError('Staging mismatch')
        verify_frozen(rows,frozen,pack);ensure_stopped(pack)
        for folder,files in frozen.items():
            for name in files:
                target=contained(pack,pack/'MCPMCP-Plugin'/folder/name)
                staged_target=contained(pack,target.with_name(name+'.new'))
                if staged_target.exists():raise ValueError('Stale staging file must be recovered first: '+str(staged_target))
                previous[target]=target.read_bytes() if target.exists() else None
                if previous[target] is not None:
                    saved=backup/folder/name;saved.parent.mkdir(parents=True,exist_ok=True);saved.write_bytes(previous[target])
        try:
            for folder,files in frozen.items():
                for name,data in files.items():
                    target=contained(pack,pack/'MCPMCP-Plugin'/folder/name);target.parent.mkdir(parents=True,exist_ok=True)
                    temp=contained(pack,target.with_name(name+'.new'))
                    temp.write_bytes((stage/folder/name).read_bytes());temp.replace(target);changed.append(target)
                    if target.read_bytes()!=data:raise ValueError('Installed mismatch')
        except BaseException:
            for target in reversed(changed):
                contained(pack,target)
                if previous[target] is None:target.unlink(missing_ok=True)
                else:target.write_bytes(previous[target])
            raise
        finally:
            for folder,files in frozen.items():
                for name in files:
                    temp=contained(pack,pack/'MCPMCP-Plugin'/folder/(name+'.new'))
                    temp.unlink(missing_ok=True)
    out={'instance':str(pack),'build_receipt_sha256':digest((ROOT/'.local/plugin-build-receipt.json').read_bytes()),'backup':str(backup),
         'recovery':'Close game, restore backed-up known files into matching folders. Replacement exceptions automatically restore changed files.',
         'artifacts':[dict(public_row(r),installed_adapter_sha256=digest((pack/'MCPMCP-Plugin'/r['canonical']/'adapter.jar').read_bytes())) for r in rows]}
    (local/'plugin-install-receipt.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+chr(10),encoding='utf-8')
    return out

def main():
    rows=plan_install();result={'instance':str(PACK),'count':len(rows),'artifacts':[public_row(r) for r in rows]}
    if '--apply' in sys.argv:result=apply_install(rows)
    (ROOT/'.local').mkdir(exist_ok=True)
    (ROOT/'.local/plugin-install-plan.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+chr(10),encoding='utf-8')
    print(json.dumps({'count':len(rows),'apply':'--apply' in sys.argv,'verified':True}))

if __name__=='__main__':main()
