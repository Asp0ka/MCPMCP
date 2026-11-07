package dev.mcpmcp.magic;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Only invokes named read methods audited by javap in evidence/. */
final class RecipeView {
 static JsonObject item(ItemStack s){return J.obj("item_id",BuiltInRegistries.ITEM.getKey(s.getItem()).toString(),"count",s.getCount(),"nbt",s.hasTag()?s.getTag().toString():null);}
 static JsonObject ingredient(Ingredient i){JsonArray alternatives=new JsonArray();ItemStack[] all=i.getItems();for(int n=0;n<Math.min(64,all.length);n++)alternatives.add(item(all[n]));return J.obj("definition",i.toJson(),"alternatives",alternatives,"alternative_count",all.length,"truncated",all.length>64,"empty",i.isEmpty());}
 static JsonObject read(Recipe<?> r,String mod)throws Exception {
  Minecraft mc=Minecraft.getInstance();String c=r.getClass().getName();
  JsonObject out=J.obj("recipe_id",r.getId().toString(),"serializer",BuiltInRegistries.RECIPE_SERIALIZER.getKey(r.getSerializer()),"recipe_type",BuiltInRegistries.RECIPE_TYPE.getKey(r.getType()),"implementation",c,"source","client_recipe_manager","automatic_execution",false);
  JsonArray inputs=new JsonArray();java.util.List<Ingredient> ingredients;
  if(c.equals("committee.nova.mods.avaritia.common.crafting.recipe.EternalSingularityCraftRecipe")){
   @SuppressWarnings("unchecked") var existing=(java.util.List<Ingredient>)r.getClass().getField("inputs").get(r);ingredients=existing;out.addProperty("ingredient_completeness","existing client field only; lazy getIngredients mutates recipe and is intentionally not called; consult JEI");
  }else ingredients=r.getIngredients();for(int n=0;n<Math.min(256,ingredients.size());n++)inputs.add(ingredient(ingredients.get(n)));
  out.add("ingredients",inputs);out.addProperty("ingredients_truncated",ingredients.size()>256);out.add("output",item(r.getResultItem(mc.level.registryAccess())));
  if(r instanceof ShapedRecipe s){out.addProperty("width",s.getWidth());out.addProperty("height",s.getHeight());out.addProperty("ordered",true);}
  if(mod.equals("avaritia")&&c.startsWith("committee.nova.mods.avaritia.common.crafting.recipe.")){
   String simple=c.substring(c.lastIndexOf('.')+1);
   switch(simple){
    case "ShapedTableCraftingRecipe" -> {out.add("width",J.json(J.read(r,"getWidth")));out.add("height",J.json(J.read(r,"getHeight")));out.addProperty("ordered",true);out.add("tier",J.json(J.read(r,"getTier")));out.addProperty("workstation","avaritia tier crafting table; EXTREME is 9x9");}
    case "ShapelessTableCraftingRecipe","InfinityCatalystCraftRecipe","EternalSingularityCraftRecipe" -> {out.addProperty("ordered",false);out.add("tier",J.json(J.read(r,"getTier")));out.addProperty("workstation","avaritia tier crafting table");out.addProperty("dynamic_note","Ingredients are the current client recipe view; runtime transforms need actual matching input and are not evaluated.");}
    case "CompressorRecipe" -> {out.add("input_count",J.json(J.read(r,"getInputCount")));out.add("time_ticks",J.json(J.read(r,"getTimeCost")));out.addProperty("workstation","avaritia neutronium compressor");}
    case "ExtremeSmithingRecipe" -> {JsonObject roles=new JsonObject();for(String field:List.of("template","base","additions"))roles.add(field,ingredient((Ingredient)r.getClass().getField(field).get(r)));out.add("input_roles",roles);out.addProperty("workstation","avaritia extreme smithing");}
    default -> out.add("mod_details",J.unknown("unreviewed_recipe_implementation"));
   }
  }
  if(mod.equals("botania")){
   for(String api:List.of("ManaInfusionRecipe","RunicAltarRecipe","PetalApothecaryRecipe","TerrestrialAgglomerationRecipe","PureDaisyRecipe","OrechidRecipe","ElvenTradeRecipe","BotanicalBreweryRecipe")){
    Class<?> type=Class.forName("vazkii.botania.api.recipe."+api,false,r.getClass().getClassLoader());if(!type.isInstance(r))continue;out.addProperty("mechanism",api);
    switch(api){
     case "ManaInfusionRecipe" -> {out.add("mana_cost",J.json(J.read(r,"getManaToConsume")));out.add("catalyst",stateIngredient(J.read(r,"getRecipeCatalyst")));}
     case "RunicAltarRecipe" -> {out.add("mana_cost",J.json(J.read(r,"getManaUsage")));out.add("reagent",ingredient((Ingredient)J.read(r,"getReagent")));}
     case "PetalApothecaryRecipe" -> out.add("reagent",ingredient((Ingredient)J.read(r,"getReagent")));
     case "TerrestrialAgglomerationRecipe" -> out.add("mana_cost",J.json(J.read(r,"getMana")));
     case "PureDaisyRecipe" -> {out.add("input_state",stateIngredient(J.read(r,"getInput")));out.add("output_state",state((BlockState)J.read(r,"getOutputState")));out.add("time_ticks",J.json(J.read(r,"getTime")));}
     case "OrechidRecipe" -> {out.add("input_state",stateIngredient(J.read(r,"getInput")));out.add("output_state",stateIngredient(J.read(r,"getOutput")));out.add("base_weight",J.json(J.read(r,"getWeight")));out.add("position_specific_weight",J.unknown("not_evaluated_without_position_context"));}
     case "ElvenTradeRecipe" -> {JsonArray outputs=new JsonArray();for(Object s:(List<?>)J.read(r,"getOutputs"))outputs.add(item((ItemStack)s));out.add("outputs",outputs);}
     case "BotanicalBreweryRecipe" -> {out.add("mana_cost",J.json(J.read(r,"getManaUsage")));out.add("container_specific_output",J.unknown("requires_real_brew_container"));}
    }
   }
  }
  out.addProperty("assembly_note","Displayed result only. Input-dependent output/NBT, remaining items and dynamic rules are not simulated. Optional JEI can supplement rules absent from RecipeManager.");return out;
 }
 private static JsonObject stateIngredient(Object o)throws Exception {return o==null?J.obj("required",false):((JsonObject)Class.forName("vazkii.botania.api.recipe.StateIngredient",false,o.getClass().getClassLoader()).getMethod("serialize").invoke(o)).deepCopy();}
 static JsonObject state(BlockState s){return J.obj("block_id",BuiltInRegistries.BLOCK.getKey(s.getBlock()),"state",s.toString());}
}
