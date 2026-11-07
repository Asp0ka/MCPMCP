package dev.mcpmcp.forge;
import java.nio.file.*;
public final class PluginConfigTest {
 public static void main(String[] args)throws Exception {
  Path dir=Files.createTempDirectory("mcpmcp-config-test"),file=dir.resolve("mcpmcp.json");
  try{Configuration c=Configuration.load(file);c.pluginOverrides.put("example","a".repeat(64));c.save(file);Configuration fresh=Configuration.load(file);if(!fresh.pluginOverrides.equals(c.pluginOverrides))throw new AssertionError("override persistence");fresh.pluginOverrides.clear();fresh.save(file);if(!Configuration.load(file).pluginOverrides.isEmpty())throw new AssertionError("revoke persistence");String raw=Files.readString(file);raw=raw.replace("\"pluginOverrides\": {}","\"pluginOverrides\": {\"bad\":\"not-a-hash\"}");Files.writeString(file,raw);try{Configuration.load(file);throw new AssertionError("invalid grant accepted");}catch(IllegalArgumentException expected){}System.out.println("PluginConfigTest: save/reload/revoke/invalid grant passed");}finally{Files.deleteIfExists(file);Files.deleteIfExists(dir);}
 }
}
