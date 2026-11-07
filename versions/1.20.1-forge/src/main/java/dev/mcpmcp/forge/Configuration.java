package dev.mcpmcp.forge;
import com.google.gson.*;
import dev.mcpmcp.core.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
public final class Configuration {
 public String mode="NORMAL",transport="MIXED",token;public boolean debug=false,trustJavaPlugins=false;public int port=5007,scanBudget=4096;
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 public static Configuration load(Path path)throws Exception {Configuration c;if(Files.exists(path)){if(Files.size(path)>65536)throw new IllegalArgumentException("config_too_large");c=GSON.fromJson(Files.readString(path),Configuration.class);}else{c=new Configuration();byte[] b=new byte[32];new SecureRandom().nextBytes(b);c.token=HexFormat.of().formatHex(b);c.save(path);}if(c==null||!Set.of("NORMAL","HACK","CHEAT","HACK_CHEAT").contains(c.mode)||!Set.of("PACKET","SIMULATE","MIXED").contains(c.transport)||c.port<1024||c.port>65535||c.token==null||!c.token.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("invalid_configuration");c.scanBudget=Math.max(64,Math.min(8192,c.scanBudget));return c;}
 public void save(Path path)throws Exception{Files.createDirectories(path.getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,GSON.toJson(this));try{Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}}
 public JsonObject publicView(){return Jsons.obj("mode",mode,"transport",transport,"debug",debug,"trust_java_plugins",trustJavaPlugins,"port",port,"scan_budget",scanBudget);}
}
