package dev.mcpmcp.rs;
import com.refinedmods.refinedstorage.network.grid.*;
import java.util.UUID;
import static dev.mcpmcp.rs.GridAccess.*;
/** Constructor selection isolated for verification against the actual mod encoder. */
final class NativePackets {
 static Object create(Command c){return switch(c.action()){
  case "rs.extract" -> new GridItemPullMessage(UUID.fromString(c.stackId()),switch(c.mode()){
   case "single"->2;case "half"->1;case "shift"->4;case "stack"->0;default->throw new IllegalArgumentException("rs_invalid_extract_mode");});
  case "rs.insert_held" -> new GridItemInsertHeldMessage(c.single());
  case "rs.craft.start" -> new GridCraftingStartRequestMessage(UUID.fromString(c.stackId()),c.quantity(),false);
  case "rs.crafting.clear" -> new GridClearMessage();
  default -> throw new IllegalArgumentException("rs_packet_action_required");
 };}
 private NativePackets(){}
}
