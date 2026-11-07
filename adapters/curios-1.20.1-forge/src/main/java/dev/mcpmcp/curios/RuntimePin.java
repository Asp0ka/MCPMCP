package dev.mcpmcp.curios;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import net.minecraftforge.fml.ModList;

/** Verify the actual Forge mod file; declaration/filename is never sufficient. */
final class RuntimePin {
 private Object file;
 private Path path;
 private long size,modified;
 void verify(){
  var mods=ModList.get();if(mods==null)throw new IllegalStateException("curios_modlist_unavailable");
  var info=mods.getModContainerById("curios").orElseThrow(()->new IllegalStateException("curios_mod_missing")).getModInfo();
  if(!Contract.VERSION.equals(info.getVersion().toString()))throw new IllegalStateException("curios_runtime_version_mismatch");
  var actual=info.getOwningFile().getFile();Path jar=actual.getFilePath();
  try{
   if(!Files.isRegularFile(jar))throw new IllegalStateException("curios_runtime_jar_required");
   long bytes=Files.size(jar),mtime=Files.getLastModifiedTime(jar).toMillis();
   if(file==actual&&jar.equals(path)&&bytes==size&&mtime==modified)return;
   verifyFile(jar,info.getVersion().toString());file=actual;path=jar;size=bytes;modified=mtime;
  }catch(IOException e){throw new IllegalStateException("curios_runtime_hash_unavailable",e);}
 }
 static void verifyFile(Path path,String version){
  if(!Contract.VERSION.equals(version))throw new IllegalStateException("curios_runtime_version_mismatch");
  try{
   MessageDigest digest=MessageDigest.getInstance("SHA-256");
   try(InputStream in=Files.newInputStream(path)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);}
   if(!Contract.SHA256.equals(HexFormat.of().formatHex(digest.digest())))throw new IllegalStateException("curios_runtime_hash_mismatch");
  }catch(IOException|NoSuchAlgorithmException e){throw new IllegalStateException("curios_runtime_hash_unavailable",e);}
 }
}
