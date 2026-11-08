package dev.mcpmcp.forge;

import com.google.gson.JsonObject;
import dev.mcpmcp.core.Jsons;
import java.net.BindException;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Session state is separate from persisted startup preferences. No timer restarts it. */
public final class ServerLifecycle implements AutoCloseable {
 @FunctionalInterface public interface Handler {JsonObject call(long session,String method,JsonObject args);}
 private final Supplier<Configuration> configuration;
 private final Path configFile,discoveryFile;
 private final String gameDirectory,instance=UUID.randomUUID().toString();
 private final Handler handler;
 private final Runnable stopped;
 private LocalServer server;
 private int actualPort;
 private String error="";
 private boolean initialized,closed,ownsDiscovery;
 private CompletableFuture<Void> closing=CompletableFuture.completedFuture(null);
 private volatile boolean running;
 private volatile long session;

 public ServerLifecycle(Supplier<Configuration> configuration,Path configFile,Path gameDirectory,Handler handler,Runnable stopped){
  this.configuration=configuration;this.configFile=configFile;this.discoveryFile=configFile.toAbsolutePath().resolveSibling("mcpmcp-runtime.json");
  this.gameDirectory=canonical(gameDirectory);this.handler=handler;this.stopped=stopped;
 }
 private static String canonical(Path path){try{return path.toRealPath().toString();}catch(Exception e){return path.toAbsolutePath().normalize().toString();}}

 public synchronized JsonObject initialize(){
  if(initialized)return status();initialized=true;
  if(configuration.get()!=null&&configuration.get().serverEnabled)return start();
  publishInactive();return status();
 }
 public boolean accepts(long requestedSession){return running&&requestedSession==session;}
 public synchronized JsonObject start(){
  if(closed){error="server_closed";return status();}
  if(running)return status();
  if(!closing.isDone()){error="server_stopping";return status();}
  Configuration c=configuration.get();error="";
  if(c==null){error="configuration_unavailable";publishInactive();return status();}
  try{Configuration.validateServerSettings(c.port,c.portEnd);}catch(IllegalArgumentException e){error="invalid_server_port_range";publishInactive();return status();}
  // Invalidate stale discovery before any bind. A failed write prevents startup.
  if(!publishInactive())return status();
  long attempt=++session;
  for(int port=c.port;port<=c.portEnd;port++){
   try{
    server=new LocalServer(port,()->configuration.get().token,(method,args)->accepts(attempt)?handler.call(attempt,method,args):Jsons.error("server_stopped"),instance);
    actualPort=server.port();running=true;
    try{publish();}catch(Exception e){running=false;session++;actualPort=0;error="discovery_write_failed";retireServer();publishInactive();}
    return status();
   }catch(BindException occupied){/* Try the next configured candidate only. */}
   catch(Exception|LinkageError failed){error="server_start_failed";publishInactive();return status();}
  }
  error="ports_unavailable";publishInactive();return status();
 }
 public synchronized JsonObject stop(){
  running=false;session++;actualPort=0;error="";
  // Detach admission first; callbacks never hold this monitor while awaiting the client.
  try{stopped.run();}catch(Exception e){error="server_cleanup_failed";}
  retireServer();
  publishInactive();return status();
 }
 public synchronized void initializationFailed(){initialized=true;running=false;session++;actualPort=0;error="initialization_failed";retireServer();publishInactive();}
 private void retireServer(){
  if(server==null)return;LocalServer retired=server;server=null;
  closing=CompletableFuture.runAsync(retired::close).handle((unused,failure)->{if(failure!=null)synchronized(this){error="server_stop_failed";}return null;});
 }
 /** Called only through the local client-thread settings API. Does not touch session state. */
 public synchronized JsonObject updateSettings(int start,int end,boolean autoStart){
  try{Configuration.validateServerSettings(start,end);}catch(IllegalArgumentException e){return settingError("invalid_server_port_range");}
  Configuration c=configuration.get();if(c==null)return settingError("configuration_unavailable");int beforeStart=c.port,beforeEnd=c.portEnd;boolean beforeAuto=c.serverEnabled;
  c.port=start;c.portEnd=end;c.serverEnabled=autoStart;
  try{c.save(configFile);JsonObject result=status();result.remove("error");result.addProperty("saved",true);return result;}
  catch(Exception e){c.port=beforeStart;c.portEnd=beforeEnd;c.serverEnabled=beforeAuto;return settingError("server_config_save_failed");}
 }
 private JsonObject settingError(String reason){JsonObject result=status();result.addProperty("error",reason);return result;}
 public synchronized JsonObject status(){
  Configuration c=configuration.get();
  return Jsons.obj("running",running,"stopping",!closing.isDone(),"actual_port",actualPort,"configured_port",c==null?0:c.port,"configured_port_end",c==null?0:c.portEnd,"auto_start",c!=null&&c.serverEnabled,"error",error,"endpoint",running?"http://127.0.0.1:"+actualPort+"/mcp":"","call_endpoint",running?"http://127.0.0.1:"+actualPort+"/call":"");
 }
 private boolean publishInactive(){try{publishInactiveRecord();return true;}catch(Exception e){error="discovery_write_failed";return false;}}
 private void publishInactiveRecord()throws Exception{
  if(ownsDiscovery&&Files.exists(discoveryFile))try{JsonObject previous=com.google.gson.JsonParser.parseString(Files.readString(discoveryFile)).getAsJsonObject();if(previous.has("instance")&&!instance.equals(previous.get("instance").getAsString()))return;}catch(Exception ignored){}
  boolean wasRunning=running;int oldPort=actualPort;running=false;actualPort=0;
  try{publish();ownsDiscovery=true;}finally{running=wasRunning;actualPort=oldPort;}
 }
 private void publish()throws Exception{
  JsonObject record=Jsons.obj("running",running,"port",running?actualPort:0,"instance",instance,"pid",ProcessHandle.current().pid(),"game_directory",gameDirectory,"endpoint",running?"http://127.0.0.1:"+actualPort+"/mcp":"","call_endpoint",running?"http://127.0.0.1:"+actualPort+"/call":"");
  Files.createDirectories(discoveryFile.getParent());
  Path temp=Files.createTempFile(discoveryFile.getParent(),"mcpmcp-runtime-",".tmp");
  try{Files.writeString(temp,Jsons.GSON.toJson(record));Files.move(temp,discoveryFile,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(temp);}
 }
 public synchronized CompletionStage<Void> whenStopped(){return closing.minimalCompletionStage();}
 public void close(){CompletableFuture<Void> completion;synchronized(this){if(!closed){stop();closed=true;}completion=closing;}completion.join();}
}
