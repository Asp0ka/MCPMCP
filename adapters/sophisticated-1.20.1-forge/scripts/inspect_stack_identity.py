"""Verify full saved-stack guards in the actual reobfuscated delivery JARs, offline."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
E = ROOT / 'evidence'
JAVAP = Path('D:/Games/mc/mc-info-mcp/tooling/jdk17/jdk-17.0.21+9/bin/javap.exe')
MAPPED = Path('D:/Games/mc/mc-info-mcp/.gradle-user-home/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.28_mapped_official_1.20.1/forge-1.20.1-47.4.28_mapped_official_1.20.1.jar')
checks = 0

def check(value, label):
    global checks
    checks += 1
    assert value, label

def capture(jar, name, filename):
    result = subprocess.run([str(JAVAP), '-classpath', str(jar), '-p', '-c', name], capture_output=True, text=True, encoding='utf-8', check=True)
    (E / filename).write_text(result.stdout + result.stderr, encoding='utf-8')
    return result.stdout

def body(text, signature, following):
    return text.split(signature, 1)[1].split(following, 1)[0]

stack = capture(MAPPED, 'net.minecraft.world.item.ItemStack', 'mapped-ItemStack.javap.txt')
save = body(stack, 'public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag);', 'public int getMaxStackSize();')
check('serializeCaps:' in save and 'String ForgeCaps' in save and 'String tag' in save and 'String Count' in save and 'String id' in save, 'actual Forge ItemStack.save includes item/count/tag/serialized capabilities')

artifacts = json.loads((E / 'distribution.json').read_text(encoding='utf-8'))
shared_classes = {}
for artifact in artifacts:
    jar = ROOT / artifact['path'] / 'adapter.jar'
    with zipfile.ZipFile(jar) as z:
        for name in ['LiveAccess', 'StackIdentity', 'Contract']:
            data = z.read('dev/mcpmcp/sophisticated/' + name + '.class')
            check(shared_classes.setdefault(name, data) == data, 'same identity guard and contract in every independent JAR')
        identity = z.read('dev/mcpmcp/sophisticated/StackIdentity.class')
        check(b'm_41739_' in identity and b'SHA-256' in identity and b'TreeSet' in identity, 'shipped full save plus canonical compound keys and SHA-256')
        check(b'expected_stack_hash' in z.read('dev/mcpmcp/sophisticated/Contract.class'), 'shipped contract contains required fingerprint')
    check(hashlib.sha256(jar.read_bytes()).hexdigest() == artifact['adapter_sha256'], 'distribution evidence hash matches current JAR')

jar = ROOT / artifacts[0]['path'] / 'adapter.jar'
live = capture(jar, 'dev.mcpmcp.sophisticated.LiveAccess', 'adapter-live.javap.txt')
identity = capture(jar, 'dev.mcpmcp.sophisticated.StackIdentity', 'adapter-stack-identity.javap.txt')
execute = body(live, 'public com.google.gson.JsonObject execute(java.lang.String, com.google.gson.JsonObject) throws java.lang.Exception;', 'com.google.gson.JsonObject quickMove(')
check(execute.index('Method guarded:') < execute.index('Method quickMove:'), 'world/menu identity/empty cursor guard precedes shared quick-move execution')
menu_guard = body(live, 'private net.minecraft.world.inventory.AbstractContainerMenu guarded(', 'public com.google.gson.JsonObject observe(')
check('MenuGuard.check:' in menu_guard and 'target_mod_menu_required' in menu_guard and 'menu_no_longer_valid' in menu_guard, 'menu identity/empty cursor/target ownership/stillValid remain mandatory')
quick = body(live, 'com.google.gson.JsonObject quickMove(', 'static void checkItem(')
check(quick.index('Method checkItem:') < quick.index('Method dev/mcpmcp/sophisticated/LiveAccess$QuickMoveSender.submit:'), 'both route dispatches occur strictly after full stack check')
check('Method role:' in quick and 'Method dev/mcpmcp/sophisticated/MenuGuard.quickMove:' in quick and 'String isInaccessibleSlot' in quick, 'ownership/read-only/active/pickup/inaccessible guards preserved')
check('String quick_move_packet' in quick, 'same guarded operation selects intended route')
guard = body(live, 'static void checkItem(', 'private static void checkItemIdCount(')
check('Method checkItemIdCount:' in guard and 'Method dev/mcpmcp/sophisticated/StackIdentity.hash:' in guard and 'String expected_stack_hash' in guard, 'item/count/full saved identity comparison')
check('String stack_hash' in live and 'String stack_token' in live and 'String backpack_stack_changed_refresh_observation' in live, 'new fingerprint coexists with prior backpack snapshot token')
check('net/minecraft/world/item/ItemStack.m_41739_:' in identity and 'java/io/DataOutputStream.writeInt:' in identity, 'reobfuscated save plus full integer count')
check('handleInventoryMouseClick' not in live and 'MultiPlayerGameMode.m_171799_:' in live and 'net/minecraft/network/protocol/game/ServerboundContainerClickPacket' in live, 'real native click and packet paths remain reobfuscated')
for label, filename in [('contract', 'nbt-contract-red.log'), ('identity', 'nbt-red.log')]:
    text = (E / filename).read_text(encoding='utf-8')
    check('AssertionError' in text and 'BUILD FAILED' in text, 'recorded pre-fix failing regression: ' + label)

test_checks = {}
for name, expected in [('AdapterContractTest', 131), ('PlayerInventorySlotTest', 347), ('StackIdentityTest', 554)]:
    match = re.search(r'PASS ' + name + r' checks=(\d+)', (E / 'build-final.log').read_text(encoding='utf-8'))
    if match is None:
        raise AssertionError('missing final test result: ' + name)
    check(int(match.group(1)) == expected, 'final test counts captured from actual build log')
    test_checks[name] = int(match.group(1))
check('PASS DistributionTest independent_jars=3 isolated_loads=6 build_distribution_equal=true' in (E / 'distribution-test.log').read_text(encoding='utf-8'), 'actual isolated distribution loading and equality test')
result = {'status': 'PASS', 'checks': checks, 'test_checks': test_checks, 'contract_version': 2, 'required_parameter': 'expected_stack_hash', 'observer_field': 'data.slots[].item.stack_hash', 'identity': 'SHA-256(type-preserving canonical ItemStack.save including ForgeCaps plus full int count)', 'client_started': False, 'network_used': False, 'mapped_forge_sha256': hashlib.sha256(MAPPED.read_bytes()).hexdigest(), 'artifacts': artifacts, 'evidence': ['build.log', 'build-final.log', 'distribution-test.log', 'nbt-red.log', 'nbt-contract-red.log', 'adapter-live.javap.txt', 'adapter-stack-identity.javap.txt', 'mapped-ItemStack.javap.txt']}
(E / 'stack-identity-evidence.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(result, ensure_ascii=False, indent=2))
