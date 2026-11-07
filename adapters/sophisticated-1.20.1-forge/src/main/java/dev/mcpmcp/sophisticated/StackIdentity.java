package dev.mcpmcp.sophisticated;

import java.io.*;
import java.security.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

/** Full saved stack identity, including ForgeCaps; compound key order is immaterial. */
final class StackIdentity {
 static String hash(ItemStack stack) {
  try {
   MessageDigest digest=MessageDigest.getInstance("SHA-256");
   try(DataOutputStream out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest))) {
    // ItemStack.save writes Count as a byte; menus can contain larger counts.
    out.writeInt(stack.getCount());
    write(stack.save(new CompoundTag()),out);
   }
   return HexFormat.of().formatHex(digest.digest());
  }catch(IOException|NoSuchAlgorithmException e){throw new IllegalStateException("stack_identity_unavailable",e);}
 }
 private static void write(Tag tag,DataOutput out) throws IOException {
  out.writeByte(tag.getId());
  if(tag instanceof CompoundTag compound) {
   var keys=new TreeSet<>(compound.getAllKeys());
   out.writeInt(keys.size());
   for(String key:keys){out.writeUTF(key);write(compound.get(key),out);}
  } else if(tag instanceof ListTag list) {
   out.writeByte(list.getElementType());out.writeInt(list.size());
   for(Tag entry:list)write(entry,out);
  } else tag.write(out);
 }
 private StackIdentity() {}
}
