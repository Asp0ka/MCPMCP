package dev.mcpmcp.curios;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import top.theillusivec4.curios.api.type.inventory.*;
import top.theillusivec4.curios.common.inventory.*;
import static dev.mcpmcp.curios.CuriosJson.*;
import static dev.mcpmcp.curios.CuriosService.require;

/** Only exact reviewed classes are writable; an unknown wrapper/subclass never inherits trust. */
@SuppressWarnings("deprecation")
final class NativeGuard {
 static boolean audited(ICurioStacksHandler stacks,IDynamicStackHandler handler){return stacks!=null&&stacks.getClass()==CurioStacksHandler.class&&handler!=null&&handler.getClass()==DynamicStackHandler.class;}
 static IDynamicStackHandler handler(CurioSlot slot,ICurioStacksHandler stacks){
  require(slot.getClass()==CurioSlot.class,"curios_unreviewed_slot_wrapper");
  require(stacks!=null&&slot.getIdentifier().equals(stacks.getIdentifier()),"curios_handler_identity_changed");
  IDynamicStackHandler handler=slot.isCosmetic()?stacks.getCosmeticStacks():stacks.getStacks();
  require(audited(stacks,handler)&&slot.getItemHandler()==handler,"curios_unreviewed_handler_wrapper");
  int index=slot.getSlotIndex();require(index>=0&&index<handler.getSlots(),"curios_handler_index_changed");
  require(!slot.isCosmetic()||stacks.hasCosmetic(),"curios_cosmetic_unavailable");
  return handler;
 }
 static void swap(CurioSlot slot,ICurioStacksHandler stacks,Slot hotbarSlot,Player player,ItemStack hotbar,boolean locked){
  IDynamicStackHandler handler=handler(slot,stacks);int index=slot.getSlotIndex();ItemStack curio=slot.getItem();
  require(!locked&&stacks.isVisible()&&slot.isActive(),"curios_slot_locked_or_inactive");
  require(hotbarSlot!=null&&hotbarSlot.getClass()==Slot.class&&hotbarSlot.isActive(),"curios_unreviewed_hotbar_wrapper");
  if(!curio.isEmpty()){
   require(player!=null,"curios_player_required");
   require(player.isCreative()||!EnchantmentHelper.hasBindingCurse(curio),"curios_binding_curse");
   require(slot.mayPickup(player),"curios_may_pickup_denied");
   require(hotbarSlot.mayPlace(curio)&&curio.getCount()<=Math.min(hotbarSlot.getMaxStackSize(),curio.getMaxStackSize()),"curios_hotbar_capacity_or_place_denied");
  }
  if(!hotbar.isEmpty()){
   require(hotbarSlot.mayPickup(player),"curios_hotbar_pickup_denied");
   require(slot.mayPlace(hotbar),"curios_may_place_denied");
   // SlotItemHandler.getMaxStackSize(stack) temporarily writes EMPTY into the handler!
   // Exact DynamicStackHandler inherits ItemStackHandler's min(slotLimit, item max).
   require(hotbar.getCount()<=Math.min(handler.getSlotLimit(index),hotbar.getMaxStackSize()),"curios_whole_stack_capacity_required");
  }
 }
 static JsonObject item(ItemStack stack){
  return obj("item_id",stack.isEmpty()?"minecraft:air":BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),"count",stack.isEmpty()?0:stack.getCount(),"stack_hash",StackIdentity.hash(stack),"name",clip(stack.getHoverName().getString(),256),"damage",stack.getDamageValue(),"max_stack_size",stack.getMaxStackSize(),"has_nbt",stack.hasTag(),"identity_scope","full_saved_stack_plus_int_count");
 }
 static String clip(String value,int n){return value.substring(0,Math.min(n,value.length()));}
 private NativeGuard(){}
}
