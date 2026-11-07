package dev.mcpmcp.rs;
import net.minecraft.nbt.*;
import java.util.*;
/** Bounded, type-preserving canonical tag identity; preview never serves as identity. */
final class NbtIdentity {
 record Description(String preview,boolean truncated,String sha256){}
 static Description describe(Tag tag){String nbt=canonicalNbt(tag,0,new int[]{4096});return new Description(nbt.substring(0,Math.min(2048,nbt.length())),nbt.length()>2048,JsonIdentity.sha(nbt));}
 private static String canonicalNbt(Tag tag,int depth,int[] budget){
  if(tag==null)return "null";
  if(depth>32||--budget[0]<0||(!(tag instanceof CompoundTag)&&!(tag instanceof ListTag)&&tag.sizeInBytes()>131072))throw new IllegalArgumentException("rs_nbt_identity_budget");
  if(tag instanceof CompoundTag compound){
   if(compound.getAllKeys().size()>budget[0])throw new IllegalArgumentException("rs_nbt_identity_budget");
   StringJoiner j=new StringJoiner(",","{","}");
   for(String key:new TreeSet<>(compound.getAllKeys())){j.add(StringTag.quoteAndEscape(key)+":"+canonicalNbt(compound.get(key),depth+1,budget));if(j.length()>65536)throw new IllegalArgumentException("rs_nbt_identity_budget");}return j.toString();
  }
  if(tag instanceof ListTag list){if(list.size()>budget[0])throw new IllegalArgumentException("rs_nbt_identity_budget");StringJoiner j=new StringJoiner(",","[","]");for(Tag v:list){j.add(canonicalNbt(v,depth+1,budget));if(j.length()>65536)throw new IllegalArgumentException("rs_nbt_identity_budget");}return j.toString();}
  String value=tag.toString();if(value.length()>65536)throw new IllegalArgumentException("rs_nbt_identity_budget");return value;
 }
 private NbtIdentity(){}
}
