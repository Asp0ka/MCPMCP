package dev.mcpmcp.sophisticated;

import com.google.gson.*;
import dev.mcpmcp.api.*;
import dev.mcpmcp.core.*;
import java.util.*;

public final class AdapterContractTest {
 private static int checks;
 private static void check(boolean condition,String label) {checks++;if(!condition)throw new AssertionError(label);}
 private static void rejects(Runnable runnable,String label) {checks++;try{runnable.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError(label);}
 private static class Context implements PluginContext {
  final Map<String,Handler> actions=new HashMap<>(),observers=new HashMap<>();
  final Map<String,JsonObject> schemas=new HashMap<>();
  public void action(String n,JsonObject s,String t,String p,Handler h){check(actions.put(n,h)==null,"no duplicate action");Schema.validate(valid(n.substring(n.indexOf('.')+1)),s);check(Policy.allowed(p.equals("hack")?"HACK":"NORMAL",t.equals("client")?"SIMULATE":"PACKET",t,p),"declared policy usable");schemas.put(n,s);}
  public void observer(String k,int p,Handler h){check(observers.put(k,h)==null,"no duplicate observer");}
  public JsonObject execute(String n,JsonObject a){throw new AssertionError("provider must not recursively execute");}
  public JsonObject observe(String k,JsonObject a){throw new AssertionError("provider must not recursively observe");}
 }
 private static JsonObject valid(String op) {JsonObject a=Jsons.obj("menu_id",1,"menu_token","identity");if(op.equals("open")){a.addProperty("inventory_slot",0);a.addProperty("expected_item","sophisticatedbackpacks:backpack");a.addProperty("expected_count",1);a.addProperty("stack_token","snapshot");}if(op.startsWith("quick_move")){a.addProperty("slot",0);a.addProperty("expected_item","minecraft:stone");a.addProperty("expected_count",2);a.addProperty("state_id",4);}if(op.equals("select_crafting"))a.addProperty("upgrade_slot",0);return a;}
 public static void main(String[] argv) throws Exception {
  Context context=new Context();
  AdapterBase.Access fake=new AdapterBase.Access(){public JsonObject observe(JsonObject a){return Jsons.obj("target_menu",false);}public JsonObject execute(String op,JsonObject a){return Jsons.obj("delivery","submitted","server_confirmed",false);}};
  new StoragePlugin(fake).register(context);new BackpackPlugin(fake).register(context);new FurnacePlugin(fake).register(context);
  check(context.actions.size()==11,"three providers register exactly eleven distinct actions");check(context.observers.size()==3,"three root kinds");
  for(String type:List.of("storage","backpack","furnace")){
   String ns=Contract.namespace(type);JsonObject observed=context.observers.get(ns).call(new JsonObject());
   check(observed.has("actions")&&observed.has("preconditions")&&observed.has("observation_schema"),"root contract works without matching menu");
   for(String op:Contract.operations(type)){
    JsonObject a=valid(op);Contract.validate(a,op);JsonObject result=context.actions.get(ns+"."+op).call(a);
    check(result.get("delivery").getAsString().equals("submitted")&&!result.has("observed_success"),"submission never claims observed success");
    JsonObject bad=a.deepCopy();bad.addProperty("mode","CHEAT");rejects(()->Contract.validate(bad,op),"reject hidden mode change");
    JsonObject missing=a.deepCopy();missing.remove("menu_token");rejects(()->Contract.validate(missing,op),"menu identity required");
    JsonObject wrong=a.deepCopy();wrong.addProperty("menu_id",1.25);rejects(()->Contract.validate(wrong,op),"reject fractional ids");
   }
  }
  for(String op:List.of("quick_move","quick_move_packet")){
   JsonObject a=valid(op);a.addProperty("slot",-1);rejects(()->Contract.validate(a,op),"no outside dropping slot");
   a.addProperty("slot",0);a.addProperty("click","throw");rejects(()->Contract.validate(a,op),"no arbitrary click");
  }
  JsonObject open=valid("open");open.addProperty("inventory_slot",36);rejects(()->Contract.validate(open,"open"),"no implicit equipment/curios slots");
  JsonObject openMissing=valid("open");openMissing.remove("stack_token");rejects(()->Contract.validate(openMissing,"open"),"full backpack snapshot required");
  MenuGuard.check(1,"one",1,"one",true);
  rejects(()->MenuGuard.check(1,"one",1,"two",true),"reused id with different menu rejected");
  rejects(()->MenuGuard.check(1,"one",2,"one",true),"changed menu id rejected");
  rejects(()->MenuGuard.check(1,"one",1,null,true),"unobserved menu rejected");
  rejects(()->MenuGuard.check(1,"one",1,"one",false),"carried cursor rejected");
  for(String role:List.of("input","fuel","output","factory_input","factory_output","generator_fuel","storage","player_inventory","crafting_input","crafting_result")){
   MenuGuard.quickMove(role,true,true);rejects(()->MenuGuard.quickMove(role,false,true),"inactive slot rejected");rejects(()->MenuGuard.quickMove(role,true,false),"native pickup denied");
  }
  for(String role:List.of("augment_red","unknown_read_only","upgrade_settings_read_only","upgrade_install_or_extra_read_only"))rejects(()->MenuGuard.quickMove(role,true,true),"unreviewed slot remains read-only");
  check(MenuGuard.furnaceRole("ironfurnaces.container.slots.SlotIronFurnaceInputGenerator").equals("generator_fuel"),"generator fuel is separate");
  check(MenuGuard.furnaceRole("ironfurnaces.container.slots.SlotIronFurnaceOutputFactory").equals("factory_output"),"factory output recognized");
  check(MenuGuard.furnaceRole("evil.SlotIronFurnaceFuel").equals("unknown_read_only"),"no simple-name class confusion");
  rejects(()->Binding.validate("sophisticatedcore","1.3.78.2249","wrong"),"core hash mismatch fails");
  rejects(()->Binding.validate("ironfurnaces","4.1.9","e457052522cef1f8644b6929a64eab96de04ce9e6e32a715f1d68d79da26d66b"),"version mismatch fails");
  Binding.validate("ironfurnaces","4.1.8","e457052522cef1f8644b6929a64eab96de04ce9e6e32a715f1d68d79da26d66b");
  check(!Policy.allowed("NORMAL","SIMULATE",Contract.transport("sort"),Contract.permission("sort")),"native C2S requires explicit packet/hack policy");
  check(!Policy.allowed("HACK","PACKET",Contract.transport("quick_move"),Contract.permission("quick_move")),"simulate action cannot silently select packet mode");
  System.out.println("PASS AdapterContractTest checks="+checks+" actions="+context.actions.size()+" observers="+context.observers.size());
 }
}
