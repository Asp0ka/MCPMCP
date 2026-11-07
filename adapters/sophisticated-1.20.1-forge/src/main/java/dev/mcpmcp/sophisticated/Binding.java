package dev.mcpmcp.sophisticated;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import net.minecraftforge.fml.ModList;

/** A manifest force-load cannot silently loosen these reviewed runtime bindings. */
final class Binding {
 private static final Map<String,String> VERSIONS=Map.of(
  "sophisticatedstorage","1.4.79.2069","sophisticatedbackpacks","3.24.63.2057","ironfurnaces","4.1.8","sophisticatedcore","1.3.78.2249");
 private static final Map<String,String> HASHES=Map.of(
  "sophisticatedstorage","4d555d1754fbd8cfe3cf0f00adc65c9d13189dd12e7f9a7b9ad3a355b1210e74",
  "sophisticatedbackpacks","4d4be2e6a9988ff08260de621c26d0b43f2e03e3aede00842c66dc136a5303bf",
  "ironfurnaces","e457052522cef1f8644b6929a64eab96de04ce9e6e32a715f1d68d79da26d66b",
  "sophisticatedcore","f32519f8605509e20e5ee9569b4b35cc30e17b85cb6f81f4111350dbc427db75");
 private static final Set<String> CHECKED=new HashSet<>();
 static synchronized void check(String type) throws Exception {
  String mod=Contract.namespace(type);checkMod(mod);if(!type.equals("furnace"))checkMod("sophisticatedcore");
 }
 private static void checkMod(String mod) throws Exception {
  if(CHECKED.contains(mod))return;
  var info=ModList.get().getModContainerById(mod).orElseThrow(()->new IllegalStateException("missing_mod_"+mod)).getModInfo();
  Path jar=info.getOwningFile().getFile().getFilePath();
  if(!Files.isRegularFile(jar))throw new IllegalStateException("exact_jar_required_"+mod);
  MessageDigest digest=MessageDigest.getInstance("SHA-256");
  try(InputStream in=Files.newInputStream(jar)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)digest.update(buf,0,n);}
  validate(mod,info.getVersion().toString(),HexFormat.of().formatHex(digest.digest()));CHECKED.add(mod);
 }
 static void validate(String mod,String version,String hash) {if(!VERSIONS.getOrDefault(mod,"").equals(version)||!HASHES.getOrDefault(mod,"").equals(hash)||!HASHES.containsKey(mod))throw new IllegalStateException("exact_jar_binding_mismatch_"+mod);}
 private Binding() {}
}
