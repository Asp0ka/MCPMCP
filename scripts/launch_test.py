"""Launch only the explicitly configured dedicated acceptance instance (offline local world)."""
import argparse, pathlib, json, subprocess, os, zipfile, uuid, platform, sys
p=argparse.ArgumentParser();p.add_argument('--instance',required=True);p.add_argument('--java',required=True);a=p.parse_args()
instance=pathlib.Path(a.instance).resolve()
if instance.name!='1.20.1-Forge_47.4.28-test':raise SystemExit('Dedicated test instance required')
game=instance.parent.parent;cfg=json.loads((instance/(instance.name+'.json')).read_text(encoding='utf-8-sig'));lib=game/'libraries'
natives=instance/'.mcpmcp-test/natives';natives.mkdir(parents=True,exist_ok=True)
classpath=[]
def allowed(rules):
 result=not rules
 for r in rules or []:
  match=True
  osrule=r.get('os',{})
  if osrule.get('name')and osrule['name']!='windows':match=False
  if osrule.get('arch')and osrule['arch']not in('x86_64','amd64'):match=False
  if r.get('features'):match=False
  if match:result=r['action']=='allow'
 return result
missing=[]
for entry in cfg['libraries']:
 if not allowed(entry.get('rules')):continue
 artifact=entry.get('downloads',{}).get('artifact')
 if artifact:jar=lib/artifact['path']
 else:
  parts=entry['name'].split(':');group,name,version=parts[:3];classifier=parts[3]if len(parts)>3 else None
  jar=lib/group.replace('.','/')/name/version/(name+'-'+version+('-'+classifier if classifier else '')+'.jar')
 if not jar.is_file():missing.append(str(jar));continue
 classpath.append(str(jar))
 if entry.get('natives')and 'windows'in entry['natives']:
  classifier=entry['natives']['windows'].replace('${arch}','64');native=entry.get('downloads',{}).get('classifiers',{}).get(classifier)
  if native:
   archive=lib/native['path']
   if archive.is_file():
    with zipfile.ZipFile(archive)as z:
     for name in z.namelist():
      if name.lower().endswith('.dll'):
       (natives/pathlib.Path(name).name).write_bytes(z.read(name))
client=instance/(instance.name+'.jar');classpath.append(str(client))
if missing:raise SystemExit('Missing launch libraries: '+json.dumps(missing))
name='MCPMCPTester';offline_uuid=str(uuid.uuid3(uuid.NAMESPACE_DNS,'OfflinePlayer:'+name)).replace('-','')
variables={'auth_player_name':name,'version_name':instance.name,'game_directory':str(instance),'assets_root':str(game/'assets'),'assets_index_name':str(cfg['assetIndex']['id']),'auth_uuid':offline_uuid,'auth_access_token':'0','clientid':'','auth_xuid':'','user_type':'legacy','version_type':'MCPMCP acceptance','natives_directory':str(natives),'launcher_name':'MCPMCP-Test','launcher_version':'1','classpath':';'.join(classpath),'library_directory':str(lib),'classpath_separator':';','resolution_width':'960','resolution_height':'640'}
def expand(v):
 for k,value in variables.items():v=v.replace('${'+k+'}',value)
 if '${'in v:raise ValueError('Unresolved launch variable')
 return v
args=[]
for value in cfg['arguments']['jvm']:
 if isinstance(value,str):args.append(expand(value))
 elif allowed(value.get('rules')):
  values=value['value']if isinstance(value['value'],list)else[value['value']];args.extend(expand(x)for x in values)
args=['-Xms512M','-Xmx3G','-Dfile.encoding=UTF-8',*args,cfg['mainClass']]
for value in cfg['arguments']['game']:
 if isinstance(value,str):args.append(expand(value))
 elif allowed(value.get('rules')):
  values=value['value']if isinstance(value['value'],list)else[value['value']];args.extend(expand(x)for x in values)
args.extend(['--width','960','--height','640'])
log=instance/'.mcpmcp-test/launch.log';log.parent.mkdir(exist_ok=True)
with log.open('w',encoding='utf-8')as out:
 process=subprocess.Popen([a.java,*args],cwd=instance,stdout=out,stderr=subprocess.STDOUT)
 print(json.dumps({'pid':process.pid,'instance':str(instance),'log':str(log),'authentication':'offline local test only'}),flush=True)
 sys.exit(process.wait())
