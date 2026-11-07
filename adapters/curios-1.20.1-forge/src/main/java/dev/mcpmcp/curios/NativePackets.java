package dev.mcpmcp.curios;

import com.google.gson.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.common.network.client.*;
import static dev.mcpmcp.curios.CuriosJson.*;

/** Constructors/fields inspected in the exact target JAR; no arbitrary reflection or payload API. */
final class NativePackets {
 static Object create(String action,JsonObject a){return switch(action){
  case "curios.open" -> new CPacketOpenCurios(ItemStack.EMPTY);
  case "curios.page" -> new CPacketPage(integer(a,"menu_id",-1),integer(a,"page",-1)>integer(a,"expected_page",-1));
  case "curios.cosmetics.set" -> new CPacketToggleCosmetics(integer(a,"menu_id",-1));
  case "curios.render.set" -> new CPacketToggleRender(str(a,"identifier",""),integer(a,"index",-1));
  case "curios.swap" -> new ServerboundContainerClickPacket(integer(a,"menu_id",-1),integer(a,"state_id",-1),integer(a,"menu_slot",-1),integer(a,"hotbar_slot",-1),ClickType.SWAP,ItemStack.EMPTY,new Int2ObjectOpenHashMap<>());
  default -> throw new IllegalArgumentException("curios_unsupported_packet");
 };}
 private NativePackets(){}
}
