package dev.mcpmcp.curios;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import top.theillusivec4.curios.api.*;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.*;
import top.theillusivec4.curios.client.gui.CuriosScreenV2;
import top.theillusivec4.curios.common.inventory.*;
import top.theillusivec4.curios.common.inventory.container.CuriosContainerV2;
import top.theillusivec4.curios.common.network.NetworkHandler;
import static dev.mcpmcp.curios.CuriosJson.*;
import static dev.mcpmcp.curios.CuriosService.require;

/** Client-thread reads and reviewed native inputs. No direct inventory mutation, server API or registry scan. */
@SuppressWarnings("removal")
final class LiveCuriosAccess implements CuriosAccess {
 private final RuntimePin pin=new RuntimePin();
 private final SessionIdentity identity=new SessionIdentity();
 private Object awaitingMenu;
 private List<Slot> awaitingSlots;
 private ICuriosItemHandler current(){
  Minecraft mc=Minecraft.getInstance();require(mc.isSameThread(),"curios_client_thread_required");pin.verify();
  if(mc.level==null||mc.player==null||mc.getConnection()==null){identity.clear();awaitingMenu=null;awaitingSlots=null;throw new IllegalStateException("curios_not_in_world");}
  return CuriosApi.getCuriosInventory(mc.player).orElseThrow(()->new IllegalStateException("curios_inventory_unavailable"));
 }
 private CuriosContainerV2 menu(){
  Minecraft mc=Minecraft.getInstance();
  require(mc.player.containerMenu.getClass()==CuriosContainerV2.class&&mc.screen!=null&&mc.screen.getClass()==CuriosScreenV2.class,"curios_v2_menu_required");
  CuriosContainerV2 menu=(CuriosContainerV2)mc.player.containerMenu;
  require(((CuriosScreenV2)mc.screen).getMenu()==menu&&menu.player==mc.player,"curios_screen_menu_mismatch");
  require(menu.curiosHandler==current(),"curios_menu_capability_changed");return menu;
 }
 public JsonObject capture(){
  ICuriosItemHandler capability=current();Minecraft mc=Minecraft.getInstance();AbstractContainerMenu menu=mc.player.containerMenu;
  String kind="other";CuriosContainerV2 curios=null;
  if(menu.getClass()==CuriosContainerV2.class&&mc.screen!=null&&mc.screen.getClass()==CuriosScreenV2.class){curios=menu();kind="curios_v2";}
  else if(menu==mc.player.inventoryMenu&&mc.screen==null)kind="player_closed";
  if(awaitingMenu!=menu){awaitingMenu=null;awaitingSlots=null;}
  else if(awaitingSlots!=null&&!sameSlots(awaitingSlots,menu.slots)){awaitingMenu=null;awaitingSlots=null;}
  JsonArray rows=new JsonArray(),inventory=new JsonArray();
  for(int i=0;i<36;i++)inventory.add(obj("inventory_slot",i,"hotbar",i<9,"item",NativeGuard.item(mc.player.getInventory().getItem(i))));
  List<Object> refs=new ArrayList<>();StringBuilder layout=new StringBuilder(kind);
  Map<String,ICurioStacksHandler> groups=groups(capability);
  for(var entry:groups.entrySet()){
   ICurioStacksHandler g=entry.getValue();refs.add(g);refs.add(g.getStacks());refs.add(g.getCosmeticStacks());
   layout.append('|').append(entry.getKey()).append(':').append(g.getStacks().getSlots()).append(':').append(g.getCosmeticStacks().getSlots()).append(':').append(g.isVisible()).append(':').append(g.hasCosmetic()).append(':').append(capability.getLockedSlots().contains(entry.getKey()));
  }
  int page=-1,pages=0;boolean cosmetics=false,hasCosmetics=false;
  if(curios!=null){
   require(menu.slots.size()<=512,"curios_menu_slot_budget");require(curios.grid!=null&&curios.grid.size()<=8,"curios_grid_invalid");
   page=curios.currentPage;pages=curios.totalPages;cosmetics=curios.isViewingCosmetics;hasCosmetics=curios.hasCosmetics;
   layout.append(";page=").append(page).append('/').append(pages).append(";cosmetic=").append(cosmetics).append(";grid=").append(curios.grid).append(";panel=").append(curios.panelWidth);
   for(int i=0;i<menu.slots.size();i++){
    Slot slot=menu.slots.get(i);refs.add(slot);
    JsonObject row=obj("menu_slot",i,"slot_class",slot.getClass().getName(),"active",slot.isActive(),"item",NativeGuard.item(slot.getItem()));
    if(slot instanceof CurioSlot cs){
     ICurioStacksHandler g=groups.get(cs.getIdentifier());boolean audited=isAudited(cs,g);int index=cs.getSlotIndex();
     boolean locked=capability.getLockedSlots().contains(cs.getIdentifier());
     row.addProperty("role","curio");row.addProperty("identifier",cs.getIdentifier());row.addProperty("index",index);row.addProperty("cosmetic",cs.isCosmetic());row.addProperty("audited",audited);row.addProperty("locked",locked||g==null||!g.isVisible());
     row.addProperty("can_toggle_render",audited&&!cs.isCosmetic()&&!locked&&cs.canToggleRender());
     row.add("render",g!=null&&index>=0&&index<g.getRenders().size()?GSON.toJsonTree(g.getRenders().get(index)):JsonNull.INSTANCE);
     // Observation must not fire unequip callbacks after hashing a row. Predicates run in candidates/preflight.
     row.add("may_pickup",JsonNull.INSTANCE);
     row.add("max_stack_size",audited?GSON.toJsonTree(cs.getMaxStackSize()):JsonNull.INSTANCE);
     layout.append(";slot=").append(i).append(':').append(cs.getIdentifier()).append(':').append(index).append(':').append(cs.isCosmetic());
    }else if(slot.getClass()==Slot.class&&slot.container==mc.player.getInventory()&&slot.getSlotIndex()>=0&&slot.getSlotIndex()<36){row.addProperty("role","player_inventory");row.addProperty("inventory_slot",slot.getSlotIndex());}
    else row.addProperty("role","read_only");
    rows.add(row);
   }
  }
  String session=identity.update(mc.level,mc.player,mc.getConnection(),menu,mc.screen,capability,refs,layout.toString());
  return obj("source","curios","available",true,"session",session,"menu_instance",identity.instance(),"menu_id",menu.containerId,"state_id",menu.getStateId(),"kind",kind,"screen_class",mc.screen==null?"":mc.screen.getClass().getName(),"menu_class",menu.getClass().getName(),"cursor_empty",menu.getCarried().isEmpty(),"cursor",NativeGuard.item(menu.getCarried()),"current_page",page,"total_pages",pages,"viewing_cosmetics",cosmetics,"has_cosmetics",hasCosmetics,"grid",curios==null?List.of():List.copyOf(curios.grid),"view_sync_pending",awaitingMenu==menu,"slots",rows,"inventory",inventory,"observed_at_ms",System.currentTimeMillis(),"server_confirmed",false);
 }
 public JsonArray types(){
  ICuriosItemHandler cap=current();Map<String,ICurioStacksHandler> groups=groups(cap);Map<String,top.theillusivec4.curios.api.type.ISlotType> definitions=CuriosApi.getEntitySlots(Minecraft.getInstance().player);
  require(definitions.size()<=256,"curios_type_budget");Set<String> names=new TreeSet<>(definitions.keySet());names.addAll(groups.keySet());require(names.size()<=256,"curios_type_budget");JsonArray result=new JsonArray();
  for(String id:names){var type=definitions.get(id);ICurioStacksHandler g=groups.get(id);result.add(obj("identifier",id,"defined",type!=null,"present",g!=null,"slots",g==null?0:g.getStacks().getSlots(),"cosmetic_slots",g==null||!g.hasCosmetic()?0:g.getCosmeticStacks().getSlots(),"base_slots",type==null?JsonNull.INSTANCE:type.getSize(),"definition_locked",type==null?JsonNull.INSTANCE:type.isLocked(),"visible",g==null?type!=null&&type.isVisible():g.isVisible(),"has_cosmetic",g==null?type!=null&&type.hasCosmetic():g.hasCosmetic(),"can_toggle_render",g==null?type!=null&&type.canToggleRendering():g.canToggleRendering(),"order",type==null?JsonNull.INSTANCE:type.getOrder(),"icon",type==null?JsonNull.INSTANCE:type.getIcon().toString(),"validators",type==null?List.of():type.getValidators().stream().map(Object::toString).sorted().limit(64).toList(),"definition_scope","client_synchronized; base locked is not an equipment legality verdict"));}
  return result;
 }
 public JsonArray slots(){
  ICuriosItemHandler cap=current();JsonArray rows=new JsonArray();
  for(var e:groups(cap).entrySet()){
   ICurioStacksHandler g=e.getValue();
   for(boolean cosmetic:new boolean[]{false,true}){
    if(cosmetic&&!g.hasCosmetic())continue;IDynamicStackHandler h=cosmetic?g.getCosmeticStacks():g.getStacks();
    for(int i=0;i<h.getSlots();i++){
     boolean render=i<g.getRenders().size()&&g.getRenders().get(i);boolean audited=NativeGuard.audited(g,h);
     JsonObject row=obj("identifier",e.getKey(),"index",i,"cosmetic",cosmetic,"visible",g.isVisible(),"locked",cap.getLockedSlots().contains(e.getKey()),"handler_slots",h.getSlots(),"render",render,"can_toggle_render",!cosmetic&&g.canToggleRendering(),"audited",audited,"item",NativeGuard.item(h.getStackInSlot(i)),"capacity",audited?h.getSlotLimit(i):JsonNull.INSTANCE,"menu_slot",JsonNull.INSTANCE,"can_equip",JsonNull.INSTANCE,"can_unequip",JsonNull.INSTANCE);
     rows.add(row);
    }
   }
  }
  // Exact currently registered slots only. No access to private off-page ProxySlots.
  Minecraft mc=Minecraft.getInstance();if(mc.player.containerMenu.getClass()==CuriosContainerV2.class&&mc.screen!=null&&mc.screen.getClass()==CuriosScreenV2.class){CuriosContainerV2 menu=menu();for(int m=0;m<menu.slots.size();m++)if(menu.slots.get(m) instanceof CurioSlot cs)for(JsonElement e:rows){JsonObject row=e.getAsJsonObject();if(str(row,"identifier","").equals(cs.getIdentifier())&&integer(row,"index",-1)==cs.getSlotIndex()&&bool(row,"cosmetic",false)==cs.isCosmetic())row.addProperty("menu_slot",m);}}
  return rows;
 }
 public JsonArray candidates(JsonObject args){
  ICuriosItemHandler cap=current();ItemStack candidate=Minecraft.getInstance().player.getInventory().getItem(integer(args,"inventory_slot",-1));require(!candidate.isEmpty(),"curios_candidate_empty");
  JsonArray rows=slots();boolean cosmetic=bool(args,"cosmetic",false);int matched=0,offset=integer(args,"offset",0),limit=integer(args,"limit",32);
  for(JsonElement e:rows){JsonObject row=e.getAsJsonObject();
   boolean filter=bool(row,"cosmetic",false)==cosmetic&&(!args.has("identifier")||str(row,"identifier","").equals(str(args,"identifier","")))&&(!args.has("index")||integer(row,"index",-1)==integer(args,"index",-2));
   if(!filter)continue;
   row.addProperty("candidate_inventory_slot",integer(args,"inventory_slot",-1));row.add("candidate",NativeGuard.item(candidate));row.addProperty("rules_scope","client_predicate_hint_not_server_acceptance");
   if(matched++<offset||matched>offset+limit)continue;
   ICurioStacksHandler g=cap.getCurios().get(str(row,"identifier",""));IDynamicStackHandler h=cosmetic?g.getCosmeticStacks():g.getStacks();int index=integer(row,"index",-1);
   if(!NativeGuard.audited(g,h))continue;
   try{row.addProperty("can_equip",h.isItemValid(index,candidate));row.addProperty("can_unequip",h.getStackInSlot(index).isEmpty()||!h.extractItem(index,1,true).isEmpty());}catch(RuntimeException ex){row.add("can_equip",JsonNull.INSTANCE);row.add("can_unequip",JsonNull.INSTANCE);row.addProperty("rules_error","predicate_unavailable");}
  }
  JsonArray filtered=new JsonArray();for(JsonElement row:rows)if(row.getAsJsonObject().has("candidate"))filtered.add(row);return filtered;
 }
 public void preflight(String action,JsonObject args){
  JsonObject frame=capture();CuriosService.validateFrame(action,args,frame);
  Minecraft mc=Minecraft.getInstance();
  if(action.equals("curios.open")){require(!top.theillusivec4.curios.common.CuriosConfig.SERVER.enableLegacyMenu.get(),"curios_legacy_menu_unsupported");return;}
  CuriosContainerV2 menu=menu();require(action.equals("curios.close")||menu.stillValid(mc.player),"curios_menu_invalid");
  require(action.equals("curios.close")||menu.curiosHandler.getClass()==top.theillusivec4.curios.common.capability.CurioInventoryCapability.CurioInventoryWrapper.class,"curios_unreviewed_capability_wrapper");
  if(action.equals("curios.swap")){
   Slot raw=menu.slots.get(integer(args,"menu_slot",-1));require(raw instanceof CurioSlot,"curios_real_curio_slot_required");CurioSlot slot=(CurioSlot)raw;
   Slot hotbar=findHotbar(menu,integer(args,"hotbar_slot",-1));
   NativeGuard.swap(slot,menu.curiosHandler.getCurios().get(slot.getIdentifier()),hotbar,mc.player,mc.player.getInventory().getItem(integer(args,"hotbar_slot",-1)),menu.curiosHandler.getLockedSlots().contains(slot.getIdentifier()));
  }
 }
 public void submit(String action,JsonObject args){
  // Revalidate after native predicate/event callbacks; a handler is synchronous on client thread.
  JsonObject frame=capture();CuriosService.validateFrame(action,args,frame);Minecraft mc=Minecraft.getInstance();
  if(action.equals("curios.close")){menu();mc.player.closeContainer();return;}
  Object packet=NativePackets.create(action,args);
  if(action.equals("curios.cosmetics.set")){
   CuriosContainerV2 menu=menu();menu.toggleCosmetics();
   // Native button also toggles local view. Block writes until SPacketPage rebuilds it again.
   awaitingMenu=menu;awaitingSlots=new ArrayList<>(menu.slots);
  }
  if(packet instanceof Packet<?> vanilla)mc.getConnection().send(vanilla);else NetworkHandler.INSTANCE.sendToServer(packet);
 }
 private static Slot findHotbar(CuriosContainerV2 menu,int index){
  Slot found=null;for(Slot slot:menu.slots)if(slot.getClass()==Slot.class&&slot.container==menu.player.getInventory()&&slot.getSlotIndex()==index){require(found==null,"curios_ambiguous_hotbar_slot");found=slot;}return found;
 }
 private static boolean isAudited(CurioSlot slot,ICurioStacksHandler g){try{NativeGuard.handler(slot,g);return true;}catch(IllegalArgumentException ex){return false;}}
 private static boolean sameSlots(List<Slot> a,List<Slot> b){if(a.size()!=b.size())return false;for(int i=0;i<a.size();i++)if(a.get(i)!=b.get(i))return false;return true;}
 private static Map<String,ICurioStacksHandler> groups(ICuriosItemHandler cap){
  Map<String,ICurioStacksHandler> raw=cap.getCurios();require(raw!=null&&raw.size()<=256,"curios_type_budget");Map<String,ICurioStacksHandler> result=new TreeMap<>();int rows=0;
  for(var e:raw.entrySet()){
   require(e.getKey()!=null&&e.getKey().length()<=128&&e.getValue()!=null,"curios_invalid_handler");ICurioStacksHandler g=e.getValue();int normal=g.getStacks().getSlots(),cosmetic=g.getCosmeticStacks().getSlots();
   require(normal>=0&&normal<=4096&&cosmetic>=0&&cosmetic<=4096,"curios_slot_budget");rows+=normal+(g.hasCosmetic()?cosmetic:0);require(rows<=4096,"curios_slot_budget");result.put(e.getKey(),g);
  }
  return result;
 }
}
