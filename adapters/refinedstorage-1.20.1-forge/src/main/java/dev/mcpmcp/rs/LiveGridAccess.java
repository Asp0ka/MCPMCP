package dev.mcpmcp.rs;

import com.google.gson.*;
import com.refinedmods.refinedstorage.RS;
import com.refinedmods.refinedstorage.api.network.grid.GridType;
import com.refinedmods.refinedstorage.container.GridContainerMenu;
import com.refinedmods.refinedstorage.container.slot.grid.CraftingGridSlot;
import com.refinedmods.refinedstorage.container.slot.grid.ResultCraftingGridSlot;
import java.nio.charset.StandardCharsets;
import com.refinedmods.refinedstorage.screen.grid.GridScreen;
import com.refinedmods.refinedstorage.screen.grid.stack.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import static dev.mcpmcp.core.Jsons.*;

/** Direct calls verified against the pinned 1.12.4 JAR, reobfuscated for production. */
final class LiveGridAccess implements GridAccess {
 private Object world,player,menu,screen,view,connection;
 private String session="";
 private GridScreen current(){
  Minecraft mc=Minecraft.getInstance();
  if(!mc.isSameThread())throw new IllegalArgumentException("rs_client_thread_required");
  if(mc.level==null||mc.player==null||mc.getConnection()==null){reset();throw new IllegalArgumentException("rs_not_in_world");}
  if(!(mc.screen instanceof GridScreen s)){reset();throw new IllegalArgumentException("rs_grid_not_open");}
  if(!(mc.player.containerMenu instanceof GridContainerMenu g)||s.getMenu()!=g||s.getGrid()!=g.getGrid()){reset();throw new IllegalArgumentException("rs_screen_menu_mismatch");}
  if(s.getView()==null){reset();throw new IllegalArgumentException("rs_grid_view_not_ready");}
  if(world!=mc.level||player!=mc.player||menu!=g||screen!=s||view!=s.getView()||connection!=mc.getConnection()){
   world=mc.level;player=mc.player;menu=g;screen=s;view=s.getView();connection=mc.getConnection();session=UUID.randomUUID().toString();
  }
  if(!s.getGrid().isGridActive()){reset();throw new IllegalArgumentException("rs_grid_inactive");}
  if(s.getGrid().getGridType()==GridType.FLUID)throw new IllegalArgumentException("rs_item_grid_required");
  return s;
 }
 private void reset(){world=null;player=null;menu=null;screen=null;view=null;connection=null;session="";}
 public Frame capture(){
  GridScreen s=current();Minecraft mc=Minecraft.getInstance();
  JsonArray inventory=new JsonArray();int index=0;
  for(ItemStack item:mc.player.getInventory().items){JsonObject row=item(item);row.addProperty("inventory_slot",index++);inventory.add(row);}
  JsonObject inv=obj("items",inventory,"sha256",JsonIdentity.digest(inventory),"scope","36 main inventory slots only");
  return new Frame(session,mc.player.containerMenu.containerId,true,s.getGrid().getGridType().name(),s.getView().canCraft(),item(mc.player.containerMenu.getCarried()),crafting(s),inv,clip(s.getSearchFieldText(),256));
 }
 public List<Entry> entries(){
  GridScreen s=current();Collection<IGridStack> raw=s.getView().getAllStacks();
  if(raw.size()>16384)throw new IllegalArgumentException("rs_snapshot_entry_limit");
  List<Entry> result=new ArrayList<>();long bytes=0;
  for(IGridStack stack:raw){
   if(!(stack instanceof ItemGridStack item))continue;
   if(stack.getId()==null)throw new IllegalArgumentException("rs_stack_uuid_missing");
   JsonObject value=item(item.getStack());bytes+=value.toString().getBytes(StandardCharsets.UTF_8).length;
   if(bytes>8_388_608)throw new IllegalArgumentException("rs_snapshot_byte_limit");
   result.add(new Entry(stack.getId().toString(),stack.getOtherId()==null?"":stack.getOtherId().toString(),stack.isCraftable(),stack.getQuantity(),value));
  }
  return List.copyOf(result);
 }
 public void submit(Command c){
  GridScreen s=current();Minecraft mc=Minecraft.getInstance();
  // Only normal client packets/UI: no storage handlers, stack setters or server APIs.
  switch(c.action()){
   case "rs.extract","rs.insert_held","rs.craft.start","rs.crafting.clear" -> RS.NETWORK_HANDLER.sendToServer(NativePackets.create(c));
   case "rs.crafting.take" -> {
    if(mc.gameMode==null)throw new IllegalArgumentException("rs_game_mode_unavailable");
    Slot slot=mc.player.containerMenu.getSlot(c.slot());
    if(!(slot instanceof ResultCraftingGridSlot)||!slot.isActive()||!slot.mayPickup(mc.player))throw new IllegalArgumentException("rs_result_slot_unavailable");
    mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,c.slot(),0,ClickType.PICKUP,mc.player);
   }
   default -> throw new IllegalArgumentException("unknown_rs_action");
  }
 }
 private JsonObject crafting(GridScreen s){
  if(s.getGrid().getGridType()!=GridType.CRAFTING)return obj("available",false);
  Minecraft mc=Minecraft.getInstance();JsonArray inputs=new JsonArray();JsonObject output=new JsonObject();int resultSlot=-1;
  List<Slot> slots=mc.player.containerMenu.slots;
  if(slots.size()>512)throw new IllegalArgumentException("rs_menu_slot_limit");
  for(int i=0;i<slots.size();i++){
   Slot slot=slots.get(i);
   if(slot instanceof CraftingGridSlot){JsonObject row=item(slot.getItem());row.addProperty("menu_slot",i);inputs.add(row);}
   if(slot instanceof ResultCraftingGridSlot){
    if(resultSlot!=-1)throw new IllegalArgumentException("rs_ambiguous_result_slot");
    resultSlot=i;output=item(slot.getItem());
   }
  }
  if(inputs.size()!=9||resultSlot<0)throw new IllegalArgumentException("rs_crafting_structure_mismatch");
  JsonObject matrix=obj("inputs",inputs,"result",output,"result_slot",resultSlot);
  String digest=JsonIdentity.digest(matrix);matrix.addProperty("available",true);matrix.addProperty("matrix_sha256",digest);return matrix;
 }
 private static JsonObject item(ItemStack stack){
  if(stack==null)throw new IllegalArgumentException("rs_missing_item_stack");
  NbtIdentity.Description nbt=NbtIdentity.describe(stack.getTag());
  return obj("item_id",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),"name",clip(stack.getHoverName().getString(),256),
   "count",stack.getCount(),"damage",stack.getDamageValue(),"max_stack_size",stack.getMaxStackSize(),
   "nbt",nbt.preview(),"nbt_truncated",nbt.truncated(),"nbt_sha256",nbt.sha256());
 }
 private static String clip(String s,int n){return s==null?"":s.substring(0,Math.min(n,s.length()));}
}
