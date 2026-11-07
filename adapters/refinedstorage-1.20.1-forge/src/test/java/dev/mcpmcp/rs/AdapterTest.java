package dev.mcpmcp.rs;
import com.google.gson.*;
import dev.mcpmcp.api.*;
import dev.mcpmcp.core.*;
import java.util.*;
import static dev.mcpmcp.core.Jsons.*;
public final class AdapterTest {
 public static void main(String[] args) throws Exception {
  Map<String,PluginContext.Handler> reads=new HashMap<>(),actions=new HashMap<>();
  new RsProvider(new RsService(new FakeGrid())).register(new PluginContext(){
   public void observer(String n,int p,Handler h){reads.put(n,h);}
   public void action(String n,JsonObject s,String t,String p,Handler h){PluginValidation.schema(s);if(!p.equals("normal")||!t.equals(n.equals("rs.crafting.take")?"input":"packet"))throw new AssertionError("wrong transport/permission");actions.put(n,h);}
   public JsonObject execute(String n,JsonObject a){throw new AssertionError("unexpected core call");}
   public JsonObject observe(String n,JsonObject a){throw new AssertionError("unexpected core read");}
  });
  check(reads.keySet().equals(Set.of("rs","rs.grid","rs.storage","rs.operation")),"discoverable observers");
  check(actions.keySet().equals(Set.of("rs.extract","rs.insert_held","rs.craft.start","rs.crafting.take","rs.crafting.clear")),"controlled actions");
  JsonObject contract=reads.get("rs").call(new JsonObject());
  check(contract.getAsJsonObject("data").has("contracts"),"contracts in status");
  JsonObject observerError=reads.get("rs.storage").call(obj("limit",0));
  check(!observerError.has("error")&&observerError.getAsJsonObject("data").has("error"),"host must not swallow observer errors");
  for(String k:RsProvider.ACTIONS){
   check(!Policy.allowed("NORMAL",k.equals("rs.crafting.take")?"PACKET":"SIMULATE",RsProvider.transport(k),"normal"),"never downgrade transport");
   check(Policy.allowed("NORMAL","MIXED",RsProvider.transport(k),"normal"),"mixed allows actual transport");
  }
  try{PluginValidation.manifest(JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("distribution/plugin.json"))).getAsJsonObject());}catch(java.io.IOException e){throw new AssertionError(e);}
  System.out.println("AdapterTest: public registration contract passed");
  ServiceTest.run();NbtIdentityTest.run();PacketTest.run();
 }
 static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
 static final class FakeGrid implements GridAccess {
  public Frame capture(){return null;}
  public java.util.List<Entry> entries(){return java.util.List.of();}
  public void submit(Command command){}
 }
}
