package dev.mcpmcp.rs;
import com.refinedmods.refinedstorage.network.grid.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;
import static dev.mcpmcp.rs.GridAccess.*;
import static dev.mcpmcp.rs.AdapterTest.check;
/** Roundtrips through the target JAR encoder. No network/server/game is started. */
final class PacketTest {
 static void run(){
  UUID id=UUID.fromString("12345678-1234-5678-9abc-123456789abc");
  String[] modes={"stack","half","single","shift"};int[] flags={0,1,2,4};
  for(int i=0;i<modes.length;i++){
   Object raw=NativePackets.create(new Command("rs.extract",id.toString(),modes[i],0,false,-1));
   check(raw instanceof GridItemPullMessage,"RS pull packet");FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());
   try{GridItemPullMessage.encode((GridItemPullMessage)raw,b);check(b.readUUID().equals(id)&&b.readInt()==flags[i]&&!b.isReadable(),"actual pull UUID/flag wire");}finally{b.release();}
  }
  for(boolean single:new boolean[]{false,true}){FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());
   try{GridItemInsertHeldMessage.encode((GridItemInsertHeldMessage)NativePackets.create(new Command("rs.insert_held","","",0,single,-1)),b);check(b.readBoolean()==single&&!b.isReadable(),"actual insert single wire");}finally{b.release();}}
  FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());
  try{GridCraftingStartRequestMessage.encode((GridCraftingStartRequestMessage)NativePackets.create(new Command("rs.craft.start",id.toString(),"",17,false,-1)),b);check(b.readUUID().equals(id)&&b.readInt()==17&&!b.readBoolean()&&!b.isReadable(),"actual craft quantity/items-only wire");}finally{b.release();}
  check(NativePackets.create(new Command("rs.crafting.clear","","",0,false,-1)) instanceof GridClearMessage,"actual clear packet");
  boolean refused=false;try{NativePackets.create(new Command("rs.crafting.take","","",0,false,13));}catch(IllegalArgumentException e){refused=true;}check(refused,"no input-to-packet downgrade");
  System.out.println("PacketTest: 9 actual RS packet wire checks passed");
 }
}
