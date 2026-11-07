package dev.mcpmcp.core;
import java.util.Set;
/** Minimum policy for untrusted JSON packet constructors. Declarations cannot lower it. */
public final class PacketPolicy {
 private static final String PREFIX="net.minecraft.network.protocol.game.";
 private static final Set<String> NORMAL=Set.of("ServerboundSetCarriedItemPacket","ServerboundSwingPacket","ServerboundContainerClosePacket","ServerboundClientInformationPacket","ServerboundKeepAlivePacket","ServerboundPongPacket");
 private static final Set<String> DENIED=Set.of("ServerboundSetCommandBlockPacket","ServerboundSetCommandMinecartPacket","ServerboundSetStructureBlockPacket","ServerboundSetJigsawBlockPacket","ServerboundJigsawGeneratePacket","ServerboundCustomPayloadPacket","ServerboundEditBookPacket");
 public static String permission(String name){if(!name.startsWith(PREFIX))return "hack";String simple=name.substring(PREFIX.length());if(DENIED.contains(simple))return "denied";if(NORMAL.contains(simple))return "normal";if(simple.contains("Command")||simple.equals("ServerboundChatPacket")||simple.equals("ServerboundSetCreativeModeSlotPacket"))return "cheat";return "hack";}
 private PacketPolicy(){}
}
