package dev.mcpmcp.curios;
import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ClickType;
import top.theillusivec4.curios.common.network.client.*;
import static dev.mcpmcp.core.Jsons.*;
import static dev.mcpmcp.curios.AdapterTest.check;
/** Real Minecraft/Forge NBT, actual target packet encoders; no ModLauncher/server/network. */
final class NativeTest {
 static final class SavedCapability implements ICapabilitySerializable<CompoundTag>{
  int identity=1;
  public <T> LazyOptional<T> getCapability(Capability<T> c,net.minecraft.core.Direction side){return LazyOptional.empty();}
  public CompoundTag serializeNBT(){CompoundTag t=new CompoundTag();t.putInt("identity",identity);return t;}
  public void deserializeNBT(CompoundTag t){identity=t.getInt("identity");}
 }
 static SavedCapability attach(ItemStack stack)throws Exception{
  SavedCapability cap=new SavedCapability();Field f=CapabilityProvider.class.getDeclaredField("capabilities");f.setAccessible(true);f.set(stack,new CapabilityDispatcher(Map.of(ResourceLocation.tryParse("test:identity"),cap),List.of()));
  for(String name:List.of("isLazy","initialized")){f=CapabilityProvider.class.getDeclaredField(name);f.setAccessible(true);f.setBoolean(stack,name.equals("initialized"));}return cap;
 }
 static void run()throws Exception{
  SharedConstants.tryDetectVersion();Field b=Bootstrap.class.getDeclaredField("isBootstrapped");b.setAccessible(true);b.setBoolean(null,true);net.minecraft.core.registries.BuiltInRegistries.bootStrap();
  ItemStack a=new ItemStack(Items.COAL,3),same=new ItemStack(Items.COAL,3);
  check(StackIdentity.hash(a).equals(StackIdentity.hash(same)),"equivalent full stacks");
  same.setTag(new CompoundTag());check(!StackIdentity.hash(a).equals(StackIdentity.hash(same)),"absent and empty NBT differ");
  a.getOrCreateTag().putString("key","x".repeat(3000)+"A");same.getOrCreateTag().putString("key","x".repeat(3000)+"B");check(!StackIdentity.hash(a).equals(StackIdentity.hash(same)),"full tail NBT identity");
  same=a.copy();same.setCount(259);check(!StackIdentity.hash(a).equals(StackIdentity.hash(same)),"count beyond saved byte included");
  same=a.copy();same.getOrCreateTag().putByte("type",(byte)1);a.getOrCreateTag().putInt("type",1);check(!StackIdentity.hash(a).equals(StackIdentity.hash(same)),"NBT numeric type");
  a=new ItemStack(Items.COAL,3);SavedCapability cap=attach(a);String before=StackIdentity.hash(a);cap.identity=2;check(!before.equals(StackIdentity.hash(a))&&!a.hasTag(),"real ForgeCaps-only mutation");
  check(StackIdentity.hash(ItemStack.EMPTY).matches("[a-f0-9]{64}"),"empty source/target fingerprint");
  CompoundTag left=new CompoundTag(),right=new CompoundTag();left.putInt("Aa",1);left.putInt("BB",2);right.putInt("BB",2);right.putInt("Aa",1);check(StackIdentity.saved(left,1).equals(StackIdentity.saved(right,1)),"compound order canonical");
  wire();pin();
 }
 static void wire(){
  JsonObject args=obj("menu_id",17,"state_id",23,"menu_slot",46,"hotbar_slot",8,"identifier","ring","index",2,"page",1,"expected_page",0);
  Object raw=NativePackets.create("curios.swap",args);check(raw instanceof ServerboundContainerClickPacket,"vanilla swap packet type");
  var p=(ServerboundContainerClickPacket)raw;check(p.getContainerId()==17&&p.getStateId()==23&&p.getSlotNum()==46&&p.getButtonNum()==8&&p.getClickType()==ClickType.SWAP&&p.getCarriedItem().isEmpty()&&p.getChangedSlots().isEmpty(),"one exact SWAP with empty prediction");
  FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer());
  try{CPacketPage.encode((CPacketPage)NativePackets.create("curios.page",args),buf);check(buf.readInt()==17&&buf.readBoolean()&&!buf.isReadable(),"actual page menu ID/next wire");}finally{buf.release();}
  buf=new FriendlyByteBuf(Unpooled.buffer());try{CPacketToggleCosmetics.encode((CPacketToggleCosmetics)NativePackets.create("curios.cosmetics.set",args),buf);check(buf.readInt()==17&&!buf.isReadable(),"actual cosmetics menu ID wire");}finally{buf.release();}
  buf=new FriendlyByteBuf(Unpooled.buffer());try{CPacketToggleRender.encode((CPacketToggleRender)NativePackets.create("curios.render.set",args),buf);check(buf.readUtf().equals("ring")&&buf.readInt()==2&&!buf.isReadable(),"actual render identifier/index wire");}finally{buf.release();}
  buf=new FriendlyByteBuf(Unpooled.buffer());try{CPacketOpenCurios.encode((CPacketOpenCurios)NativePackets.create("curios.open",args),buf);check(buf.readItem().isEmpty()&&!buf.isReadable(),"actual open empty cursor wire");}finally{buf.release();}
 }
 static void pin()throws Exception{
  Path target=Path.of(System.getProperty("adapter.targetJar"));RuntimePin.verifyFile(target,Contract.VERSION);check(true,"full target pin passes");
  try{RuntimePin.verifyFile(target,"5.14.1");throw new AssertionError("version suffix required");}catch(IllegalStateException ex){check(ex.getMessage().equals("curios_runtime_version_mismatch"),"full version required");}
  Path mismatch=Files.createTempFile(Path.of("."),"pin-mismatch",".jar");try{Files.writeString(mismatch,"wrong target");try{RuntimePin.verifyFile(mismatch,Contract.VERSION);throw new AssertionError("full hash required");}catch(IllegalStateException ex){check(ex.getMessage().equals("curios_runtime_hash_mismatch"),"full hash required");}}finally{Files.deleteIfExists(mismatch);}
 }
}
