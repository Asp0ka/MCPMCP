package dev.mcpmcp.sophisticated;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

/** Ownership shared by observation and quick-move authorization. */
final class PlayerInventorySlots {
 static boolean owns(Slot slot,Container playerInventory) {
  if(slot==null||playerInventory==null)return false;
  int index=slot.getContainerSlot();
  if(index<0||index>=36)return false;
  // Forge SlotItemHandler.container is its shared empty dummy container. Only
  // reviewed concrete classes prove that handler index maps to getInv()[index].
  // Subclasses/custom handlers may remap, filter, or expose upgrade/ghost slots.
  if(slot instanceof SlotItemHandler) {
   if(slot.getClass()!=SlotItemHandler.class)return false;
   IItemHandler handler=((SlotItemHandler)slot).getItemHandler();
   return handler!=null&&handler.getClass()==InvWrapper.class&&((InvWrapper)handler).getInv()==playerInventory;
  }
  return slot.container==playerInventory;
 }
 private PlayerInventorySlots() {}
}
