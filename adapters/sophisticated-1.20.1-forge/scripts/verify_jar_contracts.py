from pathlib import Path
import json, hashlib, re

ROOT = Path(__file__).resolve().parents[1]
E = ROOT/'evidence'
checks = 0
def check(value, message):
    global checks
    checks += 1
    assert value, message
def evidence(name):
    return (E/(name+'.javap.txt')).read_text(encoding='utf-8')
def method(text, name):
    match = re.search(r'^  (?:public|protected|private).*? '+re.escape(name)+r'\([^\n]*\);\n(.*?)(?=^  (?:public|protected|private)|\Z)', text, re.M|re.S)
    assert match, 'missing bytecode method '+name
    return match.group(1)

targets = json.loads((E/'targets.json').read_text(encoding='utf-8'))
for mod, target in targets.items():
    check(hashlib.sha256(Path(target['path']).read_bytes()).hexdigest()==target['sha256'], 'unchanged exact jar '+mod)
core = evidence('net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase')
for name in ['getTotalSlotsNumber','getUpgradeContainers','getOpenContainer','getNumberOfStorageInventorySlots','getNumberOfUpgradeSlots','getUpgradeEnabled','isUpgradeRunnable','getSortBy','isInaccessibleSlot','isStorageInventorySlot']:
    check(' '+name+'(' in core, 'core method '+name)
for name in ['sort','setOpenTabId']:
    body = method(core,name)
    check('isClientSide:' in body and 'sendToServer:' in body, 'native client C2S route '+name)
check('upgradeSlots:' in method(core,'getTotalSlotsNumber'), 'extra slots included in total')
check('upgradeSlots:' in method(core,'m_38853_'), 'native getSlot resolves separate upgrade list')
check('extends net.p3pp3rf1y.sophisticatedstorage.common.gui.StorageContainerMenu' in evidence('net.p3pp3rf1y.sophisticatedstorage.common.gui.LimitedBarrelContainerMenu'), 'limited barrel inherits supported menu')
backpack = evidence('net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackOpenMessage')
check('public net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackOpenMessage(int);' in backpack, 'verified backpack inventory-slot constructor')
check('extends net.p3pp3rf1y.sophisticatedcore.network.PacketHandler' in evidence('net.p3pp3rf1y.sophisticatedbackpacks.network.SBPPacketHandler'), 'backpack handler inherits reviewed sendToServer')
check('public <M> void sendToServer(M);' in evidence('net.p3pp3rf1y.sophisticatedcore.network.PacketHandler'), 'exact send signature')
furnace = evidence('ironfurnaces.container.furnaces.BlockIronFurnaceContainerBase')
source = (ROOT/'src/main/java/dev/mcpmcp/sophisticated/LiveAccess.java').read_text(encoding='utf-8')
getter_segment = source.split('for(String method:List.of(')[1].split('))data.add')[0]
for name in re.findall(r'"([^"]+)"', getter_segment):
    check(' '+name+'();' in furnace, 'native furnace getter '+name)
check('protected ironfurnaces.tileentity.furnaces.BlockIronFurnaceTileBase te;' in furnace, 'exact read-only tile field')
tile = evidence('ironfurnaces.tileentity.furnaces.BlockIronFurnaceTileBase')
field_segment = source.split('for(String name:List.of(')[1].split('))raw.add')[0]
for name in re.findall(r'"([^"]+)"', field_segment):
    check(re.search(r'^  public [^\n]+ '+re.escape(name)+r';$',tile,re.M), 'public native tile field '+name)
for name in ['furnaceBurnTime','cookTime','totalCookTime','recipesUsed','generatorBurn','factoryCookTime','factoryTotalCookTime']:
    check(any(name+':' in evidence('ironfurnaces.container.furnaces.BlockIronFurnaceContainerBase$'+str(i)) for i in range(1,25)), 'raw field is used by native synchronized data slots: '+name)
check('isItemFuel:' in evidence('ironfurnaces.container.slots.SlotIronFurnaceFuel'), 'native ordinary fuel predicate')
generator = evidence('ironfurnaces.container.slots.SlotIronFurnaceInputGenerator')
check('hasGeneratorBlastingRecipe:' in generator and 'getSmokingBurn:' in generator and 'isItemFuel:' in generator, 'generator fuels use augment-dependent native rules')
for unsafe in ['getServer(', 'Files.write', '.setEnergy(', '.setItem(', 'setSortBy', 'setUpgradeEnabled', '.setScreen(', 'closeContainer(', 'context.execute(', 'context.observe(']:
    check(unsafe not in source, 'no unrequested mutation or recursive delegation '+unsafe)
print('PASS verify_jar_contracts checks='+str(checks)+' real_mod_jars='+str(len(targets)))
