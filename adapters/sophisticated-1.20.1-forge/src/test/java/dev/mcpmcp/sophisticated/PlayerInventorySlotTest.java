package dev.mcpmcp.sophisticated;

import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.*;
import net.minecraftforge.items.wrapper.*;

/** Uses actual mapped Minecraft/Forge classes, without a client, world, or network. */
public final class PlayerInventorySlotTest {
 private static int checks;
 private static void check(boolean condition,String label) {checks++;if(!condition)throw new AssertionError(label);}
 private static void denied(String role,boolean active,boolean pickup,String error) {
  checks++;
  try {MenuGuard.quickMove(role,active,pickup);}catch(IllegalStateException e){if(error.equals(e.getMessage()))return;throw e;}
  throw new AssertionError("must reject "+role+" with "+error);
 }
 public static final class UpgradeFixture {
  private final List<Slot> slots;
  UpgradeFixture(Slot slot) {slots=List.of(slot);}
  public List<Slot> getSlots() {return slots;}
 }
 public static final class MenuFixture extends AbstractContainerMenu {
  private final Map<Integer,Object> upgrades=new HashMap<>();
  private final Set<Integer> storage=new HashSet<>();
  MenuFixture() {super(null,47);}
  public Map<Integer,Object> getUpgradeContainers() {return upgrades;}
  public boolean isStorageInventorySlot(int slot) {return storage.contains(slot);}
  @Override public boolean stillValid(Player player) {return true;}
  @Override public ItemStack quickMoveStack(Player player,int slot) {throw new AssertionError("test must never move items");}
 }
 private static void refused(Slot slot,Container playerInventory,String label) throws Exception {
  check(!PlayerInventorySlots.owns(slot,playerInventory),label);
  for(String type:List.of("furnace","storage","backpack")) {
   String role=new LiveAccess(type).role(new MenuFixture(),slot,47,playerInventory);
   check(!role.equals("player_inventory"),label+" role "+type);
   denied(role,true,true,"slot_role_read_only");
  }
 }
 public static void main(String[] args) throws Exception {
  // Only initialize local registries. Full Forge Bootstrap requires a ModLauncher
  // transformed event bus and initializes networking, neither needed by slot tests.
  SharedConstants.tryDetectVersion();
  var flag=Bootstrap.class.getDeclaredField("isBootstrapped");flag.setAccessible(true);flag.setBoolean(null,true);
  net.minecraft.core.registries.BuiltInRegistries.bootStrap();
  Container playerInventory=new net.minecraft.world.entity.player.Inventory(null);
  Slot wrapped=new SlotItemHandler(new InvWrapper(playerInventory),1,0,0);
  playerInventory.setItem(1,new ItemStack(net.minecraft.world.item.Items.RAW_IRON,3));
  check(wrapped.getItem()==playerInventory.getItem(1)&&wrapped.getItem().getCount()==3,"observed raw_iron3 uses same underlying inventory slot1");
  check(wrapped.mayPickup(null),"native InvWrapper pickup allows the observed source");
  playerInventory.setItem(1,ItemStack.EMPTY);
  check(!wrapped.mayPickup(null),"native empty source pickup still denies");
  check(wrapped.container!=playerInventory,"Forge handler slot uses a dummy container");
  check(PlayerInventorySlots.owns(wrapped,playerInventory),"real InvWrapper(playerInventory) must be recognized (observed menu47/container1)");
  for(String type:List.of("furnace","storage","backpack")) {
   LiveAccess live=new LiveAccess(type);
   for(int i=0;i<36;i++)for(Slot slot:List.of(new Slot(playerInventory,i,0,0),new SlotItemHandler(new InvWrapper(playerInventory),i,0,0))) {
    // Menu indexes deliberately differ from inventory indexes; geometry is irrelevant.
    String role=live.role(new MenuFixture(),slot,46+i,playerInventory);
    check(role.equals("player_inventory"),type+" direct/wrapped inventory index "+i);
    MenuGuard.quickMove(role,slot.isActive(),true);
   }
  }
  refused(new Slot(new SimpleContainer(41),1,0,0),playerInventory,"other direct inventory");
  refused(new SlotItemHandler(new InvWrapper(new SimpleContainer(41)),1,0,0),playerInventory,"other wrapped inventory");
  refused(new SlotItemHandler(new ItemStackHandler(41),1,0,0),playerInventory,"storage/upgrade handler is not a player");
  refused(new SlotItemHandler(new InvWrapper(playerInventory),1,0,0){},playerInventory,"unreviewed handler slot subclass");
  refused(new SlotItemHandler(new InvWrapper(playerInventory){},1,0,0),playerInventory,"unreviewed wrapper subclass");
  refused(new SlotItemHandler(new RangedWrapper(new InvWrapper(playerInventory),0,36),1,0,0),playerInventory,"unreviewed range remapping");
  Container deceptive=new SimpleContainer(41){@Override public boolean equals(Object other){return true;}};
  refused(new SlotItemHandler(new InvWrapper(deceptive),1,0,0),playerInventory,"equals is not ownership identity");
  for(int i:new int[]{-1,36,40,41}) {
   refused(new Slot(playerInventory,i,0,0),playerInventory,"direct slot outside main/hotbar "+i);
   refused(new SlotItemHandler(new InvWrapper(playerInventory),i,0,0),playerInventory,"wrapped slot outside main/hotbar "+i);
  }
  check(!PlayerInventorySlots.owns(null,playerInventory),"null slot fails closed");
  check(!PlayerInventorySlots.owns(wrapped,null),"null player fails closed");
  for(String type:List.of("storage","backpack")) {
   LiveAccess live=new LiveAccess(type);MenuFixture menu=new MenuFixture();
   // A registered upgrade wins even if it exposes the player's underlying handler.
   menu.upgrades.put(0,new UpgradeFixture(wrapped));menu.storage.add(47);
   String role=live.role(menu,wrapped,47,playerInventory);
   check(role.equals("upgrade_settings_read_only"),"upgrade ownership takes precedence in "+type);
   denied(role,true,true,"slot_role_read_only");
   Slot upgrade=new SlotItemHandler(new ItemStackHandler(1),0,0,0);
   menu.upgrades.clear();menu.upgrades.put(0,new UpgradeFixture(upgrade));
   check(live.role(menu,upgrade,47,playerInventory).equals("upgrade_settings_read_only"),"ordinary upgrade stays read-only");
   denied(live.role(menu,upgrade,47,playerInventory),true,true,"slot_role_read_only");
   menu.upgrades.clear();menu.storage.clear();
   check(live.role(menu,upgrade,47,playerInventory).equals("upgrade_install_or_extra_read_only"),"upgrade installation stays read-only");
   menu.storage.add(0);
   check(live.role(menu,upgrade,0,playerInventory).equals("storage"),"native storage role retained");
  }
  String playerRole=new LiveAccess("furnace").role(new MenuFixture(),wrapped,47,playerInventory);
  denied(playerRole,false,true,"slot_inactive_or_pickup_denied");
  denied(playerRole,true,false,"slot_inactive_or_pickup_denied");
  Slot locked=new Slot(playerInventory,1,0,0){@Override public boolean mayPickup(Player player){return false;}};
  denied(new LiveAccess("backpack").role(new MenuFixture(),locked,47,playerInventory),true,locked.mayPickup(null),"slot_inactive_or_pickup_denied");
  for(String augment:List.of("Red","Green","Blue"))denied(MenuGuard.furnaceRole("ironfurnaces.container.slots.SlotIronFurnaceAugment"+augment),true,true,"slot_role_read_only");
  // Important 2: same menu/state/item/count must not authorize NBT replacement.
  var itemView=LiveAccess.class.getDeclaredMethod("item",ItemStack.class);itemView.setAccessible(true);
  var itemGuard=LiveAccess.class.getDeclaredMethod("checkItem",ItemStack.class,com.google.gson.JsonObject.class);itemGuard.setAccessible(true);
  ItemStack observed=new ItemStack(net.minecraft.world.item.Items.COAL,3);
  observed.getOrCreateTag().putString("review_identity","A");
  playerInventory.setItem(1,observed);
  var view=(com.google.gson.JsonObject)itemView.invoke(null,wrapped.getItem());
  var oldArgs=dev.mcpmcp.core.Jsons.obj("expected_item","minecraft:coal","expected_count",3,"expected_stack_hash",view.get("stack_hash").getAsString());
  ItemStack replacement=observed.copy();replacement.getOrCreateTag().putString("review_identity","B");
  playerInventory.setItem(1,replacement);
  boolean rejected=false;
  try {itemGuard.invoke(null,wrapped.getItem(),oldArgs);}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof IllegalStateException)rejected=true;else throw e;}
  check(rejected,"same item/count with NBT A -> B must reject on newly authorized wrapped player slot");
  System.out.println("PASS PlayerInventorySlotTest checks="+checks+" actual_forge_wrappers=true adapters=3 main_and_hotbar=36 client_started=false");
 }
}

