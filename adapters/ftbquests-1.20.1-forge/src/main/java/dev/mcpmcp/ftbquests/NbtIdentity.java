package dev.mcpmcp.ftbquests;
import java.io.*;
import java.util.*;
import net.minecraft.nbt.*;
import static dev.mcpmcp.ftbquests.Json.*;
/** Type preserving, sorted compound keys; lists retain order, all long values stay exact. */
final class NbtIdentity {
 static String hash(Tag tag){try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);encode(tag,out,0,new int[]{262144});out.flush();return Json.hash(bytes.toByteArray());}catch(IOException e){throw new IllegalStateException(e);}}
 private static void encode(Tag tag,DataOutputStream out,int depth,int[] budget)throws IOException{
  need(tag!=null&&depth<=32&&--budget[0]>=0,"ftbquests_definition_budget");out.writeByte(tag.getId());
  if(tag instanceof CompoundTag c){var keys=new TreeSet<>(c.getAllKeys());out.writeInt(keys.size());for(String k:keys){string(k,out,budget);encode(c.get(k),out,depth+1,budget);}}
  else if(tag instanceof ListTag list){out.writeInt(list.size());for(Tag t:list)encode(t,out,depth+1,budget);}
  else if(tag instanceof ByteArrayTag a){byte[] v=a.getAsByteArray();budget[0]-=v.length;out.writeInt(v.length);out.write(v);}
  else if(tag instanceof IntArrayTag a){int[] v=a.getAsIntArray();budget[0]-=v.length*4;out.writeInt(v.length);for(int i:v)out.writeInt(i);}
  else if(tag instanceof LongArrayTag a){long[] v=a.getAsLongArray();budget[0]-=v.length*8;out.writeInt(v.length);for(long i:v)out.writeLong(i);}
  else string(tag.getAsString(),out,budget);
  need(budget[0]>=0,"ftbquests_definition_budget");
 }
 private static void string(String s,DataOutputStream out,int[] budget)throws IOException{byte[] v=s.getBytes(java.nio.charset.StandardCharsets.UTF_8);budget[0]-=v.length;need(budget[0]>=0,"ftbquests_definition_budget");out.writeInt(v.length);out.write(v);}
}
