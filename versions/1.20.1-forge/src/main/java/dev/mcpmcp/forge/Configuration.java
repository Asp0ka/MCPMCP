package dev.mcpmcp.forge;

import com.google.gson.*;
import dev.mcpmcp.core.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;

public final class Configuration {
 public String mode="NORMAL",transport="MIXED",token;
 public boolean debug=false,trustJavaPlugins=false,serverEnabled=true;
 public int port=5210,portEnd=5220,scanBudget=4096;
 public Map<String,String> pluginOverrides=new LinkedHashMap<>();
 private transient JsonObject retained=new JsonObject();
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();

 public static Configuration load(Path path)throws Exception {
  Configuration c;
  if(Files.exists(path)){
   if(Files.size(path)>65536)throw new IllegalArgumentException("config_too_large");
   JsonObject raw=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
   // Gson alone accepts null primitives and coerces strings; settings must be explicit values.
   if(raw.has("port"))Jsons.integer(raw,"port",5210);
   if(raw.has("portEnd"))Jsons.integer(raw,"portEnd",5220);
   if(raw.has("serverEnabled"))Jsons.bool(raw,"serverEnabled",true);
   c=GSON.fromJson(raw,Configuration.class);
   if(c==null)throw new IllegalArgumentException("invalid_configuration");
   // Old installations keep their selected start; absent fields alone migrate.
   if(!raw.has("portEnd"))c.portEnd=(int)Math.min((long)c.port+10,65535);
   if(!raw.has("serverEnabled"))c.serverEnabled=true;
   c.retained=raw.deepCopy();
  }else{
   c=new Configuration();byte[] b=new byte[32];new SecureRandom().nextBytes(b);c.token=HexFormat.of().formatHex(b);c.save(path);
  }
  c.validate();c.scanBudget=Math.max(64,Math.min(8192,c.scanBudget));return c;
 }

 public static void validateServerSettings(int start,int end){
  if(start<1024||end>65535||start>end||end-start>10)throw new IllegalArgumentException("invalid_server_port_range");
 }
 private void validate(){
  validateServerSettings(port,portEnd);
  if(mode==null||transport==null||!Set.of("NORMAL","HACK","CHEAT","HACK_CHEAT").contains(mode)||!Set.of("PACKET","SIMULATE","MIXED").contains(transport)||token==null||!token.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("invalid_configuration");
  if(pluginOverrides==null||pluginOverrides.size()>256||pluginOverrides.entrySet().stream().anyMatch(e->e.getKey()==null||!e.getKey().matches("[a-z0-9_.-]{1,96}")||e.getValue()==null||!e.getValue().matches("[a-f0-9]{64}")))throw new IllegalArgumentException("invalid_plugin_overrides");
 }

 public synchronized void save(Path path)throws Exception{
  validate();
  JsonObject value=retained.deepCopy();
  for(var e:GSON.toJsonTree(this).getAsJsonObject().entrySet())value.add(e.getKey(),e.getValue());
  Path absolute=path.toAbsolutePath();Files.createDirectories(absolute.getParent());
  Path temp=Files.createTempFile(absolute.getParent(),absolute.getFileName().toString(),".tmp");
  try{
   Files.writeString(temp,GSON.toJson(value));
   Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
   retained=value;
  }finally{Files.deleteIfExists(temp);}
 }

 public JsonObject publicView(){return Jsons.obj("mode",mode,"transport",transport,"debug",debug,"trust_java_plugins",trustJavaPlugins,"port",port,"port_end",portEnd,"server_enabled",serverEnabled,"scan_budget",scanBudget);}
}
