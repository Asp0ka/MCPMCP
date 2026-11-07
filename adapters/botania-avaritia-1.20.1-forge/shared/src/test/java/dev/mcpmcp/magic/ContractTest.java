package dev.mcpmcp.magic;
import com.google.gson.*;
import dev.mcpmcp.api.*;
import dev.mcpmcp.core.*;
import java.util.*;

public final class ContractTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static JsonObject obj(String json){return JsonParser.parseString(json).getAsJsonObject();}
 static void rejects(Runnable f){boolean no=false;try{f.run();}catch(IllegalArgumentException e){no=true;}check(no,"invalid input must reject");}
 public static void main(String[] argv)throws Exception {
  for(String mod:List.of("botania","avaritia")){
   Map<String,PluginContext.Handler> reads=new HashMap<>(),actions=new HashMap<>();int[] calls={0},acts={0};
   MagicProvider p=new MagicProvider(mod,new MagicProvider.Backend(){public JsonObject read(String kind,JsonObject a){calls[0]++;return J.obj("kind",kind,"args",a);}public JsonObject interact(JsonObject a,boolean packet){acts[0]++;return J.obj("packet",packet,"delivery","submitted");}});
   p.register(new PluginContext(){public void observer(String kind,int priority,Handler h){check(priority==100,"priority");reads.put(kind,h);}public void action(String n,JsonObject s,String t,String perm,Handler h){check(n.endsWith(t)&&perm.equals("normal"),"fixed transport normal policy");PluginValidation.schema(s);actions.put(n,h);}public JsonObject execute(String n,JsonObject a){throw new AssertionError("registration is side effect free");}public JsonObject observe(String n,JsonObject a){throw new AssertionError("no recursive observer");}});
   check(p.id().equals("mcpmcp."+mod),"provider id");check(reads.size()==4&&!reads.containsKey("recipes"),"independent namespace");
   JsonObject contract=reads.get(mod).call(new JsonObject());check(contract.getAsJsonObject("schemas").size()==4,"discoverable schemas");check(calls[0]==0,"contract works without world/backend");
   check(actions.size()==2&&contract.getAsJsonObject("actions").size()==2,"safe native operations discoverable");
   var valid=J.obj("x",0,"y",64,"z",0,"expected_block",mod+":test","expected_item","minecraft:air");
   check(actions.get(mod+".interact.packet").call(valid).get("packet").getAsBoolean(),"packet stays packet");
   check(!actions.get(mod+".interact.client").call(valid).get("packet").getAsBoolean(),"client stays client");
   check(acts[0]==2,"one backend call per invocation no child jobs");
   for(String invalid:List.of("{}",J.obj("x",0,"y",64,"z",0,"expected_block","x","expected_item","y","hand","bad").toString())){boolean rejected=false;try{actions.get(mod+".interact.packet").call(obj(invalid));}catch(IllegalArgumentException ex){rejected=true;}check(rejected&&acts[0]==2,"invalid operation rejected before backend");}
   for(String suffix:List.of("",".blocks",".menu",".recipes"))PluginValidation.schema(MagicProvider.schema(suffix));
   for(String invalid:List.of("{}","{\"x\":0,\"y\":64,\"z\":0,\"radius\":9}","{\"x\":0,\"y\":64,\"z\":0,\"scan_budget\":4097}","{\"x\":0,\"y\":64,\"z\":0,\"server\":true}","{\"x\":0.5,\"y\":64,\"z\":0}")){int before=calls[0];check(!reads.get(mod+".blocks").call(obj(invalid)).get("ok").getAsBoolean(),"closed bounded block contract");check(calls[0]==before,"invalid query does not touch live world");}
   for(String invalid:List.of("{\"limit\":0}","{\"limit\":65}","{\"scan_budget\":0}","{\"offset\":1}","{\"query\":false}"))check(!reads.get(mod+".recipes").call(obj(invalid)).get("ok").getAsBoolean(),"recipe contract rejects invalid");
   check(reads.get(mod+".blocks").call(obj("{\"x\":0,\"y\":64,\"z\":0,\"radius\":8}")).get("kind").getAsString().equals(".blocks"),"boundary reaches backend once");
   check(J.unknown("not_synced").get("status").getAsString().equals("unknown"),"unknown distinct from zero");
  }
  long[] time={1000};Cursors c=new Cursors(()->time[0]);Object world=new Object(),source=new Object();String t=c.next(world,source,"q",25);check(c.offset(t,world,source,"q")==25,"cursor offset");
  rejects(()->c.offset(t,new Object(),source,"q"));rejects(()->c.offset(t,world,new Object(),"q"));rejects(()->c.offset(t,world,source,"changed"));rejects(()->c.offset("25",world,source,"q"));time[0]+=120001;rejects(()->c.offset(t,world,source,"q"));
  check(Policy.allowed("NORMAL","PACKET","packet","normal"),"core packet policy");check(!Policy.allowed("NORMAL","PACKET","client","normal"),"packet cannot simulate");check(!Policy.allowed("NORMAL","MIXED","packet","hack"),"no escalation");
  DataTest.run();System.out.println("Botania/Avaritia public contracts: "+checks+" checks passed");
 }
}
