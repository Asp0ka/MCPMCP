package dev.mcpmcp.forge;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

public final class ServerConfigurationTest {
 public static void main(String[] args)throws Exception {
  Path dir=Files.createTempDirectory(Path.of(args.length==0?System.getProperty("java.io.tmpdir"):args[0]),"server-config-");
  try {
   Path file=dir.resolve("mcpmcp.json");
   Configuration defaults=Configuration.load(file);
   check(defaults.port==5210,"new installations start at 5210");
   JsonObject view=defaults.publicView();
   check(view.get("port_end").getAsInt()==5220,"default end 5220");
   check(view.get("server_enabled").getAsBoolean(),"default automatic startup");
   check(!view.has("token"),"public view excludes secret");
   JsonObject old=new JsonObject();old.addProperty("port",5007);old.addProperty("token","a".repeat(64));
   old.addProperty("mode","HACK");old.addProperty("transport","PACKET");old.addProperty("debug",true);old.addProperty("trustJavaPlugins",true);
   JsonObject grants=new JsonObject();grants.addProperty("sample","b".repeat(64));old.add("pluginOverrides",grants);
   JsonObject extra=new JsonObject();extra.addProperty("keep","untouched");old.add("future_setting",extra);
   Files.writeString(file,old.toString());byte[] original=Files.readAllBytes(file);
   Configuration migrated=Configuration.load(file);
   check(migrated.port==5007,"legacy configured start preserved");
   check(migrated.publicView().get("port_end").getAsInt()==5017,"missing end derived from legacy start");
   check(Arrays.equals(original,Files.readAllBytes(file)),"loading migration never rewrites configuration");
   migrated.save(file);Configuration reloaded=Configuration.load(file);
   JsonObject saved=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
   check(saved.getAsJsonObject("future_setting").equals(extra),"unknown settings retained");
   check(reloaded.token.equals(migrated.token)&&reloaded.pluginOverrides.equals(migrated.pluginOverrides),"token and grants retained");
   check(reloaded.mode.equals("HACK")&&reloaded.transport.equals("PACKET")&&reloaded.debug&&reloaded.trustJavaPlugins,"permissions and other fields retained");
   old.addProperty("port",65530);Files.writeString(file,old.toString());
   check(Configuration.load(file).publicView().get("port_end").getAsInt()==65535,"legacy range clipped to 65535");
   for(int[] range:new int[][]{{1023,1024},{5210,5221},{5220,5210},{65535,65536}}){
    old.addProperty("port",range[0]);old.addProperty("portEnd",range[1]);Files.writeString(file,old.toString());
    try{Configuration.load(file);throw new AssertionError("invalid range accepted");}catch(IllegalArgumentException expected){}
   }
   for(String field:List.of("port","portEnd","serverEnabled")){
    for(JsonElement invalid:List.of(JsonNull.INSTANCE,new JsonPrimitive("5210"),new JsonPrimitive(5210.5))){
     JsonObject malformed=saved.deepCopy();malformed.add(field,invalid);Files.writeString(file,malformed.toString());
     try{Configuration.load(file);throw new AssertionError("malformed server setting accepted: "+field);}catch(IllegalArgumentException expected){}
    }
   }
   System.out.println("ServerConfigurationTest: defaults/migration/preservation/range validation PASS");
  }finally{try(var paths=Files.walk(dir)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
 private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
}
