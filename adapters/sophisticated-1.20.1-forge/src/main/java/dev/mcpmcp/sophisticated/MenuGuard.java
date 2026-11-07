package dev.mcpmcp.sophisticated;

import java.util.*;

final class MenuGuard {
 static void check(int expectedId,String expectedToken,int actualId,String actualToken,boolean cursorEmpty) {
  if(expectedId!=actualId||actualToken==null||!actualToken.equals(expectedToken))throw new IllegalStateException("menu_identity_changed_refresh_observation");
  if(!cursorEmpty)throw new IllegalStateException("cursor_not_empty");
 }
 static String furnaceRole(String name) {
  return switch(name) {
   case "ironfurnaces.container.slots.SlotIronFurnaceInput" -> "input";
   case "ironfurnaces.container.slots.SlotIronFurnaceFuel" -> "fuel";
   case "ironfurnaces.container.slots.SlotIronFurnace" -> "output";
   case "ironfurnaces.container.slots.SlotIronFurnaceInputGenerator" -> "generator_fuel";
   case "ironfurnaces.container.slots.SlotIronFurnaceInputFactory" -> "factory_input";
   case "ironfurnaces.container.slots.SlotIronFurnaceOutputFactory" -> "factory_output";
   case "ironfurnaces.container.slots.SlotIronFurnaceAugmentRed" -> "augment_red";
   case "ironfurnaces.container.slots.SlotIronFurnaceAugmentGreen" -> "augment_green";
   case "ironfurnaces.container.slots.SlotIronFurnaceAugmentBlue" -> "augment_blue";
   default -> "unknown_read_only";
  };
 }
 static void quickMove(String role,boolean active,boolean mayPickup) {
  if(!active||!mayPickup)throw new IllegalStateException("slot_inactive_or_pickup_denied");
  if(!Set.of("storage","player_inventory","crafting_input","crafting_result","input","fuel","output","generator_fuel","factory_input","factory_output").contains(role))throw new IllegalStateException("slot_role_read_only");
 }
 private MenuGuard() {}
}
