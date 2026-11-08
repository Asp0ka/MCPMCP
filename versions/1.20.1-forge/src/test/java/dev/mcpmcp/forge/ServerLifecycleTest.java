package dev.mcpmcp.forge;

import com.google.gson.*;
import dev.mcpmcp.core.Jsons;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;

public final class ServerLifecycleTest {
 public static void main(String[] args)throws Exception {
  Path dir=Files.createTempDirectory(Path.of(args.length==0?System.getProperty("java.io.tmpdir"):args[0]),"server-lifecycle-");
  try{firstOccupiedUsesNextPort(dir);settingsArePersistentButSessionIsTemporary(dir);occupiedRangeDoesNotRetryUntilManualStart(dir);generationRejectsOldRequests(dir);stopDoesNotWaitForInflightHttp(dir);discoveryClearsStaleAndFailedStartup(dir);}finally{try(var paths=Files.walk(dir)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
  System.out.println("ServerLifecycleTest: fallback/all-occupied/manual-retry/settings-rollback/session-generation/nonblocking-stop PASS");
 }
 private static void firstOccupiedUsesNextPort(Path dir)throws Exception{
  try(PortRange ports=PortRange.reserve()){
   Path file=dir.resolve("mcpmcp.json");Configuration c=Configuration.load(file);c.port=ports.start;c.portEnd=ports.start+10;c.save(file);
   byte[] hash=digest(file);AtomicReference<Configuration> current=new AtomicReference<>(c);
   ports.releaseFrom(1);
   try(ServerLifecycle lifecycle=new ServerLifecycle(current::get,file,dir,(session,method,a)->Jsons.obj("method",method),()->{})){
    JsonObject state=lifecycle.initialize();
    check(state.get("running").getAsBoolean(),"server binds despite first port occupied");
    check(state.get("actual_port").getAsInt()==ports.start+1,"first free port chosen in ascending order");
    check(c.port==ports.start,"fallback never overwrites configured start");
    check(Arrays.equals(hash,digest(file)),"starting does not write config");
    JsonObject discovered=JsonParser.parseString(Files.readString(dir.resolve("mcpmcp-runtime.json"))).getAsJsonObject();
    check(discovered.get("running").getAsBoolean()&&discovered.get("port").getAsInt()==ports.start+1,"discovery advertises actual port");
    check(discovered.get("game_directory").getAsString().equals(dir.toRealPath().toString()),"discovery identifies game directory");
    check(!discovered.has("token")&&!state.has("token"),"status and discovery exclude secret");
    HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    String body=Jsons.obj("method","probe","args",new JsonObject()).toString();
    HttpRequest req=HttpRequest.newBuilder(URI.create(state.get("call_endpoint").getAsString())).timeout(Duration.ofSeconds(3)).header("Authorization","Bearer "+c.token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
    HttpResponse<String> response=http.send(req,HttpResponse.BodyHandlers.ofString());
    check(response.statusCode()==200&&JsonParser.parseString(response.body()).getAsJsonObject().get("method").getAsString().equals("probe"),"real HTTP endpoint dispatches");
   }
   check(!JsonParser.parseString(Files.readString(dir.resolve("mcpmcp-runtime.json"))).getAsJsonObject().get("running").getAsBoolean(),"close publishes inactive discovery");
  }
 }
 private static void settingsArePersistentButSessionIsTemporary(Path dir)throws Exception{
  try(PortRange ports=PortRange.reserve()){
   Path file=dir.resolve("session-settings.json");Configuration c=Configuration.load(file);c.port=ports.start;c.portEnd=ports.start+10;c.serverEnabled=false;c.save(file);
   AtomicReference<Configuration> current=new AtomicReference<>(c);AtomicInteger stopped=new AtomicInteger();ports.releaseFrom(0);
   try(ServerLifecycle lifecycle=new ServerLifecycle(current::get,file,dir,(session,method,a)->Jsons.obj("ok",true),stopped::incrementAndGet)){
    check(!lifecycle.initialize().get("running").getAsBoolean(),"automatic startup disabled");
    byte[] hash=digest(file);JsonObject started=lifecycle.start();
    check(started.get("running").getAsBoolean(),"manual start overrides disabled startup for session");
    check(lifecycle.updateSettings(ports.start+1,ports.start+10,true).get("running").getAsBoolean(),"saving settings retains running session");
    check(lifecycle.status().get("actual_port").getAsInt()==ports.start,"saving start port does not rebind");
    check(Configuration.load(file).port==ports.start+1&&Configuration.load(file).serverEnabled,"settings persisted");
    hash=digest(file);lifecycle.stop();lifecycle.whenStopped().toCompletableFuture().get(3,java.util.concurrent.TimeUnit.SECONDS);
    check(stopped.get()==1,"stop cancels session work");
    current.set(Configuration.load(file));
    check(!lifecycle.initialize().get("running").getAsBoolean(),"reload and repeated initialize never undo manual stop");
    lifecycle.updateSettings(ports.start+1,ports.start+10,true);
    check(!lifecycle.status().get("running").getAsBoolean(),"save does not reopen stopped session");
    JsonObject restarted=lifecycle.start();check(restarted.get("actual_port").getAsInt()==ports.start+1,"manual restart applies saved range: "+restarted);
    lifecycle.stop();check(Arrays.equals(hash,digest(file)),"manual start/stop do not alter config hash");
    JsonObject rejected=lifecycle.updateSettings(ports.start,ports.start+11,true);
    check(rejected.get("error").getAsString().equals("invalid_server_port_range"),"invalid update has stable reason");
    check(Arrays.equals(hash,digest(file)),"invalid update never writes config");
    Files.delete(file);Files.createDirectory(file);
    JsonObject failed=lifecycle.updateSettings(ports.start+2,ports.start+10,false);
    check(failed.get("error").getAsString().equals("server_config_save_failed"),"save failure reported");
    check(current.get().port==ports.start+1&&current.get().serverEnabled,"failed save rolls back memory");
   }
  }
 }
 private static void occupiedRangeDoesNotRetryUntilManualStart(Path dir)throws Exception{
  try(PortRange ports=PortRange.reserve()){
   Path file=dir.resolve("occupied.json");Configuration c=Configuration.load(file);c.port=ports.start;c.portEnd=ports.start+10;c.save(file);byte[] hash=digest(file);
   try(ServerLifecycle lifecycle=new ServerLifecycle(()->c,file,dir,(session,method,a)->Jsons.obj("ok",true),()->{})){
    JsonObject failed=lifecycle.initialize();
    check(!failed.get("running").getAsBoolean()&&failed.get("error").getAsString().equals("ports_unavailable"),"all occupied stops with stable reason");
    JsonObject record=JsonParser.parseString(Files.readString(dir.resolve("mcpmcp-runtime.json"))).getAsJsonObject();
    check(!record.get("running").getAsBoolean()&&record.get("port").getAsInt()==0,"failed bind clears discovery");
    ports.releaseFrom(0);
    check(!lifecycle.initialize().get("running").getAsBoolean(),"repeated initialization cannot retry failed startup");
    check(lifecycle.status().get("error").getAsString().equals("ports_unavailable"),"failed reason retained for display");
    check(lifecycle.start().get("running").getAsBoolean(),"explicit manual retry succeeds");
    check(lifecycle.status().get("error").getAsString().isEmpty(),"successful retry clears startup reason");
    check(Arrays.equals(hash,digest(file)),"failed bind and manual retry never write config");
    lifecycle.stop();lifecycle.whenStopped().toCompletableFuture().get(3,java.util.concurrent.TimeUnit.SECONDS);try(ServerSocket reused=PortRange.bind(ports.start)){check(reused.isBound(),"stop releases listener");}
   }
  }
 }
 private static void generationRejectsOldRequests(Path dir)throws Exception{
  try(PortRange ports=PortRange.reserve()){
   Path file=dir.resolve("generation.json");Configuration c=Configuration.load(file);c.port=ports.start;c.portEnd=ports.start+10;c.save(file);ports.releaseFrom(0);
   AtomicLong admitted=new AtomicLong(-1);
   try(ServerLifecycle lifecycle=new ServerLifecycle(()->c,file,dir,(session,method,a)->{admitted.set(session);return Jsons.obj("ok",true);},()->{})){
    lifecycle.initialize();
    HttpRequest request=HttpRequest.newBuilder(URI.create(lifecycle.status().get("call_endpoint").getAsString())).timeout(Duration.ofSeconds(3)).header("Authorization","Bearer "+c.token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(Jsons.obj("method","probe","args",new JsonObject()).toString())).build();
    HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.discarding());
    long old=admitted.get();check(old>=0&&lifecycle.accepts(old),"current session admits requests");
    lifecycle.stop();check(!lifecycle.accepts(old),"stop invalidates session");lifecycle.whenStopped().toCompletableFuture().get(3,java.util.concurrent.TimeUnit.SECONDS);
    lifecycle.start();check(!lifecycle.accepts(old),"restart cannot admit old HTTP work");
   }
  }
 }
 private static void stopDoesNotWaitForInflightHttp(Path dir)throws Exception{
  try(PortRange ports=PortRange.reserve()){
   Path file=dir.resolve("inflight.json");Configuration c=Configuration.load(file);c.port=ports.start;c.portEnd=ports.start+10;c.save(file);ports.releaseFrom(0);
   java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
   try(ServerLifecycle lifecycle=new ServerLifecycle(()->c,file,dir,(session,method,a)->{entered.countDown();try{release.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return Jsons.obj("ok",true);},()->{})){
    lifecycle.initialize();
    HttpRequest request=HttpRequest.newBuilder(URI.create(lifecycle.status().get("call_endpoint").getAsString())).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+c.token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(Jsons.obj("method","probe","args",new JsonObject()).toString())).build();
    var pending=HttpClient.newHttpClient().sendAsync(request,HttpResponse.BodyHandlers.discarding());
    check(entered.await(3,java.util.concurrent.TimeUnit.SECONDS),"inflight HTTP entered handler");
    long before=System.nanoTime();lifecycle.stop();long millis=(System.nanoTime()-before)/1_000_000;
    release.countDown();
    check(millis<500,"stop must not block client/render thread waiting for inflight HTTP (ms="+millis+")");
    check(!lifecycle.status().get("running").getAsBoolean(),"inflight stop closes admission immediately");
    pending.handle((r,e)->null).get(3,java.util.concurrent.TimeUnit.SECONDS);
   }finally{release.countDown();}
  }
 }
 private static void discoveryClearsStaleAndFailedStartup(Path dir)throws Exception{
  Path file=dir.resolve("disabled.json"),discovery=dir.resolve("mcpmcp-runtime.json");Configuration c=Configuration.load(file);c.serverEnabled=false;c.save(file);
  Files.writeString(discovery,Jsons.obj("running",true,"port",12345,"instance","previous-game").toString());
  try(ServerLifecycle lifecycle=new ServerLifecycle(()->c,file,dir,(session,method,a)->Jsons.obj("ok",true),()->{})){
   lifecycle.initialize();JsonObject inactive=JsonParser.parseString(Files.readString(discovery)).getAsJsonObject();
   check(!inactive.get("running").getAsBoolean()&&inactive.get("port").getAsInt()==0,"disabled new game invalidates stale discovery");
   JsonObject newer=Jsons.obj("running",false,"port",0,"instance","newer-game");Files.writeString(discovery,newer.toString());
   lifecycle.stop();check(JsonParser.parseString(Files.readString(discovery)).equals(newer),"older game shutdown cannot overwrite newer instance record");
  }
  try(ServerLifecycle lifecycle=new ServerLifecycle(()->null,file,dir,(session,method,a)->Jsons.obj("ok",true),()->{})){
   lifecycle.initializationFailed();JsonObject inactive=JsonParser.parseString(Files.readString(discovery)).getAsJsonObject();
   check(!inactive.get("running").getAsBoolean()&&inactive.get("port").getAsInt()==0,"invalid configuration startup clears discovery");
   check(lifecycle.status().get("error").getAsString().equals("initialization_failed"),"initialization error retained");
   check(!lifecycle.initialize().get("running").getAsBoolean(),"initialization failure never retries itself");
   check(lifecycle.updateSettings(5210,5220,true).get("error").getAsString().equals("configuration_unavailable"),"missing config fails safely");
  }
 }
 private static byte[] digest(Path file)throws Exception{return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));}
 private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 private static final class PortRange implements AutoCloseable {
  final int start;final List<ServerSocket> held;
  PortRange(int start,List<ServerSocket> held){this.start=start;this.held=held;}
  static PortRange reserve()throws Exception{
   for(int attempt=0;attempt<200;attempt++){
    List<ServerSocket> held=new ArrayList<>();
    try{ServerSocket first=bind(0);held.add(first);int start=first.getLocalPort();if(start<1024||start>65525||start<=5220&&start+10>=5210)throw new IOException("range_not_isolated");for(int p=start+1;p<=start+10;p++)held.add(bind(p));return new PortRange(start,held);}
    catch(IOException e){for(ServerSocket socket:held)socket.close();}
   }throw new AssertionError("cannot reserve independent consecutive temporary ports");
  }
  static ServerSocket bind(int port)throws IOException{ServerSocket socket=new ServerSocket();try{socket.setReuseAddress(false);socket.bind(new InetSocketAddress("127.0.0.1",port));return socket;}catch(IOException e){socket.close();throw e;}}
  void releaseFrom(int index)throws IOException{for(int i=index;i<held.size();i++)held.get(i).close();}
  public void close()throws IOException{releaseFrom(0);}
 }
}
