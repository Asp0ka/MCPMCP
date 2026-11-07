"""Exact local JAR/API/artifact verification. Does not launch or modify Minecraft."""
from pathlib import Path
import hashlib,json,re,subprocess,zipfile
ROOT=Path(__file__).resolve().parent
JAVAP=Path("D:/Games/mc/mc-info-mcp/tooling/jdk17/jdk-17.0.21+9/bin/javap.exe")
targets=json.loads((ROOT/"targets.json").read_text(encoding="utf8"))
required={
 "ars_nouveau":{
  "api.mana.IManaCap":["getCurrentMana()","getMaxMana()"],
  "setup.registry.CapabilityRegistry":["getMana(net.minecraft.world.entity.LivingEntity)"],
  "api.spell.ISpellCaster":["getCurrentSlot()","getSpell(int)","getMaxSlots()","getSpellName(int)"],
  "api.spell.ISpellCasterProvider":["getSpellCaster(net.minecraft.world.item.ItemStack)"],
  "api.spell.Spell":["serializeRecipe()","getCost()","isValid()","isEmpty()"],
  "api.registry.GlyphRegistry":["getSpellpartMap()"],
  "api.source.ISourceTile":["getSource()","getMaxSource()","getTransferRate()"],
  "client.registry.ModKeyBindings":[" NEXT_SLOT;"," PREVIOUS_SLOT;"," OPEN_BOOK;"],
  "common.crafting.recipes.ImbuementRecipe":["asRecipe()"],
  "common.crafting.recipes.GlyphRecipe":["asRecipe()"],
  "api.enchanting_apparatus.EnchantingApparatusRecipe":["asRecipe()"],
 },
 "irons_spellbooks":{
  "player.ClientMagicData":["getPlayerMana()","getSpellSelectionManager()","getCooldowns()","isCasting()","getCastingSpellId()","getCastingSpellLevel()","getCastDurationRemaining()","getCastDuration()"],
  "api.magic.SpellSelectionManager":["getAllSpells()","getSelection()","getSelectionIndex()","getSpellSlot(int)"],
  "api.magic.SpellSelectionManager$SelectionOption":[" spellData;"," slot;"," slotIndex;"," globalIndex;"],
  "api.spells.ISpellContainer":["get(net.minecraft.world.item.ItemStack)","isSpellContainer(net.minecraft.world.item.ItemStack)","getAllSpells()","mustEquip()","isSpellWheel()","getMaxSpellCount()"],
  "api.spells.SpellData":["getSpell()","getLevel()","isLocked()"],
  "api.spells.SpellSlot":["index()","spellData()"],
  "api.spells.AbstractSpell":["getSpellId()","getManaCost(int)","getCastType()","isEnabled()","getMaxLevel()","getSpellCooldown()"],
  "api.registry.SpellRegistry":[" REGISTRY;"],
  "player.KeyMappings":[" SPELLBOOK_CAST_ACTIVE_KEYMAP;"," QUICK_CAST_MAPPINGS;"],
  "capabilities.magic.PlayerCooldowns":["getSpellCooldowns()"],
  "capabilities.magic.CooldownInstance":["getCooldownRemaining()","getSpellCooldown()"],
  "recipe_types.alchemist_cauldron.FillAlchemistCauldronRecipe":["input()","result()","returned()","mustFitAll()"],
  "recipe_types.alchemist_cauldron.EmptyAlchemistCauldronRecipe":["input()","result()","fluid()"],
  "recipe_types.alchemist_cauldron.BrewAlchemistCauldronRecipe":["fluidIn()","reagent()","results()","byproduct()"],
  "block.alchemist_cauldron.AlchemistCauldronTile":["getFluidAmount()"," inputItems;"," fluidInventory;","handleUpdateTag(net.minecraft.nbt.CompoundTag)"],
  "block.scroll_forge.ScrollForgeTile":["getItemHandler()","handleUpdateTag(net.minecraft.nbt.CompoundTag)"],
 },
 "traveloptics":{
  "api.spells.AbstractUniqueSpell":["extends io.redspace.ironsspellbooks.api.spells.AbstractSpell"],
  "api.spells.AdvancedSpell":["extends io.redspace.ironsspellbooks.api.spells.AbstractSpell"],
 }
}
prefix={"ars_nouveau":"com.hollingsworth.arsnouveau.","irons_spellbooks":"io.redspace.ironsspellbooks.","traveloptics":"com.gametechbc.traveloptics."}
report={"targets":[],"api_symbols_checked":0,"artifacts":[],"game_started":False}
for mod,info in targets.items():
 path=Path(info["path"]);actual=hashlib.sha256(path.read_bytes()).hexdigest()
 assert actual==info["sha256"],f"target changed: {mod}"
 with zipfile.ZipFile(path) as z:
  manifest=z.read("META-INF/MANIFEST.MF").decode();toml=z.read("META-INF/mods.toml").decode()
  match=re.search(r'^version\s*=\s*"([^"]+)"',toml,re.M)
  assert match is not None,f"missing mod version: {mod}"
  version=match.group(1)
  if version=="${file.jarVersion}":
   match=re.search(r"Implementation-Version: ([^\r\n]+)",manifest)
   assert match is not None,f"missing manifest version: {mod}"
   version=match.group(1)
 folder=ROOT/"distribution"/(mod+"-"+actual[:32]);decl=json.loads((folder/"plugin.json").read_text())
 assert decl["target_version"]==version and decl["target_sha256"]==actual
 report["targets"].append({"mod_id":mod,"version":version,"sha256":actual,"path":str(path)})
 for cls,methods in required[mod].items():
  proc=subprocess.run([str(JAVAP),"-p","-classpath",str(path),prefix[mod]+cls],capture_output=True,text=True,encoding="utf8",errors="replace",check=True)
  for method in methods:
   assert method in proc.stdout,f"missing {cls}.{method}"
   report["api_symbols_checked"]+=1
 jar=folder/"adapter.jar"
 with zipfile.ZipFile(jar) as z:
  service=z.read("META-INF/services/dev.mcpmcp.api.McpmcpPlugin").decode().strip().splitlines();assert len(service)==1
  assert "dev/mcpmcp/magic/MagicProvider.class" in z.namelist()
  assert not any(n.startswith(("dev/mcpmcp/api/","dev/mcpmcp/core/","net/minecraft/","com/google/gson/")) for n in z.namelist())
 report["artifacts"].append({"path":str(jar),"sha256":hashlib.sha256(jar.read_bytes()).hexdigest(),"bytes":jar.stat().st_size,"provider":service[0]})
(ROOT/"verification.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n",encoding="utf8")
print(json.dumps(report,indent=2,ensure_ascii=False))
print("Target/API/artifact verification PASS")
