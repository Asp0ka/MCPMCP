from pathlib import Path
import hashlib
import json
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
JAVAP = Path('D:/Games/mc/mc-info-mcp/tooling/jdk17/jdk-17.0.21+9/bin/javap.exe')
MODS = Path('D:/Games/mc/Cheat/versions/神秘启旅/mods')
TARGETS = {
    'botania': {'file': '[魔法][植物魔法]Botania-1.20.1-453-FORGE.jar', 'sha256': '222755726437ed9d908c58462b604c10d137c3756a4a228ab459dbbaf8bd4c19', 'version': '1.20.1-453-FORGE', 'implementation_version': '1.20.1-453-FORGE'},
    'avaritia': {'file': 'Re-Avaritia-forged-1.20.1-1.3.9.0-beta4.jar', 'sha256': 'a2f823fb0f226bfb25d10534a880150403aca5ce6b593f93a5e8ee6a7fb47d51', 'version': '1.3.9.0', 'implementation_version': '1.20.1-1.3.9.0-beta4'},
}
BOTANIA_API = {
    'ManaInfusionRecipe': ['getManaToConsume()', 'getRecipeCatalyst()'],
    'RunicAltarRecipe': ['getManaUsage()'],
    'RecipeWithReagent': ['getReagent()'],
    'PetalApothecaryRecipe': [],
    'TerrestrialAgglomerationRecipe': ['getMana()'],
    'PureDaisyRecipe': ['getInput()', 'getOutputState()', 'getTime()'],
    'OrechidRecipe': ['getInput()', 'getOutput()', 'getWeight()'],
    'ElvenTradeRecipe': ['getOutputs()'],
    'BotanicalBreweryRecipe': ['getManaUsage()'],
    'StateIngredient': ['serialize()'],
}
AVARITIA_RECIPES = {
    'ShapedTableCraftingRecipe': ['getWidth()', 'getHeight()', 'getTier()'],
    'ShapelessTableCraftingRecipe': ['getTier()'],
    'InfinityCatalystCraftRecipe': ['getTier()'],
    'EternalSingularityCraftRecipe': ['getTier()'],
    'CompressorRecipe': ['getInputCount()', 'getTimeCost()'],
    'ExtremeSmithingRecipe': ['Ingredient template;', 'Ingredient base;', 'Ingredient additions;'],
}

def metadata(mod):
    import tomllib
    target = TARGETS[mod]
    path = MODS / target['file']
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    assert digest == target['sha256'], ('target_hash_changed', str(path), digest)
    with zipfile.ZipFile(path) as jar:
        manifest = jar.read('META-INF/MANIFEST.MF').decode().replace(chr(13), '')
        fields = dict(line.split(': ', 1) for line in manifest.splitlines() if ': ' in line)
        assert fields['Implementation-Version'] == target['implementation_version']
        toml = tomllib.loads(jar.read('META-INF/mods.toml').decode())
        info = next(m for m in toml['mods'] if m['modId'] == mod)
        version = info['version'].replace('${file.jarVersion}', fields['Implementation-Version'])
        assert version == target['version']
    return {'path': str(path), **target, 'actual_sha256': digest, 'actual_mod_version': version}

def inspect(jar, name, expected=()):
    result = subprocess.run([str(JAVAP), '-p', '-c', '-classpath', str(jar), name], capture_output=True, text=True, encoding='utf-8', errors='replace')
    assert result.returncode == 0, (name, result.stderr)
    for member in expected:
        assert member in result.stdout, (name, member, 'missing_member')
    return result.stdout

def main():
    results = {}
    for mod in TARGETS:
        results[mod] = metadata(mod)
        jar = Path(results[mod]['path'])
        classes = {}
        if mod == 'botania':
            classes.update({'vazkii.botania.api.recipe.' + name: members for name, members in BOTANIA_API.items()})
            for name in ['ManaInfusionRecipe', 'RunicAltarRecipe', 'PetalsRecipe', 'PureDaisyRecipe', 'OrechidRecipe', 'ElvenTradeRecipe', 'BotanicalBreweryRecipe', 'RecipeTerraPlate']:
                classes['vazkii.botania.common.crafting.' + name] = []
            for name in ['PetalApothecaryBlockEntity', 'RunicAltarBlockEntity', 'SimpleInventoryBlockEntity', 'BotaniaBlockEntity', 'BreweryBlockEntity', 'TerrestrialAgglomerationPlateBlockEntity']:
                classes['vazkii.botania.common.block.block_entity.' + name] = ['getFluid()'] if name == 'PetalApothecaryBlockEntity' else []
            for name in ['ManaPoolBlockEntity', 'ManaSpreaderBlockEntity']:
                classes['vazkii.botania.common.block.block_entity.mana.' + name] = []
        else:
            classes.update({'committee.nova.mods.avaritia.common.crafting.recipe.' + name: members for name, members in AVARITIA_RECIPES.items()})
            for name in ['TierCraftMenu', 'CompressorMenu', 'NeutronCollectorMenu']:
                classes['committee.nova.mods.avaritia.common.menu.' + name] = []
            for name in ['CompressorTile', 'TierCraftTile', 'collector.BaseNeutronCollectorTile']:
                classes['committee.nova.mods.avaritia.common.tile.' + name] = []
            for name in ['BaseTileEntity', 'BaseInventoryTileEntity']:
                classes['committee.nova.mods.avaritia.api.common.tile.' + name] = []
            classes['committee.nova.mods.avaritia.api.common.wrapper.ItemStackWrapper'] = []
            classes['committee.nova.mods.avaritia.init.registry.ModCraftTier'] = ['int size;']
        text = chr(10).join(inspect(jar, name, members) for name, members in classes.items())
        evidence = ROOT / 'evidence' / (mod + '-verified-javap.txt')
        evidence.write_text(text, encoding='utf-8')
        results[mod]['javap_classes'] = len(classes)
        print(mod, results[mod]['actual_mod_version'], results[mod]['actual_sha256'], 'javap_classes=' + str(len(classes)))
    (ROOT / 'evidence' / 'target-verification.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
    print('Exact target metadata and reflected members verified')

if __name__ == '__main__':
    main()
