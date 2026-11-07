package dev.mcpmcp.sophisticated;

import com.google.gson.*;
import dev.mcpmcp.core.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/** Exact-version mod APIs are reflected; mapped Minecraft calls are reobfuscated. */
final class LiveAccess implements AdapterBase.Access {
 private static final String STORAGE="net.p3pp3rf1y.sophisticatedstorage.common.gui.StorageContainerMenu";
 private static final String BACKPACK="net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer";
 private static final String FURNACE="ironfurnaces.container.furnaces.BlockIronFurnaceContainerBase";
 private final String type;
 private final Map<AbstractContainerMenu,String> identities=new WeakHashMap<>();
 private record BackpackSnapshot(int slot,ItemStack stack) {}
 private final Map<String,BackpackSnapshot> backpackSnapshots=new HashMap<>();
 private Object level;
 LiveAccess(String type) {this.type=type;}
 private Minecraft world() throws Exception {
  Binding.check(type);
  Minecraft mc=Minecraft.getInstance();
  if(mc.player==null||mc.level==null||mc.getConnection()==null)throw new IllegalStateException("not_in_world");
  if(!mc.isSameThread())throw new IllegalStateException("client_thread_required");
  if(level!=mc.level){level=mc.level;identities.clear();backpackSnapshots.clear();}
  return mc;
 }
 private boolean supported(AbstractContainerMenu menu) throws Exception {return Class.forName(type.equals("storage")?STORAGE:type.equals("backpack")?BACKPACK:FURNACE).isInstance(menu);}
 private String token(AbstractContainerMenu menu) {return identities.computeIfAbsent(menu,m->UUID.randomUUID().toString());}
 private AbstractContainerMenu guarded(Minecraft mc,JsonObject args,boolean inventory) throws Exception {
  AbstractContainerMenu menu=mc.player.containerMenu;
  MenuGuard.check(Jsons.integer(args,"menu_id",-1),Jsons.str(args,"menu_token",""),menu.containerId,identities.get(menu),menu.getCarried().isEmpty());
  if(inventory ? menu!=mc.player.inventoryMenu : !supported(menu))throw new IllegalStateException(inventory?"player_inventory_menu_required":"target_mod_menu_required");
  if(!inventory&&!menu.stillValid(mc.player))throw new IllegalStateException("menu_no_longer_valid");
  return menu;
 }
 @Override public JsonObject observe(JsonObject args) throws Exception {
  Minecraft mc=world();AbstractContainerMenu menu=mc.player.containerMenu;boolean target=supported(menu);
  JsonObject out=Jsons.obj("source","live_client_menu_server_acceptance_not_confirmed","menu_id",menu.containerId,"menu_token",token(menu),"menu_class",menu.getClass().getName(),"state_id",menu.getStateId(),"target_menu",target,"player_inventory_menu",menu==mc.player.inventoryMenu,"carried",item(menu.getCarried()),"server_confirmation",false);
  if(type.equals("backpack"))out.add("inventory_backpacks",backpacks(mc,menu));
  if(!target){out.addProperty("available",false);return out;}
  int total=slotCount(menu),offset=Jsons.integer(args,"offset",0),limit=Jsons.integer(args,"limit",64);
  if(total>16384)throw new IllegalStateException("menu_slot_budget_exceeded");
  JsonArray slots=new JsonArray();ItemStack candidate=args.has("inventory_slot")?mc.player.getInventory().getItem(Jsons.integer(args,"inventory_slot",-1)):null;
  for(int i=offset;i<Math.min(total,offset+limit);i++){Slot slot=menu.getSlot(i);JsonObject row=slot(menu,slot,i);if(candidate!=null)row.addProperty("accepts_candidate",!candidate.isEmpty()&&slot.isActive()&&slot.mayPlace(candidate));slots.add(row);}
  out.add("slots",slots);out.addProperty("slot_total",total);out.addProperty("slot_offset",offset);out.addProperty("slots_has_more",offset+slots.size()<total);out.addProperty("available",true);
  if(candidate!=null)out.add("candidate",Jsons.obj("inventory_slot",Jsons.integer(args,"inventory_slot",-1),"item",item(candidate)));
  if(type.equals("furnace"))out.add("furnace",furnace(menu));else {
   out.addProperty("storage_inventory_slots",(int)call(menu,"getNumberOfStorageInventorySlots"));
   out.addProperty("installed_upgrade_slots",(int)call(menu,"getNumberOfUpgradeSlots"));
   out.addProperty("sort_by",call(menu,"getSortBy").toString());
   upgrades(out,menu,offset,limit);
  }return out;
 }
 private JsonArray backpacks(Minecraft mc,AbstractContainerMenu menu) throws Exception {
  JsonArray result=new JsonArray();backpackSnapshots.clear();Class<?> backpack=Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem");
  for(int i=0;i<36;i++){ItemStack s=mc.player.getInventory().getItem(i);if(!s.isEmpty()&&backpack.isInstance(s.getItem())){
   String snapshot=UUID.randomUUID().toString();backpackSnapshots.put(snapshot,new BackpackSnapshot(i,s.copy()));
   result.add(Jsons.obj("inventory_slot",i,"item",item(s),"stack_token",snapshot,"open_allowed",menu==mc.player.inventoryMenu&&menu.getCarried().isEmpty()));
  }}return result;
 }
 private int slotCount(AbstractContainerMenu menu) throws Exception {return type.equals("furnace")?menu.slots.size():(int)call(menu,"getTotalSlotsNumber");}
 private JsonObject slot(AbstractContainerMenu menu,Slot s,int index) throws Exception {
  return Jsons.obj("menu_slot",index,"container_slot",s.getContainerSlot(),"slot_class",s.getClass().getName(),"role",role(menu,s,index),"active",s.isActive(),"may_pickup",s.mayPickup(Minecraft.getInstance().player),"max_stack",s.getMaxStackSize(),"item",item(s.getItem()));
 }
 private String role(AbstractContainerMenu menu,Slot s,int index) throws Exception {
  if(s.container==Minecraft.getInstance().player.getInventory())return "player_inventory";
  if(type.equals("furnace"))return MenuGuard.furnaceRole(s.getClass().getName());
  for(var entry:upgradeMap(menu).entrySet()){Object upgrade=entry.getValue();if(((List<?>)call(upgrade,"getSlots")).contains(s)){
   if(Class.forName(Contract.CRAFTING).isInstance(upgrade)){
    if(((List<?>)call(upgrade,"getRecipeSlots")).contains(s))return "crafting_input";
    if(s instanceof ResultSlot)return "crafting_result";
   }return "upgrade_settings_read_only";
  }}
  if((boolean)call(menu,"isStorageInventorySlot",new Class<?>[]{int.class},index))return "storage";
  return "upgrade_install_or_extra_read_only";
 }
 @SuppressWarnings("unchecked") private Map<Integer,Object> upgradeMap(AbstractContainerMenu menu) throws Exception {return (Map<Integer,Object>)call(menu,"getUpgradeContainers");}
 private void upgrades(JsonObject out,AbstractContainerMenu menu,int offset,int limit) throws Exception {
  var map=upgradeMap(menu);var entries=new ArrayList<>(map.entrySet());entries.sort(Map.Entry.comparingByKey());
  JsonArray rows=new JsonArray();Optional<?> open=(Optional<?>)call(menu,"getOpenContainer");
  for(int i=offset;i<Math.min(entries.size(),offset+limit);i++){var e=entries.get(i);Object u=e.getValue();boolean crafting=Class.forName(Contract.CRAFTING).isInstance(u);JsonArray slots=new JsonArray();int emitted=0;
   for(Object obj:(List<?>)call(u,"getSlots")){Slot s=(Slot)obj;if(emitted++>=128)break;int index=s.index;boolean valid=index>=0&&index<slotCount(menu)&&menu.getSlot(index)==s;JsonObject sr=Jsons.obj("menu_slot",valid?index:null,"container_slot",s.getContainerSlot(),"slot_class",s.getClass().getName(),"item",item(s.getItem()),"active",s.isActive());if(valid)sr.addProperty("role",role(menu,s,index));slots.add(sr);}
   rows.add(Jsons.obj("upgrade_slot",e.getKey(),"class",u.getClass().getName(),"item",item((ItemStack)call(u,"getUpgradeStack")),"enabled",call(menu,"getUpgradeEnabled",new Class<?>[]{int.class},e.getKey()),"runnable",call(menu,"isUpgradeRunnable",new Class<?>[]{int.class},e.getKey()),"selected",open.isPresent()&&open.get()==u,"client_tab_open",call(u,"isOpen"),"crafting_supported",crafting,"slots",slots,"slot_total",((List<?>)call(u,"getSlots")).size(),"slots_truncated",emitted>128));
  }out.add("upgrades",rows);out.addProperty("upgrade_container_total",entries.size());out.addProperty("upgrades_has_more",offset+rows.size()<entries.size());
 }
 private JsonObject furnace(AbstractContainerMenu menu) throws Exception {
  JsonObject data=new JsonObject();
  for(String method:List.of("getTier","getEnergy","getMaxEnergy","isBurning","isGeneratorBurning","getIsFactory","getIsFurnace","getIsGenerator","getAutoInput","getAutoOutput","getAugmentGUI","isAutoSplit","getRedstoneMode","getComSub","getSettingTop","getSettingBottom","getSettingFront","getSettingBack","getSettingLeft","getSettingRight"))data.add(method,Jsons.GSON.toJsonTree(call(menu,method)));
  net.minecraft.core.BlockPos pos=(net.minecraft.core.BlockPos)call(menu,"getPos");data.add("block_position",Jsons.obj("x",pos.getX(),"y",pos.getY(),"z",pos.getZ()));
  Field field=Class.forName(FURNACE).getDeclaredField("te");field.setAccessible(true);Object tile=field.get(menu);
  JsonObject raw=new JsonObject();
  for(String name:List.of("currentAugment","factoryCookTime","factoryTotalCookTime","generatorBurn","generatorRecentRecipeRF","furnaceBurnTime","cookTime","totalCookTime","recipesUsed"))raw.add(name,Jsons.GSON.toJsonTree(tile.getClass().getField(name).get(tile)));
  data.add("client_tile_menu_data",raw);data.addProperty("data_scope","client tile fields used by native menu and synchronized data slots; not an authoritative server snapshot");
  data.addProperty("fuel_semantics","fuel and generator_fuel slots use distinct native mayPlace rules; factories use energy; inactive slots are reported but actions reject them");return data;
 }
 @Override public JsonObject execute(String op,JsonObject args) throws Exception {
  Minecraft mc=world();AbstractContainerMenu menu=guarded(mc,args,op.equals("open"));
  if(op.equals("open")){
   int i=Jsons.integer(args,"inventory_slot",-1);ItemStack stack=mc.player.getInventory().getItem(i);
   checkItem(stack,args);if(!Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem").isInstance(stack.getItem()))throw new IllegalStateException("backpack_item_required");
   BackpackSnapshot expected=backpackSnapshots.get(Jsons.str(args,"stack_token",""));if(expected==null||expected.slot()!=i||!ItemStack.matches(stack,expected.stack()))throw new IllegalStateException("backpack_stack_changed_refresh_observation");
   Object packet=Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackOpenMessage").getConstructor(int.class).newInstance(i);
   Object handler=Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.network.SBPPacketHandler").getField("INSTANCE").get(null);
   call(handler,"sendToServer",new Class<?>[]{Object.class},packet);
   backpackSnapshots.remove(Jsons.str(args,"stack_token",""));
   return submitted(op,menu);
  }
  if(op.equals("sort")){call(menu,"sort");return submitted(op,menu);}
  if(op.equals("select_crafting")){int slot=Jsons.integer(args,"upgrade_slot",-1);Object upgrade=upgradeMap(menu).get(slot);if(!Class.forName(Contract.CRAFTING).isInstance(upgrade))throw new IllegalStateException("crafting_upgrade_not_present");call(menu,"setOpenTabId",new Class<?>[]{int.class},slot);return submitted(op,menu);}
  if(op.startsWith("quick_move")){
   if(Jsons.integer(args,"state_id",-1)!=menu.getStateId())throw new IllegalStateException("menu_state_changed");
   int index=Jsons.integer(args,"slot",-1);if(index<0||index>=slotCount(menu))throw new IllegalArgumentException("slot_out_of_range");Slot slot=menu.getSlot(index);
   checkItem(slot.getItem(),args);String role=role(menu,slot,index);
   MenuGuard.quickMove(role,slot.isActive(),slot.mayPickup(mc.player));
   if(!type.equals("furnace")&&(boolean)call(menu,"isInaccessibleSlot",new Class<?>[]{int.class},index))throw new IllegalStateException("slot_inaccessible");
   if(op.equals("quick_move_packet"))mc.getConnection().send(new ServerboundContainerClickPacket(menu.containerId,menu.getStateId(),index,0,ClickType.QUICK_MOVE,menu.getCarried().copy(),new Int2ObjectOpenHashMap<>()));
   else {if(mc.gameMode==null)throw new IllegalStateException("game_mode_unavailable");mc.gameMode.handleInventoryMouseClick(menu.containerId,index,0,ClickType.QUICK_MOVE,mc.player);}
   return submitted(op,menu);
  }throw new IllegalArgumentException("unsupported_operation");
 }
 private static void checkItem(ItemStack s,JsonObject a) {if(s.isEmpty()||!BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(Jsons.str(a,"expected_item",""))||s.getCount()!=Jsons.integer(a,"expected_count",-1))throw new IllegalStateException("slot_item_or_count_changed");}
 private static JsonObject submitted(String op,AbstractContainerMenu m) {return Jsons.obj("delivery","submitted","operation",op,"menu_id",m.containerId,"server_confirmed",false,"verification","read_mod_observer_after_server_synchronization");}
 private static JsonObject item(ItemStack s) {return Jsons.obj("item_id",BuiltInRegistries.ITEM.getKey(s.getItem()).toString(),"count",s.getCount(),"has_nbt",s.hasTag());}
 private static Object call(Object obj,String name) throws Exception {return call(obj,name,new Class<?>[0]);}
 private static Object call(Object obj,String name,Class<?>[] signature,Object...args) throws Exception {return obj.getClass().getMethod(name,signature).invoke(obj,args);}
}
