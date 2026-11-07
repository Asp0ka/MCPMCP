package dev.mcpmcp.sophisticated;

import com.google.gson.*;
import dev.mcpmcp.core.Jsons;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.InvWrapper;

/** Real saved Minecraft stacks + Forge capability dispatcher; only submission is intercepted. */
public final class StackIdentityTest {
 private static int checks;
 private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 @FunctionalInterface interface Attempt {void run() throws Exception;}
 private static void rejects(Attempt attempt,String error) throws Exception {
  checks++;try{attempt.run();}catch(IllegalStateException e){if(error.equals(e.getMessage()))return;throw e;}
  throw new AssertionError("expected "+error);
 }
 public static final class Menu extends AbstractContainerMenu {
  final Map<Integer,Object> upgrades=new HashMap<>();boolean inaccessible;
  Menu(Slot slot){super(null,47);addSlot(slot);}
  public int getTotalSlotsNumber(){return slots.size();}
  public Map<Integer,Object> getUpgradeContainers(){return upgrades;}
  public boolean isStorageInventorySlot(int index){return false;}
  public boolean isInaccessibleSlot(int index){return inaccessible;}
  @Override public boolean stillValid(Player player){return true;}
  @Override public ItemStack quickMoveStack(Player player,int slot){throw new AssertionError("no actual item movement in offline test");}
 }
 static final class Delivery implements LiveAccess.QuickMoveSender {
  int calls;boolean packet;int slot;
  public void submit(boolean packet,int slot){calls++;this.packet=packet;this.slot=slot;}
 }
 static final class SavedCapability implements ICapabilitySerializable<CompoundTag> {
  int identity;SavedCapability(int identity){this.identity=identity;}
  public <T> LazyOptional<T> getCapability(Capability<T> capability,net.minecraft.core.Direction side){return LazyOptional.empty();}
  public CompoundTag serializeNBT(){CompoundTag tag=new CompoundTag();tag.putInt("identity",identity);return tag;}
  public void deserializeNBT(CompoundTag tag){identity=tag.getInt("identity");}
 }
 private static SavedCapability attach(ItemStack stack,int identity) throws Exception {
  SavedCapability cap=new SavedCapability(identity);
  // Avoid ModLauncher/event-bus boot; use the real dispatcher + serializer ItemStack.save calls.
  var field=CapabilityProvider.class.getDeclaredField("capabilities");field.setAccessible(true);
  field.set(stack,new CapabilityDispatcher(Map.of(ResourceLocation.tryParse("review:identity"),cap),List.of()));
  for(String name:List.of("isLazy","initialized")){var f=CapabilityProvider.class.getDeclaredField(name);f.setAccessible(true);f.setBoolean(stack,name.equals("initialized"));}
  return cap;
 }
 private static ItemStack tagged(String identity){ItemStack stack=new ItemStack(Items.COAL,3);stack.getOrCreateTag().putString("review_identity",identity);return stack;}
 private static JsonObject observed(Menu menu) throws Exception {
  var view=LiveAccess.class.getDeclaredMethod("item",ItemStack.class);view.setAccessible(true);
  JsonObject item=(JsonObject)view.invoke(null,menu.getSlot(0).getItem());
  check(item.get("stack_hash").getAsString().matches("[0-9a-f]{64}"),"observer emits fixed full hash");
  return Jsons.obj("menu_id",47,"menu_token","same-menu","slot",0,"state_id",menu.getStateId(),"expected_item",item.get("item_id").getAsString(),"expected_count",item.get("count").getAsInt(),"expected_stack_hash",item.get("stack_hash").getAsString());
 }
 private static void blocked(LiveAccess live,String op,Menu menu,JsonObject args,Container inventory,String error) throws Exception {
  Delivery delivery=new Delivery();
  rejects(()->live.quickMove(op,menu,args,null,inventory,delivery),error);
  check(delivery.calls==0,"neither packet nor native dispatch on rejected identity");
 }
 private static void accepted(LiveAccess live,String op,Menu menu,JsonObject args,Container inventory) throws Exception {
  Contract.validate(args,op);Delivery delivery=new Delivery();
  JsonObject result=live.quickMove(op,menu,args,null,inventory,delivery);
  check(delivery.calls==1&&delivery.packet==op.equals("quick_move_packet")&&delivery.slot==0,"exactly one intended delivery route");
  check(result.get("delivery").getAsString().equals("submitted")&&!result.get("server_confirmed").getAsBoolean(),"no server success claim");
 }
 public static void main(String[] args) throws Exception {
  SharedConstants.tryDetectVersion();
  var boot=Bootstrap.class.getDeclaredField("isBootstrapped");boot.setAccessible(true);boot.setBoolean(null,true);
  net.minecraft.core.registries.BuiltInRegistries.bootStrap();
  for(String type:List.of("storage","backpack","furnace"))for(String op:List.of("quick_move","quick_move_packet"))for(boolean wrapped:List.of(false,true)){
   LiveAccess live=new LiveAccess(type);Container inventory=new Inventory(null);
   Slot slot=wrapped?new SlotItemHandler(new InvWrapper(inventory),1,0,0):new Slot(inventory,1,0,0);
   Menu menu=new Menu(slot);
   inventory.setItem(1,tagged("A"));JsonObject expected=observed(menu);int state=menu.getStateId();
   accepted(live,op,menu,expected,inventory);
   inventory.setItem(1,tagged("B"));
   check(menu.getStateId()==state&&menu.containerId==47,"repro keeps same menu and state id");
   blocked(live,op,menu,expected,inventory,"slot_stack_changed_refresh_observation");
   inventory.setItem(1,tagged("A"));inventory.getItem(1).getOrCreateTag().putString("review_identity","mutated");
   blocked(live,op,menu,expected,inventory,"slot_stack_changed_refresh_observation");
   // Correct no-tag and explicit empty-tag identities both work; neither is a wildcard.
   inventory.setItem(1,new ItemStack(Items.COAL,3));JsonObject noTag=observed(menu);accepted(live,op,menu,noTag,inventory);
   inventory.getItem(1).setTag(new CompoundTag());JsonObject emptyTag=observed(menu);accepted(live,op,menu,emptyTag,inventory);
   check(!noTag.get("expected_stack_hash").equals(emptyTag.get("expected_stack_hash")),"absent and explicit empty tags remain distinct saved identities");
   blocked(live,op,menu,noTag,inventory,"slot_stack_changed_refresh_observation");
   inventory.getItem(1).getOrCreateTag().putString("review_identity","added");
   blocked(live,op,menu,emptyTag,inventory,"slot_stack_changed_refresh_observation");
   inventory.setItem(1,new ItemStack(Items.COAL,3));
   blocked(live,op,menu,expected,inventory,"slot_stack_changed_refresh_observation");
   // Capability-only changes are visible even while has_nbt stays false.
   SavedCapability cap=attach(inventory.getItem(1),1);
   check(!inventory.getItem(1).hasTag()&&inventory.getItem(1).save(new CompoundTag()).getCompound("ForgeCaps").getCompound("review:identity").getInt("identity")==1,"actual Forge dispatcher participates in save with no ordinary NBT");
   JsonObject capArgs=observed(menu);accepted(live,op,menu,capArgs,inventory);cap.identity=2;
   blocked(live,op,menu,capArgs,inventory,"slot_stack_changed_refresh_observation");
   inventory.setItem(1,new ItemStack(Items.COAL,3));attach(inventory.getItem(1),1);accepted(live,op,menu,capArgs,inventory);
   JsonObject missing=capArgs.deepCopy();missing.remove("expected_stack_hash");
   blocked(live,op,menu,missing,inventory,"slot_stack_changed_refresh_observation");
   inventory.getItem(1).setCount(4);blocked(live,op,menu,capArgs,inventory,"slot_item_or_count_changed");
   inventory.setItem(1,new ItemStack(Items.CHARCOAL,3));blocked(live,op,menu,capArgs,inventory,"slot_item_or_count_changed");
   inventory.setItem(1,ItemStack.EMPTY);blocked(live,op,menu,capArgs,inventory,"slot_item_or_count_changed");
   inventory.setItem(1,tagged("A"));JsonObject stale=observed(menu);menu.incrementStateId();
   blocked(live,op,menu,stale,inventory,"menu_state_changed");
   JsonObject fresh=observed(menu);
   if(!type.equals("furnace")){
    menu.inaccessible=true;blocked(live,op,menu,fresh,inventory,"slot_inaccessible");menu.inaccessible=false;
    menu.upgrades.put(0,new PlayerInventorySlotTest.UpgradeFixture(slot));
    blocked(live,op,menu,fresh,inventory,"slot_role_read_only");menu.upgrades.clear();
   }
   accepted(live,op,menu,fresh,inventory);
  }
  check(StackIdentity.hash(ItemStack.EMPTY).matches("[0-9a-f]{64}"),"empty carried cursor and empty observed slots have a valid identity");
  ItemStack left=tagged("same"),right=tagged("same");
  left.getOrCreateTag().putInt("Aa",1);left.getOrCreateTag().putInt("BB",2);
  right.getOrCreateTag().putInt("BB",2);right.getOrCreateTag().putInt("Aa",1);
  check(StackIdentity.hash(left).equals(StackIdentity.hash(right)),"compound insertion order does not change hash");
  right.getOrCreateTag().putByte("Aa",(byte)1);
  check(!StackIdentity.hash(left).equals(StackIdentity.hash(right)),"numeric NBT tag type is part of identity");
  CompoundTag nested=new CompoundTag();nested.putInt("inner",1);
  CompoundTag tail=new CompoundTag();tail.putString("tail","different");
  ListTag list=new ListTag();list.add(nested);list.add(tail);
  left.getOrCreateTag().put("list",list);String before=StackIdentity.hash(left);
  nested.putInt("inner",2);check(!before.equals(StackIdentity.hash(left)),"nested compound within list is included");
  before=StackIdentity.hash(left);Collections.swap(list,0,1);
  check(!before.equals(StackIdentity.hash(left)),"list ordering is part of identity");
  ItemStack large=new ItemStack(Items.COAL,1);String one=StackIdentity.hash(large);large.setCount(257);
  check(!one.equals(StackIdentity.hash(large)),"full integer count avoids save Count byte wraparound");
  System.out.println("PASS StackIdentityTest checks="+checks+" adapters=3 routes=2 ownership_forms=2 full_nbt=true forge_caps=true no_client_world_network=true");
 }
}
