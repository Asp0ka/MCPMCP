package dev.mcpmcp.magic;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import io.netty.channel.*;
import java.util.*;

/** Passive inbound evidence. Does not invoke save/writePacketNBT (some mutate state). */
final class SyncEvidence {
 record Sample(BlockEntityType<?> type,CompoundTag tag,long time){}
 private final Map<BlockPos,Sample> blocks=new LinkedHashMap<>();
 private final Map<Integer,Map<Integer,Integer>> data=new HashMap<>();
 private volatile Connection attached;private volatile long generation;
 private final String mod;
 SyncEvidence(String mod){this.mod=mod;}
 synchronized Sample block(BlockPos p,BlockEntityType<?> type){Sample s=blocks.get(p);return s!=null&&s.type==type?s:null;}
 synchronized Integer menuData(int menu,int key){return data.getOrDefault(menu,Map.of()).get(key);}
 synchronized void clear(){blocks.clear();data.clear();generation++;}
 void attach(){
  Minecraft mc=Minecraft.getInstance();Connection c=mc.getConnection()==null?null:mc.getConnection().getConnection();
  if(c==attached)return;clear();attached=c;if(c==null||c.channel()==null)return;
  Channel channel=c.channel();String name="mcpmcp_"+mod+"_read_evidence";
  channel.eventLoop().execute(()->{if(channel.pipeline().get(name)==null)channel.pipeline().addBefore("packet_handler",name,new ChannelInboundHandlerAdapter(){
   @Override public void channelRead(ChannelHandlerContext ctx,Object msg)throws Exception{
    // Evidence capture must never swallow or transform the game packet.
    try{if(c==attached)capture(msg);}catch(RuntimeException ignored){}
    super.channelRead(ctx,msg);
   }
  });});
 }
 private synchronized void put(BlockPos p,BlockEntityType<?> type,CompoundTag tag){
  if(tag==null||!BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type).getNamespace().equals(mod))return;
  if(blocks.size()>=4096&&!blocks.containsKey(p))blocks.remove(blocks.keySet().iterator().next());
  blocks.put(p.immutable(),new Sample(type,tag.copy(),System.currentTimeMillis()));
 }
 private synchronized void invalidate(BlockPos p){blocks.remove(p);}
 private synchronized void unload(int x,int z){blocks.keySet().removeIf(p->(p.getX()>>4)==x&&(p.getZ()>>4)==z);}
 private void capture(Object p){
  if(p instanceof ClientboundLoginPacket||p instanceof ClientboundRespawnPacket){clear();return;}
  if(p instanceof ClientboundLevelChunkWithLightPacket chunk){unload(chunk.getX(),chunk.getZ());chunk.getChunkData().getBlockEntitiesTagsConsumer(chunk.getX(),chunk.getZ()).accept(this::put);}
  else if(p instanceof ClientboundBlockEntityDataPacket b)put(b.getPos(),b.getType(),b.getTag());
  else if(p instanceof ClientboundBlockUpdatePacket b)invalidate(b.getPos());
  else if(p instanceof ClientboundSectionBlocksUpdatePacket b)b.runUpdates((pos,state)->invalidate(pos));
  else if(p instanceof ClientboundForgetLevelChunkPacket c)unload(c.getX(),c.getZ());
  else if(p instanceof ClientboundOpenScreenPacket o){synchronized(this){data.clear();data.put(o.getContainerId(),new HashMap<>());}}
  else if(p instanceof ClientboundContainerSetDataPacket d){synchronized(this){if(data.containsKey(d.getContainerId()))data.get(d.getContainerId()).put(d.getId(),d.getValue());}}
  else if(p instanceof ClientboundContainerClosePacket c){synchronized(this){data.remove(c.getContainerId());}}
 }
 long generation(){return generation;}
}
