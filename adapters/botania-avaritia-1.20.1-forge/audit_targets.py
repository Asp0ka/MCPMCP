from pathlib import Path
import zipfile, subprocess, json, hashlib
root=Path(__file__).parent
mods=Path('D:/Games/mc/Cheat/versions/神秘启旅/mods')
javap='D:/Games/mc/mc-info-mcp/tooling/jdk17/jdk-17.0.21+9/bin/javap.exe'
targets={'botania':'[魔法][植物魔法]Botania-1.20.1-453-FORGE.jar','avaritia':'Re-Avaritia-forged-1.20.1-1.3.9.0-beta4.jar'}
classes={
'botania':['vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity','vazkii.botania.common.block.block_entity.mana.ManaSpreaderBlockEntity','vazkii.botania.common.block.block_entity.PetalApothecaryBlockEntity','vazkii.botania.common.block.block_entity.RunicAltarBlockEntity','vazkii.botania.api.recipe.ManaInfusionRecipe','vazkii.botania.api.recipe.RunicAltarRecipe','vazkii.botania.api.recipe.PetalApothecaryRecipe','vazkii.botania.api.recipe.PureDaisyRecipe','vazkii.botania.api.recipe.TerrestrialAgglomerationRecipe','vazkii.botania.api.recipe.OrechidRecipe','vazkii.botania.api.recipe.ElvenTradeRecipe','vazkii.botania.api.recipe.BotanicalBreweryRecipe','vazkii.botania.common.block.block_entity.SimpleInventoryBlockEntity'],
'avaritia':['committee.nova.mods.avaritia.common.crafting.recipe.BaseTableCraftingRecipe','committee.nova.mods.avaritia.common.crafting.recipe.ShapedTableCraftingRecipe','committee.nova.mods.avaritia.common.crafting.recipe.ShapelessTableCraftingRecipe','committee.nova.mods.avaritia.common.crafting.recipe.CompressorRecipe','committee.nova.mods.avaritia.common.crafting.recipe.ExtremeSmithingRecipe','committee.nova.mods.avaritia.common.crafting.recipe.InfinityCatalystCraftRecipe','committee.nova.mods.avaritia.common.crafting.recipe.EternalSingularityCraftRecipe','committee.nova.mods.avaritia.common.tile.CompressorTile','committee.nova.mods.avaritia.common.tile.collector.BaseNeutronCollectorTile','committee.nova.mods.avaritia.common.menu.CompressorMenu','committee.nova.mods.avaritia.common.menu.NeutronCollectorMenu']}
for mod,n in targets.items():
 jar=mods/n
 z=zipfile.ZipFile(jar)
 (root/'evidence').mkdir(parents=True,exist_ok=True)
 (root/'evidence'/f'{mod}-manifest.txt').write_text(z.read('META-INF/MANIFEST.MF').decode()+chr(10)+z.read('META-INF/mods.toml').decode()+chr(10)+'SHA256 '+hashlib.sha256(jar.read_bytes()).hexdigest(),encoding='utf-8')
 out=[]
 for c in classes[mod]:
  p=subprocess.run([javap,'-classpath',str(jar),'-p','-c',c],capture_output=True,text=True,encoding='utf-8',errors='replace')
  if p.returncode: raise RuntimeError(p.stderr)
  out.append(p.stdout)
 (root/'evidence'/f'{mod}-javap.txt').write_text(chr(10).join(out),encoding='utf-8')
 print(mod, 'classes',len(out),'bytes',sum(map(len,out)))
 print(chr(10).join(line for t in out for line in t.splitlines() if line.startswith('public ') or line.startswith('  public ') or line.startswith('  private ') or line.startswith('  protected ')))

