"""Package verified current builds, installed hashes and acceptance provenance.
Creates the fixed-instance 13-plugin bundle and a separate mutually exclusive
JEI-variant bundle. Requires committed source and matching build/install receipts.
"""
from pathlib import Path
import json,subprocess
from plugin_release import (ROOT,PACK,catalog,select,mod_inventory,receipt_matches,
    require_ids,digest,public_row,write_archive,contained)

def clean_commit():
    if subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True).strip():
        raise ValueError('Commit source/evidence changes before packaging')
    return subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()

def combine_acceptance(rows,baseline,curios):
    # Prior binary results remain valid only if every baseline artifact is unchanged.
    baseline_required={'all_plugins_loaded','policy_restored','empty_cursor_before_leave','no_recovery','iron_effect','travel_effect','furnace_transfer','storage_roundtrip','backpack_contract','nbt_stale_native_rejected','nbt_stale_packet_rejected','jei_focus_preserved'}
    curios_required={'all_plugins_loaded','read_slots','equip','unequip','swap','cosmetic','render','stale_identity_rejected','invalid_slot_rejected','cursor_rejected','menu_preserved','closed_state_restored','policy_restored','empty_cursor','no_recovery'}
    for qa,keys in [(baseline,baseline_required),(curios,curios_required)]:
        if not qa.get('verified') or not keys.issubset(qa.get('checks',{})) or not all(qa['checks'].get(k) is True for k in keys):raise ValueError('Missing/failed acceptance checks: '+qa.get('batch','unknown'))
    expected={r['id']:r['adapter_sha256'] for r in rows}
    if curios.get('installed_hashes')!=expected:raise ValueError('Curios acceptance/final binary mismatch')
    old={k:v for k,v in expected.items() if k!='mcpmcp.curios'}
    if baseline.get('installed_hashes')!=old:raise ValueError('Baseline binary changed; rerun its acceptance')
    return dict(curios,baseline_batch=baseline['batch'],baseline_checks=baseline['checks'])


def inventory():
    allrows=catalog();build=receipt_matches(allrows)
    rows=select(allrows,mod_inventory());require_ids(rows)
    installed=json.loads((ROOT/'.local/plugin-install-receipt.json').read_bytes())
    if installed['build_receipt_sha256']!=digest((ROOT/'.local/plugin-build-receipt.json').read_bytes()):raise ValueError('Install/build receipt mismatch')
    byid={r['id']:r for r in installed['artifacts']}
    require_ids(installed['artifacts'])
    for row in rows:
        for name,pathkey in [('adapter.jar','jar_path'),('plugin.json','manifest_path'),('GUIDE.md','guide_path')]:
            p=contained(PACK,PACK/'MCPMCP-Plugin'/row['canonical']/name)
            if not p.is_file() or p.read_bytes()!=row[pathkey].read_bytes():raise ValueError('Installed file does not match current build/guide: '+row['id']+' '+name)
        if byid[row['id']]['installed_adapter_sha256']!=row['adapter_sha256']:raise ValueError('Install receipt drift')
    baseline=json.loads((ROOT/'docs/evidence/plugin-acceptance-0.1.2/final-delta.json').read_bytes())
    curios=json.loads((ROOT/'docs/evidence/curios-0.1.2/acceptance.json').read_bytes())
    qa=combine_acceptance(rows,baseline,curios)
    return rows,allrows,build,qa

def bundle(out,rows,commit,kind,qa):
    contents={}
    if len({r['canonical'] for r in rows})!=len(rows):raise ValueError('Duplicate ZIP folders')
    for row in rows:
        prefix='MCPMCP-Plugin/'+row['canonical']+'/'
        for name,key in [('plugin.json','manifest_path'),('adapter.jar','jar_path'),('GUIDE.md','guide_path')]:contents[prefix+name]=row[key].read_bytes()
    metadata={'git_commit':commit,'kind':kind,'minecraft':'1.20.1','tested_forge':'47.4.21',
       'artifacts':[public_row(r) for r in rows],'acceptance_batch':qa['batch'],
       'build_receipt_sha256':digest((ROOT/'.local/plugin-build-receipt.json').read_bytes()),
       'install_receipt_sha256':digest((ROOT/'.local/plugin-install-receipt.json').read_bytes()),
       'acceptance_sha256':digest((ROOT/'docs/evidence/curios-0.1.2/acceptance.json').read_bytes()),
       'baseline_acceptance_sha256':digest((ROOT/'docs/evidence/plugin-acceptance-0.1.2/final-delta.json').read_bytes()),
       'files':{n:digest(b) for n,b in contents.items()}}
    contents['manifest.json']=(json.dumps(metadata,ensure_ascii=False,indent=2)+chr(10)).encode()
    contents['INSTALL.md']=("""# MCPMCP optional adapters

1. Exit the intended game before copying cold-loaded Java adapters. Keep backups.
2. Match each target mod's exact full SHA-256 and version in plugin.json. Copy only
   matching folders into the intended instance's MCPMCP-Plugin directory. Never put
   adapter.jar in mods; do not install two variants with the same plugin ID.
3. The main plugin bundle contains the 13 targets used by 神秘启旅. The JEI variants
   bundle contains two alternatives: choose exactly one matching the installed JEI.
   Do not install that alternatives bundle wholesale.
4. Review code before locally enabling trusted Java plugins, then restart. No game
   mods, client configuration, credentials or saves are included. See main package
   docs/plugin-acceptance-progress.md for passed scenarios and unverified scope.
""").encode('utf-8')
    write_archive(out,contents)
    return {'file':out.name,'sha256':digest(out.read_bytes()),'adapters':len(rows)}

def main():
    commit=clean_commit();rows,allrows,build,qa=inventory()
    main=bundle(ROOT/'dist/MCPMCP-Plugins-1.20.1-forge.zip',rows,commit,'tested-instance',qa)
    variants=bundle(ROOT/'dist/MCPMCP-JEI-Variants-1.20.1-forge.zip',[r for r in allrows if r['mod_id']=='jei'],commit,'choose-one-JEI-variant',qa)
    result={'git_commit':commit,'bundles':[main,variants],'verified':True}
    (ROOT/'dist/plugin-packages.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+chr(10),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False))

if __name__=='__main__':main()
