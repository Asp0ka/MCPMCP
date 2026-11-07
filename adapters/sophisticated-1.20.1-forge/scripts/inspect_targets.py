from pathlib import Path
import zipfile,hashlib,json,subprocess,re,tomllib
ROOT=Path(__file__).resolve().parents[1]
MODS=Path('D:/Games/mc/Cheat/versions/神秘启旅/mods')
JAVAP=Path('D:/Games/mc/mc-info-mcp/tooling/jdk17/jdk-17.0.21+9/bin/javap.exe')
e=ROOT/'evidence';e.mkdir(exist_ok=True)
jars={}
for mod in ['sophisticatedcore','sophisticatedstorage','sophisticatedbackpacks','ironfurnaces']:
 f=next(f for f in MODS.iterdir() if mod+'-' in f.name and f.suffix=='.jar');jars[mod]=f
 z=zipfile.ZipFile(f);(e/(mod+'-mods.toml')).write_bytes(z.read('META-INF/mods.toml'))
versions={m:next(x['version'] for x in tomllib.loads(zipfile.ZipFile(f).read('META-INF/mods.toml').decode())['mods'] if x['modId']==m) for m,f in jars.items()}
(e/'targets.json').write_text(json.dumps({m:{'path':str(f),'version':versions[m],'sha256':hashlib.sha256(f.read_bytes()).hexdigest()} for m,f in jars.items()},ensure_ascii=False,indent=2),encoding='utf-8')
classes={
'sophisticatedcore':['common.gui.StorageContainerMenuBase','common.gui.UpgradeContainerBase','upgrades.crafting.CraftingUpgradeContainer','network.PacketHandler','network.SyncContainerClientDataMessage','common.gui.SortBy'],
'sophisticatedbackpacks':['network.BackpackOpenMessage','network.SBPPacketHandler','common.gui.BackpackContainer','backpack.wrapper.BackpackWrapper','backpack.BackpackItem'],
'sophisticatedstorage':['common.gui.StorageContainerMenu','common.gui.LimitedBarrelContainerMenu'],
'ironfurnaces':['container.furnaces.BlockIronFurnaceContainerBase','container.slots.SlotIronFurnaceInput','container.slots.SlotIronFurnaceFuel','container.slots.SlotIronFurnaceInputGenerator','container.slots.SlotIronFurnaceInputFactory','container.slots.SlotIronFurnaceOutputFactory','tileentity.furnaces.BlockIronFurnaceTileBase']}
classes['ironfurnaces'] += ['container.furnaces.BlockIronFurnaceContainerBase$'+str(i) for i in range(1,25)]
for mod,cs in classes.items():
 for c in cs:
  name=('net.p3pp3rf1y.'+mod+'.' if mod!='ironfurnaces' else 'ironfurnaces.')+c
  r=subprocess.run([str(JAVAP),'-classpath',str(jars[mod]),'-p','-c',name],capture_output=True,text=True,encoding='utf-8')
  (e/(name+'.javap.txt')).write_text(r.stdout+r.stderr,encoding='utf-8')
  print(name,r.returncode)
  if r.returncode: raise RuntimeError('javap failed: '+name)
