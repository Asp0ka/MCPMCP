package dev.mcpmcp.rs;
import net.minecraft.nbt.*;
import static dev.mcpmcp.rs.AdapterTest.check;
/** Uses real Minecraft tag classes, independent from the detached action fixture. */
final class NbtIdentityTest {
 static void run(){
  CompoundTag a=new CompoundTag();a.putInt("b",2);a.putString("a","value");
  CompoundTag b=new CompoundTag();b.putString("a","value");b.putInt("b",2);
  check(NbtIdentity.describe(a).sha256().equals(NbtIdentity.describe(b).sha256()),"compound key order stable");
  b.putByte("b",(byte)2);check(!NbtIdentity.describe(a).sha256().equals(NbtIdentity.describe(b).sha256()),"tag type included");
  check(!NbtIdentity.describe(null).sha256().equals(NbtIdentity.describe(new CompoundTag()).sha256()),"absent and empty differ");
  CompoundTag longA=new CompoundTag(),longB=new CompoundTag();longA.putString("key","x".repeat(3000)+"A");longB.putString("key","x".repeat(3000)+"B");
  check(NbtIdentity.describe(longA).truncated(),"preview clipped");
  check(NbtIdentity.describe(longA).preview().equals(NbtIdentity.describe(longB).preview())&&!NbtIdentity.describe(longA).sha256().equals(NbtIdentity.describe(longB).sha256()),"full identity beyond identical preview");
  ListTag first=new ListTag(),second=new ListTag();first.add(IntTag.valueOf(1));first.add(IntTag.valueOf(2));second.add(IntTag.valueOf(2));second.add(IntTag.valueOf(1));
  check(!NbtIdentity.describe(first).sha256().equals(NbtIdentity.describe(second).sha256()),"list order significant");
  CompoundTag large=new CompoundTag();large.putString("value","z".repeat(66000));boolean bounded=false;
  try{NbtIdentity.describe(large);}catch(IllegalArgumentException e){bounded=e.getMessage().equals("rs_nbt_identity_budget");}
  check(bounded,"oversized NBT fail closed");
  CompoundTag deep=new CompoundTag(),cursor=deep;for(int i=0;i<34;i++){CompoundTag next=new CompoundTag();cursor.put("next",next);cursor=next;}bounded=false;
  try{NbtIdentity.describe(deep);}catch(IllegalArgumentException e){bounded=e.getMessage().equals("rs_nbt_identity_budget");}
  check(bounded,"deep NBT fail closed");
  System.out.println("NbtIdentityTest: 8 real Minecraft NBT identity checks passed");
 }
}
