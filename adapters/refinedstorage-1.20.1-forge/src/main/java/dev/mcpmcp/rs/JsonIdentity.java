package dev.mcpmcp.rs;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** Deterministic digest independent of JSON/NBT compound key iteration order. */
final class JsonIdentity {
 static String digest(JsonElement value){return sha(canonical(value));}
 static String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private static String canonical(JsonElement e){
  if(e.isJsonObject()){StringJoiner j=new StringJoiner(",","{","}");new TreeSet<>(e.getAsJsonObject().keySet()).forEach(k->j.add(new JsonPrimitive(k)+":"+canonical(e.getAsJsonObject().get(k))));return j.toString();}
  if(e.isJsonArray()){StringJoiner j=new StringJoiner(",","[","]");e.getAsJsonArray().forEach(v->j.add(canonical(v)));return j.toString();}
  return e.toString();
 }
 private JsonIdentity(){}
}
