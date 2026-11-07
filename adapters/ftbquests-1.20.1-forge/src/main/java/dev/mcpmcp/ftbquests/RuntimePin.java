package dev.mcpmcp.ftbquests;

import com.google.gson.JsonArray;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import net.minecraftforge.fml.ModList;
import static dev.mcpmcp.ftbquests.Json.*;

/** The actual ModList-selected file is authoritative; duplicate filenames are irrelevant. */
final class RuntimePin {
 record Pin(String modId,String version,String sha256){}
 static final List<Pin> PINS=List.of(
  new Pin("ftbteams","2001.3.2","b1cb4d82ea9f137a87e12a7aaf3329619294b56f7d70337dfd557a76e10b6e07"),
  new Pin("architectury","9.2.14","218b471d0b8a1f6cda14cfc1beb9eeb0df54304500acc6c5613d9b88ec65d9af"),
  new Pin("ftblibrary","2001.2.13","41810281b65336265cae2c88412022d02f2f0a3f28c66a06173d86f98acf14a7"),
  new Pin("ftbquests","2001.4.22","9ea9d159a78a2724cfe0457b024680fdb52a2126eff9cfa5280a30578f647a68"));
 interface Filesystem { String digest(Path path); }
 record Loaded(String version,Path path,Object identity){}
 interface Lookup { Loaded get(String modId); }
 private final Lookup lookup;private final Filesystem filesystem;
 RuntimePin(){this(id->{var mods=ModList.get();if(mods==null)throw new IllegalStateException("ftbquests_modlist_unavailable");var info=mods.getModContainerById(id).orElseThrow(()->new IllegalStateException("ftbquests_dependency_missing_"+id)).getModInfo();var file=info.getOwningFile().getFile();return new Loaded(info.getVersion().toString(),file.getFilePath(),file);},RuntimePin::digest);}
 RuntimePin(Lookup lookup,Filesystem filesystem){this.lookup=lookup;this.filesystem=filesystem;}
 void verify(){
  for(Pin pin:PINS){Loaded actual=lookup.get(pin.modId());need(actual!=null,"ftbquests_dependency_missing_"+pin.modId());need(pin.version().equals(actual.version()),"ftbquests_dependency_version_mismatch_"+pin.modId());
   need(actual.path()!=null&&pin.sha256().equals(filesystem.digest(actual.path())),"ftbquests_dependency_hash_mismatch_"+pin.modId());
  }
 }
 static String digest(Path path){try{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(path)){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)d.update(bytes,0,n);}return HexFormat.of().formatHex(d.digest());}catch(IOException|NoSuchAlgorithmException e){throw new IllegalStateException("ftbquests_dependency_hash_unavailable",e);}}
 static JsonArray declarations(){JsonArray a=new JsonArray();for(Pin p:PINS)a.add(obj("mod_id",p.modId(),"version",p.version(),"sha256",p.sha256()));return a;}
}
