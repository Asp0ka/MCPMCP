package dev.mcpmcp.curios;

import java.io.*;
import java.security.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

/** Canonical FULL saved stack, not a clipped tooltip/tag or Java hashCode. */
final class StackIdentity {
 static String hash(ItemStack stack){return saved(stack.save(new CompoundTag()),stack.getCount());}
 static String saved(CompoundTag tag,int count){
  try{
   MessageDigest digest=MessageDigest.getInstance("SHA-256");
   try(DataOutputStream out=new DataOutputStream(new DigestOutputStream(new BudgetSink(),digest))){out.writeInt(count);write(tag,out,0,new int[]{65536});}
   return HexFormat.of().formatHex(digest.digest());
  }catch(IOException|NoSuchAlgorithmException e){throw new IllegalStateException("curios_stack_identity_unavailable",e);}
 }
 private static void write(Tag tag,DataOutput out,int depth,int[] budget)throws IOException {
  if(tag==null||depth>32||--budget[0]<0)throw new IllegalArgumentException("curios_nbt_budget");out.writeByte(tag.getId());
  if(tag instanceof CompoundTag compound){var keys=new TreeSet<>(compound.getAllKeys());out.writeInt(keys.size());for(String key:keys){out.writeUTF(key);write(compound.get(key),out,depth+1,budget);}}
  else if(tag instanceof ListTag list){out.writeByte(list.getElementType());out.writeInt(list.size());for(Tag entry:list)write(entry,out,depth+1,budget);}
  else {if(tag.sizeInBytes()>1048576)throw new IllegalArgumentException("curios_nbt_budget");tag.write(out);}
 }
 private static final class BudgetSink extends OutputStream {
  private int remaining=1048576;
  private void consume(int n){if(n>remaining)throw new IllegalArgumentException("curios_nbt_budget");remaining-=n;}
  public void write(int value){consume(1);}
  public void write(byte[] bytes,int offset,int length){consume(length);}
 }
 private StackIdentity(){}
}
