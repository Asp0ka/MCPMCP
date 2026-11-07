package dev.mcpmcp.magic;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

final class Resources {
 static JsonObject block(String mod,BlockEntity be,SyncEvidence evidence)throws Exception {
  String c=be.getClass().getName();SyncEvidence.Sample s=evidence.block(be.getBlockPos(),be.getType());
  JsonObject out=J.obj("implementation",c,"source","last_received_server_block_entity_payload","observed_at_ms",s==null?null:s.time(),"freshness","last received snapshot; may lag current server state","complete_server_nbt",false);
  CompoundTag n=s==null?null:s.tag();
  out.add("inventory",inventory(n));
  if(mod.equals("botania")){
   if(c.endsWith("ManaPoolBlockEntity")){number(out,n,"mana","mana");number(out,n,"mana_capacity","manaCap");bool(out,n,"outputting","outputting");bool(out,n,"can_accept","canAccept");bool(out,n,"can_spare","canSpare");}
   else if(c.endsWith("ManaSpreaderBlockEntity")){number(out,n,"mana","mana");number(out,n,"rotation_x","rotationX");number(out,n,"rotation_y","rotationY");bool(out,n,"can_shoot_burst","canShootBurst");out.add("binding",J.unknown("receiver_binding_requires_context_and_can_be_stale"));out.add("mana_capacity",J.unknown("capacity_not_in_audited_payload"));}
   else if(c.endsWith("RunicAltarBlockEntity")){number(out,n,"mana","mana");number(out,n,"target_mana","manaToGet");out.add("recipe_progress",ratio(n,"mana","manaToGet"));out.add("active_recipe",J.unknown("recipe_identity_not_synchronized"));out.add("completion",J.unknown("mana_ready_does_not_prove_reagent_or_wand_completion"));}
   else if(c.endsWith("PetalApothecaryBlockEntity")){out.add("fluid",J.known(J.read(be,"getFluid").toString(),"client_block_state_property"));out.add("progress",J.unknown("instant_recipe_no_synced_progress_counter"));}
   else if(c.endsWith("BreweryBlockEntity")||c.endsWith("TerrestrialAgglomerationPlateBlockEntity")){number(out,n,"mana","mana");out.add("target_mana",J.unknown("required_recipe_cost_not_in_audited_payload"));}
   else out.add("mechanism",J.unknown("unreviewed_work_block"));
  }else{
   if(c.endsWith("CompressorTile")){number(out,n,"material_count","MaterialCount");number(out,n,"progress_ticks","Progress");bool(out,n,"ejecting","Ejecting");out.add("material",n!=null&&n.contains("MaterialStack",10)?J.known(rawItem(n.getCompound("MaterialStack")),"server_block_entity_payload"):J.unknown("no_material_payload"));out.add("active_recipe",J.unknown("recipe_not_in_sync_payload"));out.add("required_materials",J.unknown("active_recipe_not_known"));}
   else if(c.contains(".tile.collector.")){number(out,n,"progress_ticks","progress");out.add("production_ticks",J.unknown("configuration_dependent_not_synced_here"));}
   else if(c.endsWith("TierCraftTile"))out.addProperty("mechanism","tier_crafting_inventory; menu observer exposes actual grid");
   else out.add("mechanism",J.unknown("unreviewed_work_block"));
  }
  return out;
 }
 static void number(JsonObject out,CompoundTag n,String name,String key){out.add(name,n!=null&&n.contains(key,99)?J.known(((NumericTag)n.get(key)).getAsNumber(),"server_block_entity_payload:"+key):J.unknown("missing_typed_server_payload:"+key));}
 static void bool(JsonObject out,CompoundTag n,String name,String key){out.add(name,n!=null&&n.contains(key,1)?J.known(n.getBoolean(key),"server_block_entity_payload:"+key):J.unknown("missing_typed_server_payload:"+key));}
 static JsonObject ratio(CompoundTag n,String a,String b){return n!=null&&n.contains(a,99)&&n.contains(b,99)&&n.getDouble(b)>0?J.known(n.getDouble(a)/n.getDouble(b),"mana/target_mana; not completed craft"):J.unknown("missing_payload_or_no_active_target");}
 static JsonObject rawItem(CompoundTag t){return J.obj("item_id",t.contains("id",8)?t.getString("id"):null,"count",t.contains("Count",99)?((NumericTag)t.get("Count")).getAsNumber():null,"nbt",t.contains("tag",10)?t.getCompound("tag").toString():null);}
 static JsonObject inventory(CompoundTag n){
  if(n==null||!n.contains("Items",9))return J.unknown("no_explicit_server_inventory_list");
  if(n.getList("Items",10).isEmpty()&&((ListTag)n.get("Items")).size()>0)return J.unknown("wrong_inventory_list_element_type");
  ListTag list=n.getList("Items",10);JsonArray items=new JsonArray();for(int i=0;i<Math.min(list.size(),256);i++){CompoundTag t=list.getCompound(i);JsonObject item=rawItem(t);item.add("slot",t.contains("Slot",99)?J.json(((NumericTag)t.get("Slot")).getAsInt()):JsonNull.INSTANCE);items.add(item);}
  return J.obj("status","known","source","explicit_server_inventory_list","nonempty_slots",items,"truncated",list.size()>256,"empty_slots_omitted",true);
 }
 static int gridSize(int machineSlots){int cells=machineSlots-1,size=(int)Math.sqrt(cells);return size*size==cells&&Set.of(3,5,7,9).contains(size)?size:0;}
 static JsonObject menu(String mod,SyncEvidence evidence)throws Exception {
  Minecraft mc=Minecraft.getInstance();AbstractContainerMenu m=mc.player.containerMenu;String c=m.getClass().getName();
  String prefix=mod.equals("avaritia")?"committee.nova.mods.avaritia.common.menu.":"vazkii.botania.";
  if(!c.startsWith(prefix))return J.obj("ok",true,"applicable",false,"menu_class",c,"reason","current_menu_is_not_target_mod");
  JsonArray slots=new JsonArray();int machine=0;
  for(int i=0;i<m.slots.size();i++){Slot s=m.slots.get(i);boolean player=s.container==mc.player.getInventory();if(!player)machine++;JsonObject row=RecipeView.item(s.getItem());row.addProperty("slot",i);row.addProperty("container_slot",s.getContainerSlot());row.addProperty("player_inventory",player);slots.add(row);}
  JsonObject out=J.obj("ok",true,"applicable",true,"menu_id",m.containerId,"menu_class",c,"slots",slots,"cursor",RecipeView.item(m.getCarried()),"cursor_empty",m.getCarried().isEmpty(),"source","current_client_menu; may include client prediction, not independent server confirmation","safe_core_actions",List.of("container.move","container.sequence"),"menu_policy","reuse already-open menu without target; keep open; core validates empty cursor and recovers only owned pickup");
  if(c.endsWith("TierCraftMenu")){int cells=machine-1,size=gridSize(machine);if(size>0){out.addProperty("grid_width",size);out.addProperty("grid_height",size);out.addProperty("result_slot",0);out.addProperty("grid_first_slot",1);out.addProperty("grid_last_slot",cells);out.addProperty("layout","row_major; javap-verified constructor plus live slot count");}else out.add("grid",J.unknown("unexpected_native_slot_layout"));out.addProperty("recipe_book_transfer",false);}
  if(c.endsWith("CompressorMenu")||c.endsWith("NeutronCollectorMenu")){Integer v=evidence.menuData(m.containerId,0);out.add("progress_ticks",v==null?J.unknown("no_received_menu_data_0"):J.known(v,"received_container_set_data:0; protocol signed_short"));}
  return out;
 }
}
