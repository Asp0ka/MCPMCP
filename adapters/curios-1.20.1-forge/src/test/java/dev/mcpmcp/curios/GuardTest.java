package dev.mcpmcp.curios;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.items.SlotItemHandler;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.event.*;
import top.theillusivec4.curios.common.inventory.*;
import static dev.mcpmcp.curios.AdapterTest.check;
import static dev.mcpmcp.curios.ServiceTest.rejects;

/** Actual target handlers and SlotItemHandler predicates. Unsafe ONLY supplies uninitialized host
 * references (no level/ModLauncher); Forge event result substitutes external mod predicates.
 * This explicitly does not test CuriosApi mixin dispatch, a live player, or server acceptance. */
final class GuardTest {
 static Object unsafe;
 static Object allocate(Class<?> type)throws Exception{if(unsafe==null){Class<?> u=Class.forName("sun.misc.Unsafe");Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=f.get(null);}return unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,type);}
 static void set(Object target,Class<?> type,String name,Object value)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
 abstract static class BasePlayer extends Player {BasePlayer(){super(null,null,0,null);}public boolean isSpectator(){return false;}public boolean isCreative(){return false;}}
 static final class StubPlayer extends BasePlayer {}
 static final class Wrapper extends DynamicStackHandler {Wrapper(){super(1,i->new SlotContext("ring",null,i,false,true));}}
 static CurioSlot slot(CurioStacksHandler g,boolean cosmetic)throws Exception{
  CurioSlot s=(CurioSlot)allocate(CurioSlot.class);var h=cosmetic?g.getCosmeticStacks():g.getStacks();
  set(s,SlotItemHandler.class,"itemHandler",h);set(s,SlotItemHandler.class,"index",0);set(s,CurioSlot.class,"identifier","ring");set(s,CurioSlot.class,"isCosmetic",cosmetic);set(s,CurioSlot.class,"renderStatuses",g.getRenders());set(s,CurioSlot.class,"slotContext",new SlotContext("ring",null,0,cosmetic,true));
  return s;
 }
 static void seedEventLists(Class<?> type)throws Exception{
  if(type==net.minecraftforge.eventbus.api.Event.class)return;seedEventLists(type.getSuperclass());
  var method=net.minecraftforge.eventbus.api.EventListenerHelper.class.getDeclaredMethod("getListenerListInternal",Class.class,boolean.class);method.setAccessible(true);method.invoke(null,type,true);
 }
 static void run()throws Exception{
  seedEventLists(CurioEquipEvent.class);seedEventLists(CurioUnequipEvent.class);net.minecraftforge.common.MinecraftForge.EVENT_BUS.start();
  Player p=(Player)allocate(StubPlayer.class);var owner=(top.theillusivec4.curios.common.capability.CurioInventoryCapability.CurioInventoryWrapper)allocate(top.theillusivec4.curios.common.capability.CurioInventoryCapability.CurioInventoryWrapper.class);set(owner,owner.getClass(),"wearer",p);CurioStacksHandler g=new CurioStacksHandler(owner,"ring",1,true,true,true,ICurio.DropRule.DEFAULT);CurioSlot slot=slot(g,false);SimpleContainer inv=new SimpleContainer(1);Slot hotbar=new Slot(inv,0,0,0);
  ItemStack curio=new ItemStack(Items.COAL,1),incoming=new ItemStack(Items.CHARCOAL,1);g.getStacks().setStackInSlot(0,curio);inv.setItem(0,incoming);
  check(NativeGuard.handler(slot,g)==g.getStacks(),"actual reviewed slot/handler identity");
  check(!NativeGuard.audited(g,new Wrapper()),"unreviewed handler subclass read-only");
  java.util.function.Consumer<CurioEquipEvent> allowEquip=e->e.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
  java.util.function.Consumer<CurioUnequipEvent> allowUnequip=e->e.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
  net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(allowEquip);net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(allowUnequip);
  try{
   String before=StackIdentity.hash(curio),other=StackIdentity.hash(incoming);
   NativeGuard.swap(slot,g,hotbar,p,incoming,false);check(StackIdentity.hash(g.getStacks().getStackInSlot(0)).equals(before)&&StackIdentity.hash(inv.getItem(0)).equals(other),"actual native preflight leaves both stacks untouched");
   rejects(()->NativeGuard.swap(slot,g,hotbar,p,incoming,true),"curios_slot_locked_or_inactive");
   ItemStack tooMany=new ItemStack(Items.CHARCOAL,65);rejects(()->NativeGuard.swap(slot,g,hotbar,p,tooMany,false),"curios_whole_stack_capacity_required");
   g.getStacks().setStackInSlot(0,ItemStack.EMPTY);NativeGuard.swap(slot,g,hotbar,p,incoming,false);check(g.getStacks().getStackInSlot(0).isEmpty(),"equip preflight no source changes");
   g.getStacks().setStackInSlot(0,curio);inv.setItem(0,ItemStack.EMPTY);NativeGuard.swap(slot,g,hotbar,p,ItemStack.EMPTY,false);check(g.getStacks().getStackInSlot(0)==curio,"unequip preflight no writes");
   curio.enchant(Enchantments.BINDING_CURSE,1);rejects(()->NativeGuard.swap(slot,g,hotbar,p,ItemStack.EMPTY,false),"curios_binding_curse");curio.setTag(null);
   CurioSlot cosmetic=slot(g,true);g.getCosmeticStacks().setStackInSlot(0,new ItemStack(Items.COAL));NativeGuard.swap(cosmetic,g,hotbar,p,ItemStack.EMPTY,false);check(NativeGuard.handler(cosmetic,g)==g.getCosmeticStacks(),"cosmetic uses actual separate handler");
  }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(allowEquip);net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(allowUnequip);}
  java.util.function.Consumer<CurioUnequipEvent> deny=e->e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(deny);try{rejects(()->NativeGuard.swap(slot,g,hotbar,p,ItemStack.EMPTY,false),"curios_may_pickup_denied");}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny);}
  java.util.function.Consumer<CurioEquipEvent> denyEquip=e->e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(denyEquip);try{g.getStacks().setStackInSlot(0,ItemStack.EMPTY);inv.setItem(0,incoming);rejects(()->NativeGuard.swap(slot,g,hotbar,p,incoming,false),"curios_may_place_denied");}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(denyEquip);}
 }
}
