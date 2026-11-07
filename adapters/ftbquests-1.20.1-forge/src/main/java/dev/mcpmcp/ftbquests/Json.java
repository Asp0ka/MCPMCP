package dev.mcpmcp.ftbquests;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

final class Json {
 static final Gson GSON=new GsonBuilder().serializeNulls().create();
 static JsonObject obj(Object... pairs){JsonObject o=new JsonObject();for(int i=0;i<pairs.length;i+=2)o.add((String)pairs[i],GSON.toJsonTree(pairs[i+1]));return o;}
 static String str(JsonObject o,String k,String fallback){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():fallback;}
 static boolean bool(JsonObject o,String k,boolean fallback){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsBoolean():fallback;}
 static int integer(JsonObject o,String k,int fallback){return o.has(k)?o.get(k).getAsBigDecimal().intValueExact():fallback;}
 static JsonArray array(JsonObject o,String k){return o.has(k)?o.getAsJsonArray(k):new JsonArray();}
 static void need(boolean valid,String error){if(!valid)throw new IllegalArgumentException(error);}
 static String id(long id){return String.format(Locale.ROOT,"%016x",id);}
 static long parseId(String id){need(id!=null&&id.matches("[0-9a-f]{16}"),"ftbquests_canonical_hex_id_required");return Long.parseUnsignedLong(id,16);}
 static String canonical(JsonElement e){
  if(e.isJsonObject()){TreeMap<String,JsonElement> sorted=new TreeMap<>();e.getAsJsonObject().entrySet().forEach(x->sorted.put(x.getKey(),x.getValue()));StringJoiner j=new StringJoiner(",","{","}");sorted.forEach((k,v)->j.add(GSON.toJson(k)+":"+canonical(v)));return j.toString();}
  if(e.isJsonArray()){StringJoiner j=new StringJoiner(",","[","]");e.getAsJsonArray().forEach(x->j.add(canonical(x)));return j.toString();}return e.toString();
 }
 static String hash(JsonElement e){return hash(canonical(e).getBytes(StandardCharsets.UTF_8));}
 static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 static JsonObject text(String s,int max){if(s==null)s="";int end=Math.min(s.length(),max);if(end<s.length()&&end>0&&Character.isHighSurrogate(s.charAt(end-1)))end--;return obj("text",s.substring(0,end),"truncated",end<s.length(),"total_utf16_units",s.length());}
 private Json(){}
}
