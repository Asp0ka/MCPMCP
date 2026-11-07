"""Capture exact installed Forge + mod ownership evidence, without launching Minecraft."""
from pathlib import Path
import hashlib
import json
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
E = ROOT / 'evidence'
CACHE = Path('D:/Games/mc/mc-info-mcp/.gradle-user-home/caches/forge_gradle')
FORGE = Path('D:/Games/mc/Cheat/libraries/net/minecraftforge/forge/1.20.1-47.4.28/forge-1.20.1-47.4.28-universal.jar')
CACHED = CACHE / 'maven_downloader/net/minecraftforge/forge/1.20.1-47.4.28/forge-1.20.1-47.4.28-universal.jar'
SOURCES = CACHED.with_name('forge-1.20.1-47.4.28-sources.jar')
JAVAP = Path('D:/Games/mc/mc-info-mcp/tooling/jdk17/jdk-17.0.21+9/bin/javap.exe')
targets = json.loads((E / 'targets.json').read_text(encoding='utf-8'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
checks = 0

def check(condition, message):
    global checks
    checks += 1
    assert condition, message


def capture(jar, name):
    result = subprocess.run([str(JAVAP), '-classpath', str(jar), '-p', '-c', name], capture_output=True, text=True, encoding='utf-8', check=True)
    (E / (name + '.javap.txt')).write_text(result.stdout + result.stderr, encoding='utf-8')
    return result.stdout


check(sha(FORGE) == sha(CACHED), 'installed Forge matches compilation-cache universal JAR')
records = []
with zipfile.ZipFile(FORGE) as binary, zipfile.ZipFile(SOURCES) as sources:
    for name in ['net.minecraftforge.items.SlotItemHandler', 'net.minecraftforge.items.wrapper.InvWrapper']:
        entry = name.replace('.', '/')
        source = sources.read(entry + '.java').decode('utf-8')
        # Preserve source evidence under the adapter only.
        dest = E / 'forge-player-inventory-source' / (entry + '.java')
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(source, encoding='utf-8')
        text = capture(FORGE, name)
        if name.endswith('SlotItemHandler'):
            check('super(emptyInventory, index,' in source, 'slot container is dummy, not handler owner')
            check('public IItemHandler getItemHandler()' in source and 'return itemHandler;' in source, 'public accessor returns actual handler')
            check('private final int index;' in source and 'this.index = index;' in source, 'handler and container indexes are identical in exact concrete slot')
            check('getItemHandler().getStackInSlot(index)' in source, 'getItem follows handler index')
            check('public net.minecraftforge.items.IItemHandler getItemHandler();' in text, 'accessor present in installed bytecode')
        else:
            check('public Container getInv()' in source and 'return inv;' in source, 'public accessor returns wrapped container')
            check('getInv().m_8020_(slot)' in source, 'wrapper reads exact unshifted container index')
            check('public net.minecraft.world.Container getInv();' in text, 'container accessor present in installed bytecode')
        records.append({'class': name, 'class_sha256': hashlib.sha256(binary.read(entry + '.class')).hexdigest(), 'source': str(dest.relative_to(ROOT))})

furnace = capture(Path(targets['ironfurnaces']['path']), 'ironfurnaces.container.furnaces.BlockIronFurnaceContainerBase')
check('net/minecraftforge/items/wrapper/InvWrapper."<init>":(Lnet/minecraft/world/Container;)V' in furnace, 'furnace wraps constructor Inventory in InvWrapper')
check('Field playerInventory:Lnet/minecraftforge/items/IItemHandler;' in furnace, 'furnace stores exact handler')
check('class net/minecraftforge/items/SlotItemHandler' in furnace, 'native slot-range builder creates concrete Forge slots')
layout = furnace.split('private void layoutPlayerInventorySlots(int, int);')[1]
check(layout.count('Field playerInventory:') == 2 and layout.count('bipush        9') == 3 and 'iconst_3' in layout and 'iconst_0' in layout, 'native layout uses same handler for 9x3 main slots and 9 hotbar slots')
core = capture(Path(targets['sophisticatedcore']['path']), 'net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase')
check('private net.minecraft.world.inventory.Slot addStorageItemSafeSlot(net.minecraft.world.entity.player.Inventory, int, int, boolean);' in core, 'core passes Inventory directly to reviewed player-slot factory')
locked = capture(Path(targets['sophisticatedcore']['path']), 'net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase$2')
check('extends net.minecraft.world.inventory.Slot' in locked and 'public boolean m_8010_(net.minecraft.world.entity.player.Player);' in locked and 'iconst_0' in locked, 'native backpack-containing slot forbids pickup')
summary = {'status': 'PASS', 'checks': checks, 'installed_forge': str(FORGE), 'forge_sha256': sha(FORGE), 'cached_forge_sha256': sha(CACHED), 'sources': str(SOURCES), 'sources_sha256': sha(SOURCES), 'classes': records, 'mod_sha256': {m: t['sha256'] for m, t in targets.items()}, 'game_started': False}
(E / 'player-inventory-evidence.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(summary, ensure_ascii=False, indent=2))
